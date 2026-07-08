package client.gui.ui;

import client.gui.network.ChatWebSocketClient;
import client.gui.utils.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConnectFrame extends JFrame {
    private JTextField serverIpField;
    private JTextField portField;
    private JComboBox<String> protocolComboBox;
    private JButton connectButton;
    private JLabel statusLabel;
    
    public ConnectFrame() {
        setTitle("聊天室 - 连接服务器");
        setSize(400, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        
        initUI();
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
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(new JLabel("服务器IP:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        serverIpField = new JTextField("localhost", 20);
        serverIpField.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        formPanel.add(serverIpField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("WebSocket端口:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        portField = new JTextField("8889", 20);
        portField.setFont(FontUtils.getChineseFont(Font.PLAIN, 14));
        formPanel.add(portField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(new JLabel("协议:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        protocolComboBox = new JComboBox<>(new String[]{"ws", "wss"});
        formPanel.add(protocolComboBox, gbc);
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        
        connectButton = new JButton("连接");
        connectButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        connectButton.setPreferredSize(new Dimension(120, 35));
        connectButton.addActionListener(new ConnectButtonListener());
        
        buttonPanel.add(connectButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        statusLabel = new JLabel("准备连接...", JLabel.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(102, 102, 102));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        mainPanel.add(statusLabel, BorderLayout.EAST);
        
        add(mainPanel);
    }
    
    private class ConnectButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String serverIp = serverIpField.getText().trim();
            String portStr = portField.getText().trim();
            String protocol = (String) protocolComboBox.getSelectedItem();
            
            if (serverIp.isEmpty() || portStr.isEmpty()) {
                JOptionPane.showMessageDialog(ConnectFrame.this, "请填写服务器地址和端口", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            int port;
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(ConnectFrame.this, "无效的端口号", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            connectButton.setEnabled(false);
            connectButton.setText("连接中...");
            statusLabel.setText("连接到 " + serverIp + ":" + port + "...");
            statusLabel.setForeground(new Color(74, 111, 165));
            
            ChatWebSocketClient webSocketClient = new ChatWebSocketClient(serverIp, port, protocol);
            webSocketClient.setConnectionListener(new ChatWebSocketClient.ConnectionListener() {
                @Override
                public void onConnected() {
                    SwingUtilities.invokeLater(() -> {
                        dispose();
                        LoginFrame loginFrame = new LoginFrame(webSocketClient);
                        loginFrame.setVisible(true);
                    });
                }
                
                @Override
                public void onDisconnected() {
                    SwingUtilities.invokeLater(() -> {
                        connectButton.setEnabled(true);
                        connectButton.setText("连接");
                        statusLabel.setText("连接已断开");
                        statusLabel.setForeground(Color.RED);
                    });
                }
                
                @Override
                public void onError(String error) {
                    SwingUtilities.invokeLater(() -> {
                        connectButton.setEnabled(true);
                        connectButton.setText("连接");
                        statusLabel.setText("连接失败: " + error);
                        statusLabel.setForeground(Color.RED);
                        JOptionPane.showMessageDialog(ConnectFrame.this, "连接失败: " + error, "错误", JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
            
            new Thread(() -> webSocketClient.connect()).start();
        }
    }
}