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
        String sql = "SELECT u.id, u.username, u.password, u.created_at, uu.uuid " +
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
                    String uuid = resultSet.getString("uuid");
                    
                    return new User(id, dbUsername, hashedPassword, createdAt, uuid);
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
}