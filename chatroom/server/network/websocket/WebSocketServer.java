package server.network.websocket;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.HandshakeState;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import server.network.router.MessageRouter;
import server.config.ServiceConfig;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public class WebSocketServer extends org.java_websocket.server.WebSocketServer {
    private final MessageRouter messageRouter;
    private final ConcurrentHashMap<WebSocket, WebSocketConnection> connections;
    
    public WebSocketServer(int port, MessageRouter messageRouter) {
        this(port, messageRouter, false);
    }
    
    public WebSocketServer(int port, MessageRouter messageRouter, boolean enableSsl) {
        super(new InetSocketAddress(port), new ArrayList<Draft>() {{ add(new DraftWithCORS()); }});
        this.messageRouter = messageRouter;
        this.connections = new ConcurrentHashMap<>();
        setReuseAddr(true);
        
        if (enableSsl) {
            configureSsl();
            System.out.println("WebSocket服务器已创建（SSL模式），将监听端口: " + port);
        } else {
            System.out.println("WebSocket服务器已创建（普通模式），将监听端口: " + port);
        }
    }
    
    /**
     * 配置SSL/TLS
     */
    private void configureSsl() {
        try {
            ServiceConfig config = ServiceConfig.getInstance();
            
            String keystorePath = config.getWebSocketSslKeystorePath();
            String keystorePassword = config.getWebSocketSslKeystorePassword();
            String keystoreType = config.getWebSocketSslKeystoreType();
            String keyPassword = config.getWebSocketSslKeyPassword();
            
            if (keystorePath == null || keystorePath.isEmpty()) {
                throw new IllegalArgumentException("SSL已启用但未配置keystore路径");
            }
            
            if (keystorePassword == null || keystorePassword.isEmpty()) {
                throw new IllegalArgumentException("SSL已启用但未配置keystore密码");
            }
            
            System.out.println("正在加载SSL证书...");
            System.out.println("Keystore路径: " + keystorePath);
            System.out.println("Keystore类型: " + keystoreType);
            
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPassword != null && !keyPassword.isEmpty() ? keyPassword.toCharArray() : keystorePassword.toCharArray());
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            
            setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            
            System.out.println("SSL配置成功");
        } catch (Exception e) {
            System.err.println("配置SSL失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("无法配置SSL: " + e.getMessage(), e);
        }
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