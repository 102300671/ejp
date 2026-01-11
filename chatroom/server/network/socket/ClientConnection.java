package server.network.socket;
import java.io.*;
import java.net.*;
import java.sql.*;
import server.message.*;
import server.sql.DatabaseManager;
import server.sql.user.UserDAO;
import server.sql.user.uuid.UUIDGenerator;
import server.user.User;

public class ClientConnection implements Runnable {
    private final Socket clientSocket;
    private volatile boolean isConnected;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final String clientAddress;
    private final int clientPort;
    private DatabaseManager dbManager;
    private MessageCodec messageCodec;
    private UserDAO userDAO;
    private boolean isAuthenticated;
    private User currentUser;

    public ClientConnection(Socket socket) throws IOException {
        this.clientSocket = socket;
        this.clientAddress = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
        this.isConnected = true;
        this.isAuthenticated = false;
        this.dbManager = new DatabaseManager();
        this.messageCodec = new MessageCodec();
        this.userDAO = new UserDAO();
        
        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            System.out.println("客户端连接初始化完成: " + clientAddress + ":" + clientPort);
        } catch (IOException e) {
            System.err.println("初始化客户端连接流失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            close();
            throw e;
        }
    }

    @Override
    public void run() {
        System.out.println("开始处理客户端连接: " + clientAddress + ":" + clientPort);
        
        try {
            while (isConnected) {
                String jsonMessage = reader.readLine();
                
                if (jsonMessage == null) {
                    System.out.println("客户端已断开连接: " + clientAddress + ":" + clientPort);
                    break;
                }
                
                System.out.println("收到客户端消息 (" + clientAddress + ":" + clientPort + "): " + jsonMessage);
                
                // 解码消息
                Message message = messageCodec.decode(jsonMessage);
                
                if (message == null) {
                    System.err.println("消息解码失败，无法处理 (" + clientAddress + ":" + clientPort + ")");
                    continue;
                }
                
                // 处理消息
                processMessage(message);
            }
        } catch (SocketException e) {
            if (isConnected) {
                System.err.println("客户端Socket异常 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("客户端IO异常 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("客户端处理异常 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
        } finally {
            close();
        }
    }
    
    /**
     * 处理客户端消息
     * @param message 要处理的消息
     */
    private void processMessage(Message message) {
        try {
            switch (message.getType()) {
                case REGISTER:
                    handleRegister(message);
                    break;
                case LOGIN:
                    handleLogin(message);
                    break;
                case UUID_AUTH:
                    handleUUIDAuth(message);
                    break;
                default:
                    // 如果未认证，只处理认证相关消息
                    if (!isAuthenticated) {
                        sendAuthFailure("未认证，请先登录或注册");
                    } else {
                        // 已认证，处理其他消息类型（后续扩展）
                        System.out.println("处理其他消息类型: " + message.getType());
                        // 暂时回显消息
                        send(messageCodec.encode(message));
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("处理消息失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("服务器内部错误");
        }
    }
    
    /**
     * 处理用户注册请求
     * @param message 注册消息
     */
    private void handleRegister(Message message) {
        try (Connection connection = dbManager.getConnection()) {
            // 解析注册信息（格式：username:password）
            String[] parts = message.getContent().split(":");
            if (parts.length != 2) {
                sendAuthFailure("注册信息格式错误");
                return;
            }
            
            String username = parts[0];
            String password = parts[1];
            
            // 检查用户名是否已存在
            if (userDAO.getUserIdByUsername(username, connection) != null) {
                sendAuthFailure("用户名已存在");
                return;
            }
            
            // 创建用户对象
            User newUser = new User(0, username, password, null, null);
            
            // 插入用户到数据库
            userDAO.insertUser(newUser, connection);
            
            // 获取生成的用户ID
            int userId = userDAO.getUserIdByUsername(username, connection);
            if (userId == 0) {
                sendAuthFailure("注册失败，无法获取用户ID");
                return;
            }
            
            // 生成并插入UUID
            String uuid = UUIDGenerator.generateAndInsertUUID(userId, connection);
            
            // 构造注册成功消息
            Message authSuccessMessage = new Message(
                MessageType.AUTH_SUCCESS,
                "server",
                message.getFrom(),
                uuid,
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            currentUser = userDAO.getUserByUsername(username, connection);
            
            System.out.println("用户注册成功: " + username + " (ID: " + userId + ")");
            
        } catch (SQLException e) {
            System.err.println("注册失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("注册失败，数据库错误");
        } catch (Exception e) {
            System.err.println("注册处理失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("注册失败，服务器内部错误");
        }
    }
    
    /**
     * 处理用户登录请求
     * @param message 登录消息
     */
    private void handleLogin(Message message) {
        try (Connection connection = dbManager.getConnection()) {
            // 解析登录信息（格式：username:password）
            String[] parts = message.getContent().split(":");
            if (parts.length != 2) {
                sendAuthFailure("登录信息格式错误");
                return;
            }
            
            String username = parts[0];
            String password = parts[1];
            
            // 验证用户名和密码
            if (!userDAO.validateUser(username, password, connection)) {
                sendAuthFailure("用户名或密码错误");
                return;
            }
            
            // 获取用户信息
            currentUser = userDAO.getUserByUsername(username, connection);
            if (currentUser == null) {
                sendAuthFailure("获取用户信息失败");
                return;
            }
            
            // 生成或获取UUID
            String uuid = currentUser.getUuid();
            if (uuid == null) {
                // 如果没有UUID，生成一个
                uuid = UUIDGenerator.generateAndInsertUUID(currentUser.getId(), connection);
                currentUser.setUuid(uuid);
            }
            
            // 构造登录成功消息
            Message authSuccessMessage = new Message(
                MessageType.AUTH_SUCCESS,
                "server",
                message.getFrom(),
                uuid,
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            
            System.out.println("用户登录成功: " + username + " (ID: " + currentUser.getId() + ")");
            
        } catch (SQLException e) {
            System.err.println("登录失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("登录失败，数据库错误");
        } catch (Exception e) {
            System.err.println("登录处理失败: " + e.getMessage());
            e.printStackTrace();
            sendAuthFailure("登录失败，服务器内部错误");
        }
    }
    
    /**
     * 处理UUID认证请求
     * @param message UUID认证消息
     */
    private void handleUUIDAuth(Message message) {
        try (Connection connection = dbManager.getConnection()) {
            // 获取UUID
            String uuid = message.getContent();
            
            // 验证UUID
            Integer userId = UUIDGenerator.validateUUID(uuid, connection);
            if (userId == null) {
                // UUID无效
                Message authFailureMessage = new Message(
                    MessageType.UUID_AUTH_FAILURE,
                    "server",
                    message.getFrom(),
                    "无效的UUID",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                return;
            }
            
            // 获取用户信息
            User user = userDAO.getUserByUsername(message.getFrom(), connection);
            if (user == null || user.getId() != userId) {
                // 用户信息不匹配
                Message authFailureMessage = new Message(
                    MessageType.UUID_AUTH_FAILURE,
                    "server",
                    message.getFrom(),
                    "用户信息不匹配",
                    null
                );
                send(messageCodec.encode(authFailureMessage));
                return;
            }
            
            // 构造UUID认证成功消息
            Message authSuccessMessage = new Message(
                MessageType.UUID_AUTH_SUCCESS,
                "server",
                message.getFrom(),
                "认证成功",
                null
            );
            
            // 发送认证成功消息
            send(messageCodec.encode(authSuccessMessage));
            
            // 标记用户已认证
            isAuthenticated = true;
            currentUser = user;
            
            System.out.println("用户UUID认证成功: " + user.getUsername() + " (ID: " + user.getId() + ")");
            
        } catch (SQLException e) {
            System.err.println("UUID认证失败: " + e.getMessage());
            e.printStackTrace();
            Message authFailureMessage = new Message(
                MessageType.UUID_AUTH_FAILURE,
                "server",
                message.getFrom(),
                "认证失败，数据库错误",
                null
            );
            send(messageCodec.encode(authFailureMessage));
        } catch (Exception e) {
            System.err.println("UUID认证处理失败: " + e.getMessage());
            e.printStackTrace();
            Message authFailureMessage = new Message(
                MessageType.UUID_AUTH_FAILURE,
                "server",
                message.getFrom(),
                "认证失败，服务器内部错误",
                null
            );
            send(messageCodec.encode(authFailureMessage));
        }
    }
    
    /**
     * 发送认证失败消息
     * @param reason 失败原因
     */
    private void sendAuthFailure(String reason) {
        Message authFailureMessage = new Message(
            MessageType.AUTH_FAILURE,
            "server",
            "client",
            reason,
            null
        );
        send(messageCodec.encode(authFailureMessage));
    }

    public synchronized void send(String message) {
        if (!isConnected) {
            System.err.println("尝试向已关闭的连接发送消息 (" + clientAddress + ":" + clientPort + ")");
            return;
        }
        
        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
            System.out.println("消息已发送到客户端 (" + clientAddress + ":" + clientPort + "): " + message);
        } catch (IOException e) {
            System.err.println("发送消息失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
            e.printStackTrace();
            close();
        }
    }

    public synchronized void close() {
        if (!isConnected) {
            return;
        }
        
        System.out.println("正在关闭客户端连接: " + clientAddress + ":" + clientPort);
        isConnected = false;
        
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("关闭读取流失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
        } finally {
            reader = null;
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("关闭写入流失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
        } finally {
            writer = null;
        }
        
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭客户端Socket失败 (" + clientAddress + ":" + clientPort + "): " + e.getMessage());
        }
        
        System.out.println("客户端连接已完全关闭: " + clientAddress + ":" + clientPort);
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public int getClientPort() {
        return clientPort;
    }

    public boolean isConnected() {
        return isConnected;
    }
}