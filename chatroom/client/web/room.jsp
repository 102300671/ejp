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
                <h2>ChatRoom - <span id="current-room-name">Loading...</span></h2>
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
                        <h3 id="current-room-name-full">Loading...</h3>
                        <div class="room-controls">
                            <button id="join-room-btn">Join</button>
                            <button id="leave-room-btn">Leave</button>
                            <button id="exit-room-btn">Exit</button>
                        </div>
                    </div>
                    <div id="messages-area" class="messages-area">
                        <!-- Messages will be displayed here -->
                    </div>
                    <div class="message-input">
                        <input type="text" id="message-input" placeholder="Type your message...">
                        <button id="send-btn">Send</button>
                        <button id="private-msg-btn">Private</button>
                    </div>
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
            document.getElementById('current-room-name').textContent = 'Invalid Room';
            document.getElementById('current-room-name-full').textContent = 'Invalid Room';
        } else {
            document.getElementById('current-room-name').textContent = roomName;
            document.getElementById('current-room-name-full').textContent = roomName;
        }
        
        // Initialize the chat client
        document.addEventListener('DOMContentLoaded', function() {
            // Wait for the opener's chatClient to be available
            function checkOpener() {
                if (window.opener && window.opener.chatClient) {
                    // Opener is available, continue initialization
                    initRoom();
                } else if (window.opener && !window.opener.closed) {
                    // Opener exists but chatClient isn't ready yet, try again
                    setTimeout(checkOpener, 100);
                } else {
                    // No opener or opener is closed
                    document.getElementById('messages-area').innerHTML = '<div class="system-message">请从主聊天窗口打开此房间</div>';
                    // Disable all buttons
                    document.querySelectorAll('button').forEach(btn => btn.disabled = true);
                    document.getElementById('message-input').disabled = true;
                }
            }
            
            function initRoom() {
                // Ensure chatClient is properly initialized with full functionality
                if (typeof chatClient === 'undefined') {
                    // If chatClient doesn't exist, initialize it with complete functionality
                    chatClient = {
                        username: null,
                        currentRoom: null,
                        currentRoomType: null,
                        messages: {},
                        rooms: [],
                        childWindows: {},
                        isConnected: false,
                        broadcastChannel: null,
                        
                        // Send message function - child windows always send through parent
                        sendMessage: function(type, to, content) {
                            if (window.opener && window.opener.chatClient) {
                                console.log('Child window sending message through parent:', type, to, content);
                                window.opener.chatClient.sendMessage(type, to, content);
                                return;
                            }
                            console.error('Cannot send message: No parent window or chatClient');
                        },
                        
                        // Update messages area for a specific room
                        updateMessagesArea: function(room) {
                            const messagesArea = document.getElementById('messages-area');
                            if (messagesArea) {
                                messagesArea.innerHTML = '';
                                if (this.messages[room]) {
                                    this.messages[room].forEach(msg => {
                                        const messageDiv = document.createElement('div');
                                        messageDiv.className = msg.isSystem ? 'system-message' : (msg.from === this.username ? 'sent-message' : 'received-message');
                                        messageDiv.innerHTML = msg.isSystem ? msg.content : `<strong>${msg.from}</strong>: ${msg.content}<br><small>${msg.time}</small>`;
                                        messagesArea.appendChild(messageDiv);
                                    });
                                    messagesArea.scrollTop = messagesArea.scrollHeight;
                                    // Use parent window's log method if available
                                    if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                        window.opener.chatClient.log('debug', `Messages updated in child window for room: ${room}`);
                                    }
                                }
                            }
                        },
                        
                        // Update rooms list (not used in child window)
                        updateRoomsList: function() {
                            // In child window, we don't have a rooms list panel, so no need to implement this
                        },
                        
                        // Show message in current window
                        showMessage: function(message, isSystem = false, roomName = null) {
                            const targetRoom = roomName || this.currentRoom;
                            
                            // Store message in memory for the specific room
                            if (!this.messages[targetRoom]) {
                                this.messages[targetRoom] = [];
                            }
                            this.messages[targetRoom].push({
                                content: message,
                                isSystem: isSystem,
                                time: new Date().toISOString().replace('T', ' ').substring(0, 19),
                                from: isSystem ? 'System' : this.username
                            });
                            
                            if (targetRoom === this.currentRoom) {
                                this.updateMessagesArea(targetRoom);
                                
                                // Broadcast the new message to all other windows using new sync mechanism
                                this.broadcastToAllWindows('newMessage', targetRoom, { 
                                    message: {
                                        content: message,
                                        isSystem: isSystem,
                                        time: new Date().toISOString().replace('T', ' ').substring(0, 19),
                                        from: isSystem ? 'System' : this.username
                                    }
                                });
                            }
                        },
                        
                        // Initialize BroadcastChannel and localStorage fallback for cross-window communication
                        initMessageSync: function() {
                            if (typeof BroadcastChannel !== 'undefined') {
                                // Create or get existing broadcast channel
                                this.broadcastChannel = new BroadcastChannel('chatroom-sync');
                                
                                // Listen for broadcast messages
                                this.broadcastChannel.onmessage = (event) => {
                                    const { type, roomName, data } = event.data;
                                    
                                    switch (type) {
                                        case 'newMessage':
                                            // Process new message from another window
                                            const { message } = data;
                                            const targetRoomName = message.to || 'system';
                                            
                                            // Store message locally
                                            if (!this.messages[targetRoomName]) {
                                                this.messages[targetRoomName] = [];
                                            }
                                            this.messages[targetRoomName].push(message);
                                            
                                            // Update UI if current room matches
                                            if (this.currentRoom === targetRoomName) {
                                                this.updateMessagesArea(targetRoomName);
                                            }
                                            if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                window.opener.chatClient.log('debug', `Child window received broadcast new message for room: ${targetRoomName}`);
                                            }
                                            break;
                                            
                                        case 'updateRoomData':
                                            // Update room data from another window
                                            if (data.messages) {
                                                this.messages = { ...this.messages, ...data.messages };
                                            }
                                            if (data.rooms) {
                                                this.rooms = data.rooms;
                                            }
                                            // Update UI if current room matches
                                            if (this.currentRoom && this.currentRoom === roomName) {
                                                this.updateMessagesArea(this.currentRoom);
                                                this.updateRoomsList();
                                            }
                                            if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                window.opener.chatClient.log('debug', `Child window received broadcast room data update`);
                                            }
                                            break;
                                            
                                        case 'syncAllData':
                                            // Full data sync from another window
                                            if (data.messages) {
                                                this.messages = data.messages;
                                            }
                                            if (data.rooms) {
                                                this.rooms = data.rooms;
                                            }
                                            if (data.username) {
                                                this.username = data.username;
                                            }
                                            if (data.currentRoom) {
                                                this.currentRoom = data.currentRoom;
                                            }
                                            if (data.currentRoomType) {
                                                this.currentRoomType = data.currentRoomType;
                                            }
                                            // Update UI
                                            this.updateMessagesArea(this.currentRoom);
                                            this.updateRoomsList();
                                            if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                window.opener.chatClient.log('debug', `Child window received broadcast full data sync`);
                                            }
                                            break;
                                    }
                                };
                                
                                if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                    window.opener.chatClient.log('info', 'Child window BroadcastChannel initialized for cross-window sync');
                                }
                            } else {
                                if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                    window.opener.chatClient.log('warn', 'Child window BroadcastChannel not supported, falling back to localStorage + storage events');
                                }
                                // Set up localStorage event listener as fallback
                                window.addEventListener('storage', (event) => {
                                    if (event.key === 'chatroom-sync' && event.newValue) {
                                        try {
                                            const syncData = JSON.parse(event.newValue);
                                            const { type, roomName, data } = syncData;
                                            
                                            switch (type) {
                                                case 'newMessage':
                                                    // Process new message from another window
                                                    const { message } = data;
                                                    const targetRoomName = message.to || 'system';
                                                    
                                                    // Store message locally
                                                    if (!this.messages[targetRoomName]) {
                                                        this.messages[targetRoomName] = [];
                                                    }
                                                    this.messages[targetRoomName].push(message);
                                                    
                                                    // Update UI if current room matches
                                                    if (this.currentRoom === targetRoomName) {
                                                        this.updateMessagesArea(targetRoomName);
                                                    }
                                                    if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                        window.opener.chatClient.log('debug', `Child window received localStorage new message for room: ${targetRoomName}`);
                                                    }
                                                    break;
                                                    
                                                case 'updateRoomData':
                                                    // Update room data from another window
                                                    if (data.messages) {
                                                        this.messages = { ...this.messages, ...data.messages };
                                                    }
                                                    if (data.rooms) {
                                                        this.rooms = data.rooms;
                                                    }
                                                    // Update UI if current room matches
                                                    if (this.currentRoom && this.currentRoom === roomName) {
                                                        this.updateMessagesArea(this.currentRoom);
                                                        this.updateRoomsList();
                                                    }
                                                    if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                        window.opener.chatClient.log('debug', `Child window received localStorage room data update`);
                                                    }
                                                    break;
                                                    
                                                case 'syncAllData':
                                                    // Full data sync from another window
                                                    if (data.messages) {
                                                        this.messages = data.messages;
                                                    }
                                                    if (data.rooms) {
                                                        this.rooms = data.rooms;
                                                    }
                                                    if (data.username) {
                                                        this.username = data.username;
                                                    }
                                                    if (data.currentRoom) {
                                                        this.currentRoom = data.currentRoom;
                                                    }
                                                    if (data.currentRoomType) {
                                                        this.currentRoomType = data.currentRoomType;
                                                    }
                                                    // Update UI
                                                    this.updateMessagesArea(this.currentRoom);
                                                    this.updateRoomsList();
                                                    if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                        window.opener.chatClient.log('debug', `Child window received localStorage full data sync`);
                                                    }
                                                    break;
                                            }
                                        } catch (error) {
                                            if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                                window.opener.chatClient.log('error', `Error processing localStorage sync: ${error.message}`);
                                            }
                                        }
                                    }
                                });
                            }
                        },
                        
                        // Broadcast message/data to all windows using BroadcastChannel or localStorage
                        broadcastToAllWindows: function(type, roomName, data) {
                            const syncData = {
                                type: type,
                                roomName: roomName,
                                data: data,
                                timestamp: Date.now()
                            };
                            
                            // Use BroadcastChannel if available
                            if (this.broadcastChannel) {
                                try {
                                    this.broadcastChannel.postMessage(syncData);
                                    if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                        window.opener.chatClient.log('debug', `Child window broadcasted ${type} via BroadcastChannel`);
                                    }
                                } catch (error) {
                                    if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                        window.opener.chatClient.log('error', `Child window BroadcastChannel postMessage failed: ${error.message}`);
                                    }
                                    // Fallback to localStorage
                                    this.syncViaLocalStorage(syncData);
                                }
                            } else {
                                // Use localStorage + storage event fallback
                                this.syncViaLocalStorage(syncData);
                            }
                        },
                        
                        // Synchronize via localStorage as fallback
                        syncViaLocalStorage: function(syncData) {
                            try {
                                // Store data in localStorage to trigger storage event in other windows
                                localStorage.setItem('chatroom-sync', JSON.stringify(syncData));
                                // Clear the item immediately to prevent stale data
                                setTimeout(() => {
                                    localStorage.removeItem('chatroom-sync');
                                }, 100);
                                if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                    window.opener.chatClient.log('debug', `Child window synced data via localStorage`);
                                }
                            } catch (error) {
                                if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                                    window.opener.chatClient.log('error', `Child window localStorage sync failed: ${error.message}`);
                                }
                            }
                        }
                    };
                }
                
                // This is a child window, use parent's chat client data
                chatClient.username = window.opener.chatClient.username;
                chatClient.currentRoom = roomName;
                chatClient.currentRoomType = roomType;
                chatClient.childWindows = window.opener.chatClient.childWindows;
                chatClient.rooms = [...window.opener.chatClient.rooms];
                chatClient.messages = window.opener.chatClient.messages;
                
                // Update UI with stored messages
                function updateMessages() {
                    if (window.opener && !window.opener.closed && window.opener.chatClient) {
                        // Update messages reference to get the latest messages
                        chatClient.messages = window.opener.chatClient.messages;
                        // Update other important chatClient properties
                        chatClient.username = window.opener.chatClient.username;
                        chatClient.isConnected = window.opener.chatClient.isConnected;
                        chatClient.isAuthenticated = window.opener.chatClient.isAuthenticated;
                        // Refresh UI for the current room
                        chatClient.updateMessagesArea(roomName);
                        if (window.opener.chatClient.log) {
                            window.opener.chatClient.log('debug', `Messages updated for room in child window: ${roomName}`);
                        }
                    }
                }
                
                // Initial update
                updateMessages();
                
                // Set up postMessage listener for real-time updates
                window.addEventListener('message', function(event) {
                    if (event.data.type === 'updateMessages') {
                        // Check if this update is for the current room or if we should always update
                        // We'll always update to ensure we have the latest messages
                        updateMessages();
                        if (window.opener && window.opener.chatClient && window.opener.chatClient.log) {
                            window.opener.chatClient.log('debug', `Received updateMessages postMessage in child window, room: ${event.data.roomName || 'all'}`);
                        }
                    }
                });
                
                // Set up a periodic sync to ensure we always have the latest messages
                // This is a fallback in case postMessage events are missed
                const syncInterval = setInterval(() => {
                    if (window.opener && !window.opener.closed) {
                        updateMessages();
                    } else {
                        // Clear interval if opener is closed
                        clearInterval(syncInterval);
                        document.getElementById('messages-area').innerHTML = '<div class="system-message">主窗口已关闭，此窗口将不再接收消息更新</div>';
                    }
                }, 5000); // Sync every 5 seconds
                
                // Clear interval when window is closed
                window.addEventListener('beforeunload', function() {
                    clearInterval(syncInterval);
                });
                
                // Send JOIN message to server through parent window
                if (window.opener && window.opener.chatClient) {
                    window.opener.chatClient.sendMessage(MessageType.JOIN, roomName, '');
                    console.log('Sent JOIN message to server for room:', roomName);
                    
                    // Force initial message sync
                    setTimeout(() => {
                        updateMessages();
                        console.log('Forced initial message sync for room:', roomName);
                    }, 200);
                }
                
                // Update current user display
                document.getElementById('current-user').textContent = chatClient.username;
                
                // Setup message input
                document.getElementById('message-input').addEventListener('keypress', function(e) {
                    if (e.key === 'Enter') {
                        sendMessage();
                    }
                });
                
                document.getElementById('send-btn').addEventListener('click', sendMessage);
                
                // Setup room controls
                document.getElementById('join-room-btn').addEventListener('click', () => {
                    window.opener.chatClient.sendMessage(MessageType.JOIN, roomName, '');
                });
                
                document.getElementById('leave-room-btn').addEventListener('click', () => {
                    window.opener.chatClient.sendMessage(MessageType.LEAVE, roomName, '');
                });
                
                document.getElementById('exit-room-btn').addEventListener('click', () => {
                    window.opener.chatClient.sendMessage(MessageType.EXIT_ROOM, roomName, '');
                    window.close();
                });
                
                // Setup private message button
                document.getElementById('private-msg-btn').addEventListener('click', () => {
                    const recipient = prompt('Enter username to send private message:');
                    if (recipient) {
                        const message = document.getElementById('message-input').value;
                        if (message) {
                            window.opener.chatClient.sendMessage(MessageType.TEXT, recipient, message);
                            document.getElementById('message-input').value = '';
                        }
                    }
                });
                
                // Initialize new message synchronization mechanism
                chatClient.initMessageSync();
                
                // Add event listener to notify parent when child window is about to close
                window.addEventListener('beforeunload', function() {
                    // Notify parent window that this child is closing
                    if (window.opener && window.opener.chatClient && window.opener.chatClient.childWindows) {
                        delete window.opener.chatClient.childWindows[roomName];
                        if (window.opener.chatClient.log) {
                            window.opener.chatClient.log('debug', `Child window for room ${roomName} is closing, removed from parent tracking`);
                        }
                    }
                });
            }
            
            // Start checking for opener
            checkOpener();
        });
        
        function sendMessage() {
            const messageInput = document.getElementById('message-input');
            const message = messageInput.value.trim();
            
            if (message) {
                // Send message to current room through parent window
                window.opener.chatClient.sendMessage(MessageType.TEXT, roomName, message);
                messageInput.value = '';
            }
        }
        
        function logout() {
            if (window.opener && window.opener.chatClient) {
                // This is a child window, perform logout through parent window
                window.opener.chatClient.logout();
            }
            window.close();
        }
    </script>
</body>
</html>