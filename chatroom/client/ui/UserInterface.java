package client.ui;
import client.message.Message;
import client.message.MessageType;
import client.network.ClientConnection;
import java.util.Scanner;

public class UserInterface {
    private final Scanner scanner;
    private final ClientConnection clientConnection;
    private String username;
    private String currentRoom;
    private String currentRoomType;
    private volatile boolean isRunning;
    
    /**
     * 构造用户界面对象
     * @param clientConnection 客户端连接对象
     */
    public UserInterface(ClientConnection clientConnection) {
        this.scanner = new Scanner(System.in);
        this.clientConnection = clientConnection;
        this.isRunning = false;
        this.currentRoom = "system";
        this.currentRoomType = "PUBLIC";
        
        // 设置消息接收回调
        clientConnection.setMessageReceivedCallback(new ClientConnection.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(Message message) {
                displayMessage(message);
            }
            
            @Override
            public void onConnectionClosed(String reason) {
                displaySystemMessage("连接已关闭: " + reason);
                stop();
            }
        });
    }
    
    /**
     * 启动用户界面
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        
        // 获取用户名
        username = getUsername();
        
        // 显示欢迎信息
        displayWelcomeMessage();
        
        // 自动显示帮助信息
        processCommand("/help");
        
        // 发送加入系统消息
        sendSystemMessage("JOIN", "system", username + " 加入了聊天室");
        
        // 启动输入处理线程
        Thread inputThread = new Thread(new InputHandler());
        inputThread.setName("InputHandler");
        inputThread.start();
    }
    
    /**
     * 停止用户界面
     */
    public void stop() {
        isRunning = false;
        
        // 发送离开系统消息
        if (username != null && clientConnection.isConnected()) {
            sendSystemMessage("LEAVE", "system", username + " 离开了聊天室");
        }
        
        scanner.close();
        System.out.println("感谢使用聊天客户端，再见！");
    }
    
    /**
     * 获取用户名
     * @return 用户名
     */
    private String getUsername() {
        System.out.print("请输入用户名: ");
        String name = scanner.nextLine().trim();
        
        while (name.isEmpty()) {
            System.out.print("用户名不能为空，请重新输入: ");
            name = scanner.nextLine().trim();
        }
        
        return name;
    }
    
    /**
     * 显示欢迎信息
     */
    private void displayWelcomeMessage() {
        System.out.println("============================================");
        System.out.println("欢迎使用聊天客户端，" + username + "!");
        System.out.println("当前房间: " + currentRoom + " (" + currentRoomType + ")");
        System.out.println("使用 /help 查看可用命令");
        System.out.println("============================================");
    }
    
    /**
     * 显示系统消息
     * @param message 系统消息内容
     */
    private void displaySystemMessage(String message) {
        System.out.println("[系统] " + message);
    }
    
    /**
     * 显示接收到的消息
     * @param message 消息对象
     */
    private void displayMessage(Message message) {
        if (message == null) {
            return;
        }
        
        switch (message.getType()) {
            case TEXT:
                String content = message.getContent();
                // 处理私人消息，去掉房间信息
                if (content.startsWith("[room:")) {
                    int roomEnd = content.indexOf("]");
                    content = content.substring(roomEnd + 1);
                    System.out.println("[私聊] " + message.getFrom() + ": " + content + " (" + message.getTime() + ")");
                } else {
                    // 房间消息，简化显示
                    System.out.println("[" + message.getFrom() + "]: " + content + " (" + message.getTime() + ")");
                }
                break;
            case SYSTEM:
                System.out.println("[系统] " + message.getContent());
                break;
            case JOIN:
                System.out.println("[系统] " + message.getFrom() + " 加入了房间 " + message.getTo());
                break;
            case LEAVE:
                System.out.println("[系统] " + message.getFrom() + " 离开了房间 " + message.getTo());
                break;
            default:
                // 隐藏未知消息的详细内容
                System.out.println("[系统] 收到未知类型消息");
                break;
        }
    }
    
    /**
     * 发送文本消息
     * @param to 接收者（用户或房间）
     * @param content 消息内容
     */
    private void sendTextMessage(String to, String content) {
        Message message = new Message(MessageType.TEXT, username, to, content);
        clientConnection.sendMessage(message);
    }
    
    /**
     * 发送系统消息
     * @param type 系统消息类型
     * @param to 接收者
     * @param content 消息内容
     */
    private void sendSystemMessage(String type, String to, String content) {
        MessageType messageType;
        
        switch (type.toUpperCase()) {
            case "JOIN":
                messageType = MessageType.JOIN;
                break;
            case "LEAVE":
                messageType = MessageType.LEAVE;
                break;
            case "CREATE":
                messageType = MessageType.CREATE_ROOM;
                break;
            case "EXIT":
                messageType = MessageType.EXIT_ROOM;
                break;
            case "LIST_ROOMS":
                messageType = MessageType.LIST_ROOMS;
                break;
            default:
                messageType = MessageType.SYSTEM;
                break;
        }
        
        Message message = new Message(messageType, username, to, content);
        clientConnection.sendMessage(message);
    }
    
    /**
     * 显示命令帮助
     */
    private void displayHelp() {
        System.out.println("============================================");
        System.out.println("可用命令:");
        System.out.println("/create <room> [type]  - 创建房间，类型默认为PUBLIC");
        System.out.println("/join <room>        - 加入指定房间");
        System.out.println("/leave <room>       - 离开指定房间");
        System.out.println("/exit <room>        - 退出指定房间并删除记录");
        System.out.println("/list               - 查看用户所在的所有房间");
        System.out.println("/msg <user> <message>  - 发送私人消息");
        System.out.println("/help               - 显示此帮助信息");
        System.out.println("/quit               - 退出客户端");
        System.out.println("直接输入消息        - 发送到当前房间");
        System.out.println("============================================");
    }
    
    /**
     * 处理用户输入的命令
     * @param command 命令内容
     */
    private void processCommand(String command) {
        String[] parts = command.split(" ", 3);
        
        if (parts.length == 0) {
            return;
        }
        
        switch (parts[0].toLowerCase()) {
            case "/create":
                if (parts.length >= 2) {
                    String room = parts[1];
                    String type = parts.length >= 3 ? parts[2].toUpperCase() : "PUBLIC";
                    // 发送创建房间消息
                    sendSystemMessage("CREATE", room, type);
                    displaySystemMessage("正在创建房间: " + room + " (类型: " + type + ")");
                } else {
                    displaySystemMessage("请指定房间名称和类型(可选): /create <room> [type]");
                }
                break;
                
            case "/join":
                if (parts.length >= 2) {
                    String room = parts[1];
                    currentRoom = room;
                    // 默认类型为PUBLIC，实际类型会从服务器获取
                    currentRoomType = "PUBLIC";
                    sendSystemMessage("JOIN", room, username + " 加入了房间");
                    displaySystemMessage("已加入房间: " + room + " (类型: " + currentRoomType + ")");
                } else {
                    displaySystemMessage("请指定房间名称: /join <room>");
                }
                break;
                
            case "/leave":
                if (parts.length >= 2) {
                    String room = parts[1];
                    if (room.equals(currentRoom)) {
                        currentRoom = "system";
                    }
                    // /leave 只是离开房间，不删除room_member记录
                    displaySystemMessage("已离开房间: " + room);
                    // 如果离开的不是system房间，自动回到system房间
                    if (!room.equals("system")) {
                        currentRoom = "system";
                        displaySystemMessage("当前房间已切换到: " + currentRoom);
                    }
                } else {
                    displaySystemMessage("请指定房间名称: /leave <room>");
                }
                break;
                
            case "/exit":
                if (parts.length >= 2) {
                    String room = parts[1];
                    if (room.equals(currentRoom)) {
                        currentRoom = "system";
                    }
                    // /exit 退出房间，同时删除room_member记录
                    sendSystemMessage("EXIT", room, username + " 退出了房间");
                    displaySystemMessage("已退出房间: " + room);
                    // 如果退出的不是system房间，自动回到system房间
                    if (!room.equals("system")) {
                        displaySystemMessage("当前房间已切换到: " + currentRoom);
                    }
                } else {
                    displaySystemMessage("请指定房间名称: /exit <room>");
                }
                break;
                
            case "/list":
                // 发送列表房间请求
                sendSystemMessage("LIST_ROOMS", "", "");
                displaySystemMessage("正在获取房间列表...");
                break;
                
            case "/msg":
                if (parts.length >= 3) {
                    String user = parts[1];
                    String content = parts[2];
                    
                    // 检查当前房间
                    if ("system".equals(currentRoom)) {
                        displaySystemMessage("在system房间中禁止发送私人消息");
                        break;
                    }
                    
                    // 在私人消息内容前添加当前房间信息，格式："[room:房间名]消息内容"
                    String formattedContent = "[room:" + currentRoom + "]" + content;
                    sendTextMessage(user, formattedContent);
                } else {
                    displaySystemMessage("请指定接收者和消息内容: /msg <user> <message>");
                }
                break;
                
            case "/help":
                displayHelp();
                break;
                
            case "/show":
                // 显示当前房间和类型
                displaySystemMessage("当前房间: " + currentRoom + " (" + currentRoomType + ")");
                break;
                
            case "/quit":
                stop();
                break;
                
            default:
                displaySystemMessage("未知命令: " + parts[0]);
                displaySystemMessage("输入 /help 查看可用命令");
                break;
        }
    }
    
    /**
     * 内部类，处理用户输入
     */
    private class InputHandler implements Runnable {
        @Override
        public void run() {
            while (isRunning) {
                try {
                    System.out.print(username + "@" + currentRoom + "(" + currentRoomType + ")> ");
                    String input = scanner.nextLine().trim();
                    
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    // 处理命令
                    if (input.startsWith("/")) {
                        processCommand(input);
                    } else {
                        // 直接输入消息，发送到当前房间
                        sendTextMessage(currentRoom, input);
                    }
                    
                } catch (Exception e) {
                    displaySystemMessage("处理输入时发生错误: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}