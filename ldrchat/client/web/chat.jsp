<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>💑 异地恋专属聊天室</title>
    <link rel="stylesheet" href="css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>💗 亲爱的，我想你了 💗</h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <div class="user-menu">
                        <button id="user-menu-btn" class="user-menu-btn">
                            <img id="user-avatar" src="" alt="User Avatar" class="user-avatar-small">
                        </button>
                        <div id="user-menu-dropdown" class="user-menu-dropdown">
                            <button id="view-profile-btn">👤 个人资料</button>
                            <button id="view-settings-btn">⚙️ 设置</button>
                            <button id="logout-btn">🚪 退出登录</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- 情侣专属状态栏 -->
            <div class="couple-status-bar">
                <div class="countdown-section">
                    <span class="countdown-label">💕 恋爱天数</span>
                    <span id="love-days" class="love-days">0</span>
                </div>
                <div class="heartbeat-animation">
                    <span class="heart-icon">❤️</span>
                </div>
                <div class="distance-section">
                    <span class="distance-label">📍 距离</span>
                    <span id="distance" class="distance">-</span>
                </div>
            </div>
            
            <!-- CP未绑定时显示绑定界面 -->
            <div id="couple-bind-panel" class="couple-bind-panel" style="display: none;">
                <div class="bind-content">
                    <div class="bind-icon">💔</div>
                    <h3>还没有绑定CP</h3>
                    <p>绑定你的另一半，开启专属聊天</p>
                    <button id="bind-cp-btn" class="bind-cp-btn">💝 绑定CP</button>
                    
                    <!-- CP请求提示 -->
                    <div id="cp-request-pending" class="cp-request-pending" style="display: none;">
                        <p>💕 等待对方确认中...</p>
                    </div>
                    
                    <!-- 收到的CP请求 -->
                    <div id="cp-request-received" class="cp-request-received" style="display: none;">
                        <p>💕 收到来自 <span id="request-sender"></span> 的绑定请求</p>
                        <div class="request-actions">
                            <button id="accept-cp-btn" class="accept-cp-btn">接受</button>
                            <button id="reject-cp-btn" class="reject-cp-btn">拒绝</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- CP已绑定时显示聊天界面 -->
            <div id="couple-chat-panel" class="couple-chat-panel">
                <div class="chat-content">
                    <!-- Messages Area -->
                    <div class="messages-panel">
                        <div class="panel-header">
                            <h3 id="current-chat-name">💑 我的CP</h3>
                            <div class="chat-controls">
                                <button id="add-friend-btn">🔍 搜索CP</button>
                                <button id="unbind-cp-btn" class="unbind-cp-btn">💔 解绑CP</button>
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
                                <input type="text" id="message-input" placeholder="输入想对TA说的话...">
                                <button id="send-btn">发送</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- CP搜索 Modal -->
        <div id="cp-search-modal" class="modal">
            <div class="modal-content cp-search-modal-content">
                <span class="close">&times;</span>
                <h3>💕 搜索你的CP</h3>
                <div class="search-input-container">
                    <input type="text" id="cp-search-input" placeholder="输入TA的用户名...">
                    <button id="search-cp-btn">搜索</button>
                </div>
                <div id="cp-search-results" class="cp-search-results">
                    <!-- Search results will be displayed here -->
                </div>
            </div>
        </div>
        
        <!-- 解绑确认 Modal -->
        <div id="unbind-confirm-modal" class="modal">
            <div class="modal-content">
                <span class="close">&times;</span>
                <h3>💔 确认解绑</h3>
                <p>确定要解除与TA的CP绑定吗？此操作不可撤销。</p>
                <div class="form-group">
                    <button id="confirm-unbind-btn" class="confirm-unbind-btn">确认解绑</button>
                    <button id="cancel-unbind-btn" class="cancel-unbind-btn">取消</button>
                </div>
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
    </div>
    
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
    <script src="js/localStorage.js"></script>
    <script src="js/localStorage_clear.js"></script>
    <script src="js/lantern-festival.js"></script>
    <script src="js/womens-day.js"></script>
    <script src="js/chat.js"></script>
    <script>
        // Initialize the chat functionality
        document.addEventListener('DOMContentLoaded', function() {
            initChat();
        });
    </script>
</body>
</html>