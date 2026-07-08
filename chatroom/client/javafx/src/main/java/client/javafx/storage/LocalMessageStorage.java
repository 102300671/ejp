package client.javafx.storage;

import client.javafx.protocol.ChatMessage;
import com.google.gson.Gson;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LocalMessageStorage {
    
    private static final String TABLE_NAME = "messages";
    private final Gson gson = new Gson();
    private Connection connection;
    private String username;
    
    public LocalMessageStorage(String username) {
        this.username = username;
        initDatabase();
    }
    
    private void initDatabase() {
        try {
            String dbPath = System.getProperty("user.home") + "/.chat_client/" + username + "_messages.db";
            java.io.File dbDir = new java.io.File(System.getProperty("user.home") + "/.chat_client");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }
            
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "conversation_id INTEGER, " +
                    "conversation_name TEXT, " +
                    "message_type TEXT, " +
                    "from_user TEXT, " +
                    "content TEXT, " +
                    "time TEXT, " +
                    "message_id TEXT, " +
                    "is_nsfw INTEGER, " +
                    "iv TEXT, " +
                    "UNIQUE(message_id) ON CONFLICT REPLACE" +
                    ")";
            
            String createConversationsTable = "CREATE TABLE IF NOT EXISTS conversations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "conversation_id INTEGER UNIQUE, " +
                    "conversation_name TEXT, " +
                    "is_pinned INTEGER DEFAULT 0, " +
                    "is_hidden INTEGER DEFAULT 0, " +
                    "last_message_time TEXT, " +
                    "last_message_content TEXT, " +
                    "UNIQUE(conversation_id) ON CONFLICT REPLACE" +
                    ")";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
                stmt.execute(createConversationsTable);
            }
        } catch (SQLException e) {
            System.err.println("初始化本地消息存储失败: " + e.getMessage());
        }
    }
    
    public void saveMessage(ChatMessage message, String conversationName) {
        if (connection == null || message.conversationId == null) {
            return;
        }
        
        String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" +
                "conversation_id, conversation_name, message_type, from_user, content, " +
                "time, message_id, is_nsfw, iv) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, message.conversationId);
            pstmt.setString(2, conversationName);
            pstmt.setString(3, message.type);
            pstmt.setString(4, message.from);
            pstmt.setString(5, message.content);
            pstmt.setString(6, message.time);
            pstmt.setString(7, message.id);
            pstmt.setInt(8, message.isNSFW ? 1 : 0);
            pstmt.setString(9, message.iv);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("保存消息失败: " + e.getMessage());
        }
    }
    
    public void saveMessages(List<ChatMessage> messages, String conversationName) {
        if (connection == null || messages == null || messages.isEmpty()) {
            return;
        }
        
        String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" +
                "conversation_id, conversation_name, message_type, from_user, content, " +
                "time, message_id, is_nsfw, iv) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (ChatMessage message : messages) {
                if (message.conversationId == null) continue;
                
                pstmt.setInt(1, message.conversationId);
                pstmt.setString(2, conversationName);
                pstmt.setString(3, message.type);
                pstmt.setString(4, message.from);
                pstmt.setString(5, message.content);
                pstmt.setString(6, message.time);
                pstmt.setString(7, message.id);
                pstmt.setInt(8, message.isNSFW ? 1 : 0);
                pstmt.setString(9, message.iv);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("批量保存消息失败: " + e.getMessage());
        }
    }
    
    public List<ChatMessage> getMessagesByConversationId(Integer conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        if (connection == null || conversationId == null) {
            return messages;
        }
        
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE conversation_id = ? ORDER BY time ASC";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ChatMessage message = new ChatMessage();
                    message.type = rs.getString("message_type");
                    message.from = rs.getString("from_user");
                    message.content = rs.getString("content");
                    message.time = rs.getString("time");
                    message.id = rs.getString("message_id");
                    message.conversationId = rs.getInt("conversation_id");
                    message.isNSFW = rs.getInt("is_nsfw") == 1;
                    message.iv = rs.getString("iv");
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询消息失败: " + e.getMessage());
        }
        
        return messages;
    }
    
    public String getLastMessageTime(Integer conversationId) {
        if (connection == null || conversationId == null) {
            return "0";
        }
        
        String sql = "SELECT MAX(time) as last_time FROM " + TABLE_NAME + " WHERE conversation_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String lastTime = rs.getString("last_time");
                    if (lastTime != null && !lastTime.isEmpty()) {
                        return lastTime;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("查询最后消息时间失败: " + e.getMessage());
        }
        
        return "0";
    }
    
    public boolean hasMessages(Integer conversationId) {
        if (connection == null || conversationId == null) {
            return false;
        }
        
        String sql = "SELECT COUNT(*) as count FROM " + TABLE_NAME + " WHERE conversation_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt("count") > 0) {
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("检查消息存在失败: " + e.getMessage());
        }
        
        return false;
    }
    
    public void updateConversation(Integer conversationId, String conversationName, String lastMessageTime, String lastMessageContent) {
        if (connection == null || conversationId == null) {
            return;
        }
        
        String sql = "INSERT OR REPLACE INTO conversations (conversation_id, conversation_name, last_message_time, last_message_content) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            pstmt.setString(2, conversationName);
            pstmt.setString(3, lastMessageTime);
            pstmt.setString(4, lastMessageContent);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("更新会话信息失败: " + e.getMessage());
        }
    }
    
    public void setConversationPinned(Integer conversationId, boolean pinned) {
        if (connection == null || conversationId == null) {
            return;
        }
        
        String sql = "UPDATE conversations SET is_pinned = ? WHERE conversation_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, pinned ? 1 : 0);
            pstmt.setInt(2, conversationId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("设置会话置顶失败: " + e.getMessage());
        }
    }
    
    public void setConversationHidden(Integer conversationId, boolean hidden) {
        if (connection == null || conversationId == null) {
            return;
        }
        
        String sql = "UPDATE conversations SET is_hidden = ? WHERE conversation_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, hidden ? 1 : 0);
            pstmt.setInt(2, conversationId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("设置会话隐藏失败: " + e.getMessage());
        }
    }
    
    public List<ConversationInfo> getConversations() {
        List<ConversationInfo> conversations = new ArrayList<>();
        if (connection == null) {
            return conversations;
        }
        
        String sql = "SELECT * FROM conversations WHERE is_hidden = 0 ORDER BY is_pinned DESC, last_message_time DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ConversationInfo info = new ConversationInfo();
                info.conversationId = rs.getInt("conversation_id");
                info.conversationName = rs.getString("conversation_name");
                info.isPinned = rs.getInt("is_pinned") == 1;
                info.isHidden = rs.getInt("is_hidden") == 1;
                info.lastMessageTime = rs.getString("last_message_time");
                info.lastMessageContent = rs.getString("last_message_content");
                conversations.add(info);
            }
        } catch (SQLException e) {
            System.err.println("查询会话列表失败: " + e.getMessage());
        }
        
        return conversations;
    }
    
    public boolean isConversationPinned(Integer conversationId) {
        if (connection == null || conversationId == null) {
            return false;
        }
        
        String sql = "SELECT is_pinned FROM conversations WHERE conversation_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_pinned") == 1;
                }
            }
        } catch (SQLException e) {
            System.err.println("查询会话置顶状态失败: " + e.getMessage());
        }
        
        return false;
    }
    
    public boolean isConversationHidden(Integer conversationId) {
        if (connection == null || conversationId == null) {
            return false;
        }
        
        String sql = "SELECT is_hidden FROM conversations WHERE conversation_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, conversationId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("is_hidden") == 1;
                }
            }
        } catch (SQLException e) {
            System.err.println("查询会话隐藏状态失败: " + e.getMessage());
        }
        
        return false;
    }
    
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("关闭数据库连接失败: " + e.getMessage());
            }
        }
    }
    
    public static class ConversationInfo {
        public Integer conversationId;
        public String conversationName;
        public boolean isPinned;
        public boolean isHidden;
        public String lastMessageTime;
        public String lastMessageContent;
    }
}