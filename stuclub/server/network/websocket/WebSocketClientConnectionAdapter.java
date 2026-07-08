package server.network.websocket;
import server.network.socket.ClientConnection;
import server.network.router.MessageRouter;

import java.io.IOException;
import java.net.Socket;

/**
 * WebSocket连接适配器，用于将WebSocket连接适配到现有的ClientConnection接口
 */
public class WebSocketClientConnectionAdapter extends ClientConnection {
    private final WebSocketConnection webSocketConnection;
    
    public WebSocketClientConnectionAdapter(WebSocketConnection webSocketConnection) throws IOException {
        // 使用WebSocket专用的构造函数，避免创建Socket对象
        super(webSocketConnection.getMessageRouter());
        this.webSocketConnection = webSocketConnection;
    }
    
    @Override
    public synchronized void send(String message) {
        // 委托给WebSocket连接发送消息
        webSocketConnection.send(message);
    }
    
    @Override
    public String getClientAddress() {
        return "websocket-client";
    }
    
    @Override
    public int getClientPort() {
        return 0;
    }
    
    @Override
    public boolean isConnected() {
        return webSocketConnection.isConnected();
    }
    
    // 覆盖其他不需要的方法
    @Override
    public void run() {
        // WebSocket连接不需要运行线程
    }
    
    @Override
    public synchronized void close() {
        // WebSocket连接由其自己管理关闭
    }
}