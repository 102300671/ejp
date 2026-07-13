package client.javafx.controller;

import client.javafx.MainApp;
import client.javafx.network.ChatWebSocketClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.net.URISyntaxException;

public class ConnectController {
    
    @FXML
    private TextField serverAddressField;
    
    @FXML
    private TextField portField;
    
    @FXML
    private Button connectButton;
    
    @FXML
    private Label statusLabel;
    
    private static ChatWebSocketClient sharedClient;
    private static String serverAddress;
    private static String serverPort;
    
    @FXML
    public void initialize() {
        serverAddressField.setText("localhost");
        portField.setText("8889");
        statusLabel.setText("未连接");
        statusLabel.setTextFill(Color.GRAY);
        
        connectButton.setOnAction(e -> connectToServer());
    }
    
    private void connectToServer() {
        String serverAddress = serverAddressField.getText().trim();
        String port = portField.getText().trim();
        
        if (serverAddress.isEmpty() || port.isEmpty()) {
            showError("请填写服务器地址和端口");
            return;
        }
        
        try {
            ConnectController.serverAddress = serverAddress;
            ConnectController.serverPort = port;
            String serverUrl = "ws://" + serverAddress + ":" + port;
            ChatWebSocketClient webSocketClient = new ChatWebSocketClient(serverUrl);
            
            webSocketClient.setOnConnectionCallback(connected -> {
                Platform.runLater(() -> {
                    if (connected) {
                        statusLabel.setText("已连接");
                        statusLabel.setTextFill(Color.GREEN);
                        sharedClient = webSocketClient;
                        try {
                            System.out.println("Attempting to show login screen...");
                            MainApp.showLoginScreen();
                            System.out.println("Login screen shown successfully");
                        } catch (Exception e) {
                            System.err.println("Error showing login screen: " + e.getMessage());
                            e.printStackTrace();
                            showError("切换到登录界面失败: " + e.getMessage());
                        }
                    } else {
                        statusLabel.setText("连接失败");
                        statusLabel.setTextFill(Color.RED);
                    }
                });
            });
            
            webSocketClient.connect();
            statusLabel.setText("连接中...");
            statusLabel.setTextFill(Color.ORANGE);
            
        } catch (URISyntaxException e) {
            showError("无效的服务器地址");
        }
    }
    
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setTextFill(Color.RED);
    }
    
    public static ChatWebSocketClient getWebSocketClient() {
        return sharedClient;
    }
    
    public static String getServerUrl() {
        if (serverAddress != null && serverPort != null) {
            return "http://" + serverAddress + ":8081";
        }
        return null;
    }
}