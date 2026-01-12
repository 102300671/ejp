package client.network;
import client.message.Message;
import client.message.MessageCodec;
import java.io.*;
import java.net.*;

public class ClientConnection implements Runnable {
    private final String serverAddress;
    private final int serverPort;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private MessageCodec messageCodec;
    private volatile boolean isConnected;
    private MessageReceivedCallback messageReceivedCallback;
    
    /**
     * 消息接收回调接口
     */
    public interface MessageReceivedCallback {
        void onMessageReceived(Message message);
        void onConnectionClosed(String reason);
    }
    
    /**
     * 构造客户端连接对象
     * @param serverAddress 服务器地址
     * @param serverPort 服务器端口
     */
    public ClientConnection(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.messageCodec = new MessageCodec();
        this.isConnected = false;
    }
    
    /**
     * 设置消息接收回调
     * @param callback 回调对象
     */
    public void setMessageReceivedCallback(MessageReceivedCallback callback) {
        this.messageReceivedCallback = callback;
    }
    
    /**
     * 连接到服务器
     * @throws IOException 连接过程中可能发生的IO异常
     */
    public void connect() throws IOException {
        if (isConnected) {
            System.out.println("已经连接到服务器");
            return;
        }
        
        System.out.println("正在连接到服务器: " + serverAddress + ":" + serverPort);
        
        try {
            // 创建Socket连接
            this.socket = new Socket(serverAddress, serverPort);
            
            // 初始化输入输出流
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            
            this.isConnected = true;
            
            System.out.println("成功连接到服务器: " + serverAddress + ":" + serverPort);
            
        } catch (IOException e) {
            System.err.println("连接服务器失败: " + e.getMessage());
            close();
            throw e;
        }
    }
    
    /**
     * 发送消息到服务器
     * @param message 要发送的消息
     */
    public synchronized void sendMessage(Message message) {
        if (!isConnected) {
            System.err.println("尝试向已关闭的连接发送消息");
            return;
        }
        
        try {
            // 编码消息
            String jsonMessage = messageCodec.encode(message);
            if (jsonMessage == null) {
                return;
            }
            
            // 发送消息
            writer.write(jsonMessage);
            writer.newLine();
            writer.flush();
            
        } catch (IOException e) {
            System.err.println("发送消息失败: " + e.getMessage());
            e.printStackTrace();
            close();
        }
    }
    
    /**
     * 运行在独立线程中，接收服务器消息
     */
    @Override
    public void run() {
        if (!isConnected) {
            System.err.println("连接未建立，无法接收消息");
            return;
        }
        
        System.out.println("开始接收服务器消息...");
        
        try {
            String jsonMessage;
            while (isConnected && (jsonMessage = reader.readLine()) != null) {
                // 解码消息
                Message message = messageCodec.decode(jsonMessage);
                if (message != null && messageReceivedCallback != null) {
                    // 通知回调处理消息
                    messageReceivedCallback.onMessageReceived(message);
                }
            }
            
            System.out.println("服务器连接已关闭");
            
        } catch (SocketException e) {
            if (isConnected) {
                System.err.println("服务器连接异常中断: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("接收消息时发生IO异常: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("接收消息时发生异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            close();
        }
    }
    
    /**
     * 关闭连接
     */
    public synchronized void close() {
        if (!isConnected) {
            return;
        }
        
        System.out.println("正在关闭客户端连接...");
        
        isConnected = false;
        
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            System.err.println("关闭读取流失败: " + e.getMessage());
        } finally {
            reader = null;
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("关闭写入流失败: " + e.getMessage());
        } finally {
            writer = null;
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭Socket失败: " + e.getMessage());
        } finally {
            socket = null;
        }
        
        System.out.println("客户端连接已完全关闭");
        
        // 通知回调连接已关闭
        if (messageReceivedCallback != null) {
            messageReceivedCallback.onConnectionClosed("连接已关闭");
        }
    }
    
    /**
     * 检查是否已连接到服务器
     * @return true表示已连接，false表示未连接
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * 获取服务器地址
     * @return 服务器地址
     */
    public String getServerAddress() {
        return serverAddress;
    }
    
    /**
     * 获取服务器端口
     * @return 服务器端口
     */
    public int getServerPort() {
        return serverPort;
    }
}