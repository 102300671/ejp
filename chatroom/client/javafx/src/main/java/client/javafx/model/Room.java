package client.javafx.model;

public class Room {
    private String name;
    private String type;
    private int memberCount;
    
    public Room(String name) {
        this.name = name;
        this.type = "public";
        this.memberCount = 0;
    }
    
    public Room(String name, String type, int memberCount) {
        this.name = name;
        this.type = type;
        this.memberCount = memberCount;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public int getMemberCount() {
        return memberCount;
    }
    
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
    
    @Override
    public String toString() {
        return name;
    }
}