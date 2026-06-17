package server.network.websocket;

import org.java_websocket.WebSocket;
import server.message.Message;
import server.message.MessageCodec;
import server.message.MessageType;
import server.network.router.MessageRouter;
import server.network.session.Session;
import server.sql.DatabaseManager;
import server.sql.user.UserDAO;
import server.sql.user.uuid.UUIDGenerator;
import server.sql.message.MessageDAO;
import server.sql.conversation.Conversation;
import server.sql.conversation.ConversationDAO;
import server.sql.conversation.ConversationMember;
import server.sql.cp.CPBindingDAO;
import server.sql.cp.CPRequestDAO;
import server.user.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class WebSocketConnection {
    private final WebSocket conn;
    private final String clientAddress;
    private final int clientPort;
    private volatile boolean isConnected;
    private boolean isAuthenticated;
    private User currentUser;
    private MessageRouter messageRouter;
    private MessageCodec messageCodec;
    private DatabaseManager dbManager;
    private UserDAO userDAO;
    private Session currentSession;
    private ConversationDAO conversationDAO;
    
    private static final java.time.ZoneId BEIJING_ZONE = java.time.ZoneId.of("Asia/Shanghai");
    
    public WebSocketConnection(WebSocket conn, MessageRouter messageRouter) {
        this(conn, messageRouter, null);
    }
    
    public WebSocketConnection(WebSocket conn, MessageRouter messageRouter, WebSocketServer webSocketServer) {
        this.conn = conn;
        this.clientAddress = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        this.clientPort = conn.getRemoteSocketAddress().getPort();
        this.isConnected = true;
        this.isAuthenticated = false;
        this.messageRouter = messageRouter;
        this.messageCodec = new MessageCodec();
        this.dbManager = new DatabaseManager();
        this.userDAO = new UserDAO();
        this.conversationDAO = new ConversationDAO();
    }
    
    public MessageRouter getMessageRouter() {
        return messageRouter;
    }
    
    public void onOpen() {
        System.out.println("WebSocket连接已打开: " + clientAddress + ":" + clientPort);
    }
    
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("WebSocket连接已关闭: " + clientAddress + ":" + clientPort + ", 代码: " + code + ", 原因: " + reason);
        isConnected = false;
        
        if (isAuthenticated && currentUser != null) {
            String userId = String.valueOf(currentUser.getId());
            messageRouter.deregisterSession(userId);
            
            try (Connection connection = dbManager.getConnection()) {
                userDAO.updateUserStatusWithLogoutTime(currentUser.getId(), "OFFLINE", connection);
                System.out.println("用户状态已更新为OFFLINE: " + currentUser.getUsername());
            } catch (SQLException e) {
                System.err.println("更新用户状态失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    public void onMessage(String message) {
        System.out.println("收到WebSocket消息: " + message);
        
        try {
            Message decodedMessage = messageCodec.decode(message);
            if (decodedMessage == null) {
                System.err.println("消息解码失败");
                return;
            }
            
            processMessage(decodedMessage);
        } catch (Exception e) {
            System.err.println("处理WebSocket消息时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void processMessage(Message message) {
        try {
            if (message.getTime() != null && !isValidMessageTime(message.getTime())) {
                System.err.println("消息时间无效: " + message.getTime());
                Message errorMsg = new Message(MessageType.SYSTEM, "server", "消息时间无效，请检查您的系统时间");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            switch (message.getType()) {
                case REGISTER:
                    handleRegister(message);
                    break;
                case LOGIN:
                    handleLogin(message);
                    break;
                case UUID_AUTH:
                    handleUUIDAuth(message);
                    break;
                case REQUEST_HISTORY:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestHistory(message);
                    break;
                case TEXT:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleTextMessage(message);
                    break;
                case SEARCH_CP:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleSearchCP(message);
                    break;
                case SEND_CP_REQUEST:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleSendCPRequest(message);
                    break;
                case ACCEPT_CP_REQUEST:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleAcceptCPRequest(message);
                    break;
                case REJECT_CP_REQUEST:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRejectCPRequest(message);
                    break;
                case UNBIND_CP:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleUnbindCP(message);
                    break;
                case CHECK_CP_STATUS:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleCheckCPStatus(message);
                    break;
                default:
                    System.err.println("未知消息类型: " + message.getType());
                    break;
            }
        } catch (Exception e) {
            System.err.println("处理消息时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean isValidMessageTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return true;
        try {
            long time = Long.parseLong(timeStr);
            long now = System.currentTimeMillis();
            return Math.abs(time - now) < 300000;
        } catch (NumberFormatException e) {
            return true;
        }
    }
    
    private void handleRegister(Message message) {
        String content = message.getContent();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.Map<String, String> data = gson.fromJson(content, java.util.Map.class);
            
            String username = data.get("username");
            String password = data.get("password");
            
            System.out.println("处理注册请求: " + username);
            
            try (Connection connection = dbManager.getConnection()) {
                // 检查用户是否存在
                if (userDAO.getUserIdByUsername(username, connection) != null) {
                    Message response = new Message(MessageType.SYSTEM, "server", "用户名已存在", null);
                    send(messageCodec.encode(response));
                    return;
                }
                
                // 创建用户对象并插入
                User newUser = new User(0, username, password, null, null);
                userDAO.insertUser(newUser, connection);
                
                // 获取刚插入的用户ID并生成UUID
                int userId = userDAO.getUserIdByUsername(username, connection);
                if (userId != -1) {
                    UUIDGenerator.generateAndInsertUUID(userId, connection);
                }
                
                Message response = new Message(MessageType.SYSTEM, "server", "注册成功，请登录", null);
                send(messageCodec.encode(response));
                
                System.out.println("注册成功: " + username);
            }
        } catch (Exception e) {
            System.err.println("注册失败: " + e.getMessage());
            e.printStackTrace();
            Message response = new Message(MessageType.SYSTEM, "server", "注册失败: " + e.getMessage(), null);
            send(messageCodec.encode(response));
        }
    }
    
    private void handleLogin(Message message) {
        String content = message.getContent();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.Map<String, String> data = gson.fromJson(content, java.util.Map.class);
            
            String username = data.get("username");
            String password = data.get("password");
            
            System.out.println("处理登录请求: " + username);
            
            try (Connection connection = dbManager.getConnection()) {
                // 验证用户名和密码
                if (!userDAO.validateUser(username, password, connection)) {
                    Message response = new Message(MessageType.SYSTEM, "server", "用户名或密码错误", null);
                    send(messageCodec.encode(response));
                    return;
                }
                
                // 获取用户信息
                User user = userDAO.getUserByUsername(username, connection);
                if (user == null) {
                    Message response = new Message(MessageType.SYSTEM, "server", "用户不存在", null);
                    send(messageCodec.encode(response));
                    return;
                }
                
                currentUser = user;
                isAuthenticated = true;
                
                currentSession = new Session(String.valueOf(user.getId()), username, new WebSocketClientConnectionAdapter(this));
                messageRouter.registerSession(currentSession);
                
                userDAO.updateUserStatus(user.getId(), "ONLINE", connection);
                
                Message response = new Message(MessageType.SYSTEM, "server", "登录成功", null);
                send(messageCodec.encode(response));
                
                System.out.println("登录成功: " + username + ", 用户ID: " + user.getId());
            }
        } catch (Exception e) {
            System.err.println("登录失败: " + e.getMessage());
            e.printStackTrace();
            Message response = new Message(MessageType.SYSTEM, "server", "登录失败: " + e.getMessage(), null);
            send(messageCodec.encode(response));
        }
    }
    
    private void handleUUIDAuth(Message message) {
        String uuid = message.getContent();
        
        System.out.println("处理UUID认证请求");
        
        try (Connection connection = dbManager.getConnection()) {
            // 使用UUIDGenerator验证UUID并获取用户ID
            Integer userId = UUIDGenerator.validateUUID(uuid, connection);
            
            if (userId == null) {
                Message response = new Message(MessageType.SYSTEM, "server", "UUID认证失败", null);
                send(messageCodec.encode(response));
                return;
            }
            
            // 根据用户ID获取用户信息
            String username = userDAO.getUsernameById(userId, connection);
            if (username == null) {
                Message response = new Message(MessageType.SYSTEM, "server", "用户不存在", null);
                send(messageCodec.encode(response));
                return;
            }
            
            // 创建用户对象
            User user = new User(userId, username, null, null, uuid);
            
            currentUser = user;
            isAuthenticated = true;
            
            currentSession = new Session(String.valueOf(userId), username, new WebSocketClientConnectionAdapter(this));
            messageRouter.registerSession(currentSession);
            
            userDAO.updateUserStatus(userId, "ONLINE", connection);
            
            Message response = new Message(MessageType.SYSTEM, "server", "UUID认证成功", null);
            send(messageCodec.encode(response));
            
            System.out.println("UUID认证成功: " + username);
        } catch (Exception e) {
            System.err.println("UUID认证失败: " + e.getMessage());
            e.printStackTrace();
            Message response = new Message(MessageType.SYSTEM, "server", "UUID认证失败: " + e.getMessage(), null);
            send(messageCodec.encode(response));
        }
    }
    
    private void handleRequestHistory(Message message) {
        try {
            int conversationId = message.getConversationId();
            
            try (Connection connection = dbManager.getConnection()) {
                MessageDAO messageDAO = new MessageDAO();
                List<Message> history = messageDAO.getConversationMessages(conversationId, 100, connection);
                
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String historyJson = gson.toJson(history);
                
                Message response = new Message(MessageType.HISTORY_RESPONSE, "server", historyJson, null, conversationId);
                send(messageCodec.encode(response));
                
                System.out.println("历史消息查询成功: 会话ID=" + conversationId + ", 消息数量=" + history.size());
            }
        } catch (Exception e) {
            System.err.println("获取历史消息失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "获取历史消息失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRequestLatestTimestamp(Message message) {
        try {
            int conversationId = message.getConversationId();
            
            try (Connection connection = dbManager.getConnection()) {
                MessageDAO messageDAO = new MessageDAO();
                String latestTime = messageDAO.getLatestConversationTimestamp(conversationId, connection);
                
                Message response = new Message(MessageType.LATEST_TIMESTAMP, "server", latestTime != null ? latestTime : "", null, conversationId);
                send(messageCodec.encode(response));
            }
        } catch (Exception e) {
            System.err.println("获取最新时间戳失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "获取最新时间戳失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleTextMessage(Message message) {
        String from = currentUser.getUsername();
        String content = message.getContent();
        
        int conversationId = -1;
        String actualContent = content;
        
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (json.has("conversation_id")) {
                conversationId = json.get("conversation_id").getAsInt();
            }
            if (json.has("content")) {
                actualContent = json.get("content").getAsString();
            }
        } catch (Exception e) {
            System.err.println("消息内容不是JSON格式: " + e.getMessage());
        }
        
        if (conversationId == -1) {
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "消息缺少 conversation_id");
            send(messageCodec.encode(errorMsg));
            return;
        }
        
        System.out.println("处理文本消息: 从" + from + "到会话" + conversationId + "的消息: " + actualContent);
        
        try (Connection connection = dbManager.getConnection()) {
            Conversation conversation = conversationDAO.getConversation(conversationId, connection);
            if (conversation == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", "会话不存在");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            int fromUserId = conversationDAO.getUserIdFromUsername(from, connection);
            if (!conversationDAO.isConversationMember(conversationId, fromUserId, connection)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", "您不是该会话的成员");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            Message conversationMessage = new Message(
                MessageType.TEXT,
                from,
                actualContent,
                message.getTime(),
                conversationId
            );
            
            messageRouter.sendMessageByConversationId(conversationId, messageCodec.encode(conversationMessage), String.valueOf(currentUser.getId()));
            
            Message storageMessage = new Message(
                MessageType.TEXT,
                from,
                actualContent,
                message.getTime(),
                conversationId
            );
            MessageDAO messageDAO = new MessageDAO();
            messageDAO.saveMessage(storageMessage, "CP", conversationId, connection);
            
        } catch (SQLException e) {
            System.err.println("处理消息失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "处理消息失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleSearchCP(Message message) {
        String searchUsername = message.getContent();
        System.out.println("处理查找CP请求: " + currentUser.getUsername() + " 查找 " + searchUsername);
        
        try (Connection connection = dbManager.getConnection()) {
            Integer foundUserId = userDAO.getUserIdByUsername(searchUsername, connection);
            
            if (foundUserId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "用户不存在", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            if (foundUserId.equals(currentUser.getId())) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "不能绑定自己", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            if (cpBindingDAO.hasCP(foundUserId, connection)) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "对方已绑定CP", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            Message resultMsg = new Message(MessageType.SYSTEM, "server", "找到用户: " + searchUsername, null);
            send(messageCodec.encode(resultMsg));
            
        } catch (SQLException e) {
            System.err.println("查找CP时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "查找失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleSendCPRequest(Message message) {
        String targetUsername = message.getContent();
        System.out.println("处理发送CP请求: " + currentUser.getUsername() + " -> " + targetUsername);
        
        try (Connection connection = dbManager.getConnection()) {
            Integer targetUserId = userDAO.getUserIdByUsername(targetUsername, connection);
            
            if (targetUserId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "用户不存在", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            
            if (cpBindingDAO.hasCP(currentUser.getId(), connection)) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "您已绑定CP，无法重复绑定", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            if (cpBindingDAO.hasCP(targetUserId, connection)) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "对方已绑定CP", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            CPRequestDAO cpRequestDAO = new CPRequestDAO();
            
            if (cpRequestDAO.hasPendingRequest(currentUser.getId(), targetUserId, connection)) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "已发送过绑定请求，请等待对方确认", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            if (cpRequestDAO.hasPendingRequest(targetUserId, currentUser.getId(), connection)) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "对方已向您发送绑定请求，请先处理", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            cpRequestDAO.createCPRequest(currentUser.getId(), targetUserId, connection);
            
            Message resultMsg = new Message(MessageType.SYSTEM, "server", "绑定请求已发送，请等待对方确认", null);
            send(messageCodec.encode(resultMsg));
            
            Session targetSession = messageRouter.getSessionByUsername(targetUsername);
            if (targetSession != null && targetSession.isActive()) {
                Message notifyMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername() + " 向您发送了CP绑定请求", null);
                targetSession.getClientConnection().send(messageCodec.encode(notifyMsg));
            }
            
            System.out.println("CP绑定请求发送成功: " + currentUser.getUsername() + " -> " + targetUsername);
            
        } catch (SQLException e) {
            System.err.println("发送CP请求时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "发送请求失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleAcceptCPRequest(Message message) {
        String requesterUsername = message.getContent();
        System.out.println("处理接受CP请求: " + currentUser.getUsername() + " 接受 " + requesterUsername);
        
        try (Connection connection = dbManager.getConnection()) {
            Integer requesterUserId = userDAO.getUserIdByUsername(requesterUsername, connection);
            
            if (requesterUserId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "用户不存在", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            CPRequestDAO cpRequestDAO = new CPRequestDAO();
            Integer requestId = cpRequestDAO.getRequestId(requesterUserId, currentUser.getId(), connection);
            
            if (requestId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "未找到绑定请求", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            connection.setAutoCommit(false);
            try {
                cpRequestDAO.acceptCPRequest(requestId, connection);
                
                CPBindingDAO cpBindingDAO = new CPBindingDAO();
                Integer conversationId = cpBindingDAO.createCPConversation(requesterUserId, currentUser.getId(), connection);
                
                connection.commit();
                
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "CP绑定成功！会话ID: " + conversationId, conversationId);
                send(messageCodec.encode(resultMsg));
                
                Session requesterSession = messageRouter.getSessionByUsername(requesterUsername);
                if (requesterSession != null && requesterSession.isActive()) {
                    Message notifyMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername() + " 接受了您的CP绑定请求！会话ID: " + conversationId, conversationId);
                    requesterSession.getClientConnection().send(messageCodec.encode(notifyMsg));
                }
                
                System.out.println("CP绑定成功: " + requesterUsername + " <-> " + currentUser.getUsername());
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            System.err.println("接受CP请求时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "绑定失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRejectCPRequest(Message message) {
        String requesterUsername = message.getContent();
        System.out.println("处理拒绝CP请求: " + currentUser.getUsername() + " 拒绝 " + requesterUsername);
        
        try (Connection connection = dbManager.getConnection()) {
            Integer requesterUserId = userDAO.getUserIdByUsername(requesterUsername, connection);
            
            if (requesterUserId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "用户不存在", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            CPRequestDAO cpRequestDAO = new CPRequestDAO();
            Integer requestId = cpRequestDAO.getRequestId(requesterUserId, currentUser.getId(), connection);
            
            if (requestId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "未找到绑定请求", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            cpRequestDAO.rejectCPRequest(requestId, connection);
            
            Message resultMsg = new Message(MessageType.SYSTEM, "server", "已拒绝绑定请求", null);
            send(messageCodec.encode(resultMsg));
            
            Session requesterSession = messageRouter.getSessionByUsername(requesterUsername);
            if (requesterSession != null && requesterSession.isActive()) {
                Message notifyMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername() + " 拒绝了您的CP绑定请求", null);
                requesterSession.getClientConnection().send(messageCodec.encode(notifyMsg));
            }
            
            System.out.println("CP绑定请求已拒绝: " + requesterUsername + " -> " + currentUser.getUsername());
            
        } catch (SQLException e) {
            System.err.println("拒绝CP请求时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "拒绝失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleUnbindCP(Message message) {
        System.out.println("处理解除CP绑定: " + currentUser.getUsername());
        
        try (Connection connection = dbManager.getConnection()) {
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            Integer cpUserId = cpBindingDAO.getCPUserId(currentUser.getId(), connection);
            
            if (cpUserId == null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "尚未绑定CP", null);
                send(messageCodec.encode(resultMsg));
                return;
            }
            
            String cpUsername = userDAO.getUsernameById(cpUserId, connection);
            
            connection.setAutoCommit(false);
            try {
                cpBindingDAO.removeCPBinding(currentUser.getId(), connection);
                connection.commit();
                
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "已解除CP绑定", null);
                send(messageCodec.encode(resultMsg));
                
                Session cpSession = messageRouter.getSessionByUsername(cpUsername);
                if (cpSession != null && cpSession.isActive()) {
                    Message notifyMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername() + " 已解除CP绑定", null);
                    cpSession.getClientConnection().send(messageCodec.encode(notifyMsg));
                }
                
                System.out.println("CP绑定已解除: " + currentUser.getUsername() + " <-> " + cpUsername);
                
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            System.err.println("解除CP绑定时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "解除绑定失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleCheckCPStatus(Message message) {
        System.out.println("处理检查CP状态: " + currentUser.getUsername());
        
        try (Connection connection = dbManager.getConnection()) {
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            String cpUsername = cpBindingDAO.getCPUsername(currentUser.getId(), connection);
            
            if (cpUsername != null) {
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "已绑定CP: " + cpUsername, null);
                send(messageCodec.encode(resultMsg));
            } else {
                CPRequestDAO cpRequestDAO = new CPRequestDAO();
                
                Integer sentRequestId = cpRequestDAO.getSentRequestId(currentUser.getId(), connection);
                if (sentRequestId != null) {
                    Message resultMsg = new Message(MessageType.SYSTEM, "server", "已发送绑定请求，等待对方确认", null);
                    send(messageCodec.encode(resultMsg));
                    return;
                }
                
                Integer receivedRequestId = cpRequestDAO.getReceivedRequestId(currentUser.getId(), connection);
                if (receivedRequestId != null) {
                    String requesterUsername = cpRequestDAO.getRequesterUsername(receivedRequestId, connection);
                    Message resultMsg = new Message(MessageType.SYSTEM, "server", "收到来自 " + requesterUsername + " 的绑定请求", null);
                    send(messageCodec.encode(resultMsg));
                    return;
                }
                
                Message resultMsg = new Message(MessageType.SYSTEM, "server", "未绑定CP", null);
                send(messageCodec.encode(resultMsg));
            }
            
        } catch (SQLException e) {
            System.err.println("检查CP状态时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "检查失败: " + e.getMessage(), null);
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void sendAuthFailure(String message) {
        System.err.println("认证失败: " + message);
        try {
            Message response = new Message(MessageType.SYSTEM, "server", message, null);
            send(messageCodec.encode(response));
        } catch (Exception e) {
            System.err.println("发送认证失败消息时出错: " + e.getMessage());
        }
    }
    
    public void send(String message) {
        if (!isConnected || conn == null) {
            System.err.println("无法发送消息，连接已关闭");
            return;
        }
        
        try {
            conn.send(message);
        } catch (Exception e) {
            System.err.println("发送消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public boolean isConnected() {
        return isConnected && conn != null && conn.isOpen();
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
}
