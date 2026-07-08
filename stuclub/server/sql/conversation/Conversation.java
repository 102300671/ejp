package server.sql.conversation;

public class Conversation {
    private final int id;
    private final String type;
    private final String name;
    private final String createdAt;
    private int clubId;
    
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
    
    public int getClubId() {
        return clubId;
    }
    
    public void setClubId(int clubId) {
        this.clubId = clubId;
    }
    
    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", clubId=" + clubId +
                '}';
    }
}
