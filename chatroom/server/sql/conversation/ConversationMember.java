package server.sql.conversation;

public class ConversationMember {
    private final int conversationId;
    private final int userId;
    private final String username;
    private final String role;
    private final String joinedAt;
    
    public ConversationMember(int conversationId, int userId, String username, String role, String joinedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.joinedAt = joinedAt;
    }
    
    public int getConversationId() {
        return conversationId;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getRole() {
        return role;
    }
    
    public String getJoinedAt() {
        return joinedAt;
    }
    
    @Override
    public String toString() {
        return "ConversationMember{" +
                "conversationId=" + conversationId +
                ", userId=" + userId +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", joinedAt='" + joinedAt + '\'' +
                '}';
    }
}
