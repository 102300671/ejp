<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>聊天室 - 用户资料</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>用户资料</h2>
                <div class="header-actions">
                    <button id="back-to-chat-btn">返回聊天</button>
                </div>
            </div>
            
            <div class="profile-container">
                <div class="profile-header">
                    <div class="avatar-section">
                        <div class="avatar-wrapper">
                            <img id="profile-avatar" src="" alt="用户头像" class="avatar-large">
                            <button id="change-avatar-btn" class="change-avatar-btn">更换</button>
                        </div>
                        <div class="status-indicator" id="profile-status"></div>
                    </div>
                    
                    <div class="profile-info">
                        <h3 id="profile-username">用户名</h3>
                        <p id="profile-display-name" class="display-name">显示名称</p>
                        <p id="profile-status-text" class="status-text">在线</p>
                    </div>
                </div>
                
                <input type="file" id="avatar-input" accept="image/*" style="display: none;">
                
                <div class="profile-stats">
                    <div class="stat-item">
                        <div class="stat-value" id="stat-messages">0</div>
                        <div class="stat-label">消息</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value" id="stat-rooms">0</div>
                        <div class="stat-label">房间</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-value" id="stat-online-time">0h</div>
                        <div class="stat-label">在线时长</div>
                    </div>
                </div>
                
                <div class="profile-details">
                    <div class="detail-section">
                        <h4>账户信息</h4>
                        <div class="detail-row">
                            <span class="detail-label">用户名:</span>
                            <span class="detail-value" id="detail-username">-</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">显示名称:</span>
                            <span class="detail-value" id="detail-display-name">-</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">邮箱:</span>
                            <span class="detail-value" id="detail-email">-</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">注册时间:</span>
                            <span class="detail-value" id="detail-joined">-</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">最后活跃:</span>
                            <span class="detail-value" id="detail-last-active">-</span>
                        </div>
                    </div>
                    
                    <div class="detail-section">
                        <h4>活动</h4>
                        <div class="detail-row">
                            <span class="detail-label">状态:</span>
                            <span class="detail-value" id="detail-status">-</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">总消息数:</span>
                            <span class="detail-value" id="detail-total-messages">0</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">加入的房间:</span>
                            <span class="detail-value" id="detail-rooms-joined">0</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">发送的图片:</span>
                            <span class="detail-value" id="detail-images-sent">0</span>
                        </div>
                        <div class="detail-row">
                            <span class="detail-label">分享的文件:</span>
                            <span class="detail-value" id="detail-files-shared">0</span>
                        </div>
                    </div>
                </div>
                
                <div class="profile-actions">
                    <button id="edit-profile-btn" class="action-btn primary">编辑资料</button>
                    <button id="view-settings-btn" class="action-btn secondary">设置</button>
                </div>
            </div>
        </div>
    </div>
    
    <script src="js/localStorage.js"></script>
    <script src="js/chat.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            // 先初始化WebSocket连接（与chat页面相同的初始化逻辑）
            const username = sessionStorage.getItem('username') || localStorage.getItem('username');
            if (username) {
                // 设置用户名
                chatClient.username = username;
                
                // 初始化消息同步机制
                chatClient.initMessageSync();
                
                // 初始化消息持久化
                chatClient.initMessagePersistence();
                
                // 建立WebSocket连接
                chatClient.connect();
                
                // 添加关闭窗口时清理子窗口的事件监听
                window.addEventListener('beforeunload', function() {
                    chatClient.closeAllChildWindows();
                });
            }
            
            // 然后初始化用户资料页面
            initUserProfile();
        });
    </script>
</body>
</html>
