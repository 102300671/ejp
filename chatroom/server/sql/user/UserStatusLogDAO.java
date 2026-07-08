package server.sql.user;

import server.message.Message;
import server.message.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserStatusLogDAO {

    public void logStatusChange(int userId, String username, String status, Connection connection) throws SQLException {
        String sql = "INSERT INTO user_status_log (user_id, username, status, change_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, status);
            stmt.executeUpdate();
            System.out.println("记录状态变更: 用户ID=" + userId + ", 用户名=" + username + ", 状态=" + status);
        }
    }

    public List<Message> getOfflineStatusChanges(int userId, String lastLogoutTime, Connection connection) throws SQLException {
        List<Message> statusMessages = new ArrayList<>();
        
        String sql = "SELECT usl.user_id, usl.username, usl.status, usl.change_time " +
                     "FROM user_status_log usl " +
                     "WHERE usl.change_time > ? " +
                     "AND usl.user_id != ? " +
                     "ORDER BY usl.change_time ASC";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, lastLogoutTime);
            stmt.setInt(2, userId);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String username = rs.getString("username");
                String status = rs.getString("status");
                String time = rs.getString("change_time").replace('T', ' ').substring(0, 19);
                
                Map<String, Object> statusData = new HashMap<>();
                statusData.put("username", username);
                statusData.put("status", status);
                statusData.put("isOnline", "ONLINE".equals(status));
                
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String content = gson.toJson(statusData);
                
                Message message = new Message(MessageType.USER_STATUS_UPDATE, username, content, time, null);
                statusMessages.add(message);
            }
        }
        
        System.out.println("获取用户离线期间的状态变更: " + statusMessages.size() + " 条");
        return statusMessages;
    }
}