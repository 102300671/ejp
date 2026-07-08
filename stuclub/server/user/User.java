package server.user;

public class User {
    private final int id;
    private final String username;
    private final String password;
    private final String createdAt;
    private String uuid;
    private boolean acceptTemporaryChat;
    private String status;
    private String avatarUrl;
    private String role;
    private String realName;
    private String studentId;
    
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
        this.acceptTemporaryChat = true;
        this.status = "OFFLINE";
        this.avatarUrl = null;
        this.role = "STUDENT";
        this.realName = null;
        this.studentId = null;
    }
    
    /**
     * 构造用户对象（带角色）
     * @param id 用户ID
     * @param username 用户名
     * @param password 密码（已加密）
     * @param createdAt 创建时间
     * @param uuid 用户唯一标识符
     * @param role 用户角色
     */
    public User(int id, String username, String password, String createdAt, String uuid, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.uuid = uuid;
        this.acceptTemporaryChat = true;
        this.status = "OFFLINE";
        this.avatarUrl = null;
        this.role = role != null ? role : "STUDENT";
        this.realName = null;
        this.studentId = null;
    }
    
    /**
     * 构造用户对象（带角色、真实姓名、学号）
     * @param id 用户ID
     * @param username 用户名
     * @param password 密码（已加密）
     * @param createdAt 创建时间
     * @param uuid 用户唯一标识符
     * @param role 用户角色
     * @param realName 真实姓名
     * @param studentId 学号/工号
     */
    public User(int id, String username, String password, String createdAt, String uuid, String role, String realName, String studentId) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.uuid = uuid;
        this.acceptTemporaryChat = true;
        this.status = "OFFLINE";
        this.avatarUrl = null;
        this.role = role != null ? role : "STUDENT";
        this.realName = realName;
        this.studentId = studentId;
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
    
    public boolean isAcceptTemporaryChat() {
        return acceptTemporaryChat;
    }
    
    public void setAcceptTemporaryChat(boolean acceptTemporaryChat) {
        this.acceptTemporaryChat = acceptTemporaryChat;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getRealName() {
        return realName;
    }
    
    public void setRealName(String realName) {
        this.realName = realName;
    }
    
    public String getStudentId() {
        return studentId;
    }
    
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
}