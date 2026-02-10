package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MessageService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> getAllMessages(int limit, int offset) {
        String sql = "SELECT id, type, from_username, to_username, content, message_type, create_time, is_nsfw " +
                     "FROM messages ORDER BY create_time DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, limit, offset);
    }
    
    public List<Map<String, Object>> getRoomMessages(String roomName, int limit) {
        String sql = "SELECT id, type, from_username, to_username, content, create_time, is_nsfw " +
                     "FROM messages WHERE message_type = 'ROOM' AND to_username = ? " +
                     "ORDER BY create_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, roomName, limit);
    }
    
    public List<Map<String, Object>> getPrivateMessages(String user1, String user2, int limit) {
        String sql = "SELECT id, type, from_username, to_username, content, create_time, is_nsfw " +
                     "FROM messages WHERE message_type = 'PRIVATE' " +
                     "AND ((from_username = ? AND to_username = ?) OR (from_username = ? AND to_username = ?)) " +
                     "ORDER BY create_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, user1, user2, user2, user1, limit);
    }
    
    public int getMessageCount() {
        String sql = "SELECT COUNT(*) FROM messages";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public int getRoomMessageCount(String roomName) {
        String sql = "SELECT COUNT(*) FROM messages WHERE message_type = 'ROOM' AND to_username = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, roomName);
    }
    
    public int getPrivateMessageCount(String user1, String user2) {
        String sql = "SELECT COUNT(*) FROM messages WHERE message_type = 'PRIVATE' " +
                     "AND ((from_username = ? AND to_username = ?) OR (from_username = ? AND to_username = ?))";
        return jdbcTemplate.queryForObject(sql, Integer.class, user1, user2, user2, user1);
    }
    
    public List<Map<String, Object>> searchMessages(String searchTerm, int limit) {
        String sql = "SELECT id, type, from_username, to_username, content, message_type, create_time, is_nsfw " +
                     "FROM messages WHERE content LIKE ? " +
                     "ORDER BY create_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, "%" + searchTerm + "%", limit);
    }
    
    public boolean deleteMessage(int messageId) {
        try {
            String sql = "DELETE FROM messages WHERE id = ?";
            int result = jdbcTemplate.update(sql, messageId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public Map<String, Object> getMessageStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalMessages", getMessageCount());
        stats.put("roomMessages", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages WHERE message_type = 'ROOM'", Integer.class));
        stats.put("privateMessages", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages WHERE message_type = 'PRIVATE'", Integer.class));
        stats.put("nsfwMessages", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages WHERE is_nsfw = true", Integer.class));
        return stats;
    }
    
    public List<Map<String, Object>> getTopUsersByMessageCount(int limit) {
        String sql = "SELECT from_username, COUNT(*) as message_count " +
                     "FROM messages GROUP BY from_username " +
                     "ORDER BY message_count DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, limit);
    }
    
    public List<Map<String, Object>> getTopRoomsByMessageCount(int limit) {
        String sql = "SELECT to_username as room_name, COUNT(*) as message_count " +
                     "FROM messages WHERE message_type = 'ROOM' " +
                     "GROUP BY to_username ORDER BY message_count DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, limit);
    }
}
