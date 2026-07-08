package client.javafx.protocol;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class ChatMessage {
    
    @SerializedName("type")
    public String type;
    
    @SerializedName("from")
    public String from;
    
    @SerializedName("content")
    public String content;
    
    @SerializedName("time")
    public String time;
    
    @SerializedName("id")
    public String id;
    
    @SerializedName("conversationId")
    public Integer conversationId;
    
    @SerializedName("isNSFW")
    public boolean isNSFW;
    
    @SerializedName("iv")
    public String iv;
    
    public MessageType getMessageType() {
        try {
            return MessageType.valueOf(type);
        } catch (Exception e) {
            return null;
        }
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
        return createRegister(username, password, null);
    }
    
    public static ChatMessage createRegister(String username, String password, byte[] avatarData) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.REGISTER.name();
        msg.from = username;
        
        JsonObject jsonContent = new JsonObject();
        jsonContent.addProperty("username", username);
        jsonContent.addProperty("password", password);
        if (avatarData != null && avatarData.length > 0) {
            String base64Avatar = java.util.Base64.getEncoder().encodeToString(avatarData);
            jsonContent.addProperty("avatar", base64Avatar);
        }
        msg.content = jsonContent.toString();
        
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.REGISTER.name(), username);
        return msg;
    }
    
    public static ChatMessage createText(String from, String content, Integer conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.TEXT.name();
        msg.from = from;
        
        JsonObject jsonContent = new JsonObject();
        if (conversationId != null) {
            jsonContent.addProperty("conversation_id", conversationId);
        }
        jsonContent.addProperty("content", content);
        msg.content = jsonContent.toString();
        
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.TEXT.name(), from);
        return msg;
    }
    
    public static ChatMessage createPrivateChat(String from, String content, String toUsername, Integer conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.PRIVATE_CHAT.name();
        msg.from = from;
        msg.conversationId = conversationId;
        
        JsonObject jsonContent = new JsonObject();
        jsonContent.addProperty("content", content);
        if (conversationId == null) {
            jsonContent.addProperty("to", toUsername);
        }
        msg.content = jsonContent.toString();
        
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.PRIVATE_CHAT.name(), toUsername);
        return msg;
    }
    
    public static ChatMessage createJoin(String from, String roomName, Integer conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.JOIN.name();
        msg.from = from;
        
        JsonObject jsonContent = new JsonObject();
        if (conversationId != null) {
            jsonContent.addProperty("conversation_id", conversationId);
        }
        jsonContent.addProperty("room_name", roomName);
        msg.content = jsonContent.toString();
        
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.JOIN.name(), roomName);
        return msg;
    }
    
    public static ChatMessage createCreateRoom(String from, String roomName, String roomType) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.CREATE_ROOM.name();
        msg.from = from;
        
        JsonObject jsonContent = new JsonObject();
        jsonContent.addProperty("room_name", roomName);
        jsonContent.addProperty("room_type", roomType);
        msg.content = jsonContent.toString();
        
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
    
    public static ChatMessage createListRooms(String from) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.LIST_ROOMS.name();
        msg.from = from;
        msg.content = "";
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.LIST_ROOMS.name(), from);
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
        msg.content = "to:" + toUsername + ";message:" + message;
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.FRIEND_REQUEST.name(), toUsername);
        return msg;
    }
    
    public static ChatMessage createFriendRequestResponse(String from, boolean accept, String username) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.FRIEND_REQUEST_RESPONSE.name();
        msg.from = from;
        msg.content = (accept ? "accept:" : "reject:") + username;
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.FRIEND_REQUEST_RESPONSE.name(), username);
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
    
    public static ChatMessage createImage(String from, String imageUrl, Integer conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.IMAGE.name();
        msg.from = from;
        msg.content = imageUrl;
        msg.time = getCurrentTime();
        msg.conversationId = conversationId;
        msg.id = generateMessageId(MessageType.IMAGE.name(), from);
        return msg;
    }
    
    public static ChatMessage createFile(String from, String fileUrl, String fileName, String openMode, Integer conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.FILE.name();
        msg.from = from;
        
        JsonObject jsonContent = new JsonObject();
        jsonContent.addProperty("url", fileUrl);
        jsonContent.addProperty("fileName", fileName);
        jsonContent.addProperty("openMode", openMode != null ? openMode : "download");
        msg.content = jsonContent.toString();
        
        msg.time = getCurrentTime();
        msg.conversationId = conversationId;
        msg.id = generateMessageId(MessageType.FILE.name(), from);
        return msg;
    }
    
    public static ChatMessage createRequestToken(String from) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.REQUEST_TOKEN.name();
        msg.from = from;
        msg.content = "";
        msg.time = getCurrentTime();
        msg.id = generateMessageId(MessageType.REQUEST_TOKEN.name(), from);
        return msg;
    }
    
    public static ChatMessage createRequestHistory(String from, String lastTimestamp, Integer conversationId) {
        ChatMessage msg = new ChatMessage();
        msg.type = MessageType.REQUEST_HISTORY.name();
        msg.from = from;
        msg.content = lastTimestamp;
        msg.time = getCurrentTime();
        msg.conversationId = conversationId;
        msg.id = generateMessageId(MessageType.REQUEST_HISTORY.name(), from);
        return msg;
    }
    
    public static String getCurrentTime() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    private static String generateMessageId(String type, String from) {
        return type + "_" + from + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }
}