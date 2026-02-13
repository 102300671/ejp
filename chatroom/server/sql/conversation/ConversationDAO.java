package server.sql.conversation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ConversationDAO {
    /**
     * 创建新会话
     * @param type 会话类型 (ROOM / FRIEND / TEMP)
     * @param name 会话名称
     * @param connection 数据库连接
     * @return 会话ID
     * @throws SQLException SQL异常
     */
    public int createConversation(String type, String name, Connection connection) throws SQLException {
        String sql = "INSERT INTO conversation (type, name) VALUES (?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, type);
            stmt.setString(2, name);
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to create conversation");
    }
    
    /**
     * 根据旧房间ID获取或创建对应的会话
     * @param roomId 旧房间ID
     * @param connection 数据库连接
     * @return 会话ID
     * @throws SQLException SQL异常
     */
    public int getOrCreateConversationFromOldRoom(int roomId, Connection connection) throws SQLException {
        // 尝试查找是否已存在对应的会话
        String findSql = "SELECT c.id FROM conversation c " +
                         "JOIN conversation_member cm ON c.id = cm.conversation_id " +
                         "JOIN room_member rm ON cm.username = (SELECT username FROM user WHERE id = rm.user_id) " +
                         "WHERE c.type = 'ROOM' AND rm.room_id = ? " +
                         "LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(findSql)) {
            stmt.setInt(1, roomId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        // 获取旧房间信息
        String roomSql = "SELECT room_name FROM room WHERE id = ?";
        String roomName = null;
        
        try (PreparedStatement stmt = connection.prepareStatement(roomSql)) {
            stmt.setInt(1, roomId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    roomName = rs.getString("room_name");
                }
            }
        }
        
        if (roomName == null) {
            throw new SQLException("Old room not found");
        }
        
        // 创建新会话
        int conversationId = createConversation("ROOM", roomName, connection);
        
        // 将会员从旧房间迁移到新会话
        String membersSql = "SELECT u.username, rm.role FROM room_member rm " +
                           "JOIN user u ON rm.user_id = u.id " +
                           "WHERE rm.room_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(membersSql)) {
            stmt.setInt(1, roomId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    String role = rs.getString("role");
                    addConversationMember(conversationId, username, role, connection);
                }
            }
        }
        
        return conversationId;
    }
    
    /**
     * 根据旧用户ID获取用户名
     * @param userId 旧用户ID
     * @param connection 数据库连接
     * @return 用户名
     * @throws SQLException SQL异常
     */
    public String getUsernameFromUserId(int userId, Connection connection) throws SQLException {
        String sql = "SELECT username FROM user WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
            }
        }
        
        throw new SQLException("User not found");
    }
    
    /**
     * 根据用户名获取用户ID
     * @param username 用户名
     * @param connection 数据库连接
     * @return 用户ID
     * @throws SQLException SQL异常
     */
    public int getUserIdFromUsername(String username, Connection connection) throws SQLException {
        String sql = "SELECT id FROM user WHERE username = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        throw new SQLException("User not found");
    }
    
    /**
     * 添加会话成员
     * @param conversationId 会话ID
     * @param username 用户名
     * @param role 角色
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void addConversationMember(int conversationId, String username, String role, Connection connection) throws SQLException {
        String sql = "INSERT INTO conversation_member (conversation_id, username, role) VALUES (?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            stmt.setString(2, username);
            stmt.setString(3, role);
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * 获取会话信息
     * @param conversationId 会话ID
     * @param connection 数据库连接
     * @return 会话信息
     * @throws SQLException SQL异常
     */
    public Conversation getConversation(int conversationId, Connection connection) throws SQLException {
        String sql = "SELECT id, type, name, created_at FROM conversation WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Conversation(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toString()
                    );
                }
            }
        }
        
        return null;
    }
    
    /**
     * 根据房间名获取会话信息
     * @param roomName 房间名
     * @param connection 数据库连接
     * @return 会话信息
     * @throws SQLException SQL异常
     */
    public Conversation getConversationByRoomName(String roomName, Connection connection) throws SQLException {
        String sql = "SELECT id, type, name, created_at FROM conversation WHERE type = 'ROOM' AND name = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, roomName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Conversation(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toString()
                    );
                }
            }
        }
        
        // 尝试从旧房间表中查找
        String oldRoomSql = "SELECT id FROM room WHERE room_name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(oldRoomSql)) {
            stmt.setString(1, roomName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int roomId = rs.getInt("id");
                    int conversationId = getOrCreateConversationFromOldRoom(roomId, connection);
                    return getConversation(conversationId, connection);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取或创建房间会话
     * @param roomName 房间名
     * @param connection 数据库连接
     * @return 会话信息
     * @throws SQLException SQL异常
     */
    public Conversation getOrCreateRoomConversation(String roomName, Connection connection) throws SQLException {
        // 尝试获取已存在的房间会话
        Conversation existingConversation = getConversationByRoomName(roomName, connection);
        if (existingConversation != null) {
            return existingConversation;
        }
        
        // 创建新的房间会话
        int conversationId = createConversation("ROOM", roomName, connection);
        return getConversation(conversationId, connection);
    }
    
    /**
     * 添加用户到房间会话
     * @param conversationId 会话ID
     * @param username 用户名
     * @param role 角色
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void addUserToRoomConversation(int conversationId, String username, String role, Connection connection) throws SQLException {
        // 检查用户是否已在会话中
        if (isConversationMember(conversationId, username, connection)) {
            return;
        }
        
        addConversationMember(conversationId, username, role, connection);
    }
    
    /**
     * 从房间会话中移除用户
     * @param conversationId 会话ID
     * @param username 用户名
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void removeUserFromRoomConversation(int conversationId, String username, Connection connection) throws SQLException {
        String sql = "DELETE FROM conversation_member WHERE conversation_id = ? AND username = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }
    
    /**
     * 获取所有公共房间会话
     * @param connection 数据库连接
     * @return 公共房间会话列表
     * @throws SQLException SQL异常
     */
    public List<Conversation> getAllPublicRoomConversations(Connection connection) throws SQLException {
        List<Conversation> roomConversations = new ArrayList<>();
        
        // 获取所有房间类型的会话
        String sql = "SELECT id, type, name, created_at FROM conversation WHERE type = 'ROOM'";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    roomConversations.add(new Conversation(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toString()
                    ));
                }
            }
        }
        
        // 从旧房间表中迁移未迁移的房间
        String oldRoomsSql = "SELECT id FROM room WHERE id NOT IN (" +
                             "SELECT rm.room_id FROM room_member rm " +
                             "JOIN conversation_member cm ON cm.username = (SELECT username FROM user WHERE id = rm.user_id) " +
                             "JOIN conversation c ON cm.conversation_id = c.id " +
                             "WHERE c.type = 'ROOM'" +
                             ")";
        
        try (PreparedStatement stmt = connection.prepareStatement(oldRoomsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int roomId = rs.getInt("id");
                    try {
                        int conversationId = getOrCreateConversationFromOldRoom(roomId, connection);
                        Conversation conversation = getConversation(conversationId, connection);
                        if (!roomConversations.contains(conversation)) {
                            roomConversations.add(conversation);
                        }
                    } catch (SQLException e) {
                        // 忽略迁移失败的房间
                        e.printStackTrace();
                    }
                }
            }
        }
        
        return roomConversations;
    }
    
    /**
     * 执行全量数据迁移
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void performFullDataMigration(Connection connection) throws SQLException {
        // 迁移所有旧房间
        String migrateRoomsSql = "SELECT id FROM room";
        
        try (PreparedStatement stmt = connection.prepareStatement(migrateRoomsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int roomId = rs.getInt("id");
                    try {
                        getOrCreateConversationFromOldRoom(roomId, connection);
                    } catch (SQLException e) {
                        // 记录迁移失败的房间
                        System.err.println("Failed to migrate room " + roomId + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // 迁移所有好友关系为好友会话
        String migrateFriendsSql = "SELECT u1.username as user1, u2.username as user2 " +
                                  "FROM friendships f " +
                                  "JOIN user u1 ON f.user1_id = u1.id " +
                                  "JOIN user u2 ON f.user2_id = u2.id";
        
        try (PreparedStatement stmt = connection.prepareStatement(migrateFriendsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String user1 = rs.getString("user1");
                    String user2 = rs.getString("user2");
                    try {
                        getOrCreatePrivateConversation(user1, user2, connection);
                    } catch (SQLException e) {
                        // 记录迁移失败的好友关系
                        System.err.println("Failed to migrate friendship between " + user1 + " and " + user2 + ": " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * 同步用户的所有会话
     * @param username 用户名
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void syncUserConversations(String username, Connection connection) throws SQLException {
        // 同步好友会话
        getUserFriendConversations(username, connection);
        
        // 同步房间会话
        // 从旧房间成员表中同步
        String syncRoomsSql = "SELECT r.id, r.room_name FROM room r " +
                              "JOIN room_member rm ON r.id = rm.room_id " +
                              "JOIN user u ON rm.user_id = u.id " +
                              "WHERE u.username = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(syncRoomsSql)) {
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int roomId = rs.getInt("id");
                    String roomName = rs.getString("room_name");
                    try {
                        getOrCreateConversationFromOldRoom(roomId, connection);
                    } catch (SQLException e) {
                        // 记录同步失败的房间
                        System.err.println("Failed to sync room " + roomName + " for user " + username + ": " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * 检查数据迁移状态
     * @param connection 数据库连接
     * @return 迁移状态信息
     * @throws SQLException SQL异常
     */
    public MigrationStatus checkMigrationStatus(Connection connection) throws SQLException {
        MigrationStatus status = new MigrationStatus();
        
        // 检查旧房间数量
        String oldRoomsSql = "SELECT COUNT(*) FROM room";
        try (PreparedStatement stmt = connection.prepareStatement(oldRoomsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    status.oldRoomsCount = rs.getInt(1);
                }
            }
        }
        
        // 检查新房间会话数量
        String newRoomsSql = "SELECT COUNT(*) FROM conversation WHERE type = 'ROOM'";
        try (PreparedStatement stmt = connection.prepareStatement(newRoomsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    status.newRoomsCount = rs.getInt(1);
                }
            }
        }
        
        // 检查旧好友关系数量
        String oldFriendsSql = "SELECT COUNT(*) FROM friendships";
        try (PreparedStatement stmt = connection.prepareStatement(oldFriendsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    status.oldFriendsCount = rs.getInt(1);
                }
            }
        }
        
        // 检查新好友会话数量
        String newFriendsSql = "SELECT COUNT(*) FROM conversation WHERE type = 'FRIEND'";
        try (PreparedStatement stmt = connection.prepareStatement(newFriendsSql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    status.newFriendsCount = rs.getInt(1);
                }
            }
        }
        
        return status;
    }
    
    /**
     * 迁移状态类
     */
    public static class MigrationStatus {
        public int oldRoomsCount = 0;
        public int newRoomsCount = 0;
        public int oldFriendsCount = 0;
        public int newFriendsCount = 0;
        
        @Override
        public String toString() {
            return "MigrationStatus{" +
                   "oldRoomsCount=" + oldRoomsCount +
                   ", newRoomsCount=" + newRoomsCount +
                   ", oldFriendsCount=" + oldFriendsCount +
                   ", newFriendsCount=" + newFriendsCount +
                   '}';
        }
    }
    
    /**
     * 获取会话成员列表
     * @param conversationId 会话ID
     * @param connection 数据库连接
     * @return 成员列表
     * @throws SQLException SQL异常
     */
    public List<ConversationMember> getConversationMembers(int conversationId, Connection connection) throws SQLException {
        String sql = "SELECT conversation_id, username, role, joined_at FROM conversation_member WHERE conversation_id = ?";
        
        List<ConversationMember> members = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(new ConversationMember(
                        rs.getInt("conversation_id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getTimestamp("joined_at").toString()
                    ));
                }
            }
        }
        
        return members;
    }
    
    /**
     * 获取用户的所有会话
     * @param username 用户名
     * @param connection 数据库连接
     * @return 会话列表
     * @throws SQLException SQL异常
     */
    public List<Conversation> getUserConversations(String username, Connection connection) throws SQLException {
        String sql = "SELECT c.id, c.type, c.name, c.created_at FROM conversation c " +
                     "JOIN conversation_member cm ON c.id = cm.conversation_id " +
                     "WHERE cm.username = ?";
        
        List<Conversation> conversations = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(new Conversation(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toString()
                    ));
                }
            }
        }
        
        return conversations;
    }
    
    /**
     * 根据类型获取会话
     * @param type 会话类型
     * @param username 用户名
     * @param connection 数据库连接
     * @return 会话列表
     * @throws SQLException SQL异常
     */
    public List<Conversation> getConversationsByType(String type, String username, Connection connection) throws SQLException {
        String sql = "SELECT c.id, c.type, c.name, c.created_at FROM conversation c " +
                     "JOIN conversation_member cm ON c.id = cm.conversation_id " +
                     "WHERE c.type = ? AND cm.username = ?";
        
        List<Conversation> conversations = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, type);
            stmt.setString(2, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(new Conversation(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toString()
                    ));
                }
            }
        }
        
        return conversations;
    }
    
    /**
     * 检查用户是否是会话成员
     * @param conversationId 会话ID
     * @param username 用户名
     * @param connection 数据库连接
     * @return 是否是成员
     * @throws SQLException SQL异常
     */
    public boolean isConversationMember(int conversationId, String username, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM conversation_member WHERE conversation_id = ? AND username = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            stmt.setString(2, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 获取会话成员数量
     * @param conversationId 会话ID
     * @param connection 数据库连接
     * @return 成员数量
     * @throws SQLException SQL异常
     */
    public int getConversationMemberCount(int conversationId, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM conversation_member WHERE conversation_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        return 0;
    }
    
    /**
     * 将会话类型从TEMP改为FRIEND
     * @param conversationId 会话ID
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void updateConversationType(int conversationId, String type, Connection connection) throws SQLException {
        String sql = "UPDATE conversation SET type = ? WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, type);
            stmt.setInt(2, conversationId);
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * 获取或创建私聊会话
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param connection 数据库连接
     * @return 会话信息
     * @throws SQLException SQL异常
     */
    public Conversation getOrCreatePrivateConversation(String user1, String user2, Connection connection) throws SQLException {
        // 尝试获取已存在的私聊会话（双向查找）
        String sql = "SELECT c.id, c.type, c.name, c.created_at FROM conversation c " +
                     "JOIN conversation_member cm1 ON c.id = cm1.conversation_id " +
                     "JOIN conversation_member cm2 ON c.id = cm2.conversation_id " +
                     "WHERE c.type IN ('FRIEND', 'TEMP') AND " +
                     "((cm1.username = ? AND cm2.username = ?) OR (cm1.username = ? AND cm2.username = ?))";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // 检查是否需要更新会话类型（如果用户已成为好友）
                    int conversationId = rs.getInt("id");
                    String currentType = rs.getString("type");
                    
                    if (currentType.equals("TEMP") && areFriends(user1, user2, connection)) {
                        updateConversationType(conversationId, "FRIEND", connection);
                        return getConversation(conversationId, connection);
                    }
                    
                    return new Conversation(
                        conversationId,
                        currentType,
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toString()
                    );
                }
            }
        }
        
        // 检查用户是否是好友
        boolean isFriends = areFriends(user1, user2, connection);
        String conversationType = isFriends ? "FRIEND" : "TEMP";
        
        // 创建新会话
        String conversationName = user1 + "_" + user2;
        int conversationId = createConversation(conversationType, conversationName, connection);
        
        // 添加两个用户为成员
        addConversationMember(conversationId, user1, "MEMBER", connection);
        addConversationMember(conversationId, user2, "MEMBER", connection);
        
        // 返回新创建的会话
        return getConversation(conversationId, connection);
    }
    
    /**
     * 检查两个用户是否是好友
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param connection 数据库连接
     * @return 是否是好友
     * @throws SQLException SQL异常
     */
    private boolean areFriends(String user1, String user2, Connection connection) throws SQLException {
        // 获取用户ID
        int userId1 = getUserIdFromUsername(user1, connection);
        int userId2 = getUserIdFromUsername(user2, connection);
        
        // 检查好友关系
        String sql = "SELECT COUNT(*) FROM friendships " +
                     "WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId1);
            stmt.setInt(2, userId2);
            stmt.setInt(3, userId2);
            stmt.setInt(4, userId1);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 当好友关系建立时，更新相应的临时会话
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void updateConversationOnFriendshipEstablished(String user1, String user2, Connection connection) throws SQLException {
        // 查找两个用户之间的临时会话
        String sql = "SELECT c.id FROM conversation c " +
                     "JOIN conversation_member cm1 ON c.id = cm1.conversation_id " +
                     "JOIN conversation_member cm2 ON c.id = cm2.conversation_id " +
                     "WHERE c.type = 'TEMP' AND " +
                     "((cm1.username = ? AND cm2.username = ?) OR (cm1.username = ? AND cm2.username = ?))";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int conversationId = rs.getInt(1);
                    updateConversationType(conversationId, "FRIEND", connection);
                }
            }
        }
    }
    
    /**
     * 获取用户的所有好友会话
     * @param username 用户名
     * @param connection 数据库连接
     * @return 好友会话列表
     * @throws SQLException SQL异常
     */
    public List<Conversation> getUserFriendConversations(String username, Connection connection) throws SQLException {
        List<Conversation> friendConversations = new ArrayList<>();
        
        // 获取用户的所有好友
        String friendsSql = "SELECT u.username FROM friendships f " +
                           "JOIN user u ON (f.user1_id = u.id OR f.user2_id = u.id) " +
                           "WHERE (f.user1_id = (SELECT id FROM user WHERE username = ?) OR " +
                           "       f.user2_id = (SELECT id FROM user WHERE username = ?)) " +
                           "AND u.username != ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(friendsSql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String friendUsername = rs.getString("username");
                    // 获取或创建好友会话
                    Conversation conversation = getOrCreatePrivateConversation(username, friendUsername, connection);
                    friendConversations.add(conversation);
                }
            }
        }
        
        return friendConversations;
    }
}
