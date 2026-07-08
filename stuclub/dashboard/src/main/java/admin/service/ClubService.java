package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ClubService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> getAllClubs() {
        String sql = "SELECT c.id, c.name, c.category, c.description, c.advisor, " +
                     "c.max_members, c.status, c.founder_id, c.created_at, " +
                     "u.username as founder_name, " +
                     "(SELECT COUNT(*) FROM club_member WHERE club_id = c.id) as member_count " +
                     "FROM club c " +
                     "LEFT JOIN user u ON c.founder_id = u.id " +
                     "ORDER BY c.created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }
    
    public Map<String, Object> getClubById(int clubId) {
        String sql = "SELECT c.id, c.name, c.category, c.description, c.advisor, " +
                     "c.max_members, c.status, c.founder_id, c.created_at, " +
                     "u.username as founder_name, " +
                     "(SELECT COUNT(*) FROM club_member WHERE club_id = c.id) as member_count " +
                     "FROM club c " +
                     "LEFT JOIN user u ON c.founder_id = u.id " +
                     "WHERE c.id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, clubId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Map<String, Object>> searchClubs(String searchTerm) {
        String sql = "SELECT c.id, c.name, c.category, c.description, c.advisor, " +
                     "c.max_members, c.status, c.founder_id, c.created_at, " +
                     "(SELECT COUNT(*) FROM club_member WHERE club_id = c.id) as member_count " +
                     "FROM club c " +
                     "WHERE c.name LIKE ? OR c.category LIKE ? OR c.advisor LIKE ? " +
                     "ORDER BY c.name LIMIT 20";
        return jdbcTemplate.queryForList(sql, "%" + searchTerm + "%", "%" + searchTerm + "%", "%" + searchTerm + "%");
    }
    
    public int getClubCount() {
        String sql = "SELECT COUNT(*) FROM club";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public int getPendingClubCount() {
        String sql = "SELECT COUNT(*) FROM club WHERE status = 'PENDING'";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public boolean createClub(String name, String category, String description, 
                              String advisor, int maxMembers, int founderId) {
        try {
            // 插入club记录
            String clubSql = "INSERT INTO club (name, category, description, advisor, max_members, founder_id, status, created_at) " +
                           "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NOW())";
            int clubResult = jdbcTemplate.update(clubSql, name, category, description, advisor, maxMembers, founderId);
            
            if (clubResult <= 0) {
                return false;
            }
            
            // 获取新创建的club ID
            Integer clubId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
            
            if (clubId == null) {
                return false;
            }
            
            // 创建对应的conversation记录
            String conversationName = name + "群聊";
            String conversationSql = "INSERT INTO conversation (type, name, club_id, created_at) VALUES ('CLUB', ?, ?, NOW())";
            int conversationResult = jdbcTemplate.update(conversationSql, conversationName, clubId);
            
            if (conversationResult <= 0) {
                System.err.println("社团创建成功但创建conversation失败，club_id: " + clubId);
                return false;
            }
            
            // 获取conversation ID
            Integer conversationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
            
            System.out.println("社团创建成功: club_id=" + clubId + ", conversation_id=" + conversationId);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateClub(int clubId, String name, String category, String description, 
                              String advisor, int maxMembers, String status) {
        try {
            String sql = "UPDATE club SET name = ?, category = ?, description = ?, advisor = ?, max_members = ?, status = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, name, category, description, advisor, maxMembers, status, clubId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteClub(int clubId) {
        try {
            String sql = "DELETE FROM club WHERE id = ?";
            int result = jdbcTemplate.update(sql, clubId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean approveClub(int clubId) {
        try {
            String sql = "UPDATE club SET status = 'APPROVED' WHERE id = ?";
            int result = jdbcTemplate.update(sql, clubId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean rejectClub(int clubId) {
        try {
            String sql = "UPDATE club SET status = 'REJECTED' WHERE id = ?";
            int result = jdbcTemplate.update(sql, clubId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public List<Map<String, Object>> getPendingClubs() {
        String sql = "SELECT c.id, c.name, c.category, c.founder_id, c.created_at, " +
                     "u.username as founder_name " +
                     "FROM club c " +
                     "LEFT JOIN user u ON c.founder_id = u.id " +
                     "WHERE c.status = 'PENDING' " +
                     "ORDER BY c.created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }
    
    public List<Map<String, Object>> getClubMembers(int clubId) {
        String sql = "SELECT cm.user_id, u.username, cm.role, cm.joined_at, cm.status " +
                     "FROM club_member cm " +
                     "JOIN user u ON cm.user_id = u.id " +
                     "WHERE cm.club_id = ? " +
                     "ORDER BY cm.role, cm.joined_at";
        return jdbcTemplate.queryForList(sql, clubId);
    }
    
    public boolean applyJoinClub(int clubId, int userId, String reason) {
        try {
            String checkSql = "SELECT COUNT(*) FROM club_member WHERE club_id = ? AND user_id = ?";
            int count = jdbcTemplate.queryForObject(checkSql, Integer.class, clubId, userId);
            if (count > 0) {
                return false;
            }
            
            String sql = "INSERT INTO club_member (club_id, user_id, role, join_type, join_reason, joined_at, status) VALUES (?, ?, 'MEMBER', 'APPLY', ?, NOW(), 'PENDING')";
            int result = jdbcTemplate.update(sql, clubId, userId, reason);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean applyJoinClubByUsername(int clubId, String username, String reason) {
        try {
            String userIdSql = "SELECT id FROM user WHERE username = ?";
            Integer userId = jdbcTemplate.queryForObject(userIdSql, Integer.class, username);
            if (userId == null) {
                return false;
            }
            
            return applyJoinClub(clubId, userId, reason);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean addMemberToClub(int clubId, int userId, String role) {
        try {
            String sql = "INSERT INTO club_member (club_id, user_id, role, joined_at, status) VALUES (?, ?, ?, NOW(), 'PENDING')";
            int result = jdbcTemplate.update(sql, clubId, userId, role);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean approveMember(int clubId, int userId) {
        try {
            String sql = "UPDATE club_member SET status = 'APPROVED', approved_at = NOW() WHERE club_id = ? AND user_id = ?";
            int result = jdbcTemplate.update(sql, clubId, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean removeMemberFromClub(int clubId, int userId) {
        try {
            String sql = "DELETE FROM club_member WHERE club_id = ? AND user_id = ?";
            int result = jdbcTemplate.update(sql, clubId, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateMemberRole(int clubId, int userId, String newRole) {
        try {
            String sql = "UPDATE club_member SET role = ? WHERE club_id = ? AND user_id = ?";
            int result = jdbcTemplate.update(sql, newRole, clubId, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}