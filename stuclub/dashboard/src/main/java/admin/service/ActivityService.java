package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ActivityService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> getAllActivities() {
        String sql = "SELECT a.id, a.title, a.description, a.activity_type, a.start_time, " +
                     "a.end_time, a.location, a.max_participants, a.current_participants, " +
                     "a.registration_deadline, a.budget, a.status, a.club_id, " +
                     "c.name as club_name, a.created_at " +
                     "FROM activities a " +
                     "LEFT JOIN club c ON a.club_id = c.id " +
                     "ORDER BY a.start_time DESC";
        return jdbcTemplate.queryForList(sql);
    }
    
    public Map<String, Object> getActivityById(int activityId) {
        String sql = "SELECT a.id, a.title, a.description, a.activity_type, a.start_time, " +
                     "a.end_time, a.location, a.max_participants, a.current_participants, " +
                     "a.registration_deadline, a.budget, a.status, a.club_id, " +
                     "c.name as club_name, a.created_at " +
                     "FROM activities a " +
                     "LEFT JOIN club c ON a.club_id = c.id " +
                     "WHERE a.id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, activityId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Map<String, Object>> searchActivities(String searchTerm) {
        String sql = "SELECT a.id, a.title, a.activity_type, a.start_time, a.club_id, " +
                     "c.name as club_name, a.status " +
                     "FROM activities a " +
                     "LEFT JOIN club c ON a.club_id = c.id " +
                     "WHERE a.title LIKE ? OR c.name LIKE ? " +
                     "ORDER BY a.start_time DESC LIMIT 20";
        return jdbcTemplate.queryForList(sql, "%" + searchTerm + "%", "%" + searchTerm + "%");
    }
    
    public int getActivityCount() {
        String sql = "SELECT COUNT(*) FROM activities";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public List<Map<String, Object>> getRecentActivities(int limit) {
        String sql = "SELECT a.id, a.title, a.start_time, a.current_participants, " +
                     "a.max_participants, a.status, c.name as club_name " +
                     "FROM activities a " +
                     "LEFT JOIN club c ON a.club_id = c.id " +
                     "WHERE a.status = 'PUBLISHED' " +
                     "ORDER BY a.start_time DESC LIMIT ?";
        return jdbcTemplate.queryForList(sql, limit);
    }
    
    public boolean createActivity(String title, String description, String activityType,
                                  String startTime, String endTime, String location,
                                  int maxParticipants, String registrationDeadline,
                                  double budget, int clubId, int createdBy) {
        try {
            String sql = "INSERT INTO activities (title, description, activity_type, " +
                         "start_time, end_time, location, max_participants, current_participants, " +
                         "registration_deadline, budget, status, club_id, created_by, created_at) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 'DRAFT', ?, ?, NOW())";
            int result = jdbcTemplate.update(sql, title, description, activityType, 
                                             startTime, endTime, location, maxParticipants,
                                             registrationDeadline, budget, clubId, createdBy);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateActivity(int activityId, String title, String description, 
                                  String activityType, String startTime, String endTime,
                                  String location, int maxParticipants, String registrationDeadline,
                                  double budget, String status) {
        try {
            String sql = "UPDATE activities SET title = ?, description = ?, activity_type = ?, " +
                         "start_time = ?, end_time = ?, location = ?, max_participants = ?, " +
                         "registration_deadline = ?, budget = ?, status = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, title, description, activityType,
                                             startTime, endTime, location, maxParticipants,
                                             registrationDeadline, budget, status, activityId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteActivity(int activityId) {
        try {
            String sql = "DELETE FROM activities WHERE id = ?";
            int result = jdbcTemplate.update(sql, activityId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean publishActivity(int activityId) {
        try {
            String sql = "UPDATE activities SET status = 'PUBLISHED' WHERE id = ?";
            int result = jdbcTemplate.update(sql, activityId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean completeActivity(int activityId) {
        try {
            String sql = "UPDATE activities SET status = 'COMPLETED' WHERE id = ?";
            int result = jdbcTemplate.update(sql, activityId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public List<Map<String, Object>> getActivityRegistrations(int activityId) {
        String sql = "SELECT ar.id, ar.user_id, ar.registered_at, ar.status, u.username " +
                     "FROM activity_registrations ar " +
                     "JOIN user u ON ar.user_id = u.id " +
                     "WHERE ar.activity_id = ? " +
                     "ORDER BY ar.registered_at DESC";
        return jdbcTemplate.queryForList(sql, activityId);
    }
    
    public boolean addRegistration(int activityId, int userId) {
        try {
            String sql = "INSERT INTO activity_registrations (activity_id, user_id, registered_at, status) " +
                         "VALUES (?, ?, NOW(), 'PENDING')";
            int result = jdbcTemplate.update(sql, activityId, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean approveRegistration(int registrationId) {
        try {
            String sql = "UPDATE activity_registrations SET status = 'APPROVED' WHERE id = ?";
            int result = jdbcTemplate.update(sql, registrationId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean rejectRegistration(int registrationId) {
        try {
            String sql = "UPDATE activity_registrations SET status = 'REJECTED' WHERE id = ?";
            int result = jdbcTemplate.update(sql, registrationId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}