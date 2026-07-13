package client.javafx.controller;

import client.javafx.MainApp;
import client.javafx.network.ChatWebSocketClient;
import client.javafx.network.ZFileService;
import client.javafx.network.OnlyOfficeService;
import client.javafx.protocol.ChatMessage;
import client.javafx.protocol.MessageType;
import client.javafx.util.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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
    private StackPane avatarContainer;
    
    private Stage profilePopupStage;
    private Label profileUsername;
    private Label profileStatus;
    
    private boolean isWindowFocused = true;
    
    @FXML
    private Button navMessagesButton;
    
    @FXML
    private Button navContactsButton;
    
    @FXML
    private ListView<ListItem> mainListView;
    
    @FXML
    private TextField searchField;
    
    @FXML
    private Button addButton;
    
    @FXML
    private Button emojiButton;
    
    @FXML
    private Button uploadImageButton;
    
    @FXML
    private Button uploadFileButton;
    
    @FXML
    private Button voiceButton;
    
    @FXML
    private Label currentChatLabel;
    
    @FXML
    private TextField messageInput;
    
    @FXML
    private Button sendButton;
    
    @FXML
    private Button membersButton;
    
    @FXML
    private VBox messagesVBox;
    
    @FXML
    private SplitPane mainSplitPane;
    
    @FXML
    private VBox profileSidebar;
    
    @FXML
    private VBox chatContent;
    
    @FXML
    private VBox emptyState;
    
    private boolean isSidebarVisible = false;
    
    private ChatWebSocketClient webSocketClient;
    private String currentUsername;
    private String currentRoom = null;
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
    private Map<String, Image> avatarCache = new HashMap<>();
    private Set<String> loadingAvatars = new HashSet<>();
    private final Logger logger = new Logger(ChatController.class);
    
    private Map<String, String> lastMessageSender = new HashMap<>();
    private Map<String, String> lastMessageTime = new HashMap<>();
    private static final long TIME_INTERVAL_MS = 3 * 60 * 1000;
    
    private String currentRoomAnnouncement = null;
    private HBox announcementBar = null;
    
    private boolean isRecording = false;
    private Thread recordingThread;
    private long recordingStartTime;
    private File tempVoiceFile;
    
    private Map<String, javafx.scene.media.MediaPlayer> voicePlayers = new HashMap<>();
    
    public enum ListItemType { MESSAGE, CONTACT, CATEGORY, FRIEND_REQUEST }
    
    @FXML
    public void initialize() {
        try {
            logger.info("ChatController初始化开始");
            currentUsername = LoginController.getCurrentUsername();
            logger.info("当前用户名: " + currentUsername);
            webSocketClient = ConnectController.getWebSocketClient();
            logger.debug("WebSocket客户端: " + (webSocketClient != null ? "已连接" : "null"));
            localMessageStorage = new client.javafx.storage.LocalMessageStorage(currentUsername);
            logger.debug("本地消息存储初始化完成");
            
            loadUserAvatar();
            logger.debug("用户头像加载完成");
            setupNavigationIcons();
            logger.debug("导航图标设置完成");
        
        avatarContainer.setOnMouseClicked(e -> toggleProfilePopup());
        
        mainListView.setCellFactory(param -> new ListItemCell());
        
        mainListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ListItem selectedItem = mainListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && (selectedItem.getType() == ListItemType.MESSAGE || selectedItem.getType() == ListItemType.CONTACT)) {
                    openIndependentChatWindow(selectedItem);
                }
            } else if (e.getClickCount() == 1) {
                ListItem selectedItem = mainListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    handleListItemSelection(selectedItem);
                }
            }
        });
        
        navMessagesButton.setOnAction(e -> switchToMessagesMode());
        navContactsButton.setOnAction(e -> switchToContactsMode());
        
        sendButton.setOnAction(e -> sendMessage());
        messageInput.setOnAction(e -> sendMessage());
        
        uploadImageButton.setOnAction(e -> uploadImage());
        uploadFileButton.setOnAction(e -> uploadFile());
        emojiButton.setOnAction(e -> showEmojiPicker());
        voiceButton.setOnAction(e -> toggleVoiceRecording());
        membersButton.setOnAction(e -> toggleProfileSidebar());
        
        setupListViewHeader();
        
        webSocketClient.setOnMessageCallback(this::handleMessage);
        
        loadConversationsFromStorage();
        
        updateMessagesList();
        
        webSocketClient.sendListRooms(currentUsername);
        webSocketClient.sendRequestFriendList(currentUsername);
        webSocketClient.sendRequestAllFriendRequests(currentUsername);
        webSocketClient.sendRequestToken(currentUsername);
        
        Platform.runLater(() -> {
            if (userAvatar != null && userAvatar.getScene() != null) {
                Stage stage = (Stage) userAvatar.getScene().getWindow();
                stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    isWindowFocused = isFocused;
                });
            }
            
            if (mainSplitPane != null) {
                mainSplitPane.setDividerPositions(1.0);
            }
            if (mainListView != null) {
                mainListView.getSelectionModel().clearSelection();
            }
        });
        currentChatLabel.setText("");
        
        chatContent.setVisible(false);
        chatContent.setManaged(false);
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        logger.info("ChatController初始化完成");
        } catch (Exception e) {
            logger.error("ChatController初始化失败", e);
        }
    }
    
    private void toggleProfilePopup() {
        logger.debug("toggleProfilePopup called, current stage: " + (profilePopupStage != null && profilePopupStage.isShowing()));
        
        if (profilePopupStage != null && profilePopupStage.isShowing()) {
            profilePopupStage.close();
            profilePopupStage = null;
            return;
        }
        
        if (avatarContainer.getScene() == null) {
            logger.warn("avatarContainer scene is null, cannot show profile popup");
            return;
        }
        
        createProfilePopupStage();
    }
    
    private void createProfilePopupStage() {
        logger.debug("Creating profile popup stage");
        
        VBox popupContent = new VBox();
        popupContent.setAlignment(Pos.CENTER);
        popupContent.setSpacing(10);
        popupContent.setPadding(new Insets(15));
        popupContent.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 12px;" +
            "-fx-background-radius: 12px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 4);"
        );
        
        profileUsername = new Label(currentUsername);
        profileUsername.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        
        profileStatus = new Label("在线");
        profileStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        
        Button editBtn = new Button("编辑资料");
        editBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #2563eb;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 6px 16px;"
        );
        editBtn.setOnAction(e -> {
            logger.debug("Edit profile button clicked");
            if (profilePopupStage != null) {
                profilePopupStage.close();
                profilePopupStage = null;
            }
            openProfileWindow();
        });
        
        Button logoutBtn = new Button("退出登录");
        logoutBtn.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: #fee2e2;" +
            "-fx-text-fill: #ef4444;" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-padding: 6px 16px;"
        );
        logoutBtn.setOnAction(e -> {
            if (profilePopupStage != null) {
                profilePopupStage.close();
                profilePopupStage = null;
            }
            logout();
        });
        
        popupContent.getChildren().addAll(profileUsername, profileStatus, editBtn, logoutBtn);
        
        Scene scene = new Scene(popupContent);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        
        profilePopupStage = new Stage();
        profilePopupStage.setScene(scene);
        profilePopupStage.initOwner(avatarContainer.getScene().getWindow());
        profilePopupStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        profilePopupStage.setAlwaysOnTop(true);
        profilePopupStage.setResizable(false);
        
        javafx.geometry.Bounds avatarBounds = avatarContainer.localToScreen(avatarContainer.getBoundsInLocal());
        logger.debug("Avatar bounds: minX=" + avatarBounds.getMinX() + ", minY=" + avatarBounds.getMinY() + ", width=" + avatarBounds.getWidth() + ", height=" + avatarBounds.getHeight());
        
        double popupWidth = 180;
        double popupHeight = 120;
        double gap = 4;
        
        double stageX = avatarBounds.getMinX() - popupWidth - gap;
        double stageY = avatarBounds.getMinY() - (popupHeight - avatarBounds.getHeight()) / 2;
        
        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D screenBounds = screen.getVisualBounds();
        
        javafx.stage.Window mainWindow = userAvatar.getScene().getWindow();
        double windowX = mainWindow.getX();
        double windowY = mainWindow.getY();
        double windowWidth = mainWindow.getWidth();
        double windowHeight = mainWindow.getHeight();
        
        boolean placedAbove = false;
        
        if (stageX < windowX) {
            stageX = avatarBounds.getMaxX() + gap;
        }
        
        if (stageX + popupWidth > windowX + windowWidth) {
            stageX = windowX + windowWidth - popupWidth;
        }
        
        if (stageY < windowY) {
            stageY = avatarBounds.getMaxY() + gap;
            placedAbove = false;
        } else if (stageY + popupHeight > windowY + windowHeight) {
            stageY = avatarBounds.getMinY() - popupHeight - gap;
            placedAbove = true;
        }
        
        if (stageX < screenBounds.getMinX()) {
            stageX = screenBounds.getMinX();
        }
        
        if (stageY < screenBounds.getMinY()) {
            stageY = screenBounds.getMinY();
        }
        
        if (stageX + popupWidth > screenBounds.getMaxX()) {
            stageX = screenBounds.getMaxX() - popupWidth;
        }
        
        if (stageY + popupHeight > screenBounds.getMaxY()) {
            stageY = screenBounds.getMaxY() - popupHeight;
        }
        
        logger.debug("Popup position: x=" + stageX + ", y=" + stageY + ", gap=" + gap + ", placedAbove=" + placedAbove + ", window bounds: " + windowX + "," + windowY + "," + windowWidth + "," + windowHeight);
        
        profilePopupStage.setX(stageX);
        profilePopupStage.setY(stageY);
        
        profilePopupStage.show();
        logger.debug("Profile popup stage shown");
        
        profilePopupStage.setOnHidden(e -> {
            profilePopupStage = null;
            logger.debug("Profile popup stage hidden");
        });
        
        setupCloseProfilePopupOnOutsideClick();
    }
    
    private void closeProfilePopup() {
        if (profilePopupStage != null) {
            profilePopupStage.close();
            profilePopupStage = null;
        }
    }
    
    private void setupCloseProfilePopupOnOutsideClick() {
        Scene scene = avatarContainer.getScene();
        if (scene == null) {
            logger.warn("setupCloseProfilePopupOnOutsideClick: avatarContainer scene is null");
            return;
        }
        
        javafx.stage.Window window = scene.getWindow();
        if (window == null) {
            logger.warn("setupCloseProfilePopupOnOutsideClick: avatarContainer scene window is null");
            return;
        }
        
        window.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
            if (profilePopupStage == null || !profilePopupStage.isShowing()) {
                return;
            }
            
            javafx.geometry.Bounds avatarBounds = avatarContainer.localToScreen(avatarContainer.getBoundsInLocal());
            double mouseX = event.getScreenX();
            double mouseY = event.getScreenY();
            
            boolean clickedOnAvatar = mouseX >= avatarBounds.getMinX() && mouseX <= avatarBounds.getMaxX() &&
                                      mouseY >= avatarBounds.getMinY() && mouseY <= avatarBounds.getMaxY();
            
            javafx.geometry.Bounds popupBounds = profilePopupStage.getScene().getRoot().localToScreen(
                profilePopupStage.getScene().getRoot().getBoundsInLocal()
            );
            boolean clickedOnPopup = mouseX >= popupBounds.getMinX() && mouseX <= popupBounds.getMaxX() &&
                                     mouseY >= popupBounds.getMinY() && mouseY <= popupBounds.getMaxY();
            
            if (!clickedOnAvatar && !clickedOnPopup) {
                profilePopupStage.close();
                profilePopupStage = null;
            }
        });
    }
    
    private void openProfileWindow() {
        logger.info("openProfileWindow called");
        closeProfilePopup();
        
        try {
            logger.debug("Loading profile.fxml");
            java.net.URL resourceUrl = getClass().getResource("/client/javafx/profile.fxml");
            logger.debug("Resource URL: " + resourceUrl);
            
            if (resourceUrl == null) {
                logger.error("profile.fxml not found in resources!");
                showError("打开个人资料窗口失败：资源文件未找到");
                return;
            }
            
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(resourceUrl);
            logger.debug("FXMLLoader created with location: " + loader.getLocation());
            
            javafx.scene.layout.VBox root = loader.load();
            logger.debug("profile.fxml loaded successfully, root: " + root);
            
            ProfileController controller = loader.getController();
            logger.debug("ProfileController obtained: " + controller);
            
            String avatarPath = LoginController.getCurrentUserAvatar();
            logger.debug("Current user avatar path: " + (avatarPath != null ? avatarPath : "null"));
            
            controller.setUserData(currentUsername, avatarPath);
            logger.debug("UserData set: username=" + currentUsername);
            
            Stage profileStage = new Stage();
            profileStage.setTitle("个人资料");
            profileStage.setScene(new Scene(root, 400, 500));
            profileStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            profileStage.initOwner(avatarContainer.getScene().getWindow());
            profileStage.show();
            logger.info("Profile window shown successfully");
        } catch (IOException e) {
            logger.error("Failed to load profile.fxml", e);
            e.printStackTrace();
            showError("打开个人资料窗口失败：" + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error opening profile window", e);
            e.printStackTrace();
            showError("打开个人资料窗口失败：" + e.getMessage());
        }
    }
    
    private void handleRoomSelection(String roomName) {
        currentRoom = roomName;
        currentConversationId = roomConversationIds.get(currentRoom);
        
        messagesVBox.getChildren().clear();
        currentRoomAnnouncement = null;
        announcementBar = null;
        resetLastMessageInfo(roomName);
        
        if (currentConversationId != null) {
            localMessageStorage.clearUnreadCount(currentConversationId);
        }
        
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
        
        String displayName = currentRoom;
        if (currentConversationId != null) {
            String roomNote = localMessageStorage.getRoomNote(currentConversationId);
            if (roomNote != null && !roomNote.isEmpty()) {
                displayName = roomNote;
            }
        }
        currentChatLabel.setText("当前房间: " + displayName);
        
        if (currentConversationId != null) {
            webSocketClient.sendRequestHistory(currentUsername, lastTime, currentConversationId);
        }
        
        refreshListViews();
    }
    
    private void handleFriendSelection(String friendName) {
        currentRoom = friendName;
        currentConversationId = roomConversationIds.get(friendName);
        
        messagesVBox.getChildren().clear();
        currentRoomAnnouncement = null;
        hideRoomAnnouncement();
        resetLastMessageInfo(friendName);
        
        if (currentConversationId != null) {
            localMessageStorage.clearUnreadCount(currentConversationId);
        }
        
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
        
        if (currentRoom == null) {
            showError("无法发送消息：未选择有效的聊天对象");
            return;
        }
        
        if (currentConversationId == null) {
            if (roomConversationIds.containsKey(currentRoom)) {
                currentConversationId = roomConversationIds.get(currentRoom);
            } else {
                showError("无法发送消息：未选择有效的聊天对象");
                return;
            }
        }
        
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
            String avatarPath = LoginController.getCurrentUserAvatar();
            
            Image defaultAvatar = createDefaultAvatar(currentUsername);
            userAvatar.setImage(defaultAvatar);
            userAvatar.setClip(new Circle(24, 24, 24));
            
            if (avatarPath != null && !avatarPath.isEmpty()) {
                new Thread(() -> {
                    try {
                        byte[] avatarData = ZFileService.getInstance().downloadFileWithCache(avatarPath);
                        Image avatarImage = new Image(new java.io.ByteArrayInputStream(avatarData));
                        avatarCache.put(currentUsername, avatarImage);
                        
                        Platform.runLater(() -> {
                            userAvatar.setImage(avatarImage);
                            userAvatar.setClip(new Circle(24, 24, 24));
                        });
                    } catch (Exception e) {
                        System.err.println("加载用户头像失败: " + e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            userAvatar.setImage(createDefaultAvatar(currentUsername));
            userAvatar.setClip(new Circle(24, 24, 24));
        }
    }
    
    private Image createDefaultAvatar(String username) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(50, 50);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        
        int hue = username.hashCode() % 360;
        if (hue < 0) hue += 360;
        
        gc.setFill(javafx.scene.paint.Color.hsb(hue, 0.6, 0.85));
        gc.fillOval(0, 0, 50, 50);
        
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.setFont(new Font("System", 24));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        
        String initial = username.length() > 0 ? username.substring(0, 1).toUpperCase() : "?";
        gc.fillText(initial, 25, 33);
        
        return canvas.snapshot(null, null);
    }
    
    private void loadConversationsFromStorage() {
        List<client.javafx.storage.LocalMessageStorage.ConversationInfo> conversations = 
            localMessageStorage.getConversations();
        
        for (client.javafx.storage.LocalMessageStorage.ConversationInfo conv : conversations) {
            if (!roomConversationIds.containsKey(conv.conversationName)) {
                roomConversationIds.put(conv.conversationName, conv.conversationId);
            }
            
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
    
    private void setupListViewHeader() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> handleSearch(newVal));
        
        addButton.setGraphic(createAddIcon());
        
        ContextMenu addMenu = new ContextMenu();
        MenuItem createRoomItem = new MenuItem("创建房间");
        createRoomItem.setOnAction(e -> createRoom());
        MenuItem joinRoomItem = new MenuItem("加入房间");
        joinRoomItem.setOnAction(e -> joinRoom());
        MenuItem addFriendItem = new MenuItem("添加好友");
        addFriendItem.setOnAction(e -> addFriend());
        
        addMenu.getItems().addAll(createRoomItem, joinRoomItem, addFriendItem);
        addButton.setOnAction(e -> addMenu.show(addButton, javafx.geometry.Side.BOTTOM, 0, 0));
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
    
    private javafx.scene.shape.SVGPath createAddIcon() {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z");
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
                    boolean isPrivateChat = friendsList.stream().anyMatch(f -> f.getUsername().equals(currentRoom));
                    String chatType;
                    String privateChatRecipient = null;
                    
                    if (isPrivateChat) {
                        chatType = ZFileService.CHAT_TYPE_PRIVATE_CHAT;
                        privateChatRecipient = currentRoom;
                    } else {
                        chatType = ZFileService.CHAT_TYPE_PUBLIC_ROOM;
                    }
                    
                    String relativePath = ZFileService.getInstance().uploadImage(selectedFile, currentRoom, chatType, privateChatRecipient);
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
                    boolean isPrivateChat = friendsList.stream().anyMatch(f -> f.getUsername().equals(currentRoom));
                    String chatType;
                    String privateChatRecipient = null;
                    
                    if (isPrivateChat) {
                        chatType = ZFileService.CHAT_TYPE_PRIVATE_CHAT;
                        privateChatRecipient = currentRoom;
                    } else {
                        chatType = ZFileService.CHAT_TYPE_PUBLIC_ROOM;
                    }
                    
                    String relativePath = ZFileService.getInstance().uploadFile(selectedFile, currentRoom, chatType, privateChatRecipient);
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
    
    private void toggleVoiceRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }
    
    private void startRecording() {
        if (currentRoom == null || currentConversationId == null) {
            showError("请先选择聊天对象");
            return;
        }
        
        isRecording = true;
        voiceButton.setText("⏹️");
        voiceButton.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 5 10; -fx-border-radius: 4;");
        
        recordingStartTime = System.currentTimeMillis();
        
        recordingThread = new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    16,
                    1,
                    2,
                    16000,
                    false
                );
                
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    javax.sound.sampled.TargetDataLine.class, format
                );
                
                if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                    Platform.runLater(() -> {
                        showError("不支持音频录制");
                        isRecording = false;
                        voiceButton.setText("🎤");
                        voiceButton.setStyle("");
                    });
                    return;
                }
                
                javax.sound.sampled.TargetDataLine line = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                line.open(format);
                line.start();
                
                tempVoiceFile = File.createTempFile("voice_", ".wav");
                javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(line);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempVoiceFile)) {
                    while (isRecording && (bytesRead = ais.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                
                line.stop();
                line.close();
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("录音失败: " + e.getMessage());
                    isRecording = false;
                    voiceButton.setText("🎤");
                    voiceButton.setStyle("");
                });
            }
        });
        
        recordingThread.start();
    }
    
    private void stopRecording() {
        isRecording = false;
        
        if (recordingThread != null) {
            try {
                recordingThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
        
        voiceButton.setText("🎤");
        voiceButton.setStyle("");
        
        if (tempVoiceFile != null && tempVoiceFile.exists()) {
            if (duration < 1) {
                showError("录音时间太短");
                tempVoiceFile.delete();
                return;
            }
            
            new Thread(() -> {
                try {
                    boolean isPrivateChat = friendsList.stream().anyMatch(f -> f.getUsername().equals(currentRoom));
                    String chatType;
                    String privateChatRecipient = null;
                    
                    if (isPrivateChat) {
                        chatType = ZFileService.CHAT_TYPE_PRIVATE_CHAT;
                        privateChatRecipient = currentRoom;
                    } else {
                        chatType = ZFileService.CHAT_TYPE_PUBLIC_ROOM;
                    }
                    
                    String relativePath = ZFileService.getInstance().uploadVoice(tempVoiceFile, currentRoom, chatType, privateChatRecipient);
                    webSocketClient.sendVoice(currentUsername, relativePath, tempVoiceFile.getName(), (int) duration, currentConversationId);
                    
                    Platform.runLater(() -> {
                        appendOwnVoice(relativePath, tempVoiceFile.getName(), (int) duration);
                    });
                    
                    tempVoiceFile.delete();
                } catch (Exception e) {
                    Platform.runLater(() -> showError("语音发送失败: " + e.getMessage()));
                    if (tempVoiceFile != null) {
                        tempVoiceFile.delete();
                    }
                }
            }).start();
        }
    }
    
    private void playVoice(String voiceUrl, String messageId) {
        javafx.scene.media.MediaPlayer player = voicePlayers.get(messageId);
        if (player != null) {
            player.stop();
            voicePlayers.remove(messageId);
            return;
        }
        
        try {
            String previewUrl = ZFileService.getInstance().getFilePreviewUrl(voiceUrl);
            if (previewUrl == null) {
                showError("无法播放语音");
                return;
            }
            
            javafx.scene.media.Media media = new javafx.scene.media.Media(previewUrl);
            player = new javafx.scene.media.MediaPlayer(media);
            
            player.setOnEndOfMedia(() -> {
                voicePlayers.remove(messageId);
            });
            
            player.setOnError(() -> {
                voicePlayers.remove(messageId);
                showError("播放失败");
            });
            
            voicePlayers.put(messageId, player);
            player.play();
            
        } catch (Exception e) {
            showError("播放语音失败: " + e.getMessage());
        }
    }
    
    private Stage emojiStage = null;
    
    private void showEmojiPicker() {
        if (emojiStage != null && emojiStage.isShowing()) {
            emojiStage.close();
            emojiStage = null;
            return;
        }
        
        List<String> emojis = Arrays.asList(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗",
            "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝",
            "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐",
            "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌",
            "😍", "🥰", "😘", "😗", "😙", "😚", "🙂", "😊",
            "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵",
            "🥶", "😱", "😨", "😰", "😥", "😢", "😭", "😓",
            "🤗", "🤔", "🤭", "🤫", "🤥", "😌", "😪", "🤤",
            "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵",
            "👍", "👎", "👏", "🙌", "👊", "✊", "🤛", "🤜",
            "🤞", "✌️", "🤟", "🤘", "👌", "👈", "👉", "👆",
            "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤙",
            "💪", "🦾", "🦵", "🦿", "🦶", "👄", "👅", "👂",
            "👃", "👁️", "👀", "🧠", "🫀", "🫁", "🦷", "🦴",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
            "💘", "💝", "💟", "💕", "💞", "💓", "💗", "💖",
            "🎉", "🎊", "🎁", "🎈", "🎂", "🍰", "🎂", "🍾",
            "🎀", "🏆", "🎯", "🎲", "🎰", "🎳", "🎮", "🎲",
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
            "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐞", "🦋",
            "🌹", "🌺", "🌸", "🌻", "🌷", "🌱", "🌿", "🍀",
            "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍑",
            "🍒", "🍍", "🥝", "🍅", "🥑", "🌽", "🥕", "🥦",
            "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥙",
            "🍳", "🥚", "🍿", "🧂", "🥨", "🧀", "🍞", "🥖",
            "🍱", "🍘", "🍙", "🍚", "🍛", "🍜", "🍝", "🍠",
            "🍢", "🍣", "🍤", "🍥", "🍡", "🍦", "🍧", "🍨",
            "🍩", "🍪", "🎂", "🍰", "🍫", "🍬", "🍭", "🍮",
            "☕", "🍵", "🍶", "🍷", "🍸", "🍹", "🥤", "🧃",
            "🍼", "🥛", "🍶", "🍾", "🧉", "🍵", "🫖", "☕",
            "🚗", "🚕", "🚙", "🚌", "🏎️", "🚓", "🚑", "🚒",
            "🚐", "🚚", "🚛", "🚜", "🦯", "🦽", "🦼", "🛴",
            "🚲", "🛵", "🏍️", "🚨", "🚔", "🚍", "🚘", "🚖",
            "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄",
            "🚅", "🚆", "🚇", "🚈", "🚉", "🚊", "🚝", "🚞",
            "✈️", "🛫", "🛬", "🚀", "🛸", "🚁", "🛳️", "⛵",
            "🚢", "🚤", "🛥️", "🛩️", "🛰️", "🚀", "🚁", "🛳️",
            "🌍", "🌎", "🌏", "🌐", "🗺️", "🏔️", "⛰️", "🏕️",
            "🏖️", "🏜️", "🏝️", "🏞️", "🏟️", "🏛️", "🏗️", "🏘️",
            "🏙️", "🏚️", "🏠", "🏡", "🏢", "🏣", "🏤", "🏥",
            "🏦", "🏨", "🏩", "🏪", "🏫", "🏬", "🏭", "🏯",
            "🏰", "💒", "🕍", "⛩️", "🛕", "🕋", "🕌", "🕋",
            "🇨🇳", "🇺🇸", "🇯🇵", "🇰🇷", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹",
            "🇪🇸", "🇵🇹", "🇵🇱", "🇷🇺", "🇨🇦", "🇦🇺", "🇳🇿", "🇮🇳"
        );
        
        emojiStage = new Stage();
        emojiStage.initStyle(StageStyle.UNDECORATED);
        emojiStage.setTitle("选择表情");
        
        GridPane gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(10));
        gridPane.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 4);"
        );
        
        int col = 0;
        int row = 0;
        for (String emoji : emojis) {
            Button emojiBtn = new Button(emoji);
            emojiBtn.setFont(new Font("System", 20));
            emojiBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5;");
            emojiBtn.setOnAction(e -> {
                insertEmojiToInput(emoji);
                emojiStage.close();
                emojiStage = null;
            });
            gridPane.add(emojiBtn, col, row);
            col++;
            if (col >= 10) {
                col = 0;
                row++;
            }
        }
        
        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        
        Scene scene = new Scene(scrollPane, 350, 200);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        emojiStage.setScene(scene);
        emojiStage.setAlwaysOnTop(true);
        
        double buttonX = emojiButton.localToScreen(0, 0).getX();
        double buttonY = emojiButton.localToScreen(0, 0).getY();
        double buttonHeight = emojiButton.getHeight();
        double popupHeight = 200;
        
        double popupY = buttonY - popupHeight - 5;
        if (popupY < 0) {
            popupY = buttonY + buttonHeight + 5;
        }
        
        emojiStage.setX(buttonX);
        emojiStage.setY(popupY);
        
        emojiStage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                emojiStage.close();
                emojiStage = null;
            }
        });
        
        emojiStage.show();
    }
    
    private void insertEmojiToInput(String emoji) {
        String currentText = messageInput.getText();
        messageInput.setText(currentText + emoji);
        messageInput.requestFocus();
    }
    
    private void appendOwnEmoji(String emoji) {
        String roomName = currentRoom;
        String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (shouldShowTimeDivider(roomName, currentTime)) {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setPadding(new Insets(10, 0, 5, 0));
            
            Label timeLabel = new Label(formatTimeDivider(currentTime));
            timeLabel.setFont(new Font("System", 11));
            timeLabel.setTextFill(Color.web("#94a3b8"));
            timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-padding: 3 10; -fx-background-radius: 10;");
            
            timeDivider.getChildren().add(timeLabel);
            messagesVBox.getChildren().add(timeDivider);
        }
        
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(2, 10, 2, 10));
        messageBox.setSpacing(8);
        
        ImageView avatarView = createAvatar(currentUsername, true);
        
        Label emojiLabel = new Label(emoji);
        emojiLabel.setFont(new Font("System", 32));
        
        messageBox.getChildren().addAll(emojiLabel, avatarView);
        
        messagesVBox.getChildren().add(messageBox);
        updateLastMessageInfo(roomName, currentUsername, currentTime);
        scrollToBottom();
    }
    
    private void appendOwnMessage(String content) {
        String roomName = currentRoom;
        String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (shouldShowTimeDivider(roomName, currentTime)) {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setPadding(new Insets(10, 0, 5, 0));
            
            Label timeLabel = new Label(formatTimeDivider(currentTime));
            timeLabel.setFont(new Font("System", 11));
            timeLabel.setTextFill(Color.web("#94a3b8"));
            timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-padding: 3 10; -fx-background-radius: 10;");
            
            timeDivider.getChildren().add(timeLabel);
            messagesVBox.getChildren().add(timeDivider);
        }
        
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(2, 10, 2, 10));
        messageBox.setSpacing(8);
        
        ImageView avatarView = createAvatar(currentUsername, true);
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        contentBox.setMaxWidth(300);
        
        Text messageText = new Text(content);
        messageText.setFill(Color.WHITE);
        messageText.setFont(new Font("System", 14));
        
        contentBox.getChildren().add(messageText);
        messageBox.getChildren().addAll(contentBox, avatarView);
        
        messagesVBox.getChildren().add(messageBox);
        updateLastMessageInfo(roomName, currentUsername, currentTime);
        scrollToBottom();
    }
    
    private void appendOwnImage(String relativePath) {
        String roomName = currentRoom;
        String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (shouldShowTimeDivider(roomName, currentTime)) {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setPadding(new Insets(10, 0, 5, 0));
            
            Label timeLabel = new Label(formatTimeDivider(currentTime));
            timeLabel.setFont(new Font("System", 11));
            timeLabel.setTextFill(Color.web("#94a3b8"));
            timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-padding: 3 10; -fx-background-radius: 10;");
            
            timeDivider.getChildren().add(timeLabel);
            messagesVBox.getChildren().add(timeDivider);
        }
        
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(2, 10, 2, 10));
        messageBox.setSpacing(8);
        
        ImageView avatarView = createAvatar(currentUsername, true);
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        
        String previewUrl = ZFileService.getInstance().getFilePreviewUrl(relativePath);
        ImageView imageView = new ImageView();
        imageView.setFitWidth(200);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);
        imageView.setOnMouseClicked(e -> previewImage(previewUrl));
        
        if (previewUrl != null) {
            imageView.setImage(new Image(previewUrl));
        }
        
        contentBox.getChildren().add(imageView);
        messageBox.getChildren().addAll(contentBox, avatarView);
        
        messagesVBox.getChildren().add(messageBox);
        updateLastMessageInfo(roomName, currentUsername, currentTime);
        scrollToBottom();
    }
    
    private void appendOwnFile(String relativePath, String fileName) {
        String roomName = currentRoom;
        String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (shouldShowTimeDivider(roomName, currentTime)) {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setPadding(new Insets(10, 0, 5, 0));
            
            Label timeLabel = new Label(formatTimeDivider(currentTime));
            timeLabel.setFont(new Font("System", 11));
            timeLabel.setTextFill(Color.web("#94a3b8"));
            timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-padding: 3 10; -fx-background-radius: 10;");
            
            timeDivider.getChildren().add(timeLabel);
            messagesVBox.getChildren().add(timeDivider);
        }
        
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(2, 10, 2, 10));
        messageBox.setSpacing(8);
        
        ImageView avatarView = createAvatar(currentUsername, true);
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        
        Button fileButton = new Button(fileName);
        fileButton.setTextFill(Color.WHITE);
        fileButton.setFont(new Font("System", 14));
        fileButton.setBackground(null);
        fileButton.setOnAction(e -> downloadFile(relativePath, fileName));
        
        contentBox.getChildren().add(fileButton);
        messageBox.getChildren().addAll(contentBox, avatarView);
        
        messagesVBox.getChildren().add(messageBox);
        updateLastMessageInfo(roomName, currentUsername, currentTime);
        scrollToBottom();
    }
    
    private void appendOwnVoice(String relativePath, String fileName, int duration) {
        String roomName = currentRoom;
        String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        if (shouldShowTimeDivider(roomName, currentTime)) {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setPadding(new Insets(10, 0, 5, 0));
            
            Label timeLabel = new Label(formatTimeDivider(currentTime));
            timeLabel.setFont(new Font("System", 11));
            timeLabel.setTextFill(Color.web("#94a3b8"));
            timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-padding: 3 10; -fx-background-radius: 10;");
            
            timeDivider.getChildren().add(timeLabel);
            messagesVBox.getChildren().add(timeDivider);
        }
        
        HBox messageBox = new HBox();
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(2, 10, 2, 10));
        messageBox.setSpacing(8);
        
        ImageView avatarView = createAvatar(currentUsername, true);
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        
        HBox voiceBox = new HBox();
        voiceBox.setSpacing(8);
        
        Button playButton = new Button("🔊");
        playButton.setFont(new Font("System", 18));
        playButton.setBackground(null);
        playButton.setTextFill(Color.WHITE);
        playButton.setOnAction(e -> playVoice(relativePath, "own_" + System.currentTimeMillis()));
        
        Label durationLabel = new Label(formatDuration(duration));
        durationLabel.setFont(new Font("System", 12));
        durationLabel.setTextFill(Color.WHITE);
        
        voiceBox.getChildren().addAll(playButton, durationLabel);
        contentBox.getChildren().add(voiceBox);
        messageBox.getChildren().addAll(contentBox, avatarView);
        
        messagesVBox.getChildren().add(messageBox);
        updateLastMessageInfo(roomName, currentUsername, currentTime);
        scrollToBottom();
    }
    
    private String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "\"";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + "'" + secs + "\"";
    }
    
    private void displayMessage(ChatMessage message) {
        String roomName = currentRoom;
        boolean isOwn = message.from.equals(currentUsername);
        
        if (shouldShowTimeDivider(roomName, message.time)) {
            HBox timeDivider = new HBox();
            timeDivider.setAlignment(Pos.CENTER);
            timeDivider.setPadding(new Insets(10, 0, 5, 0));
            
            Label timeLabel = new Label(formatTimeDivider(message.time));
            timeLabel.setFont(new Font("System", 11));
            timeLabel.setTextFill(Color.web("#94a3b8"));
            timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-padding: 3 10; -fx-background-radius: 10;");
            
            timeDivider.getChildren().add(timeLabel);
            messagesVBox.getChildren().add(timeDivider);
        }
        
        HBox messageBox = new HBox();
        if (isOwn) {
            messageBox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }
        messageBox.setPadding(new Insets(2, 10, 2, 10));
        messageBox.setSpacing(8);
        
        ImageView avatarView = createAvatar(message.from, isOwn);
        
        VBox contentBox = new VBox();
        contentBox.setPadding(new Insets(8, 12, 8, 12));
        if (isOwn) {
            contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
        } else {
            contentBox.setBackground(new Background(new BackgroundFill(Color.web("#e2e8f0"), new CornerRadii(12), null)));
        }
        contentBox.setMaxWidth(300);
        
        boolean showUsername = shouldShowUsername(roomName, message.from);
        if (showUsername && !isOwn) {
            Label senderLabel = new Label(message.from);
            senderLabel.setFont(new Font("System", 12));
            senderLabel.setTextFill(Color.web("#64748b"));
            contentBox.getChildren().add(senderLabel);
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
        } else if (type == MessageType.VOICE) {
            try {
                JsonObject voiceInfo = gson.fromJson(message.content, JsonObject.class);
                String voiceUrl = voiceInfo.has("url") ? voiceInfo.get("url").getAsString() : message.content;
                int duration = voiceInfo.has("duration") ? voiceInfo.get("duration").getAsInt() : 0;
                
                HBox voiceBox = new HBox();
                voiceBox.setSpacing(8);
                
                Button playButton = new Button("🔊");
                playButton.setFont(new Font("System", 18));
                playButton.setBackground(null);
                if (isOwn) {
                    playButton.setTextFill(Color.WHITE);
                } else {
                    playButton.setTextFill(Color.web("#3b82f6"));
                }
                playButton.setOnAction(e -> playVoice(voiceUrl, message.id != null ? message.id : "voice_" + System.currentTimeMillis()));
                
                Label durationLabel = new Label(formatDuration(duration));
                durationLabel.setFont(new Font("System", 12));
                if (isOwn) {
                    durationLabel.setTextFill(Color.WHITE);
                } else {
                    durationLabel.setTextFill(Color.BLACK);
                }
                
                voiceBox.getChildren().addAll(playButton, durationLabel);
                messageContent.getChildren().add(voiceBox);
            } catch (Exception e) {
                Text text = new Text("[语音]");
                text.setFont(new Font("System", 14));
                if (isOwn) {
                    text.setFill(Color.WHITE);
                } else {
                    text.setFill(Color.BLACK);
                }
                messageContent.getChildren().add(text);
            }
        } else if (type == MessageType.EMOJI) {
            Label emojiLabel = new Label(message.content);
            emojiLabel.setFont(new Font("System", 32));
            messageContent.getChildren().add(emojiLabel);
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
        
        contentBox.getChildren().add(messageContent);
        
        if (isOwn) {
            messageBox.getChildren().addAll(contentBox, avatarView);
        } else {
            messageBox.getChildren().addAll(avatarView, contentBox);
        }
        
        messagesVBox.getChildren().add(messageBox);
        updateLastMessageInfo(roomName, message.from, message.time);
        scrollToBottom();
    }
    
    private ImageView createAvatar(String username, boolean isOwn) {
        return createAvatar(username, isOwn, null);
    }
    
    private ImageView createAvatar(String username, boolean isOwn, String avatarPath) {
        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(36);
        avatarView.setFitHeight(36);
        avatarView.setClip(new Circle(18, 18, 18));
        
        if (avatarCache.containsKey(username)) {
            avatarView.setImage(avatarCache.get(username));
            return avatarView;
        }
        
        if (avatarPath != null && !avatarPath.isEmpty()) {
            loadAvatarFromZFile(username, avatarPath);
            if (avatarCache.containsKey(username)) {
                avatarView.setImage(avatarCache.get(username));
                return avatarView;
            }
        } else {
            for (Friend friend : friendsList) {
                if (friend.getUsername().equals(username) && friend.getAvatar() != null && !friend.getAvatar().isEmpty()) {
                    loadAvatarFromZFile(username, friend.getAvatar());
                    if (avatarCache.containsKey(username)) {
                        avatarView.setImage(avatarCache.get(username));
                        return avatarView;
                    }
                    break;
                }
            }
        }
        
        String firstLetter = username.substring(0, 1).toUpperCase();
        String svgContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">" +
            "<circle cx=\"50\" cy=\"50\" r=\"45\" fill=\"" + (isOwn ? "#2563eb" : getAvatarColor(username)) + "\"/>" +
            "<text x=\"50\" y=\"60\" font-size=\"40\" text-anchor=\"middle\" fill=\"white\">" + firstLetter + "</text>" +
            "</svg>";
        
        Image defaultAvatar = new Image(new java.io.ByteArrayInputStream(svgContent.getBytes()));
        avatarCache.put(username, defaultAvatar);
        avatarView.setImage(defaultAvatar);
        
        return avatarView;
    }
    
    private String getAvatarColor(String username) {
        String[] colors = {
            "#4a6fa5", "#6b8e23", "#8b4513", "#2f4f4f", "#800080",
            "#008080", "#dc143c", "#ff6347", "#ffa500", "#20b2aa",
            "#9370db", "#ff69b4", "#00ced1", "#32cd32", "#ffd700",
            "#c71585", "#00bfff", "#ff4500", "#483d8b", "#00fa9a"
        };
        
        int hash = Math.abs(username.hashCode());
        return colors[hash % colors.length];
    }
    
    private void loadAvatarFromZFile(String username, String avatarPath) {
        if (avatarPath == null || avatarPath.isEmpty()) {
            return;
        }
        
        synchronized (loadingAvatars) {
            if (loadingAvatars.contains(avatarPath)) {
                return;
            }
            loadingAvatars.add(avatarPath);
        }
        
        new Thread(() -> {
            try {
                byte[] avatarData = ZFileService.getInstance().downloadFileWithCache(avatarPath);
                Image avatarImage = new Image(new java.io.ByteArrayInputStream(avatarData));
                avatarCache.put(username, avatarImage);
                
                Platform.runLater(() -> {
                    refreshListViews();
                });
            } catch (Exception e) {
                System.err.println("加载头像失败: " + username + ", " + e.getMessage());
            } finally {
                synchronized (loadingAvatars) {
                    loadingAvatars.remove(avatarPath);
                }
            }
        }).start();
    }
    
    private void renderMessagesFromLocalStorage(String roomName, Integer conversationId) {
        if (conversationId == null) {
            return;
        }
        
        List<ChatMessage> localMsgs = localMessageStorage.getMessagesByConversationId(conversationId);
        roomMessages.put(roomName, new ArrayList<>(localMsgs));
        
        if (currentRoom != null && roomName.equals(currentRoom)) {
            messagesVBox.getChildren().clear();
            resetLastMessageInfo(roomName);
            for (ChatMessage msg : localMsgs) {
                displayMessage(msg);
            }
        }
    }
    
    private String getPreviewText(ChatMessage msg) {
        if (msg == null || msg.content == null) {
            return "";
        }
        
        MessageType type = msg.getMessageType();
        if (type == null) {
            return processPreviewContent(msg.content);
        }
        
        switch (type) {
            case TEXT:
            case PRIVATE_CHAT:
                try {
                    JsonObject jsonContent = gson.fromJson(msg.content, JsonObject.class);
                    if (jsonContent.has("content")) {
                        return jsonContent.get("content").getAsString();
                    }
                } catch (Exception e) {
                    return msg.content;
                }
                break;
            case SYSTEM:
                return processPreviewContent(msg.content);
            case JOIN:
                try {
                    JsonObject jsonContent = gson.fromJson(msg.content, JsonObject.class);
                    if (jsonContent.has("room_name")) {
                        return msg.from + " 加入了 " + jsonContent.get("room_name").getAsString();
                    }
                } catch (Exception e) {
                    return msg.from + " 加入了房间";
                }
                break;
            case EXIT_ROOM:
                return msg.from + " 退出了房间";
            case IMAGE:
                return "[图片]";
            case FILE:
                try {
                    JsonObject jsonContent = gson.fromJson(msg.content, JsonObject.class);
                    if (jsonContent.has("name")) {
                        return "[文件] " + jsonContent.get("name").getAsString();
                    }
                } catch (Exception e) {
                    return "[文件]";
                }
                break;
            case VOICE:
                try {
                    JsonObject jsonContent = gson.fromJson(msg.content, JsonObject.class);
                    int duration = jsonContent.has("duration") ? jsonContent.get("duration").getAsInt() : 0;
                    return "[语音] " + formatDuration(duration);
                } catch (Exception e) {
                    return "[语音]";
                }
            case FRIEND_REQUEST:
                return "[好友请求]";
            case FRIEND_REQUEST_RESPONSE:
                if ("accepted".equals(msg.content)) {
                    return "好友请求已接受";
                } else if ("rejected".equals(msg.content)) {
                    return "好友请求已拒绝";
                }
                return msg.content;
            case ROOM_JOIN_REQUEST:
                return msg.from + " 请求加入房间";
            case ROOM_JOIN_RESPONSE:
                if (msg.content != null && msg.content.startsWith("accept")) {
                    return "房间加入请求已被同意";
                } else if (msg.content != null && msg.content.startsWith("reject")) {
                    return "房间加入请求已被拒绝";
                }
                return msg.content;
            default:
                return processPreviewContent(msg.content);
        }
        
        return msg.content;
    }
    
    private String processPreviewContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        if (content.startsWith("{") && content.endsWith("}")) {
            try {
                JsonObject jsonContent = gson.fromJson(content, JsonObject.class);
                
                if (jsonContent.has("content")) {
                    return jsonContent.get("content").getAsString();
                }
                
                if (jsonContent.has("room_name")) {
                    return jsonContent.get("room_name").getAsString();
                }
                
                if (jsonContent.has("rooms")) {
                    return "[房间列表]";
                }
                
                if (jsonContent.has("users")) {
                    return "[用户列表]";
                }
            } catch (Exception e) {
                return content;
            }
        }
        
        return content;
    }
    
    private String formatMessageTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        
        try {
            java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date messageDate = inputFormat.parse(timeStr);
            
            java.util.Calendar messageCal = java.util.Calendar.getInstance();
            messageCal.setTime(messageDate);
            
            java.util.Calendar nowCal = java.util.Calendar.getInstance();
            
            int yearDiff = nowCal.get(java.util.Calendar.YEAR) - messageCal.get(java.util.Calendar.YEAR);
            
            if (yearDiff > 0) {
                java.text.SimpleDateFormat yearFormat = new java.text.SimpleDateFormat("yyyy/MM/dd");
                return yearFormat.format(messageDate);
            }
            
            java.util.Calendar yesterdayCal = java.util.Calendar.getInstance();
            yesterdayCal.add(java.util.Calendar.DAY_OF_MONTH, -1);
            
            boolean isToday = nowCal.get(java.util.Calendar.YEAR) == messageCal.get(java.util.Calendar.YEAR) &&
                             nowCal.get(java.util.Calendar.MONTH) == messageCal.get(java.util.Calendar.MONTH) &&
                             nowCal.get(java.util.Calendar.DAY_OF_MONTH) == messageCal.get(java.util.Calendar.DAY_OF_MONTH);
            
            boolean isYesterday = yesterdayCal.get(java.util.Calendar.YEAR) == messageCal.get(java.util.Calendar.YEAR) &&
                                yesterdayCal.get(java.util.Calendar.MONTH) == messageCal.get(java.util.Calendar.MONTH) &&
                                yesterdayCal.get(java.util.Calendar.DAY_OF_MONTH) == messageCal.get(java.util.Calendar.DAY_OF_MONTH);
            
            if (isToday) {
                java.text.SimpleDateFormat hourFormat = new java.text.SimpleDateFormat("HH:mm");
                return hourFormat.format(messageDate);
            }
            
            if (isYesterday) {
                java.text.SimpleDateFormat hourFormat = new java.text.SimpleDateFormat("HH:mm");
                return "昨天 " + hourFormat.format(messageDate);
            }
            
            java.util.Calendar oneWeekAgoCal = java.util.Calendar.getInstance();
            oneWeekAgoCal.add(java.util.Calendar.DAY_OF_MONTH, -7);
            
            if (messageCal.after(oneWeekAgoCal)) {
                String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                int dayOfWeek = messageCal.get(java.util.Calendar.DAY_OF_WEEK);
                return weekDays[dayOfWeek - 1];
            }
            
            java.text.SimpleDateFormat monthFormat = new java.text.SimpleDateFormat("MM/dd");
            return monthFormat.format(messageDate);
            
        } catch (Exception e) {
            return timeStr;
        }
    }
    
    private boolean shouldShowTimeDivider(String roomName, String currentTime) {
        String lastTimeStr = lastMessageTime.get(roomName);
        if (lastTimeStr == null || lastTimeStr.isEmpty()) {
            return true;
        }
        
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date lastDate = format.parse(lastTimeStr);
            java.util.Date currentDate = format.parse(currentTime);
            
            long diff = currentDate.getTime() - lastDate.getTime();
            if (diff >= TIME_INTERVAL_MS) {
                return true;
            }
            
            java.util.Calendar lastCal = java.util.Calendar.getInstance();
            lastCal.setTime(lastDate);
            java.util.Calendar currentCal = java.util.Calendar.getInstance();
            currentCal.setTime(currentDate);
            
            return lastCal.get(java.util.Calendar.DATE) != currentCal.get(java.util.Calendar.DATE);
        } catch (Exception e) {
            return true;
        }
    }
    
    private boolean shouldShowUsername(String roomName, String sender) {
        String lastSender = lastMessageSender.get(roomName);
        return lastSender == null || !lastSender.equals(sender);
    }
    
    private void updateLastMessageInfo(String roomName, String sender, String time) {
        lastMessageSender.put(roomName, sender);
        lastMessageTime.put(roomName, time);
    }
    
    private void resetLastMessageInfo(String roomName) {
        lastMessageSender.remove(roomName);
        lastMessageTime.remove(roomName);
    }
    
    private String formatTimeDivider(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        
        try {
            java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date messageDate = inputFormat.parse(timeStr);
            
            java.util.Calendar messageCal = java.util.Calendar.getInstance();
            messageCal.setTime(messageDate);
            
            java.util.Calendar nowCal = java.util.Calendar.getInstance();
            
            int yearDiff = nowCal.get(java.util.Calendar.YEAR) - messageCal.get(java.util.Calendar.YEAR);
            
            if (yearDiff > 0) {
                java.text.SimpleDateFormat yearFormat = new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm");
                return yearFormat.format(messageDate);
            }
            
            java.util.Calendar todayCal = java.util.Calendar.getInstance();
            boolean isToday = todayCal.get(java.util.Calendar.YEAR) == messageCal.get(java.util.Calendar.YEAR) &&
                             todayCal.get(java.util.Calendar.MONTH) == messageCal.get(java.util.Calendar.MONTH) &&
                             todayCal.get(java.util.Calendar.DAY_OF_MONTH) == messageCal.get(java.util.Calendar.DAY_OF_MONTH);
            
            if (isToday) {
                java.text.SimpleDateFormat hourFormat = new java.text.SimpleDateFormat("今天 HH:mm");
                return hourFormat.format(messageDate);
            }
            
            java.util.Calendar yesterdayCal = java.util.Calendar.getInstance();
            yesterdayCal.add(java.util.Calendar.DAY_OF_MONTH, -1);
            boolean isYesterday = yesterdayCal.get(java.util.Calendar.YEAR) == messageCal.get(java.util.Calendar.YEAR) &&
                                yesterdayCal.get(java.util.Calendar.MONTH) == messageCal.get(java.util.Calendar.MONTH) &&
                                yesterdayCal.get(java.util.Calendar.DAY_OF_MONTH) == messageCal.get(java.util.Calendar.DAY_OF_MONTH);
            
            if (isYesterday) {
                java.text.SimpleDateFormat hourFormat = new java.text.SimpleDateFormat("昨天 HH:mm");
                return hourFormat.format(messageDate);
            }
            
            java.text.SimpleDateFormat monthFormat = new java.text.SimpleDateFormat("MM月dd日 HH:mm");
            return monthFormat.format(messageDate);
            
        } catch (Exception e) {
            return timeStr;
        }
    }
    
    private void showNotification(String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("通知");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
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
        TextInputDialog searchDialog = new TextInputDialog();
        searchDialog.setTitle("搜索房间");
        searchDialog.setHeaderText("请输入房间名称进行搜索");
        searchDialog.setContentText("搜索关键词:");
        
        Optional<String> searchResult = searchDialog.showAndWait();
        searchResult.ifPresent(keyword -> {
            onRoomSelectedCallback = roomInfo -> {
                String roomName = (String) roomInfo.get("name");
                webSocketClient.sendRequestRoomJoin(currentUsername, roomName);
                showNotification("房间加入请求已发送，请等待房主/管理员同意");
            };
            webSocketClient.sendSearchRooms(currentUsername, keyword);
        });
    }
    
    private void leaveRoom() {
        if (currentRoom != null && !currentRoom.equals("system")) {
            webSocketClient.sendExitRoom(currentUsername, currentRoom);
            roomsList.removeIf(r -> r.getName().equals(currentRoom));
            messagesVBox.getChildren().clear();
            currentRoom = null;
            currentConversationId = null;
            currentChatLabel.setText("");
            chatContent.setVisible(false);
            chatContent.setManaged(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
        }
    }
    
    private void addFriend() {
        TextInputDialog searchDialog = new TextInputDialog();
        searchDialog.setTitle("搜索用户");
        searchDialog.setHeaderText("请输入用户名进行搜索");
        searchDialog.setContentText("搜索关键词:");
        
        Optional<String> searchResult = searchDialog.showAndWait();
        searchResult.ifPresent(keyword -> {
            onUserSelectedCallback = userInfo -> {
                String username = (String) userInfo.get("username");
                webSocketClient.sendFriendRequest(currentUsername, username, "");
            };
            webSocketClient.sendSearchUsers(currentUsername, keyword);
        });
    }
    
    private void showFriendRequests() {
        webSocketClient.sendRequestAllFriendRequests(currentUsername);
    }
    
    private void handleSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            if (isMessagesMode) {
                updateMessagesList();
            } else {
                updateContactsList();
            }
            return;
        }
        
        String lowerKeyword = keyword.toLowerCase().trim();
        ObservableList<ListItem> filteredList = FXCollections.observableArrayList();
        
        if (isMessagesMode) {
            for (ListItem item : messagesList) {
                if (item.getDisplayName().toLowerCase().contains(lowerKeyword)) {
                    filteredList.add(item);
                }
            }
        } else {
            for (ListItem item : contactsList) {
                if (item.getDisplayName().toLowerCase().contains(lowerKeyword)) {
                    filteredList.add(item);
                }
            }
        }
        
        mainListView.setItems(filteredList);
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
    
    private void openIndependentChatWindow(ListItem item) {
        final String roomName = item.getName();
        Integer convIdInt = roomConversationIds.get(roomName);
        if (convIdInt == null) {
            convIdInt = item.getConversationId();
        }
        final Integer convId = convIdInt;
        
        Stage chatStage = new Stage();
        chatStage.setTitle(item.getDisplayName());
        chatStage.setWidth(600);
        chatStage.setHeight(500);
        
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");
        
        HBox headerBar = new HBox();
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPadding(new Insets(10, 15, 10, 15));
        headerBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
        
        ImageView avatarView = createAvatar(roomName, false, item.getAvatar());
        avatarView.setFitWidth(36);
        avatarView.setFitHeight(36);
        avatarView.setClip(new Circle(18, 18, 18));
        
        Label nameLabel = new Label(item.getDisplayName());
        nameLabel.setFont(Font.font(16));
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeButton = new Button("×");
        closeButton.setFont(Font.font(18));
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-padding: 0 10;");
        closeButton.setOnAction(e -> chatStage.close());
        
        headerBar.getChildren().addAll(avatarView, new Region(), nameLabel, spacer, closeButton);
        
        VBox messagesVBox = new VBox();
        messagesVBox.setStyle("-fx-padding: 10px; -fx-spacing: 5px;");
        
        ScrollPane scrollPane = new ScrollPane(messagesVBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        
        HBox inputBar = new HBox();
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setSpacing(10);
        inputBar.setPadding(new Insets(10, 15, 10, 15));
        inputBar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");
        
        TextField messageInput = new TextField();
        messageInput.setPromptText("输入消息...");
        messageInput.setStyle("-fx-font-size: 14px; -fx-padding: 10px 14px; -fx-background-color: #f1f5f9; -fx-border-color: #e2e8f0; -fx-border-radius: 10px; -fx-background-radius: 10px;");
        HBox.setHgrow(messageInput, Priority.ALWAYS);
        
        Button sendButton = new Button("发送");
        sendButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-background-color: #2563eb; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10px 20px;");
        
        inputBar.getChildren().addAll(messageInput, sendButton);
        
        root.setTop(headerBar);
        root.setCenter(scrollPane);
        root.setBottom(inputBar);
        
        Scene scene = new Scene(root);
        chatStage.setScene(scene);
        
        if (convId != null && localMessageStorage.hasMessages(convId)) {
            List<ChatMessage> localMsgs = localMessageStorage.getMessagesByConversationId(convId);
            for (ChatMessage msg : localMsgs) {
                HBox messageBox = new HBox();
                boolean isOwn = msg.from.equals(currentUsername);
                messageBox.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                messageBox.setPadding(new Insets(2, 10, 2, 10));
                messageBox.setSpacing(8);
                
                ImageView msgAvatar = createAvatar(msg.from, isOwn);
                msgAvatar.setFitWidth(32);
                msgAvatar.setFitHeight(32);
                msgAvatar.setClip(new Circle(16, 16, 16));
                
                VBox contentBox = new VBox();
                contentBox.setPadding(new Insets(8, 12, 8, 12));
                contentBox.setBackground(new Background(new BackgroundFill(isOwn ? Color.web("#2563eb") : Color.web("#e2e8f0"), new CornerRadii(12), null)));
                contentBox.setMaxWidth(250);
                
                String displayContent = msg.content;
                if (msg.getMessageType() == MessageType.PRIVATE_CHAT || msg.getMessageType() == MessageType.TEXT) {
                    try {
                        JsonObject contentObj = gson.fromJson(msg.content, JsonObject.class);
                        if (contentObj.has("content")) {
                            displayContent = contentObj.get("content").getAsString();
                        }
                    } catch (Exception ex) {
                    }
                }
                
                Text messageText = new Text(displayContent);
                messageText.setFont(Font.font(14));
                messageText.setFill(isOwn ? Color.WHITE : Color.BLACK);
                
                contentBox.getChildren().add(messageText);
                
                if (isOwn) {
                    messageBox.getChildren().addAll(contentBox, msgAvatar);
                } else {
                    messageBox.getChildren().addAll(msgAvatar, contentBox);
                }
                
                messagesVBox.getChildren().add(messageBox);
            }
        }
        
        sendButton.setOnAction(e -> {
            String content = messageInput.getText().trim();
            if (content.isEmpty() || convId == null) return;
            
            boolean isPrivate = friendsList.stream().anyMatch(f -> f.getUsername().equals(roomName));
            
            ChatMessage outgoingMessage = new ChatMessage();
            outgoingMessage.from = currentUsername;
            outgoingMessage.content = isPrivate ? "{\"content\":\"" + content + "\"}" : content;
            outgoingMessage.time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            if (isPrivate) {
                outgoingMessage.type = MessageType.PRIVATE_CHAT.name();
                outgoingMessage.conversationId = convId;
                webSocketClient.sendPrivateChat(currentUsername, content, roomName, convId);
            } else {
                outgoingMessage.type = MessageType.TEXT.name();
                outgoingMessage.conversationId = convId;
                webSocketClient.sendText(currentUsername, content, convId);
            }
            
            localMessageStorage.saveMessage(outgoingMessage, roomName);
            
            HBox messageBox = new HBox();
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            messageBox.setPadding(new Insets(2, 10, 2, 10));
            messageBox.setSpacing(8);
            
            ImageView msgAvatar = createAvatar(currentUsername, true);
            msgAvatar.setFitWidth(32);
            msgAvatar.setFitHeight(32);
            msgAvatar.setClip(new Circle(16, 16, 16));
            
            VBox contentBox = new VBox();
            contentBox.setPadding(new Insets(8, 12, 8, 12));
            contentBox.setBackground(new Background(new BackgroundFill(Color.web("#2563eb"), new CornerRadii(12), null)));
            contentBox.setMaxWidth(250);
            
            Text messageText = new Text(content);
            messageText.setFont(Font.font(14));
            messageText.setFill(Color.WHITE);
            
            contentBox.getChildren().add(messageText);
            messageBox.getChildren().addAll(contentBox, msgAvatar);
            
            messagesVBox.getChildren().add(messageBox);
            messageInput.setText("");
            
            messagesVBox.layout();
            messagesVBox.getChildren().stream()
                .reduce((first, second) -> second)
                .ifPresent(node -> node.requestFocus());
        });
        
        messageInput.setOnAction(e -> sendButton.fire());
        
        chatStage.show();
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
                case JOIN:
                    handleJoinResponse(message);
                    break;
                case USERS_SEARCH_RESULT:
                    handleUsersSearchResult(message);
                    break;
                case ROOMS_SEARCH_RESULT:
                    handleRoomsSearchResult(message);
                    break;
                case ROOM_JOIN_REQUEST:
                    handleRoomJoinRequest(message);
                    break;
                case ROOM_JOIN_RESPONSE:
                    handleRoomJoinResponse(message);
                    break;
                case ROOM_ANNOUNCEMENT:
                    handleRoomAnnouncement(message);
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
            
            for (JsonElement roomElement : roomsArray) {
                JsonObject roomObj = roomElement.getAsJsonObject();
                String roomName = roomObj.get("name").getAsString();
                Integer conversationId = roomObj.has("conversation_id") ? roomObj.get("conversation_id").getAsInt() : null;
                
                roomConversationIds.put(roomName, conversationId);
                
                if (!existingRooms.contains(roomName)) {
                    roomsList.add(new Room(roomName));
                }
                
                if (conversationId != null) {
                    String existingName = localMessageStorage.getConversationNameById(conversationId);
                    if (existingName == null) {
                        localMessageStorage.updateConversation(conversationId, roomName, "", "", "");
                    }
                }
            }
            
            refreshListViews();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private Consumer<Map<String, Object>> onUserSelectedCallback;
    private Consumer<Map<String, Object>> onRoomSelectedCallback;
    
    private void handleUsersSearchResult(ChatMessage message) {
        try {
            JsonArray usersArray = gson.fromJson(message.content, JsonArray.class);
            
            java.util.List<Map<String, Object>> userList = new java.util.ArrayList<>();
            for (JsonElement userElement : usersArray) {
                JsonObject userObj = userElement.getAsJsonObject();
                java.util.Map<String, Object> userInfo = new java.util.HashMap<>();
                userInfo.put("id", userObj.has("id") ? userObj.get("id").getAsInt() : 0);
                userInfo.put("username", userObj.has("username") ? userObj.get("username").getAsString() : "");
                userList.add(userInfo);
            }
            
            if (onUserSelectedCallback != null) {
                showSearchResultDialog(userList, "选择用户", "username", onUserSelectedCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleRoomsSearchResult(ChatMessage message) {
        try {
            JsonArray roomsArray = gson.fromJson(message.content, JsonArray.class);
            
            java.util.List<Map<String, Object>> roomList = new java.util.ArrayList<>();
            for (JsonElement roomElement : roomsArray) {
                JsonObject roomObj = roomElement.getAsJsonObject();
                java.util.Map<String, Object> roomInfo = new java.util.HashMap<>();
                roomInfo.put("id", roomObj.has("id") ? roomObj.get("id").getAsString() : "");
                roomInfo.put("name", roomObj.has("name") ? roomObj.get("name").getAsString() : "");
                roomInfo.put("type", roomObj.has("type") ? roomObj.get("type").getAsString() : "");
                roomInfo.put("memberCount", roomObj.has("memberCount") ? roomObj.get("memberCount").getAsInt() : 0);
                roomList.add(roomInfo);
            }
            
            if (onRoomSelectedCallback != null) {
                showSearchResultDialog(roomList, "选择房间", "name", onRoomSelectedCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showSearchResultDialog(java.util.List<Map<String, Object>> results, String title, String displayField, Consumer<Map<String, Object>> callback) {
        javafx.scene.control.Dialog<Map<String, Object>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        
        javafx.scene.control.ListView<String> listView = new javafx.scene.control.ListView<>();
        java.util.Map<String, Map<String, Object>> displayMap = new java.util.HashMap<>();
        
        for (Map<String, Object> result : results) {
            String displayText = (String) result.get(displayField);
            if ("room".equalsIgnoreCase(title) || "房间".equalsIgnoreCase(title)) {
                String type = (String) result.get("type");
                int memberCount = result.get("memberCount") != null ? (int) result.get("memberCount") : 0;
                displayText = displayText + " (" + type + ", " + memberCount + "人)";
            }
            listView.getItems().add(displayText);
            displayMap.put(displayText, result);
        }
        
        listView.setPrefWidth(300);
        listView.setPrefHeight(200);
        
        dialog.getDialogPane().setContent(listView);
        
        javafx.scene.control.ButtonType selectButton = new javafx.scene.control.ButtonType("选择", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType cancelButton = new javafx.scene.control.ButtonType("取消", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(selectButton, cancelButton);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectButton) {
                String selectedItem = listView.getSelectionModel().getSelectedItem();
                return displayMap.get(selectedItem);
            }
            return null;
        });
        
        Optional<Map<String, Object>> result = dialog.showAndWait();
        result.ifPresent(callback);
    }
    
    private void handleJoinResponse(ChatMessage message) {
        if (message.conversationId != null && message.conversationId > 0) {
            roomConversationIds.put(currentRoom, message.conversationId);
            
            if (currentRoom != null) {
                currentConversationId = message.conversationId;
            }
        }
        
        try {
            if (message.content != null && message.content.startsWith("{") && message.content.endsWith("}")) {
                JsonObject responseObj = gson.fromJson(message.content, JsonObject.class);
                if (responseObj.has("room_name") && responseObj.has("conversation_id")) {
                    String roomName = responseObj.get("room_name").getAsString();
                    Integer conversationId = responseObj.get("conversation_id").getAsInt();
                    
                    roomConversationIds.put(roomName, conversationId);
                    
                    if (roomName.equals(currentRoom)) {
                        currentConversationId = conversationId;
                        
                        if (responseObj.has("announcement")) {
                            String announcement = responseObj.get("announcement").getAsString();
                            currentRoomAnnouncement = announcement;
                            showRoomAnnouncement();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleRoomAnnouncement(ChatMessage message) {
        try {
            JsonObject responseObj = gson.fromJson(message.content, JsonObject.class);
            if (responseObj.has("announcement") && message.conversationId != null && 
                message.conversationId.equals(currentConversationId)) {
                String announcement = responseObj.get("announcement").getAsString();
                currentRoomAnnouncement = announcement;
                showRoomAnnouncement();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showRoomAnnouncement() {
        if (announcementBar != null) {
            messagesVBox.getChildren().remove(announcementBar);
            announcementBar = null;
        }
        
        if (currentRoomAnnouncement == null || currentRoomAnnouncement.isEmpty()) {
            return;
        }
        
        announcementBar = new HBox();
        announcementBar.setAlignment(Pos.CENTER_LEFT);
        announcementBar.setPadding(new Insets(8, 15, 8, 15));
        announcementBar.setStyle("-fx-background-color: #fffbeb; -fx-border-color: #fef3c7; -fx-border-width: 1 0;");
        announcementBar.setSpacing(8);
        
        Label iconLabel = new Label("📢");
        iconLabel.setFont(new Font("System", 14));
        
        Text announcementText = new Text(currentRoomAnnouncement);
        announcementText.setFont(new Font("System", 13));
        announcementText.setFill(Color.web("#92400e"));
        
        Button editButton = new Button("编辑");
        editButton.setFont(new Font("System", 12));
        editButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #d97706; -fx-padding: 2 6;");
        editButton.setOnAction(e -> showAnnouncementEditDialog());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        announcementBar.getChildren().addAll(iconLabel, announcementText, spacer, editButton);
        
        if (!messagesVBox.getChildren().isEmpty()) {
            messagesVBox.getChildren().add(0, announcementBar);
        } else {
            messagesVBox.getChildren().add(announcementBar);
        }
    }
    
    private void hideRoomAnnouncement() {
        if (announcementBar != null) {
            messagesVBox.getChildren().remove(announcementBar);
            announcementBar = null;
        }
    }
    
    private void showAnnouncementEditDialog() {
        TextInputDialog dialog = new TextInputDialog(currentRoomAnnouncement != null ? currentRoomAnnouncement : "");
        dialog.setTitle("编辑房间公告");
        dialog.setHeaderText("请输入房间公告内容");
        dialog.setContentText("公告内容:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(announcement -> {
            if (currentConversationId != null) {
                webSocketClient.sendSetRoomAnnouncement(currentUsername, announcement, currentConversationId);
            }
        });
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
        
        if (targetRoom == null && conversationId != null) {
            String storedName = localMessageStorage.getConversationNameById(conversationId);
            if (storedName != null) {
                targetRoom = storedName;
            } else {
                targetRoom = "conversation_" + conversationId;
            }
        } else if (targetRoom == null) {
            targetRoom = currentRoom;
        }
        
        if (conversationId != null) {
            roomConversationIds.put(targetRoom, conversationId);
        }
        
        if (message.conversationId != null) {
            localMessageStorage.saveMessage(message, targetRoom);
            localMessageStorage.updateConversation(message.conversationId, targetRoom, message.time, message.content, message.from);
        }
        
        boolean isCurrentRoom = currentRoom != null && targetRoom.equals(currentRoom);
        if (isCurrentRoom) {
            roomMessages.put(targetRoom, new ArrayList<>(localMessageStorage.getMessagesByConversationId(conversationId)));
            displayMessage(message);
        } else {
            roomMessages.put(targetRoom, new ArrayList<>(localMessageStorage.getMessagesByConversationId(conversationId)));
            if (conversationId != null) {
                localMessageStorage.incrementUnreadCount(conversationId);
            }
            
            if (!isWindowFocused) {
                boolean isMuted = localMessageStorage.isMuted(conversationId);
                if (!isMuted) {
                    playNotificationSound();
                }
                showSystemTrayNotification(targetRoom, message);
                flashWindow();
            }
        }
        
        refreshListViews();
    }
    
    private void playNotificationSound() {
        try {
            javafx.scene.media.AudioClip audioClip = new javafx.scene.media.AudioClip(
                getClass().getResource("/sounds/notification.wav").toExternalForm()
            );
            audioClip.play();
        } catch (Exception e) {
        }
    }
    
    private void showSystemTrayNotification(String roomName, ChatMessage message) {
        try {
            Stage notificationStage = new Stage();
            notificationStage.initStyle(StageStyle.UNDECORATED);
            notificationStage.setAlwaysOnTop(true);
            
            VBox notificationBox = new VBox();
            notificationBox.setStyle(
                "-fx-background-color: #1e293b;" +
                "-fx-padding: 15px;" +
                "-fx-border-radius: 8px;" +
                "-fx-background-radius: 8px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 0);"
            );
            notificationBox.setMaxWidth(300);
            
            Label titleLabel = new Label(roomName);
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
            
            String preview = getPreviewText(message);
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            Label contentLabel = new Label(preview);
            contentLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
            contentLabel.setWrapText(true);
            
            notificationBox.getChildren().addAll(titleLabel, contentLabel);
            
            Scene scene = new Scene(notificationBox, javafx.scene.paint.Color.TRANSPARENT);
            notificationStage.setScene(scene);
            
            javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
            javafx.geometry.Rectangle2D screenBounds = screen.getVisualBounds();
            
            double notificationWidth = 300;
            double notificationHeight = 80;
            double gap = 20;
            
            notificationStage.setX(screenBounds.getMaxX() - notificationWidth - gap);
            notificationStage.setY(screenBounds.getMaxY() - notificationHeight - gap);
            
            notificationStage.show();
            
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> {
                        notificationStage.close();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } catch (Exception e) {
        }
    }
    
    private void flashWindow() {
        try {
            Stage stage = (Stage) messagesVBox.getScene().getWindow();
            stage.toFront();
            stage.requestFocus();
        } catch (Exception e) {
        }
    }
    
    private EventHandler<MouseEvent> sidebarClickOutsideHandler;
    
    private void toggleProfileSidebar() {
        if (isSidebarVisible) {
            profileSidebar.getChildren().clear();
            profileSidebar.setVisible(false);
            isSidebarVisible = false;
            if (sidebarClickOutsideHandler != null) {
                profileSidebar.getParent().removeEventHandler(MouseEvent.MOUSE_CLICKED, sidebarClickOutsideHandler);
                sidebarClickOutsideHandler = null;
            }
            return;
        }
        
        if (currentRoom == null) {
            showInfo("请先选择一个聊天对象");
            return;
        }
        
        isSidebarVisible = true;
        profileSidebar.getChildren().clear();
        
        sidebarClickOutsideHandler = e -> {
            if (!profileSidebar.isVisible()) {
                return;
            }
            
            javafx.scene.Node source = (javafx.scene.Node) e.getSource();
            javafx.scene.Node target = (javafx.scene.Node) e.getTarget();
            
            boolean clickedOnSidebar = false;
            javafx.scene.Node node = target;
            while (node != null) {
                if (node == profileSidebar) {
                    clickedOnSidebar = true;
                    break;
                }
                node = node.getParent();
            }
            
            if (!clickedOnSidebar) {
                toggleProfileSidebar();
            }
        };
        
        profileSidebar.getParent().addEventHandler(MouseEvent.MOUSE_CLICKED, sidebarClickOutsideHandler);
        
        boolean isPrivateChat = friendsList.stream().anyMatch(f -> f.getUsername().equals(currentRoom));
        
        List<String> members = new ArrayList<>();
        if (isPrivateChat) {
            for (Friend friend : friendsList) {
                if (friend.getUsername().equals(currentRoom)) {
                    members.add(friend.getUsername());
                    break;
                }
            }
        } else {
            members = getRoomMembers(currentRoom);
        }
        
        VBox sidebarContent = new VBox();
        sidebarContent.setStyle("-fx-background-color: #f5f5f5;");
        
        TextField searchField = new TextField();
        searchField.setPromptText("搜索群成员");
        searchField.setStyle("-fx-background-color: #e5e5e5; -fx-background-radius: 20; -fx-border-radius: 20; -fx-padding: 8 15; -fx-font-size: 13; -fx-border-color: transparent;");
        searchField.setMaxWidth(250);
        searchField.setAlignment(Pos.CENTER_LEFT);
        
        HBox searchBox = new HBox();
        searchBox.setAlignment(Pos.CENTER);
        searchBox.setPadding(new Insets(15, 0, 10, 0));
        searchBox.getChildren().add(searchField);
        sidebarContent.getChildren().add(searchBox);
        
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(15);
        gridPane.setPadding(new Insets(0, 15, 10, 15));
        
        int maxVisible = 16;
        boolean hasMore = members.size() > maxVisible;
        List<String> displayMembers = hasMore ? members.subList(0, maxVisible) : members;
        
        int col = 0;
        int row = 0;
        for (String member : displayMembers) {
            VBox memberBox = new VBox(6);
            memberBox.setAlignment(Pos.CENTER);
            
            Circle avatarClip = new Circle(25, 25, 25);
            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(50);
            avatarView.setFitHeight(50);
            avatarView.setPreserveRatio(true);
            avatarView.setClip(avatarClip);
            
            boolean isFriend = friendsList.stream().anyMatch(f -> f.getUsername().equals(member));
            if (isFriend) {
                for (Friend friend : friendsList) {
                    if (friend.getUsername().equals(member)) {
                        if (friend.getAvatar() != null && !friend.getAvatar().isEmpty()) {
                            loadAvatarFromZFile(member, friend.getAvatar());
                            if (avatarCache.containsKey(member)) {
                                avatarView.setImage(avatarCache.get(member));
                            }
                        }
                        break;
                    }
                }
            }
            
            if (avatarView.getImage() == null) {
                avatarView.setImage(createDefaultAvatar(member));
            }
            
            Label nameLabel = new Label(truncateText(member, 8));
            nameLabel.setFont(Font.font(13));
            nameLabel.setStyle("-fx-text-fill: #374151;");
            nameLabel.setAlignment(Pos.CENTER);
            nameLabel.setMaxWidth(70);
            nameLabel.setWrapText(true);
            
            memberBox.getChildren().addAll(avatarView, nameLabel);
            gridPane.add(memberBox, col, row);
            
            col++;
            if (col >= 4) {
                col = 0;
                row++;
            }
        }
        
        sidebarContent.getChildren().add(gridPane);
        
        if (hasMore) {
            final List<String> finalMembers = members;
            Button moreButton = new Button("查看更多");
            moreButton.setFont(Font.font(13));
            moreButton.setStyle("-fx-text-fill: #3b82f6; -fx-background-color: transparent; -fx-padding: 8 0;");
            moreButton.setOnAction(e -> {
                showFullMembersList(finalMembers);
            });
            
            HBox moreBox = new HBox();
            moreBox.setAlignment(Pos.CENTER);
            moreBox.getChildren().add(moreButton);
            sidebarContent.getChildren().add(moreBox);
        }
        
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: #e5e5e5;");
        sidebarContent.getChildren().add(divider);
        
        if (!isPrivateChat) {
            String roomNote = localMessageStorage.getRoomNote(currentConversationId);
            String roomDisplay = (roomNote != null && !roomNote.isEmpty()) ? roomNote : currentRoom;
            
            addSettingSection(sidebarContent, "房间名称", roomDisplay, true, true, newValue -> {
                JsonObject content = new JsonObject();
                content.addProperty("room_name", newValue);
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.type = MessageType.UPDATE_PROFILE.name();
                chatMessage.from = currentUsername;
                chatMessage.content = content.toString();
                chatMessage.conversationId = currentConversationId;
                webSocketClient.sendMessage(chatMessage);
                currentRoom = newValue;
                currentChatLabel.setText(newValue);
                localMessageStorage.updateConversationName(currentConversationId, newValue);
            });
            
            String announcementDisplay = "";
            if (currentRoomAnnouncement == null || currentRoomAnnouncement.isEmpty()) {
                announcementDisplay = "发布后通知全部房间成员";
            } else {
                announcementDisplay = truncateText(currentRoomAnnouncement, 30);
            }
            addSettingSection(sidebarContent, "房间公告", announcementDisplay, true, true);
            
            String noteDisplay = (roomNote == null || roomNote.isEmpty()) ? "房间的备注仅自己可见" : roomNote;
            addSettingSection(sidebarContent, "备注", noteDisplay, true, true, newValue -> {
                localMessageStorage.setRoomNote(currentConversationId, newValue);
                updateTitleBarWithNote();
                refreshListViews();
                toggleProfileSidebar();
            });
            
            String displayName = localMessageStorage.getRoomDisplayName(currentConversationId);
            String displayNameDisplay = (displayName == null || displayName.isEmpty()) ? currentUsername : displayName;
            addSettingSection(sidebarContent, "我在本房间的名称", displayNameDisplay, true, true, newValue -> {
                JsonObject content = new JsonObject();
                content.addProperty("display_name", newValue);
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.type = MessageType.UPDATE_PROFILE.name();
                chatMessage.from = currentUsername;
                chatMessage.content = content.toString();
                chatMessage.conversationId = currentConversationId;
                webSocketClient.sendMessage(chatMessage);
                localMessageStorage.setRoomDisplayName(currentConversationId, newValue);
            });
            
            Region divider2 = new Region();
            divider2.setPrefHeight(1);
            divider2.setStyle("-fx-background-color: #e5e5e5;");
            sidebarContent.getChildren().add(divider2);
            
            addSettingSection(sidebarContent, "查找聊天内容", "", true, false);
            
            boolean isPinned = localMessageStorage.isConversationPinned(currentConversationId);
            addSettingSection(sidebarContent, "置顶聊天", isPinned, newValue -> {
                localMessageStorage.setConversationPinned(currentConversationId, newValue);
                refreshListViews();
            });
            
            boolean isMuted = localMessageStorage.isMuted(currentConversationId);
            addSettingSection(sidebarContent, "消息免打扰", isMuted, newValue -> {
                localMessageStorage.setMuted(currentConversationId, newValue);
                refreshListViews();
            });
        } else {
            addSettingSection(sidebarContent, "状态", "在线", false, false);
            
            String roomNote = localMessageStorage.getRoomNote(currentConversationId);
            String noteDisplay = (roomNote == null || roomNote.isEmpty()) ? "备注仅自己可见" : roomNote;
            addSettingSection(sidebarContent, "备注", noteDisplay, true, true, newValue -> {
                localMessageStorage.setRoomNote(currentConversationId, newValue);
                toggleProfileSidebar();
            });
            
            Region divider2 = new Region();
            divider2.setPrefHeight(1);
            divider2.setStyle("-fx-background-color: #e5e5e5;");
            sidebarContent.getChildren().add(divider2);
            
            addSettingSection(sidebarContent, "查找聊天内容", "", true, false);
            
            boolean isPinned = localMessageStorage.isConversationPinned(currentConversationId);
            addSettingSection(sidebarContent, "置顶聊天", isPinned, newValue -> {
                localMessageStorage.setConversationPinned(currentConversationId, newValue);
                refreshListViews();
            });
            
            boolean isMuted = localMessageStorage.isMuted(currentConversationId);
            addSettingSection(sidebarContent, "消息免打扰", isMuted, newValue -> {
                localMessageStorage.setMuted(currentConversationId, newValue);
                refreshListViews();
            });
        }
        
        ScrollPane scrollPane = new ScrollPane(sidebarContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        
        profileSidebar.getChildren().add(scrollPane);
        
        profileSidebar.setVisible(true);
    }
    
    private void addSettingSection(VBox parent, String title, String value) {
        addSettingSection(parent, title, value, false, false, null);
    }
    
    private void addSettingSection(VBox parent, String title, String value, boolean isEditable, boolean hasEllipsis) {
        addSettingSection(parent, title, value, isEditable, hasEllipsis, null);
    }
    
    private void addSettingSection(VBox parent, String title, String value, boolean isEditable, boolean hasEllipsis, Consumer<String> saveCallback) {
        VBox section = new VBox();
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(12, 15, 12, 15));
        section.setSpacing(5);
        section.setStyle("-fx-background-color: white;");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font(15));
        titleLabel.setStyle("-fx-text-fill: #1f2937;");
        section.getChildren().add(titleLabel);
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font(14));
        valueLabel.setStyle("-fx-text-fill: #6b7280;");
        if (hasEllipsis) {
            valueLabel.setMaxWidth(250);
            valueLabel.setWrapText(true);
        }
        section.getChildren().add(valueLabel);
        
        if (isEditable) {
            section.setCursor(javafx.scene.Cursor.HAND);
            
            section.setOnMouseEntered(e -> {
                section.setStyle("-fx-background-color: #f9fafb;");
                valueLabel.setStyle("-fx-text-fill: #3b82f6;");
            });
            
            section.setOnMouseExited(e -> {
                section.setStyle("-fx-background-color: white;");
                valueLabel.setStyle("-fx-text-fill: #6b7280;");
            });
            
            section.setOnMouseClicked(e -> {
                if (saveCallback != null) {
                    startInlineEditing(section, valueLabel, value, saveCallback);
                } else {
                    handleSettingSectionClick(title, value);
                }
            });
        }
        
        parent.getChildren().add(section);
    }
    
    private void addSettingSection(VBox parent, String title, boolean initialValue, Consumer<Boolean> toggleCallback) {
        HBox section = new HBox();
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(12, 15, 12, 15));
        section.setSpacing(8);
        section.setStyle("-fx-background-color: white;");
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font(15));
        titleLabel.setStyle("-fx-text-fill: #1f2937;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        ToggleButton toggleButton = new ToggleButton();
        toggleButton.setSelected(initialValue);
        toggleButton.setPrefWidth(48);
        toggleButton.setPrefHeight(26);
        toggleButton.setStyle(
            "-fx-background-color: " + (initialValue ? "#3b82f6" : "#e5e5e5") + "; " +
            "-fx-background-radius: 13; " +
            "-fx-padding: 0; "
        );
        
        Region thumb = new Region();
        thumb.setPrefWidth(22);
        thumb.setPrefHeight(22);
        thumb.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 11; " +
            "-fx-translate-x: " + (initialValue ? "24" : "2") + "; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 0);"
        );
        toggleButton.setGraphic(thumb);
        
        toggleButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            toggleButton.setStyle(
                "-fx-background-color: " + (newVal ? "#3b82f6" : "#e5e5e5") + "; " +
                "-fx-background-radius: 13; " +
                "-fx-padding: 0; "
            );
            thumb.setStyle(
                "-fx-background-color: white; " +
                "-fx-background-radius: 11; " +
                "-fx-translate-x: " + (newVal ? "24" : "2") + "; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 0);"
            );
            toggleCallback.accept(newVal);
        });
        
        section.getChildren().addAll(titleLabel, toggleButton);
        parent.getChildren().add(section);
    }
    
    private void startInlineEditing(VBox section, Label valueLabel, String initialValue, Consumer<String> saveCallback) {
        TextField textField = new TextField(initialValue);
        textField.setFont(Font.font(14));
        textField.setStyle("-fx-background-color: white; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 4;");
        textField.setMaxWidth(250);
        
        int index = section.getChildren().indexOf(valueLabel);
        section.getChildren().set(index, textField);
        
        textField.requestFocus();
        textField.selectAll();
        
        Runnable finishEditing = () -> {
            String newValue = textField.getText().trim();
            if (!newValue.equals(initialValue)) {
                saveCallback.accept(newValue);
            }
            section.getChildren().set(index, valueLabel);
        };
        
        textField.setOnAction(e -> {
            finishEditing.run();
        });
        
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                finishEditing.run();
            }
        });
    }
    
    private void updateTitleBarWithNote() {
        if (currentConversationId == null || currentRoom == null) {
            return;
        }
        
        String roomNote = localMessageStorage.getRoomNote(currentConversationId);
        String displayName = (roomNote != null && !roomNote.isEmpty()) ? roomNote : currentRoom;
        
        boolean isRoom = roomsList.stream().anyMatch(r -> r.getName().equals(currentRoom));
        if (isRoom) {
            currentChatLabel.setText("当前房间: " + displayName);
        } else {
            currentChatLabel.setText("聊天: " + displayName);
        }
    }
    
    private void handleSettingSectionClick(String title, String value) {
        switch (title) {
            case "房间公告":
                showAnnouncementDialog();
                break;
            case "查找聊天内容":
                showSearchMessagesDialog();
                break;
        }
    }
    
    private void showFullMembersList(List<String> members) {
        Stage fullStage = new Stage();
        fullStage.setTitle("房间成员");
        fullStage.setWidth(400);
        fullStage.setHeight(500);
        fullStage.initOwner((Stage) messagesVBox.getScene().getWindow());
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        
        int col = 0;
        int row = 0;
        for (String member : members) {
            VBox memberBox = new VBox(5);
            memberBox.setAlignment(Pos.CENTER);
            memberBox.setPrefWidth(80);
            
            Circle avatarClip = new Circle(25, 25, 25);
            ImageView avatarView = new ImageView();
            avatarView.setFitWidth(50);
            avatarView.setFitHeight(50);
            avatarView.setPreserveRatio(true);
            avatarView.setClip(avatarClip);
            
            boolean isFriend = friendsList.stream().anyMatch(f -> f.getUsername().equals(member));
            if (isFriend) {
                for (Friend friend : friendsList) {
                    if (friend.getUsername().equals(member)) {
                        if (friend.getAvatar() != null && !friend.getAvatar().isEmpty()) {
                            loadAvatarFromZFile(member, friend.getAvatar());
                            if (avatarCache.containsKey(member)) {
                                avatarView.setImage(avatarCache.get(member));
                            }
                        }
                        break;
                    }
                }
            }
            
            if (avatarView.getImage() == null) {
                avatarView.setImage(createDefaultAvatar(member));
            }
            
            Label nameLabel = new Label(member);
            nameLabel.setFont(Font.font(12));
            nameLabel.setAlignment(Pos.CENTER);
            
            memberBox.getChildren().addAll(avatarView, nameLabel);
            gridPane.add(memberBox, col, row);
            
            col++;
            if (col >= 4) {
                col = 0;
                row++;
            }
        }
        
        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        
        content.getChildren().add(scrollPane);
        
        Scene scene = new Scene(content);
        fullStage.setScene(scene);
        fullStage.show();
    }
    
    private void showAnnouncementDialog() {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("房间公告");
        dialogStage.setWidth(400);
        dialogStage.setHeight(300);
        dialogStage.initOwner((Stage) messagesVBox.getScene().getWindow());
        
        VBox content = new VBox();
        content.setPadding(new Insets(15));
        content.setSpacing(10);
        
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_RIGHT);
        Button editButton = new Button("编辑");
        editButton.setFont(Font.font(13));
        editButton.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 5 15; -fx-background-radius: 4;");
        editButton.setOnAction(e -> {
            dialogStage.close();
            showAnnouncementEditDialog();
        });
        header.getChildren().add(editButton);
        content.getChildren().add(header);
        
        Label titleLabel = new Label("公告");
        titleLabel.setFont(Font.font(16));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f2937;");
        content.getChildren().add(titleLabel);
        
        TextArea announcementArea = new TextArea();
        announcementArea.setEditable(false);
        announcementArea.setFont(Font.font(14));
        announcementArea.setStyle("-fx-text-fill: #6b7280; -fx-background-color: #f9fafb;");
        if (currentRoomAnnouncement == null || currentRoomAnnouncement.isEmpty()) {
            announcementArea.setText("群主未设置");
        } else {
            announcementArea.setText(currentRoomAnnouncement);
        }
        VBox.setVgrow(announcementArea, Priority.ALWAYS);
        content.getChildren().add(announcementArea);
        
        Button closeButton = new Button("关闭");
        closeButton.setFont(Font.font(13));
        closeButton.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #374151; -fx-padding: 8 30; -fx-background-radius: 4;");
        closeButton.setOnAction(e -> dialogStage.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().add(closeButton);
        content.getChildren().add(buttonBox);
        
        Scene scene = new Scene(content);
        dialogStage.setScene(scene);
        dialogStage.show();
    }
    
    private void showSearchMessagesDialog() {
        Stage dialogStage = new Stage();
        dialogStage.setTitle(currentRoom + "的聊天记录");
        dialogStage.setWidth(600);
        dialogStage.setHeight(500);
        dialogStage.initOwner((Stage) messagesVBox.getScene().getWindow());
        
        VBox content = new VBox();
        content.setStyle("-fx-background-color: white;");
        
        TextField searchField = new TextField();
        searchField.setPromptText("搜索聊天记录...");
        searchField.setStyle("-fx-background-color: #e5e5e5; -fx-background-radius: 20; -fx-border-radius: 20; -fx-padding: 10 20; -fx-font-size: 14; -fx-border-color: transparent;");
        searchField.setMaxWidth(560);
        
        HBox searchBox = new HBox();
        searchBox.setAlignment(Pos.CENTER);
        searchBox.setPadding(new Insets(15, 0, 10, 0));
        searchBox.getChildren().add(searchField);
        content.getChildren().add(searchBox);
        
        VBox resultsVBox = new VBox();
        resultsVBox.setSpacing(5);
        resultsVBox.setPadding(new Insets(0, 15, 10, 15));
        
        ScrollPane scrollPane = new ScrollPane(resultsVBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        content.getChildren().add(scrollPane);
        
        Scene scene = new Scene(content);
        dialogStage.setScene(scene);
        
        Runnable renderMessages = () -> {
            resultsVBox.getChildren().clear();
            
            if (currentConversationId == null) {
                return;
            }
            
            List<ChatMessage> allMessages = localMessageStorage.getMessagesByConversationId(currentConversationId);
            String keyword = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";
            
            List<ChatMessage> filtered = new ArrayList<>();
            for (ChatMessage msg : allMessages) {
                String contentText = getPreviewText(msg);
                String sender = msg.from != null ? msg.from.toLowerCase() : "";
                
                if (keyword.isEmpty() || 
                    (contentText != null && contentText.toLowerCase().contains(keyword)) ||
                    sender.contains(keyword)) {
                    filtered.add(msg);
                }
            }
            
            Collections.reverse(filtered);
            
            for (ChatMessage msg : filtered) {
                HBox messageItem = new HBox();
                messageItem.setAlignment(Pos.CENTER_LEFT);
                messageItem.setPadding(new Insets(10, 0, 10, 0));
                messageItem.setSpacing(10);
                messageItem.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");
                
                Circle avatarClip = new Circle(20, 20, 20);
                ImageView avatarView = new ImageView();
                avatarView.setFitWidth(40);
                avatarView.setFitHeight(40);
                avatarView.setPreserveRatio(true);
                avatarView.setClip(avatarClip);
                
                boolean isFriend = friendsList.stream().anyMatch(f -> f.getUsername().equals(msg.from));
                if (isFriend) {
                    for (Friend friend : friendsList) {
                        if (friend.getUsername().equals(msg.from)) {
                            if (friend.getAvatar() != null && !friend.getAvatar().isEmpty()) {
                                loadAvatarFromZFile(msg.from, friend.getAvatar());
                                if (avatarCache.containsKey(msg.from)) {
                                    avatarView.setImage(avatarCache.get(msg.from));
                                }
                            }
                            break;
                        }
                    }
                }
                
                if (avatarView.getImage() == null) {
                    avatarView.setImage(createDefaultAvatar(msg.from));
                }
                
                VBox infoBox = new VBox();
                infoBox.setSpacing(3);
                HBox.setHgrow(infoBox, Priority.ALWAYS);
                
                Label nameLabel = new Label(msg.from);
                nameLabel.setFont(Font.font(14));
                nameLabel.setStyle("-fx-text-fill: #1f2937;");
                
                Node contentNode = createMessagePreviewNode(msg);
                
                infoBox.getChildren().addAll(nameLabel, contentNode);
                
                Label timeLabel = new Label(formatSearchTime(msg.time));
                timeLabel.setFont(Font.font(12));
                timeLabel.setStyle("-fx-text-fill: #9ca3af;");
                timeLabel.setAlignment(Pos.CENTER_RIGHT);
                timeLabel.setMaxWidth(120);
                
                messageItem.getChildren().addAll(avatarView, infoBox, timeLabel);
                resultsVBox.getChildren().add(messageItem);
            }
            
            if (filtered.isEmpty()) {
                Label emptyLabel = new Label(keyword.isEmpty() ? "暂无聊天记录" : "未找到匹配的聊天记录");
                emptyLabel.setFont(Font.font(14));
                emptyLabel.setStyle("-fx-text-fill: #9ca3af;");
                emptyLabel.setPadding(new Insets(20, 0, 20, 0));
                emptyLabel.setAlignment(Pos.CENTER);
                resultsVBox.getChildren().add(emptyLabel);
            }
        };
        
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            renderMessages.run();
        });
        
        dialogStage.show();
        
        renderMessages.run();
    }
    
    private Node createMessagePreviewNode(ChatMessage msg) {
        MessageType type = msg.getMessageType();
        
        if (type == MessageType.IMAGE) {
            ImageView imageView = new ImageView();
            imageView.setFitWidth(60);
            imageView.setFitHeight(60);
            imageView.setPreserveRatio(true);
            imageView.setStyle("-fx-border-color: #e5e5e5; -fx-border-width: 1;");
            
            try {
                JsonObject jsonContent = gson.fromJson(msg.content, JsonObject.class);
                if (jsonContent.has("url")) {
                    String imageUrl = jsonContent.get("url").getAsString();
                    if (imageUrl.startsWith("/")) {
                        imageUrl = "http://localhost:8080" + imageUrl;
                    }
                    Image image = new Image(imageUrl, true);
                    imageView.setImage(image);
                }
            } catch (Exception e) {
                Label label = new Label("[图片]");
                label.setFont(Font.font(13));
                label.setStyle("-fx-text-fill: #6b7280;");
                return label;
            }
            
            return imageView;
        } else if (type == MessageType.FILE) {
            try {
                JsonObject jsonContent = gson.fromJson(msg.content, JsonObject.class);
                String fileName = jsonContent.has("fileName") ? jsonContent.get("fileName").getAsString() : 
                                 (jsonContent.has("name") ? jsonContent.get("name").getAsString() : "[文件]");
                
                Label label = new Label("[文件] " + fileName);
                label.setFont(Font.font(13));
                label.setStyle("-fx-text-fill: #6b7280;");
                label.setWrapText(true);
                label.setMaxWidth(450);
                return label;
            } catch (Exception e) {
                Label label = new Label("[文件]");
                label.setFont(Font.font(13));
                label.setStyle("-fx-text-fill: #6b7280;");
                return label;
            }
        } else if (type == MessageType.EMOJI) {
            Label label = new Label(msg.content);
            label.setFont(Font.font(24));
            return label;
        } else {
            String previewText = getPreviewText(msg);
            if (previewText != null && previewText.length() > 80) {
                previewText = previewText.substring(0, 80) + "...";
            }
            
            Label label = new Label(previewText != null ? previewText : "");
            label.setFont(Font.font(13));
            label.setStyle("-fx-text-fill: #6b7280;");
            label.setWrapText(true);
            label.setMaxWidth(450);
            return label;
        }
    }
    
    private String formatSearchTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return "";
        }
        
        try {
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timeStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
        } catch (Exception e) {
            return timeStr;
        }
    }
    
    private List<String> getRoomMembers(String roomName) {
        List<String> members = new ArrayList<>();
        
        if (currentConversationId != null) {
            List<ChatMessage> msgs = roomMessages.get(currentRoom);
            if (msgs != null) {
                Set<String> senders = new LinkedHashSet<>();
                for (ChatMessage msg : msgs) {
                    if (msg.from != null && !msg.from.isEmpty() && !msg.from.equals(currentUsername)) {
                        senders.add(msg.from);
                    }
                }
                members.addAll(senders);
            }
        }
        
        members.add(currentUsername);
        
        return members;
    }
    
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    private void handleSystemMessage(ChatMessage message) {
        String targetRoom = currentRoom;
        
        if (message.content != null && message.content.startsWith("{") && message.content.contains("rooms")) {
            try {
                JsonObject responseObj = gson.fromJson(message.content, JsonObject.class);
                if (responseObj.has("rooms")) {
                    JsonArray roomsArray = responseObj.getAsJsonArray("rooms");
                    for (JsonElement roomElement : roomsArray) {
                        JsonObject roomObj = roomElement.getAsJsonObject();
                        String roomName = roomObj.has("name") ? roomObj.get("name").getAsString() : null;
                        int conversationId = roomObj.has("conversation_id") ? roomObj.get("conversation_id").getAsInt() : 0;
                        
                        if (roomName != null && conversationId > 0) {
                            boolean roomExists = roomsList.stream().anyMatch(r -> r.getName().equals(roomName));
                            if (!roomExists) {
                                roomsList.add(new Room(roomName));
                            }
                            roomConversationIds.put(roomName, conversationId);
                            
                            String existingName = localMessageStorage.getConversationNameById(conversationId);
                            if (existingName == null) {
                                localMessageStorage.updateConversation(conversationId, roomName, "", "", "");
                            }
                        }
                    }
                    refreshListViews();
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
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
        
        if (message.content != null && message.content.contains("创建成功")) {
            String roomName = extractRoomNameFromSystemMessage(message.content);
            if (roomName != null && message.conversationId != null) {
                boolean roomExists = roomsList.stream().anyMatch(r -> r.getName().equals(roomName));
                if (!roomExists) {
                    roomsList.add(new Room(roomName));
                    roomConversationIds.put(roomName, message.conversationId);
                    targetRoom = roomName;
                }
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
            
            if (currentRoom != null && targetRoom.equals(currentRoom)) {
                displayMessage(message);
            }
        }
        
        refreshListViews();
    }
    
    private String extractRoomNameFromSystemMessage(String content) {
        if (content == null || !content.contains("房间")) {
            return null;
        }
        
        int startIdx = content.indexOf("房间") + 2;
        int endIdx = content.indexOf("创建成功");
        
        if (startIdx > 0 && endIdx > startIdx) {
            return content.substring(startIdx, endIdx).trim();
        }
        
        return null;
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
                    String avatar = friendObj.has("avatar") ? friendObj.get("avatar").getAsString() : null;
                    Friend friend = new Friend(username, isOnline, avatar);
                    friendsList.add(friend);
                    
                    if (avatar != null && !avatar.isEmpty()) {
                        loadAvatarFromZFile(username, avatar);
                    }
                    
                    if (friendObj.has("conversation_id")) {
                        Integer conversationId = friendObj.get("conversation_id").getAsInt();
                        roomConversationIds.put(username, conversationId);
                        
                        String existingName = localMessageStorage.getConversationNameById(conversationId);
                        if (existingName == null) {
                            localMessageStorage.updateConversation(conversationId, username, "", "", "");
                        }
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
            
            if (username.equals(currentUsername)) {
                if (statusObj.has("avatar")) {
                    String avatar = statusObj.get("avatar").getAsString();
                    if (avatar != null && !avatar.isEmpty()) {
                        new Thread(() -> {
                            try {
                                byte[] avatarData = ZFileService.getInstance().downloadFileWithCache(avatar);
                                Image avatarImage = new Image(new java.io.ByteArrayInputStream(avatarData));
                                avatarCache.put(currentUsername, avatarImage);
                                
                                Platform.runLater(() -> {
                                    userAvatar.setImage(avatarImage);
                                    userAvatar.setClip(new Circle(24, 24, 24));
                                });
                            } catch (Exception e) {
                                System.err.println("更新导航栏头像失败: " + e.getMessage());
                            }
                        }).start();
                    }
                }
            }
            
            for (Friend friend : friendsList) {
                if (friend.getUsername().equals(username)) {
                    friend.setOnline(statusObj.get("isOnline").getAsBoolean());
                    
                    if (statusObj.has("avatar")) {
                        String avatar = statusObj.get("avatar").getAsString();
                        friend.setAvatar(avatar);
                        
                        if (avatar != null && !avatar.isEmpty()) {
                            loadAvatarFromZFile(username, avatar);
                        }
                    }
                    
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
    
    private void handleRoomJoinRequest(ChatMessage message) {
        Platform.runLater(() -> {
            String fromUsername = message.from;
            String roomName = message.content;
            
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("房间加入请求");
            alert.setHeaderText("收到房间加入请求");
            alert.setContentText(fromUsername + " 请求加入房间 " + roomName + "，是否同意？");
            
            ButtonType acceptButton = new ButtonType("同意");
            ButtonType rejectButton = new ButtonType("拒绝");
            
            alert.getButtonTypes().setAll(acceptButton, rejectButton);
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == acceptButton) {
                    webSocketClient.sendRoomJoinResponse(currentUsername, true, roomName, fromUsername);
                } else {
                    webSocketClient.sendRoomJoinResponse(currentUsername, false, roomName, fromUsername);
                }
            }
        });
    }
    
    private void handleRoomJoinResponse(ChatMessage message) {
        Platform.runLater(() -> {
            String content = message.content;
            String[] parts = content.split(":");
            
            if (parts.length >= 2) {
                String response = parts[0];
                String roomName = parts[1];
                
                if ("accept".equalsIgnoreCase(response)) {
                    boolean roomExists = roomsList.stream().anyMatch(r -> r.getName().equals(roomName));
                    if (!roomExists) {
                        roomsList.add(new Room(roomName));
                    }
                    showInfo("房间加入请求已被同意，你已加入房间: " + roomName);
                    refreshListViews();
                } else if ("reject".equalsIgnoreCase(response)) {
                    showInfo("房间加入请求被拒绝");
                }
            }
        });
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
            
            String targetRoom = currentRoom;
            
            if (message.conversationId != null) {
                for (Map.Entry<String, Integer> entry : roomConversationIds.entrySet()) {
                    if (entry.getValue().equals(message.conversationId)) {
                        targetRoom = entry.getKey();
                        break;
                    }
                }
            }
            
            List<ChatMessage> newMessages = new ArrayList<>();
            
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
                
                newMessages.add(historyMsg);
            }
            
            if (!newMessages.isEmpty() && message.conversationId != null) {
                localMessageStorage.saveMessages(newMessages, targetRoom);
            }
            
            renderMessagesFromLocalStorage(targetRoom, message.conversationId);
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
        private String avatar;
        
        public Friend(String username) {
            this.username = username;
            this.online = false;
            this.avatar = null;
        }
        
        public Friend(String username, boolean online) {
            this.username = username;
            this.online = online;
            this.avatar = null;
        }
        
        public Friend(String username, boolean online, String avatar) {
            this.username = username;
            this.online = online;
            this.avatar = avatar;
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
        
        public String getAvatar() {
            return avatar;
        }
        
        public void setAvatar(String avatar) {
            this.avatar = avatar;
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
        private String avatar;
        private int unreadCount;
        private boolean isMuted;
        
        public ListItem(ListItemType type, String name, String displayName) {
            this.type = type;
            this.name = name;
            this.displayName = displayName;
            this.unreadCount = 0;
            this.isMuted = false;
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
        public int getUnreadCount() { return unreadCount; }
        public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
        public boolean isPinned() { return isPinned; }
        public void setPinned(boolean pinned) { isPinned = pinned; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public boolean isMuted() { return isMuted; }
        public void setMuted(boolean muted) { isMuted = muted; }
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
                setGraphic(null);
                setContextMenu(null);
            } else {
                switch (item.getType()) {
                    case MESSAGE:
                        setGraphic(createMessageItemGraphic(item));
                        setContextMenu(createMessageContextMenu(item));
                        break;
                    case CONTACT:
                        setGraphic(createContactItemGraphic(item));
                        setContextMenu(null);
                        break;
                    case CATEGORY:
                        setGraphic(createCategoryItemGraphic(item));
                        setContextMenu(null);
                        return;
                    case FRIEND_REQUEST:
                        setGraphic(createFriendRequestItemGraphic(item));
                        setContextMenu(null);
                        break;
                }
                setStyle(null);
            }
        }
        
        private HBox createMessageItemGraphic(ListItem item) {
            HBox container = new HBox(8);
            container.setPadding(new Insets(8, 12, 8, 12));
            container.setAlignment(Pos.CENTER_LEFT);
            
            ImageView avatarView = createAvatar(item.getName(), false, item.getAvatar());
            avatarView.setFitWidth(44);
            avatarView.setFitHeight(44);
            avatarView.setClip(new Circle(22, 22, 22));
            
            VBox infoBox = new VBox(2);
            infoBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(infoBox, Priority.ALWAYS);
            infoBox.setFillWidth(true);
            
            Label nameLabel = new Label();
            if (item.isPinned()) {
                nameLabel.setText("★ " + item.getDisplayName());
            } else {
                nameLabel.setText(item.getDisplayName());
            }
            nameLabel.setFont(Font.font(14));
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333;");
            nameLabel.setMaxWidth(130);
            nameLabel.setEllipsisString("...");
            infoBox.getChildren().add(nameLabel);
            
            Label previewLabel = new Label(item.getLastMessage() != null ? item.getLastMessage() : "");
            previewLabel.setFont(Font.font(12));
            previewLabel.setStyle("-fx-text-fill: #666;");
            previewLabel.setMaxWidth(130);
            previewLabel.setEllipsisString("...");
            infoBox.getChildren().add(previewLabel);
            
            VBox rightBox = new VBox(3);
            rightBox.setAlignment(Pos.TOP_RIGHT);
            rightBox.setPrefWidth(60);
            rightBox.setMaxWidth(60);
            
            Label timeLabel = new Label(formatMessageTime(item.getLastMessageTime()));
            timeLabel.setFont(Font.font(11));
            timeLabel.setStyle("-fx-text-fill: #999;");
            timeLabel.setAlignment(Pos.CENTER_RIGHT);
            timeLabel.setMaxWidth(58);
            timeLabel.setMinWidth(58);
            rightBox.getChildren().add(timeLabel);
            
            if (item.getUnreadCount() > 0) {
                if (item.isMuted()) {
                    Region redDot = new Region();
                    redDot.setPrefWidth(8);
                    redDot.setPrefHeight(8);
                    redDot.setStyle("-fx-background-color: #ef4444; -fx-background-radius: 4;");
                    rightBox.getChildren().add(redDot);
                } else {
                    Label unreadLabel = new Label(item.getUnreadCount() > 99 ? "99+" : String.valueOf(item.getUnreadCount()));
                    unreadLabel.setFont(Font.font(11));
                    unreadLabel.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 2 6;");
                    unreadLabel.setAlignment(Pos.CENTER);
                    rightBox.getChildren().add(unreadLabel);
                }
            }
            
            container.getChildren().addAll(avatarView, infoBox, rightBox);
            
            return container;
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
        
        private HBox createCategoryItemGraphic(ListItem item) {
            HBox container = new HBox(4);
            container.setPadding(new Insets(6, 12, 6, 12));
            container.setAlignment(Pos.CENTER_LEFT);
            
            Label arrowLabel = new Label(expandedCategories.contains(item.getName()) ? "▼" : "▶");
            arrowLabel.setFont(Font.font(12));
            arrowLabel.setStyle("-fx-text-fill: #999;");
            
            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setFont(Font.font(14));
            nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
            
            container.getChildren().addAll(arrowLabel, nameLabel);
            
            return container;
        }
        
        private HBox createContactItemGraphic(ListItem item) {
            HBox container = new HBox(8);
            container.setPadding(new Insets(6, 12, 6, 20));
            container.setAlignment(Pos.CENTER_LEFT);
            
            ImageView avatarView = createAvatar(item.getName(), false, item.getAvatar());
            avatarView.setFitWidth(40);
            avatarView.setFitHeight(40);
            avatarView.setClip(new Circle(20, 20, 20));
            
            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setFont(Font.font(13));
            nameLabel.setStyle("-fx-text-fill: #333;");
            
            container.getChildren().addAll(avatarView, nameLabel);
            
            return container;
        }
        
        private HBox createFriendRequestItemGraphic(ListItem item) {
            HBox container = new HBox(8);
            container.setPadding(new Insets(6, 12, 6, 20));
            container.setAlignment(Pos.CENTER_LEFT);
            
            ImageView avatarView = createAvatar(item.getDisplayName(), false, null);
            avatarView.setFitWidth(40);
            avatarView.setFitHeight(40);
            avatarView.setClip(new Circle(20, 20, 20));
            
            Label nameLabel = new Label(item.getDisplayName());
            nameLabel.setFont(Font.font(13));
            nameLabel.setStyle("-fx-text-fill: #333;");
            
            container.getChildren().addAll(avatarView, nameLabel);
            
            return container;
        }
    }
    
    private void switchToMessagesMode() {
        isMessagesMode = true;
        navMessagesButton.getStyleClass().add("nav-button-active");
        navContactsButton.getStyleClass().remove("nav-button-active");
        mainListView.setItems(messagesList);
    }
    
    private void switchToContactsMode() {
        isMessagesMode = false;
        navContactsButton.getStyleClass().add("nav-button-active");
        navMessagesButton.getStyleClass().remove("nav-button-active");
        updateContactsList();
        mainListView.setItems(contactsList);
    }
    
    private void handleListItemSelection(ListItem item) {
        if (isSidebarVisible) {
            toggleProfileSidebar();
        }
        
        if (item.getType() == ListItemType.CATEGORY) {
            toggleCategory(item.getName());
            updateContactsList();
        } else if (item.getType() == ListItemType.MESSAGE || item.getType() == ListItemType.CONTACT) {
            boolean isSameRoom = currentRoom != null && currentRoom.equals(item.getName());
            
            if (isSameRoom) {
                messagesVBox.getChildren().clear();
                currentRoom = null;
                currentConversationId = null;
                currentChatLabel.setText("");
                chatContent.setVisible(false);
                chatContent.setManaged(false);
                emptyState.setVisible(true);
                emptyState.setManaged(true);
                return;
            }
            
            currentRoom = item.getName();
            
            if (roomConversationIds.containsKey(currentRoom)) {
                currentConversationId = roomConversationIds.get(currentRoom);
            } else if (item.getConversationId() != null) {
                currentConversationId = item.getConversationId();
            }
            
            if (currentConversationId != null) {
                localMessageStorage.clearUnreadCount(currentConversationId);
            }
            
            renderMessagesFromLocalStorage(currentRoom, currentConversationId);
            
            String lastTime = "0";
            if (currentConversationId != null && localMessageStorage.hasMessages(currentConversationId)) {
                lastTime = localMessageStorage.getLastMessageTime(currentConversationId);
            }
            
            boolean isRoom = roomsList.stream().anyMatch(r -> r.getName().equals(item.getName()));
            if (isRoom) {
                currentChatLabel.setText("当前房间: " + item.getDisplayName());
            } else {
                currentChatLabel.setText("聊天: " + item.getDisplayName());
            }
            
            if (isRoom) {
                webSocketClient.sendJoin(currentUsername, currentRoom, currentConversationId);
            }
            if (currentConversationId != null) {
                webSocketClient.sendRequestHistory(currentUsername, lastTime, currentConversationId);
            }
            
            chatContent.setVisible(true);
            chatContent.setManaged(true);
            emptyState.setVisible(false);
            emptyState.setManaged(false);
            
            refreshListViews();
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
            
            if (convId != null && localMessageStorage.isConversationHidden(convId)) {
                continue;
            }
            
            String displayName = room.getName();
            if (convId != null) {
                String roomNote = localMessageStorage.getRoomNote(convId);
                if (roomNote != null && !roomNote.isEmpty()) {
                    displayName = roomNote;
                }
            }
            
            ListItem item = new ListItem(ListItemType.MESSAGE, room.getName(), displayName);
            item.setConversationId(convId);
            if (convId != null) {
                item.setPinned(localMessageStorage.isConversationPinned(convId));
                item.setUnreadCount(localMessageStorage.getUnreadCount(convId));
                item.setMuted(localMessageStorage.isMuted(convId));
                
                if (roomMessages.containsKey(room.getName()) && !roomMessages.get(room.getName()).isEmpty()) {
                    List<ChatMessage> msgs = roomMessages.get(room.getName());
                    ChatMessage lastMsg = msgs.get(msgs.size() - 1);
                    String sender = lastMsg.from;
                    String content = getPreviewText(lastMsg);
                    if (sender != null && !sender.isEmpty() && !sender.equals(currentUsername)) {
                        item.setLastMessage(sender + ": " + content);
                    } else {
                        item.setLastMessage(content);
                    }
                    item.setLastMessageTime(lastMsg.time);
                }
            }
            
            if (convId != null && item.isPinned()) {
                pinnedItems.add(item);
            } else {
                normalItems.add(item);
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
                item.setAvatar(friend.getAvatar());
                item.setUnreadCount(localMessageStorage.getUnreadCount(convId));
                item.setMuted(localMessageStorage.isMuted(convId));
                
                if (roomMessages.containsKey(friend.getUsername()) && !roomMessages.get(friend.getUsername()).isEmpty()) {
                    List<ChatMessage> msgs = roomMessages.get(friend.getUsername());
                    ChatMessage lastMsg = msgs.get(msgs.size() - 1);
                    item.setLastMessage(getPreviewText(lastMsg));
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
                if (convId != null) {
                    item.setMuted(localMessageStorage.isMuted(convId));
                }
                contactsList.add(item);
            }
        }
        
        ListItem roomsCategory = new ListItem(ListItemType.CATEGORY, "rooms", "房间");
        contactsList.add(roomsCategory);
        
        if (expandedCategories.contains("rooms")) {
            for (Room room : roomsList) {
                Integer convId = roomConversationIds.get(room.getName());
                
                String displayName = room.getName();
                if (convId != null) {
                    String roomNote = localMessageStorage.getRoomNote(convId);
                    if (roomNote != null && !roomNote.isEmpty()) {
                        displayName = roomNote;
                    }
                }
                
                ListItem item = new ListItem(ListItemType.CONTACT, room.getName(), displayName);
                item.setConversationId(convId);
                if (convId != null) {
                    item.setMuted(localMessageStorage.isMuted(convId));
                }
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