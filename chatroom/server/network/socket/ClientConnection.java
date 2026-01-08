package server.network.socket;
import java.io.*;
import java.net.*;

public class ClientConnection implements Runnable {
    private Socket socket;
    private boolean connected;
    private BufferedReader reader;
    private BufferedWriter writer;

    public ClientConnection(Socket socket) throws IOException{
        this.socket = socket;
        this.connected = true;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    @Override
    public void run() {
        try {
            while(connected) {
                String json = reader.readLine();
                if(json == null) {
                    break;
                }
                send(json);
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public void send(String json) {
        try {
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            connected = false;
            reader.close();
            writer.close();
            socket.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}