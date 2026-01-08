package server.network.socket;
import java.net.*;

public class ServerListener implements Runnable {
    private ServerSocket serverSocket;
    private boolean isRunning;

    public ServerListener(int port) throws Exception {
        serverSocket = new ServerSocket(port);
        isRunning = true;
    }

    @Override
    public void run() {
        System.out.println("服务器正在监听端口 " + serverSocket.getLocalPort());
        while (isRunning) {
            try {
                Socket socket = serverSocket.accept();
                ClientConnection clientConnection = new ClientConnection(socket);
                new Thread(clientConnection).start();
                System.out.println("新客户端已连接: " + socket.getInetAddress().getHostAddress());
                // Here you would typically start a new thread to handle the client connection
            } catch (Exception e) {
                System.err.println("接受客户端连接时出错: " + e.getMessage());
            }
        }
    }

    public void stop() {
        isRunning = false;
        try {
            serverSocket.close();
        } catch (Exception e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }
}
