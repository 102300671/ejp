package server.sql.activity;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 活动数据访问对象
 * 处理活动相关的数据库操作
 */
public class ActivityDAO {
    
    public ActivityDAO() {
    }
    
    /**
     * 创建新活动
     */
    public int createActivity(Map<String, Object> activity, Connection conn) throws SQLException {
        String sql = "INSERT INTO activities (club_id, title, description, activity_type, start_time, end_time, " +
                     "location, max_participants, registration_deadline, budget, status, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, (int) activity.get("clubId"));
            pstmt.setString(2, (String) activity.get("title"));
            pstmt.setString(3, (String) activity.get("description"));
            pstmt.setString(4, (String) activity.get("activityType"));
            pstmt.setTimestamp(5, Timestamp.valueOf((String) activity.get("startTime")));
            pstmt.setTimestamp(6, Timestamp.valueOf((String) activity.get("endTime")));
            pstmt.setString(7, (String) activity.get("location"));
            pstmt.setInt(8, (int) activity.getOrDefault("maxParticipants", 0));
            
            String deadline = (String) activity.get("registrationDeadline");
            if (deadline != null) {
                pstmt.setTimestamp(9, Timestamp.valueOf(deadline));
            } else {
                pstmt.setNull(9, Types.TIMESTAMP);
            }
            
            pstmt.setDouble(10, (double) activity.getOrDefault("budget", 0.0));
            pstmt.setInt(11, (int) activity.get("createdBy"));
            
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        return -1;
    }
    
    /**
     * 根据ID获取活动
     */
    public Map<String, Object> getActivityById(int activityId, Connection conn) throws SQLException {
        String sql = "SELECT a.*, c.name as club_name, u.username as creator_name " +
                     "FROM activities a " +
                     "JOIN club c ON a.club_id = c.id " +
                     "JOIN user u ON a.created_by = u.id " +
                     "WHERE a.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, activityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractActivityFromResultSet(rs);
                }
            }
        }
        return null;
    }
    
    /**
     * 获取社团的所有活动
     */
    public List<Map<String, Object>> getActivitiesByClub(int clubId, Connection conn) throws SQLException {
        List<Map<String, Object>> activities = new ArrayList<>();
        String sql = "SELECT a.*, u.username as creator_name " +
                     "FROM activities a " +
                     "JOIN user u ON a.created_by = u.id " +
                     "WHERE a.club_id = ? " +
                     "ORDER BY a.start_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, clubId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    activities.add(extractActivityFromResultSet(rs));
                }
            }
        }
        return activities;
    }
    
    /**
     * 获取所有已发布的活动
     */
    public List<Map<String, Object>> getPublishedActivities(Connection conn) throws SQLException {
        List<Map<String, Object>> activities = new ArrayList<>();
        String sql = "SELECT a.*, c.name as club_name, u.username as creator_name " +
                     "FROM activities a " +
                     "JOIN club c ON a.club_id = c.id " +
                     "JOIN user u ON a.created_by = u.id " +
                     "WHERE a.status IN ('PUBLISHED', 'IN_PROGRESS', 'COMPLETED') " +
                     "ORDER BY a.start_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    activities.add(extractActivityFromResultSet(rs));
                }
            }
        }
        return activities;
    }
    
    /**
     * 获取即将开始的活动
     */
    public List<Map<String, Object>> getUpcomingActivities(int limit, Connection conn) throws SQLException {
        List<Map<String, Object>> activities = new ArrayList<>();
        String sql = "SELECT a.*, c.name as club_name, u.username as creator_name " +
                     "FROM activities a " +
                     "JOIN club c ON a.club_id = c.id " +
                     "JOIN user u ON a.created_by = u.id " +
                     "WHERE a.status = 'PUBLISHED' AND a.start_time > NOW() " +
                     "ORDER BY a.start_time ASC " +
                     "LIMIT ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    activities.add(extractActivityFromResultSet(rs));
                }
            }
        }
        return activities;
    }
    
    /**
     * 搜索活动
     */
    public List<Map<String, Object>> searchActivities(String keyword, Connection conn) throws SQLException {
        List<Map<String, Object>> activities = new ArrayList<>();
        String sql = "SELECT a.*, c.name as club_name, u.username as creator_name " +
                     "FROM activities a " +
                     "JOIN club c ON a.club_id = c.id " +
                     "JOIN user u ON a.created_by = u.id " +
                     "WHERE (a.title LIKE ? OR a.description LIKE ?) AND a.status IN ('PUBLISHED', 'IN_PROGRESS') " +
                     "ORDER BY a.start_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    activities.add(extractActivityFromResultSet(rs));
                }
            }
        }
        return activities;
    }
    
    /**
     * 更新活动
     */
    public boolean updateActivity(int activityId, Map<String, Object> activity, Connection conn) throws SQLException {
        String sql = "UPDATE activities SET title = ?, description = ?, activity_type = ?, start_time = ?, end_time = ?, " +
                     "location = ?, max_participants = ?, registration_deadline = ?, budget = ?, status = ? " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, (String) activity.get("title"));
            pstmt.setString(2, (String) activity.get("description"));
            pstmt.setString(3, (String) activity.get("activityType"));
            pstmt.setTimestamp(4, Timestamp.valueOf((String) activity.get("startTime")));
            pstmt.setTimestamp(5, Timestamp.valueOf((String) activity.get("endTime")));
            pstmt.setString(6, (String) activity.get("location"));
            pstmt.setInt(7, (int) activity.getOrDefault("maxParticipants", 0));
            
            String deadline = (String) activity.get("registrationDeadline");
            if (deadline != null) {
                pstmt.setTimestamp(8, Timestamp.valueOf(deadline));
            } else {
                pstmt.setNull(8, Types.TIMESTAMP);
            }
            
            pstmt.setDouble(9, (double) activity.getOrDefault("budget", 0.0));
            pstmt.setString(10, (String) activity.getOrDefault("status", "DRAFT"));
            pstmt.setInt(11, activityId);
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 更新活动状态
     */
    public boolean updateActivityStatus(int activityId, String status, Connection conn) throws SQLException {
        String sql = "UPDATE activities SET status = ?";
        if ("PUBLISHED".equals(status)) {
            sql += ", published_at = CURRENT_TIMESTAMP";
        }
        sql += " WHERE id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, activityId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 删除活动
     */
    public boolean deleteActivity(int activityId, Connection conn) throws SQLException {
        String sql = "DELETE FROM activities WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, activityId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 获取活动报名列表
     */
    public List<Map<String, Object>> getActivityRegistrations(int activityId, Connection conn) throws SQLException {
        List<Map<String, Object>> registrations = new ArrayList<>();
        String sql = "SELECT ar.*, u.username, u.real_name, u.student_id, u.phone, " +
                     "r.username as approver_name " +
                     "FROM activity_registrations ar " +
                     "JOIN user u ON ar.user_id = u.id " +
                     "LEFT JOIN user r ON ar.approved_by = r.id " +
                     "WHERE ar.activity_id = ? " +
                     "ORDER BY ar.registration_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, activityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reg = new HashMap<>();
                    reg.put("id", rs.getInt("id"));
                    reg.put("activityId", rs.getInt("activity_id"));
                    reg.put("userId", rs.getInt("user_id"));
                    reg.put("username", rs.getString("username"));
                    reg.put("realName", rs.getString("real_name"));
                    reg.put("studentId", rs.getString("student_id"));
                    reg.put("phone", rs.getString("phone"));
                    reg.put("status", rs.getString("status"));
                    reg.put("registrationTime", rs.getTimestamp("registration_time"));
                    reg.put("approvalTime", rs.getTimestamp("approval_time"));
                    reg.put("approverName", rs.getString("approver_name"));
                    reg.put("isCheckedIn", rs.getBoolean("is_checked_in"));
                    reg.put("checkInTime", rs.getTimestamp("check_in_time"));
                    reg.put("notes", rs.getString("notes"));
                    registrations.add(reg);
                }
            }
        }
        return registrations;
    }
    
    /**
     * 创建活动报名
     */
    public boolean createRegistration(int activityId, int userId, String realName, String studentId, 
                                       String phone, String notes, Connection conn) throws SQLException {
        String sql = "INSERT INTO activity_registrations (activity_id, user_id, real_name, student_id, phone, notes, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'PENDING')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            pstmt.setString(3, realName);
            pstmt.setString(4, studentId);
            pstmt.setString(5, phone);
            pstmt.setString(6, notes);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 处理报名审批
     */
    public boolean processRegistration(int registrationId, int approverId, boolean approved, Connection conn) throws SQLException {
        String sql = "UPDATE activity_registrations SET status = ?, approved_by = ?, approval_time = CURRENT_TIMESTAMP " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, approved ? "APPROVED" : "REJECTED");
            pstmt.setInt(2, approverId);
            pstmt.setInt(3, registrationId);
            
            // 如果通过，更新活动报名人数
            if (approved) {
                updateParticipantCount(registrationId, conn);
            }
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 签到
     */
    public boolean checkIn(int registrationId, Connection conn) throws SQLException {
        String sql = "UPDATE activity_registrations SET is_checked_in = TRUE, check_in_time = CURRENT_TIMESTAMP " +
                     "WHERE id = ? AND status = 'APPROVED'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, registrationId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 获取用户的报名记录
     */
    public List<Map<String, Object>> getUserRegistrations(int userId, Connection conn) throws SQLException {
        List<Map<String, Object>> registrations = new ArrayList<>();
        String sql = "SELECT ar.*, a.title as activity_title, a.start_time, a.location, a.status as activity_status, " +
                     "c.name as club_name " +
                     "FROM activity_registrations ar " +
                     "JOIN activities a ON ar.activity_id = a.id " +
                     "JOIN club c ON a.club_id = c.id " +
                     "WHERE ar.user_id = ? " +
                     "ORDER BY ar.registration_time DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reg = new HashMap<>();
                    reg.put("id", rs.getInt("id"));
                    reg.put("activityId", rs.getInt("activity_id"));
                    reg.put("activityTitle", rs.getString("activity_title"));
                    reg.put("activityStartTime", rs.getTimestamp("start_time"));
                    reg.put("location", rs.getString("location"));
                    reg.put("clubName", rs.getString("club_name"));
                    reg.put("status", rs.getString("status"));
                    reg.put("isCheckedIn", rs.getBoolean("is_checked_in"));
                    reg.put("registrationTime", rs.getTimestamp("registration_time"));
                    registrations.add(reg);
                }
            }
        }
        return registrations;
    }
    
    /**
     * 检查用户是否已报名活动
     */
    public boolean hasRegistered(int activityId, int userId, Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM activity_registrations WHERE activity_id = ? AND user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * 取消报名
     */
    public boolean cancelRegistration(int activityId, int userId, Connection conn) throws SQLException {
        String sql = "UPDATE activity_registrations SET status = 'CANCELLED' WHERE activity_id = ? AND user_id = ? AND status = 'PENDING'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * 更新活动报名人数
     */
    private void updateParticipantCount(int registrationId, Connection conn) throws SQLException {
        String selectSql = "SELECT activity_id FROM activity_registrations WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setInt(1, registrationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int activityId = rs.getInt("activity_id");
                    String updateSql = "UPDATE activities SET current_participants = " +
                                       "(SELECT COUNT(*) FROM activity_registrations WHERE activity_id = ? AND status = 'APPROVED') " +
                                       "WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, activityId);
                        updateStmt.setInt(2, activityId);
                        updateStmt.executeUpdate();
                    }
                }
            }
        }
    }
    
    private Map<String, Object> extractActivityFromResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> activity = new HashMap<>();
        activity.put("id", rs.getInt("id"));
        activity.put("clubId", rs.getInt("club_id"));
        activity.put("title", rs.getString("title"));
        activity.put("description", rs.getString("description"));
        activity.put("activityType", rs.getString("activity_type"));
        activity.put("startTime", rs.getTimestamp("start_time"));
        activity.put("endTime", rs.getTimestamp("end_time"));
        activity.put("location", rs.getString("location"));
        activity.put("maxParticipants", rs.getInt("max_participants"));
        activity.put("currentParticipants", rs.getInt("current_participants"));
        activity.put("registrationDeadline", rs.getTimestamp("registration_deadline"));
        activity.put("posterUrl", rs.getString("poster_url"));
        activity.put("budget", rs.getDouble("budget"));
        activity.put("status", rs.getString("status"));
        activity.put("createdBy", rs.getInt("created_by"));
        activity.put("createdAt", rs.getTimestamp("created_at"));
        activity.put("publishedAt", rs.getTimestamp("published_at"));
        
        try {
            activity.put("clubName", rs.getString("club_name"));
        } catch (SQLException e) {
            // club_name 可能不存在
        }
        
        try {
            activity.put("creatorName", rs.getString("creator_name"));
        } catch (SQLException e) {
            // creator_name 可能不存在
        }
        
        return activity;
    }
}
