package client.gui.ui;

import client.gui.network.ChatWebSocketClient;
import client.gui.protocol.ChatMessage;
import client.gui.protocol.MessageType;
import client.gui.utils.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginFrame extends JFrame {
    private ChatWebSocketClient webSocketClient;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;
    
    public LoginFrame(ChatWebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        
        setTitle("聊天室 - 登录");
        setSize(400, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        
        initUI();
        setupMessageListener();
    }
    
    private void initUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel titleLabel = new JLabel("聊天室", JLabel.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleLabel.setForeground(new Color(74, 111, 165));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(new JLabel("用户名:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        usernameField = new JTextField(20);
        usernameField.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        formPanel.add(usernameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("密码:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        formPanel.add(passwordField, gbc);
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        loginButton = new JButton("登录");
        loginButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        loginButton.setPreferredSize(new Dimension(100, 35));
        loginButton.addActionListener(new LoginButtonListener());
        
        registerButton = new JButton("注册");
        registerButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        registerButton.setPreferredSize(new Dimension(100, 35));
        registerButton.addActionListener(new RegisterButtonListener());
        
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        statusLabel = new JLabel("", JLabel.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        mainPanel.add(statusLabel, BorderLayout.EAST);
        
        add(mainPanel);
    }
    
    private void setupMessageListener() {
        webSocketClient.setMessageListener(message -> {
            SwingUtilities.invokeLater(() -> {
                MessageType type = message.getMessageType();
                if (type == MessageType.AUTH_SUCCESS) {
                    webSocketClient.setAuthenticated(true);
                    dispose();
                    MainChatFrame mainChatFrame = new MainChatFrame(webSocketClient, usernameField.getText());
                    mainChatFrame.setVisible(true);
                } else if (type == MessageType.AUTH_FAILURE) {
                    statusLabel.setText("认证失败: " + message.getContent());
                    statusLabel.setForeground(Color.RED);
                    loginButton.setEnabled(true);
                    registerButton.setEnabled(true);
                }
            });
        });
    }
    
    private class LoginButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(LoginFrame.this, "请填写用户名和密码", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            loginButton.setEnabled(false);
            registerButton.setEnabled(false);
            statusLabel.setText("登录中...");
            statusLabel.setForeground(new Color(74, 111, 165));
            
            webSocketClient.sendLogin(username, password);
        }
    }
    
    private class RegisterButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(LoginFrame.this, "请填写用户名和密码", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            loginButton.setEnabled(false);
            registerButton.setEnabled(false);
            statusLabel.setText("注册中...");
            statusLabel.setForeground(new Color(74, 111, 165));
            
            webSocketClient.sendRegister(username, password);
        }
    }
}