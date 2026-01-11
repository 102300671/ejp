import java.util.Scanner;
import java.io.IOException;
import server.network.socket.*;
import server.network.router.MessageRouter;
import server.sql.DatabaseManager;

public class ChatServer {
    private ServerListener serverListener;
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
        
        // 初始化数据库管理器
        this.databaseManager = new DatabaseManager();
        System.out.println("数据库管理器已初始化");
        
        // 初始化消息路由器
        this.messageRouter = new MessageRouter();
        
        // 初始化服务器监听器
        this.serverListener = new ServerListener(port);
        
        this.isRunning = true;
        System.out.println("聊天服务器初始化完成");
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
        System.out.println("服务器端口: " + (serverListener != null ? serverListener.getPort() : "未设置"));
        System.out.println("活动会话数: " + (messageRouter != null ? messageRouter.getActiveSessionCount() : 0));
        System.out.println("房间数: " + (messageRouter != null ? messageRouter.getRoomCount() : 0));
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
