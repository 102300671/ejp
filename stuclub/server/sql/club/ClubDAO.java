package server.sql.club;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import server.club.*;
import server.network.router.MessageRouter;

public class ClubDAO {
    private MessageRouter messageRouter;
    
    public ClubDAO(MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }
    
    public void insertPublicClub(PublicClub club, Connection conn) throws SQLException {
        String sql = "insert into club (name, category) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, club.getName());
            pstmt.setString(2, "PUBLIC");
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    club.setId(String.valueOf(generatedKeys.getInt(1)));
                }
            }
        }
    }
    
    public void insertPrivateClub(PrivateClub club, Connection conn) throws SQLException {
        String sql = "insert into club (name, category) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, club.getName());
            pstmt.setString(2, "PRIVATE");
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    club.setId(String.valueOf(generatedKeys.getInt(1)));
                }
            }
        }
    }
    
    public Club getClubById(String clubId, Connection conn) throws SQLException {
        String sql = "select id, name, category from club where id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("name");
                    String type = rs.getString("category");
                    
                    Club club;
                    if ("PUBLIC".equals(type)) {
                        club = new PublicClub(name, id, messageRouter);
                    } else {
                        club = new PrivateClub(name, id, messageRouter);
                    }
                    
                    loadClubOwnersAndAdmins(club, id, conn);
                    
                    return club;
                }
            }
        }
        return null;
    }
    
    public List<Club> getPublicClubs(Connection conn) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "select id, name from club where category = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "PUBLIC");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("name");
                    Club club = new PublicClub(name, id, messageRouter);
                    
                    loadClubOwnersAndAdmins(club, id, conn);
                    
                    clubs.add(club);
                }
            }
        }
        return clubs;
    }
    
    public List<Club> getAllClubs(Connection conn) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "select r.id, r.name, r.category, c.id as conversation_id " +
                     "from club r " +
                     "left join conversation c on c.type = 'CLUB' and c.name COLLATE utf8mb4_unicode_ci = r.name COLLATE utf8mb4_unicode_ci";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("name");
                    String type = rs.getString("category");
                    Integer conversationId = rs.getObject("conversation_id") != null ? rs.getInt("conversation_id") : null;
                    
                    Club club;
                    if ("PUBLIC".equals(type)) {
                        club = new PublicClub(name, id, messageRouter);
                    } else {
                        club = new PrivateClub(name, id, messageRouter);
                    }
                    
                    if (conversationId != null) {
                        club.setConversationId(conversationId);
                    }
                    
                    loadClubOwnersAndAdmins(club, id, conn);
                    
                    clubs.add(club);
                }
            }
        }
        return clubs;
    }
    
    public boolean joinClub(String clubId, String userId, Connection conn) throws SQLException {
        return joinClub(clubId, userId, "MEMBER", conn);
    }
    
    public boolean joinClub(String clubId, String userId, String role, Connection conn) throws SQLException {
        String sql = "insert into club_member (club_id, user_id, role) values (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            pstmt.setInt(2, Integer.parseInt(userId));
            pstmt.setString(3, role);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean updateUserRole(String clubId, String userId, String role, Connection conn) throws SQLException {
        String sql = "update club_member set role = ? where club_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role);
            pstmt.setInt(2, Integer.parseInt(clubId));
            pstmt.setInt(3, Integer.parseInt(userId));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public String getUserRole(String clubId, String userId, Connection conn) throws SQLException {
        String sql = "select role from club_member where club_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        }
        return null;
    }
    
    public List<java.util.Map<String, Object>> getClubMembersWithRoles(String clubId, Connection conn) throws SQLException {
        List<java.util.Map<String, Object>> members = new ArrayList<>();
        String sql = "select rm.user_id, u.username, rm.role, rm.joined_at, u.status from club_member rm " +
                     "join user u on rm.user_id = u.id where rm.club_id = ? order by rm.role, rm.joined_at";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> memberInfo = new java.util.HashMap<>();
                    memberInfo.put("userId", rs.getInt("user_id"));
                    memberInfo.put("username", rs.getString("username"));
                    memberInfo.put("role", rs.getString("role"));
                    memberInfo.put("joinedAt", rs.getTimestamp("joined_at") != null ? rs.getTimestamp("joined_at").toString() : null);
                    memberInfo.put("status", rs.getString("status"));
                    members.add(memberInfo);
                }
            }
        }
        return members;
    }
    
    public boolean leaveClub(String clubId, String userId, Connection conn) throws SQLException {
        String sql = "delete from club_member where club_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            pstmt.setInt(2, Integer.parseInt(userId));
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    public boolean isUserInClub(String clubId, String userId, Connection conn) throws SQLException {
        String sql = "select count(*) from club_member where club_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    public List<String> getClubMembers(String clubId, Connection conn) throws SQLException {
        List<String> memberIds = new ArrayList<>();
        String sql = "select user_id from club_member where club_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    memberIds.add(String.valueOf(rs.getInt("user_id")));
                }
            }
        }
        return memberIds;
    }
    
    public int getUserClubCount(String userId, Connection conn) throws SQLException {
        String sql = "select count(*) from club_member where user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
    
    public boolean clubExists(String clubName, Connection conn) throws SQLException {
        String sql = "select count(*) from club where name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, clubName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    public Club getClubByName(String clubName, Connection conn) throws SQLException {
        String sql = "select id, name, category from club where name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, clubName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("name");
                    String type = rs.getString("category");
                    
                    if ("PUBLIC".equals(type)) {
                        return new PublicClub(name, id, messageRouter);
                    } else {
                        return new PrivateClub(name, id, messageRouter);
                    }
                }
            }
        }
        return null;
    }
    
    public List<Club> searchClubs(String searchTerm, Connection conn) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "select id, name, category, created_at from club where name like ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + searchTerm + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("name");
                    String type = rs.getString("category");
                    String createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null;
                    
                    Club club;
                    if ("PUBLIC".equals(type)) {
                        club = new PublicClub(name, id, messageRouter);
                    } else {
                        club = new PrivateClub(name, id, messageRouter);
                    }
                    
                    int memberCount = getMemberCount(id, conn);
                    club.setMemberCount(memberCount);
                    
                    club.setCreatedAt(createdAt);
                    
                    clubs.add(club);
                }
            }
        }
        return clubs;
    }
    
    private int getMemberCount(String clubId, Connection conn) throws SQLException {
        String sql = "select count(*) as count from club_member where club_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        return 0;
    }
    
    private void loadClubOwnersAndAdmins(Club club, String clubId, Connection conn) throws SQLException {
        String sql = "select user_id, role from club_member where club_id = ? and role in ('PRESIDENT', 'ADMIN')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId = String.valueOf(rs.getInt("user_id"));
                    String role = rs.getString("role");
                    
                    if ("PRESIDENT".equals(role)) {
                        club.setOwnerId(userId);
                    } else if ("ADMIN".equals(role)) {
                        club.addAdmin(userId);
                    }
                }
            }
        }
    }
    
    public boolean isAcceptTemporaryChatInClub(String clubId, String userId, Connection conn) throws SQLException {
        return true;
    }
    
    public boolean updateClubAcceptTemporaryChat(String clubId, String userId, boolean acceptTemporaryChat, Connection conn) throws SQLException {
        return true;
    }
    
    public boolean updateClubType(String clubId, String clubType, Connection conn) throws SQLException {
        String sql = "update club set category = ? where id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, clubType);
            pstmt.setInt(2, Integer.parseInt(clubId));
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("更新社团类型: 社团ID=" + clubId + ", clubType=" + clubType + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    public boolean updateUserClubDisplayName(String clubId, String userId, String displayName, Connection conn) throws SQLException {
        String sql = "update club_member set display_name = ? where club_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (displayName == null || displayName.trim().isEmpty()) {
                pstmt.setNull(1, Types.VARCHAR);
            } else {
                pstmt.setString(1, displayName.trim());
            }
            pstmt.setInt(2, Integer.parseInt(clubId));
            pstmt.setInt(3, Integer.parseInt(userId));
            
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("更新社团显示名: 社团ID=" + clubId + ", 用户ID=" + userId + ", displayName=" + displayName + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    public String getUserClubDisplayName(String clubId, String userId, Connection conn) throws SQLException {
        String sql = "select display_name from club_member where club_id = ? and user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            pstmt.setInt(2, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("display_name");
                }
            }
        }
        return null;
    }
    
    public boolean isClubDisplayNameAvailable(String clubId, String displayName, Connection conn) throws SQLException {
        if (displayName == null || displayName.trim().isEmpty()) {
            return false;
        }
        
        String sql = "select count(*) from club_member where club_id = ? and display_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            pstmt.setString(2, displayName.trim());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return true;
    }
    
    public String getUserDisplayNameInClub(String clubId, String userId, String username, Connection conn) throws SQLException {
        String displayName = getUserClubDisplayName(clubId, userId, conn);
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return username;
    }
    
    public List<Club> getUserClubs(String userId, Connection conn) throws SQLException {
        List<Club> clubs = new ArrayList<>();
        String sql = "select c.id, c.name, c.category, c.created_at, cm.role, co.id as conversation_id " +
                     "from club c " +
                     "join club_member cm on c.id = cm.club_id " +
                     "left join conversation co on co.type = 'CLUB' and co.name COLLATE utf8mb4_unicode_ci = c.name COLLATE utf8mb4_unicode_ci " +
                     "where cm.user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(userId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getInt("id"));
                    String name = rs.getString("name");
                    String type = rs.getString("category");
                    String createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null;
                    Integer conversationId = rs.getObject("conversation_id") != null ? rs.getInt("conversation_id") : null;
                    
                    Club club;
                    if ("PUBLIC".equals(type)) {
                        club = new PublicClub(name, id, messageRouter);
                    } else {
                        club = new PrivateClub(name, id, messageRouter);
                    }
                    
                    club.setCreatedAt(createdAt);
                    if (conversationId != null) {
                        club.setConversationId(conversationId);
                    }
                    
                    clubs.add(club);
                }
            }
        }
        return clubs;
    }
    
    public String getCreatorId(String clubId, Connection conn) throws SQLException {
        String sql = "select owner_id from club where id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("owner_id");
                }
            }
        }
        return null;
    }
    
    public List<String> getAdminIds(String clubId, Connection conn) throws SQLException {
        List<String> adminIds = new ArrayList<>();
        String sql = "select user_id from club_member where club_id = ? and role = 'ADMIN'";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(clubId));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    adminIds.add(String.valueOf(rs.getInt("user_id")));
                }
            }
        }
        return adminIds;
    }
}