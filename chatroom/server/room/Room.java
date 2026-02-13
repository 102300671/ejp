package server.room;
import java.util.*;
import server.network.router.MessageRouter;

public abstract class Room {
    private final String name;
    private String id;
    // 使用Map存储用户信息，键为用户ID，值为用户名，便于快速查找和管理
    private final Map<String, String> users;
    
    private final MessageRouter messageRouter;
    private int memberCount; // 房间成员数量
    private String createdAt; // 房间创建时间
    private String ownerId; // 房主ID
    private Set<String> adminIds; // 管理员ID集合
    private Integer conversationId; // 会话ID
    
    /**
     * 构造房间对象
     * @param name 房间名称
     * @param id 房间ID
     * @param messageRouter 消息路由器
     */
    public Room(String name, String id, MessageRouter messageRouter) {
        this.name = name;
        this.id = id;
        this.messageRouter = messageRouter;
        this.users = new HashMap<>();
        this.memberCount = 0;
        this.createdAt = null;
        this.ownerId = null;
        this.adminIds = new HashSet<>();
        this.conversationId = null;
        System.out.println("创建新房间: " + name + " (ID: " + id + ")");
    }
    
    /**
     * 获取会话ID
     * @return 会话ID
     */
    public Integer getConversationId() {
        return conversationId;
    }
    
    /**
     * 设置会话ID
     * @param conversationId 会话ID
     */
    public void setConversationId(Integer conversationId) {
        this.conversationId = conversationId;
    }
    
    /**
     * 获取消息路由器
     * @return 消息路由器
     */
    protected MessageRouter getMessageRouter() {
        return messageRouter;
    }
    
    /**
     * 获取房间名称
     * @return 房间名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取房间类型
     * @return 房间类型（PUBLIC或PRIVATE）
     */
    public String getType() {
        if (this instanceof PublicRoom) {
            return "PUBLIC";
        } else if (this instanceof PrivateRoom) {
            return "PRIVATE";
        }
        return "UNKNOWN";
    }
    
    /**
     * 获取房间ID
     * @return 房间ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取房间成员数量
     * @return 房间成员数量
     */
    public int getMemberCount() {
        return memberCount;
    }
    
    /**
     * 设置房间成员数量
     * @param memberCount 房间成员数量
     */
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
    
    /**
     * 获取房间创建时间
     * @return 房间创建时间
     */
    public String getCreatedAt() {
        return createdAt;
    }
    
    /**
     * 设置房间创建时间
     * @param createdAt 房间创建时间
     */
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * 获取房主ID
     * @return 房主ID
     */
    public String getOwnerId() {
        return ownerId;
    }
    
    /**
     * 设置房主ID
     * @param ownerId 房主ID
     */
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    
    /**
     * 获取管理员ID集合
     * @return 管理员ID集合
     */
    public Set<String> getAdminIds() {
        return adminIds;
    }
    
    /**
     * 添加管理员
     * @param adminId 管理员ID
     */
    public void addAdmin(String adminId) {
        if (adminId != null && !adminId.isEmpty()) {
            adminIds.add(adminId);
        }
    }
    
    /**
     * 移除管理员
     * @param adminId 管理员ID
     */
    public void removeAdmin(String adminId) {
        adminIds.remove(adminId);
    }
    
    /**
     * 检查用户是否为房主
     * @param userId 用户ID
     * @return true表示是房主
     */
    public boolean isOwner(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }
    
    /**
     * 检查用户是否为管理员
     * @param userId 用户ID
     * @return true表示是管理员
     */
    public boolean isAdmin(String userId) {
        return adminIds.contains(userId);
    }
    
    /**
     * 检查用户是否为房主或管理员
     * @param userId 用户ID
     * @return true表示是房主或管理员
     */
    public boolean isOwnerOrAdmin(String userId) {
        return isOwner(userId) || isAdmin(userId);
    }
    
    /**
     * 设置房间ID（仅用于DAO层从数据库获取ID后设置）
     * @param id 房间ID
     */
    public void setId(String id) {
        if (id != null && !id.isEmpty() && this.id == null) {
            this.id = id;
        }
    }
    
    /**
     * 添加用户到房间
     * @param userId 用户ID
     * @param username 用户名
     * @return true表示添加成功，false表示用户已存在
     */
    public boolean addUser(String userId, String username) {
        if (userId == null || username == null || userId.isEmpty() || username.isEmpty()) {
            System.err.println("尝试添加无效用户 (ID: " + userId + ", 用户名: " + username + ")");
            return false;
        }
        
        if (users.containsKey(userId)) {
            System.out.println("用户已存在于房间中: " + userId + " - " + username);
            return false;
        }
        
        users.put(userId, username);
        System.out.println("用户添加成功: " + username + " (" + userId + ") 加入房间: " + name);
        return true;
    }
    
    /**
     * 从房间移除用户
     * @param userId 用户ID
     * @return true表示移除成功，false表示用户不存在
     */
    public boolean removeUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            System.err.println("尝试移除无效用户ID: " + userId);
            return false;
        }
        
        String username = users.remove(userId);
        if (username != null) {
            System.out.println("用户移除成功: " + username + " (" + userId + ") 离开房间: " + name);
            return true;
        }
        
        System.out.println("用户不存在于房间中: " + userId);
        return false;
    }
    
    /**
     * 检查用户是否在房间中
     * @param userId 用户ID
     * @return true表示用户在房间中，false表示不在
     */
    public boolean hasUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return users.containsKey(userId);
    }
    
    /**
     * 获取房间内的用户数量
     * @return 用户数量
     */
    public int getUserCount() {
        return users.size();
    }
    
    /**
     * 获取房间内的所有用户ID
     * @return 用户ID集合
     */
    public Set<String> getUserIds() {
        return Collections.unmodifiableSet(users.keySet());
    }
    
    /**
     * 获取房间内的所有用户名
     * @return 用户名集合
     */
    public Collection<String> getUsernames() {
        return Collections.unmodifiableCollection(users.values());
    }
    
    /**
     * 根据用户ID获取用户名
     * @param userId 用户ID
     * @return 用户名，如果用户不存在则返回null
     */
    public String getUsernameById(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return users.get(userId);
    }
    
    /**
     * 获取房间的字符串表示
     * @return 房间的字符串表示
     */
    @Override
    public String toString() {
        return "Room{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", userCount=" + (memberCount > 0 ? memberCount : users.size()) +
                '}';
    }
    
    /**
     * 广播消息给房间内的所有用户（抽象方法，由子类实现）
     * @param message 要广播的消息
     */
    public abstract void broadcastMessage(String message);
}
