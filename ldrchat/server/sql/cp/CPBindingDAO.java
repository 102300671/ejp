package server.sql.cp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class CPBindingDAO {
    
    public static class CPBinding {
        public int id;
        public int user1Id;
        public int user2Id;
        public Timestamp createdAt;
        public String user1Username;
        public String user2Username;
    }
    
    /**
     * 创建CP绑定（每个用户只能绑定一个CP）
     */
    public boolean createCPBinding(int user1Id, int user2Id, Connection connection) throws SQLException {
        // 先检查用户是否已经有CP
        if (hasCP(user1Id, connection)) {
            throw new SQLException("用户已绑定CP，无法重复绑定");
        }
        if (hasCP(user2Id, connection)) {
            throw new SQLException("对方已绑定CP，无法绑定");
        }
        
        String sql = "INSERT INTO cp_bindings (user1_id, user2_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, user1Id);
            stmt.setInt(2, user2Id);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    /**
     * 解除CP绑定
     */
    public boolean removeCPBinding(int userId, Connection connection) throws SQLException {
        String sql = "DELETE FROM cp_bindings WHERE user1_id = ? OR user2_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    /**
     * 检查用户是否已绑定CP
     */
    public boolean hasCP(int userId, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM cp_bindings WHERE user1_id = ? OR user2_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * 获取用户的CP信息
     */
    public CPBinding getUserCP(int userId, Connection connection) throws SQLException {
        String sql = "SELECT cb.*, u1.username as user1_username, u2.username as user2_username " +
                     "FROM cp_bindings cb " +
                     "JOIN user u1 ON cb.user1_id = u1.id " +
                     "JOIN user u2 ON cb.user2_id = u2.id " +
                     "WHERE cb.user1_id = ? OR cb.user2_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    CPBinding binding = new CPBinding();
                    binding.id = rs.getInt("id");
                    binding.user1Id = rs.getInt("user1_id");
                    binding.user2Id = rs.getInt("user2_id");
                    binding.createdAt = rs.getTimestamp("created_at");
                    binding.user1Username = rs.getString("user1_username");
                    binding.user2Username = rs.getString("user2_username");
                    return binding;
                }
            }
        }
        return null;
    }
    
    /**
     * 获取用户的CP用户名
     */
    public String getCPUsername(int userId, Connection connection) throws SQLException {
        CPBinding binding = getUserCP(userId, connection);
        if (binding == null) {
            return null;
        }
        // 返回对方的用户名
        return binding.user1Id == userId ? binding.user2Username : binding.user1Username;
    }
    
    /**
     * 获取用户的CP ID
     */
    public Integer getCPUserId(int userId, Connection connection) throws SQLException {
        CPBinding binding = getUserCP(userId, connection);
        if (binding == null) {
            return null;
        }
        // 返回对方的用户ID
        return binding.user1Id == userId ? binding.user2Id : binding.user1Id;
    }
    
    /**
     * 创建CP会话（绑定成功后创建会话）
     */
    public Integer createCPConversation(int user1Id, int user2Id, Connection connection) throws SQLException {
        // 先创建会话
        String createConversationSql = "INSERT INTO conversations (name, type, created_at) VALUES (?, 'CP', CURRENT_TIMESTAMP)";
        int conversationId = -1;
        
        try (PreparedStatement stmt = connection.prepareStatement(createConversationSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "情侣会话");
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    conversationId = rs.getInt(1);
                }
            }
        }
        
        if (conversationId == -1) {
            throw new SQLException("创建会话失败");
        }
        
        // 添加用户1到会话
        String addMemberSql = "INSERT INTO conversation_members (conversation_id, user_id, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = connection.prepareStatement(addMemberSql)) {
            stmt.setInt(1, conversationId);
            stmt.setInt(2, user1Id);
            stmt.executeUpdate();
            
            stmt.setInt(2, user2Id);
            stmt.executeUpdate();
        }
        
        return conversationId;
    }
}
