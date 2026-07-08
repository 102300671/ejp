package client.javafx.network;

import client.javafx.protocol.ChatMessage;
import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

public class ChatWebSocketClient extends WebSocketClient {
    
    private final Gson gson = new Gson();
    private Consumer<ChatMessage> onMessageCallback;
    private Consumer<Boolean> onConnectionCallback;
    
    public ChatWebSocketClient(String serverUrl) throws URISyntaxException {
        super(new URI(serverUrl));
    }
    
    public void setOnMessageCallback(Consumer<ChatMessage> callback) {
        this.onMessageCallback = callback;
    }
    
    public void setOnConnectionCallback(Consumer<Boolean> callback) {
        this.onConnectionCallback = callback;
    }
    
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (onConnectionCallback != null) {
            onConnectionCallback.accept(true);
        }
    }
    
    @Override
    public void onMessage(String message) {
        try {
            ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);
            if (onMessageCallback != null) {
                onMessageCallback.accept(chatMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (onConnectionCallback != null) {
            onConnectionCallback.accept(false);
        }
    }
    
    @Override
    public void onError(Exception ex) {
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
    
    public void sendRegister(String username, String password, byte[] avatarData) {
        ChatMessage chatMessage = ChatMessage.createRegister(username, password, avatarData);
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
    
    public void sendImage(String from, String imageUrl, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createImage(from, imageUrl, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendFile(String from, String fileUrl, String fileName, String openMode, Integer conversationId) {
        ChatMessage chatMessage = ChatMessage.createFile(from, fileUrl, fileName, openMode, conversationId);
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
}