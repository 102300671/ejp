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
        String sql = "SELECT m.id, m.type, m.from_username, m.conversation_id, m.content, m.message_type, m.create_time, m.is_nsfw, m.iv, " +
                     "c.name as conversation_name, c.type as conversation_type " +
                     "FROM messages m " +
                     "LEFT JOIN conversation c ON m.conversation_id = c.id " +
                     "ORDER BY m.create_time DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, limit, offset);
    }
    
    public List<Map<String, Object>> getConversationMessages(int conversationId, int limit) {
        String sql = "SELECT id, type, from_username, conversation_id, content, create_time, is_nsfw, iv " +
                     "FROM messages WHERE conversation_id = ? " +
                     "ORDER BY create_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, conversationId, limit);
    }
    
    public List<Map<String, Object>> getRoomMessages(String roomName, int limit) {
        String sql = "SELECT m.id, m.type, m.from_username, m.conversation_id, m.content, m.create_time, m.is_nsfw, m.iv " +
                     "FROM messages m " +
                     "JOIN conversation c ON m.conversation_id = c.id " +
                     "WHERE c.type = 'ROOM' AND c.name = ? " +
                     "ORDER BY m.create_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, roomName, limit);
    }
    
    public List<Map<String, Object>> getPrivateMessages(String user1, String user2, int limit) {
        String sql = "SELECT m.id, m.type, m.from_username, m.conversation_id, m.content, m.create_time, m.is_nsfw, m.iv " +
                     "FROM messages m " +
                     "JOIN conversation c ON m.conversation_id = c.id " +
                     "JOIN conversation_member cm1 ON c.id = cm1.conversation_id " +
                     "JOIN conversation_member cm2 ON c.id = cm2.conversation_id " +
                     "WHERE c.type IN ('FRIEND', 'TEMP') " +
                     "AND cm1.username = ? AND cm2.username = ? " +
                     "ORDER BY m.create_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, user1, user2, limit);
    }
    
    public int getMessageCount() {
        String sql = "SELECT COUNT(*) FROM messages";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public int getConversationMessageCount(int conversationId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE conversation_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, conversationId);
    }
    
    public int getRoomMessageCount(String roomName) {
        String sql = "SELECT COUNT(*) FROM messages m " +
                     "JOIN conversation c ON m.conversation_id = c.id " +
                     "WHERE c.type = 'ROOM' AND c.name = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, roomName);
    }
    
    public int getPrivateMessageCount(String user1, String user2) {
        String sql = "SELECT COUNT(*) FROM messages m " +
                     "JOIN conversation c ON m.conversation_id = c.id " +
                     "JOIN conversation_member cm1 ON c.id = cm1.conversation_id " +
                     "JOIN conversation_member cm2 ON c.id = cm2.conversation_id " +
                     "WHERE c.type IN ('FRIEND', 'TEMP') " +
                     "AND cm1.username = ? AND cm2.username = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, user1, user2);
    }
    
    public List<Map<String, Object>> searchMessages(String searchTerm, int limit) {
        String sql = "SELECT m.id, m.type, m.from_username, m.conversation_id, m.content, m.message_type, m.create_time, m.is_nsfw, m.iv, " +
                     "c.name as conversation_name, c.type as conversation_type " +
                     "FROM messages m " +
                     "LEFT JOIN conversation c ON m.conversation_id = c.id " +
                     "WHERE m.content LIKE ? " +
                     "ORDER BY m.create_time DESC LIMIT ?";
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
        stats.put("roomMessages", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM messages m JOIN conversation c ON m.conversation_id = c.id WHERE c.type = 'ROOM'", Integer.class));
        stats.put("privateMessages", jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM messages m JOIN conversation c ON m.conversation_id = c.id WHERE c.type IN ('FRIEND', 'TEMP')", Integer.class));
        stats.put("nsfwMessages", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages WHERE is_nsfw = true", Integer.class));
        return stats;
    }
    
    public List<Map<String, Object>> getTopUsersByMessageCount(int limit) {
        String sql = "SELECT from_username, COUNT(*) as message_count " +
                     "FROM messages GROUP BY from_username " +
                     "ORDER BY message_count DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, limit);
    }
    
    public List<Map<String, Object>> getTopConversationsByMessageCount(int limit) {
        String sql = "SELECT m.conversation_id, c.name as conversation_name, c.type as conversation_type, COUNT(*) as message_count " +
                     "FROM messages m " +
                     "LEFT JOIN conversation c ON m.conversation_id = c.id " +
                     "GROUP BY m.conversation_id, c.name, c.type " +
                     "ORDER BY message_count DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, limit);
    }
    
    public List<Map<String, Object>> getTopRoomsByMessageCount(int limit) {
        String sql = "SELECT c.id as conversation_id, c.name as room_name, COUNT(*) as message_count " +
                     "FROM messages m " +
                     "JOIN conversation c ON m.conversation_id = c.id " +
                     "WHERE c.type = 'ROOM' " +
                     "GROUP BY c.id, c.name ORDER BY message_count DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, limit);
    }
}
