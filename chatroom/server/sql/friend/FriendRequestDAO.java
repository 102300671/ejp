package server.sql.friend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class FriendRequestDAO {
    
    public static class FriendRequest {
        public int id;
        public int fromUserId;
        public int toUserId;
        public String status;
        public Timestamp createdAt;
        public Timestamp updatedAt;
        public String fromUsername;
        public String toUsername;
    }
    
    public boolean sendFriendRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        String sql = "INSERT INTO friend_requests (from_user_id, to_user_id, status) VALUES (?, ?, 'PENDING')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean updateFriendRequestStatus(int requestId, String status, Connection connection) throws SQLException {
        String sql = "UPDATE friend_requests SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, requestId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public List<FriendRequest> getPendingRequests(int userId, Connection connection) throws SQLException {
        String sql = "SELECT fr.*, u1.username as from_username, u2.username as to_username " +
                     "FROM friend_requests fr " +
                     "JOIN user u1 ON fr.from_user_id = u1.id " +
                     "JOIN user u2 ON fr.to_user_id = u2.id " +
                     "WHERE fr.to_user_id = ? AND fr.status = 'PENDING' " +
                     "ORDER BY fr.created_at DESC";
        
        List<FriendRequest> requests = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FriendRequest request = new FriendRequest();
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
    
    public boolean hasPendingRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM friend_requests " +
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
    
    public FriendRequest getFriendRequest(int fromUserId, int toUserId, Connection connection) throws SQLException {
        String sql = "SELECT fr.*, u1.username as from_username, u2.username as to_username " +
                     "FROM friend_requests fr " +
                     "JOIN user u1 ON fr.from_user_id = u1.id " +
                     "JOIN user u2 ON fr.to_user_id = u2.id " +
                     "WHERE fr.from_user_id = ? AND fr.to_user_id = ? AND fr.status = 'PENDING'";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FriendRequest request = new FriendRequest();
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
    
    public List<FriendRequest> getAllFriendRequests(int userId, Connection connection) throws SQLException {
        String sql = "SELECT fr.*, u1.username as from_username, u2.username as to_username " +
                     "FROM friend_requests fr " +
                     "JOIN user u1 ON fr.from_user_id = u1.id " +
                     "JOIN user u2 ON fr.to_user_id = u2.id " +
                     "WHERE fr.from_user_id = ? OR fr.to_user_id = ? " +
                     "ORDER BY fr.updated_at DESC";
        
        List<FriendRequest> requests = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FriendRequest request = new FriendRequest();
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
}