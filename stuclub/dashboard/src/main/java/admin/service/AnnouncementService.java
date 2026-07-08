package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnnouncementService {
    
    @Autowired
    private DataSource dataSource;
    
    public List<Map<String, Object>> getAllAnnouncements() {
        List<Map<String, Object>> announcements = new ArrayList<>();
        String sql = "SELECT a.*, c.name as club_name, u.username as creator_name " +
                     "FROM announcements a " +
                     "LEFT JOIN club c ON a.club_id = c.id " +
                     "LEFT JOIN user u ON a.created_by = u.id " +
                     "ORDER BY a.is_pinned DESC, COALESCE(a.published_at, a.created_at) DESC";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> announcement = new HashMap<>();
                announcement.put("id", rs.getInt("id"));
                announcement.put("club_id", rs.getInt("club_id"));
                announcement.put("club_name", rs.getString("club_name"));
                announcement.put("title", rs.getString("title"));
                announcement.put("content", rs.getString("content"));
                announcement.put("priority", rs.getString("priority"));
                announcement.put("is_pinned", rs.getBoolean("is_pinned"));
                announcement.put("status", rs.getString("status"));
                announcement.put("created_by", rs.getInt("created_by"));
                announcement.put("creator_name", rs.getString("creator_name"));
                announcement.put("created_at", rs.getString("created_at"));
                announcement.put("updated_at", rs.getString("updated_at"));
                announcement.put("published_at", rs.getString("published_at"));
                announcements.add(announcement);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return announcements;
    }
    
    public Map<String, Object> getAnnouncementById(int id) {
        String sql = "SELECT a.*, c.name as club_name, u.username as creator_name " +
                     "FROM announcements a " +
                     "LEFT JOIN club c ON a.club_id = c.id " +
                     "LEFT JOIN user u ON a.created_by = u.id " +
                     "WHERE a.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> announcement = new HashMap<>();
                    announcement.put("id", rs.getInt("id"));
                    announcement.put("club_id", rs.getInt("club_id"));
                    announcement.put("club_name", rs.getString("club_name"));
                    announcement.put("title", rs.getString("title"));
                    announcement.put("content", rs.getString("content"));
                    announcement.put("priority", rs.getString("priority"));
                    announcement.put("is_pinned", rs.getBoolean("is_pinned"));
                    announcement.put("status", rs.getString("status"));
                    announcement.put("created_by", rs.getInt("created_by"));
                    announcement.put("creator_name", rs.getString("creator_name"));
                    announcement.put("created_at", rs.getString("created_at"));
                    announcement.put("updated_at", rs.getString("updated_at"));
                    announcement.put("published_at", rs.getString("published_at"));
                    return announcement;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    public boolean createAnnouncement(String title, String content, String priority,
                                     int isPinned, int clubId, int createdBy) {
        String sql = "INSERT INTO announcements (title, content, priority, is_pinned, status, club_id, created_by, created_at) " +
                     "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, NOW())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, content);
            stmt.setString(3, priority);
            stmt.setInt(4, isPinned);
            if (clubId > 0) {
                stmt.setInt(5, clubId);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }
            stmt.setInt(6, createdBy);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean publishAnnouncement(int id) {
        String sql = "UPDATE announcements SET status = 'PUBLISHED', published_at = NOW() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateAnnouncement(int id, String title, String content, String priority,
                                      int isPinned, String status, int clubId) {
        String sql = "UPDATE announcements SET title = ?, content = ?, priority = ?, is_pinned = ?, status = ?, club_id = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, content);
            stmt.setString(3, priority);
            stmt.setInt(4, isPinned);
            stmt.setString(5, status);
            if (clubId > 0) {
                stmt.setInt(6, clubId);
            } else {
                stmt.setNull(6, java.sql.Types.INTEGER);
            }
            stmt.setInt(7, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteAnnouncement(int id) {
        String sql = "DELETE FROM announcements WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
