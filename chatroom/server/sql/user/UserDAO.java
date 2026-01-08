package server.sql.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class UserDAO {
  public static Connection getConnection() throws   ClassNotFoundException, SQLException {
      Class.forName("com.mysql.cj.jdbc.Driver");
      System.out.println("Driver OK");
      String url = "jdbc:mysql://localhost:3306/chatroom_server_db?userSSL=false&serverTimezone=Asia/Shanghai";
      String user = "chatroom";
      String passward = "chatroom";
      Connection conn = DriverManager.getConnection(url, user, passward);
      return conn;
  } 
  public static void main(String[] args) {
    try {
        Connection conn = getConnection();
        System.out.println("连接成功！");
        // 测试完成后记得关闭连接
        if (conn != null) conn.close();
    } catch (ClassNotFoundException e) {
        System.err.println("找不到数据库驱动类: " + e.getMessage());
        e.printStackTrace();
    } catch (SQLException e) {
        System.err.println("数据库连接失败: " + e.getMessage());
        System.err.println("SQL状态: " + e.getSQLState());
        System.err.println("错误码: " + e.getErrorCode());
        e.printStackTrace();
    }
  }
}