<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>社团群聊 - 学生社团管理系统</title>
    <link rel="stylesheet" href="css/style.css">
    <style>
        .club-chat-container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
            height: calc(100vh - 100px);
        }
        .club-chat-layout {
            display: flex;
            gap: 20px;
            height: 100%;
        }
        .chat-main {
            flex: 1;
            display: flex;
            flex-direction: column;
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            overflow: hidden;
        }
        .chat-sidebar {
            width: 280px;
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            display: flex;
            flex-direction: column;
        }
        .sidebar-section {
            padding: 15px;
            border-bottom: 1px solid #eee;
        }
        .sidebar-section h3 {
            margin: 0 0 10px 0;
            font-size: 14px;
            color: #666;
        }
        .club-info-header {
            text-align: center;
            padding: 20px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        .club-logo-large {
            width: 80px;
            height: 80px;
            border-radius: 50%;
            background: white;
            color: #667eea;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 36px;
            margin: 0 auto 15px;
        }
        .club-name-large {
            font-size: 20px;
            font-weight: bold;
            margin-bottom: 5px;
        }
        .member-list {
            flex: 1;
            overflow-y: auto;
            padding: 10px;
        }
        .member-item {
            display: flex;
            align-items: center;
            padding: 10px;
            border-radius: 4px;
            margin-bottom: 5px;
            transition: background 0.2s;
        }
        .member-item:hover {
            background: #f0f0f0;
        }
        .member-avatar {
            width: 36px;
            height: 36px;
            border-radius: 50%;
            background: #007bff;
            color: white;
            display: flex;
            align-items: center;
            justify-content: center;
            margin-right: 10px;
            font-size: 14px;
        }
        .member-name {
            flex: 1;
            font-size: 14px;
        }
        .member-role {
            font-size: 10px;
            padding: 2px 8px;
            border-radius: 10px;
            background: #e9ecef;
            color: #666;
        }
        .role-president {
            background: #ffc107;
            color: #333;
        }
        .role-admin {
            background: #17a2b8;
            color: white;
        }
        .activity-preview {
            background: #f8f9fa;
            border-radius: 4px;
            padding: 10px;
            margin-bottom: 10px;
        }
        .activity-preview h4 {
            margin: 0 0 5px 0;
            font-size: 14px;
        }
        .activity-preview p {
            margin: 0;
            font-size: 12px;
            color: #666;
        }
        .back-btn {
            background: #6c757d;
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 4px;
            cursor: pointer;
            margin-bottom: 10px;
        }
        .announcement-list {
            max-height: 200px;
            overflow-y: auto;
        }
        .announcement-item {
            padding: 10px;
            background: #fff3cd;
            border-radius: 4px;
            margin-bottom: 10px;
            border-left: 3px solid #ffc107;
        }
        .announcement-item h4 {
            margin: 0 0 5px 0;
            font-size: 14px;
        }
        .announcement-item p {
            margin: 0;
            font-size: 12px;
            color: #666;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>社团群聊 - <span id="club-name">加载中...</span></h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <button onclick="window.location.href='clubs.jsp'">返回社团列表</button>
                    <button id="logout-btn" onclick="logout()">退出登录</button>
                </div>
            </div>
            
            <div class="chat-content">
                <div class="club-chat-container">
                    <div class="club-chat-layout">
                        <!-- 侧边栏 -->
                        <div class="chat-sidebar">
                            <div class="club-info-header">
                                <div class="club-logo-large" id="club-logo">计</div>
                                <div class="club-name-large" id="club-name-sidebar">计算机协会</div>
                                <div class="club-category" style="color: rgba(255,255,255,0.8);">学术科技</div>
                            </div>
                            
                            <!-- 社团公告 -->
                            <div class="sidebar-section">
                                <h3>📢 社团公告</h3>
                                <div class="announcement-list" id="announcement-list">
                                    <div class="announcement-item">
                                        <h4>欢迎加入计算机协会</h4>
                                        <p>请各位成员积极参与社团活动</p>
                                    </div>
                                </div>
                            </div>
                            
                            <!-- 近期活动 -->
                            <div class="sidebar-section">
                                <h3>🎯 近期活动</h3>
                                <div id="activity-preview-list">
                                    <div class="activity-preview">
                                        <h4>Python培训</h4>
                                        <p>6月20日 14:00 | 教学楼A101</p>
                                    </div>
                                </div>
                            </div>
                            
                            <!-- 成员列表 -->
                            <div class="sidebar-section" style="flex: 1; overflow: hidden; display: flex; flex-direction: column; padding-bottom: 0;">
                                <h3>👥 成员列表 (<span id="member-count">0</span>)</h3>
                                <div class="member-list" id="member-list"></div>
                            </div>
                            
                            <!-- 操作按钮 -->
                            <div class="sidebar-section" style="border-bottom: none;">
                                <button class="back-btn" onclick="leaveClub()" style="width: 100%;">退出社团</button>
                            </div>
                        </div>
                        
                        <!-- 主聊天区域 -->
                        <div class="chat-main">
                            <div class="panel-header">
                                <h3 id="current-chat-name-full">社团群聊</h3>
                                <div class="chat-controls">
                                    <button id="view-activity-btn" onclick="viewClubActivities()">查看活动</button>
                                </div>
                            </div>
                            <div id="messages-area" class="messages-area"></div>
                            <div class="message-input">
                                <input type="file" id="image-input" accept="image/*" style="display: none;">
                                <div class="message-input-buttons">
                                    <button id="image-btn" title="发送图片">图片</button>
                                </div>
                                <div class="message-input-main">
                                    <input type="text" id="message-input" placeholder="输入您的消息...">
                                    <button id="send-btn">发送</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    
    <script>
        let clubId = null;
        let clubName = '';
        let currentUser = null;
        let members = [];
        let messages = [];
        
        // 初始化
        document.addEventListener('DOMContentLoaded', function() {
            const urlParams = new URLSearchParams(window.location.search);
            clubId = urlParams.get('club');
            
            if (!clubId) {
                alert('无效的社团ID');
                window.location.href = 'clubs.jsp';
                return;
            }
            
            loadCurrentUser();
            loadClubInfo();
            loadMembers();
            loadMessages();
        });
        
        function loadCurrentUser() {
            const stored = localStorage.getItem('chatUser');
            if (stored) {
                currentUser = JSON.parse(stored);
                document.getElementById('current-user').textContent = currentUser.username;
            }
        }
        
        function loadClubInfo() {
            // 模拟数据
            const clubs = {
                '1': { name: '计算机协会', category: '学术科技', initial: '计' },
                '2': { name: '音乐社', category: '文化艺术', initial: '音' },
                '3': { name: '篮球俱乐部', category: '体育健身', initial: '篮' },
                '4': { name: '志愿者协会', category: '志愿服务', initial: '志' }
            };
            
            clubName = clubs[clubId]?.name || '未知社团';
            document.getElementById('club-name').textContent = clubName;
            document.getElementById('club-name-sidebar').textContent = clubName;
            document.getElementById('current-chat-name-full').textContent = clubName;
            document.getElementById('club-logo').textContent = clubs[clubId]?.initial || '?';
        }
        
        function loadMembers() {
            // 模拟成员数据
            members = [
                { id: 1, username: 'admin', realName: '系统管理员', role: 'PRESIDENT', status: 'ONLINE' },
                { id: 2, username: 'user1', realName: '张三', role: 'ADMIN', status: 'ONLINE' },
                { id: 3, username: 'user2', realName: '李四', role: 'MEMBER', status: 'OFFLINE' },
                { id: 4, username: 'user3', realName: '王五', role: 'MEMBER', status: 'ONLINE' }
            ];
            
            renderMembers();
        }
        
        function renderMembers() {
            const list = document.getElementById('member-list');
            document.getElementById('member-count').textContent = members.length;
            
            list.innerHTML = members.map(member => {
                const initial = member.realName?.charAt(0) || member.username.charAt(0);
                let roleClass = '';
                let roleText = '';
                
                if (member.role === 'PRESIDENT') {
                    roleClass = 'role-president';
                    roleText = '社长';
                } else if (member.role === 'ADMIN') {
                    roleClass = 'role-admin';
                    roleText = '副社长';
                }
                
                return `
                    <div class="member-item">
                        <div class="member-avatar" style="background: ${member.status === 'ONLINE' ? '#28a745' : '#6c757d'}">
                            ${initial}
                        </div>
                        <div class="member-name">
                            <div>${member.realName || member.username}</div>
                            <div style="font-size: 11px; color: #999;">@${member.username}</div>
                        </div>
                        ${roleText ? `<span class="member-role ${roleClass}">${roleText}</span>` : ''}
                    </div>
                `;
            }).join('');
        }
        
        function loadMessages() {
            // 模拟消息数据
            messages = [
                { id: 1, from: 'admin', content: '欢迎大家加入计算机协会！', time: '10:30', isSystem: false },
                { id: 2, from: 'user1', content: '社团活动什么时候开始？', time: '10:32', isSystem: false },
                { id: 3, from: 'admin', content: '本周六下午2点在A101有Python培训，欢迎参加！', time: '10:33', isSystem: false }
            ];
            
            renderMessages();
        }
        
        function renderMessages() {
            const area = document.getElementById('messages-area');
            area.innerHTML = messages.map(msg => {
                if (msg.isSystem) {
                    return `<div class="system-message">${msg.content}</div>`;
                }
                
                const isOwn = msg.from === currentUser?.username;
                const msgClass = isOwn ? 'message-sent' : 'message-received';
                const align = isOwn ? 'flex-end' : 'flex-start';
                
                return `
                    <div class="message ${msgClass}" style="align-self: ${align};">
                        <div class="message-content">${msg.content}</div>
                        <div class="message-info">
                            <span class="message-sender">${msg.from}</span>
                            <span class="message-time">${msg.time}</span>
                        </div>
                    </div>
                `;
            }).join('');
            
            area.scrollTop = area.scrollHeight;
        }
        
        function sendMessage() {
            const input = document.getElementById('message-input');
            const content = input.value.trim();
            
            if (!content) return;
            
            const msg = {
                id: messages.length + 1,
                from: currentUser?.username || 'anonymous',
                content: content,
                time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
                isSystem: false
            };
            
            messages.push(msg);
            input.value = '';
            renderMessages();
        }
        
        document.getElementById('message-input').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
        
        document.getElementById('send-btn').addEventListener('click', sendMessage);
        
        function viewClubActivities() {
            alert('社团活动页面开发中...');
        }
        
        function leaveClub() {
            if (confirm('确定要退出该社团吗？')) {
                alert('退出成功！');
                window.location.href = 'clubs.jsp';
            }
        }
        
        function logout() {
            localStorage.clear();
            window.location.href = 'login.jsp';
        }
    </script>
</body>
</html>
