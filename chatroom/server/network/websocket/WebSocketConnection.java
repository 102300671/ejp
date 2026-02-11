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
import server.sql.friend.FriendRequestDAO;
import server.sql.friend.FriendshipDAO;
import server.room.PrivateRoom;
import server.room.PublicRoom;
import server.room.Room;
import server.user.User;
import server.util.AESUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import server.config.ServiceConfig;

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
        
        // 注销会话并更新用户状态
        if (isAuthenticated && currentUser != null) {
            String userId = String.valueOf(currentUser.getId());
            messageRouter.deregisterSession(userId);
            
            // 更新用户状态为OFFLINE
            try (Connection connection = dbManager.getConnection()) {
                userDAO.updateUserStatus(currentUser.getId(), "OFFLINE", connection);
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
                case REQUEST_USER_STATS:
                    // 已认证，处理请求用户统计数据
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestUserStats(message);
                    break;
                case IMAGE:
                    // 已认证，处理图片消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleImageMessage(message);
                    break;
                case FILE:
                    // 已认证，处理文件消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleFileMessage(message);
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
                    
                case PRIVATE_CHAT:
                    // 已认证，处理私聊消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    
                    String privateFrom = currentUser.getUsername();
                    String privateTo = message.getTo();
                    String privateContent = message.getContent();
                    
                    System.out.println("处理私聊消息: 从" + privateFrom + "到" + privateTo + "的消息: " + privateContent);
                    
                    // 检查是否为临时聊天（非好友关系）
                    try (Connection connection = dbManager.getConnection()) {
                        server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
                        boolean isFriend = friendshipDAO.areFriends(privateFrom, privateTo, connection);
                        
                        if (!isFriend) {
                            // 临时聊天，需要检查权限
                            boolean allowTemporaryChat = checkTemporaryChatPermission(privateTo, connection);
                            
                            if (!allowTemporaryChat) {
                                Message errorMsg = new Message(MessageType.SYSTEM, "server", privateFrom, "无法发送临时聊天消息：对方不接受临时聊天");
                                send(messageCodec.encode(errorMsg));
                                System.out.println("临时聊天被拒绝: " + privateTo + " 不接受临时聊天");
                                break;
                            }
                        }
                        
                        // 查找接收者用户ID
                        String recipientId = null;
                        for (Session session : messageRouter.getSessions().values()) {
                            if (session.getUsername().equals(privateTo)) {
                                recipientId = session.getUserId();
                                break;
                            }
                        }
                        
                        // 保存私聊消息到数据库
                        Message privateChatMsg = new Message(MessageType.PRIVATE_CHAT, privateFrom, privateTo, privateContent);
                        MessageDAO messageDAO = new MessageDAO();
                        messageDAO.saveMessage(privateChatMsg, "PRIVATE", connection);
                        System.out.println("私聊消息已保存到数据库: 从" + privateFrom + "到" + privateTo + "的消息: " + privateContent);
                        
                        // 发送私聊消息
                        if (recipientId != null) {
                            // 接收者在线，发送消息
                            if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(privateChatMsg))) {
                                System.out.println("私聊消息发送成功: 从" + privateFrom + "到" + privateTo + "的消息: " + privateContent);
                            } else {
                                Message errorMsg = new Message(MessageType.SYSTEM, "server", privateFrom, "发送私聊消息失败: 用户" + privateTo + "可能不在线");
                                send(messageCodec.encode(errorMsg));
                            }
                        } else {
                            // 接收者不在线，通知发送者
                            Message infoMsg = new Message(MessageType.SYSTEM, "server", privateFrom, "消息已发送，但用户" + privateTo + "当前不在线，上线后将收到消息");
                            send(messageCodec.encode(infoMsg));
                            System.out.println("私聊接收者不在线: " + privateTo);
                        }
                    } catch (SQLException e) {
                        System.err.println("处理私聊消息失败: " + e.getMessage());
                        e.printStackTrace();
                        Message errorMsg = new Message(MessageType.SYSTEM, "server", privateFrom, "发送私聊消息失败: 服务器内部错误");
                        send(messageCodec.encode(errorMsg));
                    }
                    break;
                    
                case FRIEND_REQUEST:
                    // 已认证，处理好友请求
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleFriendRequest(message);
                    break;
                    
                case FRIEND_REQUEST_RESPONSE:
                    // 已认证，处理好友请求响应
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleFriendRequestResponse(message);
                    break;
                    
                case REQUEST_FRIEND_LIST:
                    // 已认证，处理请求好友列表
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestFriendList();
                    break;
                    
                case SEARCH_USERS:
                    // 已认证，处理用户搜索
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleSearchUsers(message);
                    break;
                    
                case REQUEST_ALL_FRIEND_REQUESTS:
                    // 已认证，处理请求所有好友请求
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestAllFriendRequests();
                    break;
                    
                case SEARCH_ROOMS:
                    // 已认证，处理房间搜索
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleSearchRooms(message);
                    break;
                    
                case REQUEST_ROOM_JOIN:
                    // 已认证，处理请求加入房间
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRequestRoomJoin(message);
                    break;
                    
                case ROOM_JOIN_REQUEST:
                    // 已认证，处理房间加入请求
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRoomJoinRequest(message);
                    break;
                    
                case ROOM_JOIN_RESPONSE:
                    // 已认证，处理房间加入响应
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRoomJoinResponse(message);
                    break;
                    
                case SET_ROOM_ADMIN:
                    // 已认证，处理设置管理员
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleSetRoomAdmin(message);
                    break;
                    
                case REMOVE_ROOM_ADMIN:
                    // 已认证，处理移除管理员
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRemoveRoomAdmin(message);
                    break;
                    
                case UPDATE_USER_SETTINGS:
                    // 已认证，处理用户设置更新
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleUpdateUserSettings(message);
                    break;
                    
                case UPDATE_ROOM_SETTINGS:
                    // 已认证，处理房间设置更新
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleUpdateRoomSettings(message);
                    break;
                    
                case RECALL_MESSAGE:
                    // 已认证，处理撤回消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                        break;
                    }
                    handleRecallMessage(message);
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
                        
                        // 更新会话的当前房间
                        Session session = messageRouter.getSessions().get(userId);
                        if (session != null) {
                            session.setCurrentRoom(roomName);
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
                            
                            // 更新会话的当前房间
                            Session session = messageRouter.getSessions().get(userId);
                            if (session != null) {
                                session.setCurrentRoom(null);
                            }
                            
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
                        
                        // 检查房间是否已存在（应用层检查）
                        if (roomDAO.roomExists(roomName, connection)) {
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "房间" + roomName + "已存在");
                            send(messageCodec.encode(systemMessage));
                            break;
                        }
                        
                        try {
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
                            
                            // 将创建者作为房主加入房间
                            roomDAO.joinRoom(newRoom.getId(), String.valueOf(currentUser.getId()), "OWNER", connection);
                            messageRouter.joinRoom(String.valueOf(currentUser.getId()), newRoom.getId());
                            
                            // 发送成功消息
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "房间" + roomName + "创建成功，类型: " + roomType);
                            send(messageCodec.encode(systemMessage));
                            
                            System.out.println("房间创建成功: " + roomName + " (ID: " + newRoom.getId() + ", 类型: " + roomType + ")");
                        } catch (java.sql.SQLException e) {
                            // 检查是否是唯一性约束冲突（数据库层保护）
                            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                                Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "房间" + roomName + "已存在");
                                send(messageCodec.encode(systemMessage));
                                break;
                            }
                            // 其他SQL错误
                            System.err.println("创建房间失败: " + e.getMessage());
                            e.printStackTrace();
                            Message systemMessage = new Message(MessageType.SYSTEM, "server", message.getFrom(), "创建房间失败: " + e.getMessage());
                            send(messageCodec.encode(systemMessage));
                        }
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
                        
                        // 发送响应，使用LIST_ROOM_USERS消息类型
                        Message usersMessage = new Message(MessageType.LIST_ROOM_USERS, "server", currentUser.getUsername(), response.toString());
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
            
            // 检查用户名是否已存在（应用层检查）
            if (userDAO.getUserIdByUsername(username, connection) != null) {
                sendAuthFailure("用户名已存在");
                return;
            }
            
            // 创建用户对象
            User newUser = new User(0, username, password, null, null);
            
            try {
                // 插入用户到数据库
                userDAO.insertUser(newUser, connection);
            } catch (java.sql.SQLException e) {
                // 检查是否是唯一性约束冲突（数据库层保护）
                if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                    sendAuthFailure("用户名已存在");
                    return;
                }
                // 其他SQL错误
                System.err.println("插入用户失败: " + e.getMessage());
                sendAuthFailure("注册失败: " + e.getMessage());
                return;
            }
            
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
            
            // 发送服务配置
            sendServiceConfig();
            
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
            
            // 发送服务配置
            sendServiceConfig();
            
            // 标记用户已认证
            isAuthenticated = true;
            
            System.out.println("用户登录成功: " + username + " (ID: " + currentUser.getId() + ")");
            
            // 更新用户状态为ONLINE
            try {
                userDAO.updateUserStatus(currentUser.getId(), "ONLINE", connection);
                System.out.println("用户状态已更新为ONLINE: " + username);
            } catch (SQLException e) {
                System.err.println("更新用户状态失败: " + e.getMessage());
                e.printStackTrace();
            }
            
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
            
            // 发送服务配置
            sendServiceConfig();
            
            // 标记用户已认证
            isAuthenticated = true;
            currentUser = user;
            
            // 更新用户状态为ONLINE
            try {
                userDAO.updateUserStatus(currentUser.getId(), "ONLINE", connection);
                System.out.println("用户状态已更新为ONLINE: " + user.getUsername());
            } catch (SQLException e) {
                System.err.println("更新用户状态失败: " + e.getMessage());
                e.printStackTrace();
            }
            
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
            
            ServiceConfig serviceConfig = ServiceConfig.getInstance();
            String zfileServerUrl = serviceConfig.getZfileServerUrl();
            
            String tokenInfo = uploadToken + "|" + zfileServerUrl;
            
            Message tokenResponse = new Message(
                MessageType.TOKEN_RESPONSE,
                "server",
                username,
                tokenInfo,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
            );
            
            send(messageCodec.encode(tokenResponse));
            System.out.println("发送上传 token 响应: " + uploadToken + ", ZFile URL: " + zfileServerUrl);
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
            iv,
            null
        );
        
        System.out.println("创建的imageMessage: " + imageMessage.toString());
        
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
     * 处理文件消息
     * @param message 文件消息
     */
    private void handleFileMessage(Message message) {
        String from = currentUser.getUsername();
        String to = message.getTo();
        String fileContent = message.getContent();
        
        System.out.println("处理文件消息: 从" + from + "到" + to + "的文件");
        
        Message fileMessage = new Message(
            MessageType.FILE,
            from,
            to,
            fileContent,
            message.getTime(),
            message.isNSFW(),
            null,
            null
        );
        
        System.out.println("创建的fileMessage: " + fileMessage.toString());
        
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
                if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(fileMessage))) {
                    try (Connection connection = dbManager.getConnection()) {
                        MessageDAO messageDAO = new MessageDAO();
                        messageDAO.saveMessage(fileMessage, "PRIVATE", connection);
                    } catch (SQLException e) {
                        System.err.println("保存私聊文件消息到数据库失败: " + e.getMessage());
                    }
                }
            }
        } else {
            for (String roomId : messageRouter.getRooms().keySet()) {
                if (to.equals(messageRouter.getRooms().get(roomId).getName())) {
                    messageRouter.broadcastToRoom(roomId, messageCodec.encode(fileMessage), String.valueOf(currentUser.getId()));
                    
                    try (Connection connection = dbManager.getConnection()) {
                        MessageDAO messageDAO = new MessageDAO();
                        messageDAO.saveMessage(fileMessage, "ROOM", connection);
                    } catch (SQLException e) {
                        System.err.println("保存房间文件消息到数据库失败: " + e.getMessage());
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
    
    /**
     * 发送服务配置到客户端
     */
    private void sendServiceConfig() {
        try {
            ServiceConfig serviceConfig = ServiceConfig.getInstance();
            
            StringBuilder configJson = new StringBuilder();
            configJson.append("{");
            configJson.append("\"zfileServerUrl\":\"").append(serviceConfig.getZfileServerUrl()).append("\"");
            configJson.append("}");
            
            Message configMessage = new Message(
                MessageType.SERVICE_CONFIG,
                "server",
                "client",
                configJson.toString(),
                null
            );
            
            send(messageCodec.encode(configMessage));
            System.out.println("服务配置已发送到客户端");
        } catch (Exception e) {
            System.err.println("发送服务配置失败: " + e.getMessage());
            e.printStackTrace();
        }
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
    
    /**
     * 处理用户统计数据请求
     * @param message 请求消息
     */
    private void handleRequestUserStats(Message message) {
        String username = currentUser.getUsername();
        System.out.println("处理用户统计数据请求: 用户 " + username);
        
        try (java.sql.Connection connection = dbManager.getConnection()) {
            // 获取消息数
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            int messageCount = messageDAO.getUserMessageCount(username, connection);
            
            // 获取图片数
            int imageCount = messageDAO.getUserImageCount(username, connection);
            
            // 获取文件数
            int fileCount = messageDAO.getUserFileCount(username, connection);
            
            // 获取房间数（从数据库查询）
            int roomCount = roomDAO.getUserRoomCount(String.valueOf(currentUser.getId()), connection);
            
            // 获取加入时间
            String joinTime = userDAO.getUserJoinTime(username, connection);
            
            // 获取用户状态
            String status = currentUser.getStatus();
            
            // 创建统计数据JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("messageCount", messageCount);
            stats.put("imageCount", imageCount);
            stats.put("fileCount", fileCount);
            stats.put("roomCount", roomCount);
            stats.put("joinTime", joinTime != null ? joinTime : "");
            stats.put("status", status != null ? status : "OFFLINE");
            
            String statsJson = gson.toJson(stats);
            
            // 创建响应消息
            Message responseMsg = new Message(MessageType.USER_STATS_RESPONSE, "server", username, statsJson);
            
            // 发送响应
            send(messageCodec.encode(responseMsg));
            System.out.println("发送用户统计数据响应: " + username + " - 消息数: " + messageCount + ", 图片数: " + imageCount + ", 文件数: " + fileCount + ", 房间数: " + roomCount + ", 加入时间: " + joinTime + ", 状态: " + status);
        } catch (java.sql.SQLException e) {
            System.err.println("获取用户统计数据失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "获取用户统计数据失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleFriendRequest(Message message) {
        String fromUsername = currentUser.getUsername();
        String toUsername = message.getTo();
        String content = message.getContent();
        
        System.out.println("处理好友请求: " + fromUsername + " -> " + toUsername);
        
        try (Connection connection = dbManager.getConnection()) {
            // 获取接收者用户ID
            int toUserId = userDAO.getUserIdByUsername(toUsername, connection);
            if (toUserId == -1) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", fromUsername, "用户 " + toUsername + " 不存在");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查是否已经是好友
            server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
            if (friendshipDAO.areFriends(currentUser.getId(), toUserId, connection)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", fromUsername, "您已经是 " + toUsername + " 的好友");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查是否已有待处理的好友请求
            server.sql.friend.FriendRequestDAO friendRequestDAO = new server.sql.friend.FriendRequestDAO();
            boolean hasPendingRequest = friendRequestDAO.hasPendingRequest(currentUser.getId(), toUserId, connection);
            
            if (hasPendingRequest) {
                // 已有待处理请求，但仍需通知接收者
                Message pendingMsg = new Message(MessageType.SYSTEM, "server", fromUsername, "您已经向 " + toUsername + " 发送了好友请求，请等待对方处理");
                send(messageCodec.encode(pendingMsg));
            } else {
                // 发送新的好友请求
                boolean success = friendRequestDAO.sendFriendRequest(currentUser.getId(), toUserId, connection);
                if (!success) {
                    Message errorMsg = new Message(MessageType.SYSTEM, "server", fromUsername, "发送好友请求失败");
                    send(messageCodec.encode(errorMsg));
                    return;
                }
                
                Message successMsg = new Message(MessageType.SYSTEM, "server", fromUsername, "好友请求已发送给 " + toUsername);
                send(messageCodec.encode(successMsg));
            }
            
            // 无论是否已有待处理请求，都通知接收者
            String recipientId = null;
            for (Session session : messageRouter.getSessions().values()) {
                if (session.getUsername().equals(toUsername)) {
                    recipientId = session.getUserId();
                    break;
                }
            }
            
            if (recipientId != null) {
                Message notificationMsg = new Message(MessageType.FRIEND_REQUEST, fromUsername, toUsername, content);
                messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(notificationMsg));
                System.out.println("好友请求已发送给在线用户: " + toUsername);
            } else {
                System.out.println("好友请求已保存，用户不在线: " + toUsername);
            }
        } catch (SQLException e) {
            System.err.println("处理好友请求失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", fromUsername, "发送好友请求失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleFriendRequestResponse(Message message) {
        String username = currentUser.getUsername();
        String toUsername = message.getTo();
        String content = message.getContent();
        
        System.out.println("处理好友请求响应: " + username + " <- " + toUsername + ", 内容: " + content);
        
        try (Connection connection = dbManager.getConnection()) {
            // 获取原始发送者用户ID（to字段中的用户）
            int fromUserId = userDAO.getUserIdByUsername(toUsername, connection);
            if (fromUserId == -1) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "用户 " + toUsername + " 不存在");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 查找好友请求（从fromUserId发送给currentUser的请求）
            server.sql.friend.FriendRequestDAO friendRequestDAO = new server.sql.friend.FriendRequestDAO();
            server.sql.friend.FriendRequestDAO.FriendRequest request = friendRequestDAO.getFriendRequest(fromUserId, currentUser.getId(), connection);
            
            if (request == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "未找到来自 " + toUsername + " 的好友请求");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 解析响应（accept/reject）
            String[] parts = content.split(":");
            String response = parts.length > 0 ? parts[0].trim().toLowerCase() : "";
            
            if ("accept".equals(response)) {
                // 接受好友请求
                server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
                boolean success = friendshipDAO.createFriendship(fromUserId, currentUser.getId(), connection);
                
                if (success) {
                    // 更新好友请求状态
                    friendRequestDAO.updateFriendRequestStatus(request.id, "ACCEPTED", connection);
                    
                    // 通知原始发送者
                    String recipientId = null;
                    for (Session session : messageRouter.getSessions().values()) {
                        if (session.getUsername().equals(toUsername)) {
                            recipientId = session.getUserId();
                            break;
                        }
                    }
                    
                    if (recipientId != null) {
                        Message notificationMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, username, toUsername, "accept:" + username);
                        messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(notificationMsg));
                        System.out.println("好友请求接受通知已发送给: " + toUsername);
                    }
                    
                    Message successMsg = new Message(MessageType.SYSTEM, "server", username, "您已接受 " + toUsername + " 的好友请求");
                    send(messageCodec.encode(successMsg));
                } else {
                    Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "接受好友请求失败");
                    send(messageCodec.encode(errorMsg));
                }
            } else if ("reject".equals(response)) {
                // 拒绝好友请求
                boolean success = friendRequestDAO.updateFriendRequestStatus(request.id, "REJECTED", connection);
                
                if (success) {
                    // 通知原始发送者
                    String recipientId = null;
                    for (Session session : messageRouter.getSessions().values()) {
                        if (session.getUsername().equals(toUsername)) {
                            recipientId = session.getUserId();
                            break;
                        }
                    }
                    
                    if (recipientId != null) {
                        Message notificationMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, username, toUsername, "reject:" + username);
                        messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), recipientId, messageCodec.encode(notificationMsg));
                        System.out.println("好友请求拒绝通知已发送给: " + toUsername);
                    }
                    
                    Message successMsg = new Message(MessageType.SYSTEM, "server", username, "您已拒绝 " + toUsername + " 的好友请求");
                    send(messageCodec.encode(successMsg));
                } else {
                    Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "拒绝好友请求失败");
                    send(messageCodec.encode(errorMsg));
                }
            } else {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "无效的好友请求响应");
                send(messageCodec.encode(errorMsg));
            }
        } catch (SQLException e) {
            System.err.println("处理好友请求响应失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "处理好友请求响应失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRequestFriendList() {
        String username = currentUser.getUsername();
        System.out.println("处理好友列表请求: 用户 " + username);
        
        try (Connection connection = dbManager.getConnection()) {
            server.sql.friend.FriendshipDAO friendshipDAO = new server.sql.friend.FriendshipDAO();
            List<server.sql.friend.FriendshipDAO.Friendship> friendships = friendshipDAO.getUserFriends(currentUser.getId(), connection);
            
            // 构建好友列表JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.List<java.util.Map<String, Object>> friendList = new java.util.ArrayList<>();
            
            for (server.sql.friend.FriendshipDAO.Friendship friendship : friendships) {
                java.util.Map<String, Object> friendInfo = new java.util.HashMap<>();
                String friendUsername = friendship.user1Id == currentUser.getId() ? friendship.user2Username : friendship.user1Username;
                friendInfo.put("username", friendUsername);
                friendInfo.put("createdAt", friendship.createdAt != null ? friendship.createdAt.toString() : "");
                friendList.add(friendInfo);
            }
            
            String friendsJson = gson.toJson(friendList);
            
            // 创建响应消息
            Message responseMsg = new Message(MessageType.FRIEND_LIST, "server", username, friendsJson);
            
            // 发送响应
            send(messageCodec.encode(responseMsg));
            System.out.println("发送好友列表响应: " + username + " - 好友数: " + friendList.size());
        } catch (SQLException e) {
            System.err.println("获取好友列表失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "获取好友列表失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleSearchUsers(Message message) {
        String username = currentUser.getUsername();
        String searchTerm = message.getContent();
        
        System.out.println("处理用户搜索请求: 用户 " + username + "，搜索词: " + searchTerm);
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "搜索词不能为空");
            send(messageCodec.encode(errorMsg));
            return;
        }
        
        try (Connection connection = dbManager.getConnection()) {
            java.util.List<server.user.User> users = userDAO.searchUsers(searchTerm, connection);
            
            // 构建用户列表JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.List<java.util.Map<String, Object>> userList = new java.util.ArrayList<>();
            
            for (server.user.User user : users) {
                java.util.Map<String, Object> userInfo = new java.util.HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : "");
                userList.add(userInfo);
            }
            
            String usersJson = gson.toJson(userList);
            
            // 创建响应消息
            Message responseMsg = new Message(MessageType.USERS_SEARCH_RESULT, "server", username, usersJson);
            
            // 发送响应
            send(messageCodec.encode(responseMsg));
            System.out.println("发送用户搜索响应: " + username + " - 找到 " + userList.size() + " 个用户");
        } catch (SQLException e) {
            System.err.println("搜索用户失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "搜索用户失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRequestAllFriendRequests() {
        String username = currentUser.getUsername();
        
        System.out.println("处理所有好友请求请求: 用户 " + username);
        
        try (Connection connection = dbManager.getConnection()) {
            server.sql.friend.FriendRequestDAO friendRequestDAO = new server.sql.friend.FriendRequestDAO();
            java.util.List<server.sql.friend.FriendRequestDAO.FriendRequest> allRequests = friendRequestDAO.getAllFriendRequests(currentUser.getId(), connection);
            
            // 构建好友请求列表JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.List<java.util.Map<String, Object>> requestList = new java.util.ArrayList<>();
            
            for (server.sql.friend.FriendRequestDAO.FriendRequest request : allRequests) {
                java.util.Map<String, Object> requestInfo = new java.util.HashMap<>();
                requestInfo.put("id", request.id);
                requestInfo.put("fromUserId", request.fromUserId);
                requestInfo.put("toUserId", request.toUserId);
                requestInfo.put("fromUsername", request.fromUsername);
                requestInfo.put("toUsername", request.toUsername);
                requestInfo.put("status", request.status);
                requestInfo.put("createdAt", request.createdAt != null ? request.createdAt.toString() : "");
                requestInfo.put("updatedAt", request.updatedAt != null ? request.updatedAt.toString() : "");
                
                // 标记是发送的还是接收的
                boolean isReceived = request.toUserId == currentUser.getId();
                requestInfo.put("isReceived", isReceived);
                
                requestList.add(requestInfo);
            }
            
            String requestsJson = gson.toJson(requestList);
            
            // 创建响应消息
            Message responseMsg = new Message(MessageType.ALL_FRIEND_REQUESTS, "server", username, requestsJson);
            
            // 发送响应
            send(messageCodec.encode(responseMsg));
            System.out.println("发送所有好友请求响应: " + username + " - 请求数: " + requestList.size());
        } catch (SQLException e) {
            System.err.println("获取所有好友请求失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "获取好友请求失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleSearchRooms(Message message) {
        String username = currentUser.getUsername();
        String searchTerm = message.getContent();
        
        System.out.println("处理房间搜索请求: 用户 " + username + "，搜索词: " + searchTerm);
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "搜索词不能为空");
            send(messageCodec.encode(errorMsg));
            return;
        }
        
        try (Connection connection = dbManager.getConnection()) {
            java.util.List<server.room.Room> rooms = roomDAO.searchRooms(searchTerm, connection);
            
            // 构建房间列表JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.List<java.util.Map<String, Object>> roomList = new java.util.ArrayList<>();
            
            for (server.room.Room room : rooms) {
                java.util.Map<String, Object> roomInfo = new java.util.HashMap<>();
                roomInfo.put("id", room.getId());
                roomInfo.put("name", room.getName());
                roomInfo.put("type", room.getType());
                roomInfo.put("memberCount", room.getMemberCount());
                roomInfo.put("createdAt", room.getCreatedAt() != null ? room.getCreatedAt().toString() : "");
                roomList.add(roomInfo);
            }
            
            String roomsJson = gson.toJson(roomList);
            
            // 创建响应消息
            Message responseMsg = new Message(MessageType.ROOMS_SEARCH_RESULT, "server", username, roomsJson);
            
            // 发送响应
            send(messageCodec.encode(responseMsg));
            System.out.println("发送房间搜索响应: " + username + " - 找到 " + roomList.size() + " 个房间");
        } catch (SQLException e) {
            System.err.println("搜索房间失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "搜索房间失败: 服务器内部错误");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRequestRoomJoin(Message message) {
        String username = currentUser.getUsername();
        String roomName = message.getContent();
        
        System.out.println("处理房间加入请求: 用户 " + username + " 请求加入房间 " + roomName);
        
        // 查找房间
        String roomId = null;
        for (String rId : messageRouter.getRooms().keySet()) {
            if (roomName.equals(messageRouter.getRooms().get(rId).getName())) {
                roomId = rId;
                break;
            }
        }
        
        if (roomId == null) {
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "房间 " + roomName + " 不存在");
            send(messageCodec.encode(errorMsg));
            return;
        }
        
        Room room = messageRouter.getRooms().get(roomId);
        
        // 检查用户是否已在房间中
        try (Connection connection = dbManager.getConnection()) {
            boolean alreadyInRoom = roomDAO.isUserInRoom(roomId, String.valueOf(currentUser.getId()), connection);
            if (alreadyInRoom) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "您已在房间 " + roomName + " 中");
                send(messageCodec.encode(errorMsg));
                return;
            }
        } catch (SQLException e) {
            System.err.println("检查用户是否在房间中失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "检查房间状态失败");
            send(messageCodec.encode(errorMsg));
            return;
        }
        
        // 将房间加入请求发送给房主和管理员
        String ownerId = room.getOwnerId();
        Set<String> adminIds = room.getAdminIds();
        
        // 收集所有需要通知的用户ID（房主和管理员）
        Set<String> notifyUserIds = new HashSet<>();
        if (ownerId != null && !ownerId.isEmpty()) {
            notifyUserIds.add(ownerId);
        }
        if (adminIds != null && !adminIds.isEmpty()) {
            notifyUserIds.addAll(adminIds);
        }
        
        if (notifyUserIds.isEmpty()) {
            // 如果没有房主和管理员，暂时自动接受请求
            Message responseMsg = new Message(MessageType.ROOM_JOIN_RESPONSE, "server", username, "accept:" + roomName);
            send(messageCodec.encode(responseMsg));
            System.out.println("房间加入请求已自动接受: " + username + " 可以加入 " + roomName);
            return;
        }
        
        // 发送房间加入请求给房主和管理员
        Message requestMsg = new Message(MessageType.ROOM_JOIN_REQUEST, username, roomName, username);
        
        int sentCount = 0;
        for (String notifyUserId : notifyUserIds) {
            if (messageRouter.sendPrivateMessage(String.valueOf(currentUser.getId()), notifyUserId, messageCodec.encode(requestMsg))) {
                sentCount++;
            }
        }
        
        if (sentCount > 0) {
            System.out.println("房间加入请求已发送: " + username + " 请求加入 " + roomName + "，已发送给 " + sentCount + " 个房主/管理员");
        } else {
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "房间加入请求发送失败，房主/管理员可能不在线");
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRoomJoinRequest(Message message) {
        String fromUsername = message.getFrom();
        String roomName = message.getContent();
        
        System.out.println("处理房间加入请求: 来自 " + fromUsername + " 的房间加入请求，房间: " + roomName);
        
        // 暂时直接接受所有房间加入请求
        // 未来应该添加房间管理员系统，由管理员决定是否接受
        
        // 查找房间
        String roomId = null;
        for (String rId : messageRouter.getRooms().keySet()) {
            if (roomName.equals(messageRouter.getRooms().get(rId).getName())) {
                roomId = rId;
                break;
            }
        }
        
        if (roomId == null) {
            System.err.println("房间 " + roomName + " 不存在");
            return;
        }
        
        // 直接接受请求，发送ROOM_JOIN_RESPONSE消息
        // 暂时发送给请求者（未来应该发送给管理员）
        Message responseMsg = new Message(MessageType.ROOM_JOIN_RESPONSE, "server", fromUsername, "accept:" + roomName);
        send(messageCodec.encode(responseMsg));
        
        System.out.println("房间加入请求已接受: " + fromUsername + " 可以加入 " + roomName);
    }
    
    private void handleRoomJoinResponse(Message message) {
        String fromUsername = message.getFrom();
        String content = message.getContent();
        
        System.out.println("处理房间加入响应: 来自 " + fromUsername + " 的响应: " + content);
        
        // 解析响应内容
        String[] parts = content.split(":");
        if (parts.length != 2) {
            System.err.println("无效的房间加入响应格式: " + content);
            return;
        }
        
        String response = parts[0];
        String roomName = parts[1];
        
        // 暂时不做任何处理，因为请求已经在前端处理了
        // 未来可以在这里添加额外的逻辑，比如自动加入房间等
        
        System.out.println("房间加入响应已处理: " + fromUsername + " 对房间 " + roomName + " 的响应: " + response);
    }
    
    private void handleSetRoomAdmin(Message message) {
        String username = currentUser.getUsername();
        String roomName = message.getTo();
        String targetUserId = message.getContent();
        
        System.out.println("处理设置管理员请求: 用户 " + username + " 在房间 " + roomName + " 设置管理员 " + targetUserId);
        
        try (Connection connection = dbManager.getConnection()) {
            // 获取房间ID
            Room room = roomDAO.getRoomByName(roomName, connection);
            if (room == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "房间 " + roomName + " 不存在");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查当前用户是否为房主
            String currentUserRole = roomDAO.getUserRole(room.getId(), String.valueOf(currentUser.getId()), connection);
            if (!"OWNER".equals(currentUserRole)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "只有房主可以设置管理员");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查目标用户是否在房间中
            if (!roomDAO.isUserInRoom(room.getId(), targetUserId, connection)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "用户不在房间中");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查目标用户当前角色
            String targetUserRole = roomDAO.getUserRole(room.getId(), targetUserId, connection);
            if ("OWNER".equals(targetUserRole)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "不能将房主设置为管理员");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            if ("ADMIN".equals(targetUserRole)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "该用户已经是管理员");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 更新用户角色为管理员
            boolean success = roomDAO.updateUserRole(room.getId(), targetUserId, "ADMIN", connection);
            if (success) {
                Message successMsg = new Message(MessageType.SYSTEM, "server", username, "已成功设置管理员");
                send(messageCodec.encode(successMsg));
                
                // 通知房间中的所有用户刷新成员列表
                broadcastRoomUpdate(room.getId());
                
                System.out.println("设置管理员成功: " + targetUserId + " 成为房间 " + roomName + " 的管理员");
            } else {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "设置管理员失败");
                send(messageCodec.encode(errorMsg));
            }
        } catch (SQLException e) {
            System.err.println("设置管理员失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "设置管理员失败: " + e.getMessage());
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRemoveRoomAdmin(Message message) {
        String username = currentUser.getUsername();
        String roomName = message.getTo();
        String targetUserId = message.getContent();
        
        System.out.println("处理移除管理员请求: 用户 " + username + " 在房间 " + roomName + " 移除管理员 " + targetUserId);
        
        try (Connection connection = dbManager.getConnection()) {
            // 获取房间ID
            Room room = roomDAO.getRoomByName(roomName, connection);
            if (room == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "房间 " + roomName + " 不存在");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查当前用户是否为房主
            String currentUserRole = roomDAO.getUserRole(room.getId(), String.valueOf(currentUser.getId()), connection);
            if (!"OWNER".equals(currentUserRole)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "只有房主可以移除管理员");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查目标用户当前角色
            String targetUserRole = roomDAO.getUserRole(room.getId(), targetUserId, connection);
            if (!"ADMIN".equals(targetUserRole)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "该用户不是管理员");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 更新用户角色为普通成员
            boolean success = roomDAO.updateUserRole(room.getId(), targetUserId, "MEMBER", connection);
            if (success) {
                Message successMsg = new Message(MessageType.SYSTEM, "server", username, "已成功移除管理员");
                send(messageCodec.encode(successMsg));
                
                // 通知房间中的所有用户刷新成员列表
                broadcastRoomUpdate(room.getId());
                
                System.out.println("移除管理员成功: " + targetUserId + " 不再是房间 " + roomName + " 的管理员");
            } else {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "移除管理员失败");
                send(messageCodec.encode(errorMsg));
            }
        } catch (SQLException e) {
            System.err.println("移除管理员失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "移除管理员失败: " + e.getMessage());
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleUpdateUserSettings(Message message) {
        String username = currentUser.getUsername();
        String settingsJson = message.getContent();
        
        System.out.println("处理用户设置更新请求: 用户 " + username + " 更新设置");
        
        try {
            // 解析JSON设置
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> settings = gson.fromJson(settingsJson, java.util.Map.class);
            
            // 获取acceptTemporaryChat设置
            Boolean acceptTemporaryChat = (Boolean) settings.get("acceptTemporaryChat");
            if (acceptTemporaryChat == null) {
                acceptTemporaryChat = true; // 默认值
            }
            
            // 更新数据库中的用户设置
            try (Connection connection = dbManager.getConnection()) {
                UserDAO userDAO = new UserDAO();
                boolean success = userDAO.updateAcceptTemporaryChat(currentUser.getId(), acceptTemporaryChat, connection);
                
                if (success) {
                    Message successMsg = new Message(MessageType.SYSTEM, "server", username, "用户设置更新成功");
                    send(messageCodec.encode(successMsg));
                    System.out.println("用户设置更新成功: " + username + " acceptTemporaryChat=" + acceptTemporaryChat);
                } else {
                    Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "用户设置更新失败");
                    send(messageCodec.encode(errorMsg));
                }
            }
        } catch (Exception e) {
            System.err.println("更新用户设置失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "更新用户设置失败: " + e.getMessage());
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleUpdateRoomSettings(Message message) {
        String username = currentUser.getUsername();
        String roomName = message.getTo();
        String settingsJson = message.getContent();
        
        System.out.println("处理房间设置更新请求: 用户 " + username + " 更新房间 " + roomName + " 的设置");
        
        try {
            // 解析JSON设置
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> settings = gson.fromJson(settingsJson, java.util.Map.class);
            
            // 获取房间ID
            Room room = roomDAO.getRoomByName(roomName, dbManager.getConnection());
            if (room == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "房间 " + roomName + " 不存在");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查当前用户是否为房主
            String currentUserRole = roomDAO.getUserRole(room.getId(), String.valueOf(currentUser.getId()), dbManager.getConnection());
            if (!"OWNER".equals(currentUserRole)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "只有房主可以修改房间设置");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 更新房间设置
            try (Connection connection = dbManager.getConnection()) {
                // 更新acceptTemporaryChat设置
                Boolean acceptTemporaryChat = (Boolean) settings.get("acceptTemporaryChat");
                if (acceptTemporaryChat != null) {
                    roomDAO.updateRoomAcceptTemporaryChat(room.getId(), String.valueOf(currentUser.getId()), acceptTemporaryChat, connection);
                }
                
                // 更新房间类型
                String roomType = (String) settings.get("roomType");
                if (roomType != null && ("PUBLIC".equals(roomType) || "PRIVATE".equals(roomType))) {
                    roomDAO.updateRoomType(room.getId(), roomType, connection);
                }
                
                Message successMsg = new Message(MessageType.SYSTEM, "server", username, "房间设置更新成功");
                send(messageCodec.encode(successMsg));
                System.out.println("房间设置更新成功: " + roomName);
            }
        } catch (Exception e) {
            System.err.println("更新房间设置失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "更新房间设置失败: " + e.getMessage());
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private void handleRecallMessage(Message message) {
        String username = currentUser.getUsername();
        String content = message.getContent();
        
        System.out.println("处理撤回消息请求: 用户 " + username + " 请求撤回消息");
        
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = gson.fromJson(content, java.util.Map.class);
            
            String messageId = (String) data.get("messageId");
            String roomName = (String) data.get("roomName");
            
            if (messageId == null || roomName == null) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "消息ID或房间名不能为空");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查消息是否属于当前用户
            if (!isMessageOwner(messageId, username)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "只能撤回自己发送的消息");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 检查消息是否在可撤回时间内（2分钟）
            if (!isMessageWithinRecallTime(messageId)) {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "消息已超过撤回时间限制（2分钟）");
                send(messageCodec.encode(errorMsg));
                return;
            }
            
            // 从数据库中删除消息
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            boolean deleted = messageDAO.deleteMessage(messageId, dbManager.getConnection());
            if (deleted) {
                // 查找房间ID
                String roomId = null;
                for (String rId : messageRouter.getRooms().keySet()) {
                    if (roomName.equals(messageRouter.getRooms().get(rId).getName())) {
                        roomId = rId;
                        break;
                    }
                }
                
                if (roomId != null) {
                    Message successMsg = new Message(MessageType.RECALL_MESSAGE, username, roomName, content);
                    messageRouter.broadcastToRoom(roomId, messageCodec.encode(successMsg));
                    System.out.println("消息撤回成功: " + messageId);
                } else {
                    Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "房间不存在");
                    send(messageCodec.encode(errorMsg));
                }
            } else {
                Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "撤回消息失败");
                send(messageCodec.encode(errorMsg));
            }
        } catch (Exception e) {
            System.err.println("撤回消息失败: " + e.getMessage());
            e.printStackTrace();
            Message errorMsg = new Message(MessageType.SYSTEM, "server", username, "撤回消息失败: " + e.getMessage());
            send(messageCodec.encode(errorMsg));
        }
    }
    
    private boolean isMessageOwner(String messageId, String username) {
        try {
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            Message message = messageDAO.getMessageById(messageId, dbManager.getConnection());
            return message != null && message.getFrom().equals(username);
        } catch (Exception e) {
            System.err.println("检查消息所有者失败: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isMessageWithinRecallTime(String messageId) {
        try {
            server.sql.message.MessageDAO messageDAO = new server.sql.message.MessageDAO();
            Message message = messageDAO.getMessageById(messageId, dbManager.getConnection());
            if (message == null || message.getTime() == null) {
                return false;
            }
            
            // 解析消息时间
            java.time.LocalDateTime messageTime = java.time.LocalDateTime.parse(message.getTime(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long messageTimeMillis = messageTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long currentTime = System.currentTimeMillis();
            long timeDiff = (currentTime - messageTimeMillis) / 1000 / 60; // 转换为分钟
            
            return timeDiff <= 2;
        } catch (Exception e) {
            System.err.println("检查消息撤回时间失败: " + e.getMessage());
            return false;
        }
    }
    
    private void broadcastRoomUpdate(String roomId) {
        // 通知房间中的所有用户刷新成员列表
        Room room = messageRouter.getRooms().get(roomId);
        if (room != null) {
            // 这里可以添加广播消息来通知所有用户刷新成员列表
            // 暂时不实现，因为前端会定期刷新
            System.out.println("房间更新通知: 房间 " + room.getName() + " 的成员列表已更新");
        }
    }
    
    /**
     * 检查是否允许临时聊天
     * @param toUsername 接收者用户名
     * @param connection 数据库连接
     * @return true表示允许，false表示不允许
     * @throws SQLException SQL异常
     */
    private boolean checkTemporaryChatPermission(String toUsername, Connection connection) throws SQLException {
        // 1. 检查接收者是否全局接受临时聊天
        UserDAO userDAO = new UserDAO();
        User toUser = userDAO.getUserByUsername(toUsername, connection);
        if (toUser == null) {
            return false;
        }
        
        if (!toUser.isAcceptTemporaryChat()) {
            return false;
        }
        
        // 2. 如果发送者和接收者在同一个房间，检查房间设置
        String currentRoom = getCurrentRoom();
        if (currentRoom != null && !currentRoom.equals("system")) {
            Room room = messageRouter.getRooms().get(currentRoom);
            if (room != null) {
                // 只有PUBLIC房间才允许临时聊天
                if (!"PUBLIC".equals(room.getType())) {
                    return false;
                }
                
                // 检查接收者在当前房间是否接受临时聊天
                String toUserId = String.valueOf(toUser.getId());
                boolean acceptInRoom = roomDAO.isAcceptTemporaryChatInRoom(room.getId(), toUserId, connection);
                if (!acceptInRoom) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 获取当前房间
     * @return 当前房间名称，如果没有则返回null
     */
    private String getCurrentRoom() {
        // 从当前用户的会话中获取当前房间
        Session session = messageRouter.getSessions().get(String.valueOf(currentUser.getId()));
        if (session != null) {
            return session.getCurrentRoom();
        }
        return null;
    }
}