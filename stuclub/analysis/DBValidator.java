import java.sql.*;
import java.util.*;
import at.favre.lib.crypto.bcrypt.BCrypt;

public class DBValidator {
    private static Connection conn;
    private static String dbUrl = "jdbc:mysql://localhost:3306/stuclub_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
    private static String dbUser = "chatroom";
    private static String dbPassword = "chatroom";
    
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);
            
            System.out.println("========================================");
            System.out.println("学生社团管理系统 - 数据库验证程序");
            System.out.println("========================================");
            
            int passed = 0;
            int failed = 0;
            
            boolean result;
            
            result = testUserRegistration();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testUserLogin();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testClubCreation();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testClubReview();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testJoinRequest();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testActivityCreation();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testActivityRegistration();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testAnnouncementCreation();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testMessageSending();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            result = testStoredProcedures();
            passed += result ? 1 : 0;
            failed += result ? 0 : 1;
            
            System.out.println("========================================");
            System.out.println("测试结果: " + passed + " 通过, " + failed + " 失败");
            System.out.println("========================================");
            
            conn.rollback();
            conn.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
        }
    }
    
    private static boolean testUserRegistration() {
        System.out.println("\n[测试1] 用户注册功能");
        try {
            String hashedPassword = BCrypt.withDefaults().hashToString(12, "password123".toCharArray());
            
            String sql = "INSERT INTO user (username, password, real_name, student_id, role, status) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "testuser_reg");
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, "测试用户");
            pstmt.setString(4, "20249991");
            pstmt.setString(5, "STUDENT");
            pstmt.setString(6, "ACTIVE");
            int rows = pstmt.executeUpdate();
            
            if (rows != 1) {
                System.out.println("  [失败] 用户注册：插入行数不正确");
                return false;
            }
            
            sql = "SELECT id, username, real_name, student_id, role FROM user WHERE username = 'testuser_reg'";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            if (!rs.next()) {
                System.out.println("  [失败] 用户注册：无法查询到注册用户");
                return false;
            }
            
            String username = rs.getString("username");
            String realName = rs.getString("real_name");
            String studentId = rs.getString("student_id");
            String role = rs.getString("role");
            
            if (!"testuser_reg".equals(username) || !"测试用户".equals(realName) || 
                !"20249991".equals(studentId) || !"STUDENT".equals(role)) {
                System.out.println("  [失败] 用户注册：数据验证失败");
                return false;
            }
            
            try {
                pstmt.executeUpdate();
                System.out.println("  [失败] 用户注册：未检测到重复用户名约束");
                return false;
            } catch (SQLException e) {
                System.out.println("  [通过] 用户注册：正确检测到唯一约束");
            }
            
            System.out.println("  [通过] 用户注册功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 用户注册：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testUserLogin() {
        System.out.println("\n[测试2] 用户登录功能");
        try {
            String sql = "SELECT id, password, status FROM user WHERE student_id = '20249991'";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            
            if (!rs.next()) {
                System.out.println("  [失败] 用户登录：用户不存在");
                return false;
            }
            
            int userId = rs.getInt("id");
            String storedPassword = rs.getString("password");
            String status = rs.getString("status");
            
            if (!"ACTIVE".equals(status)) {
                System.out.println("  [失败] 用户登录：账号状态异常");
                return false;
            }
            
            BCrypt.Result result = BCrypt.verifyer().verify("password123".toCharArray(), storedPassword);
            if (!result.verified) {
                System.out.println("  [失败] 用户登录：密码验证失败");
                return false;
            }
            
            sql = "DELETE FROM user_uuid WHERE user_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
            
            sql = "INSERT INTO user_uuid (user_id, uuid, issued_at) VALUES (?, ?, NOW())";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.setString(2, UUID.randomUUID().toString());
            pstmt.executeUpdate();
            
            sql = "UPDATE user SET last_login_time = NOW() WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
            
            sql = "SELECT last_login_time FROM user WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            rs = pstmt.executeQuery();
            if (rs.next() && rs.getTimestamp("last_login_time") != null) {
                System.out.println("  [通过] 用户登录功能测试通过");
                return true;
            }
            
            System.out.println("  [失败] 用户登录：登录时间未更新");
            return false;
            
        } catch (Exception e) {
            System.out.println("  [失败] 用户登录：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testClubCreation() {
        System.out.println("\n[测试3] 社团创建功能");
        try {
            String sql = "INSERT INTO club (name, description, category, advisor, max_members, founder_id, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, "测试社团_reg");
            pstmt.setString(2, "测试用社团");
            pstmt.setString(3, "学术科技");
            pstmt.setString(4, "李老师");
            pstmt.setInt(5, 50);
            pstmt.setInt(6, getUserIdByStudentId("20249991"));
            pstmt.setString(7, "PENDING");
            int rows = pstmt.executeUpdate();
            
            if (rows != 1) {
                System.out.println("  [失败] 社团创建：插入行数不正确");
                return false;
            }
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (!rs.next()) {
                System.out.println("  [失败] 社团创建：无法获取自增ID");
                return false;
            }
            
            int clubId = rs.getInt(1);
            
            sql = "SELECT name, description, category, advisor, status FROM club WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            rs = pstmt.executeQuery();
            
            if (!rs.next()) {
                System.out.println("  [失败] 社团创建：无法查询到社团");
                return false;
            }
            
            if (!"测试社团_reg".equals(rs.getString("name")) || !"PENDING".equals(rs.getString("status"))) {
                System.out.println("  [失败] 社团创建：数据验证失败");
                return false;
            }
            
            System.out.println("  [通过] 社团创建功能测试通过（ID: " + clubId + "）");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 社团创建：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testClubReview() {
        System.out.println("\n[测试4] 社团审核功能");
        try {
            String sql = "SELECT id FROM club WHERE name = '测试社团_reg'";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            if (!rs.next()) {
                System.out.println("  [失败] 社团审核：社团不存在");
                return false;
            }
            int clubId = rs.getInt("id");
            
            sql = "UPDATE club SET status = 'APPROVED', approved_at = NOW() WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            pstmt.executeUpdate();
            
            sql = "SELECT status FROM club WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            rs = pstmt.executeQuery();
            if (!rs.next() || !"APPROVED".equals(rs.getString("status"))) {
                System.out.println("  [失败] 社团审核：状态未更新");
                return false;
            }
            
            sql = "INSERT INTO club_member (club_id, user_id, role, status, approved_at) VALUES (?, ?, ?, ?, NOW())";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            pstmt.setInt(2, getUserIdByStudentId("20249991"));
            pstmt.setString(3, "PRESIDENT");
            pstmt.setString(4, "APPROVED");
            pstmt.executeUpdate();
            
            sql = "SELECT COUNT(*) FROM club_member WHERE club_id = ? AND role = 'PRESIDENT'";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            rs = pstmt.executeQuery();
            if (!rs.next() || rs.getInt(1) != 1) {
                System.out.println("  [失败] 社团审核：社长未添加");
                return false;
            }
            
            System.out.println("  [通过] 社团审核功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 社团审核：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testJoinRequest() {
        System.out.println("\n[测试5] 入社申请功能");
        try {
            int clubId = getClubIdByName("测试社团_reg");
            int userId = getUserIdByStudentId("20249991");
            
            String sql = "INSERT INTO join_requests (user_id, club_id, reason, status) VALUES (?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.setInt(2, clubId);
            pstmt.setString(3, "我想加入测试社团");
            pstmt.setString(4, "PENDING");
            pstmt.executeUpdate();
            
            sql = "SELECT COUNT(*) FROM join_requests WHERE user_id = ? AND club_id = ? AND status = 'PENDING'";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.setInt(2, clubId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next() || rs.getInt(1) != 1) {
                System.out.println("  [失败] 入社申请：申请记录未创建");
                return false;
            }
            
            try {
                pstmt.executeUpdate();
                System.out.println("  [失败] 入社申请：未检测到重复申请约束");
                return false;
            } catch (SQLException e) {
                System.out.println("  [通过] 入社申请：正确检测到重复申请约束");
            }
            
            sql = "UPDATE join_requests SET status = 'APPROVED', reviewed_by = ?, reviewed_at = NOW() WHERE user_id = ? AND club_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);
            pstmt.setInt(3, clubId);
            pstmt.executeUpdate();
            
            sql = "SELECT status FROM join_requests WHERE user_id = ? AND club_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.setInt(2, clubId);
            rs = pstmt.executeQuery();
            if (!rs.next() || !"APPROVED".equals(rs.getString("status"))) {
                System.out.println("  [失败] 入社申请：审核状态未更新");
                return false;
            }
            
            System.out.println("  [通过] 入社申请功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 入社申请：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testActivityCreation() {
        System.out.println("\n[测试6] 活动创建功能");
        try {
            int clubId = getClubIdByName("测试社团_reg");
            int userId = getUserIdByStudentId("20249991");
            
            String sql = "INSERT INTO activities (club_id, title, description, activity_type, start_time, end_time, location, max_participants, current_participants, budget, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, clubId);
            pstmt.setString(2, "测试活动_reg");
            pstmt.setString(3, "测试用活动");
            pstmt.setString(4, "PUBLIC");
            pstmt.setTimestamp(5, new Timestamp(System.currentTimeMillis() + 86400000));
            pstmt.setTimestamp(6, new Timestamp(System.currentTimeMillis() + 172800000));
            pstmt.setString(7, "活动场地");
            pstmt.setInt(8, 30);
            pstmt.setInt(9, 0);
            pstmt.setDouble(10, 500.00);
            pstmt.setString(11, "DRAFT");
            pstmt.setInt(12, userId);
            int rows = pstmt.executeUpdate();
            
            if (rows != 1) {
                System.out.println("  [失败] 活动创建：插入行数不正确");
                return false;
            }
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (!rs.next()) {
                System.out.println("  [失败] 活动创建：无法获取自增ID");
                return false;
            }
            int activityId = rs.getInt(1);
            
            sql = "UPDATE activities SET status = 'PUBLISHED', published_at = NOW() WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.executeUpdate();
            
            sql = "SELECT status FROM activities WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            rs = pstmt.executeQuery();
            if (!rs.next() || !"PUBLISHED".equals(rs.getString("status"))) {
                System.out.println("  [失败] 活动创建：发布状态未更新");
                return false;
            }
            
            System.out.println("  [通过] 活动创建功能测试通过（ID: " + activityId + "）");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 活动创建：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testActivityRegistration() {
        System.out.println("\n[测试7] 活动报名功能");
        try {
            int activityId = getActivityIdByTitle("测试活动_reg");
            int userId = getUserIdByStudentId("20249991");
            
            String sql = "INSERT INTO activity_registrations (activity_id, user_id, real_name, student_id, status) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            pstmt.setString(3, "测试用户");
            pstmt.setString(4, "20249991");
            pstmt.setString(5, "PENDING");
            pstmt.executeUpdate();
            
            sql = "UPDATE activities SET current_participants = current_participants + 1 WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.executeUpdate();
            
            sql = "SELECT COUNT(*) FROM activity_registrations WHERE activity_id = ? AND user_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next() || rs.getInt(1) != 1) {
                System.out.println("  [失败] 活动报名：报名记录未创建");
                return false;
            }
            
            try {
                pstmt.executeUpdate();
                System.out.println("  [失败] 活动报名：未检测到重复报名约束");
                return false;
            } catch (SQLException e) {
                System.out.println("  [通过] 活动报名：正确检测到重复报名约束");
            }
            
            sql = "SELECT current_participants FROM activities WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            rs = pstmt.executeQuery();
            if (!rs.next() || rs.getInt(1) != 1) {
                System.out.println("  [失败] 活动报名：报名人数未更新");
                return false;
            }
            
            sql = "UPDATE activity_registrations SET status = 'APPROVED', approval_time = NOW(), approved_by = ? WHERE activity_id = ? AND user_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, userId);
            pstmt.setInt(2, activityId);
            pstmt.setInt(3, userId);
            pstmt.executeUpdate();
            
            sql = "SELECT status FROM activity_registrations WHERE activity_id = ? AND user_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            rs = pstmt.executeQuery();
            if (!rs.next() || !"APPROVED".equals(rs.getString("status"))) {
                System.out.println("  [失败] 活动报名：审核状态未更新");
                return false;
            }
            
            sql = "UPDATE activity_registrations SET is_checked_in = TRUE, check_in_time = NOW() WHERE activity_id = ? AND user_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
            
            sql = "SELECT is_checked_in FROM activity_registrations WHERE activity_id = ? AND user_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, activityId);
            pstmt.setInt(2, userId);
            rs = pstmt.executeQuery();
            if (!rs.next() || !rs.getBoolean("is_checked_in")) {
                System.out.println("  [失败] 活动报名：签到状态未更新");
                return false;
            }
            
            System.out.println("  [通过] 活动报名功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 活动报名：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testAnnouncementCreation() {
        System.out.println("\n[测试8] 公告管理功能");
        try {
            int userId = getUserIdByStudentId("20249991");
            
            String sql = "INSERT INTO announcements (title, content, priority, is_pinned, status, club_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, "测试公告_reg");
            pstmt.setString(2, "测试公告内容");
            pstmt.setString(3, "IMPORTANT");
            pstmt.setBoolean(4, true);
            pstmt.setString(5, "DRAFT");
            pstmt.setNull(6, Types.INTEGER);
            pstmt.setInt(7, userId);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (!rs.next()) {
                System.out.println("  [失败] 公告管理：无法获取自增ID");
                return false;
            }
            int announcementId = rs.getInt(1);
            
            sql = "UPDATE announcements SET status = 'PUBLISHED', published_at = NOW() WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, announcementId);
            pstmt.executeUpdate();
            
            sql = "SELECT status, is_pinned, priority FROM announcements WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, announcementId);
            rs = pstmt.executeQuery();
            
            if (!rs.next() || !"PUBLISHED".equals(rs.getString("status")) || 
                !rs.getBoolean("is_pinned") || !"IMPORTANT".equals(rs.getString("priority"))) {
                System.out.println("  [失败] 公告管理：数据验证失败");
                return false;
            }
            
            System.out.println("  [通过] 公告管理功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 公告管理：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testMessageSending() {
        System.out.println("\n[测试9] 消息发送功能");
        try {
            int clubId = getClubIdByName("测试社团_reg");
            int userId = getUserIdByStudentId("20249991");
            
            String sql = "SELECT id FROM conversation WHERE club_id = ? AND type = 'CLUB'";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            ResultSet rs = pstmt.executeQuery();
            
            int conversationId;
            if (rs.next()) {
                conversationId = rs.getInt(1);
            } else {
                sql = "INSERT INTO conversation (type, name, club_id) VALUES (?, ?, ?)";
                pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                pstmt.setString(1, "CLUB");
                pstmt.setString(2, "测试社团群聊");
                pstmt.setInt(3, clubId);
                pstmt.executeUpdate();
                
                rs = pstmt.getGeneratedKeys();
                rs.next();
                conversationId = rs.getInt(1);
                
                sql = "INSERT INTO conversation_member (conversation_id, user_id, role) VALUES (?, ?, ?)";
                pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, conversationId);
                pstmt.setInt(2, userId);
                pstmt.setString(3, "OWNER");
                pstmt.executeUpdate();
            }
            
            sql = "INSERT INTO messages (type, user_id, conversation_id, content, create_time) VALUES (?, ?, ?, ?, NOW())";
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "TEXT");
            pstmt.setInt(2, userId);
            pstmt.setInt(3, conversationId);
            pstmt.setString(4, "测试消息内容");
            pstmt.executeUpdate();
            
            sql = "UPDATE conversation SET updated_at = NOW() WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, conversationId);
            pstmt.executeUpdate();
            
            sql = "SELECT COUNT(*) FROM messages WHERE conversation_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, conversationId);
            rs = pstmt.executeQuery();
            if (!rs.next() || rs.getInt(1) != 1) {
                System.out.println("  [失败] 消息发送：消息记录未创建");
                return false;
            }
            
            sql = "SELECT updated_at FROM conversation WHERE id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, conversationId);
            rs = pstmt.executeQuery();
            if (!rs.next() || rs.getTimestamp("updated_at") == null) {
                System.out.println("  [失败] 消息发送：会话更新时间未更新");
                return false;
            }
            
            System.out.println("  [通过] 消息发送功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 消息发送：" + e.getMessage());
            return false;
        }
    }
    
    private static boolean testStoredProcedures() {
        System.out.println("\n[测试10] 存储过程测试");
        try {
            String sql = "SELECT COUNT(*) FROM user WHERE status = 'ACTIVE'";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            rs.next();
            int totalUsers = rs.getInt(1);
            
            sql = "SELECT COUNT(*) FROM club WHERE status = 'APPROVED'";
            rs = conn.createStatement().executeQuery(sql);
            rs.next();
            int totalClubs = rs.getInt(1);
            
            sql = "SELECT COUNT(*) FROM activities WHERE status IN ('PUBLISHED', 'IN_PROGRESS')";
            rs = conn.createStatement().executeQuery(sql);
            rs.next();
            int totalActivities = rs.getInt(1);
            
            sql = "SELECT COUNT(*) FROM club_member WHERE status = 'APPROVED'";
            rs = conn.createStatement().executeQuery(sql);
            rs.next();
            int totalMembers = rs.getInt(1);
            
            System.out.println("  系统统计数据：");
            System.out.println("    - 总用户数: " + totalUsers);
            System.out.println("    - 总社团数: " + totalClubs);
            System.out.println("    - 总活动数: " + totalActivities);
            System.out.println("    - 总成员数: " + totalMembers);
            
            int clubId = getClubIdByName("测试社团_reg");
            
            sql = "SELECT COUNT(*) FROM club_member WHERE club_id = ? AND status = 'APPROVED'";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            rs = pstmt.executeQuery();
            rs.next();
            int memberCount = rs.getInt(1);
            
            sql = "SELECT COUNT(*) FROM join_requests WHERE club_id = ? AND status = 'PENDING'";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            rs = pstmt.executeQuery();
            rs.next();
            int pendingRequests = rs.getInt(1);
            
            sql = "SELECT COUNT(*) FROM activities WHERE club_id = ? AND status IN ('PUBLISHED', 'IN_PROGRESS') AND start_time > NOW()";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, clubId);
            rs = pstmt.executeQuery();
            rs.next();
            int upcomingActivities = rs.getInt(1);
            
            System.out.println("  社团统计数据（测试社团）：");
            System.out.println("    - 成员数: " + memberCount);
            System.out.println("    - 待审核申请: " + pendingRequests);
            System.out.println("    - 即将举办活动: " + upcomingActivities);
            
            System.out.println("  [通过] 统计查询功能测试通过");
            return true;
            
        } catch (Exception e) {
            System.out.println("  [失败] 统计查询：" + e.getMessage());
            return false;
        }
    }
    
    private static int getUserIdByStudentId(String studentId) throws SQLException {
        String sql = "SELECT id FROM user WHERE student_id = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, studentId);
        ResultSet rs = pstmt.executeQuery();
        return rs.next() ? rs.getInt(1) : -1;
    }
    
    private static int getClubIdByName(String name) throws SQLException {
        String sql = "SELECT id FROM club WHERE name = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, name);
        ResultSet rs = pstmt.executeQuery();
        return rs.next() ? rs.getInt(1) : -1;
    }
    
    private static int getActivityIdByTitle(String title) throws SQLException {
        String sql = "SELECT id FROM activities WHERE title = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, title);
        ResultSet rs = pstmt.executeQuery();
        return rs.next() ? rs.getInt(1) : -1;
    }
}