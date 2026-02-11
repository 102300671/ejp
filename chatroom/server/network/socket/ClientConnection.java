package server.network.socket;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import server.message.*;
import server.network.router.MessageRouter;
import server.network.session.Session;
import server.sql.DatabaseManager;
import server.sql.room.RoomDAO;
import server.sql.user.UserDAO;
import server.sql.user.uuid.UUIDGenerator;
import server.sql.message.MessageDAO;
import server.room.PublicRoom;
import server.room.PrivateRoom;
import server.room.Room;
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
    private RoomDAO roomDAO;
    private boolean isAuthenticated;
    private User currentUser;
    private MessageRouter messageRouter;
    private Session currentSession;

    /**
     * TCP客户端连接构造函数
     * @param socket TCP Socket对象
     * @param messageRouter 消息路由器
     * @throws IOException IO异常
     */
    public ClientConnection(Socket socket, MessageRouter messageRouter) throws IOException {
        this.clientSocket = socket;
        this.clientAddress = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
        this.isConnected = true;
        this.isAuthenticated = false;
        this.dbManager = new DatabaseManager();
        this.messageCodec = new MessageCodec();
        this.userDAO = new UserDAO();
        this.roomDAO = new RoomDAO(messageRouter);
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
    
    /**
     * WebSocket客户端连接适配器专用构造函数
     * @param messageRouter 消息路由器
     */
    protected ClientConnection(MessageRouter messageRouter) {
        this.clientSocket = null;
        this.clientAddress = "websocket-client";
        this.clientPort = 0;
        this.isConnected = true;
        this.isAuthenticated = false;
        this.dbManager = new DatabaseManager();
        this.messageCodec = new MessageCodec();
        this.userDAO = new UserDAO();
        this.roomDAO = new RoomDAO(messageRouter);
        this.messageRouter = messageRouter;
        // WebSocket连接不需要初始化流
        this.reader = null;
        this.writer = null;
    }

    @Override
    public void run() {
        System.out.println("开始处理客户端连接: " + clientAddress + ":" + clientPort);
        
        try {
            while (isConnected) {
                String jsonMessage = reader.readLine();
                
                if (jsonMessage == null) {
                    System.out.println("客户端已断开连接: " + clientAddress + ":" + clientPort);
                    break;
                }
                
                System.out.println("收到客户端消息 (" + clientAddress + ":" + clientPort + "): " + jsonMessage);
                
                // 解码消息
                Message message = messageCodec.decode(jsonMessage);
                
                if (message == null) {
                    System.err.println("消息解码失败，无法处理 (" + clientAddress + ":" + clientPort + ")");
                    continue;
                }
                
                // 处理消息
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
            close();
        }
    }
    
    /**
     * 处理客户端消息
     * @param message 要处理的消息
     */
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
                        
                        if (recipientId == null) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "用户" + to + "不存在或不在线");
                            send(messageCodec.encode(errorMsg));
                            break;
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
                        if (room.getUserIds().contains(recipientId)) {
                            recipientInRoom = true;
                        }
                        
                        if (!senderInRoom) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "您不在房间" + roomName + "中");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        if (!recipientInRoom) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", from, "用户" + to + "不在房间" + roomName + "中");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        // 发送私人消息
                        Message privateMsg = new Message(MessageType.TEXT, from, to, actualContent);
                        if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(privateMsg))) {
                            System.out.println("私人消息发送成功: 从" + from + "到" + to + "的消息: " + actualContent);
                            
                            // 保存私人消息到数据库
                            try (Connection connection = dbManager.getConnection()) {
                                MessageDAO messageDAO = new MessageDAO();
                                messageDAO.saveMessage(privateMsg, "PRIVATE", connection);
                            } catch (SQLException e) {
                                System.err.println("保存私人消息到数据库失败: " + e.getMessage());
                                e.printStackTrace();
                            }
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
                                // 广播消息
                                messageRouter.broadcastToRoom(roomId, messageCodec.encode(broadcastMessage));
                                
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
                        Room room = messageRouter.getRooms().get(roomId);
                        System.out.println("当前房间" + roomName + "中的用户数量: " + room.getUserCount());
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
                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setInt(1, currentUser.getId());
                            
                            try (ResultSet rs = pstmt.executeQuery()) {
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
                        
                        // 获取房间用户列表（包含角色信息）
                        List<Map<String, Object>> usersList = roomDAO.getRoomMembersWithRoles(room.getId(), connection);
                        
                        // 获取在线用户ID集合
                        List<Map<String, Object>> roomUsersList = messageRouter.getRoomUsers(room.getId());
                        Set<String> onlineUserIds = new HashSet<>();
                        for (Map<String, Object> userMap : roomUsersList) {
                            if (Boolean.TRUE.equals(userMap.get("isOnline"))) {
                                // 从数据库获取用户ID
                                String username = (String) userMap.get("username");
                                try {
                                    int userId = userDAO.getUserIdByUsername(username, connection);
                                    onlineUserIds.add(String.valueOf(userId));
                                } catch (SQLException e) {
                                    System.err.println("获取用户ID失败: " + e.getMessage());
                                }
                            }
                        }
                        
                        // 构建JSON响应
                        StringBuilder response = new StringBuilder("{\"users\":[");
                        boolean first = true;
                        for (Map<String, Object> user : usersList) {
                            if (!first) {
                                response.append(",");
                            }
                            String userId = String.valueOf(user.get("userId"));
                            boolean isOnline = onlineUserIds.contains(userId);
                            String status = (String) user.get("status");
                            response.append("{\"userId\":").append(userId)
                                   .append(",\"username\":\"").append(user.get("username")).append("\"")
                                   .append(",\"role\":\"").append(user.get("role")).append("\"")
                                   .append(",\"isOnline\":").append(isOnline)
                                   .append(",\"status\":\"").append(status != null ? status : "OFFLINE").append("\"")
                                   .append(",\"joinedAt\":\"").append(user.get("joinedAt")).append("\"")
                                   .append("}");
                            first = false;
                        }
                        response.append("],\"currentUserRole\":\"").append(roomDAO.getUserRole(room.getId(), String.valueOf(currentUser.getId()), connection)).append("\"");
                        
                        // 添加房主和管理员信息
                        response.append(",\"ownerId\":\"").append(room.getOwnerId()).append("\"");
                        response.append(",\"adminIds\":[");
                        boolean firstAdmin = true;
                        for (String adminId : room.getAdminIds()) {
                            if (!firstAdmin) {
                                response.append(",");
                            }
                            response.append("\"").append(adminId).append("\"");
                            firstAdmin = false;
                        }
                        response.append("]}");
                        
                        // 发送响应
                        Message usersMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), response.toString());
                        send(messageCodec.encode(usersMessage));
                    } catch (Exception e) {
                        System.err.println("获取房间用户列表失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取房间用户列表失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                case REQUEST_HISTORY:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理消息历史请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        int limit = 50;
                        try {
                            limit = Integer.parseInt(message.getContent());
                        } catch (NumberFormatException e) {
                        }
                        
                        MessageDAO messageDAO = new MessageDAO();
                        List<Message> history = messageDAO.getRoomMessages(message.getTo(), limit, connection);
                        
                        StringBuilder historyContent = new StringBuilder();
                        for (Message msg : history) {
                            historyContent.append("[").append(msg.getTime()).append("] ")
                                       .append(msg.getFrom()).append(": ").append(msg.getContent())
                                       .append("||");
                        }
                        
                        Message historyMessage = new Message(MessageType.HISTORY_RESPONSE, "server", message.getTo(), historyContent.toString());
                        send(messageCodec.encode(historyMessage));
                    } catch (SQLException e) {
                        System.err.println("获取消息历史失败: " + e.getMessage());
                        e.printStackTrace();
                        Message systemMessage = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取消息历史失败: " + e.getMessage());
                        send(messageCodec.encode(systemMessage));
                    }
                    break;
                case PRIVATE_CHAT:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理私人消息: " + currentUser.getUsername() + " -> " + message.getTo());
                    try (Connection connection = dbManager.getConnection()) {
                        String recipient = message.getTo();
                        String privateContent = message.getContent();
                        
                        int recipientId = userDAO.getUserIdByUsername(recipient, connection);
                        if (recipientId == 0) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "用户" + recipient + "不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        Session recipientSession = messageRouter.getSession(String.valueOf(recipientId));
                        if (recipientSession == null || !recipientSession.isActive()) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "用户" + recipient + "不在线");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        Message privateMsg = new Message(MessageType.PRIVATE_CHAT, currentUser.getUsername(), recipient, privateContent);
                        if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), String.valueOf(recipientId), messageCodec.encode(privateMsg))) {
                            MessageDAO messageDAO = new MessageDAO();
                            messageDAO.saveMessage(privateMsg, "PRIVATE", connection);
                        } else {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "发送私人消息失败");
                            send(messageCodec.encode(errorMsg));
                        }
                    } catch (SQLException e) {
                        System.err.println("发送私人消息失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case FRIEND_REQUEST:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理好友请求: " + currentUser.getUsername() + " -> " + message.getTo());
                    try (Connection connection = dbManager.getConnection()) {
                        String recipient = message.getTo();
                        int recipientId = userDAO.getUserIdByUsername(recipient, connection);
                        
                        if (recipientId == 0) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "用户" + recipient + "不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
                        if (friendshipDAO.areFriends(currentUser.getId(), recipientId, connection)) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "已经是好友了");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        server.sql.friend.FriendRequestDAO friendRequestDAO = new server.sql.friend.FriendRequestDAO();
                        if (friendRequestDAO.hasPendingRequest(currentUser.getId(), recipientId, connection)) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "好友请求已发送，等待对方处理");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        friendRequestDAO.sendFriendRequest(currentUser.getId(), recipientId, connection);
                        
                        Session recipientSession = messageRouter.getSession(String.valueOf(recipientId));
                        if (recipientSession != null && recipientSession.isActive()) {
                            Message friendRequestMsg = new Message(MessageType.FRIEND_REQUEST, currentUser.getUsername(), recipient, "");
                            recipientSession.getClientConnection().send(messageCodec.encode(friendRequestMsg));
                        }
                        
                        Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "好友请求已发送给 " + recipient);
                        send(messageCodec.encode(successMsg));
                    } catch (SQLException e) {
                        System.err.println("发送好友请求失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "发送好友请求失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case FRIEND_REQUEST_RESPONSE:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理好友请求响应: " + currentUser.getUsername() + " -> " + message.getTo());
                    try (Connection connection = dbManager.getConnection()) {
                        String friendUsername = message.getTo();
                        String response = message.getContent();
                        int friendId = userDAO.getUserIdByUsername(friendUsername, connection);
                        
                        if (friendId == 0) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "用户" + friendUsername + "不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        server.sql.friend.FriendRequestDAO friendRequestDAO = new server.sql.friend.FriendRequestDAO();
                        server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
                        
                        if ("ACCEPT".equals(response)) {
                            friendRequestDAO.updateFriendRequestStatus(friendRequestDAO.getFriendRequest(friendId, currentUser.getId(), connection).id, "ACCEPTED", connection);
                            friendshipDAO.createFriendship(friendId, currentUser.getId(), connection);
                            
                            Session friendSession = messageRouter.getSession(String.valueOf(friendId));
                            if (friendSession != null && friendSession.isActive()) {
                                Message acceptMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, currentUser.getUsername(), friendUsername, "ACCEPT");
                                friendSession.getClientConnection().send(messageCodec.encode(acceptMsg));
                            }
                            
                            Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "已接受 " + friendUsername + " 的好友请求");
                            send(messageCodec.encode(successMsg));
                        } else if ("REJECT".equals(response)) {
                            friendRequestDAO.updateFriendRequestStatus(friendRequestDAO.getFriendRequest(friendId, currentUser.getId(), connection).id, "REJECTED", connection);
                            
                            Session friendSession = messageRouter.getSession(String.valueOf(friendId));
                            if (friendSession != null && friendSession.isActive()) {
                                Message rejectMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, currentUser.getUsername(), friendUsername, "REJECT");
                                friendSession.getClientConnection().send(messageCodec.encode(rejectMsg));
                            }
                            
                            Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "已拒绝 " + friendUsername + " 的好友请求");
                            send(messageCodec.encode(successMsg));
                        } else if ("REMOVE".equals(response)) {
                            friendshipDAO.removeFriendship(currentUser.getId(), friendId, connection);
                            
                            Session friendSession = messageRouter.getSession(String.valueOf(friendId));
                            if (friendSession != null && friendSession.isActive()) {
                                Message removeMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, currentUser.getUsername(), friendUsername, "REMOVE");
                                friendSession.getClientConnection().send(messageCodec.encode(removeMsg));
                            }
                            
                            Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "已删除好友 " + friendUsername);
                            send(messageCodec.encode(successMsg));
                        }
                    } catch (SQLException e) {
                        System.err.println("处理好友请求响应失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "操作失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case REQUEST_FRIEND_LIST:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理好友列表请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
                        List<server.sql.friend.FriendshipDAO.Friendship> friends = friendshipDAO.getUserFriends(currentUser.getId(), connection);
                        
                        StringBuilder friendsList = new StringBuilder();
                        for (server.sql.friend.FriendshipDAO.Friendship friendship : friends) {
                            String friendName = friendship.user1Id == currentUser.getId() ? friendship.user2Username : friendship.user1Username;
                            friendsList.append(friendName).append("||");
                        }
                        
                        Message friendListMsg = new Message(MessageType.FRIEND_LIST, "server", currentUser.getUsername(), friendsList.toString());
                        send(messageCodec.encode(friendListMsg));
                    } catch (SQLException e) {
                        System.err.println("获取好友列表失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取好友列表失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case REQUEST_ALL_FRIEND_REQUESTS:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理好友请求列表请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        server.sql.friend.FriendRequestDAO friendRequestDAO = new server.sql.friend.FriendRequestDAO();
                        List<server.sql.friend.FriendRequestDAO.FriendRequest> requests = friendRequestDAO.getAllFriendRequests(currentUser.getId(), connection);
                        
                        StringBuilder requestsList = new StringBuilder();
                        for (server.sql.friend.FriendRequestDAO.FriendRequest request : requests) {
                            String otherUser = request.fromUserId == currentUser.getId() ? request.toUsername : request.fromUsername;
                            requestsList.append(otherUser).append(":").append(request.status).append("||");
                        }
                        
                        Message requestsMsg = new Message(MessageType.ALL_FRIEND_REQUESTS, "server", currentUser.getUsername(), requestsList.toString());
                        send(messageCodec.encode(requestsMsg));
                    } catch (SQLException e) {
                        System.err.println("获取好友请求列表失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取好友请求列表失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case SEARCH_USERS:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理用户搜索请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String keyword = message.getContent();
                        String sql = "SELECT username FROM user WHERE username LIKE ? LIMIT 10";
                        
                        StringBuilder usersList = new StringBuilder();
                        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                            stmt.setString(1, "%" + keyword + "%");
                            ResultSet rs = stmt.executeQuery();
                            while (rs.next()) {
                                String username = rs.getString("username");
                                if (!username.equals(currentUser.getUsername())) {
                                    usersList.append(username).append("||");
                                }
                            }
                        }
                        
                        if (usersList.length() == 0) {
                            usersList.append("未找到匹配的用户");
                        }
                        
                        Message searchResultMsg = new Message(MessageType.USERS_SEARCH_RESULT, "server", currentUser.getUsername(), usersList.toString());
                        send(messageCodec.encode(searchResultMsg));
                    } catch (SQLException e) {
                        System.err.println("搜索用户失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "搜索用户失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case SEARCH_ROOMS:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理房间搜索请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String keyword = message.getContent();
                        String sql = "SELECT room_name, room_type FROM room WHERE room_name LIKE ? LIMIT 10";
                        
                        StringBuilder roomsList = new StringBuilder();
                        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                            stmt.setString(1, "%" + keyword + "%");
                            ResultSet rs = stmt.executeQuery();
                            while (rs.next()) {
                                String roomName = rs.getString("room_name");
                                String roomType = rs.getString("room_type");
                                roomsList.append(roomName).append("#").append(roomType).append("||");
                            }
                        }
                        
                        if (roomsList.length() == 0) {
                            roomsList.append("未找到匹配的房间");
                        }
                        
                        Message searchResultMsg = new Message(MessageType.ROOMS_SEARCH_RESULT, "server", currentUser.getUsername(), roomsList.toString());
                        send(messageCodec.encode(searchResultMsg));
                    } catch (SQLException e) {
                        System.err.println("搜索房间失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "搜索房间失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case REQUEST_ROOM_JOIN:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理房间加入请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String roomName = message.getTo();
                        Room room = roomDAO.getRoomByName(roomName, connection);
                        
                        if (room == null) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间" + roomName + "不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        if (room instanceof PublicRoom) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "公共房间可以直接加入，使用 /join " + roomName);
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        PrivateRoom privateRoom = (PrivateRoom) room;
                        String ownerId = privateRoom.getOwnerId();
                        if (ownerId != null && ownerId.equals(String.valueOf(currentUser.getId()))) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "您是房主，无需申请加入");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        Session ownerSession = messageRouter.getSession(ownerId);
                        if (ownerSession != null && ownerSession.isActive()) {
                            Message joinRequestMsg = new Message(MessageType.ROOM_JOIN_REQUEST, currentUser.getUsername(), roomName, "");
                            ownerSession.getClientConnection().send(messageCodec.encode(joinRequestMsg));
                            
                            Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "已发送加入房间请求给房主");
                            send(messageCodec.encode(successMsg));
                        } else {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房主不在线，无法处理加入请求");
                            send(messageCodec.encode(errorMsg));
                        }
                    } catch (SQLException e) {
                        System.err.println("处理房间加入请求失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "处理房间加入请求失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case REQUEST_USER_STATS:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理用户统计请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        MessageDAO messageDAO = new MessageDAO();
                        int messageCount = messageDAO.getUserMessageCount(currentUser.getUsername(), connection);
                        int imageCount = messageDAO.getUserImageCount(currentUser.getUsername(), connection);
                        int fileCount = messageDAO.getUserFileCount(currentUser.getUsername(), connection);
                        
                        server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
                        List<server.sql.friend.FriendshipDAO.Friendship> friends = friendshipDAO.getUserFriends(currentUser.getId(), connection);
                        int friendCount = friends.size();
                        
                        String stats = String.format("用户统计信息:\n用户名: %s\n消息总数: %d\n图片数量: %d\n文件数量: %d\n好友数量: %d\n注册时间: %s\n状态: %s",
                                currentUser.getUsername(), messageCount, imageCount, fileCount, friendCount,
                                currentUser.getCreatedAt() != null ? currentUser.getCreatedAt().toString() : "未知",
                                currentUser.getStatus() != null ? currentUser.getStatus() : "未知");
                        
                        Message statsMsg = new Message(MessageType.USER_STATS_RESPONSE, "server", currentUser.getUsername(), stats);
                        send(messageCodec.encode(statsMsg));
                    } catch (SQLException e) {
                        System.err.println("获取用户统计失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取用户统计失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case RECALL_MESSAGE:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理消息撤回请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String messageId = message.getContent();
                        MessageDAO messageDAO = new MessageDAO();
                        
                        if (messageDAO.deleteMessage(messageId, connection)) {
                            Message recallMsg = new Message(MessageType.RECALL_MESSAGE, currentUser.getUsername(), message.getTo(), "");
                            messageRouter.broadcastToRoom(message.getTo(), messageCodec.encode(recallMsg));
                            
                            Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "消息已撤回");
                            send(messageCodec.encode(successMsg));
                        } else {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "撤回消息失败，消息不存在或无权限");
                            send(messageCodec.encode(errorMsg));
                        }
                    } catch (SQLException e) {
                        System.err.println("撤回消息失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "撤回消息失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case UPDATE_ROOM_SETTINGS:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理房间设置更新: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String[] parts = message.getContent().split("\\|");
                        if (parts.length < 2) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "设置格式错误");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        String roomName = parts[0];
                        String settingType = parts[1];
                        
                        RoomDAO roomDAO = new RoomDAO(messageRouter);
                        Room room = roomDAO.getRoomByName(roomName, connection);
                        
                        if (room == null) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        String roomId = room.getId();
                        
                        if ("display_name".equals(settingType)) {
                            String displayName = parts.length > 2 ? parts[2] : null;
                            
                            if (displayName != null && !displayName.trim().isEmpty()) {
                                if (!roomDAO.isRoomDisplayNameAvailable(roomId, displayName, connection)) {
                                    Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "该显示名已被占用");
                                    send(messageCodec.encode(errorMsg));
                                    break;
                                }
                            }
                            
                            if (roomDAO.updateUserRoomDisplayName(roomId, String.valueOf(currentUser.getId()), displayName, connection)) {
                                Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间显示名已更新");
                                send(messageCodec.encode(successMsg));
                                
                                // Broadcast display name update to room members
                                String updateContent = String.valueOf(currentUser.getId()) + ":" + (displayName != null ? displayName : "") + ":" + currentUser.getUsername();
                                Message updateMsg = new Message(MessageType.ROOM_DISPLAY_NAME_UPDATED, "server", roomName, updateContent);
                                messageRouter.broadcastToRoom(roomId, messageCodec.encode(updateMsg));
                            } else {
                                Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "更新显示名失败");
                                send(messageCodec.encode(errorMsg));
                            }
                        } else if ("accept_temporary_chat".equals(settingType)) {
                            boolean accept = Boolean.parseBoolean(parts[2]);
                            if (roomDAO.updateRoomAcceptTemporaryChat(roomId, String.valueOf(currentUser.getId()), accept, connection)) {
                                Message successMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "临时聊天设置已更新");
                                send(messageCodec.encode(successMsg));
                            } else {
                                Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "更新临时聊天设置失败");
                                send(messageCodec.encode(errorMsg));
                            }
                        } else {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "未知的设置类型");
                            send(messageCodec.encode(errorMsg));
                        }
                    } catch (SQLException e) {
                        System.err.println("更新房间设置失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "更新房间设置失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                case REQUEST_ROOM_DISPLAY_NAMES:
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    System.out.println("处理房间显示名请求: " + currentUser.getUsername());
                    try (Connection connection = dbManager.getConnection()) {
                        String roomName = message.getContent();
                        RoomDAO roomDAO = new RoomDAO(messageRouter);
                        Room room = roomDAO.getRoomByName(roomName, connection);
                        
                        if (room == null) {
                            Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "房间不存在");
                            send(messageCodec.encode(errorMsg));
                            break;
                        }
                        
                        String roomId = room.getId();
                        List<java.util.Map<String, Object>> members = roomDAO.getRoomMembersWithRoles(roomId, connection);
                        
                        StringBuilder displayNamesContent = new StringBuilder();
                        for (java.util.Map<String, Object> member : members) {
                            int userId = (int) member.get("userId");
                            String username = (String) member.get("username");
                            String displayName = roomDAO.getUserRoomDisplayName(roomId, String.valueOf(userId), connection);
                            
                            if (displayName != null && !displayName.trim().isEmpty()) {
                                displayNamesContent.append(userId).append(":").append(displayName).append("||");
                            }
                        }
                        
                        Message displayNamesMsg = new Message(MessageType.ROOM_DISPLAY_NAMES_RESPONSE, "server", currentUser.getUsername(), displayNamesContent.toString());
                        send(messageCodec.encode(displayNamesMsg));
                    } catch (SQLException e) {
                        System.err.println("获取房间显示名失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", currentUser.getUsername(), "获取房间显示名失败: " + e.getMessage());
                        send(messageCodec.encode(errorMsg));
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
            Session existingSession = messageRouter.getSessionByUsername(username);
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
                System.out.println("用户登录失败，用户名已登录: " + username);
                return;
            }
            
            // 构造登录成功消息
            Message authSuccessMessage = new Message(
                MessageType.AUTH_SUCCESS,
                "server",
                message.getFrom(),
                uuid,
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            
            // 更新用户状态为ONLINE
            try {
                userDAO.updateUserStatus(currentUser.getId(), "ONLINE", connection);
                System.out.println("用户状态已更新为ONLINE: " + username);
            } catch (SQLException e) {
                System.err.println("更新用户状态失败: " + e.getMessage());
                e.printStackTrace();
            }
            
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
            User user = userDAO.getUserByUsername(message.getFrom(), connection);
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
            Session existingSession = messageRouter.getSessionByUsername(user.getUsername());
            if (existingSession != null && existingSession.isActive()) {
                // 用户名已经登录，拒绝新登录
                Message authFailureMessage = new Message(
                    MessageType.UUID_AUTH_FAILURE,
                    "server",
                    message.getFrom(),
                    "该用户名已在其他地方登录",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                System.out.println("用户UUID认证失败，用户名已登录: " + user.getUsername());
                return;
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
        if (!isConnected) {
            System.err.println("尝试向已关闭的连接发送消息 (" + clientAddress + ":" + clientPort + ")");
            return;
        }
        
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
            System.out.println("消息已发送到客户端 (" + clientAddress + ":" + clientPort + "): " + message);
        } catch (IOException e) {
            System.err.println("发送消息失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
            close();
        }
    }

    public synchronized void close() {
        if (!isConnected) {
            return;
        }
        
        System.out.println("正在关闭客户端连接: " + clientAddress + ":" + clientPort);
        isConnected = false;
        
        // 注销会话
        if (isAuthenticated && currentUser != null && messageRouter != null) {
            String userId = String.valueOf(currentUser.getId());
            messageRouter.deregisterSession(userId);
            System.out.println("会话已注销: 用户ID=" + userId);
            
            // 更新用户状态为OFFLINE
            try (Connection connection = dbManager.getConnection()) {
                userDAO.updateUserStatus(currentUser.getId(), "OFFLINE", connection);
                System.out.println("用户状态已更新为OFFLINE: " + currentUser.getUsername());
            } catch (SQLException e) {
                System.err.println("更新用户状态失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("关闭读取流失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
        } finally {
            reader = null;
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("关闭写入流失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
        } finally {
            writer = null;
        }
        
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭客户端Socket失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
        }
        
        System.out.println("客户端连接已完全关闭: " + clientAddress + ":" + clientPort);
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public int getClientPort() {
        return clientPort;
    }

    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * 获取当前客户端连接的用户
     * @return 当前用户对象
     */
    public User getCurrentUser() {
        return currentUser;
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
        
        // 检查是否已经存在会话
        currentSession = messageRouter.getSession(userId);
        if (currentSession != null) {
            // 如果会话存在，只更新客户端连接
            System.out.println("会话已存在，更新客户端连接: 用户ID=" + userId);
            currentSession.setClientConnection(this);
            currentSession.setActive(true); // 确保会话是活动状态
        } else {
            // 如果会话不存在，创建新会话
            currentSession = new Session(userId, currentUser.getUsername(), this);
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
    

}