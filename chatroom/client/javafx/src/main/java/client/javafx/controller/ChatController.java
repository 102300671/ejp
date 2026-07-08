package client.javafx.controller;

import client.javafx.MainApp;
import client.javafx.network.ChatWebSocketClient;
import client.javafx.network.ZFileService;
import client.javafx.network.OnlyOfficeService;
import client.javafx.protocol.ChatMessage;
import client.javafx.protocol.MessageType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ChatController {
    
    @FXML
    private ImageView userAvatar;
    
    @FXML
    private Button navMessagesButton;
    
    @FXML
    private Button navContactsButton;
    
    @FXML
    private Label contentTitleLabel;
    
    @FXML
    private ListView<ListItem> mainListView;
    
    @FXML
    private HBox contentButtons;
    
    @FXML
    private Button createRoomButton;
    
    @FXML
    private Button joinRoomButton;
    
    @FXML
    private Button leaveRoomButton;
    
    @FXML
    private Button addFriendButton;
    
    @FXML
    private Button friendRequestsButton;
    
    @FXML
    private Button logoutButton;
    
    @FXML
    private Button uploadImageButton;
    
    @FXML
    private Button uploadFileButton;
    
    @FXML
    private Label currentChatLabel;
    
    @FXML
    private TextField messageInput;
    
    @FXML
    private Button sendButton;
    
    @FXML
    private VBox messagesVBox;
    
    private ChatWebSocketClient webSocketClient;
    private String currentUsername;
    private String currentRoom = "system";
    private Integer currentConversationId;
    private Map<String, Integer> roomConversationIds = new HashMap<>();
    private Map<String, List<ChatMessage>> roomMessages = new HashMap<>();
    private ObservableList<Room> roomsList = FXCollections.observableArrayList();
    private ObservableList<Friend> friendsList = FXCollections.observableArrayList();
    private ObservableList<ListItem> messagesList = FXCollections.observableArrayList();
    private ObservableList<ListItem> contactsList = FXCollections.observableArrayList();
    private final Gson gson = new Gson();
    private client.javafx.storage.LocalMessageStorage localMessageStorage;
    private boolean isMessagesMode = true;
    private Set<String> expandedCategories = new HashSet<>();
    private List<FriendRequest> pendingFriendRequests = new ArrayList<>();
    
    public enum ListItemType { MESSAGE, CONTACT, CATEGORY, FRIEND_REQUEST }
    
    @FXML
    public void initialize() {
        currentUsername = LoginController.getCurrentUsername();
        webSocketClient = ConnectController.getWebSocketClient();
        localMessageStorage = new client.javafx.storage.LocalMessageStorage(currentUsername);
        
        loadUserAvatar();
        setupNavigationIcons();
        
        mainListView.setCellFactory(param -> new ListItemCell());
        
        mainListView.setOnMouseClicked(e -> {
            ListItem selectedItem = mainListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                handleListItemSelection(selectedItem);
            }
        });
        
        navMessagesButton.setOnAction(e -> switchToMessagesMode());
        navContactsButton.setOnAction(e -> switchToContactsMode());
        
        sendButton.setOnAction(e -> sendMessage());
        messageInput.setOnAction(e -> sendMessage());
        
        uploadImageButton.setOnAction(e -> uploadImage());
        uploadFileButton.setOnAction(e -> uploadFile());
        
        createRoomButton.setOnAction(e -> createRoom());
        joinRoomButton.setOnAction(e -> joinRoom());
        leaveRoomButton.setOnAction(e -> leaveRoom());
        
        addFriendButton.setOnAction(e -> addFriend());
        friendRequestsButton.setOnAction(e -> showFriendRequests());
        
        logoutButton.setOnAction(e -> logout());
        
        webSocketClient.setOnMessageCallback(this::handleMessage);
        
        roomsList.add(new Room("system"));
        currentRoom = "system";
        currentChatLabel.setText("当前房间: system");
        
        loadConversationsFromStorage();
        
        updateMessagesList();
        
        webSocketClient.sendListRooms(currentUsername);
        webSocketClient.sendRequestFriendList(currentUsername);
        webSocketClient.sendRequestAllFriendRequests(currentUsername);
        webSocketClient.sendRequestToken(currentUsername);
    }
    
    private void handleRoomSelection(String roomName) {
        currentRoom = roomName;
        currentConversationId = roomConversationIds.get(currentRoom);
        
        messagesVBox.getChildren().clear();
        
        String lastTime = "0";
        if (currentConversationId != null && localMessageStorage.hasMessages(currentConversationId)) {
            List<ChatMessage> localMsgs = localMessageStorage.getMessagesByConversationId(currentConversationId);
            if (!localMsgs.isEmpty()) {
                for (ChatMessage msg : localMsgs) {
                    displayMessage(msg);
                }
                roomMessages.put(currentRoom, new ArrayList<>(localMsgs));
                lastTime = localMessageStorage.getLastMessageTime(currentConversationId);
            }
        }
        
        webSocketClient.sendJoin(currentUsername, currentRoom, currentConversationId);
        currentChatLabel.setText("当前房间: " + currentRoom);
        
        if (currentConversationId != null) {
            webSocketClient.sendRequestHistory(currentUsername, lastTime, currentConversationId);
        }
        
        refreshListViews();
    }
    
    private void handleFriendSelection(String friendName) {
        currentRoom = friendName;
        currentConversationId = roomConversationIds.get(friendName);
        
        messagesVBox.getChildren().clear();
        
        String lastTime = "0";
        if (currentConversationId != null && localMessageStorage.hasMessages(currentConversationId)) {
            List<ChatMessage> localMsgs = localMessageStorage.getMessagesByConversationId(currentConversationId);
            if (!localMsgs.isEmpty()) {
                for (ChatMessage msg : localMsgs) {
                    displayMessage(msg);
                }
                roomMessages.put(friendName, new ArrayList<>(localMsgs));
                lastTime = localMessageStorage.getLastMessageTime(currentConversationId);
            }
        }
        
        currentChatLabel.setText("私聊: " + friendName);
        
        if (currentConversationId != null) {
            webSocketClient.sendRequestHistory(currentUsername, lastTime, currentConversationId);
        }
        
        refreshListViews();
    }
    
    private void sendMessage() {
        String content = messageInput.getText().trim();
        if (content.isEmpty()) return;
        
        boolean isPrivate = friendsList.stream().anyMatch(f -> f.getUsername().equals(currentRoom));
        
        ChatMessage outgoingMessage = new ChatMessage();
        outgoingMessage.from = currentUsername;
        outgoingMessage.content = content;
        outgoingMessage.time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (isPrivate) {
            outgoingMessage.type = MessageType.PRIVATE_CHAT.name();
            outgoingMessage.conversationId = currentConversationId;
            webSocketClient.sendPrivateChat(currentUsername, content, currentRoom, currentConversationId);
        } else {
            outgoingMessage.type = MessageType.TEXT.name();
            outgoingMessage.conversationId = currentConversationId;
            webSocketClient.sendText(currentUsername, content, currentConversationId);
        }
        
        if (!roomMessages.containsKey(currentRoom)) {
            roomMessages.put(currentRoom, new ArrayList<>());
        }
        roomMessages.get(currentRoom).add(outgoingMessage);
        
        if (currentConversationId != null) {
            localMessageStorage.saveMessage(outgoingMessage, currentRoom);
        }
        
        appendOwnMessage(content);
        messageInput.setText("");
        
        refreshListViews();
    }
    
    private void loadUserAvatar() {
        try {
            String avatarPath = "avatars/" + currentUsername + ".png";
            java.io.File avatarFile = new java.io.File(avatarPath);
            
            if (avatarFile.exists()) {
                userAvatar.setImage(new Image(avatarFile.toURI().toString()));
            } else {
                avatarPath = "avatars/default.png";
                java.io.File defaultAvatar = new java.io.File(avatarPath);
                if (defaultAvatar.exists()) {
                    userAvatar.setImage(new Image(defaultAvatar.toURI().toString()));
                } else {
                    userAvatar.setImage(createDefaultAvatar(currentUsername));
                }
            }
        } catch (Exception e) {
            userAvatar.setImage(createDefaultAvatar(currentUsername));
        }
    }
    
    private Image createDefaultAvatar(String username) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(48, 48);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        
        int hue = username.hashCode() % 360;
        if (hue < 0) hue += 360;
        
        gc.setFill(javafx.scene.paint.Color.hsb(hue, 0.6, 0.85));
        gc.fillOval(0, 0, 48, 48);
        
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.setFont(new Font("System", 24));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        
        String initial = username.length() > 0 ? username.substring(0, 1).toUpperCase() : "?";
        gc.fillText(initial, 24, 32);
        
        return canvas.snapshot(null, null);
    }
    
    private void loadConversationsFromStorage() {
        List<client.javafx.storage.LocalMessageStorage.ConversationInfo> conversations = 
            localMessageStorage.getConversations();
        
        for (client.javafx.storage.LocalMessageStorage.ConversationInfo conv : conversations) {
            roomConversationIds.put(conv.conversationName, conv.conversationId);
            
            if (!roomMessages.containsKey(conv.conversationName)) {
                roomMessages.put(conv.conversationName, new ArrayList<>());
            }
            
            List<ChatMessage> localMsgs = localMessageStorage.getMessagesByConversationId(conv.conversationId);
            if (!localMsgs.isEmpty()) {
                roomMessages.get(conv.conversationName).addAll(localMsgs);
            }
        }
    }
    
    private void setupNavigationIcons() {
        navMessagesButton.setGraphic(createMessageIcon());
        navContactsButton.setGraphic(createContactsIcon());
    }
    
    private javafx.scene.shape.SVGPath createMessageIcon() {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent("M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z");
        path.getStyleClass().add("nav-icon");
        return path;
    }
    
    private javafx.scene.shape.SVGPath createContactsIcon() {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z");
        path.getStyleClass().add("nav-icon");
        return path;
    }
    
    private void uploadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择图片");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(MainApp.getPrimaryStage());
        if (selectedFile != null) {
            if (selectedFile.length() > 10 * 1024 * 1024) {
                showError("图片大小不能超过 10MB");
                return;
            }
            
            new Thread(() -> {
                try {
                    String relativePath = ZFileService.getInstance().uploadImage(selectedFile, currentRoom);
                    webSocketClient.sendImage(currentUsername, relativePath, currentConversationId);
                    
                    Platform.runLater(() -> {
                        appendOwnImage(relativePath);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("图片上传失败: " + e.getMessage()));
                }
            }).start();
        }
    }
    
    private void uploadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择文件");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        
        File selectedFile = fileChooser.showOpenDialog(MainApp.getPrimaryStage());
        if (selectedFile != null) {
            if (selectedFile.length() > 50 * 1024 * 1024) {
                showError("文件大小不能超过 50MB");
                return;
            }
            
            new Thread(() -> {
                try {
                    String relativePath = ZFileService.getInstance().uploadFile(selectedFile, currentRoom);
                    webSocketClient.sendFile(currentUsername, relativePath, selectedFile.getName(), "download", currentConversationId);
                    
                    Platform.runLater(() -> {
                        appendOwnFile(relativePath, selectedFile.getName());
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("文件上传失败: " + e.getMessage()));
                }
            }).start();
        }
    }
    
    private void appendOwnMessage(String content) {
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        contentBox.setMaxWidth(300);
        
        Label senderLabel = new Label(currentUsername);
        senderLabel.setTextFill(Color.WHITE);
        senderLabel.setFont(new Font("System", 12));
        
        Text messageText = new Text(content);
        messageText.setFill(Color.WHITE);
        messageText.setFont(new Font("System", 14));
        
        Label timeLabel = new Label(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setFont(new Font("System", 10));
        
        contentBox.getChildren().addAll(senderLabel, messageText, timeLabel);
        messageBox.getChildren().add(contentBox);
        
        messagesVBox.getChildren().add(messageBox);
        scrollToBottom();
    }
    
    private void appendOwnImage(String relativePath) {
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        
        Label senderLabel = new Label(currentUsername);
        senderLabel.setTextFill(Color.WHITE);
        senderLabel.setFont(new Font("System", 12));
        
        String previewUrl = ZFileService.getInstance().getFilePreviewUrl(relativePath);
        ImageView imageView = new ImageView();
        imageView.setFitWidth(200);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);
        imageView.setOnMouseClicked(e -> previewImage(previewUrl));
        
        if (previewUrl != null) {
            imageView.setImage(new Image(previewUrl));
        }
        
        contentBox.getChildren().addAll(senderLabel, imageView);
        messageBox.getChildren().add(contentBox);
        
        messagesVBox.getChildren().add(messageBox);
        scrollToBottom();
    }
    
    private void appendOwnFile(String relativePath, String fileName) {
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        
        Label senderLabel = new Label(currentUsername);
        senderLabel.setTextFill(Color.WHITE);
        senderLabel.setFont(new Font("System", 12));
        
        Button fileButton = new Button(fileName);
        fileButton.setTextFill(Color.WHITE);
        fileButton.setFont(new Font("System", 14));
        fileButton.setBackground(null);
        fileButton.setOnAction(e -> downloadFile(relativePath, fileName));
        
        contentBox.getChildren().addAll(senderLabel, fileButton);
        messageBox.getChildren().add(contentBox);
        
        messagesVBox.getChildren().add(messageBox);
        scrollToBottom();
    }
    
    private void displayMessage(ChatMessage message) {
        HBox messageBox = new HBox();
        boolean isOwn = message.from.equals(currentUsername);
        
        if (isOwn) {
            messageBox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        if (isOwn) {
            contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        } else {
            contentBox.setBackground(new Background(new BackgroundFill(Color.web("#e2e8f0"), new CornerRadii(12), null)));
        }
        contentBox.setMaxWidth(300);
        
        Label senderLabel = new Label(message.from);
        senderLabel.setFont(new Font("System", 12));
        if (isOwn) {
            senderLabel.setTextFill(Color.WHITE);
        } else {
            senderLabel.setTextFill(Color.web("#64748b"));
        }
        
        Label timeLabel = new Label(message.time);
        timeLabel.setFont(new Font("System", 10));
        if (isOwn) {
            timeLabel.setTextFill(Color.WHITE);
        } else {
            timeLabel.setTextFill(Color.web("#94a3b8"));
        }
        
        VBox messageContent = new VBox();
        
        MessageType type = message.getMessageType();
        if (type == MessageType.IMAGE) {
            String imageUrl = message.content;
            String previewUrl = ZFileService.getInstance().getFilePreviewUrl(imageUrl);
            
            ImageView imageView = new ImageView();
            imageView.setFitWidth(200);
            imageView.setFitHeight(200);
            imageView.setPreserveRatio(true);
            
            if (previewUrl != null) {
                imageView.setImage(new Image(previewUrl));
            }
            
            imageView.setOnMouseClicked(e -> previewImage(previewUrl));
            messageContent.getChildren().add(imageView);
        } else if (type == MessageType.FILE) {
            try {
                JsonObject fileInfo = gson.fromJson(message.content, JsonObject.class);
                String fileUrl = fileInfo.has("url") ? fileInfo.get("url").getAsString() : message.content;
                String fileName = fileInfo.has("fileName") ? fileInfo.get("fileName").getAsString() : "未知文件";
                
                Button fileButton = new Button(fileName);
                fileButton.setFont(new Font("System", 14));
                fileButton.setBackground(null);
                if (isOwn) {
                    fileButton.setTextFill(Color.WHITE);
                } else {
                    fileButton.setTextFill(Color.web("#3b82f6"));
                }
                fileButton.setOnAction(e -> downloadFile(fileUrl, fileName));
                messageContent.getChildren().add(fileButton);
            } catch (Exception e) {
                Text text = new Text(message.content);
                text.setFont(new Font("System", 14));
                if (isOwn) {
                    text.setFill(Color.WHITE);
                } else {
                    text.setFill(Color.BLACK);
                }
                messageContent.getChildren().add(text);
            }
        } else {
            String displayContent = message.content;
            
            if (type == MessageType.PRIVATE_CHAT || type == MessageType.TEXT) {
                try {
                    JsonObject contentObj = gson.fromJson(message.content, JsonObject.class);
                    if (contentObj.has("content")) {
                        displayContent = contentObj.get("content").getAsString();
                    }
                } catch (Exception e) {
                }
            }
            
            Text text = new Text(displayContent);
            text.setFont(new Font("System", 14));
            if (isOwn) {
                text.setFill(Color.WHITE);
            } else {
                text.setFill(Color.BLACK);
            }
            messageContent.getChildren().add(text);
        }
        
        contentBox.getChildren().addAll(senderLabel, messageContent, timeLabel);
        messageBox.getChildren().add(contentBox);
        
        messagesVBox.getChildren().add(messageBox);
        scrollToBottom();
    }
    
    private void previewImage(String url) {
        if (url == null) return;
        
        Stage previewStage = new Stage();
        previewStage.setTitle("图片预览");
        
        ImageView imageView = new ImageView();
        imageView.setImage(new Image(url));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(600);
        imageView.setFitHeight(600);
        
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        
        Scene scene = new Scene(scrollPane, 650, 650);
        previewStage.setScene(scene);
        previewStage.show();
    }
    
    private void downloadFile(String relativePath, String fileName) {
        if (OnlyOfficeService.getInstance().canPreviewWithOnlyOffice(fileName)) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("文件预览");
            alert.setHeaderText("检测到可预览文件");
            alert.setContentText("是否使用 OnlyOffice 在线预览该文件？");
            
            ButtonType previewButton = new ButtonType("在线预览");
            ButtonType downloadButton = new ButtonType("直接下载");
            
            alert.getButtonTypes().setAll(previewButton, downloadButton);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == previewButton) {
                    previewWithOnlyOffice(relativePath, fileName);
                    return;
                }
            }
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存文件");
        fileChooser.setInitialFileName(fileName);
        File saveFile = fileChooser.showSaveDialog(MainApp.getPrimaryStage());
        
        if (saveFile != null) {
            new Thread(() -> {
                try {
                    byte[] fileData = ZFileService.getInstance().downloadFile(relativePath);
                    Files.write(saveFile.toPath(), fileData);
                    Platform.runLater(() -> showInfo("文件下载成功"));
                } catch (Exception e) {
                    Platform.runLater(() -> showError("文件下载失败: " + e.getMessage()));
                }
            }).start();
        }
    }
    
    private void previewWithOnlyOffice(String relativePath, String fileName) {
        new Thread(() -> {
            try {
                JsonObject config = OnlyOfficeService.getInstance().getOnlyOfficeConfig(relativePath, fileName, "view");
                String documentUrl = config.getAsJsonObject("document").get("url").getAsString();
                
                Platform.runLater(() -> {
                    Stage previewStage = new Stage();
                    previewStage.setTitle("OnlyOffice - " + fileName);
                    
                    WebView webView = new WebView();
                    WebEngine webEngine = webView.getEngine();
                    
                    String editorUrl = OnlyOfficeService.getInstance().getOnlyOfficeApiUrl();
                    editorUrl = editorUrl.replace("/web-apps/apps/api/documents/api.js", "/web-apps/apps/documents/webviewer.html");
                    editorUrl = editorUrl + "?url=" + java.net.URLEncoder.encode(documentUrl);
                    
                    webEngine.load(editorUrl);
                    
                    Scene scene = new Scene(webView, 900, 700);
                    previewStage.setScene(scene);
                    previewStage.show();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("OnlyOffice 预览失败: " + e.getMessage()));
            }
        }).start();
    }
    
    private void scrollToBottom() {
        messagesVBox.layout();
        messagesVBox.getChildren().stream()
            .reduce((first, second) -> second)
            .ifPresent(node -> {
                node.requestFocus();
            });
    }
    
    private void createRoom() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("创建房间");
        dialog.setHeaderText("请输入房间信息");
        
        Label nameLabel = new Label("房间名称:");
        TextField nameField = new TextField();
        
        Label typeLabel = new Label("房间类型:");
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("public", "private");
        typeCombo.setValue("public");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(typeLabel, 0, 1);
        grid.add(typeCombo, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new String[]{nameField.getText(), typeCombo.getValue()};
            }
            return null;
        });
        
        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            String roomName = data[0];
            String roomType = data[1];
            if (!roomName.isEmpty()) {
                webSocketClient.sendCreateRoom(currentUsername, roomName, roomType);
            }
        });
    }
    
    private void joinRoom() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("加入房间");
        dialog.setHeaderText("请输入房间名称");
        dialog.setContentText("房间名称:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(roomName -> {
            webSocketClient.sendJoin(currentUsername, roomName, null);
        });
    }
    
    private void leaveRoom() {
        if (currentRoom != null && !currentRoom.equals("system")) {
            webSocketClient.sendExitRoom(currentUsername, currentRoom);
            roomsList.removeIf(r -> r.getName().equals(currentRoom));
            currentRoom = "system";
            currentConversationId = roomConversationIds.get(currentRoom);
            messagesVBox.getChildren().clear();
            currentChatLabel.setText("当前房间: system");
            
            if (roomMessages.containsKey("system")) {
                for (ChatMessage msg : roomMessages.get("system")) {
                    displayMessage(msg);
                }
            }
        }
    }
    
    private void addFriend() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("添加好友");
        dialog.setHeaderText("请输入好友用户名");
        dialog.setContentText("用户名:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(username -> {
            webSocketClient.sendFriendRequest(currentUsername, username, "");
        });
    }
    
    private void showFriendRequests() {
        webSocketClient.sendRequestAllFriendRequests(currentUsername);
    }
    
    private void logout() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
        try {
            MainApp.showConnectScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleMessage(ChatMessage message) {
        MessageType type = message.getMessageType();
        
        Platform.runLater(() -> {
            if (type == null) {
                return;
            }
            
            switch (type) {
                case ROOM_LIST:
                    handleRoomList(message);
                    break;
                case TEXT:
                case PRIVATE_CHAT:
                case IMAGE:
                case FILE:
                    handleChatMessage(message);
                    break;
                case SYSTEM:
                    handleSystemMessage(message);
                    break;
                case FRIEND_LIST:
                    handleFriendList(message);
                    break;
                case USER_STATUS_UPDATE:
                    handleUserStatusUpdate(message);
                    break;
                case ALL_FRIEND_REQUESTS:
                    handleAllFriendRequests(message);
                    break;
                case FRIEND_REQUEST:
                    handleFriendRequest(message);
                    break;
                case FRIEND_REQUEST_RESPONSE:
                    handleFriendRequestResponse(message);
                    break;
                case TOKEN_RESPONSE:
                    handleTokenResponse(message);
                    break;
                case SERVICE_CONFIG:
                    handleServiceConfig(message);
                    break;
                case HISTORY_RESPONSE:
                    handleHistoryResponse(message);
                    break;
                default:
                    break;
            }
        });
    }
    
    private void handleRoomList(ChatMessage message) {
        try {
            JsonObject responseObj = gson.fromJson(message.content, JsonObject.class);
            JsonArray roomsArray = responseObj.getAsJsonArray("rooms");
            
            Set<String> existingRooms = new HashSet<>();
            for (Room room : roomsList) {
                existingRooms.add(room.getName());
            }
            
            boolean systemRoomFound = false;
            
            for (JsonElement roomElement : roomsArray) {
                JsonObject roomObj = roomElement.getAsJsonObject();
                String roomName = roomObj.get("name").getAsString();
                Integer conversationId = roomObj.has("conversation_id") ? roomObj.get("conversation_id").getAsInt() : null;
                
                if (conversationId != null) {
                    roomConversationIds.put(roomName, conversationId);
                }
                
                if (!existingRooms.contains(roomName)) {
                    roomsList.add(new Room(roomName));
                }
                
                if ("system".equals(roomName)) {
                    systemRoomFound = true;
                }
            }
            
            if (roomConversationIds.containsKey("system")) {
                currentConversationId = roomConversationIds.get("system");
                
                if ("system".equals(currentRoom)) {
                    webSocketClient.sendJoin(currentUsername, currentRoom, currentConversationId);
                    
                    String lastTime = "0";
                    if (localMessageStorage.hasMessages(currentConversationId)) {
                        lastTime = localMessageStorage.getLastMessageTime(currentConversationId);
                    }
                    webSocketClient.sendRequestHistory(currentUsername, lastTime, currentConversationId);
                }
            }
            
            refreshListViews();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleChatMessage(ChatMessage message) {
        Integer conversationId = message.conversationId;
        
        String targetRoom = null;
        if (message.getMessageType() == MessageType.PRIVATE_CHAT) {
            targetRoom = message.from;
        } else {
            for (Map.Entry<String, Integer> entry : roomConversationIds.entrySet()) {
                if (entry.getValue().equals(conversationId)) {
                    targetRoom = entry.getKey();
                    break;
                }
            }
        }
        
        if (targetRoom == null) {
            targetRoom = currentRoom;
        }
        
        if (conversationId != null) {
            roomConversationIds.put(targetRoom, conversationId);
        }
        
        if (!roomMessages.containsKey(targetRoom)) {
            roomMessages.put(targetRoom, new ArrayList<>());
        }
        roomMessages.get(targetRoom).add(message);
        
        if (message.conversationId != null) {
            localMessageStorage.saveMessage(message, targetRoom);
            localMessageStorage.updateConversation(message.conversationId, targetRoom, message.time, message.content);
        }
        
        if (targetRoom.equals(currentRoom)) {
            displayMessage(message);
        }
        
        refreshListViews();
    }
    
    private void handleSystemMessage(ChatMessage message) {
        String targetRoom = currentRoom;
        
        if (message.conversationId != null) {
            for (Map.Entry<String, Integer> entry : roomConversationIds.entrySet()) {
                if (entry.getValue().equals(message.conversationId)) {
                    targetRoom = entry.getKey();
                    break;
                }
            }
            
            roomConversationIds.put(targetRoom, message.conversationId);
            
            if ("system".equals(targetRoom)) {
                currentConversationId = message.conversationId;
            }
        }
        
        if (targetRoom != null) {
            if (!roomMessages.containsKey(targetRoom)) {
                roomMessages.put(targetRoom, new ArrayList<>());
            }
            roomMessages.get(targetRoom).add(message);
            
            if (message.conversationId != null) {
                localMessageStorage.saveMessage(message, targetRoom);
            }
            
            if (targetRoom.equals(currentRoom)) {
                displayMessage(message);
            }
        }
        
        refreshListViews();
    }
    
    private void handleFriendList(ChatMessage message) {
        try {
            JsonArray friendsArray = gson.fromJson(message.content, JsonArray.class);
            
            friendsList.clear();
            for (JsonElement friendElement : friendsArray) {
                JsonObject friendObj = friendElement.getAsJsonObject();
                String username = null;
                if (friendObj.has("username")) {
                    username = friendObj.get("username").getAsString();
                } else if (friendObj.has("name")) {
                    username = friendObj.get("name").getAsString();
                }
                
                if (username != null) {
                    boolean isOnline = friendObj.has("isOnline") ? friendObj.get("isOnline").getAsBoolean() : false;
                    Friend friend = new Friend(username, isOnline);
                    friendsList.add(friend);
                    
                    if (friendObj.has("conversation_id")) {
                        Integer conversationId = friendObj.get("conversation_id").getAsInt();
                        roomConversationIds.put(username, conversationId);
                    }
                }
            }
            refreshListViews();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleUserStatusUpdate(ChatMessage message) {
        try {
            JsonObject statusObj = gson.fromJson(message.content, JsonObject.class);
            String username = statusObj.get("username").getAsString();
            
            for (Friend friend : friendsList) {
                if (friend.getUsername().equals(username)) {
                    friend.setOnline(statusObj.get("isOnline").getAsBoolean());
                    refreshListViews();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleAllFriendRequests(ChatMessage message) {
        try {
            JsonArray requestsArray = gson.fromJson(message.content, JsonArray.class);
            
            pendingFriendRequests.clear();
            for (JsonElement requestElement : requestsArray) {
                JsonObject requestObj = requestElement.getAsJsonObject();
                
                int id = requestObj.has("id") ? requestObj.get("id").getAsInt() : 0;
                String from = null;
                if (requestObj.has("from_username")) {
                    from = requestObj.get("from_username").getAsString();
                } else if (requestObj.has("from")) {
                    from = requestObj.get("from").getAsString();
                } else if (requestObj.has("username")) {
                    from = requestObj.get("username").getAsString();
                }
                
                String messageText = requestObj.has("message") ? requestObj.get("message").getAsString() : "";
                String createdAt = requestObj.has("createdAt") ? requestObj.get("createdAt").getAsString() : "";
                
                if (from != null && !from.equals(currentUsername)) {
                    pendingFriendRequests.add(new FriendRequest(id, from, messageText, createdAt));
                }
            }
            refreshListViews();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleFriendRequest(ChatMessage message) {
        Platform.runLater(() -> {
            showFriendRequestDialog(message.from);
        });
    }
    
    private void showFriendRequestDialog(String from) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("好友请求");
        alert.setHeaderText("收到好友请求");
        alert.setContentText(from + " 请求添加你为好友，是否同意？");
        
        ButtonType acceptButton = new ButtonType("同意");
        ButtonType rejectButton = new ButtonType("拒绝");
        
        alert.getButtonTypes().setAll(acceptButton, rejectButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == acceptButton) {
                webSocketClient.sendFriendRequestResponse(currentUsername, true, from);
            } else {
                webSocketClient.sendFriendRequestResponse(currentUsername, false, from);
            }
        }
    }
    
    private void handleFriendRequestResponse(ChatMessage message) {
        if ("accepted".equals(message.content)) {
            friendsList.add(new Friend(message.from));
            showInfo("好友添加成功");
        } else if ("rejected".equals(message.content)) {
            showInfo("好友请求被拒绝");
        }
    }
    
    private void handleTokenResponse(ChatMessage message) {
        try {
            String[] parts = message.content.split("\\|");
            if (parts.length >= 2) {
                String token = parts[0];
                String zfileUrl = parts[1];
                
                ZFileService.getInstance().setUploadToken(token);
                ZFileService.getInstance().setZfileServerUrl(zfileUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleServiceConfig(ChatMessage message) {
        try {
            JsonObject config = gson.fromJson(message.content, JsonObject.class);
            if (config.has("zfileServerUrl")) {
                String zfileUrl = config.get("zfileServerUrl").getAsString();
                ZFileService.getInstance().setZfileServerUrl(zfileUrl);
            }
            if (config.has("onlyOfficeApiUrl")) {
                String onlyOfficeUrl = config.get("onlyOfficeApiUrl").getAsString();
                OnlyOfficeService.getInstance().setOnlyOfficeApiUrl(onlyOfficeUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleHistoryResponse(ChatMessage message) {
        try {
            JsonArray messagesArray = gson.fromJson(message.content, JsonArray.class);
            
            if (messagesArray == null || messagesArray.size() == 0) {
                return;
            }
            
            String targetRoom = currentRoom;
            
            if (message.conversationId != null) {
                for (Map.Entry<String, Integer> entry : roomConversationIds.entrySet()) {
                    if (entry.getValue().equals(message.conversationId)) {
                        targetRoom = entry.getKey();
                        break;
                    }
                }
            }
            
            boolean isFirstBatch = !roomMessages.containsKey(targetRoom) || roomMessages.get(targetRoom).isEmpty();
            
            for (JsonElement msgElement : messagesArray) {
                JsonObject msgObj = msgElement.getAsJsonObject();
                
                ChatMessage historyMsg = new ChatMessage();
                historyMsg.type = msgObj.has("type") ? msgObj.get("type").getAsString() : "";
                historyMsg.from = msgObj.has("from") ? msgObj.get("from").getAsString() : "";
                historyMsg.content = msgObj.has("content") ? msgObj.get("content").getAsString() : "";
                historyMsg.time = msgObj.has("time") ? msgObj.get("time").getAsString() : "";
                historyMsg.id = msgObj.has("id") ? msgObj.get("id").getAsString() : "";
                
                if (msgObj.has("conversationId")) {
                    historyMsg.conversationId = msgObj.get("conversationId").getAsInt();
                }
                
                if (!roomMessages.containsKey(targetRoom)) {
                    roomMessages.put(targetRoom, new ArrayList<>());
                }
                
                roomMessages.get(targetRoom).add(historyMsg);
                
                if (historyMsg.conversationId != null) {
                    localMessageStorage.saveMessage(historyMsg, targetRoom);
                }
                
                if (targetRoom.equals(currentRoom)) {
                    displayMessage(historyMsg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static class Room {
        private final String name;
        
        public Room(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    public static class Friend {
        private final String username;
        private boolean online;
        
        public Friend(String username) {
            this.username = username;
            this.online = false;
        }
        
        public Friend(String username, boolean online) {
            this.username = username;
            this.online = online;
        }
        
        public String getUsername() {
            return username;
        }
        
        public boolean isOnline() {
            return online;
        }
        
        public void setOnline(boolean online) {
            this.online = online;
        }
        
        @Override
        public String toString() {
            return username + (online ? " (在线)" : " (离线)");
        }
    }
    
    private static class RoomCell extends ListCell<Room> {
        @Override
        protected void updateItem(Room room, boolean empty) {
            super.updateItem(room, empty);
            if (empty || room == null) {
                setText(null);
            } else {
                setText(room.getName());
            }
        }
    }
    
    private static class FriendCell extends ListCell<Friend> {
        @Override
        protected void updateItem(Friend friend, boolean empty) {
            super.updateItem(friend, empty);
            if (empty || friend == null) {
                setText(null);
            } else {
                setText(friend.getUsername() + (friend.isOnline() ? " (在线)" : " (离线)"));
            }
        }
    }
    
    public static class ListItem {
        private ListItemType type;
        private String name;
        private String displayName;
        private Integer conversationId;
        private String lastMessage;
        private String lastMessageTime;
        private boolean hasNewMessage;
        private boolean isOnline;
        private boolean isPinned;
        
        public ListItem(ListItemType type, String name, String displayName) {
            this.type = type;
            this.name = name;
            this.displayName = displayName;
        }
        
        public ListItemType getType() { return type; }
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public Integer getConversationId() { return conversationId; }
        public void setConversationId(Integer conversationId) { this.conversationId = conversationId; }
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        public String getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(String lastMessageTime) { this.lastMessageTime = lastMessageTime; }
        public boolean hasNewMessage() { return hasNewMessage; }
        public void setHasNewMessage(boolean hasNewMessage) { this.hasNewMessage = hasNewMessage; }
        public boolean isOnline() { return isOnline; }
        public void setOnline(boolean online) { isOnline = online; }
        public boolean isPinned() { return isPinned; }
        public void setPinned(boolean pinned) { isPinned = pinned; }
    }
    
    public static class FriendRequest {
        private int id;
        private String fromUsername;
        private String message;
        private String createdAt;
        
        public FriendRequest(int id, String fromUsername, String message, String createdAt) {
            this.id = id;
            this.fromUsername = fromUsername;
            this.message = message;
            this.createdAt = createdAt;
        }
        
        public int getId() { return id; }
        public String getFromUsername() { return fromUsername; }
        public String getMessage() { return message; }
        public String getCreatedAt() { return createdAt; }
    }
    
    private class ListItemCell extends ListCell<ListItem> {
        @Override
        protected void updateItem(ListItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(null);
                setContextMenu(null);
            } else {
                StringBuilder text = new StringBuilder();
                switch (item.getType()) {
                    case MESSAGE:
                        if (item.isPinned()) {
                            text.append("★ ");
                        }
                        text.append(item.getDisplayName());
                        if (item.getLastMessage() != null) {
                            text.append(" - ").append(item.getLastMessage());
                        }
                        if (item.hasNewMessage()) {
                            text.append(" *");
                        }
                        setContextMenu(createMessageContextMenu(item));
                        break;
                    case CONTACT:
                        text.append(item.getDisplayName());
                        text.append(item.isOnline() ? " (在线)" : " (离线)");
                        setContextMenu(null);
                        break;
                    case CATEGORY:
                        text.append(item.getDisplayName());
                        setText(text.toString());
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
                        setContextMenu(null);
                        return;
                    case FRIEND_REQUEST:
                        text.append("好友请求: ").append(item.getDisplayName());
                        setContextMenu(null);
                        break;
                }
                setText(text.toString());
                setStyle(null);
            }
        }
        
        private ContextMenu createMessageContextMenu(ListItem item) {
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem pinItem = new MenuItem(item.isPinned() ? "取消置顶" : "置顶");
            pinItem.setOnAction(e -> {
                boolean newPinned = !item.isPinned();
                localMessageStorage.setConversationPinned(item.getConversationId(), newPinned);
                item.setPinned(newPinned);
                refreshListViews();
            });
            
            MenuItem hideItem = new MenuItem("隐藏");
            hideItem.setOnAction(e -> {
                localMessageStorage.setConversationHidden(item.getConversationId(), true);
                refreshListViews();
            });
            
            contextMenu.getItems().addAll(pinItem, hideItem);
            return contextMenu;
        }
    }
    
    private void switchToMessagesMode() {
        isMessagesMode = true;
        navMessagesButton.getStyleClass().add("nav-button-active");
        navContactsButton.getStyleClass().remove("nav-button-active");
        contentTitleLabel.setText("消息");
        mainListView.setItems(messagesList);
        contentButtons.setVisible(true);
    }
    
    private void switchToContactsMode() {
        isMessagesMode = false;
        navContactsButton.getStyleClass().add("nav-button-active");
        navMessagesButton.getStyleClass().remove("nav-button-active");
        contentTitleLabel.setText("通讯录");
        updateContactsList();
        mainListView.setItems(contactsList);
        contentButtons.setVisible(true);
    }
    
    private void handleListItemSelection(ListItem item) {
        if (item.getType() == ListItemType.CATEGORY) {
            toggleCategory(item.getName());
            updateContactsList();
        } else if (item.getType() == ListItemType.MESSAGE || item.getType() == ListItemType.CONTACT) {
            if (item.getConversationId() != null) {
                currentConversationId = item.getConversationId();
            }
            currentRoom = item.getName();
            messagesVBox.getChildren().clear();
            
            String lastTime = "0";
            if (currentConversationId != null && localMessageStorage.hasMessages(currentConversationId)) {
                List<ChatMessage> localMsgs = localMessageStorage.getMessagesByConversationId(currentConversationId);
                if (!localMsgs.isEmpty()) {
                    for (ChatMessage msg : localMsgs) {
                        displayMessage(msg);
                    }
                    roomMessages.put(currentRoom, new ArrayList<>(localMsgs));
                    lastTime = localMessageStorage.getLastMessageTime(currentConversationId);
                }
            }
            
            if (item.getType() == ListItemType.MESSAGE) {
                currentChatLabel.setText("聊天: " + item.getDisplayName());
            } else {
                currentChatLabel.setText("聊天: " + item.getDisplayName());
            }
            
            if (currentConversationId != null) {
                webSocketClient.sendJoin(currentUsername, currentRoom, currentConversationId);
                webSocketClient.sendRequestHistory(currentUsername, lastTime, currentConversationId);
            }
        }
    }
    
    private void toggleCategory(String categoryName) {
        if (expandedCategories.contains(categoryName)) {
            expandedCategories.remove(categoryName);
        } else {
            expandedCategories.add(categoryName);
        }
    }
    
    private void updateMessagesList() {
        messagesList.clear();
        
        List<ListItem> pinnedItems = new ArrayList<>();
        List<ListItem> normalItems = new ArrayList<>();
        
        for (Room room : roomsList) {
            Integer convId = roomConversationIds.get(room.getName());
            if (convId != null) {
                if (localMessageStorage.isConversationHidden(convId)) {
                    continue;
                }
                
                ListItem item = new ListItem(ListItemType.MESSAGE, room.getName(), room.getName());
                item.setConversationId(convId);
                item.setPinned(localMessageStorage.isConversationPinned(convId));
                
                if (roomMessages.containsKey(room.getName()) && !roomMessages.get(room.getName()).isEmpty()) {
                    List<ChatMessage> msgs = roomMessages.get(room.getName());
                    ChatMessage lastMsg = msgs.get(msgs.size() - 1);
                    item.setLastMessage(lastMsg.content);
                    item.setLastMessageTime(lastMsg.time);
                }
                
                if (item.isPinned()) {
                    pinnedItems.add(item);
                } else {
                    normalItems.add(item);
                }
            }
        }
        
        for (Friend friend : friendsList) {
            Integer convId = roomConversationIds.get(friend.getUsername());
            if (convId != null) {
                if (localMessageStorage.isConversationHidden(convId)) {
                    continue;
                }
                
                ListItem item = new ListItem(ListItemType.MESSAGE, friend.getUsername(), friend.getUsername());
                item.setConversationId(convId);
                item.setOnline(friend.isOnline());
                item.setPinned(localMessageStorage.isConversationPinned(convId));
                
                if (roomMessages.containsKey(friend.getUsername()) && !roomMessages.get(friend.getUsername()).isEmpty()) {
                    List<ChatMessage> msgs = roomMessages.get(friend.getUsername());
                    ChatMessage lastMsg = msgs.get(msgs.size() - 1);
                    item.setLastMessage(lastMsg.content);
                    item.setLastMessageTime(lastMsg.time);
                }
                
                if (item.isPinned()) {
                    pinnedItems.add(item);
                } else {
                    normalItems.add(item);
                }
            }
        }
        
        pinnedItems.sort((a, b) -> {
            if (b.getLastMessageTime() != null && a.getLastMessageTime() != null) {
                return b.getLastMessageTime().compareTo(a.getLastMessageTime());
            }
            return 0;
        });
        
        normalItems.sort((a, b) -> {
            if (b.getLastMessageTime() != null && a.getLastMessageTime() != null) {
                return b.getLastMessageTime().compareTo(a.getLastMessageTime());
            }
            return 0;
        });
        
        messagesList.addAll(pinnedItems);
        messagesList.addAll(normalItems);
    }
    
    private void updateContactsList() {
        contactsList.clear();
        
        ListItem friendsCategory = new ListItem(ListItemType.CATEGORY, "friends", "好友");
        contactsList.add(friendsCategory);
        
        if (expandedCategories.contains("friends")) {
            for (Friend friend : friendsList) {
                Integer convId = roomConversationIds.get(friend.getUsername());
                ListItem item = new ListItem(ListItemType.CONTACT, friend.getUsername(), friend.getUsername());
                item.setConversationId(convId);
                item.setOnline(friend.isOnline());
                contactsList.add(item);
            }
        }
        
        ListItem roomsCategory = new ListItem(ListItemType.CATEGORY, "rooms", "房间");
        contactsList.add(roomsCategory);
        
        if (expandedCategories.contains("rooms")) {
            for (Room room : roomsList) {
                Integer convId = roomConversationIds.get(room.getName());
                ListItem item = new ListItem(ListItemType.CONTACT, room.getName(), room.getName());
                item.setConversationId(convId);
                contactsList.add(item);
            }
        }
        
        ListItem requestsCategory = new ListItem(ListItemType.CATEGORY, "requests", "好友请求");
        contactsList.add(requestsCategory);
        
        if (expandedCategories.contains("requests")) {
            for (FriendRequest request : pendingFriendRequests) {
                ListItem item = new ListItem(ListItemType.FRIEND_REQUEST, String.valueOf(request.getId()), request.getFromUsername());
                contactsList.add(item);
            }
        }
    }
    
    private void refreshListViews() {
        updateMessagesList();
        updateContactsList();
    }
}