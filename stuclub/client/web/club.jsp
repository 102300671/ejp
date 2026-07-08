<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>ChatRoom - Club</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>社团群聊 - <span id="current-chat-name">加载中...</span></h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <button id="logout-btn" onclick="logout()">退出登录</button>
                    <button id="close-window-btn" onclick="window.close()">关闭窗口</button>
                </div>
            </div>
            
            <div class="chat-content">
                <div class="messages-panel full-width">
                    <div class="panel-header">
                        <h3 id="current-chat-name-full">加载中...</h3>
                        <div class="chat-controls">
                            <button id="join-club-btn">加入</button>
                            <button id="leave-club-btn">离开</button>
                            <button id="exit-club-btn">退出</button>
                        </div>
                    </div>
                    <div id="messages-area" class="messages-area">
                    </div>
                    <div class="message-input">
                        <input type="file" id="image-input" accept="image/*" style="display: none;">
                        <div class="message-input-buttons">
                            <button id="image-btn" title="发送图片">图片</button>
                        </div>
                        <div class="message-input-main">
                            <input type="text" id="message-input" placeholder="输入您的消息...">
                            <button id="send-btn">发送</button>
                            <button id="private-msg-btn">成员</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <div id="image-modal" class="modal">
            <div class="modal-content image-modal-content">
                <span class="close">&times;</span>
                <div class="nsfw-modal-wrapper">
                    <img id="modal-image" src="" alt="图片预览">
                    <button id="modal-nsfw-toggle-btn" class="nsfw-toggle-btn" style="display: none;">显示NSFW内容</button>
                </div>
            </div>
        </div>
    </div>
    
    <script src="js/lantern-festival.js"></script>
    <script src="js/womens-day.js"></script>
    <script src="js/chat.js"></script>
    <script>
        const urlParams = new URLSearchParams(window.location.search);
        const clubName = urlParams.get('club');
        const clubType = urlParams.get('type');
        
        if (!clubName || !clubType) {
            document.getElementById('current-chat-name').textContent = 'Invalid Club';
            document.getElementById('current-chat-name-full').textContent = 'Invalid Club';
        } else {
            document.getElementById('current-chat-name').textContent = clubName;
            document.getElementById('current-chat-name-full').textContent = clubName;
        }
        
        console.log('子窗口JavaScript代码开始执行');
        console.log('社团名称:', clubName, '社团类型:', clubType);
        console.log('父窗口:', window.opener ? '存在' : '不存在');
        
        document.addEventListener('DOMContentLoaded', function() {
            console.log('子窗口DOM已加载完成，开始初始化');
            console.log('社团名称:', clubName, '社团类型:', clubType);
            console.log('父窗口:', window.opener ? '存在' : '不存在');
            console.log('父窗口状态:', window.opener && !window.opener.closed ? '打开' : '关闭');
            
            function checkOpener() {
                console.log('检查父窗口...');
                console.log('父窗口存在:', !!window.opener);
                console.log('父窗口已关闭:', window.opener ? window.opener.closed : '无父窗口');
                console.log('父窗口的chatClient:', window.opener && window.opener.chatClient ? '可用' : '不可用');
                
                if (window.opener && !window.opener.closed && window.opener.chatClient) {
                    console.log('父窗口的chatClient可用，继续初始化');
                    initClub();
                } else if (window.opener && !window.opener.closed) {
                    console.log('父窗口存在但chatClient尚未准备好，100毫秒后重试');
                    setTimeout(checkOpener, 100);
                } else {
                    console.log('没有父窗口或父窗口已关闭');
                    document.getElementById('messages-area').innerHTML = '<div class="system-message">请从主聊天窗口打开此社团</div>';
                    document.querySelectorAll('button').forEach(btn => btn.disabled = true);
                    document.getElementById('message-input').disabled = true;
                }
            }
            
            function initClub() {
                console.log('开始初始化子窗口');
                
                console.log('使用从父窗口继承的chatClient对象');
                
                if (!window.opener || !window.opener.chatClient) {
                    console.error('父窗口或chatClient不可用');
                    return;
                }
                
                console.log('从父窗口获取的初始数据:');
                console.log('- 用户名:', window.opener.chatClient.username);
                console.log('- 当前社团:', clubName);
                console.log('- 社团消息数:', window.opener.chatClient.messages[clubName]?.length || 0);
                console.log('- 所有社团:', JSON.stringify(window.opener.chatClient.clubs || []));
                
                console.log('chatClient对象结构:', JSON.stringify({
                    username: window.opener.chatClient.username,
                    messagesCount: Object.keys(window.opener.chatClient.messages || {}).length,
                    hasSendMessage: typeof window.opener.chatClient.sendMessage === 'function',
                    hasHandleBroadcastMessage: typeof window.opener.chatClient.handleBroadcastMessage === 'function'
                }));
                
                console.log('为子窗口设置专门的消息监听器');
                
                if (typeof window._childMessageListener === 'function') {
                    console.log('移除现有的子窗口消息监听器');
                    window.removeEventListener('message', window._childMessageListener);
                }
                
                window._childMessageListener = function(event) {
                    console.log('子窗口message事件监听器被触发');
                    console.log('事件源:', event.source === window.opener ? '父窗口' : '其他来源');
                    console.log('事件数据:', JSON.stringify(event.data));
                    
                    const data = event.data;
                    
                    console.log('子窗口收到父窗口消息:', JSON.stringify(data));
                    console.log('消息来源:', data.source);
                    
                    console.log('子窗口处理父窗口消息:', data.type, 'for', data.clubName, 'ID:', data.uniqueId);
                    
                    switch (data.type) {
                        case 'SYNC_MESSAGES':
                            console.log('子窗口处理同步消息请求:', data.clubName, '当前社团:', clubName);
                            console.log('同步消息完整数据:', JSON.stringify(data));
                            
                            if (data.data && data.data.clubName === clubName) {
                                console.log('子窗口同步社团数据:', data.data.clubName, '消息数:', (data.data.messages || []).length);
                                
                                console.log('更新父窗口的消息数据:', data.data.clubName);
                                window.opener.chatClient.messages[data.data.clubName] = data.data.messages || [];
                                if (data.data.username) {
                                    console.log('子窗口更新用户名:', data.data.username);
                                    window.opener.chatClient.username = data.data.username;
                                }
                                if (data.data.clubs) {
                                    console.log('子窗口更新社团列表:', data.data.clubs.length, '个社团');
                                    window.opener.chatClient.clubs = data.data.clubs;
                                }
                                
                                console.log('开始更新UI...');
                                updateMessagesArea(clubName);
                                document.getElementById('current-user').textContent = window.opener.chatClient.username || '未知用户';
                                
                                console.log('子窗口同步完成:', clubName, '最终消息数:', window.opener.chatClient.messages[clubName]?.length || 0);
                            } else {
                                console.log('子窗口忽略不匹配的同步消息:', data.clubName || data.data?.clubName, '当前社团:', clubName);
                            }
                            break;
                            
                        case 'NEW_MESSAGE':
                            console.log('子窗口处理新消息:', data.clubName || data.data?.clubName, '当前社团:', clubName);
                            console.log('新消息完整数据:', JSON.stringify(data));
                            
                            const messageClubName = data.clubName || data.data?.clubName;
                            console.log('消息的社团名称:', messageClubName, '当前社团:', clubName);
                            
                            if (messageClubName === clubName && data.data && data.data.message) {
                                const message = data.data.message;
                                console.log('子窗口收到新消息内容:', JSON.stringify(message));
                                
                                if (!window.opener.chatClient.messages[clubName]) {
                                    console.log('子窗口为社团', clubName, '创建新消息列表');
                                    window.opener.chatClient.messages[clubName] = [];
                                }
                                
                                const isDuplicate = window.opener.chatClient.messages[clubName].some(m => 
                                    m.content === message.content && 
                                    m.from === message.from && 
                                    m.time === message.time
                                );
                                
                                if (!isDuplicate) {
                                    console.log('子窗口添加新消息到社团', clubName);
                                    window.opener.chatClient.messages[clubName].push(message);
                                    console.log('开始更新消息显示区域...');
                                    updateMessagesArea(clubName);
                                    console.log('子窗口成功显示新消息:', message.content.substring(0, 50), '...');
                                } else {
                                    console.log('子窗口忽略重复消息:', message.content.substring(0, 50));
                                }
                            } else {
                                console.log('子窗口忽略不匹配的新消息:', messageClubName, '当前社团:', clubName);
                            }
                            break;
                        default:
                            console.log('子窗口收到未知消息类型:', data.type);
                            break;
                    }
                };
                
                window.addEventListener('message', window._childMessageListener);
                
                if (window.opener) {
                    setTimeout(() => {
                        try {
                            window.opener.postMessage({
                                type: 'CHILD_READY',
                                clubName: clubName,
                                source: 'child'
                            }, '*');
                            console.log('已通知父窗口子窗口准备就绪，社团:', clubName);
                        } catch (error) {
                            console.error('通知父窗口失败:', error);
                        }
                    }, 500);
                }
                
                console.log('更新UI，设置当前用户');
                document.getElementById('current-user').textContent = window.opener.chatClient.username || '未知用户';
                console.log('立即更新消息显示区域，社团:', clubName);
                console.log('当前社团的消息数:', window.opener.chatClient.messages[clubName]?.length || 0);
                updateMessagesArea(clubName);
                
                document.getElementById('message-input').addEventListener('keypress', function(e) {
                    if (e.key === 'Enter') {
                        sendMessage();
                    }
                });
                
                document.getElementById('send-btn').addEventListener('click', sendMessage);
                
                document.getElementById('join-club-btn').addEventListener('click', () => {
                    if (window.opener.chatClient.sendMessage(MessageType.JOIN, clubName, '')) {
                        console.log('已发送JOIN消息');
                    }
                });
                
                document.getElementById('leave-club-btn').addEventListener('click', () => {
                    if (window.opener.chatClient.sendMessage(MessageType.LEAVE, clubName, '')) {
                        console.log('已发送LEAVE消息');
                    }
                });
                
                document.getElementById('exit-club-btn').addEventListener('click', () => {
                    if (window.opener.chatClient.sendMessage(MessageType.EXIT_CLUB, clubName, '')) {
                        setTimeout(() => {
                            window.close();
                        }, 100);
                    }
                });
                
                document.getElementById('private-msg-btn').addEventListener('click', () => {
                    if (window.opener && window.opener.chatClient) {
                        window.opener.chatClient.sendMessage(MessageType.LIST_CLUB_USERS, clubName, '');
                    }
                });
                
                document.getElementById('image-btn').addEventListener('click', function() {
                    const imageInput = document.getElementById('image-input');
                    imageInput.click();
                });
                
                document.getElementById('image-input').addEventListener('change', function(e) {
                    if (e.target.files && e.target.files.length > 0) {
                        const file = e.target.files[0];
                        if (window.opener && window.opener.chatClient) {
                            window.opener.chatClient.handleImageUpload(file);
                        }
                        e.target.value = '';
                    }
                });
                
                window.addEventListener('beforeunload', function() {
                    console.log('子窗口关闭:', clubName);
                });
                
                setTimeout(() => {
                    if (window.opener) {
                        try {
                            window.opener.postMessage({
                                type: 'REQUEST_SYNC',
                                clubName: clubName,
                                source: 'child'
                            }, '*');
                            console.log('已请求同步数据，社团:', clubName);
                        } catch (error) {
                            console.error('请求同步失败:', error);
                        }
                    }
                }, 1000);
            }
            
            function updateMessagesArea(club) {
                console.log('更新消息显示区域，社团:', club);
                const messagesArea = document.getElementById('messages-area');
                if (messagesArea) {
                    messagesArea.innerHTML = '';
                    if (window.opener.chatClient.messages[club] && window.opener.chatClient.messages[club].length > 0) {
                        window.opener.chatClient.messages[club].forEach(msg => {
                            if (msg.isSystem) {
                                const messageDiv = document.createElement('div');
                                messageDiv.className = 'system-message';
                                messageDiv.innerHTML = msg.content;
                                messagesArea.appendChild(messageDiv);
                            } else {
                                const isSent = msg.from === window.opener.chatClient.username;
                                
                                const messageWrapper = document.createElement('div');
                                messageWrapper.className = isSent ? 'sent-message-wrapper' : 'received-message-wrapper';
                                
                                const usernameDiv = document.createElement('div');
                                usernameDiv.className = 'message-username';
                                usernameDiv.textContent = msg.from;
                                messageWrapper.appendChild(usernameDiv);
                                
                                const messageDiv = document.createElement('div');
                                messageDiv.className = isSent ? 'sent-message' : 'received-message';
                                
                                let contentHtml = '';
                                if (msg.type === 'IMAGE') {
                                    contentHtml = `<img src="${msg.content}" alt="图片" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;" onclick="openImageModal('${msg.content}')">`;
                                } else {
                                    contentHtml = msg.content;
                                }
                                
                                messageDiv.innerHTML = 
                                    `<div class="message-content">${contentHtml}</div><div class="message-time"><small>${msg.time}</small></div>`;
                                messageWrapper.appendChild(messageDiv);
                                
                                messagesArea.appendChild(messageWrapper);
                            }
                        });
                        messagesArea.scrollTop = messagesArea.scrollHeight;
                        console.log('成功更新消息显示区域，消息数:', window.opener.chatClient.messages[club].length);
                    } else {
                        messagesArea.innerHTML = '<div class="system-message">暂无消息</div>';
                        console.log('更新消息显示区域，当前社团没有消息');
                    }
                }
            }
            
            checkOpener();
        });
        
        function sendMessage() {
            const messageInput = document.getElementById('message-input');
            const message = messageInput.value.trim();
            
            if (message) {
                try {
                    if (window.opener && window.opener.chatClient) {
                        if (window.opener.chatClient.sendMessage(MessageType.TEXT, clubName, message)) {
                            messageInput.value = '';
                            console.log('消息发送成功:', message);
                        } else {
                            document.getElementById('messages-area').innerHTML += 
                                '<div class="system-message">无法发送消息：与主窗口的连接已断开</div>';
                        }
                    } else {
                        document.getElementById('messages-area').innerHTML += 
                            '<div class="system-message">无法发送消息：与主窗口的连接已断开</div>';
                    }
                } catch (error) {
                    console.error('发送消息出错:', error);
                    document.getElementById('messages-area').innerHTML += 
                        '<div class="system-message">发送消息时发生错误：' + error.message + '</div>';
                }
            }
        }
        
        function logout() {
            if (window.opener && window.opener.chatClient) {
                window.opener.chatClient.logout();
            }
            window.close();
        }
        
        const imageModal = document.getElementById('image-modal');
        const modalImage = document.getElementById('modal-image');
        const imageModalCloseBtn = imageModal.querySelector('.close');
        const modalNsfwToggleBtn = document.getElementById('modal-nsfw-toggle-btn');
        
        window.openImageModal = async function(imageSrc) {
            const imgBySrc = document.querySelector(`img[src="${imageSrc}"]`);
            
            if (imgBySrc) {
                const iv = imgBySrc.getAttribute('data-iv');
                const encryptedUrl = imgBySrc.getAttribute('data-encrypted-url');
                const isShowing = imgBySrc.classList.contains('showing');
                
                if (iv && encryptedUrl) {
                    if (isShowing) {
                        modalImage.classList.remove('blurred');
                        modalImage.classList.add('showing');
                        modalNsfwToggleBtn.style.display = 'block';
                        modalNsfwToggleBtn.classList.add('minimized');
                        modalNsfwToggleBtn.textContent = '隐藏';
                        modalImage.src = imageSrc;
                    } else {
                        modalImage.classList.add('blurred');
                        modalImage.classList.remove('showing');
                        modalNsfwToggleBtn.style.display = 'block';
                        modalNsfwToggleBtn.classList.remove('minimized');
                        modalNsfwToggleBtn.textContent = '显示NSFW内容';
                        
                        try {
                            const decryptedUrl = await window.opener.chatClient.decryptImage(encryptedUrl, iv);
                            modalImage.src = decryptedUrl;
                        } catch (error) {
                            console.error('解密图片失败:', error);
                            modalImage.src = imageSrc;
                        }
                    }
                } else {
                    modalImage.classList.remove('blurred', 'showing');
                    modalNsfwToggleBtn.style.display = 'none';
                    modalImage.src = imageSrc;
                }
                imageModal.style.display = 'block';
                return;
            }
            
            modalImage.classList.remove('blurred', 'showing');
            modalNsfwToggleBtn.style.display = 'none';
            modalImage.src = imageSrc;
            imageModal.style.display = 'block';
        };
        
        modalNsfwToggleBtn.addEventListener('click', function() {
            if (modalImage.classList.contains('showing')) {
                modalImage.classList.remove('showing');
                modalNsfwToggleBtn.textContent = '显示NSFW内容';
                modalNsfwToggleBtn.classList.remove('minimized');
            } else {
                modalImage.classList.add('showing');
                modalNsfwToggleBtn.textContent = '隐藏';
                modalNsfwToggleBtn.classList.add('minimized');
            }
        });
        
        imageModalCloseBtn.addEventListener('click', function() {
            imageModal.style.display = 'none';
            modalImage.src = '';
            modalImage.classList.remove('blurred', 'showing');
            modalNsfwToggleBtn.style.display = 'none';
            modalNsfwToggleBtn.classList.remove('minimized');
        });
        
        window.addEventListener('click', function(e) {
            if (e.target === imageModal) {
                imageModal.style.display = 'none';
                modalImage.src = '';
                modalImage.classList.remove('blurred', 'showing');
                modalNsfwToggleBtn.style.display = 'none';
                modalNsfwToggleBtn.classList.remove('minimized');
            }
        });
        
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && imageModal.style.display === 'block') {
                imageModal.style.display = 'none';
                modalImage.src = '';
                modalImage.classList.remove('blurred', 'showing');
                modalNsfwToggleBtn.style.display = 'none';
                modalNsfwToggleBtn.classList.remove('minimized');
            }
        });
    </script>
</body>
</html>