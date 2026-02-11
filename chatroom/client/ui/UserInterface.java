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
     * @param username 已认证的用户名
     */
    public UserInterface(ClientConnection clientConnection, String username) {
        this.scanner = new Scanner(System.in);
        this.clientConnection = clientConnection;
        this.username = username;
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
            case HISTORY_RESPONSE:
                displayHistoryResponse(message);
                break;
            case LIST_ROOM_USERS:
                displayRoomUsers(message);
                break;
            case FRIEND_REQUEST:
                System.out.println("[好友请求] " + message.getFrom() + " 请求添加你为好友");
                break;
            case FRIEND_REQUEST_RESPONSE:
                System.out.println("[好友] " + message.getContent());
                break;
            case FRIEND_LIST:
                displayFriendList(message);
                break;
            case ALL_FRIEND_REQUESTS:
                displayFriendRequests(message);
                break;
            case USERS_SEARCH_RESULT:
                displayUserSearchResult(message);
                break;
            case ROOMS_SEARCH_RESULT:
                displayRoomSearchResult(message);
                break;
            case ROOM_JOIN_REQUEST:
                System.out.println("[房间请求] " + message.getFrom() + " 申请加入房间 " + message.getTo());
                break;
            case ROOM_JOIN_RESPONSE:
                System.out.println("[房间] " + message.getContent());
                break;
            case USER_STATS_RESPONSE:
                displayUserStats(message);
                break;
            case PRIVATE_CHAT:
                System.out.println("[私聊] " + message.getFrom() + ": " + message.getContent() + " (" + message.getTime() + ")");
                break;
            case DELETE_MESSAGE:
                System.out.println("[系统] 消息已删除: " + message.getContent());
                break;
            case RECALL_MESSAGE:
                System.out.println("[系统] " + message.getFrom() + " 撤回了一条消息");
                break;
            default:
                // 隐藏未知消息的详细内容
                System.out.println("[系统] 收到消息: " + message.getType() + " from " + message.getFrom());
                break;
        }
    }
    
    /**
     * 显示消息历史响应
     */
    private void displayHistoryResponse(Message message) {
        System.out.println("============================================");
        System.out.println("消息历史 (" + message.getTo() + "):");
        System.out.println("============================================");
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            String[] messages = content.split("\\|\\|");
            for (String msg : messages) {
                if (!msg.isEmpty()) {
                    System.out.println(msg);
                }
            }
        } else {
            System.out.println("暂无消息历史");
        }
        System.out.println("============================================");
    }
    
    /**
     * 显示房间成员列表
     */
    private void displayRoomUsers(Message message) {
        System.out.println("============================================");
        System.out.println("房间成员 (" + message.getTo() + "):");
        System.out.println("============================================");
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            String[] users = content.split("\\|\\|");
            for (String user : users) {
                if (!user.isEmpty()) {
                    System.out.println("  - " + user);
                }
            }
        } else {
            System.out.println("暂无成员");
        }
        System.out.println("============================================");
    }
    
    /**
     * 显示好友列表
     */
    private void displayFriendList(Message message) {
        System.out.println("============================================");
        System.out.println("好友列表:");
        System.out.println("============================================");
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            String[] friends = content.split("\\|\\|");
            if (friends.length > 0) {
                for (String friend : friends) {
                    if (!friend.isEmpty()) {
                        System.out.println("  - " + friend);
                    }
                }
            } else {
                System.out.println("暂无好友");
            }
        } else {
            System.out.println("暂无好友");
        }
        System.out.println("============================================");
    }
    
    /**
     * 显示好友请求
     */
    private void displayFriendRequests(Message message) {
        System.out.println("============================================");
        System.out.println("好友请求:");
        System.out.println("============================================");
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            String[] requests = content.split("\\|\\|");
            if (requests.length > 0) {
                for (String request : requests) {
                    if (!request.isEmpty()) {
                        System.out.println("  - " + request);
                    }
                }
            } else {
                System.out.println("暂无好友请求");
            }
        } else {
            System.out.println("暂无好友请求");
        }
        System.out.println("============================================");
    }
    
    /**
     * 显示用户搜索结果
     */
    private void displayUserSearchResult(Message message) {
        System.out.println("============================================");
        System.out.println("用户搜索结果:");
        System.out.println("============================================");
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            String[] users = content.split("\\|\\|");
            if (users.length > 0) {
                for (String user : users) {
                    if (!user.isEmpty()) {
                        System.out.println("  - " + user);
                    }
                }
            } else {
                System.out.println("未找到匹配的用户");
            }
        } else {
            System.out.println("未找到匹配的用户");
        }
        System.out.println("============================================");
    }
    
    /**
     * 显示房间搜索结果
     */
    private void displayRoomSearchResult(Message message) {
        System.out.println("============================================");
        System.out.println("房间搜索结果:");
        System.out.println("============================================");
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            String[] rooms = content.split("\\|\\|");
            if (rooms.length > 0) {
                for (String room : rooms) {
                    if (!room.isEmpty()) {
                        System.out.println("  - " + room);
                    }
                }
            } else {
                System.out.println("未找到匹配的房间");
            }
        } else {
            System.out.println("未找到匹配的房间");
        }
        System.out.println("============================================");
    }
    
    /**
     * 显示用户统计信息
     */
    private void displayUserStats(Message message) {
        System.out.println("============================================");
        System.out.println("用户统计信息:");
        System.out.println("============================================");
        System.out.println(message.getContent());
        System.out.println("============================================");
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
        System.out.println("房间管理:");
        System.out.println("  /create <room> [type]  - 创建房间，类型默认为PUBLIC");
        System.out.println("  /join <room>          - 加入指定房间");
        System.out.println("  /leave <room>         - 离开指定房间");
        System.out.println("  /exit <room>          - 退出指定房间并删除记录");
        System.out.println("  /list                 - 查看用户所在的所有房间");
        System.out.println("  /members <room>       - 查看房间成员列表");
        System.out.println("  /searchrooms <keyword> - 搜索房间");
        System.out.println("  /requestjoin <room>   - 申请加入房间");
        System.out.println("消息管理:");
        System.out.println("  /history [limit]       - 查看当前房间消息历史");
        System.out.println("  /pm <user> <message>  - 发送私人消息");
        System.out.println("  /recall <messageId>   - 撤回消息");
        System.out.println("好友系统:");
        System.out.println("  /addfriend <username> - 发送好友请求");
        System.out.println("  /accept <username>    - 接受好友请求");
        System.out.println("  /reject <username>    - 拒绝好友请求");
        System.out.println("  /friends              - 查看好友列表");
        System.out.println("  /friendrequests       - 查看好友请求");
        System.out.println("  /removefriend <username> - 删除好友");
        System.out.println("  /chat <username>       - 开始与好友聊天");
        System.out.println("用户功能:");
        System.out.println("  /stats                - 查看用户统计信息");
        System.out.println("  /searchusers <keyword> - 搜索用户");
        System.out.println("  /show                 - 显示当前房间和类型");
        System.out.println("其他:");
        System.out.println("  /help                 - 显示此帮助信息");
        System.out.println("  /quit                 - 退出客户端");
        System.out.println("直接输入消息            - 发送到当前房间");
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
                    displaySystemMessage("已离开房间: " + room);
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
                    sendSystemMessage("EXIT", room, username + " 退出了房间");
                    displaySystemMessage("已退出房间: " + room);
                    if (!room.equals("system")) {
                        displaySystemMessage("当前房间已切换到: " + currentRoom);
                    }
                } else {
                    displaySystemMessage("请指定房间名称: /exit <room>");
                }
                break;
                
            case "/list":
                sendSystemMessage("LIST_ROOMS", "", "");
                displaySystemMessage("正在获取房间列表...");
                break;
                
            case "/members":
                if (parts.length >= 2) {
                    String room = parts[1];
                    sendSystemMessage("LIST_ROOM_USERS", room, "");
                    displaySystemMessage("正在获取房间成员列表...");
                } else {
                    sendSystemMessage("LIST_ROOM_USERS", currentRoom, "");
                    displaySystemMessage("正在获取当前房间成员列表...");
                }
                break;
                
            case "/searchrooms":
                if (parts.length >= 2) {
                    String keyword = parts[1];
                    Message searchMsg = new Message(MessageType.SEARCH_ROOMS, username, "server", keyword);
                    clientConnection.sendMessage(searchMsg);
                    displaySystemMessage("正在搜索房间: " + keyword);
                } else {
                    displaySystemMessage("请指定搜索关键词: /searchrooms <keyword>");
                }
                break;
                
            case "/requestjoin":
                if (parts.length >= 2) {
                    String room = parts[1];
                    Message requestMsg = new Message(MessageType.REQUEST_ROOM_JOIN, username, room, "");
                    clientConnection.sendMessage(requestMsg);
                    displaySystemMessage("已发送加入房间请求: " + room);
                } else {
                    displaySystemMessage("请指定房间名称: /requestjoin <room>");
                }
                break;
                
            case "/history":
                int limit = 50;
                if (parts.length >= 2) {
                    try {
                        limit = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        displaySystemMessage("无效的限制数量，使用默认值50");
                    }
                }
                Message historyMsg = new Message(MessageType.REQUEST_HISTORY, username, currentRoom, String.valueOf(limit));
                clientConnection.sendMessage(historyMsg);
                displaySystemMessage("正在获取消息历史...");
                break;
                
            case "/pm":
            case "/msg":
                if (parts.length >= 3) {
                    String user = parts[1];
                    String content = parts[2];
                    Message pmMsg = new Message(MessageType.PRIVATE_CHAT, username, user, content);
                    clientConnection.sendMessage(pmMsg);
                    System.out.println("[私聊] 你 -> " + user + ": " + content);
                } else {
                    displaySystemMessage("请指定接收者和消息内容: /pm <user> <message>");
                }
                break;
                
            case "/recall":
                if (parts.length >= 2) {
                    String messageId = parts[1];
                    Message recallMsg = new Message(MessageType.RECALL_MESSAGE, username, currentRoom, messageId);
                    clientConnection.sendMessage(recallMsg);
                    displaySystemMessage("正在撤回消息: " + messageId);
                } else {
                    displaySystemMessage("请指定消息ID: /recall <messageId>");
                }
                break;
                
            case "/addfriend":
                if (parts.length >= 2) {
                    String friendUser = parts[1];
                    Message addFriendMsg = new Message(MessageType.FRIEND_REQUEST, username, friendUser, "");
                    clientConnection.sendMessage(addFriendMsg);
                    displaySystemMessage("已发送好友请求给: " + friendUser);
                } else {
                    displaySystemMessage("请指定用户名: /addfriend <username>");
                }
                break;
                
            case "/accept":
                if (parts.length >= 2) {
                    String friendUser = parts[1];
                    Message acceptMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, username, friendUser, "ACCEPT");
                    clientConnection.sendMessage(acceptMsg);
                    displaySystemMessage("已接受好友请求: " + friendUser);
                } else {
                    displaySystemMessage("请指定用户名: /accept <username>");
                }
                break;
                
            case "/reject":
                if (parts.length >= 2) {
                    String friendUser = parts[1];
                    Message rejectMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, username, friendUser, "REJECT");
                    clientConnection.sendMessage(rejectMsg);
                    displaySystemMessage("已拒绝好友请求: " + friendUser);
                } else {
                    displaySystemMessage("请指定用户名: /reject <username>");
                }
                break;
                
            case "/friends":
                Message friendListMsg = new Message(MessageType.REQUEST_FRIEND_LIST, username, "server", "");
                clientConnection.sendMessage(friendListMsg);
                displaySystemMessage("正在获取好友列表...");
                break;
                
            case "/friendrequests":
                Message friendRequestsMsg = new Message(MessageType.REQUEST_ALL_FRIEND_REQUESTS, username, "server", "");
                clientConnection.sendMessage(friendRequestsMsg);
                displaySystemMessage("正在获取好友请求...");
                break;
                
            case "/removefriend":
                if (parts.length >= 2) {
                    String friendUser = parts[1];
                    Message removeMsg = new Message(MessageType.FRIEND_REQUEST_RESPONSE, username, friendUser, "REMOVE");
                    clientConnection.sendMessage(removeMsg);
                    displaySystemMessage("已删除好友: " + friendUser);
                } else {
                    displaySystemMessage("请指定用户名: /removefriend <username>");
                }
                break;
                
            case "/chat":
                if (parts.length >= 2) {
                    String chatUser = parts[1];
                    currentRoom = chatUser;
                    currentRoomType = "PRIVATE";
                    displaySystemMessage("已切换到私聊模式，正在与 " + chatUser + " 聊天");
                    displaySystemMessage("使用 /pm " + chatUser + " <message> 发送消息");
                } else {
                    displaySystemMessage("请指定用户名: /chat <username>");
                }
                break;
                
            case "/stats":
                Message statsMsg = new Message(MessageType.REQUEST_USER_STATS, username, "server", "");
                clientConnection.sendMessage(statsMsg);
                displaySystemMessage("正在获取用户统计信息...");
                break;
                
            case "/searchusers":
                if (parts.length >= 2) {
                    String keyword = parts[1];
                    Message searchUsersMsg = new Message(MessageType.SEARCH_USERS, username, "server", keyword);
                    clientConnection.sendMessage(searchUsersMsg);
                    displaySystemMessage("正在搜索用户: " + keyword);
                } else {
                    displaySystemMessage("请指定搜索关键词: /searchusers <keyword>");
                }
                break;
                
            case "/show":
                displaySystemMessage("当前房间: " + currentRoom + " (" + currentRoomType + ")");
                break;
                
            case "/help":
                displayHelp();
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