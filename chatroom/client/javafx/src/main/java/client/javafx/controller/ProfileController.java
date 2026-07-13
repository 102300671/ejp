package client.javafx.controller;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import client.javafx.network.ZFileService;
import client.javafx.util.PlatformUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ProfileController {
    
    @FXML
    private ImageView profileAvatar;
    
    @FXML
    private Label usernameLabel;
    
    @FXML
    private ComboBox<String> statusComboBox;
    
    @FXML
    private Button uploadAvatarButton;
    
    @FXML
    private Button saveButton;
    
    @FXML
    private Button cancelButton;
    
    private String username;
    private String avatarPath;
    private String newAvatarPath;
    private final Gson gson = new Gson();
    
    public void setUserData(String username, String avatarPath) {
        this.username = username;
        this.avatarPath = avatarPath;
        this.usernameLabel.setText(username);
        this.statusComboBox.setValue("ONLINE");
        
        loadAvatar();
    }
    
    private void loadAvatar() {
        if (avatarPath != null && !avatarPath.isEmpty()) {
            new Thread(() -> {
                try {
                    byte[] avatarData = ZFileService.getInstance().downloadFileWithCache(avatarPath);
                    Image avatarImage = new Image(new java.io.ByteArrayInputStream(avatarData));
                    Platform.runLater(() -> {
                        profileAvatar.setImage(avatarImage);
                        profileAvatar.setClip(new Circle(50, 50, 50));
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        profileAvatar.setImage(createDefaultAvatar(username));
                    });
                }
            }).start();
        } else {
            profileAvatar.setImage(createDefaultAvatar(username));
        }
    }
    
    private Image createDefaultAvatar(String username) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(100, 100);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        
        int hue = username.hashCode() % 360;
        if (hue < 0) hue += 360;
        
        gc.setFill(javafx.scene.paint.Color.hsb(hue, 0.6, 0.85));
        gc.fillOval(0, 0, 100, 100);
        
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.setFont(new javafx.scene.text.Font("System", 48));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        
        String initial = username.length() > 0 ? username.substring(0, 1).toUpperCase() : "?";
        gc.fillText(initial, 50, 68);
        
        return canvas.snapshot(null, null);
    }
    
    @FXML
    public void initialize() {
        uploadAvatarButton.setOnAction(e -> uploadAvatar());
        saveButton.setOnAction(e -> saveProfile());
        cancelButton.setOnAction(e -> closeWindow());
        
        profileAvatar.setClip(new Circle(50, 50, 50));
        
        statusComboBox.getItems().addAll("ONLINE", "OFFLINE", "AWAY", "BUSY");
        statusComboBox.setValue("ONLINE");
    }
    
    private void uploadAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(uploadAvatarButton.getScene().getWindow());
        if (selectedFile != null) {
            try {
                Image image = new Image(new FileInputStream(selectedFile));
                profileAvatar.setImage(image);
                
                new Thread(() -> {
                    try {
                        ZFileService zfileService = ZFileService.getInstance();
                        if (zfileService.getZfileServerUrl() == null || zfileService.getZfileServerUrl().isEmpty() ||
                            zfileService.getZfileServerUrl().equals("https://your-zfile-server-url") ||
                            zfileService.getUploadToken() == null || zfileService.getUploadToken().isEmpty()) {
                            newAvatarPath = uploadAvatarLocally(selectedFile);
                        } else {
                            newAvatarPath = zfileService.uploadAvatar(selectedFile, username);
                        }
                        Platform.runLater(() -> {
                            avatarPath = newAvatarPath;
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            showError("上传头像失败: " + e.getMessage());
                        });
                    }
                }).start();
            } catch (Exception e) {
                showError("加载图片失败: " + e.getMessage());
            }
        }
    }
    
    
    private String uploadAvatarLocally(File file) throws Exception {
        String uploadPath = PlatformUtils.getUserAvatarsUploadDir(username);
        Path dirPath = Paths.get(uploadPath);
        
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        
        File[] existingFiles = dirPath.toFile().listFiles();
        if (existingFiles != null) {
            for (File existingFile : existingFiles) {
                existingFile.delete();
            }
        }
        
        String extension = getFileExtension(file.getName());
        String newFileName = "avatar" + extension;
        File uploadedFile = new File(uploadPath + newFileName);
        
        Files.copy(file.toPath(), uploadedFile.toPath());
        
        return "/avatars/users/" + username + "/" + newFileName;
    }
    
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(dotIndex).toLowerCase();
        }
        return ".png";
    }
    
    private void saveProfile() {
        String status = statusComboBox.getValue();
        
        JsonObject profileData = new JsonObject();
        profileData.addProperty("username", username);
        profileData.addProperty("status", status);
        if (avatarPath != null) {
            profileData.addProperty("avatar", avatarPath);
        }
        
        String content = gson.toJson(profileData);
        
        try {
            ConnectController.getWebSocketClient().sendUpdateProfile(username, content);
            showSuccess("个人资料保存成功");
            closeWindow();
        } catch (Exception e) {
            showError("保存失败: " + e.getMessage());
        }
    }
    
    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
    
    private void showSuccess(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("成功");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}