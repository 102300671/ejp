package server.network.router;
import server.network.session.Session;
import server.room.Room;
import server.room.PublicRoom;
import server.room.PrivateRoom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageRouter {
    // 管理所有活动会话，键为用户ID
    private final Map<String, Session> sessions;
    // 管理所有房间，键为房间ID
    private final Map<String, Room> rooms;
    // 管理用户所属的房间，键为用户ID，值为房间ID列表
    private final Map<String, List<String>> userRooms;

    public MessageRouter() {
        this.sessions = new ConcurrentHashMap<>();
        this.rooms = new ConcurrentHashMap<>();
        this.userRooms = new ConcurrentHashMap<>();
        System.out.println("消息路由器已初始化");
    }

    /**
     * 注册新会话
     * @param session 用户会话
     */
    public void registerSession(Session session) {
        if (session == null || session.getUserId() == null) {
            System.err.println("无效的会话对象");
            return;
        }

        String userId = session.getUserId();
        sessions.put(userId, session);
        userRooms.putIfAbsent(userId, new ArrayList<>());
        System.out.println("会话已注册: 用户ID=" + userId);
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
            
            // 从所有房间中移除用户
            List<String> roomIds = userRooms.remove(userId);
            if (roomIds != null) {
                for (String roomId : roomIds) {
                    Room room = rooms.get(roomId);
                    if (room != null) {
                        room.removeUser(userId);
                    }
                }
            }

            System.out.println("会话已注销: 用户ID=" + userId);
        }
    }

    /**
     * 创建新房间
     * @param name 房间名称
     * @param id 房间ID
     * @param isPublic 是否为公共房间
     * @return 创建的房间对象
     */
    public Room createRoom(String name, String id, boolean isPublic) {
        if (name == null || id == null || name.isEmpty() || id.isEmpty()) {
            System.err.println("无效的房间参数");
            return null;
        }

        if (rooms.containsKey(id)) {
            System.err.println("房间ID已存在: " + id);
            return null;
        }

        Room room = isPublic ? new PublicRoom(name, id, this) : new PrivateRoom(name, id, this);
        rooms.put(id, room);
        System.out.println("创建新房间: " + name + " (ID: " + id + ")");
        return room;
    }

    /**
     * 获取房间
     * @param roomId 房间ID
     * @return 房间对象，如果不存在则返回null
     */
    public Room getRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        return rooms.get(roomId);
    }

    /**
     * 将用户加入房间
     * @param userId 用户ID
     * @param roomId 房间ID
     * @return true表示加入成功，false表示失败
     */
    public boolean joinRoom(String userId, String roomId) {
        if (userId == null || roomId == null || userId.isEmpty() || roomId.isEmpty()) {
            System.err.println("无效的用户ID或房间ID");
            return false;
        }

        Session session = sessions.get(userId);
        Room room = rooms.get(roomId);

        if (session == null) {
            System.err.println("用户会话不存在: " + userId);
            return false;
        }

        if (room == null) {
            System.err.println("房间不存在: " + roomId);
            return false;
        }

        // 将用户添加到房间
        if (room.addUser(userId, session.getUsername())) {
            // 记录用户所属的房间
            userRooms.get(userId).add(roomId);
            System.out.println("用户" + session.getUsername() + "(ID: " + userId + ") 加入房间: " + room.getName());
            return true;
        }

        return false;
    }

    /**
     * 将用户从房间移除
     * @param userId 用户ID
     * @param roomId 房间ID
     * @return true表示移除成功，false表示失败
     */
    public boolean leaveRoom(String userId, String roomId) {
        if (userId == null || roomId == null || userId.isEmpty() || roomId.isEmpty()) {
            System.err.println("无效的用户ID或房间ID");
            return false;
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            System.err.println("房间不存在: " + roomId);
            return false;
        }

        // 从房间移除用户
        if (room.removeUser(userId)) {
            // 更新用户房间列表
            List<String> userRoomList = userRooms.get(userId);
            if (userRoomList != null) {
                userRoomList.remove(roomId);
            }
            System.out.println("用户" + userId + " 离开房间: " + room.getName());
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
     * 向房间广播消息
     * @param roomId 房间ID
     * @param message 消息内容
     * @return true表示广播成功，false表示失败
     */
    public boolean broadcastToRoom(String roomId, String message) {
        if (roomId == null || message == null || roomId.isEmpty() || message.isEmpty()) {
            System.err.println("无效的广播参数");
            return false;
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            System.err.println("房间不存在: " + roomId);
            return false;
        }

        try {
            Set<String> userIds;
            
            // 如果是system房间，向所有客户端广播消息
            if ("system".equals(room.getName())) {
                System.out.println("向所有客户端广播system消息: " + message);
                // 获取所有活动会话的用户ID
                userIds = sessions.keySet();
            } else {
                // 否则只向房间内的用户广播
                userIds = room.getUserIds();
                System.out.println("向房间" + room.getName() + " (ID: " + room.getId() + ") 广播消息，用户数量: " + userIds.size());
            }
            
            for (String userId : userIds) {
                Session session = sessions.get(userId);
                if (session != null && session.isActive()) {
                    session.getClientConnection().send(message);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("房间广播失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取指定用户的会话
     * @param userId 用户ID
     * @return 用户会话，如果不存在则返回null
     */
    public Session getSession(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return sessions.get(userId);
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
     * 获取房间数量
     * @return 房间数量
     */
    public int getRoomCount() {
        return rooms.size();
    }
    
    /**
     * 获取所有房间
     * @return 房间映射表
     */
    public Map<String, Room> getRooms() {
        return rooms;
    }
    
    /**
     * 添加房间到路由器
     * @param room 要添加的房间对象
     * @return true表示添加成功，false表示失败
     */
    public boolean addRoom(Room room) {
        if (room == null || room.getId() == null || room.getId().isEmpty()) {
            System.err.println("无效的房间对象");
            return false;
        }
        
        if (rooms.containsKey(room.getId())) {
            System.err.println("房间ID已存在: " + room.getId());
            return false;
        }
        
        rooms.put(room.getId(), room);
        System.out.println("房间已添加到路由器: " + room.getName() + " (ID: " + room.getId() + ")");
        return true;
    }
}
