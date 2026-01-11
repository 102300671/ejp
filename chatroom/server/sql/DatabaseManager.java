package server.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    // 数据库配置参数
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatroom_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "chatroom";
    private static final String DB_PASSWORD = "chatroom";
    private static final String DB_DRIVER = "com.mysql.cj.jdbc.Driver";
    
    private static boolean driverLoaded = false;
    
    static {
        // 静态初始化块，确保驱动只加载一次
        try {
            Class.forName(DB_DRIVER);
            driverLoaded = true;
            System.out.println("MySQL数据库驱动加载成功");
        } catch (ClassNotFoundException e) {
            System.err.println("加载MySQL数据库驱动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("数据库驱动未加载，无法建立连接");
        }
        
        try {
            Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("成功建立数据库连接");
            return connection;
        } catch (SQLException e) {
            System.err.println("建立数据库连接失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("数据库连接已成功关闭");
                }
            } catch (SQLException e) {
                System.err.println("关闭数据库连接时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
