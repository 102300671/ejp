<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>ChatRoom - Main</title>
    <link rel="stylesheet" href="css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>ChatRoom</h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <div class="user-menu">
                        <button id="user-menu-btn" class="user-menu-btn">
                            <img id="user-avatar" src="" alt="User Avatar" class="user-avatar-small">
                        </button>
                        <div id="user-menu-dropdown" class="user-menu-dropdown">
                            <button id="view-profile-btn">👤 Profile</button>
                            <button id="view-settings-btn">⚙️ Settings</button>
                            <button id="logout-btn">🚪 Logout</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="chat-content">
                <!-- Rooms List -->
                <div class="rooms-panel">
                    <div class="panel-header">
                        <h3>Chat</h3>
                        <button id="create-room-btn">Create Room</button>
                    </div>
                    <div id="rooms-list" class="rooms-list">
                        <!-- Rooms will be populated here -->
                    </div>
                    <div class="panel-footer">
                        <button id="refresh-rooms-btn">Refresh</button>
                    </div>
                </div>
                
                <!-- Messages Area -->
                <div class="messages-panel">
                    <div class="panel-header">
                        <h3 id="current-room-name">system</h3>
                        <div class="room-controls">
                                <button id="join-room-btn">Join</button>
                                <button id="leave-room-btn">Leave</button>
                                <button id="exit-room-btn">Exit</button>
                                <button id="add-friend-btn">Add</button>
                            </div>
                    </div>
                    <div id="messages-area" class="messages-area">
                        <!-- Messages will be displayed here -->
                    </div>
                    <div class="message-input">
                        <input type="file" id="image-input" accept="image/*" style="display: none;">
                        <input type="file" id="file-input" style="display: none;">
                        <div class="message-input-buttons">
                            <button id="image-btn" title="Send Image">Image</button>
                            <button id="file-btn" title="Send File">File</button>
                        </div>
                        <div class="message-input-main">
                            <input type="text" id="message-input" placeholder="Type your message...">
                            <button id="send-btn">Send</button>
                            <button id="private-msg-btn">Members</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Create Room Modal -->
        <div id="create-room-modal" class="modal">
            <div class="modal-content">
                <span class="close">&times;</span>
                <h3>Create New Room</h3>
                <form id="create-room-form">
                    <div class="form-group">
                        <label for="room-name">Room Name:</label>
                        <input type="text" id="room-name" required>
                    </div>
                    <div class="form-group">
                        <label for="room-type">Room Type:</label>
                        <select id="room-type">
                            <option value="PUBLIC">Public</option>
                            <option value="PRIVATE">Private</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <button type="submit">Create</button>
                    </div>
                </form>
            </div>
        </div>
        
        <!-- Join Room Modal -->
        <div id="join-room-modal" class="modal">
            <div class="modal-content">
                <span class="close">&times;</span>
                <h3>Join Room</h3>
                <form id="join-room-form">
                    <div class="form-group">
                        <label for="join-room-name">Room Name:</label>
                        <input type="text" id="join-room-name" required placeholder="Enter room name to join">
                    </div>
                    <div class="form-group">
                        <button type="submit">Join</button>
                    </div>
                </form>
            </div>
        </div>
        
        <!-- Image Preview Modal -->
        <div id="image-modal" class="modal">
            <div class="modal-content image-modal-content">
                <span class="close">&times;</span>
                <div class="nsfw-modal-wrapper">
                    <img id="modal-image" src="" alt="图片预览">
                    <button id="modal-nsfw-toggle-btn" class="nsfw-toggle-btn" style="display: none;">显示NSFW内容</button>
                </div>
            </div>
        </div>
        
        <!-- Image Upload Preview Modal -->
        <div id="image-upload-modal" class="modal">
            <div class="modal-content">
                <span class="close">&times;</span>
                <h3>图片预览</h3>
                <div class="image-preview-container">
                    <img id="upload-preview-image" src="" alt="图片预览">
                </div>
                <div class="form-group">
                    <label class="nsfw-checkbox-label">
                        <input type="checkbox" id="nsfw-checkbox">
                        <span>标记为NSFW（敏感内容）</span>
                    </label>
                </div>
                <div class="nsfw-warning" id="nsfw-warning" style="display: none;">
                    <div class="warning-icon">⚠️</div>
                    <div class="warning-content">
                        <strong>重要提示</strong>
                        <p>NSFW内容将被加密传输并默认模糊显示</p>
                        <p class="prohibited-content">禁止内容：</p>
                        <ul class="prohibited-list">
                            <li>未成年内容</li>
                            <li>非自愿内容</li>
                            <li>非法内容</li>
                            <li>暴力、血腥内容</li>
                        </ul>
                        <p class="audit-notice">服务器将记录所有NSFW内容用于审核</p>
                    </div>
                </div>
                <div class="form-group">
                    <button type="button" id="cancel-upload-btn">取消</button>
                    <button type="button" id="confirm-upload-btn">发送</button>
                </div>
            </div>
        </div>
        
        <!-- File View Modal -->
        <div id="file-modal" class="modal">
            <div class="modal-content file-modal-content">
                <span class="close" onclick="document.getElementById('file-modal').style.display='none'">&times;</span>
                <h3 id="file-title"></h3>
                <div id="file-loading" style="text-align: center; padding: 20px;">加载中...</div>
                <pre id="file-content" class="file-content-display"></pre>
            </div>
        </div>
        
        <!-- User Search Modal -->
        <div id="user-search-modal" class="modal">
            <div class="modal-content user-search-modal-content">
                <span class="close" onclick="document.getElementById('user-search-modal').style.display='none'">&times;</span>
                <h3>Search Users</h3>
                <div class="search-input-container">
                    <input type="text" id="user-search-input" placeholder="Enter username to search...">
                    <button id="search-users-btn">Search</button>
                </div>
                <div class="modal-tabs">
                    <button class="tab-btn active" data-tab="search">Search Results</button>
                    <button class="tab-btn" data-tab="requests">Friend Requests</button>
                </div>
                <div id="search-results" class="user-search-results">
                    <!-- Search results will be displayed here -->
                </div>
                <div id="friend-requests" class="friend-requests-list" style="display: none;">
                    <!-- Friend requests will be displayed here -->
                </div>
            </div>
        </div>
        
        <!-- Room Search Modal -->
        <div id="room-search-modal" class="modal">
            <div class="modal-content room-search-modal-content">
                <span class="close" onclick="document.getElementById('room-search-modal').style.display='none'">&times;</span>
                <h3>Search Rooms</h3>
                <div class="search-input-container">
                    <input type="text" id="room-search-input" placeholder="Enter room name to search...">
                    <button id="search-rooms-btn">Search</button>
                </div>
                <div class="modal-tabs">
                    <button class="tab-btn active" data-tab="search">Search Results</button>
                    <button class="tab-btn" data-tab="requests">Room Requests</button>
                </div>
                <div id="room-search-results" class="room-search-results">
                    <!-- Search results will be displayed here -->
                </div>
                <div id="room-requests" class="room-requests-list" style="display: none;">
                    <!-- Room requests will be displayed here -->
                </div>
            </div>
        </div>
    </div>
    
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
    <script src="js/localStorage.js"></script>
    <script src="js/chat.js"></script>
    <script>
        // Initialize the chat functionality
        document.addEventListener('DOMContentLoaded', function() {
            initChat();
        });
    </script>
</body>
</html>