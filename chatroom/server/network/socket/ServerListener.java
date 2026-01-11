package server.network.socket;
import java.net.*;

public class ServerListener implements Runnable {
    private ServerSocket serverSocket;
    private volatile boolean isRunning;

    public ServerListener(int port) throws Exception {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("服务器已成功创建，将监听端口: " + port);
        } catch (Exception e) {
            System.err.println("创建服务器Socket失败 (端口: " + port + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public void run() {
        System.out.println("服务器正在监听端口: " + serverSocket.getLocalPort());
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("新客户端已连接: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
                
                ClientConnection clientConnection = new ClientConnection(clientSocket);
                Thread clientThread = new Thread(clientConnection);
                clientThread.setName("ClientHandler-" + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
                clientThread.start();
                
            } catch (SocketException e) {
                if (isRunning) {
                    System.err.println("服务器Socket异常: " + e.getMessage());
                    e.printStackTrace();
                } else {
                    System.out.println("服务器Socket已正常关闭");
                }
                break;
            } catch (Exception e) {
                System.err.println("接受客户端连接时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("服务器监听线程已退出");
    }

    public void stop() {
        isRunning = false;
        System.out.println("正在停止服务器...");
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("服务器Socket已成功关闭");
            } catch (Exception e) {
                System.err.println("关闭服务器Socket时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取服务器监听端口
     * @return 服务器端口号
     */
    public int getPort() {
        if (serverSocket != null) {
            return serverSocket.getLocalPort();
        }
        return -1;
    }
}
