package client.javafx.network;

import client.javafx.protocol.ChatMessage;
import client.javafx.util.Logger;
import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatWebSocketClient extends WebSocketClient {
    
    private final Gson gson = new Gson();
    private List<Consumer<ChatMessage>> onMessageCallbacks = new ArrayList<>();
    private Consumer<Boolean> onConnectionCallback;
    private final Logger logger = new Logger(ChatWebSocketClient.class);
    
    public ChatWebSocketClient(String serverUrl) throws URISyntaxException {
        super(new URI(serverUrl));
    }
    
    public void addOnMessageCallback(Consumer<ChatMessage> callback) {
        this.onMessageCallbacks.add(callback);
    }
    
    public void setOnMessageCallback(Consumer<ChatMessage> callback) {
        this.onMessageCallbacks.clear();
        this.onMessageCallbacks.add(callback);
    }
    
    public void removeOnMessageCallback(Consumer<ChatMessage> callback) {
        this.onMessageCallbacks.remove(callback);
    }
    
    public void clearOnMessageCallbacks() {
        this.onMessageCallbacks.clear();
    }
    
    public void setOnConnectionCallback(Consumer<Boolean> callback) {
        this.onConnectionCallback = callback;
    }
    
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket连接已打开");
        if (onConnectionCallback != null) {
            onConnectionCallback.accept(true);
        }
    }
    
    @Override
    public void onMessage(String message) {
        try {
            logger.debug("收到WebSocket消息: " + message);
            ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);
            logger.debug("消息类型: " + chatMessage.type + ", from: " + chatMessage.from);
            logger.debug("回调数量: " + onMessageCallbacks.size());
            for (Consumer<ChatMessage> callback : onMessageCallbacks) {
                callback.accept(chatMessage);
            }
        } catch (Exception e) {
            logger.error("消息解析失败", e);
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket连接已关闭: code=" + code + ", reason=" + reason + ", remote=" + remote);
        if (onConnectionCallback != null) {
            onConnectionCallback.accept(false);
        }
    }
    
    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket错误", ex);
        if (onConnectionCallback != null) {
            onConnectionCallback.accept(false);
        }
    }
    
    public void sendMessage(ChatMessage message) {
        String json = gson.toJson(message);
        send(json);
    }
    
    public void sendLogin(String username, String password) {
        ChatMessage chatMessage = ChatMessage.createLogin(username, password);
        sendMessage(chatMessage);
    }
    
    public void sendRegister(String username, String password) {
        sendRegister(username, password, null);
    }
    
    public void sendRegister(String username, String password, String avatarPath) {
        ChatMessage chatMessage = ChatMessage.createRegister(username, password, avatarPath);
        sendMessage(chatMessage);
    }
    
    public void sendText(String from, String content, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createText(from, content, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendPrivateChat(String from, String content, String toUsername, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createPrivateChat(from, content, toUsername, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendJoin(String from, String roomName, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createJoin(from, roomName, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendCreateRoom(String from, String roomName, String roomType) {
        ChatMessage chatMessage = ChatMessage.createCreateRoom(from, roomName, roomType);
        sendMessage(chatMessage);
    }
    
    public void sendExitRoom(String from, String roomName) {
        ChatMessage chatMessage = ChatMessage.createExitRoom(from, roomName);
        sendMessage(chatMessage);
    }
    
    public void sendListRooms(String from) {
        ChatMessage chatMessage = ChatMessage.createListRooms(from);
        sendMessage(chatMessage);
    }
    
    public void sendRequestFriendList(String from) {
        ChatMessage chatMessage = ChatMessage.createRequestFriendList(from);
        sendMessage(chatMessage);
    }
    
    public void sendFriendRequest(String from, String toUsername, String message) {
        ChatMessage chatMessage = ChatMessage.createFriendRequest(from, toUsername, message);
        sendMessage(chatMessage);
    }
    
    public void sendFriendRequestResponse(String from, boolean accept, String username) {
        ChatMessage chatMessage = ChatMessage.createFriendRequestResponse(from, accept, username);
        sendMessage(chatMessage);
    }
    
    public void sendRequestAllFriendRequests(String username) {
        ChatMessage chatMessage = ChatMessage.createRequestAllFriendRequests(username);
        sendMessage(chatMessage);
    }
    
    public void sendUpdateProfile(String from, String content) {
        ChatMessage chatMessage = ChatMessage.createUpdateProfile(from, content);
        sendMessage(chatMessage);
    }
    
    public void sendImage(String from, String imageUrl, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createImage(from, imageUrl, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendFile(String from, String fileUrl, String fileName, String openMode, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createFile(from, fileUrl, fileName, openMode, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendVoice(String from, String voiceUrl, String fileName, int duration, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createVoice(from, voiceUrl, fileName, duration, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendRequestToken(String from) {
        ChatMessage chatMessage = ChatMessage.createRequestToken(from);
        sendMessage(chatMessage);
    }
    
    public void sendRequestHistory(String from, String lastTimestamp, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createRequestHistory(from, lastTimestamp, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendSearchUsers(String from, String keyword) {
        ChatMessage chatMessage = ChatMessage.createSearchUsers(from, keyword);
        sendMessage(chatMessage);
    }
    
    public void sendSearchRooms(String from, String keyword) {
        ChatMessage chatMessage = ChatMessage.createSearchRooms(from, keyword);
        sendMessage(chatMessage);
    }
    
    public void sendRequestRoomJoin(String from, String roomName) {
        ChatMessage chatMessage = ChatMessage.createRequestRoomJoin(from, roomName);
        sendMessage(chatMessage);
    }
    
    public void sendRoomJoinResponse(String from, boolean accept, String roomName, String requester) {
        ChatMessage chatMessage = ChatMessage.createRoomJoinResponse(from, accept, roomName, requester);
        sendMessage(chatMessage);
    }
    
    public void sendSetRoomAnnouncement(String from, String announcement, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createSetRoomAnnouncement(from, announcement, conversationId);
        sendMessage(chatMessage);
    }
}