package server.network.router;
import server.network.session.Session;
import server.club.Club;
import server.club.PublicClub;
import server.club.PrivateClub;
import server.message.Message;
import server.message.MessageType;
import server.message.MessageCodec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import server.sql.DatabaseManager;
import server.sql.club.ClubDAO;
import server.sql.user.UserDAO;
import server.sql.conversation.ConversationDAO;
import server.sql.conversation.ConversationMember;
import server.sql.friend.FriendshipDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MessageRouter {
    private final Map<String, Session> sessions;
    private final Map<String, Club> clubs;
    private final Map<String, List<String>> userClubs;

    public MessageRouter() {
        this.sessions = new ConcurrentHashMap<>();
        this.clubs = new ConcurrentHashMap<>();
        this.userClubs = new ConcurrentHashMap<>();
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
        
        List<String> userClubList = userClubs.get(userId);
        if (userClubList == null) {
            userClubList = new ArrayList<>();
            userClubs.put(userId, userClubList);
        }
        
        // 注册新会话
        sessions.put(userId, session);
        System.out.println("会话已注册: 用户ID=" + userId + ", 用户名=" + username);
        
        // 通知好友用户上线
        notifyFriendsOfUserStatusUpdate(userId, username, "ONLINE");
        
        // 向用户发送所有好友的当前状态
        sendFriendsStatusToUser(userId, username);
        
        return true;
    }
    
    /**
     * 向用户发送所有好友的当前状态
     * @param userId 用户ID
     * @param username 用户名
     * @return 发送成功的好友数量
     */
    public int sendFriendsStatusToUser(String userId, String username) {
        if (userId == null || username == null) {
            System.err.println("无效的参数");
            return 0;
        }
        
        int sentCount = 0;
        Session userSession = sessions.get(userId);
        
        if (userSession == null || !userSession.isActive()) {
            System.err.println("用户会话不存在或非活动: " + username);
            return 0;
        }
        
        try (Connection connection = new DatabaseManager().getConnection()) {
            // 获取用户的所有好友
            FriendshipDAO friendshipDAO = new FriendshipDAO();
            List<FriendshipDAO.Friendship> friendships;
            try {
                friendships = friendshipDAO.getUserFriends(Integer.parseInt(userId), connection);
            } catch (Exception e) {
                // 如果表不存在，返回空列表
                if (e.getMessage().contains("doesn't exist")) {
                    System.err.println("Friendships表不存在，跳过好友状态获取");
                    return 0;
                }
                throw e;
            }
            
            System.out.println("向用户" + username + "发送好友状态，好友数量: " + friendships.size());
            
            // 为每个好友创建并发送状态更新消息
            MessageCodec messageCodec = new MessageCodec();
            
            for (FriendshipDAO.Friendship friendship : friendships) {
                if (friendship == null) continue;
                
                // 确定好友的用户名
                String friendUsername = null;
                if (friendship.user1Username != null && !friendship.user1Username.equals(username)) {
                    friendUsername = friendship.user1Username;
                } else if (friendship.user2Username != null && !friendship.user2Username.equals(username)) {
                    friendUsername = friendship.user2Username;
                }
                
                if (friendUsername == null) continue;
                
                // 检查好友是否在线
                Session friendSession = getSessionByUsername(friendUsername);
                boolean isOnline = friendSession != null && friendSession.isActive();
                String status = isOnline ? "ONLINE" : "OFFLINE";
                
                // 创建状态更新消息
                Map<String, Object> statusData = new HashMap<>();
                statusData.put("username", friendUsername);
                statusData.put("status", status);
                statusData.put("isOnline", isOnline);
                
                // 将状态数据转换为JSON字符串
                String statusJson = null;
                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    statusJson = gson.toJson(statusData);
                } catch (Exception e) {
                    System.err.println("转换状态数据为JSON失败: " + e.getMessage());
                    continue;
                }
                
                Message statusMessage = new Message(
                    MessageType.USER_STATUS_UPDATE,
                    friendUsername,
                    statusJson,
                    null
                );
                
                // 编码并发送消息
                String encodedMessage = messageCodec.encode(statusMessage);
                userSession.getClientConnection().send(encodedMessage);
                sentCount++;
                
                System.out.println("向用户" + username + "发送好友状态: " + friendUsername + " 现在 " + status);
            }
            
        } catch (Exception e) {
            System.err.println("向用户发送好友状态失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("成功向用户" + username + "发送" + sentCount + "个好友的状态");
        return sentCount;
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
            
            // 不删除userRooms映射，这样用户重连时仍然在原来的房间中
            // 只需要将会话标记为非活动状态即可

            System.out.println("会话已注销: 用户ID=" + userId);
            
            // 通知好友用户下线
            if (removedSession.getUsername() != null) {
                notifyFriendsOfUserStatusUpdate(userId, removedSession.getUsername(), "OFFLINE");
            }
        }
    }

    /**
     * 创建新房间
     * @param name 房间名称
     * @param id 房间ID
     * @param isPublic 是否为公共房间
     * @return 创建的房间对象
     */
    public Club createClub(String name, String id, boolean isPublic) {
        if (name == null || id == null || name.isEmpty() || id.isEmpty()) {
            System.err.println("无效的社团参数");
            return null;
        }

        if (clubs.containsKey(id)) {
            System.err.println("社团ID已存在: " + id);
            return null;
        }

        Club club = isPublic ? new PublicClub(name, id, this) : new PrivateClub(name, id, this);
        clubs.put(id, club);
        System.out.println("创建新社团: " + name + " (ID: " + id + ")");
        return club;
    }
    
    /**
     * 添加社团
     * @param club 社团对象
     * @return 是否添加成功
     */

    /**
     * 获取社团
     * @param clubId 社团ID
     * @return 社团对象，如果不存在则返回null
     */
    public Club getClub(String clubId) {
        if (clubId == null || clubId.isEmpty()) {
            return null;
        }
        return clubs.get(clubId);
    }
    
    /**
     * 获取社团用户列表
     * @param clubId 社团ID
     * @return 用户列表，包含用户名和在线状态
     */
    public List<Map<String, Object>> getClubUsers(String clubId) {
        List<Map<String, Object>> usersList = new ArrayList<>();
        
        try (Connection connection = new DatabaseManager().getConnection()) {
            ConversationDAO conversationDAO = new ConversationDAO();
            UserDAO userDAO = new UserDAO();
            
            int conversationId = -1;
            
            try {
                int id = Integer.parseInt(clubId);
                
                // 先尝试直接作为conversation_id查找
                String checkConversationSql = "SELECT id FROM conversation WHERE id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(checkConversationSql)) {
                    pstmt.setInt(1, id);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            conversationId = id;
                        }
                    }
                }
                
                // 如果不是conversation_id，尝试作为club_id查找对应的conversation
                if (conversationId <= 0) {
                    String findConversationSql = "SELECT id FROM conversation WHERE club_id = ? AND type = 'CLUB'";
                    try (PreparedStatement pstmt = connection.prepareStatement(findConversationSql)) {
                        pstmt.setInt(1, id);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                conversationId = rs.getInt("id");
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // roomId不是数字，返回空列表
                return usersList;
            }
            
            if (conversationId <= 0) {
                return usersList;
            }
            
            List<ConversationMember> members = conversationDAO.getConversationMembers(conversationId, connection);
            
            for (ConversationMember member : members) {
                int userId = member.getUserId();
                String memberId = String.valueOf(userId);
                
                String username = userDAO.getUsernameById(userId, connection);
                
                if (username != null) {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("username", username);
                    
                    boolean isOnline = false;
                    
                    Session session = sessions.get(memberId);
                    if (session != null && session.isActive()) {
                        isOnline = true;
                    }
                    
                    userInfo.put("isOnline", isOnline);
                    usersList.add(userInfo);
                }
            }
        } catch (SQLException e) {
            System.err.println("获取房间用户列表失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return usersList;
    }

    /**
     * 将用户加入社团
     * @param userId 用户ID
     * @param clubId 社团ID
     * @return true表示加入成功，false表示失败
     */
    public boolean joinClub(String userId, String clubId) {
        if (userId == null || clubId == null || userId.isEmpty() || clubId.isEmpty()) {
            System.err.println("无效的用户ID或社团ID");
            return false;
        }

        Session session = sessions.get(userId);
        Club club = clubs.get(clubId);

        if (session == null) {
            System.err.println("用户会话不存在: " + userId);
            return false;
        }

        if (club == null) {
            System.err.println("社团不存在: " + clubId);
            return false;
        }

        if (club.addUser(userId, session.getUsername())) {
            List<String> userClubList = userClubs.computeIfAbsent(userId, key -> new ArrayList<>());
            userClubList.add(clubId);
            System.out.println("用户" + session.getUsername() + "(ID: " + userId + ") 加入社团: " + club.getName());
            
            try {
                MessageCodec messageCodec = new MessageCodec();
                Message joinMessage = new Message(
                    MessageType.JOIN,
                    session.getUsername(),
                    session.getUsername() + " 加入了社团",
                    null,
                    club.getConversationId()
                );
                
                String encodedMessage = messageCodec.encode(joinMessage);
                broadcastToClub(clubId, encodedMessage);
            } catch (Exception e) {
                System.err.println("创建或广播加入社团消息失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            return true;
        }

        return false;
    }

    /**
     * 将用户从社团移除
     * @param userId 用户ID
     * @param clubId 社团ID
     * @return true表示移除成功，false表示失败
     */
    public boolean leaveClub(String userId, String clubId) {
        if (userId == null || clubId == null || userId.isEmpty() || clubId.isEmpty()) {
            System.err.println("无效的用户ID或社团ID");
            return false;
        }

        Club club = clubs.get(clubId);
        if (club == null) {
            System.err.println("社团不存在: " + clubId);
            return false;
        }

        if (club.removeUser(userId)) {
            List<String> userClubList = userClubs.get(userId);
            if (userClubList != null) {
                userClubList.remove(clubId);
            }
            System.out.println("用户" + userId + " 离开社团: " + club.getName());
            return true;
        }

        return false;
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
            // 实际应用中应该使用Message对象和MessageCodec进行消息编码
            // 这里简化处理，直接发送消息内容
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
                ConversationDAO conversationDAO = new ConversationDAO();
                List<ConversationMember> members = conversationDAO.getConversationMembers(conversationId, connection);
                for (ConversationMember member : members) {
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
     * 向社团广播消息
     * @param clubId 社团ID
     * @param message 消息内容
     * @return true表示广播成功，false表示失败
     */
    public boolean broadcastToClub(String clubId, String message) {
        return broadcastToClub(clubId, message, null);
    }

    /**
     * 向社团广播消息（可排除指定用户）
     * @param clubId 社团ID
     * @param message 消息内容
     * @param excludeUserId 要排除的用户ID
     * @return true表示广播成功，false表示失败
     */
    public boolean broadcastToClub(String clubId, String message, String excludeUserId) {
        if (clubId == null || message == null || clubId.isEmpty() || message.isEmpty()) {
            System.err.println("无效的广播参数");
            return false;
        }

        Club club = clubs.get(clubId);
        if (club == null) {
            System.err.println("社团不存在: " + clubId);
            return false;
        }

        try {
            Set<String> userIds;
            
            if ("system".equals(club.getName())) {
                System.out.println("向所有客户端广播system消息: " + message);
                userIds = sessions.keySet();
            } else {
                userIds = club.getUserIds();
                System.out.println("向社团" + club.getName() + " (ID: " + club.getId() + ") 广播消息，用户数量: " + userIds.size());
            }
            
            for (String userId : userIds) {
                if (excludeUserId != null && excludeUserId.equals(userId)) {
                    System.out.println("跳过广播给用户: " + userId);
                    continue;
                }
                
                Session session = sessions.get(userId);
                if (session != null && session.isActive()) {
                    session.getClientConnection().send(message);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("社团广播失败: " + e.getMessage());
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
        
        // 使用迭代器遍历确保并发安全
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
     * 获取社团数量
     * @return 社团数量
     */
    public int getClubCount() {
        return clubs.size();
    }
    
    /**
     * 获取所有社团
     * @return 社团映射表
     */
    public Map<String, Club> getClubs() {
        return clubs;
    }
    
    /**
     * 添加社团到路由器
     * @param club 要添加的社团对象
     * @return true表示添加成功，false表示失败
     */
    public boolean addClub(Club club) {
        if (club == null || club.getId() == null || club.getId().isEmpty()) {
            System.err.println("无效的社团对象");
            return false;
        }
        
        if (clubs.containsKey(club.getId())) {
            System.err.println("社团ID已存在: " + club.getId());
            return false;
        }
        
        clubs.put(club.getId(), club);
        System.out.println("社团已添加到路由器: " + club.getName() + " (ID: " + club.getId() + "), conversation_id: " + club.getConversationId());
        return true;
    }
    
    /**
     * 从会话ID中提取实际的社团名或用户名
     * 客户端使用前缀区分会话类型：#表示社团，@表示好友/私聊
     * @param sessionId 会话ID（可能带前缀）
     * @return 实际的社团名或用户名（不带前缀）
     */
    public static String extractActualName(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return sessionId;
        }
        
        if (sessionId.startsWith("#") || sessionId.startsWith("@")) {
            return sessionId.substring(1);
        }
        
        return sessionId;
    }
    
    /**
     * 判断会话ID是否为社团会话
     * @param sessionId 会话ID
     * @return true表示社团会话，false表示好友/私聊会话
     */
    public static boolean isClubSession(String sessionId) {
        return sessionId != null && sessionId.startsWith("#");
    }
    
    /**
     * 判断会话ID是否为好友/私聊会话
     * @param sessionId 会话ID
     * @return true表示好友/私聊会话，false表示社团会话
     */
    public static boolean isFriendSession(String sessionId) {
        return sessionId != null && sessionId.startsWith("@");
    }
    
    /**
     * 当用户状态更新时，向其所有好友发送状态更新通知
     * @param userId 用户ID
     * @param username 用户名
     * @param newStatus 新状态
     * @return 发送成功的好友数量
     */
    public int notifyFriendsOfUserStatusUpdate(String userId, String username, String newStatus) {
        if (userId == null || username == null || newStatus == null) {
            System.err.println("无效的状态更新参数");
            return 0;
        }
        
        int sentCount = 0;
        
        try (Connection connection = new DatabaseManager().getConnection()) {
            // 获取用户的所有好友
            FriendshipDAO friendshipDAO = new FriendshipDAO();
            List<FriendshipDAO.Friendship> friendships;
            try {
                friendships = friendshipDAO.getUserFriends(Integer.parseInt(userId), connection);
            } catch (Exception e) {
                // 如果表不存在，返回空列表
                if (e.getMessage().contains("doesn't exist")) {
                    System.err.println("Friendships表不存在，跳过好友状态通知");
                    return 0;
                }
                throw e;
            }
            
            System.out.println("用户" + username + "的好友数量: " + friendships.size());
            
            // 为每个好友创建并发送状态更新消息
            MessageCodec messageCodec = new MessageCodec();
            
            for (FriendshipDAO.Friendship friendship : friendships) {
                if (friendship == null) continue;
                
                // 确定好友的用户名
                String friendUsername = null;
                if (friendship.user1Username != null && !friendship.user1Username.equals(username)) {
                    friendUsername = friendship.user1Username;
                } else if (friendship.user2Username != null && !friendship.user2Username.equals(username)) {
                    friendUsername = friendship.user2Username;
                }
                
                if (friendUsername == null) continue;
                
                System.out.println("处理好友: " + friendUsername);
                
                // 查找好友的在线会话
                Session friendSession = getSessionByUsername(friendUsername);
                if (friendSession != null && friendSession.isActive()) {
                    // 创建状态更新消息
                    Map<String, Object> statusData = new HashMap<>();
                    statusData.put("username", username);
                    statusData.put("status", newStatus);
                    statusData.put("isOnline", !"OFFLINE".equals(newStatus));
                    
                    // 将状态数据转换为JSON字符串
                    String statusJson = null;
                    try {
                        // 使用Gson库将Map转换为JSON字符串
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        statusJson = gson.toJson(statusData);
                    } catch (Exception e) {
                        System.err.println("转换状态数据为JSON失败: " + e.getMessage());
                        continue;
                    }
                    
                    Message statusMessage = new Message(
                        MessageType.USER_STATUS_UPDATE,
                        username,
                        statusJson,
                        null
                    );
                    
                    // 编码并发送消息
                    String encodedMessage = messageCodec.encode(statusMessage);
                    friendSession.getClientConnection().send(encodedMessage);
                    sentCount++;
                    
                    System.out.println("向好友" + friendUsername + "发送状态更新通知: " + username + " 现在 " + newStatus);
                }
            }
            
        } catch (Exception e) {
            System.err.println("向好友发送状态更新通知失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("成功向" + sentCount + "个好友发送状态更新通知");
        return sentCount;
    }
}
