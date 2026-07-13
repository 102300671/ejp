package client.javafx.controller;

import client.javafx.MainApp;
import client.javafx.network.ChatWebSocketClient;
import client.javafx.network.ZFileService;
import client.javafx.protocol.ChatMessage;
import client.javafx.protocol.MessageType;
import client.javafx.util.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
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
    
    @FXML
    private Button loginTabButton;
    
    @FXML
    private Button registerTabButton;
    
    @FXML
    private VBox loginPane;
    
    @FXML
    private VBox registerPane;
    
    private ChatWebSocketClient webSocketClient;
    private static String currentUsername;
    private static String currentUserAvatar;
    private byte[] avatarData;
    private String avatarPath;
    private final Logger logger = new Logger(LoginController.class);
    
    private boolean authSuccessReceived = false;
    private boolean serviceConfigReceived = false;
    
    @FXML
    public void initialize() {
        logger.info("LoginController初始化开始");
        webSocketClient = ConnectController.getWebSocketClient();
        logger.debug("WebSocket客户端: " + (webSocketClient != null ? "已获取" : "null"));
        
        loginButton.setOnAction(e -> login());
        registerButton.setOnAction(e -> register());
        uploadAvatarButton.setOnAction(e -> uploadAvatar());
        
        loginTabButton.setOnAction(e -> switchToLogin());
        registerTabButton.setOnAction(e -> switchToRegister());
        
        if (webSocketClient != null) {
            webSocketClient.setOnMessageCallback(this::handleMessage);
            logger.info("消息回调已注册");
        } else {
            logger.warn("WebSocket客户端为空，无法注册消息回调");
        }
        
        avatarPlaceholder.setVisible(true);
        registerAvatarView.setVisible(false);
        logger.info("LoginController初始化完成");
    }
    
    private void switchToLogin() {
        loginPane.setVisible(true);
        loginPane.setManaged(true);
        registerPane.setVisible(false);
        registerPane.setManaged(false);
        
        loginTabButton.getStyleClass().add("auth-tab-active");
        registerTabButton.getStyleClass().remove("auth-tab-active");
    }
    
    private void switchToRegister() {
        registerPane.setVisible(true);
        registerPane.setManaged(true);
        loginPane.setVisible(false);
        loginPane.setManaged(false);
        
        registerTabButton.getStyleClass().add("auth-tab-active");
        loginTabButton.getStyleClass().remove("auth-tab-active");
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
        
        logger.info("用户尝试登录: " + username);
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
        
        webSocketClient.sendRegister(username, password, avatarPath);
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
                ZFileService zfileService = ZFileService.getInstance();
                if (zfileService.getZfileServerUrl() == null || zfileService.getZfileServerUrl().isEmpty() || 
                    zfileService.getZfileServerUrl().equals("https://your-zfile-server-url")) {
                    showError("ZFile服务器未配置，请先配置服务参数");
                    return;
                }
                
                String username = registerUsernameField.getText().trim();
                if (username.isEmpty()) {
                    showError("请先输入用户名");
                    return;
                }
                
                avatarPath = zfileService.uploadAvatar(selectedFile, username);
                
                Image image = new Image(selectedFile.toURI().toString());
                registerAvatarView.setImage(image);
                registerAvatarView.setClip(new Circle(40, 40, 38));
                registerAvatarView.setVisible(true);
                avatarPlaceholder.setVisible(false);
                
                statusLabel.setText("头像上传成功");
                statusLabel.setTextFill(Color.GREEN);
            } catch (Exception e) {
                e.printStackTrace();
                showError("头像上传失败: " + e.getMessage());
            }
        }
    }
    
    private void handleMessage(ChatMessage message) {
        logger.debug("收到消息: type=" + message.type + ", from=" + message.from);
        
        MessageType type = message.getMessageType();
        
        Platform.runLater(() -> {
            if (type == null) {
                logger.warn("消息类型解析失败: " + message.type);
                statusLabel.setText("未知消息类型");
                statusLabel.setTextFill(Color.RED);
                return;
            }
            
            logger.debug("处理消息类型: " + type.name());
            
            switch (type) {
                case AUTH_SUCCESS:
                    logger.info("收到AUTH_SUCCESS消息");
                    currentUsername = loginUsernameField.getText().trim();
                    if (currentUsername.isEmpty()) {
                        currentUsername = registerUsernameField.getText().trim();
                    }
                    logger.info("当前用户名: " + currentUsername);
                    
                    if (message.content != null) {
                        logger.debug("消息内容: " + message.content);
                        String[] parts = message.content.split("\\|");
                        if (parts.length >= 2) {
                            currentUserAvatar = parts[1];
                            logger.debug("头像路径: " + currentUserAvatar);
                        }
                    }
                    
                    authSuccessReceived = true;
                    trySwitchToChatScreen();
                    break;
                case SERVICE_CONFIG:
                    logger.info("收到SERVICE_CONFIG消息");
                    try {
                        com.google.gson.JsonObject config = new com.google.gson.Gson().fromJson(message.content, com.google.gson.JsonObject.class);
                        if (config.has("zfileServerUrl")) {
                            String zfileUrl = config.get("zfileServerUrl").getAsString();
                            ZFileService.getInstance().setZfileServerUrl(zfileUrl);
                            logger.info("设置ZFile服务器URL: " + zfileUrl);
                        }
                    } catch (Exception e) {
                        logger.error("解析SERVICE_CONFIG失败", e);
                    }
                    serviceConfigReceived = true;
                    trySwitchToChatScreen();
                    break;
                case AUTH_FAILURE:
                    logger.warn("认证失败: " + message.content);
                    statusLabel.setText("认证失败: " + message.content);
                    statusLabel.setTextFill(Color.RED);
                    break;
                default:
                    logger.debug("忽略未处理的消息类型: " + type.name());
                    break;
            }
        });
    }
    
    public static String getCurrentUserAvatar() {
        return currentUserAvatar;
    }
    
    private void trySwitchToChatScreen() {
        if (authSuccessReceived && serviceConfigReceived) {
            try {
                logger.info("尝试切换到聊天界面...");
                MainApp.showChatScreen();
                logger.info("切换到聊天界面成功");
            } catch (Exception e) {
                logger.error("切换界面失败", e);
                showError("切换界面失败: " + e.getMessage());
            }
        } else {
            logger.debug("等待必要消息: AUTH_SUCCESS=" + authSuccessReceived + ", SERVICE_CONFIG=" + serviceConfigReceived);
        }
    }
    
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setTextFill(Color.RED);
    }
    
    public static String getCurrentUsername() {
        return currentUsername;
    }
}