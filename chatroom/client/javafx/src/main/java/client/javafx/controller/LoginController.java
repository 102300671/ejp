package client.javafx.controller;

import client.javafx.MainApp;
import client.javafx.network.ChatWebSocketClient;
import client.javafx.protocol.ChatMessage;
import client.javafx.protocol.MessageType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class LoginController {
    
    @FXML
    private TextField loginUsernameField;
    
    @FXML
    private PasswordField loginPasswordField;
    
    @FXML
    private Button loginButton;
    
    @FXML
    private TextField registerUsernameField;
    
    @FXML
    private PasswordField registerPasswordField;
    
    @FXML
    private PasswordField registerConfirmField;
    
    @FXML
    private Button registerButton;
    
    @FXML
    private ImageView registerAvatarView;
    
    @FXML
    private Button uploadAvatarButton;
    
    @FXML
    private Label avatarPlaceholder;
    
    @FXML
    private Circle avatarCircle;
    
    @FXML
    private Label statusLabel;
    
    private ChatWebSocketClient webSocketClient;
    private static String currentUsername;
    private byte[] avatarData;
    
    @FXML
    public void initialize() {
        webSocketClient = ConnectController.getWebSocketClient();
        
        loginButton.setOnAction(e -> login());
        registerButton.setOnAction(e -> register());
        uploadAvatarButton.setOnAction(e -> uploadAvatar());
        
        if (webSocketClient != null) {
            webSocketClient.setOnMessageCallback(this::handleMessage);
        }
        
        avatarPlaceholder.setVisible(true);
        registerAvatarView.setVisible(false);
    }
    
    private void login() {
        String username = loginUsernameField.getText().trim();
        String password = loginPasswordField.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("请填写用户名和密码");
            return;
        }
        
        if (webSocketClient == null) {
            showError("请先连接服务器");
            return;
        }
        
        webSocketClient.sendLogin(username, password);
        statusLabel.setText("登录中...");
        statusLabel.setTextFill(Color.ORANGE);
    }
    
    private void register() {
        String username = registerUsernameField.getText().trim();
        String password = registerPasswordField.getText().trim();
        String confirm = registerConfirmField.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("请填写用户名和密码");
            return;
        }
        
        if (!password.equals(confirm)) {
            showError("两次输入的密码不一致");
            return;
        }
        
        if (webSocketClient == null) {
            showError("请先连接服务器");
            return;
        }
        
        webSocketClient.sendRegister(username, password, avatarData);
        statusLabel.setText("注册中...");
        statusLabel.setTextFill(Color.ORANGE);
    }
    
    private void uploadAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择头像");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(MainApp.getPrimaryStage());
        if (selectedFile != null) {
            try {
                FileInputStream fis = new FileInputStream(selectedFile);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                avatarData = bos.toByteArray();
                fis.close();
                bos.close();
                
                Image image = new Image(selectedFile.toURI().toString());
                registerAvatarView.setImage(image);
                registerAvatarView.setClip(new Circle(40, 40, 38));
                registerAvatarView.setVisible(true);
                avatarPlaceholder.setVisible(false);
                
                statusLabel.setText("头像已选择");
                statusLabel.setTextFill(Color.GREEN);
            } catch (IOException e) {
                showError("读取头像文件失败");
            }
        }
    }
    
    private void handleMessage(ChatMessage message) {
        MessageType type = message.getMessageType();
        
        Platform.runLater(() -> {
            if (type == null) {
                statusLabel.setText("未知消息类型");
                statusLabel.setTextFill(Color.RED);
                return;
            }
            
            switch (type) {
                case AUTH_SUCCESS:
                    currentUsername = loginUsernameField.getText().trim();
                    if (currentUsername.isEmpty()) {
                        currentUsername = registerUsernameField.getText().trim();
                    }
                    try {
                        MainApp.showChatScreen();
                    } catch (Exception e) {
                        showError("切换界面失败: " + e.getMessage());
                    }
                    break;
                case AUTH_FAILURE:
                    statusLabel.setText("认证失败: " + message.content);
                    statusLabel.setTextFill(Color.RED);
                    break;
                default:
                    break;
            }
        });
    }
    
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setTextFill(Color.RED);
    }
    
    public static String getCurrentUsername() {
        return currentUsername;
    }
}