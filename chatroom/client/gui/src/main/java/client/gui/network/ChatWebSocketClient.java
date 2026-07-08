package client.gui.network;

import client.gui.protocol.ChatMessage;
import client.gui.protocol.MessageType;
import com.google.gson.Gson;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatWebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketClient.class);
    
    private org.java_websocket.client.WebSocketClient wsClient;
    private MessageListener messageListener;
    private ConnectionListener connectionListener;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    
    private String serverAddress;
    private int serverPort;
    private String protocol;
    private boolean isConnected = false;
    private boolean isAuthenticated = false;
    
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_INTERVAL = 5000;
    
    public interface MessageListener {
        void onMessage(ChatMessage message);
    }
    
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }
    
    public ChatWebSocketClient(String serverAddress, int serverPort, String protocol) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.protocol = protocol;
    }
    
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }
    
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    public void connect() {
        try {
            String url = protocol + "://" + serverAddress + ":" + serverPort;
            logger.info("Connecting to WebSocket server: {}", url);
            
            wsClient = new org.java_websocket.client.WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.info("WebSocket connection opened");
                    isConnected = true;
                    reconnectAttempts = 0;
                    
                    if (connectionListener != null) {
                        connectionListener.onConnected();
                    }
                }
                
                @Override
                public void onMessage(String message) {
                    logger.debug("Received message: {}", message);
                    executorService.submit(() -> {
                        try {
                            ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);
                            if (messageListener != null) {
                                messageListener.onMessage(chatMessage);
                            }
                        } catch (Exception e) {
                            logger.error("Error parsing message: {}", e.getMessage());
                        }
                    });
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("WebSocket connection closed: code={}, reason={}, remote={}", code, reason, remote);
                    isConnected = false;
                    isAuthenticated = false;
                    
                    if (connectionListener != null) {
                        connectionListener.onDisconnected();
                    }
                    
                    if (code != 1000 && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect();
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    logger.error("WebSocket error: {}", ex.getMessage());
                    isConnected = false;
                    
                    if (connectionListener != null) {
                        connectionListener.onError(ex.getMessage());
                    }
                    
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect();
                    }
                }
            };
            
            wsClient.connect();
        } catch (URISyntaxException e) {
            logger.error("Invalid WebSocket URL: {}", e.getMessage());
            if (connectionListener != null) {
                connectionListener.onError("Invalid server address");
            }
        }
    }
    
    private void scheduleReconnect() {
        reconnectAttempts++;
        long rawDelay = RECONNECT_INTERVAL * (long) Math.pow(2, reconnectAttempts - 1);
        final long delay = Math.min(rawDelay, 30000);
        
        logger.info("Scheduling reconnect attempt {}/{} in {}ms", reconnectAttempts, MAX_RECONNECT_ATTEMPTS, delay);
        
        executorService.submit(() -> {
            try {
                Thread.sleep(delay);
                if (!isConnected) {
                    connect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }
        isConnected = false;
        isAuthenticated = false;
    }
    
    public void sendMessage(ChatMessage message) {
        if (!isConnected || wsClient == null) {
            logger.warn("Not connected to server, cannot send message");
            return;
        }
        
        String json = gson.toJson(message);
        logger.debug("Sending message: {}", json);
        
        try {
            wsClient.send(json);
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
        }
    }
    
    public void sendLogin(String username, String password) {
        ChatMessage message = ChatMessage.createLogin(username, password);
        sendMessage(message);
    }
    
    public void sendRegister(String username, String password) {
        ChatMessage message = ChatMessage.createRegister(username, password);
        sendMessage(message);
    }
    
    public void sendText(String from, String content, String conversationId) {
        ChatMessage message = ChatMessage.createText(from, content, conversationId);
        sendMessage(message);
    }
    
    public void sendJoin(String from, String roomName, String conversationId) {
        ChatMessage message = ChatMessage.createJoin(from, roomName, conversationId);
        sendMessage(message);
    }
    
    public void sendListRooms(String from) {
        ChatMessage message = ChatMessage.createListRooms(from);
        sendMessage(message);
    }
    
    public void sendCreateRoom(String from, String roomName, String roomType) {
        ChatMessage message = ChatMessage.createCreateRoom(from, roomName, roomType);
        sendMessage(message);
    }
    
    public void sendExitRoom(String from, String roomName) {
        ChatMessage message = ChatMessage.createExitRoom(from, roomName);
        sendMessage(message);
    }
    
    public void sendPrivateChat(String from, String content, String conversationId) {
        ChatMessage message = ChatMessage.createPrivateChat(from, content, conversationId);
        sendMessage(message);
    }
    
    public void sendRequestFriendList(String from) {
        ChatMessage message = ChatMessage.createRequestFriendList(from);
        sendMessage(message);
    }
    
    public void sendFriendRequest(String from, String toUsername, String message) {
        ChatMessage chatMessage = ChatMessage.createFriendRequest(from, toUsername, message);
        sendMessage(chatMessage);
    }
    
    public void sendFriendRequestResponse(String from, String toUsername, boolean accept) {
        ChatMessage chatMessage = ChatMessage.createFriendRequestResponse(from, toUsername, accept);
        sendMessage(chatMessage);
    }
    
    public void sendRequestAllFriendRequests(String from) {
        ChatMessage chatMessage = ChatMessage.createRequestAllFriendRequests(from);
        sendMessage(chatMessage);
    }
    
    public void sendPrivateChat(String from, String content, String toUsername, String conversationId) {
        ChatMessage chatMessage = ChatMessage.createPrivateChat(from, content, toUsername, conversationId);
        sendMessage(chatMessage);
    }
    
    public void sendListRoomUsers(String from, String roomName, String conversationId) {
        ChatMessage message = ChatMessage.createListRoomUsers(from, roomName, conversationId);
        sendMessage(message);
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    public void setAuthenticated(boolean authenticated) {
        isAuthenticated = authenticated;
    }
    
    public void shutdown() {
        disconnect();
        executorService.shutdown();
    }
}