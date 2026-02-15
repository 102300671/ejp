<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>聊天室 - 设置</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>设置</h2>
                <div class="header-actions">
                    <button id="back-to-chat-btn">返回聊天</button>
                </div>
            </div>
            
            <div class="settings-container">
                    <div class="settings-tabs">
                        <button class="settings-tab-btn active" data-tab="account">账户</button>
                        <button class="settings-tab-btn" data-tab="appearance">外观</button>
                        <button class="settings-tab-btn" data-tab="notifications">通知</button>
                        <button class="settings-tab-btn" data-tab="privacy">隐私</button>
                        <button class="settings-tab-btn" data-tab="storage">存储</button>
                    </div>
                
                <div class="settings-content">
                    <div id="tab-account" class="settings-tab-content active">
                        <div class="settings-section">
                            <h3>账户设置</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>修改密码</h4>
                                    <p>更新密码以保护账户安全</p>
                                </div>
                                <button id="change-password-btn" class="settings-btn">修改</button>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>邮箱地址</h4>
                                    <p>更新邮箱用于账户恢复</p>
                                </div>
                                <button id="change-email-btn" class="settings-btn">编辑</button>
                            </div>
                            
                            <div class="settings-item danger">
                                <div class="settings-item-info">
                                    <h4>删除账户</h4>
                                    <p>永久删除账户和所有数据</p>
                                </div>
                                <button id="delete-account-btn" class="settings-btn danger-btn">删除</button>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-appearance" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>外观设置</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>主题</h4>
                                    <p>选择您喜欢的颜色主题</p>
                                </div>
                                <select id="theme-select" class="settings-select">
                                    <option value="light">浅色</option>
                                    <option value="dark">深色</option>
                                    <option value="auto">自动（跟随系统）</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>字体大小</h4>
                                    <p>调整文本大小以提高可读性</p>
                                </div>
                                <select id="font-size-select" class="settings-select">
                                    <option value="small">小</option>
                                    <option value="medium" selected>中</option>
                                    <option value="large">大</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>消息气泡</h4>
                                    <p>选择消息气泡样式</p>
                                </div>
                                <select id="bubble-style-select" class="settings-select">
                                    <option value="rounded">圆角</option>
                                    <option value="square">方形</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>显示时间戳</h4>
                                    <p>在消息上显示时间戳</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="show-timestamps" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-notifications" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>通知设置</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>桌面通知</h4>
                                    <p>当您不在应用中时接收通知</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="desktop-notifications">
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>声音通知</h4>
                                    <p>收到新消息时播放声音</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="sound-notifications" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>消息预览</h4>
                                    <p>在通知中显示消息内容</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="message-preview" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>静音所有通知</h4>
                                    <p>临时禁用所有通知</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="mute-all">
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-privacy" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>隐私设置</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>接受临时聊天</h4>
                                    <p>允许非好友向您发送临时聊天消息</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="accept-temporary-chat" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>在线状态</h4>
                                    <p>向其他用户显示您的在线状态</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="show-online-status" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>已读回执</h4>
                                    <p>让其他人知道您已阅读他们的消息</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="read-receipts" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>资料可见性</h4>
                                    <p>控制谁可以看到您的资料</p>
                                </div>
                                <select id="profile-visibility" class="settings-select">
                                    <option value="everyone">所有人</option>
                                    <option value="contacts">仅联系人</option>
                                    <option value="private">私密</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>NSFW内容</h4>
                                    <p>允许查看NSFW内容</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="allow-nsfw">
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-storage" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>存储设置</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>消息存储</h4>
                                    <p>本地存储消息以便离线访问</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="message-storage" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>存储类型</h4>
                                    <p>选择消息的存储方式</p>
                                </div>
                                <select id="storage-type" class="settings-select">
                                    <option value="indexeddb">IndexedDB（推荐）</option>
                                    <option value="localstorage">localStorage</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>每个房间最大消息数</h4>
                                    <p>限制每个房间存储的消息数量</p>
                                </div>
                                <select id="max-messages" class="settings-select">
                                    <option value="100">100</option>
                                    <option value="200" selected>200</option>
                                    <option value="500">500</option>
                                    <option value="1000">1000</option>
                                </select>
                            </div>
                            
                            <div class="settings-item danger">
                                <div class="settings-item-info">
                                    <h4>清除所有数据</h4>
                                    <p>删除所有本地存储的消息和设置</p>
                                </div>
                                <button id="clear-data-btn" class="settings-btn danger-btn">清除</button>
                            </div>
                        </div>
                    </div>
                </div>
                
                <div class="settings-footer">
                    <button id="save-settings-btn" class="action-btn primary">保存设置</button>
                    <button id="reset-settings-btn" class="action-btn secondary">重置为默认</button>
                </div>
            </div>
        </div>
    </div>
    
    <script src="js/chat.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            initUserSettings();
        });
    </script>
</body>
</html>
