package server;
import java.util.Scanner;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import server.network.socket.*;
import server.network.websocket.WebSocketServer;
import server.network.router.MessageRouter;
import server.sql.DatabaseManager;
import server.sql.conversation.ConversationDAO;
import server.sql.conversation.Conversation;
import server.sql.club.ClubDAO;
import server.config.ServiceConfig;

public class ChatServer {
    private ServerListener serverListener;
    private WebSocketServer webSocketServer;
    private MessageRouter messageRouter;
    private DatabaseManager databaseManager;
    private volatile boolean isRunning;
    
    public ChatServer() {
        this.isRunning = false;
    }
    
    /**
     * 初始化服务器
     * @param port 服务器端口
     * @throws Exception 初始化过程中可能发生的异常
     */
    public void initialize(int port) throws Exception {
        System.out.println("正在初始化聊天服务器...");
        
        // 初始化服务配置
        ServiceConfig serviceConfig = ServiceConfig.getInstance();
        System.out.println("ZFile服务器地址: " + serviceConfig.getZfileServerUrl());
        
        // 初始化数据库管理器
        this.databaseManager = new DatabaseManager();
        System.out.println("数据库管理器已初始化");
        
        // 初始化消息路由器
        this.messageRouter = new MessageRouter();
        
        // 初始化服务器监听器
        this.serverListener = new ServerListener(port, messageRouter);
        
        // 初始化WebSocket服务器（使用比TCP端口大1的端口）
        boolean enableSsl = serviceConfig.isWebSocketSslEnabled();
        this.webSocketServer = new WebSocketServer(port + 1, messageRouter, enableSsl);
        
        // 检查并创建system房间
        setupSystemRoom();
        
        // 从数据库加载所有房间
        loadAllRooms();
        
        this.isRunning = true;
        System.out.println("聊天服务器初始化完成");
        printStatus();
    }
    
    /**
     * 检查并创建system会话
     * @throws Exception 数据库操作异常
     */
    private void setupSystemRoom() throws Exception {
        System.out.println("正在检查并创建system会话...");
        Connection conn = null;
        try {
            conn = databaseManager.getConnection();
            ConversationDAO conversationDAO = new ConversationDAO();
            
            int conversationId = -1;
            // 检查system conversation是否存在
            if (!conversationDAO.conversationExists("system", conn)) {
                System.out.println("system conversation不存在，正在创建...");
                // 创建system conversation
                conversationId = conversationDAO.createConversation("SYSTEM", "system", conn);
                System.out.println("system conversation创建成功，ID: " + conversationId);
            } else {
                System.out.println("system conversation已存在");
                // 获取conversation_id
                Conversation conversation = conversationDAO.getConversationByName("system", conn);
                if (conversation != null) {
                    conversationId = conversation.getId();
                    System.out.println("system conversation已存在，ID: " + conversationId);
                }
            }
            
            server.club.PublicClub systemClub = new server.club.PublicClub("system", "system", messageRouter);
            systemClub.setConversationId(conversationId);
            messageRouter.addClub(systemClub);
            System.out.println("已将system房间添加到消息路由器，ID: system, conversation_id: " + conversationId);
        } catch (Exception e) {
            System.err.println("设置system会话时出错: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (conn != null) {
                databaseManager.closeConnection(conn);
            }
        }
    }
    
    /**
     * 从数据库加载所有会话
     * @throws Exception 数据库操作异常
     */
    private void loadAllRooms() throws Exception {
        System.out.println("正在从数据库加载所有会话...");
        Connection conn = null;
        try {
            conn = databaseManager.getConnection();
            ConversationDAO conversationDAO = new ConversationDAO();
            ClubDAO clubDAO = new ClubDAO(messageRouter);
            
            // 加载所有社团会话
            for (Conversation conversation : conversationDAO.getAllClubConversations(conn)) {
                // 只添加非system会话（system会话已单独处理）
                if (!"system".equals(conversation.getName())) {
                    // 从club表获取真实的社团名称，而不是使用conversation表中的名称
                    int clubId = conversation.getClubId();
                    String clubName = conversation.getName();
                    
                    if (clubId > 0) {
                        String sql = "SELECT name FROM club WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setInt(1, clubId);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                if (rs.next()) {
                                    clubName = rs.getString("name");
                                }
                            }
                        }
                    }
                    
                    System.out.println("已加载社团会话: " + clubName + " (ID: " + conversation.getId() + ", club_id: " + clubId + ")");
                    
                    server.club.PublicClub club = new server.club.PublicClub(
                        clubName, 
                        String.valueOf(conversation.getId()), 
                        messageRouter
                    );
                    club.setConversationId(conversation.getId());
                    messageRouter.addClub(club);
                }
            }
            
            System.out.println("所有会话加载完成");
        } catch (Exception e) {
            System.err.println("加载会话时出错: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (conn != null) {
                databaseManager.closeConnection(conn);
            }
        }
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        if (!isRunning) {
            System.err.println("服务器未初始化");
            return;
        }
        
        System.out.println("正在启动聊天服务器...");
        
        // 启动服务器监听器线程
        Thread serverThread = new Thread(serverListener);
        serverThread.setName("ServerListener");
        serverThread.start();
        
        // 启动WebSocket服务器
        webSocketServer.start();
        
        System.out.println("聊天服务器已成功启动");
        System.out.println("输入 'stop' 或 'quit' 停止服务器");
        
        // 启动命令行交互
        handleCommandLine();
    }
    
    /**
     * 处理命令行交互
     */
    private void handleCommandLine() {
        Scanner scanner = new Scanner(System.in);
        
        while (isRunning) {
            String command = scanner.nextLine().trim().toLowerCase();
            
            switch (command) {
                case "stop":
                case "quit":
                    stop();
                    break;
                case "status":
                    printStatus();
                    break;
                case "help":
                    printHelp();
                    break;
                default:
                    if (!command.isEmpty()) {
                        System.out.println("未知命令: " + command);
                        printHelp();
                    }
                    break;
            }
        }
        
        scanner.close();
    }
    
    /**
     * 打印服务器状态
     */
    private void printStatus() {
        System.out.println("=== 服务器状态 ===");
        System.out.println("运行状态: " + (isRunning ? "运行中" : "已停止"));
        System.out.println("TCP服务器端口: " + (serverListener != null ? serverListener.getPort() : "未设置"));
        System.out.println("WebSocket服务器端口: " + (webSocketServer != null ? webSocketServer.getPort() : "未设置"));
        System.out.println("活动会话数: " + (messageRouter != null ? messageRouter.getActiveSessionCount() : 0));
        System.out.println("社团数: " + (messageRouter != null ? messageRouter.getClubCount() : 0));
        System.out.println("================");
    }
    
    /**
     * 打印帮助信息
     */
    private void printHelp() {
        System.out.println("=== 命令帮助 ===");
        System.out.println("status - 查看服务器状态");
        System.out.println("stop/quit - 停止服务器");
        System.out.println("help - 显示此帮助信息");
        System.out.println("================");
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        System.out.println("正在停止聊天服务器...");
        
        isRunning = false;
        
        // 停止服务器监听器
        if (serverListener != null) {
            serverListener.stop();
        }
        
        // 停止WebSocket服务器
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException e) {
                System.err.println("停止WebSocket服务器时被中断: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("聊天服务器已成功停止");
    }
    
    /**
     * 主方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        
        try {
            int port = 0;
            
            // 解析命令行参数
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("无效的端口号: " + args[0]);
                    args = null; // 使用交互模式
                }
            }
            
            // 如果没有提供端口参数，则使用交互模式
            if (args == null || args.length == 0) {
                Scanner scanner = new Scanner(System.in);
                System.out.print("请输入服务器端口: ");
                port = scanner.nextInt();
                scanner.nextLine(); // 读取换行符
            }
            
            // 初始化并启动服务器
            chatServer.initialize(port);
            chatServer.start();
            
        } catch (Exception e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
            chatServer.stop();
            System.exit(1);
        }
    }
}
