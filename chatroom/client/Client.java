package client;
import client.network.ClientConnection;
import client.ui.UserInterface;
import java.io.IOException;
import java.util.Scanner;

public class Client {
    private ClientConnection clientConnection;
    private UserInterface userInterface;
    private volatile boolean isRunning;
    
    /**
     * 构造客户端对象
     */
    public Client() {
        this.isRunning = false;
    }
    
    /**
     * 初始化客户端
     * @param serverAddress 服务器地址
     * @param serverPort 服务器端口
     * @throws IOException 初始化过程中可能发生的IO异常
     */
    public void initialize(String serverAddress, int serverPort) throws IOException {
        System.out.println("正在初始化聊天客户端...");
        
        // 初始化客户端连接
        this.clientConnection = new ClientConnection(serverAddress, serverPort);
        
        // 连接到服务器
        this.clientConnection.connect();
        
        this.isRunning = true;
        
        System.out.println("聊天客户端初始化完成");
    }
    
    /**
     * 启动客户端
     */
    public void start() {
        if (!isRunning) {
            System.err.println("客户端未初始化");
            return;
        }
        
        System.out.println("正在启动聊天客户端...");
        
        // 启动消息接收线程
        Thread messageThread = new Thread(clientConnection);
        messageThread.setName("MessageReceiver");
        messageThread.start();
        
        // 进行用户认证
        if (authenticate()) {
            // 认证成功，初始化用户界面
            this.userInterface = new UserInterface(clientConnection);
            
            // 启动用户界面
            userInterface.start();
            
            System.out.println("聊天客户端已成功启动");
        } else {
            // 认证失败，关闭客户端
            System.err.println("认证失败，无法启动聊天客户端");
            stop();
        }
    }
    
    /**
     * 进行用户认证
     * @return 认证是否成功
     */
    private boolean authenticate() {
        // 初始化认证界面
        client.ui.AuthenticationInterface authInterface = new client.ui.AuthenticationInterface(clientConnection);
        
        // 开始认证流程
        return authInterface.authenticate();
    }
    
    /**
     * 停止客户端
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        System.out.println("正在停止聊天客户端...");
        
        this.isRunning = false;
        
        // 停止用户界面
        if (userInterface != null) {
            userInterface.stop();
        }
        
        // 关闭客户端连接
        if (clientConnection != null) {
            clientConnection.close();
        }
        
        System.out.println("聊天客户端已成功停止");
    }
    
    /**
     * 主方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Client client = new Client();
        
        try {
            String serverAddress = "localhost";
            int serverPort = 8080;
            
            // 解析命令行参数
            if (args.length >= 1) {
                serverAddress = args[0];
            }
            
            if (args.length >= 2) {
                try {
                    serverPort = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.err.println("无效的端口号: " + args[1]);
                    args = null; // 使用交互模式
                }
            }
            
            // 如果没有提供足够的参数，则使用交互模式
            if (args == null || args.length < 2) {
                Scanner scanner = new Scanner(System.in);
                
                System.out.print("请输入服务器地址 (默认: localhost): ");
                String inputAddress = scanner.nextLine().trim();
                if (!inputAddress.isEmpty()) {
                    serverAddress = inputAddress;
                }
                
                System.out.print("请输入服务器端口 (默认: 8080): ");
                String inputPort = scanner.nextLine().trim();
                if (!inputPort.isEmpty()) {
                    try {
                        serverPort = Integer.parseInt(inputPort);
                    } catch (NumberFormatException e) {
                        System.err.println("无效的端口号，使用默认端口 8080");
                        serverPort = 8080;
                    }
                }
            }
            
            // 初始化并启动客户端
            client.initialize(serverAddress, serverPort);
            client.start();
            
        } catch (IOException e) {
            System.err.println("客户端启动失败: " + e.getMessage());
            e.printStackTrace();
            client.stop();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("发生未知错误: " + e.getMessage());
            e.printStackTrace();
            client.stop();
            System.exit(1);
        }
    }
}