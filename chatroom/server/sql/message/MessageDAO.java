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
        String sql = "INSERT INTO messages (type, from_username, to_username, content, message_type, is_nsfw, iv) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, message.getType().name());
            stmt.setString(2, message.getFrom());
            stmt.setString(3, message.getTo());
            stmt.setString(4, message.getContent());
            stmt.setString(5, messageType);
            stmt.setBoolean(6, message.isNSFW());
            stmt.setString(7, message.getIv());
            
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
        String sql = "SELECT id, type, from_username, to_username, content, create_time, is_nsfw, iv FROM messages " +
                     "WHERE message_type = 'ROOM' AND to_username = ? " +
                     "ORDER BY create_time DESC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, roomName);
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int dbId = rs.getInt("id");
                String typeStr = rs.getString("type");
                MessageType type;
                try {
                    type = MessageType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("未知的消息类型: " + typeStr + "，跳过该消息");
                    continue;
                }
                
                String from = rs.getString("from_username");
                String to = rs.getString("to_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                String messageId = String.format("%s_%s_%d", type.name(), roomName, dbId);
                Message message = new Message(type, from, to, content, time, isNSFW, iv, messageId);
                messages.add(message);
            }
        }
        
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
        String sql = "SELECT id, type, from_username, to_username, content, create_time, is_nsfw, iv FROM messages " +
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
                int dbId = rs.getInt("id");
                String typeStr = rs.getString("type");
                MessageType type;
                try {
                    type = MessageType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("未知的消息类型: " + typeStr + "，跳过该消息");
                    continue;
                }
                
                String from = rs.getString("from_username");
                String to = rs.getString("to_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                String messageId = String.format("%s_%s_%d", type.name(), from + "_" + to, dbId);
                Message message = new Message(type, from, to, content, time, isNSFW, iv, messageId);
                messages.add(message);
            }
        }
        
        List<Message> reversedMessages = new ArrayList<>(messages.size());
        for (int i = messages.size() - 1; i >= 0; i--) {
            reversedMessages.add(messages.get(i));
        }
        
        return reversedMessages;
    }
    
    /**
     * 获取指定房间的最新消息时间戳
     * @param roomName 房间名称
     * @param connection 数据库连接
     * @return 最新消息时间戳，如果没有消息则返回null
     * @throws SQLException SQL异常
     */
    public String getLatestRoomTimestamp(String roomName, Connection connection) throws SQLException {
        String sql = "SELECT create_time FROM messages " +
                     "WHERE message_type = 'ROOM' AND to_username = ? " +
                     "ORDER BY create_time DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, roomName);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("create_time").replace('T', ' ').substring(0, 19);
            }
        }
        
        return null;
    }
    
    /**
     * 获取两个用户之间的最新消息时间戳
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param connection 数据库连接
     * @return 最新消息时间戳，如果没有消息则返回null
     * @throws SQLException SQL异常
     */
    public String getLatestPrivateTimestamp(String user1, String user2, Connection connection) throws SQLException {
        String sql = "SELECT create_time FROM messages " +
                     "WHERE message_type = 'PRIVATE' " +
                     "AND ((from_username = ? AND to_username = ?) OR (from_username = ? AND to_username = ?)) " +
                     "ORDER BY create_time DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("create_time").replace('T', ' ').substring(0, 19);
            }
        }
        
        return null;
    }
    
    /**
     * 获取指定房间在指定时间戳之后的消息
     * @param roomName 房间名称
     * @param afterTimestamp 起始时间戳
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getRoomMessagesAfter(String roomName, String afterTimestamp, int limit, Connection connection) throws SQLException {
        String sql = "SELECT id, type, from_username, to_username, content, create_time, is_nsfw, iv FROM messages " +
                     "WHERE message_type = 'ROOM' AND to_username = ? AND create_time > ? " +
                     "ORDER BY create_time ASC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, roomName);
            stmt.setString(2, afterTimestamp);
            stmt.setInt(3, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int dbId = rs.getInt("id");
                String typeStr = rs.getString("type");
                MessageType type;
                try {
                    type = MessageType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("未知的消息类型: " + typeStr + "，跳过该消息");
                    continue;
                }
                
                String from = rs.getString("from_username");
                String to = rs.getString("to_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                String messageId = String.format("%s_%s_%d", type.name(), roomName, dbId);
                Message message = new Message(type, from, to, content, time, isNSFW, iv, messageId);
                messages.add(message);
            }
        }
        
        return messages;
    }
    
    /**
     * 获取两个用户之间在指定时间戳之后的消息
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param afterTimestamp 起始时间戳
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getPrivateMessagesAfter(String user1, String user2, String afterTimestamp, int limit, Connection connection) throws SQLException {
        String sql = "SELECT id, type, from_username, to_username, content, create_time, is_nsfw, iv FROM messages " +
                     "WHERE message_type = 'PRIVATE' " +
                     "AND ((from_username = ? AND to_username = ?) OR (from_username = ? AND to_username = ?)) " +
                     "AND create_time > ? " +
                     "ORDER BY create_time ASC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            stmt.setString(5, afterTimestamp);
            stmt.setInt(6, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int dbId = rs.getInt("id");
                String typeStr = rs.getString("type");
                MessageType type;
                try {
                    type = MessageType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("未知的消息类型: " + typeStr + "，跳过该消息");
                    continue;
                }
                
                String from = rs.getString("from_username");
                String to = rs.getString("to_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                String messageId = String.format("%s_%s_%d", type.name(), from + "_" + to, dbId);
                Message message = new Message(type, from, to, content, time, isNSFW, iv, messageId);
                messages.add(message);
            }
        }
        
        return messages;
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
    
    /**
     * 获取与指定用户有私聊消息的所有用户列表
     * @param username 用户名
     * @param connection 数据库连接
     * @return 用户名列表
     * @throws SQLException SQL异常
     */
    public List<String> getPrivateChatUsers(String username, Connection connection) throws SQLException {
        String sql = "SELECT DISTINCT CASE " +
                     "WHEN from_username = ? THEN to_username " +
                     "ELSE from_username " +
                     "END AS chat_user " +
                     "FROM messages " +
                     "WHERE message_type = 'PRIVATE' " +
                     "AND (from_username = ? OR to_username = ?)";
        
        List<String> users = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, username);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String chatUser = rs.getString("chat_user");
                if (chatUser != null && !chatUser.isEmpty()) {
                    users.add(chatUser);
                }
            }
        }
        
        return users;
    }
    
    /**
     * 获取用户发送的消息总数
     * @param username 用户名
     * @param connection 数据库连接
     * @return 消息总数
     * @throws SQLException SQL异常
     */
    public int getUserMessageCount(String username, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE from_username = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    /**
     * 获取用户发送的图片数量
     * @param username 用户名
     * @param connection 数据库连接
     * @return 图片数量
     * @throws SQLException SQL异常
     */
    public int getUserImageCount(String username, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE from_username = ? AND type = 'IMAGE'";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    /**
     * 获取用户发送的文件数量
     * @param username 用户名
     * @param connection 数据库连接
     * @return 文件数量
     * @throws SQLException SQL异常
     */
    public int getUserFileCount(String username, Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE from_username = ? AND type = 'FILE'";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
}
