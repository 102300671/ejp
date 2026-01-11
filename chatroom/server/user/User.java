package server.user;

public class User {
    private final int id;
    private final String username;
    private final String password;
    private final String createdAt;
    private String uuid;
    
    /**
     * 构造用户对象
     * @param id 用户ID
     * @param username 用户名
     * @param password 密码（已加密）
     * @param createdAt 创建时间
     * @param uuid 用户唯一标识符
     */
    public User(int id, String username, String password, String createdAt, String uuid) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.uuid = uuid;
    }
    
    public int getId() {
        return id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}