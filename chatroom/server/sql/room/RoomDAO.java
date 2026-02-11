package server.sql.room;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import server.room.*;
import server.network.router.MessageRouter;

public class RoomDAO {
    private MessageRouter messageRouter;
    
    public RoomDAO(MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }
    
    public void insertPublicRoom(PublicRoom room, Connection conn) throws SQLException {
        String sql = "insert into room (room_name, room_type) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, room.getName());
            pstmt.setString(2, "PUBLIC");
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    room.setId(String.valueOf(generatedKeys.getInt(1)));
                }
            }
        }
    }
    
    public void insertPrivateRoom(PrivateRoom room, Connection conn) throws SQLException {
        String sql = "insert into room (room_name, room_type) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, room.getName());
            pstmt.setString(2, "PRIVATE");
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    room.setId(String.valueOf(generatedKeys.getInt(1)));
                }
            }
        }
    }
    
    public Room getRoomById(String roomId, Connection conn) throws SQLException {
        String sql = "select id, room_name, room_type from room where id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    String type = rs.getString("room_type");
                    
                    Room room;
                    if ("PUBLIC".equals(type)) {
                        room = new PublicRoom(name, id, messageRouter);
                    } else {
                        room = new PrivateRoom(name, id, messageRouter);
                    }
                    
                    // 加载房主和管理员信息
                    loadRoomOwnersAndAdmins(room, id, conn);
                    
                    return room;
                }
            }
        }
        return null;
    }
    
    public List<Room> getPublicRooms(Connection conn) throws SQLException {
        List<Room> rooms = new ArrayList<>();
        String sql = "select id, room_name from room where room_type = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "PUBLIC");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    rooms.add(new PublicRoom(name, id, messageRouter));
                }
            }
        }
        return rooms;
    }
    
    /**
     * 获取所有房间（包括公共和私人房间）
     * @param conn 数据库连接
     * @return 房间列表
     * @throws SQLException SQL异常
     */
    public List<Room> getAllRooms(Connection conn) throws SQLException {
        List<Room> rooms = new ArrayList<>();
        String sql = "select id, room_name, room_type from room";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    String type = rs.getString("room_type");
                    
                    if ("PUBLIC".equals(type)) {
                        rooms.add(new PublicRoom(name, id, messageRouter));
                    } else {
                        rooms.add(new PrivateRoom(name, id, messageRouter));
                    }
                }
            }
        }
        return rooms;
    }
    
    public boolean joinRoom(String roomId, String userId, Connection conn) throws SQLException {
        return joinRoom(roomId, userId, "MEMBER", conn);
    }
    
    public boolean joinRoom(String roomId, String userId, String role, Connection conn) throws SQLException {
        String sql = "insert into room_member (room_id, user_id, role) values (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            pstmt.setString(3, role);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean updateUserRole(String roomId, String userId, String role, Connection conn) throws SQLException {
        String sql = "update room_member set role = ? where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role);
            pstmt.setInt(2, Integer.parseInt(roomId));
            pstmt.setInt(3, Integer.parseInt(userId));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public String getUserRole(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "select role from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        }
        return null;
    }
    
    public List<java.util.Map<String, Object>> getRoomMembersWithRoles(String roomId, Connection conn) throws SQLException {
        List<java.util.Map<String, Object>> members = new ArrayList<>();
        String sql = "select rm.user_id, u.username, rm.role, rm.joined_at, u.status from room_member rm " +
                     "join user u on rm.user_id = u.id where rm.room_id = ? order by rm.role, rm.joined_at";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> memberInfo = new java.util.HashMap<>();
                    memberInfo.put("userId", rs.getInt("user_id"));
                    memberInfo.put("username", rs.getString("username"));
                    memberInfo.put("role", rs.getString("role"));
                    memberInfo.put("joinedAt", rs.getTimestamp("joined_at") != null ? rs.getTimestamp("joined_at").toString() : null);
                    memberInfo.put("status", rs.getString("status"));
                    members.add(memberInfo);
                }
            }
        }
        return members;
    }
    
    public boolean leaveRoom(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "delete from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean isUserInRoom(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "select count(*) from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    public List<String> getRoomMembers(String roomId, Connection conn) throws SQLException {
        List<String> memberIds = new ArrayList<>();
        String sql = "select user_id from room_member where room_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(String.valueOf(rs.getInt("user_id")));
                }
            }
        }
        return memberIds;
    }
    
    /**
     * 获取用户加入的房间数量
     * @param userId 用户ID
     * @param conn 数据库连接
     * @return 房间数量
     * @throws SQLException SQL异常
     */
    public int getUserRoomCount(String userId, Connection conn) throws SQLException {
        String sql = "select count(*) from room_member where user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    /**
     * 检查房间是否存在
     * @param roomName 房间名称
     * @param conn 数据库连接
     * @return true表示房间存在，false表示不存在
     * @throws SQLException SQL异常
     */
    public boolean roomExists(String roomName, Connection conn) throws SQLException {
        String sql = "select count(*) from room where room_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, roomName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * 根据房间名称获取房间
     * @param roomName 房间名称
     * @param conn 数据库连接
     * @return 房间对象，如果不存在则返回null
     * @throws SQLException SQL异常
     */
    public Room getRoomByName(String roomName, Connection conn) throws SQLException {
        String sql = "select id, room_name, room_type from room where room_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, roomName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    String type = rs.getString("room_type");
                    
                    if ("PUBLIC".equals(type)) {
                        return new PublicRoom(name, id, messageRouter);
                    } else {
                        return new PrivateRoom(name, id, messageRouter);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 搜索房间
     * @param searchTerm 搜索词
     * @param conn 数据库连接
     * @return 房间列表
     * @throws SQLException SQL异常
     */
    public List<Room> searchRooms(String searchTerm, Connection conn) throws SQLException {
        List<Room> rooms = new ArrayList<>();
        String sql = "select id, room_name, room_type, created_at from room where room_name like ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + searchTerm + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    String type = rs.getString("room_type");
                    String createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null;
                    
                    Room room;
                    if ("PUBLIC".equals(type)) {
                        room = new PublicRoom(name, id, messageRouter);
                    } else {
                        room = new PrivateRoom(name, id, messageRouter);
                    }
                    
                    // 设置成员数量
                    int memberCount = getMemberCount(id, conn);
                    room.setMemberCount(memberCount);
                    
                    // 设置创建时间
                    room.setCreatedAt(createdAt);
                    
                    rooms.add(room);
                }
            }
        }
        return rooms;
    }
    
    /**
     * 获取房间成员数量
     * @param roomId 房间ID
     * @param conn 数据库连接
     * @return 成员数量
     * @throws SQLException SQL异常
     */
    private int getMemberCount(String roomId, Connection conn) throws SQLException {
        String sql = "select count(*) as count from room_member where room_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return 0;
    }
    
    /**
     * 加载房间的房主和管理员信息
     * @param room 房间对象
     * @param roomId 房间ID
     * @param conn 数据库连接
     * @throws SQLException SQL异常
     */
    private void loadRoomOwnersAndAdmins(Room room, String roomId, Connection conn) throws SQLException {
        String sql = "select user_id, role from room_member where room_id = ? and role in ('OWNER', 'ADMIN')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId = String.valueOf(rs.getInt("user_id"));
                    String role = rs.getString("role");
                    
                    if ("OWNER".equals(role)) {
                        room.setOwnerId(userId);
                    } else if ("ADMIN".equals(role)) {
                        room.addAdmin(userId);
                    }
                }
            }
        }
    }
    
    /**
     * 检查用户在房间中是否接受临时聊天
     * @param roomId 房间ID
     * @param userId 用户ID
     * @param conn 数据库连接
     * @return true表示接受临时聊天，false表示不接受
     * @throws SQLException SQL异常
     */
    public boolean isAcceptTemporaryChatInRoom(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "select accept_temporary_chat from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("accept_temporary_chat");
                }
            }
        }
        return true;
    }
    
    /**
     * 更新用户在房间中的临时聊天接受设置
     * @param roomId 房间ID
     * @param userId 用户ID
     * @param acceptTemporaryChat 是否接受临时聊天
     * @param conn 数据库连接
     * @return 更新成功返回true，否则返回false
     * @throws SQLException SQL异常
     */
    public boolean updateRoomAcceptTemporaryChat(String roomId, String userId, boolean acceptTemporaryChat, Connection conn) throws SQLException {
        String sql = "update room_member set accept_temporary_chat = ? where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, acceptTemporaryChat);
            pstmt.setInt(2, Integer.parseInt(roomId));
            pstmt.setInt(3, Integer.parseInt(userId));
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("更新房间临时聊天设置: 房间ID=" + roomId + ", 用户ID=" + userId + ", acceptTemporaryChat=" + acceptTemporaryChat + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    /**
     * 更新房间类型
     * @param roomId 房间ID
     * @param roomType 房间类型（PUBLIC或PRIVATE）
     * @param conn 数据库连接
     * @return 更新成功返回true，否则返回false
     * @throws SQLException SQL异常
     */
    public boolean updateRoomType(String roomId, String roomType, Connection conn) throws SQLException {
        String sql = "update room set room_type = ? where id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, roomType);
            pstmt.setInt(2, Integer.parseInt(roomId));
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("更新房间类型: 房间ID=" + roomId + ", roomType=" + roomType + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    /**
     * 更新用户在房间中的显示名
     * @param roomId 房间ID
     * @param userId 用户ID
     * @param displayName 显示名，如果为null或空字符串则清除显示名
     * @param conn 数据库连接
     * @return 更新成功返回true，否则返回false
     * @throws SQLException SQL异常
     */
    public boolean updateUserRoomDisplayName(String roomId, String userId, String displayName, Connection conn) throws SQLException {
        String sql = "update room_member set display_name = ? where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (displayName == null || displayName.trim().isEmpty()) {
                pstmt.setNull(1, Types.VARCHAR);
            } else {
                pstmt.setString(1, displayName.trim());
            }
            pstmt.setInt(2, Integer.parseInt(roomId));
            pstmt.setInt(3, Integer.parseInt(userId));
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("更新房间显示名: 房间ID=" + roomId + ", 用户ID=" + userId + ", displayName=" + displayName + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    /**
     * 获取用户在房间中的显示名
     * @param roomId 房间ID
     * @param userId 用户ID
     * @param conn 数据库连接
     * @return 显示名，如果未设置则返回null
     * @throws SQLException SQL异常
     */
    public String getUserRoomDisplayName(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "select display_name from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("display_name");
                }
            }
        }
        return null;
    }
    
    /**
     * 检查房间内显示名是否可用（唯一性检查）
     * @param roomId 房间ID
     * @param displayName 要检查的显示名
     * @param conn 数据库连接
     * @return true表示显示名可用，false表示已被占用
     * @throws SQLException SQL异常
     */
    public boolean isRoomDisplayNameAvailable(String roomId, String displayName, Connection conn) throws SQLException {
        if (displayName == null || displayName.trim().isEmpty()) {
            return false;
        }
        
        String sql = "select count(*) from room_member where room_id = ? and display_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setString(2, displayName.trim());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return true;
    }
    
    /**
     * 获取用户在房间中的显示名，如果未设置则返回用户名
     * @param roomId 房间ID
     * @param userId 用户ID
     * @param username 用户名（备用）
     * @param conn 数据库连接
     * @return 显示名或用户名
     * @throws SQLException SQL异常
     */
    public String getUserDisplayNameInRoom(String roomId, String userId, String username, Connection conn) throws SQLException {
        String displayName = getUserRoomDisplayName(roomId, userId, conn);
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return username;
    }
}