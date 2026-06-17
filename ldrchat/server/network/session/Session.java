package server.network.session;
import server.network.socket.ClientConnection;

public class Session {
    private final String userId;
    private final String username;
    private volatile ClientConnection clientConnection;
    private volatile boolean isActive;
    private String currentRoom;

    public Session(String userId, String username, ClientConnection clientConnection) {
        this.userId = userId;
        this.username = username;
        this.clientConnection = clientConnection;
        this.isActive = true;
        this.currentRoom = null;
        System.out.println("创建新会话: 用户ID=" + userId + ", 用户名=" + username);
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public ClientConnection getClientConnection() {
        return clientConnection;
    }

    public void setClientConnection(ClientConnection clientConnection) {
        if (clientConnection != null) {
            this.clientConnection = clientConnection;
            System.out.println("会话客户端连接已更新: 用户ID=" + userId);
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) {
            System.out.println("会话已禁用: 用户ID=" + userId);
        }
    }
    
    public String getCurrentRoom() {
        return currentRoom;
    }
    
    public void setCurrentRoom(String currentRoom) {
        this.currentRoom = currentRoom;
    }

    @Override
    public String toString() {
        return "Session{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", isActive=" + isActive +
                ", currentRoom='" + currentRoom + '\'' +
                '}';
    }
}
