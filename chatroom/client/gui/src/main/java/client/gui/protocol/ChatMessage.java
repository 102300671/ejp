package client.gui.protocol;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class ChatMessage {
    private String type;
    private String from;
    private String content;
    private String time;
    private String id;
    
    @SerializedName("conversationId")
    private String conversationId;
    
    private boolean isNSFW;
    private String iv;
    
    public ChatMessage() {
    }
    
    public ChatMessage(String type, String from, String content) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.time = getCurrentTime();
        this.id = generateMessageId(type, from);
    }
    
    public static ChatMessage createLogin(String username, String password) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.LOGIN.name();
        msg.from = username;
        msg.content = username + ":" + password;
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.LOGIN.name(), username);
        return msg;
    }
    
    public static ChatMessage createRegister(String username, String password) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.REGISTER.name();
        msg.from = username;
        msg.content = username + ":" + password;
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.REGISTER.name(), username);
        return msg;
    }
    
    public static ChatMessage createText(String from, String content, String conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.TEXT.name();
        msg.from = from;
        if (conversationId != null && !conversationId.isEmpty()) {
            msg.content = "{\"conversation_id\":\"" + conversationId + "\",\"content\":\"" + escapeJson(content) + "\"}";
        } else {
            msg.content = content;
        }
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.TEXT.name(), from);
        return msg;
    }
    
    public static ChatMessage createJoin(String from, String roomName, String conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.JOIN.name();
        msg.from = from;
        if (conversationId != null && !conversationId.isEmpty()) {
            msg.content = "{\"conversation_id\":\"" + conversationId + "\",\"room_name\":\"" + roomName + "\"}";
            msg.conversationId = conversationId;
        } else {
            msg.content = roomName;
        }
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.JOIN.name(), roomName);
        return msg;
    }
    
    public static ChatMessage createListRooms(String from) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.LIST_ROOMS.name();
        msg.from = from;
        msg.content = "";
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.LIST_ROOMS.name(), from);
        return msg;
    }
    
    public static ChatMessage createCreateRoom(String from, String roomName, String roomType) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.CREATE_ROOM.name();
        msg.from = from;
        msg.content = "{\"room_name\":\"" + roomName + "\",\"room_type\":\"" + roomType + "\"}";
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.CREATE_ROOM.name(), roomName);
        return msg;
    }
    
    public static ChatMessage createExitRoom(String from, String roomName) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.EXIT_ROOM.name();
        msg.from = from;
        msg.content = roomName;
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.EXIT_ROOM.name(), roomName);
        return msg;
    }
    
    public static ChatMessage createPrivateChat(String from, String content, String conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.PRIVATE_CHAT.name();
        msg.from = from;
        if (conversationId != null && !conversationId.isEmpty()) {
            msg.content = "{\"conversation_id\":\"" + conversationId + "\",\"content\":\"" + escapeJson(content) + "\"}";
        } else {
            msg.content = content;
        }
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.PRIVATE_CHAT.name(), from);
        return msg;
    }
    
    public static ChatMessage createRequestFriendList(String from) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.REQUEST_FRIEND_LIST.name();
        msg.from = from;
        msg.content = "";
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.REQUEST_FRIEND_LIST.name(), from);
        return msg;
    }
    
    public static ChatMessage createFriendRequest(String from, String toUsername, String message) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.FRIEND_REQUEST.name();
        msg.from = from;
        msg.content = "to:" + toUsername + ";" + (message != null ? message : "");
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.FRIEND_REQUEST.name(), toUsername);
        return msg;
    }
    
    public static ChatMessage createFriendRequestResponse(String from, String toUsername, boolean accept) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.FRIEND_REQUEST_RESPONSE.name();
        msg.from = from;
        msg.content = (accept ? "accept" : "reject") + ":" + toUsername;
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.FRIEND_REQUEST_RESPONSE.name(), toUsername);
        return msg;
    }
    
    public static ChatMessage createRequestAllFriendRequests(String from) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.REQUEST_ALL_FRIEND_REQUESTS.name();
        msg.from = from;
        msg.content = "";
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.REQUEST_ALL_FRIEND_REQUESTS.name(), from);
        return msg;
    }
    
    public static ChatMessage createPrivateChat(String from, String content, String toUsername, String conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.PRIVATE_CHAT.name();
        msg.from = from;
        
        JsonObject jsonContent = new JsonObject();
        jsonContent.addProperty("content", content);
        if (conversationId != null && !conversationId.isEmpty()) {
            jsonContent.addProperty("conversation_id", conversationId);
        } else {
            jsonContent.addProperty("to", toUsername);
        }
        msg.content = jsonContent.toString();
        
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.PRIVATE_CHAT.name(), toUsername);
        return msg;
    }
    
    public static ChatMessage createListRoomUsers(String from, String roomName, String conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.LIST_ROOM_USERS.name();
        msg.from = from;
        if (conversationId != null && !conversationId.isEmpty()) {
            msg.content = "{\"conversation_id\":\"" + conversationId + "\",\"content\":\"\"}";
        } else {
            msg.content = "";
        }
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.LIST_ROOM_USERS.name(), roomName);
        return msg;
    }
    
    public static String getCurrentTime() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    private static String generateMessageId(String type, String from) {
        return type + "_" + from + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    private static String escapeJson(String content) {
        if (content == null) return "";
        return content.replace("\\", "\\\\")
                      .replace("\"", "\\\"")
                      .replace("\n", "\\n")
                      .replace("\r", "\\r");
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public boolean isNSFW() {
        return isNSFW;
    }
    
    public void setNSFW(boolean NSFW) {
        isNSFW = NSFW;
    }
    
    public String getIv() {
        return iv;
    }
    
    public void setIv(String iv) {
        this.iv = iv;
    }
    
    public MessageType getMessageType() {
        try {
            return MessageType.valueOf(type);
        } catch (Exception e) {
            return null;
        }
    }
}