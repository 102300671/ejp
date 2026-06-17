package server.network.socket;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import server.message.*;
import server.network.router.MessageRouter;
import server.network.session.Session;
import server.sql.DatabaseManager;
import server.sql.user.UserDAO;
import server.sql.user.uuid.UUIDGenerator;
import server.sql.message.MessageDAO;
import server.sql.conversation.ConversationDAO;
import server.sql.conversation.Conversation;
import server.sql.cp.CPBindingDAO;
import server.sql.cp.CPRequestDAO;
import server.user.User;

public class ClientConnection implements Runnable {
    private final Socket clientSocket;
    private volatile boolean isConnected;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final String clientAddress;
    private final int clientPort;
    private DatabaseManager dbManager;
    private MessageCodec messageCodec;
    private UserDAO userDAO;
    private boolean isAuthenticated;
    private User currentUser;
    private MessageRouter messageRouter;
    private Session currentSession;
    
    private volatile long lastActiveTime;
    private static final long HEARTBEAT_INTERVAL = 30000;
    private static final long HEARTBEAT_TIMEOUT = 90000;
    private Thread heartbeatThread;

    public ClientConnection(Socket socket, MessageRouter messageRouter) throws IOException {
        this.clientSocket = socket;
        this.clientAddress = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
        this.isConnected = true;
        this.isAuthenticated = false;
        this.dbManager = new DatabaseManager();
        this.messageCodec = new MessageCodec();
        this.userDAO = new UserDAO();
        this.messageRouter = messageRouter;
        
        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            System.out.println("客户端连接初始化完成: " + clientAddress + ":" + clientPort);
        } catch (IOException e) {
            System.err.println("初始化客户端连接流失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            close();
            throw e;
        }
    }
    
    protected ClientConnection(MessageRouter messageRouter) {
        this.clientSocket = null;
        this.clientAddress = "websocket-client";
        this.clientPort = 0;
        this.isConnected = true;
        this.isAuthenticated = false;
        this.dbManager = new DatabaseManager();
        this.messageCodec = new MessageCodec();
        this.userDAO = new UserDAO();
        this.messageRouter = messageRouter;
        this.reader = null;
        this.writer = null;
    }

    @Override
    public void run() {
        System.out.println("开始处理客户端连接: " + clientAddress + ":" + clientPort);
        
        lastActiveTime = System.currentTimeMillis();
        startHeartbeat();
        
        try {
            while (isConnected) {
                String jsonMessage = reader.readLine();
                
                if (jsonMessage == null) {
                    System.out.println("客户端已断开连接: " + clientAddress + ":" + clientPort);
                    break;
                }
                
                System.out.println("收到客户端消息 (" + clientAddress + ":" + clientPort + "): " + jsonMessage);
                
                Message message = messageCodec.decode(jsonMessage);
                
                if (message == null) {
                    System.err.println("消息解码失败，无法处理 (" + clientAddress + ":" + clientPort + ")");
                    continue;
                }
                
                lastActiveTime = System.currentTimeMillis();
                
                processMessage(message);
            }
        } catch (SocketException e) {
            if (isConnected) {
                System.err.println("客户端Socket异常 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("客户端IO异常 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("客户端处理异常 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
        } finally {
            stopHeartbeat();
            close();
        }
    }
    
    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (isConnected) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    
                    if (!isConnected) break;
                    
                    long idleTime = System.currentTimeMillis() - lastActiveTime;
                    if (idleTime > HEARTBEAT_TIMEOUT) {
                        System.out.println("客户端心跳超时 (" + clientAddress + ":" + clientPort + "): 空闲 " + idleTime + "ms");
                        close();
                        break;
                    }
                    
                    if (isConnected && isAuthenticated) {
                        Message ping = new Message(MessageType.PING, "server", String.valueOf(System.currentTimeMillis()));
                        send(messageCodec.encode(ping));
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.setName("Heartbeat-" + clientAddress + ":" + clientPort);
        heartbeatThread.start();
    }
    
    private void stopHeartbeat() {
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }

    private void processMessage(Message message) {
        try {
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
                case PONG:
                    lastActiveTime = System.currentTimeMillis();
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
    
    private void handleTextMessage(Message message) {
        String from = currentUser.getUsername();
        String content = message.getContent();
        Integer conversationId = message.getConversationId();
        
        System.out.println("处理文本消息: 从" + from + "的消息: " + content);
        
        try (Connection connection = dbManager.getConnection()) {
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            Integer cpUserId = cpBindingDAO.getCPUserId(currentUser.getId(), connection);
            
            if (cpUserId == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", "尚未绑定CP，请先绑定", conversationId);
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            Message outgoingMessage = new Message(
                MessageType.TEXT,
                from,
                content,
                message.getTime(),
                conversationId
            );
            
            boolean sent = messageRouter.sendMessageByConversationId(conversationId, messageCodec.encode(outgoingMessage), String.valueOf(currentUser.getId()));
            
            if (sent) {
                System.out.println("消息发送成功: 从" + from);
                
                try {
                    MessageDAO messageDAO = new MessageDAO();
                    messageDAO.saveMessage(outgoingMessage, "CP", connection);
                } catch (SQLException e) {
                    System.err.println("保存消息到数据库失败: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", "发送消息失败: CP可能不在线", conversationId);
                send(messageCodec.encode(errorMsg));
            }
        } catch (SQLException e) {
            System.err.println("处理文本消息时数据库异常: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", "发送消息失败: " + e.getMessage(), conversationId);
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
            
            Message resultMsg = new Message(MessageType.SYSTEM, "server", "找到用户: " + searchUsername + ", 用户ID: " + foundUserId, null);
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
    
    private void handleRegister(Message message) {
        String content = message.getContent();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            Map<String, String> data = gson.fromJson(content, Map.class);
            
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
            Map<String, String> data = gson.fromJson(content, Map.class);
            
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
                
                currentSession = new Session(String.valueOf(user.getId()), username, this);
                messageRouter.registerSession(currentSession);
                
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
            
            // 根据用户ID获取用户名
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
            
            currentSession = new Session(String.valueOf(userId), username, this);
            messageRouter.registerSession(currentSession);
            
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
        if (!isConnected) {
            System.err.println("无法发送消息，连接已关闭");
            return;
        }
        
        try {
            if (writer != null) {
                writer.write(message + "\n");
                writer.flush();
            } else if (clientSocket != null && clientSocket.getOutputStream() != null) {
                OutputStreamWriter osw = new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8");
                osw.write(message + "\n");
                osw.flush();
            }
        } catch (IOException e) {
            System.err.println("发送消息失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void close() {
        isConnected = false;
        
        if (currentSession != null) {
            messageRouter.deregisterSession(currentSession.getUserId());
            currentSession = null;
        }
        
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("关闭读取器失败: " + e.getMessage());
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("关闭写入器失败: " + e.getMessage());
        }
        
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                System.out.println("客户端连接已关闭: " + clientAddress + ":" + clientPort);
            }
        } catch (IOException e) {
            System.err.println("关闭客户端Socket失败: " + e.getMessage());
        }
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public String getClientAddress() {
        return clientAddress;
    }
    
    public int getClientPort() {
        return clientPort;
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
}
