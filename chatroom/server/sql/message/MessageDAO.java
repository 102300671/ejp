package server.sql.message;

import server.message.Message;
import server.message.MessageType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    /**
     * 保存消息到数据库
     * @param message 消息对象
     * @param messageType 消息类别 (ROOM:房间消息, PRIVATE:私人消息)
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void saveMessage(Message message, String messageType, Connection connection) throws SQLException {
        String sql = "INSERT INTO messages (type, from_username, to_username, content, message_type) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, message.getType().name());
            stmt.setString(2, message.getFrom());
            stmt.setString(3, message.getTo());
            stmt.setString(4, message.getContent());
            stmt.setString(5, messageType);
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * 获取指定房间的消息历史
     * @param roomName 房间名称
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getRoomMessages(String roomName, int limit, Connection connection) throws SQLException {
        String sql = "SELECT id, type, from_username, to_username, content, create_time FROM messages " +
                     "WHERE message_type = 'ROOM' AND to_username = ? " +
                     "ORDER BY create_time DESC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, roomName);
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                MessageType type = MessageType.valueOf(rs.getString("type"));
                String from = rs.getString("from_username");
                String to = rs.getString("to_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                
                Message message = new Message(type, from, to, content, time);
                messages.add(message);
            }
        }
        
        // 反转列表，使最早的消息在前面
        List<Message> reversedMessages = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            reversedMessages.add(messages.get(i));
        }
        
        return reversedMessages;
    }
    
    /**
     * 获取两个用户之间的私人消息历史
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getPrivateMessages(String user1, String user2, int limit, Connection connection) throws SQLException {
        String sql = "SELECT id, type, from_username, to_username, content, create_time FROM messages " +
                     "WHERE message_type = 'PRIVATE' " +
                     "AND ((from_username = ? AND to_username = ?) OR (from_username = ? AND to_username = ?)) " +
                     "ORDER BY create_time DESC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            stmt.setInt(5, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                MessageType type = MessageType.valueOf(rs.getString("type"));
                String from = rs.getString("from_username");
                String to = rs.getString("to_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                
                Message message = new Message(type, from, to, content, time);
                messages.add(message);
            }
        }
        
        // 反转列表，使最早的消息在前面
        List<Message> reversedMessages = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            reversedMessages.add(messages.get(i));
        }
        
        return reversedMessages;
    }
    
    /**
     * 获取用户的所有未读消息（可选功能）
     * @param username 用户名
     * @param connection 数据库连接
     * @return 未读消息数量
     * @throws SQLException SQL异常
     */
    public int getUnreadMessageCount(String username, Connection connection) throws SQLException {
        // 这里可以扩展为支持未读消息功能
        return 0;
    }
}
