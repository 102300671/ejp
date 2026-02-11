package server.sql.user;

import java.sql.*;
import server.user.User;
import at.favre.lib.crypto.bcrypt.BCrypt;

public class UserDAO {
    
    /**
     * 插入新用户到数据库
     * @param user 用户对象
     * @param connection 数据库连接
     * @throws SQLException 如果插入过程中发生数据库错误
     */
    public void insertUser(User user, Connection connection) throws SQLException {
        String sql = "INSERT INTO user (username, password) VALUES (?, ?)";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, user.getUsername());
            
            // 使用BCrypt加密密码
            String hashedPassword = BCrypt.withDefaults().hashToString(12, user.getPassword().toCharArray());
            preparedStatement.setString(2, hashedPassword);
            
            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("成功插入用户: " + user.getUsername() + "，影响行数: " + rowsAffected);
            
        } catch (SQLException e) {
            System.err.println("插入用户失败 (用户名: " + user.getUsername() + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 根据用户名获取用户ID
     * @param username 用户名
     * @param connection 数据库连接
     * @return 用户ID，如果用户不存在则返回null
     * @throws SQLException 如果查询过程中发生数据库错误
     */
    public Integer getUserIdByUsername(String username, Connection connection) throws SQLException {
        String sql = "SELECT id FROM user WHERE username = ?";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
            
            System.out.println("未找到用户: " + username);
            return null;
            
        } catch (SQLException e) {
            System.err.println("查询用户ID失败 (用户名: " + username + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 根据用户名获取用户完整信息
     * @param username 用户名
     * @param connection 数据库连接
     * @return 用户对象，如果用户不存在则返回null
     * @throws SQLException 如果查询过程中发生数据库错误
     */
    public User getUserByUsername(String username, Connection connection) throws SQLException {
        String sql = "SELECT u.id, u.username, u.password, u.created_at, u.accept_temporary_chat, u.status, uu.uuid " +
                     "FROM user u " +
                     "LEFT JOIN user_uuid uu ON u.id = uu.user_id " +
                     "WHERE u.username = ?";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String dbUsername = resultSet.getString("username");
                    String hashedPassword = resultSet.getString("password");
                    String createdAt = resultSet.getString("created_at");
                    boolean acceptTemporaryChat = resultSet.getBoolean("accept_temporary_chat");
                    String status = resultSet.getString("status");
                    String uuid = resultSet.getString("uuid");
                    
                    User user = new User(id, dbUsername, hashedPassword, createdAt, uuid);
                    user.setAcceptTemporaryChat(acceptTemporaryChat);
                    user.setStatus(status);
                    return user;
                }
            }
            
            System.out.println("未找到用户: " + username);
            return null;
            
        } catch (SQLException e) {
            System.err.println("查询用户信息失败 (用户名: " + username + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 验证用户名和密码
     * @param username 用户名
     * @param password 密码
     * @param connection 数据库连接
     * @return 如果验证成功返回true，否则返回false
     * @throws SQLException 如果查询过程中发生数据库错误
     */
    public boolean validateUser(String username, String password, Connection connection) throws SQLException {
        User user = getUserByUsername(username, connection);
        
        if (user == null) {
            return false;
        }
        
        // 使用BCrypt验证密码
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
        boolean isValid = result.verified;
        
        System.out.println("用户验证结果 (" + username + "): " + (isValid ? "成功" : "失败"));
        return isValid;
    }
    
    /**
     * 根据用户ID获取用户名
     * @param userId 用户ID
     * @param connection 数据库连接
     * @return 用户名，如果用户不存在则返回null
     * @throws SQLException 如果查询过程中发生数据库错误
     */
    public String getUsernameById(int userId, Connection connection) throws SQLException {
        String sql = "SELECT username FROM user WHERE id = ?";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, userId);
            
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("username");
                }
            }
            
            System.out.println("未找到用户ID对应的用户名: " + userId);
            return null;
            
        } catch (SQLException e) {
            System.err.println("查询用户名失败 (用户ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 根据用户名获取用户加入时间
     * @param username 用户名
     * @param connection 数据库连接
     * @return 用户加入时间，如果用户不存在则返回null
     * @throws SQLException 如果查询过程中发生数据库错误
     */
    public String getUserJoinTime(String username, Connection connection) throws SQLException {
        String sql = "SELECT created_at FROM user WHERE username = ?";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    java.sql.Timestamp createdAt = resultSet.getTimestamp("created_at");
                    if (createdAt != null) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd");
                        return sdf.format(createdAt);
                    }
                }
            }
            
            System.out.println("未找到用户: " + username);
            return null;
            
        } catch (SQLException e) {
            System.err.println("查询用户加入时间失败 (用户名: " + username + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 搜索用户（支持模糊搜索）
     * @param searchTerm 搜索关键词
     * @param connection 数据库连接
     * @return 用户列表
     * @throws SQLException 如果查询过程中发生数据库错误
     */
    public java.util.List<User> searchUsers(String searchTerm, Connection connection) throws SQLException {
        String sql = "SELECT id, username, created_at FROM user WHERE username LIKE ? ORDER BY username LIMIT 20";
        
        java.util.List<User> users = new java.util.ArrayList<>();
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, "%" + searchTerm + "%");
            
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    String username = resultSet.getString("username");
                    String createdAt = resultSet.getString("created_at");
                    
                    users.add(new User(id, username, null, createdAt, null));
                }
            }
            
            System.out.println("搜索用户结果: 找到 " + users.size() + " 个用户");
            
        } catch (SQLException e) {
            System.err.println("搜索用户失败 (搜索词: " + searchTerm + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        return users;
    }
    
    /**
     * 更新用户的临时聊天接受设置
     * @param userId 用户ID
     * @param acceptTemporaryChat 是否接受临时聊天
     * @param connection 数据库连接
     * @return 更新成功返回true，否则返回false
     * @throws SQLException 如果更新过程中发生数据库错误
     */
    public boolean updateAcceptTemporaryChat(int userId, boolean acceptTemporaryChat, Connection connection) throws SQLException {
        String sql = "UPDATE user SET accept_temporary_chat = ? WHERE id = ?";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setBoolean(1, acceptTemporaryChat);
            preparedStatement.setInt(2, userId);
            
            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("更新用户临时聊天设置: 用户ID=" + userId + ", acceptTemporaryChat=" + acceptTemporaryChat + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("更新用户临时聊天设置失败 (用户ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 更新用户状态
     * @param userId 用户ID
     * @param status 用户状态 (ONLINE, OFFLINE, AWAY, BUSY)
     * @param connection 数据库连接
     * @return 更新成功返回true，否则返回false
     * @throws SQLException 如果更新过程中发生数据库错误
     */
    public boolean updateUserStatus(int userId, String status, Connection connection) throws SQLException {
        String sql = "UPDATE user SET status = ? WHERE id = ?";
        
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, status);
            preparedStatement.setInt(2, userId);
            
            int rowsAffected = preparedStatement.executeUpdate();
            System.out.println("更新用户状态: 用户ID=" + userId + ", status=" + status + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("更新用户状态失败 (用户ID: " + userId + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}