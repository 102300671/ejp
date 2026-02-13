<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>ChatRoom - Room</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>ChatRoom - <span id="current-chat-name">Loading...</span></h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <button id="logout-btn" onclick="logout()">Logout</button>
                    <button id="close-window-btn" onclick="window.close()">Close Window</button>
                </div>
            </div>
            
            <div class="chat-content">
                <!-- Messages Area -->
                <div class="messages-panel full-width">
                    <div class="panel-header">
                        <h3 id="current-chat-name-full">Loading...</h3>
                        <div class="chat-controls">
                            <button id="join-room-btn">加入</button>
                            <button id="leave-room-btn">离开</button>
                            <button id="exit-room-btn">退出</button>
                        </div>
                    </div>
                    <div id="messages-area" class="messages-area">
                        <!-- Messages will be displayed here -->
                    </div>
                    <div class="message-input">
                        <input type="file" id="image-input" accept="image/*" style="display: none;">
                        <div class="message-input-buttons">
                            <button id="image-btn" title="Send Image">Image</button>
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
    </div>
    
    <script src="js/chat.js"></script>
    <script>
        // Get room name and type from URL parameters
        const urlParams = new URLSearchParams(window.location.search);
        const roomName = urlParams.get('room');
        const roomType = urlParams.get('type');
        
        if (!roomName || !roomType) {
            document.getElementById('current-chat-name').textContent = 'Invalid Room';
            document.getElementById('current-chat-name-full').textContent = 'Invalid Room';
        } else {
            document.getElementById('current-chat-name').textContent = roomName;
            document.getElementById('current-chat-name-full').textContent = roomName;
        }
        
        // Initialize the chat client
        console.log('子窗口JavaScript代码开始执行');
        console.log('房间名称:', roomName, '房间类型:', roomType);
        console.log('父窗口:', window.opener ? '存在' : '不存在');
        
        document.addEventListener('DOMContentLoaded', function() {
            console.log('子窗口DOM已加载完成，开始初始化');
            console.log('房间名称:', roomName, '房间类型:', roomType);
            console.log('父窗口:', window.opener ? '存在' : '不存在');
            console.log('父窗口状态:', window.opener && !window.opener.closed ? '打开' : '关闭');
            
            // Wait for the opener's chatClient to be available
            function checkOpener() {
                console.log('检查父窗口...');
                console.log('父窗口存在:', !!window.opener);
                console.log('父窗口已关闭:', window.opener ? window.opener.closed : '无父窗口');
                console.log('父窗口的chatClient:', window.opener && window.opener.chatClient ? '可用' : '不可用');
                
                if (window.opener && !window.opener.closed && window.opener.chatClient) {
                    console.log('父窗口的chatClient可用，继续初始化');
                    // Opener is available, continue initialization
                    initRoom();
                } else if (window.opener && !window.opener.closed) {
                    // Opener exists but chatClient isn't ready yet, try again
                    console.log('父窗口存在但chatClient尚未准备好，100毫秒后重试');
                    setTimeout(checkOpener, 100);
                } else {
                    // No opener or opener is closed
                    console.log('没有父窗口或父窗口已关闭');
                    document.getElementById('messages-area').innerHTML = '<div class="system-message">请从主聊天窗口打开此房间</div>';
                    // Disable all buttons
                    document.querySelectorAll('button').forEach(btn => btn.disabled = true);
                    document.getElementById('message-input').disabled = true;
                }
            }
            
            function initRoom() {
                console.log('开始初始化子窗口');
                
                // 不要重新定义chatClient对象，直接使用从父窗口继承的chatClient对象
                console.log('使用从父窗口继承的chatClient对象');
                
                // 检查父窗口的chatClient是否可用
                if (!window.opener || !window.opener.chatClient) {
                    console.error('父窗口或chatClient不可用');
                    return;
                }
                
                // 从父窗口获取初始数据
                console.log('从父窗口获取的初始数据:');
                console.log('- 用户名:', window.opener.chatClient.username);
                console.log('- 当前房间:', roomName);
                console.log('- 房间消息数:', window.opener.chatClient.messages[roomName]?.length || 0);
                console.log('- 所有房间:', JSON.stringify(window.opener.chatClient.rooms || []));
                
                // 检查chatClient对象的结构
                console.log('chatClient对象结构:', JSON.stringify({
                    username: window.opener.chatClient.username,
                    messagesCount: Object.keys(window.opener.chatClient.messages || {}).length,
                    hasSendMessage: typeof window.opener.chatClient.sendMessage === 'function',
                    hasHandleBroadcastMessage: typeof window.opener.chatClient.handleBroadcastMessage === 'function'
                }));
                
                // 为子窗口创建专门的消息监听器
                console.log('为子窗口设置专门的消息监听器');
                
                // 首先移除所有现有的message事件监听器，避免重复绑定
                // 注意：这可能会移除其他可能存在的监听器，所以先检查是否存在
                if (typeof window._childMessageListener === 'function') {
                    console.log('移除现有的子窗口消息监听器');
                    window.removeEventListener('message', window._childMessageListener);
                }
                
                // 创建新的监听器函数
                window._childMessageListener = function(event) {
                    console.log('子窗口message事件监听器被触发');
                    console.log('事件源:', event.source === window.opener ? '父窗口' : '其他来源');
                    console.log('事件数据:', JSON.stringify(event.data));
                    // 检查消息是否可能来自父窗口
                    // 注意：在某些浏览器中，event.source可能不是window.opener本身，而是一个代理对象
                    // 所以我们不严格检查event.source === window.opener，而是接受所有消息，然后在后续处理中进行验证
                    // if (event.source !== window.opener) {
                    //     console.log('子窗口忽略来自非父窗口的消息');
                    //     return;
                    // }
                    
                    const data = event.data;
                    
                    // 记录所有来自父窗口的消息
                    console.log('子窗口收到父窗口消息:', JSON.stringify(data));
                    
                    // 记录消息来源
                    console.log('消息来源:', data.source);
                    
                    // 接受所有来自父窗口的消息，不管source字段是什么
                    // 因为主窗口可能发送不同source值的消息
                    
                    console.log('子窗口处理父窗口消息:', data.type, 'for', data.roomName, 'ID:', data.uniqueId);
                    
                    switch (data.type) {
                        case 'SYNC_MESSAGES':
                            console.log('子窗口处理同步消息请求:', data.roomName, '当前房间:', roomName);
                            console.log('同步消息完整数据:', JSON.stringify(data));
                            
                            if (data.data && data.data.roomName === roomName) {
                                console.log('子窗口同步房间数据:', data.data.roomName, '消息数:', (data.data.messages || []).length);
                                
                                // 更新父窗口的消息数据
                                console.log('更新父窗口的消息数据:', data.data.roomName);
                                window.opener.chatClient.messages[data.data.roomName] = data.data.messages || [];
                                if (data.data.username) {
                                    console.log('子窗口更新用户名:', data.data.username);
                                    window.opener.chatClient.username = data.data.username;
                                }
                                if (data.data.rooms) {
                                    console.log('子窗口更新房间列表:', data.data.rooms.length, '个房间');
                                    window.opener.chatClient.rooms = data.data.rooms;
                                }
                                
                                // 更新UI
                                console.log('开始更新UI...');
                                updateMessagesArea(roomName);
                                document.getElementById('current-user').textContent = window.opener.chatClient.username || '未知用户';
                                
                                console.log('子窗口同步完成:', roomName, '最终消息数:', window.opener.chatClient.messages[roomName]?.length || 0);
                            } else {
                                console.log('子窗口忽略不匹配的同步消息:', data.roomName || data.data?.roomName, '当前房间:', roomName);
                            }
                            break;
                            
                        case 'NEW_MESSAGE':
                            console.log('子窗口处理新消息:', data.roomName || data.data?.roomName, '当前房间:', roomName);
                            console.log('新消息完整数据:', JSON.stringify(data));
                            
                            // 检查根级别的roomName或data.roomName是否匹配当前房间
                            const messageRoomName = data.roomName || data.data?.roomName;
                            console.log('消息的房间名称:', messageRoomName, '当前房间:', roomName);
                            
                            if (messageRoomName === roomName && data.data && data.data.message) {
                                const message = data.data.message;
                                console.log('子窗口收到新消息内容:', JSON.stringify(message));
                                
                                // 更新父窗口的消息数据
                                if (!window.opener.chatClient.messages[roomName]) {
                                    console.log('子窗口为房间', roomName, '创建新消息列表');
                                    window.opener.chatClient.messages[roomName] = [];
                                }
                                
                                // 检查重复消息
                                const isDuplicate = window.opener.chatClient.messages[roomName].some(m => 
                                    m.content === message.content && 
                                    m.from === message.from && 
                                    m.time === message.time
                                );
                                
                                if (!isDuplicate) {
                                    console.log('子窗口添加新消息到房间', roomName);
                                    window.opener.chatClient.messages[roomName].push(message);
                                    console.log('开始更新消息显示区域...');
                                    updateMessagesArea(roomName);
                                    console.log('子窗口成功显示新消息:', message.content.substring(0, 50), '...');
                                } else {
                                    console.log('子窗口忽略重复消息:', message.content.substring(0, 50));
                                }
                            } else {
                                console.log('子窗口忽略不匹配的新消息:', messageRoomName, '当前房间:', roomName);
                            }
                            break;
                        default:
                            console.log('子窗口收到未知消息类型:', data.type);
                            break;
                    }
                };
                
                // 添加新的监听器
                window.addEventListener('message', window._childMessageListener);
                
                // 通知父窗口准备就绪
                if (window.opener) {
                    setTimeout(() => {
                        try {
                            window.opener.postMessage({
                                type: 'CHILD_READY',
                                roomName: roomName,
                                source: 'child'
                            }, '*');
                            console.log('已通知父窗口子窗口准备就绪，房间:', roomName);
                        } catch (error) {
                            console.error('通知父窗口失败:', error);
                        }
                    }, 500);
                }
                
                // 更新UI
                console.log('更新UI，设置当前用户');
                document.getElementById('current-user').textContent = window.opener.chatClient.username || '未知用户';
                console.log('立即更新消息显示区域，房间:', roomName);
                console.log('当前房间的消息数:', window.opener.chatClient.messages[roomName]?.length || 0);
                updateMessagesArea(roomName);
                
                // 设置消息输入
                document.getElementById('message-input').addEventListener('keypress', function(e) {
                    if (e.key === 'Enter') {
                        sendMessage();
                    }
                });
                
                document.getElementById('send-btn').addEventListener('click', sendMessage);
                
                // 设置房间控制按钮
                document.getElementById('join-room-btn').addEventListener('click', () => {
                    if (window.opener.chatClient.sendMessage(MessageType.JOIN, roomName, '')) {
                        console.log('已发送JOIN消息');
                    }
                });
                
                document.getElementById('leave-room-btn').addEventListener('click', () => {
                    if (window.opener.chatClient.sendMessage(MessageType.LEAVE, roomName, '')) {
                        console.log('已发送LEAVE消息');
                    }
                });
                
                document.getElementById('exit-room-btn').addEventListener('click', () => {
                    if (window.opener.chatClient.sendMessage(MessageType.EXIT_ROOM, roomName, '')) {
                        setTimeout(() => {
                            window.close();
                        }, 100);
                    }
                });
                
                // 设置成员按钮
                document.getElementById('private-msg-btn').addEventListener('click', () => {
                    // 请求房间用户列表
                    if (window.opener && window.opener.chatClient) {
                        window.opener.chatClient.sendMessage(MessageType.LIST_ROOM_USERS, roomName, '');
                    }
                });
                
                // 图片上传按钮功能
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
                
                // 窗口关闭时清理
                window.addEventListener('beforeunload', function() {
                    console.log('子窗口关闭:', roomName);
                });
                
                // 请求初始同步
                setTimeout(() => {
                    if (window.opener) {
                        try {
                            window.opener.postMessage({
                                type: 'REQUEST_SYNC',
                                roomName: roomName,
                                source: 'child'
                            }, '*');
                            console.log('已请求同步数据，房间:', roomName);
                        } catch (error) {
                            console.error('请求同步失败:', error);
                        }
                    }
                }, 1000);
            }
            
            // 更新消息显示区域的函数
            function updateMessagesArea(room) {
                console.log('更新消息显示区域，房间:', room);
                const messagesArea = document.getElementById('messages-area');
                if (messagesArea) {
                    messagesArea.innerHTML = '';
                    if (window.opener.chatClient.messages[room] && window.opener.chatClient.messages[room].length > 0) {
                        window.opener.chatClient.messages[room].forEach(msg => {
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
                        console.log('成功更新消息显示区域，消息数:', window.opener.chatClient.messages[room].length);
                    } else {
                        messagesArea.innerHTML = '<div class="system-message">暂无消息</div>';
                        console.log('更新消息显示区域，当前房间没有消息');
                    }
                }
            }
            
            // Start checking for opener
            checkOpener();
        });
        
        function sendMessage() {
            const messageInput = document.getElementById('message-input');
            const message = messageInput.value.trim();
            
            if (message) {
                try {
                    if (window.opener && window.opener.chatClient) {
                        if (window.opener.chatClient.sendMessage(MessageType.TEXT, roomName, message)) {
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
        
        // Image modal functionality
        const imageModal = document.getElementById('image-modal');
        const modalImage = document.getElementById('modal-image');
        const imageModalCloseBtn = imageModal.querySelector('.close');
        const modalNsfwToggleBtn = document.getElementById('modal-nsfw-toggle-btn');
        
        // Function to open image modal
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
        
        // Modal NSFW toggle button functionality
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
        
        // Close image modal when close button is clicked
        imageModalCloseBtn.addEventListener('click', function() {
            imageModal.style.display = 'none';
            modalImage.src = '';
            modalImage.classList.remove('blurred', 'showing');
            modalNsfwToggleBtn.style.display = 'none';
            modalNsfwToggleBtn.classList.remove('minimized');
        });
        
        // Close image modal when clicking outside
        window.addEventListener('click', function(e) {
            if (e.target === imageModal) {
                imageModal.style.display = 'none';
                modalImage.src = '';
                modalImage.classList.remove('blurred', 'showing');
                modalNsfwToggleBtn.style.display = 'none';
                modalNsfwToggleBtn.classList.remove('minimized');
            }
        });
        
        // Close image modal with Escape key
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