package server.network.router;

import server.network.session.Session;
import server.message.Message;
import server.message.MessageType;
import server.message.MessageCodec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import server.sql.DatabaseManager;
import server.sql.user.UserDAO;
import server.sql.cp.CPBindingDAO;
import java.sql.Connection;
import java.sql.SQLException;

public class MessageRouter {
    // 管理所有活动会话，键为用户ID
    private final Map<String, Session> sessions;

    public MessageRouter() {
        this.sessions = new ConcurrentHashMap<>();
        System.out.println("消息路由器已初始化");
    }

    /**
     * 注册新会话
     * @param session 用户会话
     * @return true表示注册成功，false表示用户名已登录
     */
    public boolean registerSession(Session session) {
        if (session == null || session.getUserId() == null) {
            System.err.println("无效的会话对象");
            return false;
        }

        String userId = session.getUserId();
        String username = session.getUsername();
        
        // 检查是否已有相同用户名的活动会话
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session existingSession = entry.getValue();
            if (existingSession != null && existingSession.getUsername() != null && 
                existingSession.getUsername().equals(username) && 
                existingSession.isActive()) {
                
                System.err.println("注册会话失败: 用户名\"" + username + "\"已在其他地方登录");
                return false;
            }
        }
        
        // 清理该用户的旧的非活动会话
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session existingSession = entry.getValue();
            if (existingSession != null && existingSession.getUsername() != null && 
                existingSession.getUsername().equals(username) && 
                !existingSession.isActive()) {
                
                System.out.println("清理旧的非活动会话: 用户名=" + username + ", 旧用户ID=" + entry.getKey());
                sessions.remove(entry.getKey());
                break;
            }
        }
        
        // 注册新会话
        sessions.put(userId, session);
        System.out.println("会话已注册: 用户ID=" + userId + ", 用户名=" + username);
        
        // 通知CP用户上线
        notifyCPOfUserStatusUpdate(userId, username, "ONLINE");
        
        // 向用户发送CP的当前状态
        sendCPStatusToUser(userId, username);
        
        return true;
    }
    
    /**
     * 向用户发送CP的当前状态
     * @param userId 用户ID
     * @param username 用户名
     * @return 发送成功返回1，失败返回0
     */
    public int sendCPStatusToUser(String userId, String username) {
        if (userId == null || username == null) {
            System.err.println("无效的参数");
            return 0;
        }
        
        Session userSession = sessions.get(userId);
        
        if (userSession == null || !userSession.isActive()) {
            System.err.println("用户会话不存在或非活动: " + username);
            return 0;
        }
        
        try (Connection connection = new DatabaseManager().getConnection()) {
            // 获取用户的CP
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            String cpUsername = cpBindingDAO.getCPUsername(Integer.parseInt(userId), connection);
            
            if (cpUsername == null) {
                System.out.println("用户" + username + "尚未绑定CP");
                return 0;
            }
            
            System.out.println("向用户" + username + "发送CP状态");
            
            // 检查CP是否在线
            Session cpSession = getSessionByUsername(cpUsername);
            boolean isOnline = cpSession != null && cpSession.isActive();
            String status = isOnline ? "ONLINE" : "OFFLINE";
            
            // 创建状态更新消息
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("username", cpUsername);
            statusData.put("status", status);
            statusData.put("isOnline", isOnline);
            
            // 将状态数据转换为JSON字符串
            String statusJson = null;
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                statusJson = gson.toJson(statusData);
            } catch (Exception e) {
                System.err.println("转换状态数据为JSON失败: " + e.getMessage());
                return 0;
            }
            
            MessageCodec messageCodec = new MessageCodec();
            Message statusMessage = new Message(
                MessageType.USER_STATUS_UPDATE,
                cpUsername,
                statusJson,
                null
            );
            
            // 编码并发送消息
            String encodedMessage = messageCodec.encode(statusMessage);
            userSession.getClientConnection().send(encodedMessage);
            
            System.out.println("向用户" + username + "发送CP状态: " + cpUsername + " 现在 " + status);
            return 1;
            
        } catch (Exception e) {
            System.err.println("向用户发送CP状态失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }

    /**
     * 注销会话
     * @param userId 用户ID
     */
    public void deregisterSession(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        Session removedSession = sessions.remove(userId);
        if (removedSession != null) {
            // 将会话标记为非活动状态
            removedSession.setActive(false);

            System.out.println("会话已注销: 用户ID=" + userId);
            
            // 通知CP用户下线
            if (removedSession.getUsername() != null) {
                notifyCPOfUserStatusUpdate(userId, removedSession.getUsername(), "OFFLINE");
            }
        }
    }

    /**
     * 向特定用户发送消息
     * @param fromUserId 发送者用户ID
     * @param toUserId 接收者用户ID
     * @param message 消息内容
     * @return true表示发送成功，false表示失败
     */
    public boolean sendPrivateMessage(String fromUserId, String toUserId, String message) {
        if (fromUserId == null || toUserId == null || message == null ||
            fromUserId.isEmpty() || toUserId.isEmpty() || message.isEmpty()) {
            System.err.println("无效的消息参数");
            return false;
        }

        Session fromSession = sessions.get(fromUserId);
        Session toSession = sessions.get(toUserId);

        if (fromSession == null) {
            System.err.println("发送者会话不存在: " + fromUserId);
            return false;
        }

        if (toSession == null || !toSession.isActive()) {
            System.err.println("接收者会话不存在或已失效: " + toUserId);
            return false;
        }

        try {
            toSession.getClientConnection().send(message);
            return true;
        } catch (Exception e) {
            System.err.println("发送私人消息失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 根据会话ID发送消息
     * @param conversationId 会话ID
     * @param message 消息内容
     * @param excludeUserId 排除的用户ID
     * @return true表示发送成功，false表示失败
     */
    public boolean sendMessageByConversationId(int conversationId, String message, String excludeUserId) {
        if (conversationId <= 0 || message == null || message.isEmpty()) {
            System.err.println("无效的会话ID或消息参数");
            return false;
        }

        try {
            // 从数据库获取会话成员
            List<String> memberUsernames = new ArrayList<>();
            try (Connection connection = new DatabaseManager().getConnection()) {
                server.sql.conversation.ConversationDAO conversationDAO = new server.sql.conversation.ConversationDAO();
                List<server.sql.conversation.ConversationMember> members = conversationDAO.getConversationMembers(conversationId, connection);
                for (server.sql.conversation.ConversationMember member : members) {
                    memberUsernames.add(member.getUsername());
                }
            } catch (SQLException e) {
                System.err.println("获取会话成员失败: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            // 向所有会话成员发送消息
            int sentCount = 0;
            for (String username : memberUsernames) {
                // 查找用户会话
                for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                    Session session = entry.getValue();
                    if (session != null && session.isActive() && session.getUsername() != null && 
                        session.getUsername().equals(username)) {
                        // 排除指定用户
                        if (excludeUserId != null && entry.getKey().equals(excludeUserId)) {
                            continue;
                        }
                        
                        session.getClientConnection().send(message);
                        sentCount++;
                        break;
                    }
                }
            }

            System.out.println("向会话 " + conversationId + " 的 " + sentCount + " 个成员发送消息成功");
            return sentCount > 0;
        } catch (Exception e) {
            System.err.println("根据会话ID发送消息失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向所有在线用户广播消息
     * @param message 消息内容
     * @param excludeUserId 要排除的用户ID
     * @return true表示广播成功，false表示失败
     */
    public boolean broadcastToAll(String message, String excludeUserId) {
        if (message == null || message.isEmpty()) {
            System.err.println("无效的广播参数");
            return false;
        }

        try {
            System.out.println("向所有客户端广播消息");
            
            for (String userId : sessions.keySet()) {
                // 跳过要排除的用户
                if (excludeUserId != null && excludeUserId.equals(userId)) {
                    continue;
                }
                
                Session session = sessions.get(userId);
                if (session != null && session.isActive()) {
                    session.getClientConnection().send(message);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("广播失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据用户ID获取会话
     * @param userId 用户ID
     * @return 会话对象，如果不存在则返回null
     */
    public Session getSession(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return sessions.get(userId);
    }
    
    /**
     * 根据用户名获取会话
     * @param username 用户名
     * @return 会话对象，如果不存在则返回null
     */
    public Session getSessionByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session != null && session.getUsername() != null && 
                session.getUsername().equals(username) && session.isActive()) {
                return session;
            }
        }
        
        return null;
    }
    
    /**
     * 获取所有会话
     * @return 会话映射表
     */
    public Map<String, Session> getSessions() {
        return sessions;
    }

    /**
     * 获取活动会话数量
     * @return 活动会话数量
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * 当用户状态更新时，向其CP发送状态更新通知
     * @param userId 用户ID
     * @param username 用户名
     * @param newStatus 新状态
     * @return 发送成功返回1，失败返回0
     */
    public int notifyCPOfUserStatusUpdate(String userId, String username, String newStatus) {
        if (userId == null || username == null || newStatus == null) {
            System.err.println("无效的状态更新参数");
            return 0;
        }
        
        try (Connection connection = new DatabaseManager().getConnection()) {
            // 获取用户的CP
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            Integer cpUserId = cpBindingDAO.getCPUserId(Integer.parseInt(userId), connection);
            
            if (cpUserId == null) {
                System.out.println("用户" + username + "尚未绑定CP");
                return 0;
            }
            
            Session cpSession = sessions.get(String.valueOf(cpUserId));
            if (cpSession != null && cpSession.isActive()) {
                // 创建状态更新消息
                Map<String, Object> statusData = new HashMap<>();
                statusData.put("username", username);
                statusData.put("status", newStatus);
                statusData.put("isOnline", "ONLINE".equals(newStatus));
                
                String statusJson = new com.google.gson.Gson().toJson(statusData);
                
                MessageCodec messageCodec = new MessageCodec();
                Message statusMessage = new Message(
                    MessageType.USER_STATUS_UPDATE,
                    username,
                    statusJson,
                    null
                );
                
                String encodedMessage = messageCodec.encode(statusMessage);
                cpSession.getClientConnection().send(encodedMessage);
                
                System.out.println("通知CP用户" + cpSession.getUsername() + ": " + username + " 现在 " + newStatus);
                return 1;
            }
            
        } catch (Exception e) {
            System.err.println("通知CP用户状态更新失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }
}
