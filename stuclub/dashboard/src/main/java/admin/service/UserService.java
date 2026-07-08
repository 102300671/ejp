package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> getAllUsers() {
        String sql = "SELECT id, username, real_name, student_id, phone, email, role, status, created_at, accept_temporary_chat FROM user ORDER BY id";
        return jdbcTemplate.queryForList(sql);
    }
    
    public Map<String, Object> getUserById(int userId) {
        String sql = "SELECT id, username, real_name, student_id, phone, email, role, status, created_at, accept_temporary_chat FROM user WHERE id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, userId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public Map<String, Object> getUserByUsername(String username) {
        String sql = "SELECT id, username, real_name, student_id, phone, email, role, status, created_at, accept_temporary_chat FROM user WHERE username = ?";
        try {
            return jdbcTemplate.queryForMap(sql, username);
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Map<String, Object>> searchUsers(String searchTerm) {
        String sql = "SELECT id, username, real_name, student_id, phone, email, role, status, created_at, accept_temporary_chat FROM user WHERE username LIKE ? OR real_name LIKE ? OR student_id LIKE ? ORDER BY id LIMIT 50";
        String term = "%" + searchTerm + "%";
        return jdbcTemplate.queryForList(sql, term, term, term);
    }
    
    public List<Map<String, Object>> getUsersByRole(String role) {
        String sql = "SELECT id, username, real_name, student_id, phone, email, role, status, created_at, accept_temporary_chat FROM user WHERE role = ? ORDER BY id";
        return jdbcTemplate.queryForList(sql, role);
    }
    
    public int getUserCount() {
        String sql = "SELECT COUNT(*) FROM user";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public int getUserCountByRole(String role) {
        String sql = "SELECT COUNT(*) FROM user WHERE role = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, role);
    }
    
    public boolean createUser(String username, String password, String realName, String studentId,
                              String phone, String email, String role) {
        try {
            String hashedPassword = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, password.toCharArray());
            String sql = "INSERT INTO user (username, password, real_name, student_id, phone, email, role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            int result = jdbcTemplate.update(sql, username, hashedPassword, realName, studentId, phone, email,
                                             role != null ? role : "STUDENT",
                                             new Timestamp(System.currentTimeMillis()));
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateUser(int userId, String realName, String studentId, String phone,
                              String email, String role, String status) {
        try {
            String sql = "UPDATE user SET real_name = ?, student_id = ?, phone = ?, email = ?, role = ?, status = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, realName, studentId, phone, email, role, status, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateUserPassword(int userId, String newPassword) {
        try {
            String hashedPassword = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
            String sql = "UPDATE user SET password = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, hashedPassword, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteUser(int userId) {
        try {
            String sql = "DELETE FROM user WHERE id = ?";
            int result = jdbcTemplate.update(sql, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateAcceptTemporaryChat(int userId, boolean accept) {
        try {
            String sql = "UPDATE user SET accept_temporary_chat = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, accept, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public List<Map<String, Object>> getUserFriends(int userId) {
        String sql = "SELECT u.id, u.username, f.status, f.created_at " +
                     "FROM friendship f " +
                     "JOIN user u ON (f.user_id_2 = u.id OR f.user_id_1 = u.id) " +
                     "WHERE (f.user_id_1 = ? OR f.user_id_2 = ?) AND u.id != ? " +
                     "AND f.status = 'ACCEPTED'";
        return jdbcTemplate.queryForList(sql, userId, userId, userId);
    }
    
    public List<Map<String, Object>> getUserRooms(int userId) {
        String sql = "SELECT r.id, r.room_name, r.room_type, rm.role, rm.joined_at " +
                     "FROM room_member rm " +
                     "JOIN room r ON rm.room_id = r.id " +
                     "WHERE rm.user_id = ? " +
                     "ORDER BY rm.joined_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }
    
    public boolean updateUserStatus(int userId, String status) {
        try {
            String sql = "UPDATE user SET status = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, status, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public Map<String, Object> getUserStatusStats() {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT status, COUNT(*) as count FROM user GROUP BY status";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        
        for (Map<String, Object> row : results) {
            String status = (String) row.get("status");
            Long count = ((Number) row.get("count")).longValue();
            stats.put(status.toLowerCase(), count);
        }
        
        return stats;
    }
    
    public Map<String, Object> getUserRoleStats() {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT role, COUNT(*) as count FROM user GROUP BY role";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        
        for (Map<String, Object> row : results) {
            String role = (String) row.get("role");
            Long count = ((Number) row.get("count")).longValue();
            stats.put(role.toLowerCase(), count);
        }
        
        return stats;
    }
}
