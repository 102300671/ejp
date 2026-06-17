package server.sql.cp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class CPRequestDAO {
    
    public static class CPRequest {
        public int id;
        public int fromUserId;
        public int toUserId;
        public String status;
        public Timestamp createdAt;
        public Timestamp updatedAt;
        public String fromUsername;
        public String toUsername;
    }
    
    /**
     * 创建CP绑定请求（别名方法）
     */
    public boolean createCPRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        return sendCPRequest(fromUserId, toUserId, connection);
    }
    
    /**
     * 发送CP绑定请求
     */
    public boolean sendCPRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        // 检查用户是否已有CP
        CPBindingDAO cpBindingDAO = new CPBindingDAO();
        if (cpBindingDAO.hasCP(fromUserId, connection)) {
            throw new SQLException("发送方已绑定CP");
        }
        if (cpBindingDAO.hasCP(toUserId, connection)) {
            throw new SQLException("接收方已绑定CP");
        }
        
        // 检查是否已有待处理的请求
        if (hasPendingRequest(fromUserId, toUserId, connection)) {
            throw new SQLException("已存在待处理的请求");
        }
        
        String sql = "INSERT INTO cp_requests (from_user_id, to_user_id, status) VALUES (?, ?, 'PENDING')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    /**
     * 更新CP请求状态
     */
    public boolean updateCPRequestStatus(int requestId, String status, Connection connection) throws SQLException {
        String sql = "UPDATE cp_requests SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, requestId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    /**
     * 获取用户收到的待处理CP请求
     */
    public List<CPRequest> getPendingRequests(int userId, Connection connection) throws SQLException {
        String sql = "SELECT cr.*, u1.username as from_username, u2.username as to_username " +
                     "FROM cp_requests cr " +
                     "JOIN user u1 ON cr.from_user_id = u1.id " +
                     "JOIN user u2 ON cr.to_user_id = u2.id " +
                     "WHERE cr.to_user_id = ? AND cr.status = 'PENDING' " +
                     "ORDER BY cr.created_at DESC";
        
        List<CPRequest> requests = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CPRequest request = new CPRequest();
                    request.id = rs.getInt("id");
                    request.fromUserId = rs.getInt("from_user_id");
                    request.toUserId = rs.getInt("to_user_id");
                    request.status = rs.getString("status");
                    request.createdAt = rs.getTimestamp("created_at");
                    request.updatedAt = rs.getTimestamp("updated_at");
                    request.fromUsername = rs.getString("from_username");
                    request.toUsername = rs.getString("to_username");
                    requests.add(request);
                }
            }
        }
        return requests;
    }
    
    /**
     * 检查是否有待处理的请求
     */
    public boolean hasPendingRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM cp_requests " +
                     "WHERE from_user_id = ? AND to_user_id = ? AND status = 'PENDING'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * 获取特定的CP请求
     */
    public CPRequest getCPRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        String sql = "SELECT cr.*, u1.username as from_username, u2.username as to_username " +
                     "FROM cp_requests cr " +
                     "JOIN user u1 ON cr.from_user_id = u1.id " +
                     "JOIN user u2 ON cr.to_user_id = u2.id " +
                     "WHERE cr.from_user_id = ? AND cr.to_user_id = ? AND cr.status = 'PENDING'";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    CPRequest request = new CPRequest();
                    request.id = rs.getInt("id");
                    request.fromUserId = rs.getInt("from_user_id");
                    request.toUserId = rs.getInt("to_user_id");
                    request.status = rs.getString("status");
                    request.createdAt = rs.getTimestamp("created_at");
                    request.updatedAt = rs.getTimestamp("updated_at");
                    request.fromUsername = rs.getString("from_username");
                    request.toUsername = rs.getString("to_username");
                    return request;
                }
            }
        }
        return null;
    }
    
    /**
     * 接受CP请求并创建绑定
     */
    public boolean acceptCPRequest(int requestId, Connection connection) throws SQLException {
        // 获取请求信息
        String getRequestSql = "SELECT from_user_id, to_user_id FROM cp_requests WHERE id = ? AND status = 'PENDING'";
        Integer fromUserId = null;
        Integer toUserId = null;
        
        try (PreparedStatement stmt = connection.prepareStatement(getRequestSql)) {
            stmt.setInt(1, requestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    fromUserId = rs.getInt("from_user_id");
                    toUserId = rs.getInt("to_user_id");
                } else {
                    throw new SQLException("请求不存在或已处理");
                }
            }
        }
        
        // 开启事务
        connection.setAutoCommit(false);
        try {
            // 创建CP绑定
            CPBindingDAO cpBindingDAO = new CPBindingDAO();
            cpBindingDAO.createCPBinding(fromUserId, toUserId, connection);
            
            // 更新请求状态为已接受
            updateCPRequestStatus(requestId, "ACCEPTED", connection);
            
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    
    /**
     * 拒绝CP请求
     */
    public boolean rejectCPRequest(int requestId, Connection connection) throws SQLException {
        return updateCPRequestStatus(requestId, "REJECTED", connection);
    }
    
    /**
     * 获取特定请求的ID
     */
    public Integer getRequestId(int fromUserId, int toUserId, Connection connection) throws SQLException {
        String sql = "SELECT id FROM cp_requests WHERE from_user_id = ? AND to_user_id = ? AND status = 'PENDING'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }
    
    /**
     * 获取用户发送的待处理请求ID
     */
    public Integer getSentRequestId(int userId, Connection connection) throws SQLException {
        String sql = "SELECT id FROM cp_requests WHERE from_user_id = ? AND status = 'PENDING' LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }
    
    /**
     * 获取用户收到的待处理请求ID
     */
    public Integer getReceivedRequestId(int userId, Connection connection) throws SQLException {
        String sql = "SELECT id FROM cp_requests WHERE to_user_id = ? AND status = 'PENDING' LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }
    
    /**
     * 获取请求者的用户名
     */
    public String getRequesterUsername(int requestId, Connection connection) throws SQLException {
        String sql = "SELECT u.username FROM cp_requests cr JOIN user u ON cr.from_user_id = u.id WHERE cr.id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, requestId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
            }
        }
        return null;
    }
}
