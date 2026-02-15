<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>聊天室 - 好友聊天</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>聊天室 - <span id="current-chat-name">加载中...</span></h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <button id="logout-btn" onclick="logout()">退出登录</button>
                    <button id="close-window-btn" onclick="window.close()">关闭窗口</button>
                </div>
            </div>
            
            <div class="chat-content">
                <!-- Messages Area -->
                <div class="messages-panel full-width">
                    <div class="panel-header">
                        <h3 id="current-chat-name-full">加载中...</h3>
                        <div class="chat-controls">
                            <button id="return-to-room-btn" class="return-button" style="display: none;">返回房间</button>
                        </div>
                    </div>
                    <div id="messages-area" class="messages-area">
                        <!-- Messages will be displayed here -->
                    </div>
                    <div class="message-input">
                        <input type="file" id="image-input" accept="image/*" style="display: none;">
                        <input type="file" id="file-input" style="display: none;">
                        <div class="message-input-buttons">
                            <button id="image-btn" title="发送图片">图片</button>
                            <button id="file-btn" title="发送文件">文件</button>
                        </div>
                        <div class="message-input-main">
                            <input type="text" id="message-input" placeholder="输入您的消息...">
                            <button id="send-btn">发送</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Image Preview Modal -->
        <div id="image-modal" class="modal">
            <div class="modal-content image-modal-content">
                <span class="close">&times;</span>
                <div class="nsfw-modal-wrapper">
                    <img id="modal-image" src="" alt="图片预览">
                    <div id="nsfw-warning" class="nsfw-warning" style="display: none;">
                        <div class="nsfw-warning-content">
                            <h3>⚠️ 检测到NSFW内容</h3>
                            <p>此图片包含可能不适当的内容。</p>
                            <div class="nsfw-actions">
                                <button id="send-nsfw-btn">仍然发送</button>
                                <button id="cancel-nsfw-btn">取消</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- File Preview Modal -->
        <div id="file-modal" class="modal">
            <div class="modal-content file-modal-content">
                <span class="close">&times;</span>
                <h3 id="file-title"></h3>
                <div id="file-loading" style="text-align: center; padding: 20px;">加载中...</div>
                <pre id="file-content" class="file-content-display"></pre>
            </div>
        </div>
    </div>

    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
    <script src="js/localStorage.js"></script>
    <script src="js/chat.js"></script>
    <script>
        // Initialize friend chat
        document.addEventListener('DOMContentLoaded', function() {
            // Get friend username from URL parameter
            const urlParams = new URLSearchParams(window.location.search);
            const friendUsername = urlParams.get('friend');
            
            if (friendUsername) {
                // Initialize chat and switch to friend chat
                initChat();
                
                // Wait for chat client to be initialized
                setTimeout(() => {
                    if (window.chatClient) {
                        // Switch to friend chat mode
                        chatClient.switchToFriendChat(friendUsername);
                    }
                }, 500);
            } else {
                // No friend specified, redirect to main page
                window.location.href = 'chat.jsp';
            }
        });
    </script>
</body>
</html>
