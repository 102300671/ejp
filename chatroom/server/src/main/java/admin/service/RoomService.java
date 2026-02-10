package admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RoomService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> getAllRooms() {
        String sql = "SELECT r.id, r.room_name, r.room_type, r.created_at, " +
                     "(SELECT COUNT(*) FROM room_member WHERE room_id = r.id) as member_count " +
                     "FROM room r ORDER BY r.id";
        return jdbcTemplate.queryForList(sql);
    }
    
    public Map<String, Object> getRoomById(int roomId) {
        String sql = "SELECT r.id, r.room_name, r.room_type, r.created_at, " +
                     "(SELECT COUNT(*) FROM room_member WHERE room_id = r.id) as member_count " +
                     "FROM room r WHERE r.id = ?";
        try {
            return jdbcTemplate.queryForMap(sql, roomId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public Map<String, Object> getRoomByName(String roomName) {
        String sql = "SELECT r.id, r.room_name, r.room_type, r.created_at, " +
                     "(SELECT COUNT(*) FROM room_member WHERE room_id = r.id) as member_count " +
                     "FROM room r WHERE r.room_name = ?";
        try {
            return jdbcTemplate.queryForMap(sql, roomName);
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<Map<String, Object>> searchRooms(String searchTerm) {
        String sql = "SELECT r.id, r.room_name, r.room_type, r.created_at, " +
                     "(SELECT COUNT(*) FROM room_member WHERE room_id = r.id) as member_count " +
                     "FROM room r WHERE r.room_name LIKE ? ORDER BY r.room_name LIMIT 20";
        return jdbcTemplate.queryForList(sql, "%" + searchTerm + "%");
    }
    
    public int getRoomCount() {
        String sql = "SELECT COUNT(*) FROM room";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    public boolean createRoom(String roomName, String roomType) {
        try {
            String sql = "INSERT INTO room (room_name, room_type, created_at) VALUES (?, ?, NOW())";
            int result = jdbcTemplate.update(sql, roomName, roomType);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateRoomName(int roomId, String newName) {
        try {
            String sql = "UPDATE room SET room_name = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, newName, roomId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateRoomType(int roomId, String roomType) {
        try {
            String sql = "UPDATE room SET room_type = ? WHERE id = ?";
            int result = jdbcTemplate.update(sql, roomType, roomId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteRoom(int roomId) {
        try {
            String sql = "DELETE FROM room WHERE id = ?";
            int result = jdbcTemplate.update(sql, roomId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public List<Map<String, Object>> getRoomMembers(int roomId) {
        String sql = "SELECT rm.user_id, u.username, rm.role, rm.joined_at " +
                     "FROM room_member rm " +
                     "JOIN user u ON rm.user_id = u.id " +
                     "WHERE rm.room_id = ? " +
                     "ORDER BY rm.role, rm.joined_at";
        return jdbcTemplate.queryForList(sql, roomId);
    }
    
    public boolean addUserToRoom(int roomId, int userId, String role) {
        try {
            String sql = "INSERT INTO room_member (room_id, user_id, role, joined_at) VALUES (?, ?, ?, NOW())";
            int result = jdbcTemplate.update(sql, roomId, userId, role);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean updateUserRole(int roomId, int userId, String newRole) {
        try {
            String sql = "UPDATE room_member SET role = ? WHERE room_id = ? AND user_id = ?";
            int result = jdbcTemplate.update(sql, newRole, roomId, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean removeUserFromRoom(int roomId, int userId) {
        try {
            String sql = "DELETE FROM room_member WHERE room_id = ? AND user_id = ?";
            int result = jdbcTemplate.update(sql, roomId, userId);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
