package server.network.websocket;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.HandshakeState;
import server.network.router.MessageRouter;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

public class WebSocketServer extends org.java_websocket.server.WebSocketServer {
    private final MessageRouter messageRouter;
    private final ConcurrentHashMap<WebSocket, WebSocketConnection> connections;
    
    public WebSocketServer(int port, MessageRouter messageRouter) {
        // 使用自定义的Draft类来处理CORS
        super(new InetSocketAddress(port), new ArrayList<Draft>() {{ add(new DraftWithCORS()); }});
        this.messageRouter = messageRouter;
        this.connections = new ConcurrentHashMap<>();
        // 允许跨域连接
        setReuseAddr(true);
        System.out.println("WebSocket服务器已创建，将监听端口: " + port);
    }
    
    /**
     * 自定义Draft类，添加CORS支持
     */
    public static class DraftWithCORS extends Draft_6455 {
        @Override
        public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) {
            // 添加CORS头，允许所有来源
            String origin = request.getFieldValue("Origin");
            if (origin != null) {
                response.put("Access-Control-Allow-Origin", origin);
                response.put("Access-Control-Allow-Credentials", "true");
                response.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
            }
            return response;
        }
        
        @Override
        public HandshakeState acceptHandshakeAsServer(ClientHandshake request) {
            // 接受所有握手请求
            return HandshakeState.MATCHED;
        }
    }
    

    
    @Override
    public void onStart() {
        System.out.println("WebSocket服务器已启动，监听端口: " + getPort());
        setConnectionLostTimeout(100);
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("新WebSocket客户端已连接: " + conn.getRemoteSocketAddress());
        // 创建WebSocket连接处理对象
        WebSocketConnection webSocketConnection = new WebSocketConnection(conn, messageRouter);
        connections.put(conn, webSocketConnection);
        webSocketConnection.onOpen();
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("WebSocket客户端已断开连接: " + conn.getRemoteSocketAddress() + "，原因: " + reason);
        WebSocketConnection webSocketConnection = connections.remove(conn);
        if (webSocketConnection != null) {
            webSocketConnection.onClose(code, reason, remote);
        }
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        WebSocketConnection webSocketConnection = connections.get(conn);
        if (webSocketConnection != null) {
            webSocketConnection.onMessage(message);
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket错误: " + ex.getMessage());
        ex.printStackTrace();
        if (conn != null) {
            connections.remove(conn);
        }
    }
    
    public ConcurrentHashMap<WebSocket, WebSocketConnection> getWebSocketConnections() {
        return connections;
    }
}