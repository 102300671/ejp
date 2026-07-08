package client.gui.ui;

import client.gui.network.ChatWebSocketClient;
import client.gui.protocol.ChatMessage;
import client.gui.protocol.MessageType;
import client.gui.utils.FontUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class MainChatFrame extends JFrame {
    private ChatWebSocketClient webSocketClient;
    private JLabel currentChatLabel;
    private String currentUsername;
    private String currentRoom;
    private String currentConversationId;
    
    private JList<String> roomsList;
    private DefaultListModel<String> roomsListModel;
    private JTextPane messagesPane;
    private JTextField messageInput;
    private JButton sendButton;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton leaveRoomButton;
    
    private Map<String, String> roomConversationIds = new HashMap<>();
    private Map<String, List<ChatMessage>> roomMessages = new HashMap<>();
    private Set<String> seenMessageIds = new HashSet<>();
    private Set<String> roomNames = new HashSet<>();
    private Set<String> friendNames = new HashSet<>();
    private DefaultListModel<String> friendsListModel;
    
    private final Gson gson = new Gson();
    
    public MainChatFrame(ChatWebSocketClient webSocketClient, String username) {
        this.webSocketClient = webSocketClient;
        this.currentUsername = username;
        this.currentRoom = "system";
        
        setTitle("聊天室 - " + username);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        initUI();
        setupMessageListener();
        setupConnectionListener();
        
        webSocketClient.sendListRooms(username);
        webSocketClient.sendRequestFriendList(username);
    }
    
    private void initUI() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(280);
        
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JSplitPane innerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        innerSplitPane.setDividerLocation(150);
        
        JPanel roomsPanel = new JPanel();
        roomsPanel.setLayout(new BorderLayout(5, 5));
        
        JLabel roomsLabel = new JLabel("房间列表");
        roomsLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        roomsPanel.add(roomsLabel, BorderLayout.NORTH);
        
        roomsListModel = new DefaultListModel<>();
        roomsListModel.addElement("system");
        roomsList = new JList<>(roomsListModel);
        roomsList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        roomsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomsList.setSelectedIndex(0);
        roomsList.addListSelectionListener(new RoomSelectionListener());
        
        JScrollPane roomsScrollPane = new JScrollPane(roomsList);
        roomsPanel.add(roomsScrollPane, BorderLayout.CENTER);
        
        JPanel roomsButtonPanel = new JPanel();
        roomsButtonPanel.setLayout(new GridLayout(3, 1, 3, 3));
        
        createRoomButton = new JButton("创建房间");
        createRoomButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        createRoomButton.addActionListener(new CreateRoomListener());
        
        joinRoomButton = new JButton("加入房间");
        joinRoomButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        joinRoomButton.addActionListener(new JoinRoomListener());
        
        leaveRoomButton = new JButton("离开房间");
        leaveRoomButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        leaveRoomButton.addActionListener(new LeaveRoomListener());
        
        roomsButtonPanel.add(createRoomButton);
        roomsButtonPanel.add(joinRoomButton);
        roomsButtonPanel.add(leaveRoomButton);
        roomsPanel.add(roomsButtonPanel, BorderLayout.SOUTH);
        
        innerSplitPane.setTopComponent(roomsPanel);
        
        JPanel friendsPanel = new JPanel();
        friendsPanel.setLayout(new BorderLayout(5, 5));
        
        JLabel friendsLabel = new JLabel("好友列表");
        friendsLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        friendsPanel.add(friendsLabel, BorderLayout.NORTH);
        
        friendsListModel = new DefaultListModel<>();
        JList<String> friendsList = new JList<>(friendsListModel);
        friendsList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        friendsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        friendsList.addListSelectionListener(new FriendSelectionListener());
        
        JScrollPane friendsScrollPane = new JScrollPane(friendsList);
        friendsPanel.add(friendsScrollPane, BorderLayout.CENTER);
        
        JPanel friendsButtonPanel = new JPanel();
        friendsButtonPanel.setLayout(new GridLayout(3, 1, 3, 3));
        
        JButton addFriendButton = new JButton("添加好友");
        addFriendButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        addFriendButton.addActionListener(new AddFriendListener());
        
        JButton friendRequestsButton = new JButton("好友请求");
        friendRequestsButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        friendRequestsButton.addActionListener(new FriendRequestsListener());
        
        JButton refreshFriendsButton = new JButton("刷新好友");
        refreshFriendsButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        refreshFriendsButton.addActionListener(e -> webSocketClient.sendRequestFriendList(currentUsername));
        
        friendsButtonPanel.add(addFriendButton);
        friendsButtonPanel.add(friendRequestsButton);
        friendsButtonPanel.add(refreshFriendsButton);
        friendsPanel.add(friendsButtonPanel, BorderLayout.SOUTH);
        
        innerSplitPane.setBottomComponent(friendsPanel);
        
        leftPanel.add(innerSplitPane, BorderLayout.CENTER);
        
        splitPane.setLeftComponent(leftPanel);
        
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());
        
        this.currentChatLabel = new JLabel("当前房间: " + currentRoom);
        this.currentChatLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        headerPanel.add(this.currentChatLabel, BorderLayout.WEST);
        
        JButton logoutButton = new JButton("退出登录");
        logoutButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        logoutButton.addActionListener(e -> {
            webSocketClient.disconnect();
            dispose();
            ConnectFrame connectFrame = new ConnectFrame();
            connectFrame.setVisible(true);
        });
        headerPanel.add(logoutButton, BorderLayout.EAST);
        
        rightPanel.add(headerPanel, BorderLayout.NORTH);
        
        messagesPane = new JTextPane();
        messagesPane.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        messagesPane.setEditable(false);
        messagesPane.setBackground(new Color(245, 245, 245));
        
        StyledDocument doc = messagesPane.getStyledDocument();
        try {
            Style systemStyle = doc.addStyle("system", null);
            StyleConstants.setForeground(systemStyle, new Color(100, 100, 100));
            StyleConstants.setItalic(systemStyle, true);
            StyleConstants.setAlignment(systemStyle, StyleConstants.ALIGN_CENTER);
            
            Style sentStyle = doc.addStyle("sent", null);
            StyleConstants.setForeground(sentStyle, new Color(255, 255, 255));
            StyleConstants.setBackground(sentStyle, new Color(74, 111, 165));
            StyleConstants.setAlignment(sentStyle, StyleConstants.ALIGN_RIGHT);
            
            Style receivedStyle = doc.addStyle("received", null);
            StyleConstants.setForeground(receivedStyle, new Color(0, 0, 0));
            StyleConstants.setBackground(receivedStyle, new Color(255, 255, 255));
            StyleConstants.setAlignment(receivedStyle, StyleConstants.ALIGN_LEFT);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        JScrollPane messagesScrollPane = new JScrollPane(messagesPane);
        rightPanel.add(messagesScrollPane, BorderLayout.CENTER);
        
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout(5, 5));
        
        messageInput = new JTextField();
        messageInput.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        messageInput.addActionListener(new SendMessageListener());
        
        sendButton = new JButton("发送");
        sendButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        sendButton.setPreferredSize(new Dimension(80, 35));
        sendButton.addActionListener(new SendMessageListener());
        
        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        rightPanel.add(inputPanel, BorderLayout.SOUTH);
        
        splitPane.setRightComponent(rightPanel);
        
        add(splitPane);
    }
    
    private void setupMessageListener() {
        webSocketClient.setMessageListener(message -> {
            SwingUtilities.invokeLater(() -> {
                handleMessage(message);
            });
        });
    }
    
    private void setupConnectionListener() {
        webSocketClient.setConnectionListener(new ChatWebSocketClient.ConnectionListener() {
            @Override
            public void onConnected() {
                SwingUtilities.invokeLater(() -> {
                    webSocketClient.sendLogin(currentUsername, "");
                });
            }
            
            @Override
            public void onDisconnected() {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainChatFrame.this, "连接已断开", "错误", JOptionPane.ERROR_MESSAGE);
                    dispose();
                    ConnectFrame connectFrame = new ConnectFrame();
                    connectFrame.setVisible(true);
                });
            }
            
            @Override
            public void onError(String error) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainChatFrame.this, "连接错误: " + error, "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    private void handleMessage(ChatMessage message) {
        MessageType type = message.getMessageType();
        if (type == null) return;
        
        if (message.getId() != null && seenMessageIds.contains(message.getId())) {
            return;
        }
        seenMessageIds.add(message.getId());
        
        switch (type) {
            case TEXT:
                handleTextMessage(message);
                break;
            case SYSTEM:
                handleSystemMessage(message);
                break;
            case JOIN:
                handleJoinMessage(message);
                break;
            case LEAVE:
                handleLeaveMessage(message);
                break;
            case LIST_ROOM_USERS:
                handleListRooms(message);
                break;
            case HISTORY_RESPONSE:
                handleHistoryResponse(message);
                break;
            case PRIVATE_CHAT:
                handlePrivateChatMessage(message);
                break;
            case FRIEND_LIST:
                handleFriendList(message);
                break;
            case FRIEND_REQUEST:
                handleFriendRequest(message);
                break;
            case FRIEND_REQUEST_RESPONSE:
                handleFriendRequestResponse(message);
                break;
            case ALL_FRIEND_REQUESTS:
                handleAllFriendRequests(message);
                break;
            case USER_STATUS_UPDATE:
                handleUserStatusUpdate(message);
                break;
            case ROOM_JOIN_RESPONSE:
                JOptionPane.showMessageDialog(this, message.getContent(), "房间加入", JOptionPane.INFORMATION_MESSAGE);
                break;
            default:
                break;
        }
    }
    
    private void handleTextMessage(ChatMessage message) {
        String roomName = currentRoom;
        
        if (message.getConversationId() != null) {
            for (Map.Entry<String, String> entry : roomConversationIds.entrySet()) {
                if (entry.getValue().equals(message.getConversationId())) {
                    roomName = entry.getKey();
                    break;
                }
            }
        }
        
        String content = message.getContent();
        if (content != null && content.startsWith("{")) {
            try {
                JsonObject jsonContent = gson.fromJson(content, JsonObject.class);
                if (jsonContent.has("content")) {
                    content = jsonContent.get("content").getAsString();
                }
            } catch (Exception e) {
            }
        }
        
        if (!roomMessages.containsKey(roomName)) {
            roomMessages.put(roomName, new ArrayList<>());
        }
        
        roomMessages.get(roomName).add(message);
        
        if (roomName.equals(currentRoom)) {
            appendMessage(message.getFrom(), content, message.getTime(), message.getFrom().equals(currentUsername));
        }
    }
    
    private void handleSystemMessage(ChatMessage message) {
        String roomName = currentRoom;
        
        if (message.getConversationId() != null) {
            for (Map.Entry<String, String> entry : roomConversationIds.entrySet()) {
                if (entry.getValue().equals(message.getConversationId())) {
                    roomName = entry.getKey();
                    break;
                }
            }
        }
        
        if (!roomMessages.containsKey(roomName)) {
            roomMessages.put(roomName, new ArrayList<>());
        }
        
        roomMessages.get(roomName).add(message);
        
        if (roomName.equals(currentRoom)) {
            appendSystemMessage(message.getContent());
        }
    }
    
    private void handleJoinMessage(ChatMessage message) {
        String roomName = message.getFrom();
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            try {
                JsonObject jsonContent = gson.fromJson(message.getContent(), JsonObject.class);
                if (jsonContent.has("room_name")) {
                    roomName = jsonContent.get("room_name").getAsString();
                }
            } catch (Exception e) {
            }
        }
        
        if (!roomsListModel.contains(roomName)) {
            roomsListModel.addElement(roomName);
        }
        
        appendSystemMessage(message.getFrom() + " 加入了房间 " + roomName);
    }
    
    private void handleLeaveMessage(ChatMessage message) {
        appendSystemMessage(message.getFrom() + " 离开了房间");
    }
    
    private void handleListRooms(ChatMessage message) {
        try {
            JsonObject responseObj = gson.fromJson(message.getContent(), JsonObject.class);
            JsonArray roomsArray = responseObj.getAsJsonArray("rooms");
            
            roomNames.clear();
            roomsListModel.clear();
            roomsListModel.addElement("system");
            roomNames.add("system");
            
            for (JsonElement roomElement : roomsArray) {
                JsonObject roomObj = roomElement.getAsJsonObject();
                String roomName = roomObj.get("name").getAsString();
                int conversationId = roomObj.get("conversation_id").getAsInt();
                
                if (!"system".equals(roomName)) {
                    roomsListModel.addElement(roomName);
                    roomConversationIds.put(roomName, String.valueOf(conversationId));
                    roomNames.add(roomName);
                }
            }
            
            for (String friendName : friendNames) {
                if (!roomsListModel.contains(friendName)) {
                    roomsListModel.addElement(friendName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleHistoryResponse(ChatMessage message) {
        try {
            JsonArray historyArray = gson.fromJson(message.getContent(), JsonArray.class);
            
            for (JsonElement msgElement : historyArray) {
                JsonObject msgObj = msgElement.getAsJsonObject();
                String type = msgObj.get("type").getAsString();
                String from = msgObj.get("from").getAsString();
                String content = msgObj.get("content").getAsString();
                String time = msgObj.get("time").getAsString();
                String id = msgObj.has("id") ? msgObj.get("id").getAsString() : null;
                
                if (id != null && seenMessageIds.contains(id)) {
                    continue;
                }
                if (id != null) {
                    seenMessageIds.add(id);
                }
                
                if ("TEXT".equals(type)) {
                    if (content.startsWith("{")) {
                        try {
                            JsonObject jsonContent = gson.fromJson(content, JsonObject.class);
                            if (jsonContent.has("content")) {
                                content = jsonContent.get("content").getAsString();
                            }
                        } catch (Exception e) {
                        }
                    }
                    appendMessage(from, content, time, from.equals(currentUsername));
                } else if ("SYSTEM".equals(type)) {
                    appendSystemMessage(content);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handlePrivateChatMessage(ChatMessage message) {
        String from = message.getFrom();
        String content = message.getContent();
        String conversationId = null;
        
        if (content.startsWith("{")) {
            try {
                JsonObject jsonContent = gson.fromJson(content, JsonObject.class);
                if (jsonContent.has("content")) {
                    content = jsonContent.get("content").getAsString();
                }
                if (jsonContent.has("conversation_id")) {
                    conversationId = jsonContent.get("conversation_id").getAsString();
                    roomConversationIds.put(from, conversationId);
                }
            } catch (Exception e) {
            }
        }
        
        friendNames.add(from);
        
        if (!roomsListModel.contains(from)) {
            roomsListModel.addElement(from);
        }
        
        if (!roomMessages.containsKey(from)) {
            roomMessages.put(from, new ArrayList<>());
        }
        
        roomMessages.get(from).add(message);
        
        if (from.equals(currentRoom)) {
            appendMessage(from, content, message.getTime(), false);
        }
    }
    
    private void handleFriendRequest(ChatMessage message) {
        String from = message.getFrom();
        String content = message.getContent();
        String messageText = "";
        
        if (content != null && content.startsWith("to:")) {
            int toEnd = content.indexOf(";");
            if (toEnd > 0) {
                messageText = content.substring(toEnd + 1);
            }
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "收到来自 " + from + " 的好友请求\n验证消息: " + messageText + "\n\n是否接受?", 
            "好友请求", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            webSocketClient.sendFriendRequestResponse(currentUsername, from, true);
        } else {
            webSocketClient.sendFriendRequestResponse(currentUsername, from, false);
        }
    }
    
    private void handleFriendRequestResponse(ChatMessage message) {
        String content = message.getContent();
        String response = "";
        String from = "";
        
        if (content != null) {
            String[] parts = content.split(":");
            if (parts.length > 0) {
                response = parts[0].trim().toLowerCase();
            }
            if (parts.length > 1) {
                from = parts[1].trim();
            }
        }
        
        if ("accept".equals(response)) {
            JOptionPane.showMessageDialog(this, from + " 接受了您的好友请求", "好友请求已接受", JOptionPane.INFORMATION_MESSAGE);
            webSocketClient.sendRequestFriendList(currentUsername);
        } else if ("reject".equals(response)) {
            JOptionPane.showMessageDialog(this, from + " 拒绝了您的好友请求", "好友请求已拒绝", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void handleAllFriendRequests(ChatMessage message) {
        try {
            JsonArray requestsArray = gson.fromJson(message.getContent(), JsonArray.class);
            
            if (requestsArray.size() == 0) {
                JOptionPane.showMessageDialog(this, "没有待处理的好友请求", "好友请求", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            StringBuilder requestsText = new StringBuilder("待处理的好友请求:\n\n");
            for (int i = 0; i < requestsArray.size(); i++) {
                JsonObject requestObj = requestsArray.get(i).getAsJsonObject();
                String fromUsername = requestObj.get("from_username").getAsString();
                String time = requestObj.has("created_at") ? requestObj.get("created_at").getAsString() : "";
                requestsText.append((i + 1) + ". ").append(fromUsername);
                if (!time.isEmpty()) {
                    requestsText.append(" (").append(time).append(")");
                }
                requestsText.append("\n");
            }
            
            JOptionPane.showMessageDialog(this, requestsText.toString(), "好友请求", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleFriendList(ChatMessage message) {
        try {
            JsonArray friendsArray = gson.fromJson(message.getContent(), JsonArray.class);
            
            for (JsonElement friendElement : friendsArray) {
                JsonObject friendObj = friendElement.getAsJsonObject();
                String username = friendObj.get("username").getAsString();
                
                friendNames.add(username);
                
                if (!friendsListModel.contains(username)) {
                    friendsListModel.addElement(username);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handleUserStatusUpdate(ChatMessage message) {
        try {
            JsonObject statusData = gson.fromJson(message.getContent(), JsonObject.class);
            String username = statusData.get("username").getAsString();
            boolean isOnline = statusData.get("isOnline").getAsBoolean();
            
            if (!friendsListModel.contains(username) && friendNames.contains(username)) {
                friendsListModel.addElement(username);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void appendMessage(String from, String content, String time, boolean isSent) {
        try {
            StyledDocument doc = messagesPane.getStyledDocument();
            String styleName = isSent ? "sent" : "received";
            
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontSize(attrs, 14);
            
            doc.insertString(doc.getLength(), from + " [" + time + "]\n", attrs);
            doc.insertString(doc.getLength(), content + "\n\n", doc.getStyle(styleName));
            
            messagesPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void appendSystemMessage(String content) {
        try {
            StyledDocument doc = messagesPane.getStyledDocument();
            doc.insertString(doc.getLength(), "[系统] " + content + "\n\n", doc.getStyle("system"));
            messagesPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private void clearMessages() {
        try {
            StyledDocument doc = messagesPane.getStyledDocument();
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    private class RoomSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting() && roomsList.getSelectedValue() != null) {
                String newRoom = roomsList.getSelectedValue();
                if (!newRoom.equals(currentRoom)) {
                    currentRoom = newRoom;
                    currentConversationId = roomConversationIds.get(currentRoom);
                    
                    clearMessages();
                    
                    if (roomMessages.containsKey(currentRoom)) {
                        for (ChatMessage msg : roomMessages.get(currentRoom)) {
                            MessageType type = msg.getMessageType();
                            if (type == MessageType.TEXT) {
                                String content = msg.getContent();
                                if (content.startsWith("{")) {
                                    try {
                                        JsonObject jsonContent = gson.fromJson(content, JsonObject.class);
                                        if (jsonContent.has("content")) {
                                            content = jsonContent.get("content").getAsString();
                                        }
                                    } catch (Exception ex) {
                                    }
                                }
                                appendMessage(msg.getFrom(), content, msg.getTime(), msg.getFrom().equals(currentUsername));
                            } else if (type == MessageType.SYSTEM) {
                                appendSystemMessage(msg.getContent());
                            }
                        }
                    }
                    
                    webSocketClient.sendJoin(currentUsername, currentRoom, currentConversationId);
                    
                    MainChatFrame.this.currentChatLabel.setText("当前房间: " + currentRoom);
                }
            }
        }
    }
    
    private class FriendSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting() && e.getSource() instanceof JList) {
                JList<String> sourceList = (JList<String>) e.getSource();
                String friendName = sourceList.getSelectedValue();
                if (friendName != null && !friendName.equals(currentRoom)) {
                    currentRoom = friendName;
                    currentConversationId = roomConversationIds.get(friendName);
                    
                    clearMessages();
                    
                    if (roomMessages.containsKey(friendName)) {
                        for (ChatMessage msg : roomMessages.get(friendName)) {
                            MessageType type = msg.getMessageType();
                            if (type == MessageType.PRIVATE_CHAT || type == MessageType.TEXT) {
                                String content = msg.getContent();
                                if (content.startsWith("{")) {
                                    try {
                                        JsonObject jsonContent = gson.fromJson(content, JsonObject.class);
                                        if (jsonContent.has("content")) {
                                            content = jsonContent.get("content").getAsString();
                                        }
                                    } catch (Exception ex) {
                                    }
                                }
                                appendMessage(msg.getFrom(), content, msg.getTime(), msg.getFrom().equals(currentUsername));
                            }
                        }
                    }
                    
                    MainChatFrame.this.currentChatLabel.setText("私聊: " + friendName);
                }
            }
        }
    }
    
    private class SendMessageListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String content = messageInput.getText().trim();
            if (content.isEmpty()) return;
            
            if (friendNames.contains(currentRoom)) {
                webSocketClient.sendPrivateChat(currentUsername, content, currentRoom, currentConversationId);
            } else {
                webSocketClient.sendText(currentUsername, content, currentConversationId);
            }
            
            appendMessage(currentUsername, content, ChatMessage.getCurrentTime(), true);
            
            messageInput.setText("");
        }
    }
    
    private class CreateRoomListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String roomName = JOptionPane.showInputDialog(MainChatFrame.this, "请输入房间名称:", "创建房间", JOptionPane.PLAIN_MESSAGE);
            if (roomName != null && !roomName.trim().isEmpty()) {
                String roomType = (String) JOptionPane.showInputDialog(MainChatFrame.this, "请选择房间类型:", "创建房间",
                        JOptionPane.PLAIN_MESSAGE, null, new String[]{"PUBLIC", "PRIVATE"}, "PUBLIC");
                
                if (roomType != null) {
                    webSocketClient.sendCreateRoom(currentUsername, roomName.trim(), roomType);
                }
            }
        }
    }
    
    private class JoinRoomListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String roomName = JOptionPane.showInputDialog(MainChatFrame.this, "请输入要加入的房间名称:", "加入房间", JOptionPane.PLAIN_MESSAGE);
            if (roomName != null && !roomName.trim().isEmpty()) {
                webSocketClient.sendJoin(currentUsername, roomName.trim(), null);
            }
        }
    }
    
    private class LeaveRoomListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentRoom != null && !"system".equals(currentRoom)) {
                int confirm = JOptionPane.showConfirmDialog(MainChatFrame.this, "确定要离开房间 " + currentRoom + " 吗?", "离开房间", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    webSocketClient.sendExitRoom(currentUsername, currentRoom);
                    roomsListModel.removeElement(currentRoom);
                    roomConversationIds.remove(currentRoom);
                    roomMessages.remove(currentRoom);
                    
                    currentRoom = "system";
                    currentConversationId = roomConversationIds.get("system");
                    roomsList.setSelectedIndex(0);
                    clearMessages();
                    
                    MainChatFrame.this.currentChatLabel.setText("当前房间: " + currentRoom);
                }
            }
        }
    }
    
    private class AddFriendListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = JOptionPane.showInputDialog(MainChatFrame.this, "请输入要添加的好友用户名:", "添加好友", JOptionPane.PLAIN_MESSAGE);
            if (username != null && !username.trim().isEmpty()) {
                String message = JOptionPane.showInputDialog(MainChatFrame.this, "请输入验证消息(可选):", "添加好友", JOptionPane.PLAIN_MESSAGE);
                webSocketClient.sendFriendRequest(currentUsername, username.trim(), message);
            }
        }
    }
    
    private class FriendRequestsListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            webSocketClient.sendRequestAllFriendRequests(currentUsername);
        }
    }
}