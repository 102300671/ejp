package server.network.session;
import server.network.socket.ClientConnection;

public class Session {
    private final String userId;
    private final String username;
    private final ClientConnection clientConnection;
    private volatile boolean isActive;

    public Session(String userId, String username, ClientConnection clientConnection) {
        this.userId = userId;
        this.username = username;
        this.clientConnection = clientConnection;
        this.isActive = true;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if (!active) {
            System.out.println("会话已禁用: 用户ID=" + userId);
        }
    }

    @Override
    public String toString() {
        return "Session{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
