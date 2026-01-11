package server.sql.room;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import server.room.*;
import server.network.router.MessageRouter;

public class RoomDAO {
    private MessageRouter messageRouter;
    
    public RoomDAO(MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }
    
    public void insertPublicRoom(PublicRoom room, Connection conn) throws SQLException {
        String sql = "insert into room (room_name, room_type) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, room.getName());
            pstmt.setString(2, "PUBLIC");
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    room.setId(String.valueOf(generatedKeys.getInt(1)));
                }
            }
        }
    }
    
    public void insertPrivateRoom(PrivateRoom room, Connection conn) throws SQLException {
        String sql = "insert into room (room_name, room_type) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, room.getName());
            pstmt.setString(2, "PRIVATE");
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    room.setId(String.valueOf(generatedKeys.getInt(1)));
                }
            }
        }
    }
    
    public Room getRoomById(String roomId, Connection conn) throws SQLException {
        String sql = "select id, room_name, room_type from room where id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    String type = rs.getString("room_type");
                    
                    if ("PUBLIC".equals(type)) {
                        return new PublicRoom(name, id, messageRouter);
                    } else {
                        return new PrivateRoom(name, id, messageRouter);
                    }
                }
            }
        }
        return null;
    }
    
    public List<Room> getPublicRooms(Connection conn) throws SQLException {
        List<Room> rooms = new ArrayList<>();
        String sql = "select id, room_name from room where room_type = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "PUBLIC");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("room_name");
                    rooms.add(new PublicRoom(name, id, messageRouter));
                }
            }
        }
        return rooms;
    }
    
    public boolean joinRoom(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "insert into room_member (room_id, user_id) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean leaveRoom(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "delete from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean isUserInRoom(String roomId, String userId, Connection conn) throws SQLException {
        String sql = "select count(*) from room_member where room_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    public List<String> getRoomMembers(String roomId, Connection conn) throws SQLException {
        List<String> memberIds = new ArrayList<>();
        String sql = "select user_id from room_member where room_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(roomId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(String.valueOf(rs.getInt("user_id")));
                }
            }
        }
        return memberIds;
    }
}