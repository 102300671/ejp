package server.sql.friend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class FriendshipDAO {
    
    public static class Friendship {
        public int id;
        public int user1Id;
        public int user2Id;
        public Timestamp createdAt;
        public String user1Username;
        public String user2Username;
    }
    
    public boolean createFriendship(int user1Id, int user2Id, Connection connection) throws SQLException {
        String sql = "INSERT INTO friendships (user1_id, user2_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, user1Id);
            stmt.setInt(2, user2Id);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean removeFriendship(int user1Id, int user2Id, Connection connection) throws SQLException {
        String sql = "DELETE FROM friendships WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, user1Id);
            stmt.setInt(2, user2Id);
            stmt.setInt(3, user2Id);
            stmt.setInt(4, user1Id);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean areFriends(int user1Id, int user2Id, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM friendships " +
                     "WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, user1Id);
            stmt.setInt(2, user2Id);
            stmt.setInt(3, user2Id);
            stmt.setInt(4, user1Id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    public boolean areFriends(String username1, String username2, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM friendships f " +
                     "JOIN user u1 ON f.user1_id = u1.id " +
                     "JOIN user u2 ON f.user2_id = u2.id " +
                     "WHERE (u1.username = ? AND u2.username = ?) OR (u1.username = ? AND u2.username = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username1);
            stmt.setString(2, username2);
            stmt.setString(3, username2);
            stmt.setString(4, username1);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    public List<Friendship> getUserFriends(int userId, Connection connection) throws SQLException {
        String sql = "SELECT f.*, u1.username as user1_username, u2.username as user2_username " +
                     "FROM friendships f " +
                     "JOIN user u1 ON f.user1_id = u1.id " +
                     "JOIN user u2 ON f.user2_id = u2.id " +
                     "WHERE f.user1_id = ? OR f.user2_id = ? " +
                     "ORDER BY f.created_at DESC";
        
        List<Friendship> friendships = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Friendship friendship = new Friendship();
                    friendship.id = rs.getInt("id");
                    friendship.user1Id = rs.getInt("user1_id");
                    friendship.user2Id = rs.getInt("user2_id");
                    friendship.createdAt = rs.getTimestamp("created_at");
                    friendship.user1Username = rs.getString("user1_username");
                    friendship.user2Username = rs.getString("user2_username");
                    friendships.add(friendship);
                }
            }
        }
        return friendships;
    }
}