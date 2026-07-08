package server.club;
import java.util.*;
import server.network.router.MessageRouter;

public abstract class Club {
    private final String name;
    private String id;
    private final Map<String, String> users;
    
    private final MessageRouter messageRouter;
    private int memberCount;
    private String createdAt;
    private String ownerId;
    private Set<String> adminIds;
    private Integer conversationId;
    
    public Club(String name, String id, MessageRouter messageRouter) {
        this.name = name;
        this.id = id;
        this.messageRouter = messageRouter;
        this.users = new HashMap<>();
        this.memberCount = 0;
        this.createdAt = null;
        this.ownerId = null;
        this.adminIds = new HashSet<>();
        this.conversationId = null;
        System.out.println("创建新社团: " + name + " (ID: " + id + ")");
    }
    
    public Integer getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(Integer conversationId) {
        this.conversationId = conversationId;
    }
    
    protected MessageRouter getMessageRouter() {
        return messageRouter;
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        if (this instanceof PublicClub) {
            return "PUBLIC";
        } else if (this instanceof PrivateClub) {
            return "PRIVATE";
        }
        return "UNKNOWN";
    }
    
    public String getId() {
        return id;
    }
    
    public int getMemberCount() {
        return memberCount;
    }
    
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    
    public Set<String> getAdminIds() {
        return adminIds;
    }
    
    public void setAdminIds(Set<String> adminIds) {
        this.adminIds = adminIds;
    }
    
    public void addAdmin(String adminId) {
        if (adminId != null && !adminId.isEmpty()) {
            adminIds.add(adminId);
        }
    }
    
    public void removeAdmin(String adminId) {
        adminIds.remove(adminId);
    }
    
    public boolean isOwner(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }
    
    public boolean isAdmin(String userId) {
        return adminIds.contains(userId);
    }
    
    public boolean isOwnerOrAdmin(String userId) {
        return isOwner(userId) || isAdmin(userId);
    }
    
    public void setId(String id) {
        if (id != null && !id.isEmpty() && this.id == null) {
            this.id = id;
        }
    }
    
    public boolean addUser(String userId, String username) {
        if (userId == null || username == null || userId.isEmpty() || username.isEmpty()) {
            System.err.println("尝试添加无效用户 (ID: " + userId + ", 用户名: " + username + ")");
            return false;
        }
        
        if (users.containsKey(userId)) {
            System.out.println("用户已存在于社团中: " + userId + " - " + username);
            return false;
        }
        
        users.put(userId, username);
        System.out.println("用户添加成功: " + username + " (" + userId + ") 加入社团: " + name);
        return true;
    }
    
    public boolean removeUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            System.err.println("尝试移除无效用户ID: " + userId);
            return false;
        }
        
        String username = users.remove(userId);
        if (username != null) {
            System.out.println("用户移除成功: " + username + " (" + userId + ") 离开社团: " + name);
            return true;
        }
        
        System.out.println("用户不存在于社团中: " + userId);
        return false;
    }
    
    public boolean hasUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return users.containsKey(userId);
    }
    
    public int getUserCount() {
        return users.size();
    }
    
    public Set<String> getUserIds() {
        return Collections.unmodifiableSet(users.keySet());
    }
    
    public Collection<String> getUsernames() {
        return Collections.unmodifiableCollection(users.values());
    }
    
    public String getUsernameById(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return users.get(userId);
    }
    
    @Override
    public String toString() {
        return "Club{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", userCount=" + (memberCount > 0 ? memberCount : users.size()) +
                '}';
    }
    
    public abstract void broadcastMessage(String message);
}