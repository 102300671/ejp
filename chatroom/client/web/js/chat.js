// Message types
const MessageType = {
    TEXT: 'TEXT',
    JOIN: 'JOIN',
    LEAVE: 'LEAVE',
    SYSTEM: 'SYSTEM',
    REGISTER: 'REGISTER',
    LOGIN: 'LOGIN',
    AUTH_SUCCESS: 'AUTH_SUCCESS',
    AUTH_FAILURE: 'AUTH_FAILURE',
    UUID_AUTH: 'UUID_AUTH',
    UUID_AUTH_SUCCESS: 'UUID_AUTH_SUCCESS',
    UUID_AUTH_FAILURE: 'UUID_AUTH_FAILURE',
    CREATE_ROOM: 'CREATE_ROOM',
    EXIT_ROOM: 'EXIT_ROOM',
    LIST_ROOMS: 'LIST_ROOMS'
};

// Chat client object
let chatClient = {
    ws: null,
    username: null,
    serverIp: null,
    serverPort: null,
    wsPort: null,
    currentRoom: 'system',
    currentRoomType: 'PUBLIC',
    rooms: [],
    messages: {}, // Store messages by room name: { [roomName]: [{content, from, time, isSystem}] },
    isConnected: false,
    isAuthenticated: false,
    childWindows: {}, // Store open windows by room name: { [roomName]: windowObject },
    cleanupInterval: null,
    logEnabled: true, // 是否启用日志
    broadcastChannel: null, // BroadcastChannel for cross-window communication
    
    // 日志记录方法
    log: function(level, message) {
        if (!this.logEnabled) return;
        
        // 支持的日志级别
        const validLevels = ['debug', 'info', 'warn', 'error'];
        if (!validLevels.includes(level)) {
            level = 'info';
        }
        
        // 格式化时间戳
        const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 23);
        
        // 控制台输出（保留原有功能）
        if (console && console[level]) {
            console[level](`[${timestamp}] [${level.toUpperCase()}] ${message}`);
        }
        
        // 通过AJAX发送到服务器端日志（如果网络允许）
        // 只有非debug级别的日志才发送到服务器，减少网络请求
        if (level !== 'debug') {
            try {
                // 使用当前页面的协议和域名构建log.jsp的URL
                const logUrl = new URL('log.jsp', window.location.href);
                
                const xhr = new XMLHttpRequest();
                xhr.open('POST', logUrl, true);
                xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
                
                // 设置超时，避免日志请求影响主要功能
                xhr.timeout = 300; // 缩短超时时间
                
                // 构建请求参数
                const params = new URLSearchParams();
                params.append('level', level);
                params.append('message', message);
                params.append('timestamp', timestamp);
                
                xhr.send(params.toString());
                
                // 处理响应和错误
                xhr.onload = xhr.onerror = xhr.ontimeout = function() {
                    // 静默失败，不影响主要功能
                };
            } catch (error) {
                // 静默失败，不影响主要功能
            }
        }
    },
    
    // Connect to server with specified address and port (only for main window)
    connectToServer: function(ip, port, wsPort, wsProtocol) {
        if (window.opener) {
            // This is a child window, don't connect directly to server
            this.log('debug', 'Child window detected, not connecting to server directly');
            return;
        }
        
        this.serverIp = ip;
        this.serverPort = port;
        this.wsPort = wsPort;
        this.wsProtocol = wsProtocol;
        
        // Show connecting message
        const statusDiv = document.getElementById('status');
        if (statusDiv) {
            statusDiv.textContent = 'Connecting to server...';
            statusDiv.className = 'status connecting';
        }
        
        // Save server info to sessionStorage instead of localStorage
        sessionStorage.setItem('serverIp', ip);
        sessionStorage.setItem('serverPort', port);
        sessionStorage.setItem('wsPort', wsPort);
        sessionStorage.setItem('wsProtocol', wsProtocol);
        
        // Establish WebSocket connection immediately
        this.connect();
        
        // If connection is successful, redirect to login page
        const connectionCheck = setInterval(() => {
            if (this.isConnected) {
                clearInterval(connectionCheck);
                
                if (statusDiv) {
                    statusDiv.textContent = 'Connection successful! Redirecting to login...';
                    statusDiv.className = 'status success';
                }
                
                setTimeout(() => {
                    window.location.href = `login.jsp?serverIp=${encodeURIComponent(ip)}&serverPort=${encodeURIComponent(port)}&wsPort=${encodeURIComponent(wsPort)}`;
                }, 1500);
            }
        }, 100);
        
        // Timeout if connection takes too long
        setTimeout(() => {
            clearInterval(connectionCheck);
            if (!this.isConnected && statusDiv) {
                statusDiv.textContent = 'Connection timed out. Please check server address and port.';
                statusDiv.className = 'status error';
            }
        }, 5000);
    },
    
    // Initialize connection using saved server info (only for main window)
    connect: function() {
        if (window.opener) {
            // This is a child window, don't connect directly to server
            console.log('Child window detected, not connecting to server directly');
            return;
        }
        
        // Check if server info is saved
        if (!this.serverIp || !this.serverPort) {
            // Try to get from sessionStorage first, then localStorage
            this.serverIp = sessionStorage.getItem('serverIp') || localStorage.getItem('serverIp');
            this.serverPort = sessionStorage.getItem('serverPort') || localStorage.getItem('serverPort');
            this.wsPort = sessionStorage.getItem('wsPort') || localStorage.getItem('wsPort');
            this.wsProtocol = sessionStorage.getItem('wsProtocol') || localStorage.getItem('wsProtocol') || 'ws';
            
            // If no server info, redirect to connect page
            if (!this.serverIp || !this.serverPort) {
                window.location.href = 'connect.jsp';
                return;
            }
        }
        
        // Connect to WebSocket server
        let wsPort;
        
        // Use saved WebSocket port if available
        if (this.wsPort) {
            wsPort = parseInt(this.wsPort);
            this.log('info', `Using saved WebSocket port: ${wsPort}`);
        } else {
            // Fallback to calculating WebSocket port (TCP port + 1)
            wsPort = parseInt(this.serverPort);
            
            // Ensure port is valid and not NaN
            if (isNaN(wsPort) || wsPort < 1 || wsPort > 65534) {
                wsPort = 8080; // Default port if invalid
                this.log('warn', `Invalid port '${this.serverPort}', using default port ${wsPort}`);
            }
            
            // WebSocket port is TCP port + 1
            wsPort += 1;
            
            // Ensure WebSocket port is within valid range
            if (wsPort > 65535) {
                wsPort = 8081; // Fallback to default WebSocket port if calculated port exceeds maximum
                this.log('warn', `Calculated WebSocket port exceeds maximum (65535), using default port ${wsPort}`);
            }
            
            // Save the calculated WebSocket port for future use
            this.wsPort = wsPort;
            sessionStorage.setItem('wsPort', wsPort);
            localStorage.setItem('wsPort', wsPort);
        }
        
        // Ensure WebSocket port is valid
        if (isNaN(wsPort) || wsPort < 1 || wsPort > 65535) {
            wsPort = 8081; // Fallback to default WebSocket port if saved port is invalid
            this.log('warn', `Invalid WebSocket port '${this.wsPort}', using default port ${wsPort}`);
            this.wsPort = wsPort;
            sessionStorage.setItem('wsPort', wsPort);
            localStorage.setItem('wsPort', wsPort);
        }
        
        // Use saved WebSocket protocol, default to ws://
        const wsProtocol = this.wsProtocol === 'wss' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${this.serverIp}:${wsPort}`;
        
        this.log('info', `Connecting to WebSocket server at ${wsUrl}`);
        this.log('info', `Using WebSocket protocol: ${wsProtocol}`);
        this.log('info', `Server IP: ${this.serverIp}, WebSocket Port: ${this.wsPort}`);
        
        // Create WebSocket connection
        this.ws = new WebSocket(wsUrl);
        
        this.ws.onopen = () => {
            this.log('info', 'WebSocket connection established');
            this.isConnected = true;
            
            // Wait a short time to ensure initChat has completed and username is set
            setTimeout(() => {
                // Ensure username is set correctly from storage
                this.username = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
                
                // Mark connection message as system message
                chatClient.showMessage('Connected to chat server via WebSocket', true);
                
                // Only send UUID authentication on chat.jsp page
                if (window.location.pathname.includes('chat.jsp')) {
                    // Try to authenticate with UUID if available (for chat.jsp)
                    const uuid = sessionStorage.getItem('uuid') || localStorage.getItem('uuid');
                    const username = sessionStorage.getItem('username') || localStorage.getItem('username');
                    
                    if (uuid && username) {
                        // Send UUID authentication
                        console.log('Sending UUID authentication');
                        this.sendMessage(MessageType.UUID_AUTH, username, uuid);
                    }
                }
            }, 100);
        };
        
        this.ws.onmessage = (event) => {
            this.log('debug', `Received message: ${event.data}`);
            try {
                const message = JSON.parse(event.data);
                this.handleMessage(message);
            } catch (e) {
                console.error('Error parsing message:', e);
            }
        };
        
        this.ws.onclose = (event) => {
            this.log('info', `WebSocket connection closed: ${event.code} ${event.reason}`);
            this.isConnected = false;
            this.showMessage('Connection closed');
        };
        
        this.ws.onerror = (error) => {
            // Log detailed error information
            this.log('error', `WebSocket error event: ${JSON.stringify(error)}`);
            this.log('error', `WebSocket error type: ${error instanceof Error ? error.name : 'unknown'}`);
            this.log('error', `WebSocket error message: ${error instanceof Error ? error.message : 'unknown'}`);
            
            this.isConnected = false;
            this.isAuthenticated = false;
            
            // Show detailed error message
            let errorMsg = 'WebSocket connection error. ';
            if (error instanceof Error) {
                errorMsg += `${error.name}: ${error.message}`;
            } else {
                errorMsg += `Event: ${JSON.stringify(error)}. Please check server address and port.`;
            }
            
            this.showMessage(errorMsg);
            
            // If on connect.jsp or login.jsp, show error status
            const statusDiv = document.getElementById('status');
            if (statusDiv) {
                statusDiv.textContent = errorMsg;
                statusDiv.className = 'status error';
            }
            
            // If on chat.jsp, show reconnect option
            const messagesArea = document.getElementById('messages-area');
            if (messagesArea && window.location.pathname.includes('chat.jsp')) {
                messagesArea.innerHTML += '<div class="system-message">Connection lost. Please refresh the page to reconnect.</div>';
            }
        };
    },
    
    // Send message - child windows send through parent window
    sendMessage: function(type, to, content) {
        if (window.opener && window.opener.chatClient) {
            // This is a child window, send message through parent window
            console.log('Child window sending message through parent:', type, to, content);
            window.opener.chatClient.sendMessage(type, to, content);
            return;
        }
        
        if (!this.isConnected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            this.showMessage('Not connected to server');
            return;
        }
        
        // Get username from localStorage if this.username is not set
        const username = this.username || localStorage.getItem('username') || 'unknown';
        
        const message = {
            type: type,
            from: username,
            to: to,
            content: content,
            time: new Date().toISOString().replace('T', ' ').substring(0, 19)
        };
        
        // Send message through WebSocket
        this.ws.send(JSON.stringify(message));
    },
    

    
    // Handle incoming messages
    handleMessage: function(message) {
        console.log('Received message:', message);
        
        switch (message.type) {
            case MessageType.AUTH_SUCCESS:
                this.handleAuthSuccess(message);
                break;
            case MessageType.AUTH_FAILURE:
                this.handleAuthFailure(message);
                break;
            case MessageType.TEXT:
                this.handleTextMessage(message);
                break;
            case MessageType.SYSTEM:
                this.handleSystemMessage(message);
                break;
            case MessageType.JOIN:
                this.handleJoinMessage(message);
                break;
            case MessageType.LEAVE:
                this.handleLeaveMessage(message);
                break;
            case MessageType.LIST_ROOMS:
                this.handleListRooms(message);
                break;
            case MessageType.UUID_AUTH_SUCCESS:
                this.handleUUIDAuthSuccess(message);
                break;
            case MessageType.UUID_AUTH_FAILURE:
                this.handleUUIDAuthFailure(message);
                break;
            default:
                console.log('Unknown message type:', message.type);
        }
    },
    
    // Initialize BroadcastChannel and localStorage fallback
    initMessageSync: function() {
        // Initialize BroadcastChannel for cross-window communication
        if (typeof BroadcastChannel !== 'undefined') {
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
                        this.log('debug', `Received broadcast new message for room: ${targetRoomName}`);
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
                        this.log('debug', `Received broadcast room data update`);
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
                        this.log('debug', `Received broadcast full data sync`);
                        break;
                }
            };
            
            this.log('info', 'BroadcastChannel initialized for cross-window sync');
        } else {
            this.log('warn', 'BroadcastChannel not supported, falling back to localStorage + storage events');
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
                                this.log('debug', `Received localStorage new message for room: ${targetRoomName}`);
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
                                this.log('debug', `Received localStorage room data update`);
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
                                this.log('debug', `Received localStorage full data sync`);
                                break;
                        }
                    } catch (error) {
                        this.log('error', `Error processing localStorage sync: ${error.message}`);
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
                this.log('debug', `Broadcasted ${type} via BroadcastChannel`);
            } catch (error) {
                this.log('error', `BroadcastChannel postMessage failed: ${error.message}`);
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
            this.log('debug', `Synced data via localStorage`);
        } catch (error) {
            this.log('error', `localStorage sync failed: ${error.message}`);
        }
    },
    
    // Show message in the UI
    showMessage: function(message, isSystem = false, roomName = null) {
        const targetRoom = roomName || this.currentRoom;
        
        // Store message in memory for the specific room
        if (!this.messages[targetRoom]) {
            this.messages[targetRoom] = [];
        }
        
        // Always get the latest username from storage first
        const storedUsername = sessionStorage.getItem('username') || localStorage.getItem('username');
        // Update chatClient.username if we found a username in storage
        if (storedUsername) {
            this.username = storedUsername;
        }
        // Get username with fallback mechanism
        const username = this.username || storedUsername || 'unknown';
        this.messages[targetRoom].push({
            content: message,
            isSystem: isSystem,
            time: new Date().toISOString().replace('T', ' ').substring(0, 19),
            from: isSystem ? 'System' : username
        });
        
        // Update current window if it's showing this room
        const currentRoomName = document.getElementById('current-room-name')?.textContent;
        if (currentRoomName === targetRoom || !roomName) {
            const messagesArea = document.getElementById('messages-area');
            if (messagesArea) {
                const messageDiv = document.createElement('div');
                if (isSystem) {
                    messageDiv.className = 'system-message';
                    messageDiv.innerHTML = message;
                } else {
                    // Use the same username as stored in messages
                    const displayedUsername = username;
                    messageDiv.className = displayedUsername === this.username ? 'sent-message' : 'received-message';
                    messageDiv.innerHTML = `<strong>${displayedUsername}</strong>: ${message}<br><small>${new Date().toISOString().replace('T', ' ').substring(0, 19)}</small>`;
                }
                messagesArea.appendChild(messageDiv);
                messagesArea.scrollTop = messagesArea.scrollHeight;
            }
        }
        
        // Broadcast the new message to all other windows using new sync mechanism
        this.broadcastToAllWindows('newMessage', targetRoom, { 
            message: {
                content: message,
                isSystem: isSystem,
                time: new Date().toISOString().replace('T', ' ').substring(0, 19),
                from: isSystem ? 'System' : username
            }
        });
        
        // Update all open windows showing this room (traditional method as backup)
        if (window.opener && window.opener.chatClient) {
            const openerRoomName = window.opener.document.getElementById('current-room-name')?.textContent;
            if (openerRoomName === targetRoom) {
                window.opener.chatClient.updateMessagesArea(targetRoom);
            }
        }
        
        // Update child windows showing this room (traditional method as backup)
        this.syncWithChildWindows(targetRoom, (childWindow, childRoomName) => {
            try {
                // First update the messages reference, then update the UI
                childWindow.chatClient.messages = this.messages;
                childWindow.chatClient.updateMessagesArea(targetRoom);
                console.log('Synced message to child window directly:', targetRoom);
            } catch (error) {
                console.error('Error syncing to child window:', error);
                // Fallback: use postMessage if direct access fails
                if (!childWindow.closed) {
                    childWindow.postMessage({ type: 'updateMessages', roomName: targetRoom }, '*');
                    console.log('Fallback to postMessage for child window:', targetRoom);
                } else {
                    // Remove closed window from tracking
                    delete this.childWindows[childRoomName];
                }
            }
        });
    },
    
    // Update messages area with stored messages for a specific room
    updateMessagesArea: function(roomName) {
        const messagesArea = document.getElementById('messages-area');
        if (messagesArea) {
            messagesArea.innerHTML = '';
            if (this.messages[roomName]) {
                // Get the latest username to ensure correctness
                const currentUsername = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
                
                this.messages[roomName].forEach(msg => {
                    const messageDiv = document.createElement('div');
                    if (msg.isSystem) {
                        messageDiv.className = 'system-message';
                        messageDiv.innerHTML = msg.content;
                    } else {
                        // Use the stored username from the message
                        const displayedUsername = msg.from;
                        messageDiv.className = displayedUsername === currentUsername ? 'sent-message' : 'received-message';
                        messageDiv.innerHTML = `<strong>${displayedUsername}</strong>: ${msg.content}<br><small>${msg.time}</small>`;
                    }
                    messagesArea.appendChild(messageDiv);
                });
                messagesArea.scrollTop = messagesArea.scrollHeight;
            }
        }
    },
    
    // Authentication handlers
    handleAuthSuccess: function(message) {
        // Get username from message.to field, which is set by server
        const username = message.to;
        this.username = username;
        
        // Save user info to sessionStorage instead of localStorage
        sessionStorage.setItem('username', this.username);
        sessionStorage.setItem('uuid', message.content);
        
        window.location.href = 'chat.jsp';
    },
    
    handleAuthFailure: function(message) {
        document.getElementById('message').textContent = 'Authentication failed: ' + message.content;
    },
    
    // UUID Authentication handlers
    handleUUIDAuthSuccess: function(message) {
        this.isAuthenticated = true;
        // Set username from storage after UUID authentication
        this.username = sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
        // Update UI with authenticated username
        const currentUserSpan = document.getElementById('current-user');
        if (currentUserSpan) {
            currentUserSpan.textContent = this.username;
        }
        // Request room list after UUID authentication
        this.sendMessage(MessageType.LIST_ROOMS, 'server', '');
        // Start periodic cleanup of closed child windows
        this.startCleanupInterval();
        
        // Update messages area to ensure correct username in all messages
        const currentRoomName = document.getElementById('current-room-name')?.textContent || 'system';
        this.updateMessagesArea(currentRoomName);
    },
    
    handleUUIDAuthFailure: function(message) {
        console.error('UUID authentication failed:', message.content);
        // Redirect to login page if UUID authentication fails
        if (window.opener && window.opener.chatClient) {
            // This is a child window, close it
            window.close();
            return;
        }
        this.logout();
    },
    
    // Logout function - can be called from child windows
    logout: function() {
        // Clear only authentication related information
        sessionStorage.removeItem('username');
        sessionStorage.removeItem('uuid');
        localStorage.removeItem('username');
        localStorage.removeItem('uuid');
        // Keep server connection information for convenience
        
        // Close all child windows
        for (const [roomName, childWindow] of Object.entries(this.childWindows)) {
            try {
                if (!childWindow.closed) {
                    childWindow.close();
                }
            } catch (error) {
                console.error('Error closing child window:', error);
            }
        }
        
        // Clear childWindows object
        this.childWindows = {};
        
        // Stop periodic cleanup
        this.stopCleanupInterval();
        
        // Close WebSocket connection
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.close();
        }
        
        // Redirect to login page
        window.location.href = 'login.jsp';
    },
    
    // Message handlers
    handleTextMessage: function(message) {
        // Store text message in memory for the specific room
        const roomName = message.to || 'system'; // Use 'to' field as room name
        if (!this.messages[roomName]) {
            this.messages[roomName] = [];
        }
        this.messages[roomName].push({
            content: message.content,
            from: message.from,
            time: message.time,
            isSystem: false
        });
        
        // Update current window if it's showing this room
        const currentRoomName = document.getElementById('current-room-name')?.textContent;
        if (currentRoomName === roomName) {
            const messagesArea = document.getElementById('messages-area');
            if (messagesArea) {
                const messageDiv = document.createElement('div');
                messageDiv.className = message.from === this.username ? 'sent-message' : 'received-message';
                messageDiv.innerHTML = `<strong>${message.from}</strong>: ${message.content}<br><small>${message.time}</small>`;
                messagesArea.appendChild(messageDiv);
                messagesArea.scrollTop = messagesArea.scrollHeight;
            }
        }
        
        // Broadcast the new message to all other windows using new sync mechanism
        this.broadcastToAllWindows('newMessage', roomName, { 
            message: { 
                content: message.content, 
                from: message.from, 
                time: message.time, 
                isSystem: false 
            } 
        });
        
        // Fallback to traditional sync if needed
        this.syncMessageToAllWindows(roomName);
    },
    
    // Sync message to all open windows
    syncMessageToAllWindows: function(roomName) {
        // Update current window if it's showing this room
        const currentRoomName = document.getElementById('current-room-name')?.textContent;
        if (currentRoomName === roomName) {
            this.updateMessagesArea(roomName);
        }
        
        // Update parent window if this is a child window and it's showing this room
        if (window.opener && window.opener.chatClient) {
            const openerRoomName = window.opener.document.getElementById('current-room-name')?.textContent;
            if (openerRoomName === roomName) {
                window.opener.chatClient.updateMessagesArea(roomName);
            }
        }
        
        // Update all child windows showing this room
        this.syncWithChildWindows(roomName, (childWindow, childRoomName) => {
            try {
                // First update the messages reference to ensure child has latest messages
                childWindow.chatClient.messages = this.messages;
                // Then update the UI for the specific room
                childWindow.chatClient.updateMessagesArea(roomName);
                this.log('debug', `Synced message to child window directly: ${roomName}`);
            } catch (error) {
                this.log('error', `Error syncing to child window: ${error}`);
                // Fallback: use postMessage if direct access fails
                if (!childWindow.closed) {
                    // Always update messages reference first before using postMessage
                    try {
                        childWindow.chatClient.messages = this.messages;
                    } catch (innerError) {
                        this.log('debug', `Could not directly update messages for child window, will rely on updateMessages() from postMessage`);
                    }
                    childWindow.postMessage({ type: 'updateMessages', roomName: roomName }, '*');
                    this.log('debug', `Fallback to postMessage for child window: ${roomName}`);
                } else {
                    // Remove closed window from tracking
                    delete this.childWindows[childRoomName];
                    this.log('debug', `Removed closed child window from tracking: ${childRoomName}`);
                }
            }
        });
    },
    
    handleSystemMessage: function(message) {
        // Determine which room this message belongs to
        let roomName = 'system';
        
        // Check if this is a room-specific system message
        if (message.to && message.to !== 'server') {
            roomName = message.to;
        } else if (message.content.includes('加入了房间') || message.content.includes('离开了房间')) {
            // Extract room name from message content
            const roomMatch = message.content.match(/(加入了房间|离开了房间)\s+([^\s]+)/);
            if (roomMatch && roomMatch[2]) {
                roomName = roomMatch[2];
            }
        }
        
        // Filter out room list and room status system messages
        if (!message.content.includes('您所在的房间: ') && !message.content.includes('您已在房间') && !message.content.includes('已加入房间: ')) {
            this.showMessage(`[System] ${message.content}`, true, roomName);
        }
        
        // Check if this is a room list message and update rooms list
        if (message.content.includes('您所在的房间: ')) {
            // Parse room list from system message
            const content = message.content;
            const roomListStart = content.indexOf('您所在的房间: ') + 8;
            const roomEntries = content.substring(roomListStart).split(', ');
            
            this.rooms = [];
            if (roomEntries[0] !== '无') {
                roomEntries.forEach(roomEntry => {
                    const [roomName, roomType] = roomEntry.split('#');
                    this.rooms.push({ name: roomName, type: roomType || 'PUBLIC' });
                });
            } else {
                // Add system room if no rooms listed
                this.rooms.push({ name: 'system', type: 'PUBLIC' });
            }
            
            this.updateRoomsList();
            
            // Update all open windows with the new room list
            this.syncWithChildWindows(null, (childWindow, childRoomName) => {
                childWindow.chatClient.rooms = [...this.rooms];
                childWindow.chatClient.updateRoomsList();
                console.log('Updated room list in child window:', childRoomName);
            });
        }
    },
    
    handleJoinMessage: function(message) {
        // Join message is room-specific
        // Ensure we have valid room name to display the notification
        const roomName = message.to && message.to.trim() !== '' ? message.to : this.currentRoom;
        if (roomName) {
            this.showMessage(`[System] ${message.from} joined ${roomName}`, true, roomName);
            
            // Update all open windows showing this room
            this.syncMessageToAllWindows(roomName);
        }
    },
    
    handleLeaveMessage: function(message) {
        // Leave message is room-specific
        this.showMessage(`[System] ${message.from} left ${message.to}`, true, message.to);
        
        // Update all open windows showing this room
        this.syncMessageToAllWindows(message.to);
    },
    
    handleListRooms: function(message) {
        // Parse room list from system message
        // Format: "您所在的房间: room1, room2, ..."
        const content = message.content;
        const roomListStart = content.indexOf('您所在的房间: ') + 8;
        const roomEntries = content.substring(roomListStart).split(', ');
        
        this.rooms = [];
        if (roomEntries[0] !== '无') {
            roomEntries.forEach(roomEntry => {
                const [roomName, roomType] = roomEntry.split('#');
                this.rooms.push({ name: roomName, type: roomType || 'PUBLIC' });
            });
        } else {
            // Add system room if no rooms listed
            this.rooms.push({ name: 'system', type: 'PUBLIC' });
        }
        
        this.updateRoomsList();
    },
    
    // UI updates
    updateRoomsList: function() {
        const roomsList = document.getElementById('rooms-list');
        roomsList.innerHTML = '';
        
        this.rooms.forEach(room => {
            const roomDiv = document.createElement('div');
            roomDiv.className = `room-item ${room.name === this.currentRoom ? 'active' : ''}`;
            roomDiv.innerHTML = `
                <div class="room-info" onclick="chatClient.switchRoom('${room.name}', '${room.type}')">
                    <strong>${room.name}</strong> (${room.type})
                </div>
                <button class="new-window-btn" onclick="event.stopPropagation(); chatClient.openRoomInNewWindow('${room.name}', '${room.type}')">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                        <path d="M8.636 3.5a.5.5 0 0 0-.5-.5H1.5A1.5 1.5 0 0 0 0 4.5v10A1.5 1.5 0 0 0 1.5 16h10a1.5 1.5 0 0 0 1.5-1.5V7.864a.5.5 0 0 0-1 0V14.5a.5.5 0 0 1-.5.5h-10a.5.5 0 0 1-.5-.5v-10a.5.5 0 0 1 .5-.5h6.636a.5.5 0 0 0 .5-.5z"/>
                        <path d="M16 .5a.5.5 0 0 0-.5-.5h-5a.5.5 0 0 0 0 1h3.793L6.146 9.146a.5.5 0 1 0 .708.708L15 1.707V5.5a.5.5 0 0 0 1 0v-5z"/>
                    </svg>
                </button>
            `;
            roomsList.appendChild(roomDiv);
        });
    },
    
    switchRoom: function(roomName, roomType) {
        this.currentRoom = roomName;
        this.currentRoomType = roomType;
        document.getElementById('current-room-name').textContent = roomName;
        this.updateRoomsList();
        
        // Send JOIN message to server to switch room
        this.sendMessage(MessageType.JOIN, roomName, '');
        
        // Update messages area with stored messages for this room
        this.updateMessagesArea(roomName);
    },
    
    // Send private message to a specific user
    sendPrivateMessage: function(to, content) {
        if (!to || !content) {
            this.showMessage('Please enter a username and message', true);
            return;
        }
        
        // Send private message through WebSocket
        this.sendMessage(MessageType.TEXT, to, content);
        
        // Store private message in a virtual room named after the recipient
        const privateRoomName = `private_${to}`;
        if (!this.messages[privateRoomName]) {
            this.messages[privateRoomName] = [];
        }
        
        const privateMessage = {
            content: content,
            from: this.username,
            to: to,
            time: new Date().toISOString().replace('T', ' ').substring(0, 19),
            isSystem: false,
            isPrivate: true
        };
        
        this.messages[privateRoomName].push(privateMessage);
        
        // Update UI if current room is this private conversation
        const currentRoomName = document.getElementById('current-room-name')?.textContent;
        if (currentRoomName === privateRoomName) {
            this.updateMessagesArea(privateRoomName);
        }
    },
    
    // Cleanup closed child windows to prevent memory leaks
    cleanupClosedWindows: function() {
        let cleanedCount = 0;
        
        for (const [roomName, childWindow] of Object.entries(this.childWindows)) {
            try {
                if (childWindow.closed) {
                    delete this.childWindows[roomName];
                    cleanedCount++;
                    this.log('debug', `Cleaned up closed child window for room: ${roomName}`);
                }
            } catch (error) {
                this.log('error', `Error checking child window status: ${error}`);
                // If error occurs, assume the window is closed and remove it
                delete this.childWindows[roomName];
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            this.log('info', `Cleaned up ${cleanedCount} closed child window(s)`);
        }
    },
    
    // Initialize BroadcastChannel for cross-window communication
    initBroadcastChannel: function() {
        if (typeof BroadcastChannel !== 'undefined') {
            // Create or get existing broadcast channel
            this.broadcastChannel = new BroadcastChannel('chatroom-messages');
            
            // Listen for broadcast messages
            this.broadcastChannel.onmessage = (event) => {
                const { type, roomName } = event.data;
                
                switch (type) {
                    case 'updateMessages':
                        // Update local messages reference
                        if (event.data.messages) {
                            this.messages = event.data.messages;
                        }
                        // Update UI if current room matches
                        const currentRoomName = document.getElementById('current-room-name')?.textContent;
                        if (currentRoomName === roomName) {
                            this.updateMessagesArea(roomName);
                        }
                        this.log('debug', `Received broadcast update for room: ${roomName}`);
                        break;
                    
                    case 'newMessage':
                        // Process new message
                        if (event.data.message) {
                            const message = event.data.message;
                            const targetRoomName = message.to || 'system';
                            
                            // Store message locally
                            if (!this.messages[targetRoomName]) {
                                this.messages[targetRoomName] = [];
                            }
                            this.messages[targetRoomName].push(message);
                            
                            // Update UI if current room matches
                            const currentRoom = document.getElementById('current-room-name')?.textContent;
                            if (currentRoom === targetRoomName) {
                                this.updateMessagesArea(targetRoomName);
                            }
                            this.log('debug', `Received broadcast new message for room: ${targetRoomName}`);
                        }
                        break;
                }
            };
            
            this.log('info', 'BroadcastChannel initialized for cross-window communication');
        } else {
            this.log('warn', 'BroadcastChannel not supported, falling back to postMessage');
        }
    },
    

    

    
    // Start periodic cleanup of closed child windows
        startCleanupInterval: function() {
            // Clear any existing interval
            this.stopCleanupInterval();
            
            // Set up new interval to check every 30 seconds
            this.cleanupInterval = setInterval(() => {
                this.cleanupClosedWindows();
            }, 30000);
            
            this.log('info', 'Started periodic child window cleanup (every 30 seconds)');
        },
    
    // Stop periodic cleanup
        stopCleanupInterval: function() {
            if (this.cleanupInterval) {
                clearInterval(this.cleanupInterval);
                this.cleanupInterval = null;
                this.log('info', 'Stopped periodic child window cleanup');
            }
        },
    
    // Generic method to synchronize data with child windows
    syncWithChildWindows: function(roomName, syncCallback) {
        if (!this.childWindows || typeof syncCallback !== 'function') {
            return;
        }
        
        for (const [childRoomName, childWindow] of Object.entries(this.childWindows)) {
            try {
                // If roomName is provided, only sync with windows showing that room
                if (roomName && childRoomName !== roomName) {
                    continue;
                }
                
                if (!childWindow.closed && childWindow.chatClient) {
                    // Execute the sync callback on the child window
                    syncCallback(childWindow, childRoomName);
                }
            } catch (error) {
                this.log('error', `Error syncing with child window ${childRoomName}: ${error}`);
                // If error occurs, remove the window from tracking
                delete this.childWindows[childRoomName];
            }
        }
    },
    
    // Open a room in a new window
        openRoomInNewWindow: function(roomName, roomType) {
            this.log('info', `Opening room in new window: ${roomName} (${roomType})`);
            
            // Check if window is already open for this room
            if (this.childWindows[roomName] && !this.childWindows[roomName].closed) {
                // Focus on the existing window
                this.childWindows[roomName].focus();
                this.log('debug', `Window already open for room ${roomName}, focusing instead`);
                return;
            }
        
        // Ensure room messages are properly initialized
        if (!this.messages[roomName]) {
            this.messages[roomName] = [];
        }
        
        // Open a new window for the room
        const windowFeatures = 'width=800,height=600,scrollbars=yes,menubar=yes,toolbar=yes,status=yes';
        const newWindow = window.open('room.jsp?room=' + encodeURIComponent(roomName) + '&type=' + encodeURIComponent(roomType), '_blank', windowFeatures);
        
        if (newWindow) {
            // Store the window reference
            this.childWindows[roomName] = newWindow;
            
            // Set up event listener for when window closes
            newWindow.addEventListener('beforeunload', () => {
                delete this.childWindows[roomName];
            });
            
            // Wait for the new window to load and then sync messages
            const waitForWindowLoad = setInterval(() => {
                if (newWindow.document && newWindow.document.readyState === 'complete') {
                    clearInterval(waitForWindowLoad);
                    
                    this.log('debug', `New window for room ${roomName} has loaded`);
                    
                    // Ensure main window is joined to this room
                    if (this.currentRoom !== roomName) {
                        // Join the room in the main window
                        this.log('debug', `Joining room ${roomName} in main window`);
                        this.sendMessage(MessageType.JOIN, roomName, '');
                    }
                    
                    // Force update the new window with all messages
                    if (newWindow.chatClient) {
                        newWindow.chatClient.messages = this.messages;
                        newWindow.chatClient.updateMessagesArea(roomName);
                        this.log('debug', `Synced messages to new window for room ${roomName}`);
                    } else {
                        newWindow.postMessage({ type: 'updateMessages', roomName: roomName }, '*');
                        this.log('debug', `Sent postMessage to update messages in new window for room ${roomName}`);
                    }
                    
                    // Notify user that new window has been opened and synced
                    this.showMessage(`已打开房间 "${roomName}" 的新窗口并同步消息`, true);
                    this.log('info', `Successfully opened and synced new window for room ${roomName}`);
                }
            }, 100);
            
            // Timeout after 5 seconds
            setTimeout(() => {
                clearInterval(waitForWindowLoad);
                this.log('warn', `Window load timeout for room ${roomName}`);
            }, 5000);
        } else {
            this.showMessage('无法打开新窗口，请检查浏览器弹窗设置', true);
            this.log('warn', `Failed to open new window for room ${roomName}: Popup blocked`);
        }
    },

    // Function to close all child windows - can be called when parent window closes
    closeAllChildWindows: function() {
        // Close all child windows
        for (const [roomName, childWindow] of Object.entries(this.childWindows)) {
            try {
                if (!childWindow.closed) {
                    childWindow.close();
                }
            } catch (error) {
                this.log('error', `Error closing child window: ${error}`);
            }
        }
        
        // Clear childWindows object
        this.childWindows = {};
    }
};

// Login functionality
function initLogin() {
    // Tab switching
    document.getElementById('login-form').addEventListener('submit', function(e) {
        e.preventDefault();
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;
        
        // Check if already connected
        if (!chatClient.isConnected) {
            chatClient.connect();
        }
        
        // Wait for connection to be established
        const sendLogin = setInterval(() => {
            if (chatClient.isConnected) {
                clearInterval(sendLogin);
                chatClient.sendMessage(MessageType.LOGIN, 'server', username + ':' + password);
            }
        }, 100);
        
        // Timeout if connection takes too long
        setTimeout(() => {
            clearInterval(sendLogin);
            if (!chatClient.isConnected) {
                document.getElementById('message').textContent = 'Connection failed. Please try again.';
            }
        }, 5000);
    });
    
    document.getElementById('register-form').addEventListener('submit', function(e) {
        e.preventDefault();
        const username = document.getElementById('register-username').value;
        const password = document.getElementById('register-password').value;
        const confirmPassword = document.getElementById('register-confirm').value;
        
        if (password !== confirmPassword) {
            document.getElementById('message').textContent = 'Passwords do not match';
            return;
        }
        
        // Check if already connected
        if (!chatClient.isConnected) {
            chatClient.connect();
        }
        
        // Wait for connection to be established
        const sendRegister = setInterval(() => {
            if (chatClient.isConnected) {
                clearInterval(sendRegister);
                chatClient.sendMessage(MessageType.REGISTER, 'server', username + ':' + password);
            }
        }, 100);
        
        // Timeout if connection takes too long
        setTimeout(() => {
            clearInterval(sendRegister);
            if (!chatClient.isConnected) {
                document.getElementById('message').textContent = 'Connection failed. Please try again.';
            }
        }, 5000);
    });
}

// Chat functionality
function initChat() {
    // Check if user is logged in, prioritize sessionStorage
    const username = sessionStorage.getItem('username') || localStorage.getItem('username');
    if (!username) {
        window.location.href = 'login.jsp';
        return;
    }
    
    // Set username in chatClient and UI immediately
    chatClient.username = username;
    const currentUserSpan = document.getElementById('current-user');
    if (currentUserSpan) {
        currentUserSpan.textContent = username;
    }
    
    // Initialize message synchronization mechanism first
    chatClient.initMessageSync();
    
    // Initialize connection
    chatClient.connect();
    // Room list will be requested after UUID authentication
    
    // Add event listener to close all child windows when parent window closes
    window.addEventListener('beforeunload', function() {
        chatClient.closeAllChildWindows();
    });
    
    // Message input
    document.getElementById('message-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });
    
    document.getElementById('send-btn').addEventListener('click', sendMessage);
    
    // Private message button functionality
    document.getElementById('private-msg-btn').addEventListener('click', function() {
        const input = document.getElementById('message-input');
        const message = input.value.trim();
        
        if (message) {
            const recipient = prompt('Enter username to send private message:');
            if (recipient && recipient.trim()) {
                chatClient.sendPrivateMessage(recipient.trim(), message);
                input.value = '';
                chatClient.showMessage(`[Private to ${recipient.trim()}] ${message}`, false);
            }
        } else {
            alert('Please type a message first.');
        }
    });
    
    function sendMessage() {
        const input = document.getElementById('message-input');
        const message = input.value.trim();
        if (message) {
            chatClient.sendMessage(MessageType.TEXT, chatClient.currentRoom, message);
            input.value = '';
        }
    }
    
    // Logout
    document.getElementById('logout-btn').addEventListener('click', function() {
        chatClient.logout();
    });
    
    // Room management
    document.getElementById('create-room-btn').addEventListener('click', function() {
        document.getElementById('create-room-modal').style.display = 'block';
    });
    
    document.querySelector('.close').addEventListener('click', function() {
        document.getElementById('create-room-modal').style.display = 'none';
    });
    
    window.addEventListener('click', function(e) {
        if (e.target === document.getElementById('create-room-modal')) {
            document.getElementById('create-room-modal').style.display = 'none';
        }
    });
    
    document.getElementById('create-room-form').addEventListener('submit', function(e) {
        e.preventDefault();
        const roomName = document.getElementById('room-name').value.trim();
        const roomType = document.getElementById('room-type').value;
        
        if (roomName) {
            chatClient.sendMessage(MessageType.CREATE_ROOM, roomName, roomType);
            document.getElementById('create-room-modal').style.display = 'none';
            document.getElementById('create-room-form').reset();
        }
    });
    
    // Join room modal functionality
    const joinRoomModal = document.getElementById('join-room-modal');
    const joinRoomForm = document.getElementById('join-room-form');
    const joinRoomCloseBtn = joinRoomModal.querySelector('.close');
    
    // Show join room modal when join button is clicked
    document.getElementById('join-room-btn').addEventListener('click', function() {
        joinRoomModal.style.display = 'block';
    });
    
    // Close join room modal when close button is clicked
    joinRoomCloseBtn.addEventListener('click', function() {
        joinRoomModal.style.display = 'none';
    });
    
    // Close join room modal when clicking outside
    window.addEventListener('click', function(e) {
        if (e.target === joinRoomModal) {
            joinRoomModal.style.display = 'none';
        }
    });
    
    // Handle join room form submission
    joinRoomForm.addEventListener('submit', function(e) {
        e.preventDefault();
        const roomName = document.getElementById('join-room-name').value;
        
        if (roomName.trim()) {
            chatClient.sendMessage(MessageType.JOIN, roomName, '');
            joinRoomModal.style.display = 'none';
            joinRoomForm.reset();
            
            // Refresh room list to reflect the join
            setTimeout(() => {
                chatClient.sendMessage(MessageType.LIST_ROOMS, 'server', '');
            }, 100);
        }
    });
    
    document.getElementById('leave-room-btn').addEventListener('click', function() {
        chatClient.sendMessage(MessageType.LEAVE, chatClient.currentRoom, chatClient.username + ' left the room');
        chatClient.currentRoom = 'system';
        chatClient.currentRoomType = 'PUBLIC';
        document.getElementById('current-room-name').textContent = 'System Room';
        
        // Refresh room list to reflect the leave
        chatClient.sendMessage(MessageType.LIST_ROOMS, 'server', '');
    });
    
    // Exit room button functionality
    document.getElementById('exit-room-btn').addEventListener('click', function() {
        // Check if current room is system, which cannot be exited
        if (chatClient.currentRoom === 'system') {
            chatClient.showMessage('[System] 不能退出系统房间', true);
            return;
        }
        
        // Send EXIT_ROOM message to server
        const roomToExit = chatClient.currentRoom;
        chatClient.sendMessage(MessageType.EXIT_ROOM, roomToExit, chatClient.username + ' exited the room');
        
        // Switch back to system room
        chatClient.currentRoom = 'system';
        chatClient.currentRoomType = 'PUBLIC';
        document.getElementById('current-room-name').textContent = 'System Room';
        
        // Refresh room list to reflect the exit
        chatClient.sendMessage(MessageType.LIST_ROOMS, 'server', '');
    });
    
    document.getElementById('refresh-rooms-btn').addEventListener('click', function() {
        chatClient.sendMessage(MessageType.LIST_ROOMS, 'server', '');
    });
}

// Tab switching for login/register
function switchTab(tabName) {
    // Hide all tab contents
    const tabContents = document.querySelectorAll('.tab-content');
    tabContents.forEach(content => {
        content.classList.remove('active');
    });
    
    // Remove active class from all tab buttons
    const tabBtns = document.querySelectorAll('.tab-btn');
    tabBtns.forEach(btn => {
        btn.classList.remove('active');
    });
    
    // Show selected tab content
    document.getElementById(tabName + '-tab').classList.add('active');
    
    // Add active class to selected tab button
    event.currentTarget.classList.add('active');
    
    // Clear message
    document.getElementById('message').textContent = '';
}