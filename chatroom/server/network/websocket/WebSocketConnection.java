package server.network.websocket;
import org.java_websocket.WebSocket;
import server.message.Message;
import server.message.MessageCodec;
import server.message.MessageType;
import server.network.router.MessageRouter;
import server.network.session.Session;
import server.sql.DatabaseManager;
import server.sql.room.RoomDAO;
import server.sql.user.UserDAO;
import server.sql.user.uuid.UUIDGenerator;
import server.sql.message.MessageDAO;
import server.room.PrivateRoom;
import server.room.PublicRoom;
import server.room.Room;
import server.user.User;
import server.util.AESUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
    private RoomDAO roomDAO;
    private Session currentSession;
    
    public WebSocketConnection(WebSocket conn, MessageRouter messageRouter) {
        this.conn = conn;
        this.clientAddress = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        this.clientPort = conn.getRemoteSocketAddress().getPort();
        this.isConnected = true;
        this.isAuthenticated = false;
        this.messageRouter = messageRouter;
        this.messageCodec = new MessageCodec();
        this.dbManager = new DatabaseManager();
        this.userDAO = new UserDAO();
        this.roomDAO = new RoomDAO(messageRouter);
    }
    
    public void onOpen() {
        System.out.println("WebSocket连接已打开: " + clientAddress + ":" + clientPort);
    }
    
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("WebSocket连接已关闭: " + clientAddress + ":" + clientPort + ", 代码: " + code + ", 原因: " + reason);
        isConnected = false;
        
        // 注销会话
        if (isAuthenticated && currentUser != null) {
            String userId = String.valueOf(currentUser.getId());
            messageRouter.deregisterSession(userId);
        }
    }
    
    public void onMessage(String message) {
        System.out.println("收到WebSocket消息: " + message);
        
        try {
            // 解码消息（java-websocket库已经确保消息是UTF-8编码）
            Message decodedMessage = messageCodec.decode(message);
            if (decodedMessage == null) {
                System.err.println("消息解码失败");
                return;
            }
            
            // 处理消息
            processMessage(decodedMessage);
        } catch (Exception e) {
            System.err.println("处理WebSocket消息时发生错误: " + e.getMessage());
            e.printStackTrace();
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
                case REQUEST_HISTORY:
                    // 已认证，处理请求历史消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestHistory(message);
                    break;
                case REQUEST_LATEST_TIMESTAMP:
                    // 已认证，处理请求最新时间戳
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestLatestTimestamp(message);
                    break;
                case REQUEST_TOKEN:
                    // 已认证，处理请求上传 token
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestToken(message);
                    break;
                case REQUEST_PRIVATE_USERS:
                    // 已认证，处理请求私聊用户列表
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestPrivateUsers(message);
                    break;
                case IMAGE:
                    // 已认证，处理图片消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleImageMessage(message);
                    break;
                case TEXT:
                    // 已认证，处理文本消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    
                    String from = currentUser.getUsername();
                    String to = message.getTo();
                    String content = message.getContent();
                    
                    System.out.println("处理文本消息: 从" + from + "到" + to + "的消息: " + content);
                    
                    // 检查是否为私人消息（消息接收者是用户名且内容包含房间信息）
                    if (content.startsWith("[room:")) {
                        // 解析房间信息
                        int roomStart = content.indexOf("[room:") + 6;
                        int roomEnd = content.indexOf("]");
                        String roomName = content.substring(roomStart, roomEnd);
                        String actualContent = content.substring(roomEnd + 1);
                        
                        System.out.println("解析私人消息: 房间=" + roomName + ", 实际内容=" + actualContent);
                        
                        // 检查房间
                        if ("system".equals(roomName)) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "在system房间中禁止发送私人消息");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        // 查找房间
                        String roomId = null;
                        boolean isPublicRoom = false;
                        for (String rId : messageRouter.getRooms().keySet()) {
                            Room room = messageRouter.getRooms().get(rId);
                            if (room.getName().equals(roomName)) {
                                roomId = rId;
                                isPublicRoom = room instanceof PublicRoom;
                                break;
                            }
                        }
                        
                        if (roomId == null) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "房间" + roomName + "不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        // 检查是否为公共房间
                        if (!isPublicRoom) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "在私人房间中禁止发送私人消息");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        // 查找接收者用户ID
                        String recipientId = null;
                        for (Session session : messageRouter.getSessions().values()) {
                            if (session.getUsername().equals(to)) {
                                recipientId = session.getUserId();
                                break;
                            }
                        }
                        
                        // 检查发送者和接收者是否在同一房间
                        boolean senderInRoom = false;
                        boolean recipientInRoom = false;
                        Room room = messageRouter.getRooms().get(roomId);
                        
                        // 检查发送者是否在房间中
                        if (room.getUserIds().contains(String.valueOf(currentUser.getId()))) {
                            senderInRoom = true;
                        }
                        
                        // 检查接收者是否在房间中
                        if (recipientId != null && room.getUserIds().contains(recipientId)) {
                            recipientInRoom = true;
                        }
                        
                        if (!senderInRoom) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "您不在房间" + roomName + "中");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        // 保存私人消息到数据库（即使接收者不在线也要保存）
                        Message privateMsg = new Message(MessageType.TEXT, from, to, actualContent);
                        try (Connection connection = dbManager.getConnection()) {
                            MessageDAO messageDAO = new MessageDAO();
                            messageDAO.saveMessage(privateMsg, "PRIVATE", connection);
                            System.out.println("私人消息已保存到数据库: 从" + from + "到" + to + "的消息: " + actualContent);
                        } catch (SQLException e) {
                            System.err.println("保存私人消息到数据库失败: " + e.getMessage());
                            e.printStackTrace();
                        }
                        
                        // 如果接收者不在线，通知发送者
                        if (recipientId == null) {
                            Message infoMsg = new Message(MessageType.SYSTEM, "server", from, "消息已发送，但用户" + to + "当前不在线，上线后将收到消息");
                            send(messageCodec.encode(infoMsg));
                            break;
                        }
                        
                        if (!recipientInRoom) {
                            Message infoMsg = new Message(MessageType.SYSTEM, "server", from, "消息已发送，但用户" + to + "不在房间" + roomName + "中，上线后将收到消息");
                            send(messageCodec.encode(infoMsg));
                            break;
                        }
                        
                        // 发送私人消息
        if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(privateMsg))) {
            System.out.println("私人消息发送成功: 从" + from + "到" + to + "的消息: " + actualContent);
        } else {
            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "发送私人消息失败: 用户" + to + "可能不在线");
            send(messageCodec.encode(errorMsg));
        }
                    } else {
                        // 广播消息到目标房间
                        for (String roomId : messageRouter.getRooms().keySet()) {
                            if (message.getTo().equals(messageRouter.getRooms().get(roomId).getName())) {
                                // 创建正确的广播消息
                                Message broadcastMessage = new Message(
                                    MessageType.TEXT,
                                    from, // 使用认证后的用户名
                                    message.getTo(),
                                    content,
                                    message.getTime()
                                );
                                // 广播消息，排除发送者
                                messageRouter.broadcastToRoom(roomId, messageCodec.encode(broadcastMessage), String.valueOf(currentUser.getId()));
                                
                                // 保存房间消息到数据库
                                try (Connection connection = dbManager.getConnection()) {
                                    MessageDAO messageDAO = new MessageDAO();
                                    messageDAO.saveMessage(broadcastMessage, "ROOM", connection);
                                } catch (SQLException e) {
                                    System.err.println("保存房间消息到数据库失败: " + e.getMessage());
                                    e.printStackTrace();
                                }
                                break;
                            }
                        }
                    }
                    break;
                    
                case JOIN:
                    // 已认证，处理加入房间消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理加入房间消息: " + currentUser.getUsername() + "加入" + message.getTo() + "房间");
                    // 将用户加入目标房间
                    try (Connection connection = dbManager.getConnection()) {
                        String roomName = message.getTo();
                        String userId = String.valueOf(currentUser.getId());
                        
                        // 查找messageRouter中的房间
                        String roomId = null;
                        for (String rId : messageRouter.getRooms().keySet()) {
                            if (roomName.equals(messageRouter.getRooms().get(rId).getName())) {
                                roomId = rId;
                                break;
                            }
                        }
                        
                        if (roomId == null) {
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间" + roomName + "不存在");
                            send(messageCodec.encode(systemMessage));
                            break;
                        }
                        
                        // 检查用户是否已在房间中（数据库层面）
                        boolean alreadyInRoom = roomDAO.isUserInRoom(roomId, userId, connection);
                        if (alreadyInRoom) {
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "您已在房间" + roomName + "中");
                            send(messageCodec.encode(systemMessage));
                        }
                        
                        // 加入房间（内存层面）
                        messageRouter.joinRoom(userId, roomId);
                        
                        // 只在用户不在房间时才将用户加入到room_member表
                        if (!alreadyInRoom) {
                            roomDAO.joinRoom(roomId, userId, connection);
                        }
                        
                        // 不再发送多余的成功消息，因为MessageRouter已经广播了JOIN消息
                        
                        System.out.println("用户加入房间成功: " + currentUser.getUsername() + " 加入 " + roomName + " (ID: " + roomId + ")");
                    } catch (SQLException e) {
                        System.err.println("加入房间失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "加入房间失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                    
                case LEAVE:
                    // 已认证，处理离开房间消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理离开房间消息: " + currentUser.getUsername() + "离开" + message.getTo() + "房间");
                    // 将用户离开目标房间
                    for (String roomId : messageRouter.getRooms().keySet()) {
                        if (message.getTo().equals(messageRouter.getRooms().get(roomId).getName())) {
                            // 离开房间
                            String userId = String.valueOf(currentUser.getId());
                            messageRouter.leaveRoom(userId, roomId);
                            // 创建正确的离开消息
                            Message leaveMessage = new Message(
                                MessageType.LEAVE,
                                currentUser.getUsername(),
                                message.getTo(),
                                null,
                                null
                            );
                            // 广播离开消息
                            messageRouter.broadcastToRoom(roomId, messageCodec.encode(leaveMessage));
                            break;
                        }
                    }
                    break;
                    
                case CREATE_ROOM:
                    // 已认证，处理创建房间消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理创建房间消息: " + message.getFrom() + "创建" + message.getTo() + "房间，类型: " + message.getContent());
                    try (Connection connection = dbManager.getConnection()) {
                        String roomName = message.getTo();
                        String roomType = message.getContent().toUpperCase();
                        
                        // 检查房间是否已存在
                        if (roomDAO.roomExists(roomName, connection)) {
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "房间" + roomName + "已存在");
                            send(messageCodec.encode(systemMessage));
                            break;
                        }
                        
                        // 创建新房间
                        Room newRoom;
                        if ("PRIVATE".equals(roomType)) {
                            newRoom = new PrivateRoom(roomName, null, messageRouter);
                            roomDAO.insertPrivateRoom((PrivateRoom) newRoom, connection);
                        } else {
                            newRoom = new PublicRoom(roomName, null, messageRouter);
                            roomDAO.insertPublicRoom((PublicRoom) newRoom, connection);
                        }
                        
                        // 添加房间到消息路由器
                        messageRouter.addRoom(newRoom);
                        
                        // 发送成功消息
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "房间" + roomName + "创建成功，类型: " + roomType);
                        send(messageCodec.encode(systemMessage));
                        
                        System.out.println("房间创建成功: " + roomName + " (ID: " + newRoom.getId() + ", 类型: " + roomType + ")");
                    } catch (SQLException e) {
                        System.err.println("创建房间失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "创建房间失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                    
                case EXIT_ROOM:
                    // 已认证，处理退出房间消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理退出房间消息: " + currentUser.getUsername() + "退出" + message.getTo() + "房间");
                    try (Connection connection = dbManager.getConnection()) {
                        // 查找房间
                        Room room = roomDAO.getRoomByName(message.getTo(), connection);
                        if (room == null) {
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间" + message.getTo() + "不存在");
                            send(messageCodec.encode(systemMessage));
                            break;
                        }
                        
                        String roomId = room.getId();
                        String userId = String.valueOf(currentUser.getId());
                        
                        // 从消息路由器中移除用户
                        messageRouter.leaveRoom(userId, roomId);
                        
                        // 从数据库中删除room_member记录
                        roomDAO.leaveRoom(roomId, userId, connection);
                        
                        // 创建正确的退出消息
                        Message exitMessage = new Message(
                            MessageType.EXIT_ROOM,
                            currentUser.getUsername(),
                            message.getTo(),
                            null,
                            null
                        );
                        // 广播退出消息
                        messageRouter.broadcastToRoom(roomId, messageCodec.encode(exitMessage));
                        
                        // 发送成功消息给用户
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "已退出房间: " + message.getTo());
                        send(messageCodec.encode(systemMessage));
                        
                        System.out.println("用户退出房间成功: " + currentUser.getUsername() + " 离开 " + message.getTo());
                    } catch (SQLException e) {
                        System.err.println("退出房间失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "退出房间失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                    
                case LIST_ROOMS:
                    // 已认证，处理房间列表请求
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理房间列表请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        // 查询用户所在的所有房间及其类型
                        String sql = "SELECT r.room_name, r.room_type FROM room r JOIN room_member rm ON r.id = rm.room_id WHERE rm.user_id = ?";
                        try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setInt(1, currentUser.getId());
                            
                            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                                StringBuilder roomsList = new StringBuilder("您所在的房间: ");
                                boolean first = true;
                                while (rs.next()) {
                                    if (!first) {
                                        roomsList.append(", ");
                                    }
                                    String roomName = rs.getString("room_name");
                                    String roomType = rs.getString("room_type");
                                    roomsList.append(roomName).append("#").append(roomType);
                                    first = false;
                                }
                                
                                if (first) {
                                    roomsList.append("无");
                                }
                                
                                Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), roomsList.toString());
                                send(messageCodec.encode(systemMessage));
                            }
                        }
                    } catch (SQLException e) {
                        System.err.println("获取房间列表失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "获取房间列表失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                    
                case LIST_ROOM_USERS:
                    // 已认证，处理房间用户列表请求
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理房间用户列表请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String roomName = message.getTo();
                        RoomDAO roomDAO = new RoomDAO(messageRouter);
                        
                        // 获取房间ID
                        Room room = roomDAO.getRoomByName(roomName, connection);
                        if (room == null) {
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间" + roomName + "不存在");
                            send(messageCodec.encode(systemMessage));
                            break;
                        }
                        
                        // 获取房间用户列表
                        List<Map<String, Object>> usersList = messageRouter.getRoomUsers(room.getId());
                        
                        // 构建JSON响应
                        StringBuilder response = new StringBuilder("{\"users\":[");
                        boolean first = true;
                        for (Map<String, Object> user : usersList) {
                            if (!first) {
                                response.append(",");
                            }
                            response.append("{\"username\":\"").append(user.get("username")).append("\",\"isOnline\":");
                            response.append(user.get("isOnline")).append("}");
                            first = false;
                        }
                        response.append("]}");
                        
                        // 发送响应，to字段设置为请求用户的用户名，确保只发送给请求者
                        Message usersMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), response.toString());
                        send(messageCodec.encode(usersMessage));
                    } catch (Exception e) {
                        System.err.println("获取房间用户列表失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取房间用户列表失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                    
                default:
                    // 已认证，处理其他消息类型
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理其他消息类型: " + message.getType());
                    // 暂时回显消息
                    send(messageCodec.encode(message));
                    break;
            }
        } catch (Exception e) {
            System.err.println("处理消息失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("服务器内部错误");
        }
    }
    
    /**
     * 处理用户注册请求
     * @param message 注册消息
     */
    private void handleRegister(Message message) {
        try (Connection connection = dbManager.getConnection()) {
            // 解析注册信息（格式：username:password）
            String[] parts = message.getContent().split(":");
            if (parts.length != 2) {
                sendAuthFailure("注册信息格式错误");
                return;
            }
            
            String username = parts[0];
            String password = parts[1];
            
            // 检查用户名是否已存在
            if (userDAO.getUserIdByUsername(username, connection) != null) {
                sendAuthFailure("用户名已存在");
                return;
            }
            
            // 创建用户对象
            User newUser = new User(0, username, password, null, null);
            
            // 插入用户到数据库
            userDAO.insertUser(newUser, connection);
            
            // 获取生成的用户ID
            int userId = userDAO.getUserIdByUsername(username, connection);
            if (userId == 0) {
                sendAuthFailure("注册失败，无法获取用户ID");
                return;
            }
            
            // 生成并插入UUID
            String uuid = UUIDGenerator.generateAndInsertUUID(userId, connection);
            
            // 构造注册成功消息，to字段设置为实际的用户名
            Message authSuccessMessage = new Message(
                MessageType.AUTH_SUCCESS,
                "server",
                username,  // 使用实际的用户名而不是message.getFrom()
                uuid,
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            currentUser = userDAO.getUserByUsername(username, connection);
            
            System.out.println("用户注册成功: " + username + " (ID: " + userId + ")");
            
            // 创建并注册会话
            createAndRegisterSession();
            
        } catch (SQLException e) {
            System.err.println("注册失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("注册失败，数据库错误");
        } catch (Exception e) {
            System.err.println("注册处理失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("注册失败，服务器内部错误");
        }
    }
    
    /**
     * 处理用户登录请求
     * @param message 登录消息
     */
    private void handleLogin(Message message) {
        try (Connection connection = dbManager.getConnection()) {
            // 解析登录信息（格式：username:password）
            String[] parts = message.getContent().split(":");
            if (parts.length != 2) {
                sendAuthFailure("登录信息格式错误");
                return;
            }
            
            String username = parts[0];
            String password = parts[1];
            
            // 验证用户名和密码
            if (!userDAO.validateUser(username, password, connection)) {
                sendAuthFailure("用户名或密码错误");
                return;
            }
            
            // 获取用户信息
            currentUser = userDAO.getUserByUsername(username, connection);
            if (currentUser == null) {
                sendAuthFailure("获取用户信息失败");
                return;
            }
            
            // 生成或获取UUID
            String uuid = currentUser.getUuid();
            if (uuid == null) {
                // 如果没有UUID，生成一个
                uuid = UUIDGenerator.generateAndInsertUUID(currentUser.getId(), connection);
                currentUser.setUuid(uuid);
            }
            
            // 检查用户名是否已经登录
            Session existingSession = messageRouter.getSessionByUsername(currentUser.getUsername());
            if (existingSession != null && existingSession.isActive()) {
                // 用户名已经登录，拒绝新登录
                Message authFailureMessage = new Message(
                    MessageType.AUTH_FAILURE,
                    "server",
                    message.getFrom(),
                    "该用户名已在其他地方登录",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                System.out.println("用户登录失败，用户名已登录: " + currentUser.getUsername());
                return;
            }
            
            // 构造登录成功消息
            Message authSuccessMessage = new Message(
                MessageType.AUTH_SUCCESS,
                "server",
                username,  // 使用正确的用户名作为to字段
                uuid,
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            
            System.out.println("用户登录成功: " + username + " (ID: " + currentUser.getId() + ")");
            
            // 创建并注册会话
            createAndRegisterSession();
            
        } catch (SQLException e) {
            System.err.println("登录失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("登录失败，数据库错误");
        } catch (Exception e) {
            System.err.println("登录处理失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("登录失败，服务器内部错误");
        }
    }
    
    /**
     * 处理UUID认证请求
     * @param message UUID认证消息
     */
    private void handleUUIDAuth(Message message) {
        try (Connection connection = dbManager.getConnection()) {
            // 获取UUID
            String uuid = message.getContent();
            
            // 验证UUID
            Integer userId = UUIDGenerator.validateUUID(uuid, connection);
            if (userId == null) {
                // UUID无效
                Message authFailureMessage = new Message(
                    MessageType.UUID_AUTH_FAILURE,
                    "server",
                    message.getFrom(),
                    "无效的UUID",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                return;
            }
            
            // 获取用户信息
            User user = null;
            if (message.getFrom() != null && !message.getFrom().equals("undefined") && !message.getFrom().equals("unknown")) {
                user = userDAO.getUserByUsername(message.getFrom(), connection);
            } else {
                // 如果没有提供用户名，通过SQL查询获取用户信息
                String sql = "SELECT * FROM user WHERE id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        String username = rs.getString("username");
                        String password = rs.getString("password");
                        String createdAt = rs.getString("created_at");
                        // 创建User对象
                        user = new User(userId, username, password, createdAt, null);
                    }
                    rs.close();
                }
            }
            
            if (user == null || user.getId() != userId) {
                // 用户信息不匹配
                Message authFailureMessage = new Message(
                    MessageType.UUID_AUTH_FAILURE,
                    "server",
                    message.getFrom(),
                    "用户信息不匹配",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                return;
            }
            
            // 检查用户名是否已经登录
            String username = user.getUsername();
            if (username != null && !username.isEmpty()) {
                Session existingSession = messageRouter.getSessionByUsername(username);
                if (existingSession != null && existingSession.isActive()) {
                    // 用户名已经登录
                    Message authFailureMessage = new Message(
                        MessageType.UUID_AUTH_FAILURE,
                        "server",
                        message.getFrom(),
                        "该用户名已在其他地方登录",
                        null
                    );
                    send(messageCodec.encode(authFailureMessage));
                    System.out.println("用户UUID认证失败，用户名已登录: " + username);
                    return;
                }
            }
            
            // 构造UUID认证成功消息
            Message authSuccessMessage = new Message(
                MessageType.UUID_AUTH_SUCCESS,
                "server",
                message.getFrom(),
                "认证成功",
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            currentUser = user;
            
            System.out.println("用户UUID认证成功: " + user.getUsername() + " (ID: " + user.getId() + ")");
            
            // 创建并注册会话
            createAndRegisterSession();
            
        } catch (SQLException e) {
            System.err.println("UUID认证失败: " + e.getMessage());
            e.printStackTrace();
            Message authFailureMessage = new Message(
                MessageType.UUID_AUTH_FAILURE,
                "server",
                message.getFrom(),
                "认证失败，数据库错误",
                null
            );
            send(messageCodec.encode(authFailureMessage));
        } catch (Exception e) {
            System.err.println("UUID认证处理失败: " + e.getMessage());
            e.printStackTrace();
            Message authFailureMessage = new Message(
                MessageType.UUID_AUTH_FAILURE,
                "server",
                message.getFrom(),
                "认证失败，服务器内部错误",
                null
            );
            send(messageCodec.encode(authFailureMessage));
        }
    }
    
    /**
     * 处理上传 token 请求
     * @param message 请求消息
     */
    private void handleRequestToken(Message message) {
        String username = currentUser.getUsername();
        System.out.println("处理上传 token 请求: 用户 " + username);
        
        try {
            server.zfile.ZFileTokenManager tokenManager = server.zfile.ZFileTokenManager.getInstance();
            String uploadToken = tokenManager.generateUploadToken(username);
            String zfileServerUrl = tokenManager.getZfileServerUrl();
            
            String tokenInfo = uploadToken + "|" + zfileServerUrl;
            
            Message tokenResponse = new Message(
                MessageType.TOKEN_RESPONSE,
                "server",
                username,
                tokenInfo,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
            );
            
            send(messageCodec.encode(tokenResponse));
            System.out.println("发送上传 token 响应: " + uploadToken);
        } catch (Exception e) {
            System.err.println("生成上传 token 失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(
                MessageType.SYSTEM,
                "server",
                username,
                "获取上传 token 失败: " + e.getMessage(),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
            );
            send(messageCodec.encode(errorMsg));
        }
    }
    
    /**
     * 处理图片消息
     * @param message 图片消息
     */
    private void handleImageMessage(Message message) {
        String from = currentUser.getUsername();
        String to = message.getTo();
        String imageUrl = message.getContent();
        boolean isNSFW = message.isNSFW();
        String iv = message.getIv();
        
        System.out.println("处理图片消息: 从" + from + "到" + to + "的图片: " + imageUrl + ", NSFW: " + isNSFW);
        
        if (isNSFW && iv != null && !iv.isEmpty()) {
            try {
                logNSFWImageAudit(from, to, imageUrl, "NSFW图片已标记，服务器已记录用于审核");
                System.out.println("NSFW图片审核日志已记录: 发送者=" + from + ", 接收者=" + to);
            } catch (Exception e) {
                System.err.println("记录NSFW审核日志失败: " + e.getMessage());
            }
        }
        
        Message imageMessage = new Message(
            MessageType.IMAGE,
            from,
            to,
            imageUrl,
            message.getTime(),
            isNSFW,
            iv
        );
        
        boolean isPrivateChat = isPrivateChat(to);
        
        if (isPrivateChat) {
            String recipientId = null;
            for (Session session : messageRouter.getSessions().values()) {
                if (session.getUsername().equals(to)) {
                    recipientId = session.getUserId();
                    break;
                }
            }
            
            if (recipientId != null) {
                if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(imageMessage))) {
                    try (Connection connection = dbManager.getConnection()) {
                        MessageDAO messageDAO = new MessageDAO();
                        messageDAO.saveMessage(imageMessage, "PRIVATE", connection);
                    } catch (SQLException e) {
                        System.err.println("保存私聊图片消息到数据库失败: " + e.getMessage());
                    }
                }
            }
        } else {
            for (String roomId : messageRouter.getRooms().keySet()) {
                if (to.equals(messageRouter.getRooms().get(roomId).getName())) {
                    messageRouter.broadcastToRoom(roomId, messageCodec.encode(imageMessage), String.valueOf(currentUser.getId()));
                    
                    try (Connection connection = dbManager.getConnection()) {
                        MessageDAO messageDAO = new MessageDAO();
                        messageDAO.saveMessage(imageMessage, "ROOM", connection);
                    } catch (SQLException e) {
                        System.err.println("保存房间图片消息到数据库失败: " + e.getMessage());
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * 发送认证失败消息
     * @param reason 失败原因
     */
    private void sendAuthFailure(String reason) {
        Message authFailureMessage = new Message(
            MessageType.AUTH_FAILURE,
            "server",
            "client",
            reason,
            null
        );
        send(messageCodec.encode(authFailureMessage));
    }
    
    public synchronized void send(String message) {
        if (!isConnected || conn == null || !conn.isOpen()) {
            System.err.println("尝试向已关闭的WebSocket连接发送消息");
            return;
        }
        
        try {
            // 确保消息是有效的JSON格式
            if (!message.trim().startsWith("{")) {
                System.err.println("尝试发送非JSON格式的消息: " + message);
                return;
            }
            
            conn.send(message);
            
            // 优化日志输出：对于HISTORY_RESPONSE消息，简化输出
            String logMessage = message;
            if (message.contains("\"type\":\"HISTORY_RESPONSE\"")) {
                try {
                    com.google.gson.JsonObject jsonMsg = com.google.gson.JsonParser.parseString(message).getAsJsonObject();
                    if (jsonMsg.has("content") && jsonMsg.get("content").isJsonPrimitive()) {
                        String content = jsonMsg.get("content").getAsString();
                        // 尝试解析content字段中的JSON数组
                        com.google.gson.JsonArray jsonArray = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
                        // 只输出消息数量，不输出完整内容
                        logMessage = "{\"type\":\"HISTORY_RESPONSE\",\"from\":\"" + jsonMsg.get("from").getAsString() + "\",\"to\":\"" + jsonMsg.get("to").getAsString() + "\",\"content\":[... " + jsonArray.size() + " messages ...],\"time\":\"" + jsonMsg.get("time").getAsString() + "\"}";
                    }
                } catch (Exception e) {
                    // 如果解析失败，使用原始消息
                    logMessage = message;
                }
            }
            
            System.out.println("消息已发送到WebSocket客户端: " + clientAddress + ":" + clientPort + ": " + logMessage);
        } catch (Exception e) {
            System.err.println("发送WebSocket消息失败: " + e.getMessage());
            e.printStackTrace();
            isConnected = false;
        }
    }
    
    /**
     * 创建并注册会话，将用户加入system房间
     */
    private void createAndRegisterSession() {
        if (currentUser == null || messageRouter == null) {
            System.err.println("无法创建会话：用户或消息路由器为空");
            return;
        }
        
        // 创建会话
        String userId = String.valueOf(currentUser.getId());
        WebSocketClientConnectionAdapter connectionAdapter = null;
        
        try {
            connectionAdapter = new WebSocketClientConnectionAdapter(this);
        } catch (IOException e) {
            System.err.println("创建WebSocket连接适配器失败: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // 检查是否已经存在会话
        currentSession = messageRouter.getSession(userId);
        if (currentSession != null) {
            // 如果会话存在，只更新客户端连接
            System.out.println("会话已存在，更新客户端连接: 用户ID=" + userId);
            currentSession.setClientConnection(connectionAdapter);
            currentSession.setActive(true); // 确保会话是活动状态
        } else {
            // 如果会话不存在，创建新会话
            currentSession = new Session(userId, currentUser.getUsername(), connectionAdapter);
            // 注册会话到消息路由器
            boolean registered = messageRouter.registerSession(currentSession);
            
            if (registered) {
                // 查找system房间
                for (String roomId : messageRouter.getRooms().keySet()) {
                    if ("system".equals(messageRouter.getRooms().get(roomId).getName())) {
                        // 加入system房间
                        messageRouter.joinRoom(userId, roomId);
                        System.out.println("用户已加入system房间：" + currentUser.getUsername());
                        break;
                    }
                }
            } else {
                System.err.println("注册会话失败: 用户\"" + currentUser.getUsername() + "\"可能已在其他地方登录");
                // 发送登录失败消息
                Message authFailureMessage = new Message(
                    MessageType.AUTH_FAILURE,
                    "server",
                    currentUser.getUsername(),
                    "该用户名已在其他地方登录",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                isAuthenticated = false;
                currentUser = null;
                currentSession = null;
                return;
            }
        }
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    public User getCurrentUser() {
        return currentUser;
    }
    
    public server.network.router.MessageRouter getMessageRouter() {
        return messageRouter;
    }
    
    /**
     * 处理请求历史消息
     * @param message 请求历史消息
     */
    private void handleRequestHistory(Message message) {
        String from = message.getFrom();
        String to = message.getTo();
        String lastTimestamp = message.getContent();
        
        System.out.println("处理历史消息请求: 从" + from + "到" + to + "的消息，最后时间戳: " + lastTimestamp);
        
        try (java.sql.Connection connection = dbManager.getConnection()) {
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            java.util.List<Message> messages;
            
            // 判断时间戳是否有效（非空、非"null"、非"0"）
            boolean isValidTimestamp = lastTimestamp != null && 
                                   !lastTimestamp.isEmpty() && 
                                   !"null".equals(lastTimestamp) && 
                                   !"0".equals(lastTimestamp);
            
            // 判断是私聊还是群聊
            if (isPrivateChat(to)) {
                // 私聊：获取与指定用户的消息
                if (isValidTimestamp) {
                    // 如果提供了有效时间戳，获取该时间戳之后的消息
                    messages = messageDAO.getPrivateMessagesAfter(from, to, lastTimestamp, 100, connection);
                    System.out.println("获取私聊增量消息: " + from + "和" + to + "之间" + lastTimestamp + "之后的" + messages.size() + "条消息");
                } else {
                    // 否则获取最近100条消息
                    messages = messageDAO.getPrivateMessages(from, to, 100, connection);
                    System.out.println("获取私聊历史消息: " + from + "和" + to + "之间的" + messages.size() + "条消息");
                }
            } else {
                // 群聊：获取指定房间的消息
                if (isValidTimestamp) {
                    // 如果提供了有效时间戳，获取该时间戳之后的消息
                    messages = messageDAO.getRoomMessagesAfter(to, lastTimestamp, 100, connection);
                    System.out.println("获取群聊增量消息: " + to + "房间" + lastTimestamp + "之后的" + messages.size() + "条消息");
                } else {
                    // 否则获取最近100条消息
                    messages = messageDAO.getRoomMessages(to, 100, connection);
                    System.out.println("获取群聊历史消息: " + to + "房间的" + messages.size() + "条消息");
                }
            }
            
            // 创建历史消息响应
            String messagesJson = messageCodec.encodeMessages(messages);
            Message historyResponseMsg = new Message(MessageType.HISTORY_RESPONSE, "server", to, messagesJson);
            
            // 发送响应
            send(messageCodec.encode(historyResponseMsg));
            System.out.println("发送历史消息响应: " + to + "的" + messages.size() + "条消息");
        } catch (java.sql.SQLException e) {
            System.err.println("获取历史消息失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "获取历史消息失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    /**
     * 判断是否为私聊
     * @param targetName 目标名称（用户名或房间名）
     * @return true表示是私聊，false表示是群聊
     */
    private boolean isPrivateChat(String targetName) {
        // 私聊的目标是用户名，群聊的目标是房间名
        // 可以通过判断是否是已知房间名来区分
        // 也可以通过简单的判断：如果是system、create_room等系统保留名，或者以#开头的，可能是房间名
        // 这里简化处理：排除系统保留名和以#开头的，都认为是私聊
        if ("system".equals(targetName) || "create_room".equals(targetName) || 
            "join_room".equals(targetName) || targetName.startsWith("#")) {
            return false;
        }
        
        // 进一步检查是否是已知的房间名
        try (java.sql.Connection connection = dbManager.getConnection()) {
            server.sql.room.RoomDAO roomDAO = new server.sql.room.RoomDAO(messageRouter);
            return !roomDAO.roomExists(targetName, connection);
        } catch (java.sql.SQLException e) {
            System.err.println("检查是否为私聊时发生错误: " + e.getMessage());
            // 异常情况下，假设是私聊（更安全的选择）
            return true;
        }
    }
    
    /**
     * 记录NSFW图片审核日志
     * @param from 发送者
     * @param to 接收者
     * @param imageUrl 图片URL
     * @param note 备注
     */
    private void logNSFWImageAudit(String from, String to, String imageUrl, String note) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);
        
        String logEntry = String.format(
            "[%s] NSFW图片审核 - 发送者: %s, 接收者: %s, 图片URL: %s, 备注: %s",
            timestamp, from, to, imageUrl, note
        );
        
        System.out.println("========================================");
        System.out.println("NSFW图片审核日志");
        System.out.println("========================================");
        System.out.println(logEntry);
        System.out.println("警告：此内容已标记为NSFW，服务器已记录用于审核");
        System.out.println("禁止内容：未成年内容、非自愿内容、非法内容");
        System.out.println("========================================");
    }
    
    /**
     * 处理请求最新时间戳
     * @param message 请求最新时间戳
     */
    private void handleRequestLatestTimestamp(Message message) {
        String from = message.getFrom();
        String roomName = message.getTo();
        
        System.out.println("处理最新时间戳请求: 从" + from + "到" + roomName + "的消息");
        
        try (java.sql.Connection connection = dbManager.getConnection()) {
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            String latestTimestamp;
            
            // 判断是私聊还是群聊
            if (isPrivateChat(roomName)) {
                // 私聊：获取两个用户之间的最新消息时间戳
                latestTimestamp = messageDAO.getLatestPrivateTimestamp(from, roomName, connection);
                System.out.println("获取私聊最新时间戳: " + from + "和" + roomName + "之间的最新时间戳: " + latestTimestamp);
            } else {
                // 群聊：获取房间的最新消息时间戳
                latestTimestamp = messageDAO.getLatestRoomTimestamp(roomName, connection);
                System.out.println("获取群聊最新时间戳: " + roomName + "房间的最新时间戳: " + latestTimestamp);
            }
            
            // 如果没有消息，返回当前时间
            if (latestTimestamp == null) {
                latestTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            }
            
            // 创建最新时间戳响应
            Message latestTimestampMsg = new Message(MessageType.LATEST_TIMESTAMP, "server", roomName, latestTimestamp);
            
            // 发送响应
            send(messageCodec.encode(latestTimestampMsg));
            System.out.println("发送最新时间戳响应: " + roomName + "的最新时间戳: " + latestTimestamp);
        } catch (java.sql.SQLException e) {
            System.err.println("获取最新时间戳失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "获取最新时间戳失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    /**
     * 处理请求私聊用户列表
     * @param message 请求消息
     */
    private void handleRequestPrivateUsers(Message message) {
        String from = message.getFrom();
        
        System.out.println("处理私聊用户列表请求: 用户" + from);
        
        try (java.sql.Connection connection = dbManager.getConnection()) {
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            java.util.List<String> users = messageDAO.getPrivateChatUsers(from, connection);
            
            // 创建用户列表JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String usersJson = gson.toJson(users);
            
            // 创建响应消息
            Message responseMsg = new Message(MessageType.PRIVATE_USERS_RESPONSE, "server", from, usersJson);
            
            // 发送响应
            send(messageCodec.encode(responseMsg));
            System.out.println("发送私聊用户列表响应: " + from + "的私聊用户数量: " + users.size());
        } catch (java.sql.SQLException e) {
            System.err.println("获取私聊用户列表失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "获取私聊用户列表失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
}