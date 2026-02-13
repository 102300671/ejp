package server.sql.conversation;

public class Conversation {
    private final int id;
    private final String type;
    private final String name;
    private final String createdAt;
    
    public Conversation(int id, String type, String name, String createdAt) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.createdAt = createdAt;
    }
    
    public int getId() {
        return id;
    }
    
    public String getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
