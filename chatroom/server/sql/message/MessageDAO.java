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
     * @param conversationId 会话ID
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void saveMessage(Message message, String messageType, int conversationId, Connection connection) throws SQLException {
        String sql = "INSERT INTO messages (type, from_username, conversation_id, content, message_type, is_nsfw, iv) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, message.getType().name());
            stmt.setString(2, message.getFrom());
            stmt.setInt(3, conversationId);
            stmt.setString(4, message.getContent());
            stmt.setString(5, messageType);
            stmt.setBoolean(6, message.isNSFW());
            stmt.setString(7, message.getIv());
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * 保存消息到数据库（兼容旧方法）
     * @param message 消息对象
     * @param messageType 消息类别 (ROOM:房间消息, PRIVATE:私人消息)
     * @param connection 数据库连接
     * @throws SQLException SQL异常
     */
    public void saveMessage(Message message, String messageType, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用带 conversationId 的方法
        throw new UnsupportedOperationException("Use saveMessage with conversationId instead");
    }
    
    /**
     * 获取指定会话的消息历史
     * @param conversationId 会话ID
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getConversationMessages(int conversationId, int limit, Connection connection) throws SQLException {
        String sql = "SELECT id, type, from_username, content, create_time, is_nsfw, iv FROM messages " +
                     "WHERE conversation_id = ? " +
                     "ORDER BY create_time DESC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
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
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                String messageId = String.format("%s_conversation_%d_%d", type.name(), conversationId, dbId);
                Message message = new Message(type, from, content, time, isNSFW, iv, messageId, conversationId);
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
     * 获取指定会话的最新消息时间戳
     * @param conversationId 会话ID
     * @param connection 数据库连接
     * @return 最新消息时间戳，如果没有消息则返回null
     * @throws SQLException SQL异常
     */
    public String getLatestConversationTimestamp(int conversationId, Connection connection) throws SQLException {
        String sql = "SELECT create_time FROM messages " +
                     "WHERE conversation_id = ? " +
                     "ORDER BY create_time DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("create_time").replace('T', ' ').substring(0, 19);
            }
        }
        
        return null;
    }
    
    /**
     * 获取指定会话在指定时间戳之后的消息
     * @param conversationId 会话ID
     * @param afterTimestamp 起始时间戳
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getConversationMessagesAfter(int conversationId, String afterTimestamp, int limit, Connection connection) throws SQLException {
        String sql = "SELECT id, type, from_username, content, create_time, is_nsfw, iv FROM messages " +
                     "WHERE conversation_id = ? AND create_time > ? " +
                     "ORDER BY create_time ASC LIMIT ?";
        
        List<Message> messages = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
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
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                String messageId = String.format("%s_conversation_%d_%d", type.name(), conversationId, dbId);
                Message message = new Message(type, from, content, time, isNSFW, iv, messageId, conversationId);
                messages.add(message);
            }
        }
        
        return messages;
    }
    
    /**
     * 获取指定房间的消息历史（兼容旧方法）
     * @param roomName 房间名称
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getRoomMessages(String roomName, int limit, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用 getConversationMessages
        throw new UnsupportedOperationException("Use getConversationMessages instead");
    }
    
    /**
     * 获取两个用户之间的私人消息历史（兼容旧方法）
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getPrivateMessages(String user1, String user2, int limit, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用 getConversationMessages
        throw new UnsupportedOperationException("Use getConversationMessages instead");
    }
    
    /**
     * 获取指定房间的最新消息时间戳（兼容旧方法）
     * @param roomName 房间名称
     * @param connection 数据库连接
     * @return 最新消息时间戳，如果没有消息则返回null
     * @throws SQLException SQL异常
     */
    public String getLatestRoomTimestamp(String roomName, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用 getLatestConversationTimestamp
        throw new UnsupportedOperationException("Use getLatestConversationTimestamp instead");
    }
    
    /**
     * 获取两个用户之间的最新消息时间戳（兼容旧方法）
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param connection 数据库连接
     * @return 最新消息时间戳，如果没有消息则返回null
     * @throws SQLException SQL异常
     */
    public String getLatestPrivateTimestamp(String user1, String user2, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用 getLatestConversationTimestamp
        throw new UnsupportedOperationException("Use getLatestConversationTimestamp instead");
    }
    
    /**
     * 获取指定房间在指定时间戳之后的消息（兼容旧方法）
     * @param roomName 房间名称
     * @param afterTimestamp 起始时间戳
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getRoomMessagesAfter(String roomName, String afterTimestamp, int limit, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用 getConversationMessagesAfter
        throw new UnsupportedOperationException("Use getConversationMessagesAfter instead");
    }
    
    /**
     * 获取两个用户之间在指定时间戳之后的消息（兼容旧方法）
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param afterTimestamp 起始时间戳
     * @param limit 限制条数
     * @param connection 数据库连接
     * @return 消息列表
     * @throws SQLException SQL异常
     */
    public List<Message> getPrivateMessagesAfter(String user1, String user2, String afterTimestamp, int limit, Connection connection) throws SQLException {
        // 旧方法保持兼容，实际使用时应使用 getConversationMessagesAfter
        throw new UnsupportedOperationException("Use getConversationMessagesAfter instead");
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
        String sql = "SELECT DISTINCT cm.username " +
                     "FROM conversation_member cm " +
                     "INNER JOIN conversation c ON cm.conversation_id = c.id " +
                     "WHERE c.type IN ('FRIEND', 'TEMP') " +
                     "AND cm.conversation_id IN (SELECT conversation_id FROM conversation_member WHERE username = ?) " +
                     "AND cm.username != ?";
        
        List<String> users = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String chatUser = rs.getString("username");
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
    
    /**
     * 根据消息ID删除消息
     * @param messageId 消息ID
     * @param connection 数据库连接
     * @return 删除成功返回true，否则返回false
     * @throws SQLException SQL异常
     */
    public boolean deleteMessage(String messageId, Connection connection) throws SQLException {
        // 解析消息ID：格式为 TYPE_conversation_timestamp_random
        String[] parts = messageId.split("_");
        if (parts.length < 4) {
            System.err.println("无效的消息ID格式: " + messageId);
            return false;
        }
        
        String messageType = parts[0];
        String target = parts[1];
        String timestampStr = parts[2];
        
        // 将时间戳转换为MySQL datetime格式
        long timestamp = Long.parseLong(timestampStr);
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime messageTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        
        // 使用消息类型、目标和时间来查找并删除消息
        String sql = "DELETE FROM messages " +
                     "WHERE type = ? AND create_time >= DATE_SUB(?, INTERVAL 1 SECOND) " +
                     "AND create_time <= DATE_ADD(?, INTERVAL 1 SECOND) " +
                     "ORDER BY create_time DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, messageType);
            stmt.setString(2, messageTime.toString().replace('T', ' ').substring(0, 19));
            stmt.setString(3, messageTime.toString().replace('T', ' ').substring(0, 19));
            
            int rowsAffected = stmt.executeUpdate();
            System.out.println("删除消息: messageId=" + messageId + ", 影响行数: " + rowsAffected);
            
            return rowsAffected > 0;
        }
    }
    
    /**
     * 根据消息ID获取消息
     * @param messageId 消息ID
     * @param connection 数据库连接
     * @return 消息对象，如果不存在则返回null
     * @throws SQLException SQL异常
     */
    public Message getMessageById(String messageId, Connection connection) throws SQLException {
        // 解析消息ID：格式为 TYPE_conversation_timestamp_random
        String[] parts = messageId.split("_");
        if (parts.length < 4) {
            System.err.println("无效的消息ID格式: " + messageId);
            return null;
        }
        
        String messageType = parts[0];
        String timestampStr = parts[2];
        
        // 将时间戳转换为MySQL datetime格式
        long timestamp = Long.parseLong(timestampStr);
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime messageTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        
        // 使用消息类型和时间来查找消息
        String sql = "SELECT id, type, from_username, content, create_time, is_nsfw, iv FROM messages " +
                     "WHERE type = ? AND create_time >= DATE_SUB(?, INTERVAL 1 SECOND) " +
                     "AND create_time <= DATE_ADD(?, INTERVAL 1 SECOND) " +
                     "ORDER BY create_time DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, messageType);
            stmt.setString(2, messageTime.toString().replace('T', ' ').substring(0, 19));
            stmt.setString(3, messageTime.toString().replace('T', ' ').substring(0, 19));
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int dbId = rs.getInt("id");
                String typeStr = rs.getString("type");
                MessageType type;
                try {
                    type = MessageType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("未知的消息类型: " + typeStr);
                    return null;
                }
                
                String from = rs.getString("from_username");
                String content = rs.getString("content");
                String time = rs.getString("create_time").replace('T', ' ').substring(0, 19);
                boolean isNSFW = rs.getBoolean("is_nsfw");
                String iv = rs.getString("iv");
                
                // 这里需要解析conversationId，暂时使用null
                return new Message(type, from, content, time, isNSFW, iv, messageId, null);
            }
        }
        
        return null;
    }
}
