package client.ui;
import client.message.Message;
import client.message.MessageType;
import client.network.ClientConnection;
import client.util.UUIDCache;
import java.util.Scanner;

public class AuthenticationInterface {
    private final Scanner scanner;
    private final ClientConnection clientConnection;
    private final UUIDCache uuidCache;
    private String username;
    private String uuid;
    private volatile boolean isAuthenticated;
    private volatile boolean isAuthenticationComplete;
    
    /**
     * 构造认证界面对象
     * @param clientConnection 客户端连接对象
     */
    public AuthenticationInterface(ClientConnection clientConnection) {
        this.scanner = new Scanner(System.in);
        this.clientConnection = clientConnection;
        this.uuidCache = new UUIDCache();
        this.isAuthenticated = false;
        this.isAuthenticationComplete = false;
        
        // 设置消息接收回调
        clientConnection.setMessageReceivedCallback(new ClientConnection.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(Message message) {
                handleAuthenticationMessage(message);
            }
            
            @Override
            public void onConnectionClosed(String reason) {
                System.out.println("连接已关闭: " + reason);
                isAuthenticationComplete = true;
            }
        });
    }
    
    /**
     * 开始认证流程
     * @return 认证是否成功
     */
    public boolean authenticate() {
        System.out.println("============================================");
        System.out.println("欢迎使用聊天客户端！");
        System.out.println("============================================");
        
        while (!isAuthenticationComplete) {
            showAuthenticationMenu();
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    register();
                    break;
                case "2":
                    login();
                    break;
                case "3":
                    System.out.println("已取消认证，退出客户端...");
                    isAuthenticationComplete = true;
                    break;
                default:
                    System.out.println("无效的选择，请重新输入");
                    break;
            }
        }
        
        return isAuthenticated;
    }
    
    /**
     * 显示认证菜单
     */
    private void showAuthenticationMenu() {
        System.out.println("请选择认证方式：");
        System.out.println("1. 注册新用户");
        System.out.println("2. 登录现有用户");
        System.out.println("3. 退出");
        System.out.print("请输入选择: ");
    }
    
    /**
     * 注册新用户
     */
    private void register() {
        System.out.println("============================================");
        System.out.println("注册新用户");
        System.out.println("============================================");
        
        // 获取用户名
        System.out.print("请输入用户名: ");
        String username = scanner.nextLine().trim();
        
        while (username.isEmpty()) {
            System.out.print("用户名不能为空，请重新输入: ");
            username = scanner.nextLine().trim();
        }
        
        // 获取密码
        System.out.print("请输入密码: ");
        String password = scanner.nextLine().trim();
        
        while (password.isEmpty()) {
            System.out.print("密码不能为空，请重新输入: ");
            password = scanner.nextLine().trim();
        }
        
        // 确认密码
        System.out.print("请确认密码: ");
        String confirmPassword = scanner.nextLine().trim();
        
        while (!password.equals(confirmPassword)) {
            System.out.print("两次输入的密码不一致，请重新输入确认密码: ");
            confirmPassword = scanner.nextLine().trim();
        }
        
        // 发送注册请求
        System.out.println("正在注册...");
        String registerContent = username + ":" + password;
        Message registerMessage = new Message(MessageType.REGISTER, username, "server", registerContent);
        clientConnection.sendMessage(registerMessage);
        
        // 等待认证结果
        waitForAuthentication();
    }
    
    /**
     * 登录现有用户
     */
    private void login() {
        System.out.println("============================================");
        System.out.println("登录现有用户");
        System.out.println("============================================");
        
        // 获取用户名
        System.out.print("请输入用户名: ");
        String username = scanner.nextLine().trim();
        
        while (username.isEmpty()) {
            System.out.print("用户名不能为空，请重新输入: ");
            username = scanner.nextLine().trim();
        }
        
        // 检查是否有缓存的UUID
        String cachedUUID = uuidCache.getUUID(username);
        if (cachedUUID != null) {
            System.out.println("检测到缓存的UUID，是否使用UUID快速登录？(y/n)");
            String choice = scanner.nextLine().trim().toLowerCase();
            
            if ("y".equals(choice)) {
                // 使用UUID认证
                System.out.println("正在使用UUID登录...");
                Message uuidAuthMessage = new Message(MessageType.UUID_AUTH, username, "server", cachedUUID);
                clientConnection.sendMessage(uuidAuthMessage);
                
                // 等待认证结果
                waitForAuthentication();
                return;
            }
        }
        
        // 使用密码登录
        System.out.print("请输入密码: ");
        String password = scanner.nextLine().trim();
        
        while (password.isEmpty()) {
            System.out.print("密码不能为空，请重新输入: ");
            password = scanner.nextLine().trim();
        }
        
        // 发送登录请求
        System.out.println("正在登录...");
        String loginContent = username + ":" + password;
        Message loginMessage = new Message(MessageType.LOGIN, username, "server", loginContent);
        clientConnection.sendMessage(loginMessage);
        
        // 等待认证结果
        waitForAuthentication();
    }
    
    /**
     * 等待认证结果
     */
    private void waitForAuthentication() {
        // 最多等待10秒
        long startTime = System.currentTimeMillis();
        while (!isAuthenticationComplete && System.currentTimeMillis() - startTime < 10000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (!isAuthenticationComplete) {
            System.out.println("认证超时，请重试");
            isAuthenticationComplete = true;
        }
    }
    
    /**
     * 处理认证消息
     * @param message 认证消息
     */
    private void handleAuthenticationMessage(Message message) {
        if (message == null) {
            return;
        }
        
        switch (message.getType()) {
            case AUTH_SUCCESS:
                System.out.println("认证成功！");
                this.username = message.getTo();
                this.uuid = message.getContent();
                this.isAuthenticated = true;
                this.isAuthenticationComplete = true;
                
                // 保存UUID到缓存
                uuidCache.saveUUID(username, uuid);
                break;
                
            case AUTH_FAILURE:
                System.out.println("认证失败: " + message.getContent());
                this.isAuthenticationComplete = true;
                break;
                
            case UUID_AUTH_SUCCESS:
                System.out.println("UUID认证成功！");
                this.username = message.getTo();
                this.uuid = message.getContent();
                this.isAuthenticated = true;
                this.isAuthenticationComplete = true;
                break;
                
            case UUID_AUTH_FAILURE:
                System.out.println("UUID认证失败: " + message.getContent());
                // UUID认证失败，重新登录
                this.isAuthenticationComplete = true;
                break;
                
            default:
                System.out.println("收到未知认证消息: " + message);
                break;
        }
    }
    
    /**
     * 获取认证后的用户名
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * 获取认证后的UUID
     * @return UUID
     */
    public String getUUID() {
        return uuid;
    }
    
    /**
     * 认证是否成功
     * @return 认证是否成功
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
}