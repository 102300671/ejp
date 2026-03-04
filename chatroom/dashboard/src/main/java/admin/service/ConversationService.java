package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ConversationService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> getAllConversations() {
        String sql = "SELECT c.id, c.type, c.name, c.created_at, " +
                     "(SELECT COUNT(*) FROM conversation_member WHERE conversation_id = c.id) as member_count, " +
                     "(SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count " +
                     "FROM conversation c ORDER BY c.id";
        return jdbcTemplate.queryForList(sql);
    }
    
    public Map<String, Object> getConversationById(int conversationId) {
        String sql = "SELECT c.id, c.type, c.name, c.created_at, " +
                     "(SELECT COUNT(*) FROM conversation_member WHERE conversation_id = c.id) as member_count, " +
                     "(SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count " +
                     "FROM conversation c WHERE c.id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, conversationId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Map<String, Object>> getConversationsByType(String type) {
        String sql = "SELECT c.id, c.type, c.name, c.created_at, " +
                     "(SELECT COUNT(*) FROM conversation_member WHERE conversation_id = c.id) as member_count, " +
                     "(SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count " +
                     "FROM conversation c WHERE c.type = ? ORDER BY c.created_at DESC";
        return jdbcTemplate.queryForList(sql, type);
    }
    
    public List<Map<String, Object>> getRoomConversations() {
        return getConversationsByType("ROOM");
    }
    
    public List<Map<String, Object>> getFriendConversations() {
        return getConversationsByType("FRIEND");
    }
    
    public List<Map<String, Object>> getTempConversations() {
        return getConversationsByType("TEMP");
    }
    
    public List<Map<String, Object>> getUserConversations(String username) {
        String sql = "SELECT c.id, c.type, c.name, c.created_at, cm.role, cm.joined_at, " +
                     "(SELECT COUNT(*) FROM messages WHERE conversation_id = c.id) as message_count " +
                     "FROM conversation c " +
                     "JOIN conversation_member cm ON c.id = cm.conversation_id " +
                     "WHERE cm.username = ? " +
                     "ORDER BY cm.joined_at DESC";
        return jdbcTemplate.queryForList(sql, username);
    }
    
    public List<Map<String, Object>> getConversationMembers(int conversationId) {
        String sql = "SELECT cm.conversation_id, cm.username, cm.role, cm.joined_at, u.id as user_id " +
                     "FROM conversation_member cm " +
                     "LEFT JOIN user u ON cm.username = u.username " +
                     "WHERE cm.conversation_id = ? " +
                     "ORDER BY cm.role, cm.joined_at";
        return jdbcTemplate.queryForList(sql, conversationId);
    }
    
    public int getConversationCount() {
        String sql = "SELECT COUNT(*) FROM conversation";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public int getConversationCountByType(String type) {
        String sql = "SELECT COUNT(*) FROM conversation WHERE type = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, type);
    }
    
    public boolean createConversation(String type, String name) {
        try {
            String sql = "INSERT INTO conversation (type, name, created_at) VALUES (?, ?, NOW())";
            int result = jdbcTemplate.update(sql, type, name);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteConversation(int conversationId) {
        try {
            String deleteMembersSql = "DELETE FROM conversation_member WHERE conversation_id = ?";
            jdbcTemplate.update(deleteMembersSql, conversationId);
            
            String deleteMessagesSql = "DELETE FROM messages WHERE conversation_id = ?";
            jdbcTemplate.update(deleteMessagesSql, conversationId);
            
            String deleteConversationSql = "DELETE FROM conversation WHERE id = ?";
            int result = jdbcTemplate.update(deleteConversationSql, conversationId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean addMember(int conversationId, String username, String role) {
        try {
            String sql = "INSERT INTO conversation_member (conversation_id, username, role, joined_at) VALUES (?, ?, ?, NOW())";
            int result = jdbcTemplate.update(sql, conversationId, username, role);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean removeMember(int conversationId, String username) {
        try {
            String sql = "DELETE FROM conversation_member WHERE conversation_id = ? AND username = ?";
            int result = jdbcTemplate.update(sql, conversationId, username);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateMemberRole(int conversationId, String username, String newRole) {
        try {
            String sql = "UPDATE conversation_member SET role = ? WHERE conversation_id = ? AND username = ?";
            int result = jdbcTemplate.update(sql, newRole, conversationId, username);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public Map<String, Object> getConversationStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalConversations", getConversationCount());
        stats.put("roomConversations", getConversationCountByType("ROOM"));
        stats.put("friendConversations", getConversationCountByType("FRIEND"));
        stats.put("tempConversations", getConversationCountByType("TEMP"));
        return stats;
    }
}
