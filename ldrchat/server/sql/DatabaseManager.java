package server.sql;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {
    // 数据库配置参数
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    private static String dbDriver;
    
    private static boolean driverLoaded = false;
    
    static {
        // 加载数据库配置
        loadDatabaseConfig();
        
        // 静态初始化块，确保驱动只加载一次
        try {
            Class.forName(dbDriver);
            driverLoaded = true;
            System.out.println("MySQL数据库驱动加载成功");
        } catch (ClassNotFoundException e) {
            System.err.println("加载MySQL数据库驱动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从配置文件加载数据库配置
     */
    private static void loadDatabaseConfig() {
        Properties properties = new Properties();
        
        try (InputStream input = DatabaseManager.class.getResourceAsStream("database.properties")) {
            if (input == null) {
                System.err.println("错误: 无法找到database.properties文件");
                System.err.println("请根据README中的说明创建并配置database.properties文件");
                throw new RuntimeException("缺少database.properties文件");
            }
            
            // 加载properties文件
            properties.load(input);
            
            // 读取配置信息
            dbUrl = properties.getProperty("db.url");
            dbUser = properties.getProperty("db.user");
            dbPassword = properties.getProperty("db.password");
            dbDriver = properties.getProperty("db.driver");
            
            // 检查配置是否包含占位符
            if (containsPlaceholders()) {
                System.err.println("错误: 数据库配置文件包含占位符");
                System.err.println("请根据README中的说明修改database.properties文件中的占位符");
                throw new RuntimeException("database.properties文件包含未替换的占位符");
            }
            
            System.out.println("数据库配置加载成功");
        } catch (IOException e) {
            System.err.println("加载数据库配置文件失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("无法加载database.properties文件", e);
        }
    }
    
    /**
     * 检查配置是否包含占位符
     * @return 如果包含占位符返回true，否则返回false
     */
    private static boolean containsPlaceholders() {
        return (dbUrl != null && (dbUrl.contains("[db_name]") || dbUrl.contains("[Timezone]"))) ||
               (dbUser != null && dbUser.contains("[db_user]")) ||
               (dbPassword != null && dbPassword.contains("[db_password]"));
    }
    
    public Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("数据库驱动未加载，无法建立连接");
        }
        
        try {
            Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
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
