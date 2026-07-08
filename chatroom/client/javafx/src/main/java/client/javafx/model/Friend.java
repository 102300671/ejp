package client.javafx.model;

public class Friend {
    private String username;
    private boolean online;
    
    public Friend(String username) {
        this.username = username;
        this.online = false;
    }
    
    public Friend(String username, boolean online) {
        this.username = username;
        this.online = online;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public boolean isOnline() {
        return online;
    }
    
    public void setOnline(boolean online) {
        this.online = online;
    }
    
    @Override
    public String toString() {
        return username;
    }
}