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
    LIST_ROOMS: 'LIST_ROOMS',
    LIST_ROOM_USERS: 'LIST_ROOM_USERS',
    REQUEST_HISTORY: 'REQUEST_HISTORY',
    HISTORY_RESPONSE: 'HISTORY_RESPONSE',
    REQUEST_LATEST_TIMESTAMP: 'REQUEST_LATEST_TIMESTAMP',
    LATEST_TIMESTAMP: 'LATEST_TIMESTAMP',
    REQUEST_TOKEN: 'REQUEST_TOKEN',
    TOKEN_RESPONSE: 'TOKEN_RESPONSE',
    IMAGE: 'IMAGE',
    REQUEST_PRIVATE_USERS: 'REQUEST_PRIVATE_USERS',
    PRIVATE_USERS_RESPONSE: 'PRIVATE_USERS_RESPONSE'
};

const AES_KEY = 'ChatRoomNSFWKey2024!@#';
const AES_IV_LENGTH = 12;

async function generateKey() {
    const encoder = new TextEncoder();
    const keyData = encoder.encode(AES_KEY);
    const hash = await crypto.subtle.digest('SHA-256', keyData);
    return await crypto.subtle.importKey(
        'raw',
        hash,
        { name: 'AES-GCM' },
        false,
        ['encrypt', 'decrypt']
    );
}

async function encryptData(data) {
    const encoder = new TextEncoder();
    const dataBytes = encoder.encode(data);
    
    const iv = crypto.getRandomValues(new Uint8Array(AES_IV_LENGTH));
    const key = await generateKey();
    
    const encrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: iv },
        key,
        dataBytes
    );
    
    const ivArray = Array.from(iv);
    const encryptedArray = Array.from(new Uint8Array(encrypted));
    
    return {
        iv: ivArray,
        data: encryptedArray
    };
}

async function decryptData(encryptedData, ivArray) {
    const key = await generateKey();
    const iv = new Uint8Array(ivArray);
    const encrypted = new Uint8Array(encryptedData);
    
    const decrypted = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: iv },
        key,
        encrypted
    );
    
    const decoder = new TextDecoder();
    return decoder.decode(decrypted);
}

function getLocalTime() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function getLocalTimeWithMillis() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const milliseconds = String(now.getMilliseconds()).padStart(3, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}.${milliseconds}`;
}

function getLocalTimeISO() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const milliseconds = String(now.getMilliseconds()).padStart(3, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${milliseconds}`;
}

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
    logEnabled: true,
    
    // ========== 新增：消息同步相关属性 ==========
    broadcastChannel: null, // 主窗口间的BroadcastChannel
    seenMessageIds: new Set(), // 消息去重集合
    syncLog: [], // 同步日志，用于调试
    
    // ========== 新增：消息持久化相关属性 ==========
    messageStorage: null, // 消息存储实例，将在initMessagePersistence中初始化
    lastSyncTime: {}, // 每个房间的最后同步时间
    isSyncing: {}, // 每个房间的同步状态
    maxMessagesPerRoom: 200, // 每个房间最多保存的消息数
    syncInterval: 30000, // 自动同步间隔（毫秒）
    useLocalStorageFallback: false, // 是否使用localStorage作为备用存储
    
    // ========== 新增：自动重连相关属性 ==========
    reconnectAttempts: 0, // 当前重连尝试次数
    maxReconnectAttempts: 10, // 最大重连尝试次数
    reconnectInterval: 3000, // 重连间隔（毫秒）
    reconnectTimer: null, // 重连定时器
    isReconnecting: false, // 是否正在重连
    pendingImageUpload: null, // 待上传的图片文件
    pendingImageNSFW: false, // 待上传图片是否为NSFW
    
    // 日志记录方法
    log: function(level, message) {
        if (!this.logEnabled) return;
        
        const validLevels = ['debug', 'info', 'warn', 'error'];
        if (!validLevels.includes(level)) {
            level = 'info';
        }
        
        const timestamp = getLocalTimeWithMillis();
        
        if (console && console[level]) {
            console[level](`[${timestamp}] [${level.toUpperCase()}] ${message}`);
        }
        
        // 记录同步日志（最多100条）
        this.syncLog.push({timestamp, level, message});
        if (this.syncLog.length > 100) {
            this.syncLog.shift();
        }
        
        // 非debug级别日志发送到服务器
        if (level !== 'debug') {
            try {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', 'log.jsp', true);
                xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
                xhr.timeout = 300;
                
                const params = new URLSearchParams();
                params.append('level', level);
                params.append('message', message);
                params.append('timestamp', timestamp);
                
                xhr.send(params.toString());
                xhr.onload = xhr.onerror = xhr.ontimeout = function() {};
            } catch (error) {}
        }
    },
    
    // ========== 新增：初始化消息同步系统 ==========
    initMessageSync: function() {
        // 子窗口：只监听父窗口的postMessage
        if (window.opener) {
            this.setupChildMessageListener();
            return;
        }
        
        // 主窗口：创建BroadcastChannel用于主窗口间通信
        if (typeof BroadcastChannel !== 'undefined') {
            try {
                this.broadcastChannel = new BroadcastChannel('chatroom-main-sync');
                this.broadcastChannel.onmessage = (event) => {
                    this.handleBroadcastMessage(event.data);
                };
                this.log('info', '主窗口BroadcastChannel初始化成功');
            } catch (error) {
                this.log('error', `BroadcastChannel初始化失败: ${error.message}`);
                this.setupLocalStorageFallback();
            }
        } else {
            this.setupLocalStorageFallback();
        }
        
        // 监听子窗口的ready消息
        this.setupChildWindowListeners();
    },
    
    // ========== 新增：处理广播消息 ==========
    handleBroadcastMessage: function(data) {
        console.log('========== 开始处理广播消息 ==========');
        console.log('收到广播消息:', JSON.stringify(data));
        
        // 消息去重检查
        if (this.isMessageDuplicate(data.uniqueId)) {
            console.log('忽略重复消息:', data.uniqueId);
            this.log('debug', `忽略重复消息: ${data.uniqueId}`);
            return;
        }
        
        console.log('处理广播消息:', data.type, 'for', data.roomName || 'all', 'ID:', data.uniqueId);
        this.log('debug', `处理广播消息: ${data.type} for ${data.roomName || 'all'}`);
        
        switch (data.type) {
            case 'SYNC_MESSAGES':
                console.log('处理SYNC_MESSAGES消息，房间:', data.roomName, '消息数:', (data.messages || []).length);
                this.syncMessagesToRoom(data.roomName, data.messages);
                console.log('SYNC_MESSAGES消息处理完成');
                break;
                
            case 'NEW_MESSAGE':
                console.log('处理NEW_MESSAGE消息');
                // 检查数据结构，如果data.data存在，说明是来自BroadcastChannel的完整消息对象
                // 需要传递data.data给processIncomingMessage
                if (data.data) {
                    console.log('NEW_MESSAGE消息包含嵌套data，使用data.data处理');
                    this.processIncomingMessage(data.data);
                } else {
                    console.log('NEW_MESSAGE消息直接处理');
                    this.processIncomingMessage(data);
                }
                console.log('NEW_MESSAGE消息处理完成');
                break;
                
            case 'ROOM_LIST_UPDATE':
                console.log('处理ROOM_LIST_UPDATE消息');
                console.log('ROOM_LIST_UPDATE消息完整数据:', JSON.stringify(data));
                
                // 检查data.data是否存在，因为消息结构是{type, data, roomName, ...}
                if (data.data && data.data.rooms) {
                    console.log('更新房间列表，房间数:', data.data.rooms.length);
                    this.rooms = data.data.rooms;
                    this.updateRoomsList();
                    console.log('房间列表更新完成');
                } else if (data.rooms) {
                    // 兼容旧格式
                    console.log('更新房间列表(旧格式)，房间数:', data.rooms.length);
                    this.rooms = data.rooms;
                    this.updateRoomsList();
                    console.log('房间列表更新完成(旧格式)');
                } else {
                    console.log('ROOM_LIST_UPDATE消息没有包含rooms数据');
                }
                break;
            default:
                console.log('收到未知类型的广播消息:', data.type);
                break;
        }
        
        console.log('========== 广播消息处理结束 ==========');
    },
    
    // ========== 新增：消息去重机制 ==========
    isMessageDuplicate: function(messageId) {
        if (this.seenMessageIds.has(messageId)) {
            return true;
        }
        this.seenMessageIds.add(messageId);
        
        // 清理旧的消息ID（防止内存泄漏）
        if (this.seenMessageIds.size > 1000) {
            const ids = Array.from(this.seenMessageIds);
            this.seenMessageIds = new Set(ids.slice(-500));
        }
        return false;
    },
    
    // ========== 新增：生成唯一消息ID ==========
    generateMessageId: function(type, roomName) {
        const timestamp = Date.now();
        const random = Math.random().toString(36).substr(2, 9);
        return `${type}_${roomName}_${timestamp}_${random}`;
    },
    
    // ========== 消息持久化核心方法 ==========
    
    // 初始化消息持久化系统
    initMessagePersistence: function() {
        console.log('=== initMessagePersistence START ===');
        
        // 直接测试MessageStorage是否存在
        console.log('MessageStorage in global scope:', typeof MessageStorage);
        if (typeof window.MessageStorage !== 'undefined') {
            console.log('MessageStorage is in window object:', window.MessageStorage);
        }
        
        // 确保MessageStorage可用
        if (typeof MessageStorage === 'undefined') {
            console.error('ERROR: MessageStorage is not defined!');
            console.error('Check if localStorage.js is properly loaded before chat.js');
            return;
        }
        
        // 重新初始化messageStorage
        this.messageStorage = MessageStorage;
        console.log('messageStorage initialized:', this.messageStorage);
        
        // 检查IndexedDB支持
        if (!this.messageStorage.isIndexedDBSupported()) {
            console.warn('IndexedDB is not supported, falling back to localStorage');
            this.log('warn', 'IndexedDB不可用，使用localStorage作为备用存储');
            this.useLocalStorageFallback = true;
        } else {
            // 测试IndexedDB连接
            this.testIndexedDBConnection();
        }
        
        this.log('info', '初始化消息持久化系统');
        
        // 加载本地缓存的消息
        this.loadAllLocalMessages();
        
        // 启动自动同步
        this.startAutoSync();
    },
    
    // 测试IndexedDB连接
    testIndexedDBConnection: function() {
        console.log('Testing IndexedDB connection...');
        if (this.messageStorage) {
            this.messageStorage.openDB()
                .then(db => {
                    console.log('IndexedDB connection successful:', db);
                    this.useLocalStorageFallback = false;
                    // 测试创建存储对象
                    const transaction = db.transaction([this.messageStorage.STORE_NAME, this.messageStorage.LAST_SYNC_STORE], 'readwrite');
                    const messageStore = transaction.objectStore(this.messageStorage.STORE_NAME);
                    const lastSyncStore = transaction.objectStore(this.messageStorage.LAST_SYNC_STORE);
                    console.log('IndexedDB stores:', messageStore, lastSyncStore);
                })
                .catch(error => {
                    console.error('IndexedDB connection failed:', error);
                    console.warn('Falling back to localStorage');
                    this.log('warn', 'IndexedDB连接失败，使用localStorage作为备用存储');
                    this.useLocalStorageFallback = true;
                });
        }
    },
    
    // 加载所有本地消息
    loadAllLocalMessages: function() {
        // 这里可以扩展为加载所有房间的本地消息
        // 目前只加载当前房间的消息
        this.loadLocalMessages(this.currentRoom);
    },
    
    // 加载指定房间的本地消息
    loadLocalMessages: function(roomName) {
        console.log('loadLocalMessages called:', roomName, this.messageStorage);
        this.log('debug', `加载${roomName}房间的本地消息`);
        
        if (!this.messageStorage) {
            console.error('loadLocalMessages skipped: messageStorage not available');
            return;
        }
        
        // 根据是否使用备用存储选择不同的加载方式
        if (this.useLocalStorageFallback) {
            // 使用localStorage备用存储
            const messages = this.messageStorage.getBackupMessages(roomName);
            console.log('Loaded messages from localStorage backup:', messages);
            
            if (messages && messages.length > 0) {
                if (!this.messages[roomName]) {
                    this.messages[roomName] = [];
                }
                
                messages.forEach(msg => {
                    const internalMsg = {
                        content: msg.content,
                        from: msg.from,
                        time: msg.createTime,
                        isSystem: msg.isSystem || false,
                        id: msg.id,
                        type: msg.type || 'TEXT'
                    };
                    
                    if (!this.messages[roomName].some(m => m.id === internalMsg.id)) {
                        this.messages[roomName].push(internalMsg);
                    }
                });
                
                this.log('info', `成功从localStorage加载${roomName}房间的${messages.length}条本地消息`);
                
                if (this.currentRoom === roomName) {
                    this.updateMessagesArea(roomName);
                }
            }
        } else {
            // 使用IndexedDB
            this.messageStorage.openDB()
                .then(db => {
                    console.log('Database created, now getting room messages');
                    console.log('Calling getRoomMessages for room:', roomName);
                    return this.messageStorage.getRoomMessages(roomName, this.maxMessagesPerRoom);
                })
                .then(messages => {
                    console.log('getRoomMessages returned:', messages);
                    if (messages && messages.length > 0) {
                            // 确保messages对象中存在该房间
                            if (!this.messages[roomName]) {
                                this.messages[roomName] = [];
                            }
                            
                            // 将本地消息添加到内存中
                            messages.forEach(msg => {
                                // 转换为内部消息格式
                                const internalMsg = {
                                    content: msg.content,
                                    from: msg.from,
                                    time: msg.createTime,
                                    isSystem: msg.isSystem || false,
                                    id: msg.id,
                                    type: msg.type || 'TEXT'
                                };
                                
                                if (!this.messages[roomName].some(m => m.id === internalMsg.id)) {
                                    this.messages[roomName].push(internalMsg);
                                }
                            });
                            
                            this.log('info', `成功加载${roomName}房间的${messages.length}条本地消息`);
                            
                            // 如果当前正在查看该房间，更新显示
                            if (this.currentRoom === roomName) {
                                this.updateMessagesArea(roomName);
                            }
                            
                            // 检查是否需要同步最新消息
                            this.checkAndSyncMessages(roomName);
                        }
                    })
                .catch(error => {
                    this.log('error', `加载本地消息失败: ${error.message}`);
                    // 降级到localStorage
                    this.useLocalStorageFallback = true;
                    this.log('warn', 'IndexedDB加载失败，降级到localStorage备用存储');
                    // 重新加载
                    this.loadLocalMessages(roomName);
                });
        }
    },
    
    // 保存消息到本地存储
    saveMessageToLocal: function(roomName, message) {
        console.log('saveMessageToLocal called:', roomName, message, this.messageStorage);
        if (!roomName || !message) {
            console.log('saveMessageToLocal skipped: missing roomName or message');
            return;
        }
        if (!this.messageStorage) {
            console.log('saveMessageToLocal skipped: messageStorage not available');
            // 尝试重新初始化MessageStorage
            this.messageStorage = typeof MessageStorage !== 'undefined' ? MessageStorage : null;
            console.log('Reinitialized messageStorage:', this.messageStorage);
            if (!this.messageStorage) return;
        }
        
        const storageMsg = {
            roomName: roomName,
            from: message.from,
            to: message.to || roomName,
            content: message.content,
            createTime: message.time || getLocalTimeISO(),
            type: message.type || 'TEXT',
            messageType: message.isSystem ? 'SYSTEM' : 'USER',
            isSystem: message.isSystem || false,
            id: message.id
        };
        
        // 根据是否使用备用存储选择不同的保存方式
        if (this.useLocalStorageFallback) {
            // 使用localStorage作为备用存储
            this.messageStorage.saveBackupMessage(storageMsg);
            this.log('debug', `消息已保存到localStorage备用存储: ${message.content.substring(0, 20)}...`);
        } else {
            // 使用IndexedDB
            this.messageStorage.saveMessage(storageMsg)
                .then(() => {
                    this.log('debug', `消息已保存到IndexedDB: ${message.content.substring(0, 20)}...`);
                    // 更新最后同步时间
                    this.lastSyncTime[roomName] = getLocalTimeISO();
                    this.saveLastSyncTime();
                })
                .catch(error => {
                    this.log('error', `保存消息到IndexedDB失败: ${error.message}`);
                    // 降级到localStorage
                    this.useLocalStorageFallback = true;
                    this.messageStorage.saveBackupMessage(storageMsg);
                    this.log('warn', '已降级到localStorage备用存储');
                });
        }
        
        // 更新最后同步时间
        this.lastSyncTime[roomName] = getLocalTimeISO();
        this.saveLastSyncTime();
    },
    
    // 检查并同步消息
    checkAndSyncMessages: function(roomName) {
        if (!this.isConnected || this.isSyncing[roomName]) return;
        
        // 检查是否已认证，未认证时不请求同步
        if (!this.isAuthenticated) {
            this.log('debug', `用户未认证，跳过${roomName}房间的消息同步检查`);
            return;
        }
        
        this.isSyncing[roomName] = true;
        this.log('debug', `检查${roomName}房间的消息同步状态`);
        
        // 向服务器请求最新消息的时间戳
        const requestMsg = {
            type: 'REQUEST_LATEST_TIMESTAMP',
            from: this.username,
            to: roomName,
            time: getLocalTimeISO()
        };
        
        this.sendMessage(requestMsg);
    },
    
    // 向服务器请求消息历史
    requestMessageHistory: function(roomName, lastTimestamp) {
        if (!this.isConnected || this.isSyncing[roomName]) return;
        
        this.isSyncing[roomName] = true;
        
        // 检查是否是私聊虚拟房间名（格式：好友username）
        let actualRoomName = roomName;
        if (roomName.startsWith('好友')) {
            // 提取真实的用户名
            actualRoomName = roomName.substring(2);
            this.log('debug', `检测到私聊虚拟房间名: ${roomName}，实际用户名: ${actualRoomName}`);
        }
        
        // 如果没有提供lastTimestamp，从IndexedDB获取本地最晚消息时间戳
        if (!lastTimestamp || lastTimestamp === '0' || lastTimestamp === 0) {
            if (this.messageStorage) {
                this.messageStorage.getLatestMessageTimestamp(roomName)
                    .then(localLatestTimestamp => {
                        const timestampToUse = localLatestTimestamp || 0;
                        this.log('info', `向服务器请求${roomName}房间的历史消息，从时间戳${timestampToUse}开始`);
                        
                        const requestMsg = {
                            type: MessageType.REQUEST_HISTORY,
                            from: this.username,
                            to: actualRoomName, // 使用实际的房间名或用户名
                            content: String(timestampToUse), // 将时间戳放在content字段中
                            time: getLocalTimeISO()
                        };
                        
                        this.sendMessage(requestMsg);
                    })
                    .catch(error => {
                        this.log('error', `获取本地最晚消息时间戳失败: ${error.message}`);
                        // 失败时使用0作为默认值
                        this.log('info', `向服务器请求${roomName}房间的历史消息，从时间戳0开始`);
                        
                        const requestMsg = {
                            type: MessageType.REQUEST_HISTORY,
                            from: this.username,
                            to: actualRoomName, // 使用实际的房间名或用户名
                            content: '0', // 将0放在content字段中
                            time: getLocalTimeISO()
                        };
                        
                        this.sendMessage(requestMsg);
                    });
            } else {
                // 如果messageStorage不可用，使用0作为默认值
                this.log('info', `向服务器请求${roomName}房间的历史消息，从时间戳0开始`);
                
                const requestMsg = {
                    type: MessageType.REQUEST_HISTORY,
                    from: this.username,
                    to: actualRoomName, // 使用实际的房间名或用户名
                    content: '0', // 将0放在content字段中
                    time: getLocalTimeISO()
                };
                
                this.sendMessage(requestMsg);
            }
        } else {
            // 如果提供了lastTimestamp，直接使用
            this.log('info', `向服务器请求${roomName}房间的历史消息，从时间戳${lastTimestamp}开始`);
            
            const requestMsg = {
                type: MessageType.REQUEST_HISTORY,
                from: this.username,
                to: actualRoomName, // 使用实际的房间名或用户名
                content: String(lastTimestamp), // 将lastTimestamp放在content字段中
                time: getLocalTimeISO()
            };
            
            this.sendMessage(requestMsg);
        }
    },
    
    // 请求私聊用户列表
    requestPrivateUsers: function() {
        if (!this.isConnected) return;
        
        this.log('info', '向服务器请求私聊用户列表');
        
        const requestMsg = {
            type: MessageType.REQUEST_PRIVATE_USERS,
            from: this.username,
            to: 'server',
            content: '',
            time: getLocalTimeISO()
        };
        
        this.sendMessage(requestMsg);
    },
    
    // 处理私聊用户列表响应
    handlePrivateUsersResponse: function(message) {
        this.log('info', '收到服务器返回的私聊用户列表');
        
        try {
            const users = message.content ? JSON.parse(message.content) : [];
            
            if (users.length === 0) {
                this.log('info', '没有私聊用户');
                return;
            }
            
            this.log('info', `收到${users.length}个私聊用户`);
            
            // 为每个私聊用户请求历史消息
            users.forEach(username => {
                const privateRoomName = `好友${username}`;
                this.log('info', `请求与${username}的私聊历史消息`);
                this.requestMessageHistory(privateRoomName);
            });
        } catch (error) {
            this.log('error', `处理私聊用户列表响应失败: ${error.message}`);
        }
    },
    
    // 处理服务器返回的历史消息
    handleHistoryMessages: function(messages, roomName) {
        if (!messages || messages.length === 0) {
            this.log('info', `服务器没有返回${roomName}房间的历史消息`);
            return;
        }
        
        this.log('info', `收到${roomName}房间的${messages.length}条历史消息`);
        
        // 保存消息到本地并更新显示
        let newMessages = 0;
        
        messages.forEach(msg => {
            // 转换为内部消息格式
            const internalMsg = {
                content: msg.content,
                from: msg.from,
                time: msg.time,
                isSystem: msg.type === 'SYSTEM',
                id: msg.id || this.generateMessageId(),
                type: msg.type || 'TEXT'
            };
            
            // 检查消息是否已存在
            if (!this.messages[roomName] || !this.messages[roomName].some(m => m.id === internalMsg.id)) {
                // 确保messages对象中存在该房间
                if (!this.messages[roomName]) {
                    this.messages[roomName] = [];
                }
                
                this.messages[roomName].push(internalMsg);
                this.saveMessageToLocal(roomName, internalMsg);
                newMessages++;
            }
        });
        
        if (newMessages > 0) {
            // 如果当前正在查看该房间，更新显示
            if (this.currentRoom === roomName) {
                this.updateMessagesArea(roomName);
            }
            
            this.log('info', `成功同步${roomName}房间的${newMessages}条新消息`);
        }
        
        // 更新最后同步时间
        this.lastSyncTime[roomName] = getLocalTimeISO();
        this.saveLastSyncTime();
        
        this.isSyncing[roomName] = false;
    },
    
    // 保存最后同步时间
    saveLastSyncTime: function() {
        localStorage.setItem('lastSyncTime', JSON.stringify(this.lastSyncTime));
    },
    
    // 加载最后同步时间
    loadLastSyncTime: function() {
        const syncTimeStr = localStorage.getItem('lastSyncTime');
        if (syncTimeStr) {
            try {
                this.lastSyncTime = JSON.parse(syncTimeStr);
            } catch (error) {
                this.log('error', `加载最后同步时间失败: ${error.message}`);
                this.lastSyncTime = {};
            }
        }
    },
    
    // 启动自动同步
    startAutoSync: function() {
        setInterval(() => {
            // 只同步当前房间的消息
            if (this.isConnected && this.currentRoom) {
                this.checkAndSyncMessages(this.currentRoom);
            }
        }, this.syncInterval);
    },
    
    // ========== 新增：发送消息到所有窗口 ==========
    broadcastToWindows: function(type, data, targetRoom = null) {
        const uniqueId = this.generateMessageId(type, targetRoom || 'all');
        
        const message = {
            type,
            data,
            roomName: targetRoom,
            uniqueId,
            timestamp: Date.now(),
            source: 'main_window'
        };
        
        console.log('========== 开始消息广播流程 ==========');
        console.log('广播消息类型:', type, '目标房间:', targetRoom || 'all', 'ID:', uniqueId);
        console.log('广播消息详细数据:', JSON.stringify(data));
        
        this.log('debug', `广播消息: ${type} to ${targetRoom || 'all'} (${uniqueId})`);
        
        // 1. 通过BroadcastChannel发送给其他主窗口标签页
        console.log('广播步骤1: 发送到其他主窗口标签页');
        if (this.broadcastChannel) {
            try {
                console.log('使用BroadcastChannel发送消息:', type, 'ID:', uniqueId);
                this.broadcastChannel.postMessage(message);
                console.log('BroadcastChannel消息发送成功');
            } catch (error) {
                console.error('BroadcastChannel发送失败:', error.message);
                this.log('error', `BroadcastChannel发送失败: ${error.message}`);
                this.storeToLocalStorage(message);
            }
        } else {
            console.log('BroadcastChannel不可用，使用localStorage降级方案');
            this.storeToLocalStorage(message);
        }
        
        // 2. 通过postMessage发送给子窗口
        console.log('广播步骤2: 发送到子窗口');
        this.syncToChildWindows(targetRoom, message);
        
        // 3. 自己也要处理这个消息（避免自己发送的不处理）
        console.log('广播步骤3: 自己处理消息');
        setTimeout(() => {
            if (!this.isMessageDuplicate(uniqueId)) {
                console.log('自己处理广播消息:', type, 'ID:', uniqueId);
                // 对于NEW_MESSAGE类型，sendMessage函数已经添加了消息，不需要自己处理
                if (type !== 'NEW_MESSAGE') {
                    // 这里需要传递message.data而不是整个message对象
                    this.handleBroadcastMessage(message.data);
                    console.log('自己处理广播消息完成');
                } else {
                    console.log('跳过自己处理NEW_MESSAGE消息，因为sendMessage函数已经添加');
                }
            } else {
                console.log('忽略重复的广播消息:', uniqueId);
            }
        }, 10);
        
        console.log('========== 消息广播流程结束 ==========');
    },
    
    // ========== 新增：同步到子窗口 ==========
    syncToChildWindows: function(targetRoom, message) {
        let syncCount = 0;
        
        console.log('主窗口开始同步消息到子窗口，目标房间:', targetRoom || 'all', '消息类型:', message.type, 'ID:', message.uniqueId);
        console.log('主窗口当前子窗口数量:', Object.keys(this.childWindows).length);
        
        Object.entries(this.childWindows).forEach(([roomName, childWindow]) => {
            try {
                // 如果指定了目标房间，只同步给对应的子窗口
                if (targetRoom && roomName !== targetRoom) {
                    console.log('主窗口跳过子窗口', roomName, '因为目标房间不匹配:', targetRoom);
                    return;
                }
                
                if (!childWindow.closed) {
                    if (childWindow.postMessage) {
                        // 简化消息格式，避免循环
                        const childMessage = {
                            type: message.type,
                            data: message.data,
                            roomName: message.roomName,
                            uniqueId: message.uniqueId,
                            source: 'parent'
                        };
                        
                        console.log('主窗口向子窗口', roomName, '发送消息:', message.type, 'ID:', message.uniqueId);
                        console.log('主窗口发送的详细消息内容:', JSON.stringify(childMessage));
                        
                        childWindow.postMessage(childMessage, '*');
                        syncCount++;
                        console.log('主窗口成功发送消息到子窗口', roomName, '发送总数:', syncCount);
                    } else {
                        console.log('主窗口跳过子窗口', roomName, '因为postMessage不可用');
                        // 窗口已关闭或无法通信，清理引用
                        delete this.childWindows[roomName];
                        console.log('主窗口清理子窗口引用:', roomName);
                    }
                } else {
                    // 窗口已关闭，清理引用
                    delete this.childWindows[roomName];
                    console.log('主窗口清理已关闭的子窗口引用:', roomName);
                }
            } catch (error) {
                console.log('主窗口同步到子窗口', roomName, '失败:', error.message);
                this.log('warn', `同步到子窗口 ${roomName} 失败: ${error.message}`);
                delete this.childWindows[roomName];
                console.log('主窗口清理出错的子窗口引用:', roomName);
            }
        });
        
        console.log('主窗口子窗口同步完成，成功发送到', syncCount, '个子窗口');
        if (syncCount > 0) {
            this.log('debug', `已同步到 ${syncCount} 个子窗口`);
        }
    },
    
    // ========== 新增：处理收到的消息 ==========
    processIncomingMessage: function(messageData) {
        const { roomName, message } = messageData;
        
        if (!roomName || !message) {
            this.log('warn', '无效的消息数据格式');
            return;
        }
        
        // 存储消息
        if (!this.messages[roomName]) {
            this.messages[roomName] = [];
        }
        
        // 检查是否已存在相同消息 - 使用消息ID优先，然后使用内容+时间+发送者
        const isDuplicate = this.messages[roomName].some(m => 
            (message.id && m.id === message.id) || 
            (!message.id && m.content === message.content && 
             m.from === message.from && 
             m.time === message.time)
        );
        
        if (!isDuplicate) {
            this.messages[roomName].push(message);
            
            // 更新当前窗口UI
            if (this.currentRoom === roomName) {
                this.updateMessagesArea(roomName);
            }
            
            this.log('debug', `已处理新消息到房间 ${roomName}: ${message.content.substring(0, 50)}`);
        }
    },
    
    // ========== 新增：同步消息到特定房间 ==========
    syncMessagesToRoom: function(roomName, messages) {
        if (!roomName || !messages) {
            this.log('warn', '无效的同步参数');
            return;
        }
        
        this.messages[roomName] = messages;
        
        // 更新当前窗口UI
        if (this.currentRoom === roomName) {
            this.updateMessagesArea(roomName);
        }
        
        this.log('debug', `已同步 ${messages.length} 条消息到房间 ${roomName}`);
    },
    
    // ========== 新增：localStorage降级方案 ==========
    setupLocalStorageFallback: function() {
        this.log('info', '使用localStorage作为同步降级方案');
        
        window.addEventListener('storage', (event) => {
            if (event.key === 'chatroom-sync-fallback' && event.newValue) {
                try {
                    const data = JSON.parse(event.newValue);
                    if (!this.isMessageDuplicate(data.uniqueId)) {
                        this.handleBroadcastMessage(data);
                    }
                } catch (error) {
                    this.log('error', `解析localStorage数据失败: ${error.message}`);
                }
            }
        });
    },
    
    storeToLocalStorage: function(message) {
        try {
            localStorage.setItem('chatroom-sync-fallback', JSON.stringify(message));
            setTimeout(() => {
                localStorage.removeItem('chatroom-sync-fallback');
            }, 100);
        } catch (error) {
            this.log('error', `存储到localStorage失败: ${error.message}`);
        }
    },
    
    // ========== 新增：子窗口监听器设置 ==========
    setupChildWindowListeners: function() {
        window.addEventListener('message', (event) => {
            const data = event.data;
            
            // 只处理来自子窗口的消息
            if (data.source !== 'child') return;
            
            switch (data.type) {
                case 'CHILD_READY':
                    this.handleChildReady(data.roomName, event.source);
                    break;
                    
                case 'REQUEST_SYNC':
                    this.syncToChildWindow(data.roomName, event.source);
                    break;
            }
        });
    },
    
    handleChildReady: function(roomName, childWindow) {
        this.log('info', `子窗口准备就绪: ${roomName}`);
        
        // 存储窗口引用
        this.childWindows[roomName] = childWindow;
        
        // 立即同步消息
        this.syncToChildWindow(roomName, childWindow);
        
        // 发送JOIN消息到服务器
        this.sendMessage(MessageType.JOIN, roomName, '');
    },
    
    syncToChildWindow: function(roomName, childWindow) {
        try {
            const syncData = {
                type: 'SYNC_MESSAGES',
                data: {
                    roomName: roomName,
                    messages: this.messages[roomName] || [],
                    allMessages: this.messages,
                    rooms: this.rooms,
                    username: this.username,
                    currentRoom: this.currentRoom
                },
                uniqueId: this.generateMessageId('SYNC', roomName),
                source: 'parent'
            };
            
            childWindow.postMessage(syncData, '*');
            this.log('debug', `已同步初始数据到子窗口: ${roomName}`);
        } catch (error) {
            this.log('error', `同步到子窗口失败: ${error.message}`);
        }
    },
    
    // ========== 新增：子窗口消息监听器设置（供子窗口使用） ==========
    setupChildMessageListener: function() {
        window.addEventListener('message', (event) => {
            const data = event.data;
            
            // 只处理来自父窗口的消息
            if (data.source !== 'parent') return;
            
            this.log('debug', `子窗口收到消息: ${data.type} for ${data.roomName || 'all'}`);
            
            // 消息去重
            if (this.isMessageDuplicate(data.uniqueId)) {
                return;
            }
            
            switch (data.type) {
                case 'SYNC_MESSAGES':
                    if (data.data && data.data.roomName === this.currentRoom) {
                        this.messages[data.data.roomName] = data.data.messages || [];
                        if (data.data.username) this.username = data.data.username;
                        if (data.data.rooms) this.rooms = data.data.rooms;
                        
                        this.updateMessagesArea(this.currentRoom);
                        this.log('debug', `子窗口同步完成: ${this.currentRoom}`);
                    }
                    break;
                    
                case 'NEW_MESSAGE':
                    if (data.data && data.data.roomName === this.currentRoom) {
                        const message = data.data.message;
                        if (!this.messages[this.currentRoom]) {
                            this.messages[this.currentRoom] = [];
                        }
                        
                        // 去重检查
                        const isDuplicate = this.messages[this.currentRoom].some(m => 
                            m.content === message.content && 
                            m.from === message.from && 
                            m.time === message.time
                        );
                        
                        if (!isDuplicate) {
                            this.messages[this.currentRoom].push(message);
                            this.updateMessagesArea(this.currentRoom);
                        }
                    }
                    break;
            }
        });
        
        // 通知父窗口准备就绪
        if (window.opener) {
            setTimeout(() => {
                try {
                    window.opener.postMessage({
                        type: 'CHILD_READY',
                        roomName: this.currentRoom,
                        source: 'child'
                    }, '*');
                    this.log('info', '已通知父窗口子窗口准备就绪');
                } catch (error) {
                    this.log('error', `通知父窗口失败: ${error.message}`);
                }
            }, 500);
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
            
            // 重置重连状态
            this.resetReconnectState();
            
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
                    // 注意：不要在这里调用 checkAndSyncMessages，因为认证是异步的
                    // 消息同步会在 handleUUIDAuthSuccess 中触发
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
            this.isAuthenticated = false;
            
            // 如果不是手动关闭，则尝试自动重连
            if (event.code !== 1000 && !this.isReconnecting) {
                this.scheduleReconnect();
            }
            
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
            
            // If on chat.jsp, show reconnect option and attempt auto-reconnect
            const messagesArea = document.getElementById('messages-area');
            if (messagesArea && window.location.pathname.includes('chat.jsp')) {
                if (!this.isReconnecting) {
                    messagesArea.innerHTML += '<div class="system-message">Connection lost. Attempting to reconnect...</div>';
                    this.scheduleReconnect();
                }
            }
        };
    },
    
    // ========== 新增：自动重连机制 ==========
    scheduleReconnect: function() {
        if (this.isReconnecting) {
            return;
        }
        
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            this.log('error', 'Max reconnection attempts reached. Please refresh the page to reconnect.');
            const messagesArea = document.getElementById('messages-area');
            if (messagesArea) {
                messagesArea.innerHTML += '<div class="system-message error">Failed to reconnect after multiple attempts. Please refresh the page.</div>';
            }
            return;
        }
        
        this.isReconnecting = true;
        this.reconnectAttempts++;
        
        const delay = this.reconnectInterval * Math.pow(2, this.reconnectAttempts - 1); // 指数退避
        const maxDelay = 30000; // 最大延迟30秒
        const actualDelay = Math.min(delay, maxDelay);
        
        this.log('info', `Scheduling reconnection attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts} in ${actualDelay}ms`);
        
        this.reconnectTimer = setTimeout(() => {
            this.log('info', `Attempting to reconnect (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            this.connect();
        }, actualDelay);
    },
    
    // ========== 新增：重置重连状态 ==========
    resetReconnectState: function() {
        this.isReconnecting = false;
        this.reconnectAttempts = 0;
        
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    },
    
    // Send message - child windows send through parent window
    sendMessage: function(type, to, content) {
        if (window.opener && window.opener.chatClient) {
            console.log('子窗口通过父窗口发送消息:', type, to, content);
            window.opener.chatClient.sendMessage(type, to, content);
            return;
        }
        
        if (!this.isConnected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            this.showMessage('未连接到服务器');
            return;
        }
        
        let message;
        
        // 检查第一个参数是否是对象（完整的消息对象）
        if (typeof type === 'object' && type !== null) {
            // 直接使用完整的消息对象
            message = type;
        } else {
            // Get username from localStorage if this.username is not set
            const username = this.username || localStorage.getItem('username') || 'unknown';
            
            // 构造标准消息对象
            message = {
                type: type,
                from: username,
                to: to,
                content: content,
                time: getLocalTime(),
                id: this.generateMessageId('TEXT', to)
            };
        }
        
        // Ensure message has a unique ID
        if (!message.id) {
            message.id = this.generateMessageId(message.type, message.to || 'system');
        }
        
        // Send message through WebSocket
        this.ws.send(JSON.stringify(message));
        
        // 如果是文本消息或图片消息，立即在本地显示并同步
        if (message.type === MessageType.TEXT || message.type === MessageType.IMAGE) {
            let roomName = message.to || 'system';
            let actualContent = message.content;
            
            // Check if this is a private message
            if (this.isInPrivateChat && this.privateChatRecipient) {
                // For private chat, use the virtual room name
                roomName = this.currentRoom; // 好友_username
                
                // Remove room prefix if present
                if (actualContent.startsWith('[room:') && message.type === MessageType.TEXT) {
                    const roomEnd = actualContent.indexOf(']');
                    if (roomEnd > 0) {
                        actualContent = actualContent.substring(roomEnd + 1);
                    }
                }
            }
            
            if (!this.messages[roomName]) {
                this.messages[roomName] = [];
            }
            
            const localMessage = {
                content: actualContent,
                from: message.from,
                time: message.time,
                isSystem: false,
                id: message.id,
                type: message.type
            };
            
            // Check for duplicate before adding
            const isDuplicate = this.messages[roomName].some(m => m.id === localMessage.id);
            if (!isDuplicate) {
                this.messages[roomName].push(localMessage);
                
                // 保存消息到本地存储
                if (this.messageStorage) {
                    this.saveMessageToLocal(roomName, localMessage);
                }
                
                // 更新当前窗口UI
                if (this.currentRoom === roomName) {
                    this.updateMessagesArea(roomName);
                }
                
                // 广播到其他窗口
                this.broadcastToWindows('NEW_MESSAGE', {
                    roomName: roomName,
                    message: localMessage
                }, roomName);
            }
        }
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
            case MessageType.LIST_ROOM_USERS:
                this.handleListRooms(message);
                break;
            case MessageType.HISTORY_RESPONSE:
                this.handleHistoryResponse(message);
                break;
            case 'LATEST_TIMESTAMP':
                this.handleLatestTimestamp(message);
                break;
            case MessageType.TOKEN_RESPONSE:
                this.handleTokenResponse(message);
                break;
            case MessageType.IMAGE:
                this.handleImageMessage(message);
                break;
            case MessageType.UUID_AUTH_SUCCESS:
                this.handleUUIDAuthSuccess(message);
                break;
            case MessageType.UUID_AUTH_FAILURE:
                this.handleUUIDAuthFailure(message);
                break;
            case MessageType.PRIVATE_USERS_RESPONSE:
                this.handlePrivateUsersResponse(message);
                break;
            default:
                console.log('Unknown message type:', message.type);
        }
    },
    


    // Show message in the UI
    showMessage: function(message, isSystem = false, roomName = null) {
        const targetRoom = roomName || this.currentRoom;
        
        if (!this.messages[targetRoom]) {
            this.messages[targetRoom] = [];
        }
        
        const username = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
        
        const messageObj = {
            content: message,
            isSystem: isSystem,
            time: getLocalTime(),
            from: isSystem ? 'System' : username
        };
        
        this.messages[targetRoom].push(messageObj);
        
        // 保存系统消息到本地存储（但排除连接状态消息）
        if (this.messageStorage && messageObj.isSystem && !message.includes('Connected to chat server via WebSocket')) {
            // 确保消息有唯一ID
            if (!messageObj.id) {
                messageObj.id = this.generateMessageId();
            }
            this.saveMessageToLocal(targetRoom, messageObj);
        }
        
        // 只更新当前窗口
        if (this.currentRoom === targetRoom) {
            this.updateMessagesArea(targetRoom);
        }
        
        // 如果是系统消息，只广播系统通知，不广播消息内容
        if (isSystem && message.includes('加入了房间') || message.includes('离开了房间')) {
            this.broadcastToWindows('NEW_MESSAGE', {
                roomName: targetRoom,
                message: messageObj
            }, targetRoom);
        }
    },
    
    // Update messages area with stored messages for a specific room
    updateMessagesArea: function(roomName) {
        const messagesArea = document.getElementById('messages-area');
        if (messagesArea) {
            messagesArea.innerHTML = '';
            if (this.messages[roomName]) {
                const currentUsername = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
                
                this.messages[roomName].forEach(msg => {
                    const messageDiv = document.createElement('div');
                    if (msg.isSystem) {
                        messageDiv.className = 'system-message';
                        messageDiv.innerHTML = msg.content;
                    } else {
                        const displayedUsername = msg.from;
                        const isSent = displayedUsername === currentUsername;
                        
                        const messageWrapper = document.createElement('div');
                        messageWrapper.className = isSent ? 'sent-message-wrapper' : 'received-message-wrapper';
                        
                        const usernameDiv = document.createElement('div');
                        usernameDiv.className = 'message-username';
                        usernameDiv.textContent = displayedUsername;
                        messageWrapper.appendChild(usernameDiv);
                        
                        const messageDiv = document.createElement('div');
                        messageDiv.className = isSent ? 'sent-message' : 'received-message';
                        
                        let contentHtml = '';
                        if (msg.type === MessageType.IMAGE) {
                            if (msg.isNSFW) {
                                const messageId = `nsfw-${msg.id || Date.now()}`;
                                const ivAttr = msg.iv ? `data-iv='${JSON.stringify(msg.iv).replace(/'/g, "\\'")}'` : '';
                                contentHtml = `
                                    <div class="nsfw-image-wrapper" id="${messageId}">
                                        <img src="${msg.content}" alt="图片" data-original-url="${msg.content}" ${ivAttr} style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;" onclick="openImageModal('${msg.content}')">
                                        <button class="nsfw-toggle-btn" onclick="toggleNSFWImage('${messageId}')">显示NSFW内容</button>
                                    </div>
                                `;
                            } else {
                                contentHtml = `<img src="${msg.content}" alt="图片" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;" onclick="openImageModal('${msg.content}')">`;
                            }
                        } else {
                            contentHtml = this.escapeHtml(msg.content);
                        }
                        
                        messageDiv.innerHTML = `<div class="message-content">${contentHtml}</div><div class="message-time"><small>${msg.time}</small></div>`;
                        messageWrapper.appendChild(messageDiv);
                        
                        messagesArea.appendChild(messageWrapper);
                    }
                });
                messagesArea.scrollTop = messagesArea.scrollHeight;
            }
        }
    },
    
    escapeHtml: function(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },
    
    // ========== 消息历史和同步处理 ==========
    
    // 处理服务器返回的历史消息响应
    handleHistoryResponse: function(message) {
        this.log('debug', '收到服务器的历史消息响应');
        
        try {
            let roomName = message.roomName || message.to;
            // 服务器返回的历史消息在content字段中
            const messages = message.content ? JSON.parse(message.content) : [];
            
            if (!roomName || !messages || messages.length === 0) {
                this.log('info', '历史消息响应为空');
                this.isSyncing[roomName] = false;
                return;
            }
            
            // 检查是否是私聊消息的历史响应
            // 私聊消息的历史响应中，message.to 是用户名，而不是房间名
            if (messages.length > 0) {
                const firstMessage = messages[0];
                // 判断是否是私聊消息：检查消息的 from 和 to 是否都是用户名（非房间名）
                if (firstMessage.from && firstMessage.to && firstMessage.from !== firstMessage.to) {
                    // 检查接收者是否为当前用户
                    const username = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
                    if (firstMessage.to === username) {
                        // 私聊消息，来自 firstMessage.from，使用虚拟房间名
                        roomName = `好友${firstMessage.from}`;
                    } else if (firstMessage.from === username) {
                        // 私聊消息，发送给 firstMessage.to，使用虚拟房间名
                        roomName = `好友${firstMessage.to}`;
                    }
                }
            }
            
            this.handleHistoryMessages(messages, roomName);
        } catch (error) {
            this.log('error', `处理历史消息响应失败: ${error.message}`);
            this.isSyncing[message.to] = false;
        }
    },
    
    // 处理服务器返回的最新时间戳
    handleLatestTimestamp: function(message) {
        this.log('debug', '收到服务器的最新时间戳响应');
        
        try {
            const roomName = message.roomName || message.to;
            const serverTimestamp = message.timestamp;
            
            if (!roomName || !serverTimestamp) {
                this.log('info', '最新时间戳响应缺少必要参数');
                return;
            }
            
            // 获取本地最后一条消息的时间戳
            const localLastTimestamp = this.lastSyncTime[roomName];
            
            // 如果本地没有消息或者服务器时间比本地新，请求历史消息
            if (!localLastTimestamp || new Date(serverTimestamp) > new Date(localLastTimestamp)) {
                this.requestMessageHistory(roomName, localLastTimestamp || 0);
            } else {
                this.log('info', `${roomName}房间的消息已经是最新的`);
                this.isSyncing[roomName] = false;
            }
        } catch (error) {
            this.log('error', `处理最新时间戳响应失败: ${error.message}`);
            this.isSyncing[roomName] = false;
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
        const messageElement = document.getElementById('message');
        if (messageElement) {
            messageElement.textContent = 'Authentication failed: ' + message.content;
        }
    },
    
    // UUID Authentication handlers
    handleUUIDAuthSuccess: function(message) {
        this.isAuthenticated = true;
        this.username = sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
        
        const currentUserSpan = document.getElementById('current-user');
        if (currentUserSpan) {
            currentUserSpan.textContent = this.username;
        }
        
        this.sendMessage(MessageType.LIST_ROOMS, 'server', '');
        this.startCleanupInterval();
        
        // 初始化消息同步系统
        this.initMessageSync();
        
        const currentRoomName = document.getElementById('current-room-name')?.textContent || 'system';
        
        // 请求当前房间的历史消息（不传入时间戳，让方法自己从IndexedDB获取本地最晚消息时间戳）
        this.requestMessageHistory(currentRoomName);
        
        // 请求私聊用户列表
        this.requestPrivateUsers();
        
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
    
    handleTokenResponse: function(message) {
        this.log('debug', '收到上传 token 响应');
        
        try {
            const tokenInfo = message.content;
            const parts = tokenInfo.split('|');
            
            if (parts.length !== 2) {
                this.log('error', 'token 响应格式错误');
                return;
            }
            
            this.uploadToken = parts[0];
            this.zfileServerUrl = parts[1];
            
            this.log('info', '上传 token 获取成功');
            
            if (this.pendingImageUpload) {
                this.uploadImageToZfile(this.pendingImageUpload);
            }
        } catch (error) {
            this.log('error', `处理 token 响应失败: ${error.message}`);
        }
    },
    
    handleImageMessage: function(message) {
        this.log('debug', '收到图片消息');
        
        try {
            const imageUrl = message.content;
            const from = message.from;
            const to = message.to;
            const time = message.time;
            const isNSFW = message.isNSFW || false;
            const iv = message.iv || null;
            
            const messageObj = {
                type: MessageType.IMAGE,
                content: imageUrl,
                from: from,
                to: to,
                time: time,
                isSystem: false,
                isNSFW: isNSFW,
                iv: iv
            };
            
            let targetRoom = to === this.username ? from : to;
            
            // 检查是否是私聊消息
            const username = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
            if (from && to && from !== to) {
                // 判断接收者是否为房间名：检查是否存在于已知房间列表中
                let isRecipientRoom = false;
                for (const room of this.rooms) {
                    if (room.name === to) {
                        isRecipientRoom = true;
                        break;
                    }
                }
                
                // 如果接收者不是房间名，则视为私人消息
                if (!isRecipientRoom) {
                    // 对于私人消息，使用虚拟房间名
                    targetRoom = `好友${from}`;
                }
            }
            
            if (!this.messages[targetRoom]) {
                this.messages[targetRoom] = [];
            }
            
            this.messages[targetRoom].push(messageObj);
            
            if (this.messageStorage) {
                this.saveMessageToLocal(targetRoom, messageObj);
            }
            
            if (this.currentRoom === targetRoom) {
                this.updateMessagesArea(targetRoom);
            }
        } catch (error) {
            this.log('error', `处理图片消息失败: ${error.message}`);
        }
    },
    
    requestUploadToken: function() {
        if (!this.isConnected || !this.isAuthenticated) {
            this.log('error', '未连接或未认证，无法请求上传 token');
            return;
        }
        
        this.log('info', '请求上传 token');
        this.sendMessage(MessageType.REQUEST_TOKEN, 'server', '');
    },
    
    uploadImageToZfile: async function(file) {
        this.log('info', '开始上传图片到 zfile');
        
        let uploadFile = file;
        
        // 如果是NSFW图片，先加密
        if (this.pendingImageNSFW) {
            this.log('info', '加密NSFW图片');
            try {
                uploadFile = await this.encryptFile(file);
            } catch (error) {
                this.log('error', `加密图片失败: ${error.message}`);
                this.pendingImageUpload = null;
                this.pendingImageNSFW = false;
                return;
            }
        }
        
        // 生成日期路径 (YYYY/MM/DD)
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const datePath = `${year}/${month}/${day}`;
        
        // 根据聊天类型构建基础路径
        let basePath = '';
        if (this.currentRoomType === 'PUBLIC') {
            basePath = `/images/group/public/${this.currentRoom}`;
        } else if (this.currentRoomType === 'PRIVATE') {
            basePath = `/images/group/private/${this.currentRoom}`;
        } else if (this.isInPrivateChat && this.privateChatRecipient) {
            basePath = `/images/private/${this.privateChatRecipient}`;
        } else {
            basePath = '/images';
        }
        
        // 完整的上传路径
        const uploadPath = `${basePath}/${datePath}`;
        
        // 第一步：创建上传任务
        const createUploadUrl = `${this.zfileServerUrl}/api/file/operator/upload/file`;
        
        fetch(createUploadUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Zfile-Token': this.uploadToken,
                'Axios-Request': 'true',
                'Axios-From': this.zfileServerUrl
            },
            body: JSON.stringify({
                storageKey: 'chatroom-files',
                path: uploadPath,
                name: uploadFile.name,
                size: uploadFile.size,
                password: ''
            })
        })
        .then(response => response.json())
        .then(data => {
            this.log('debug', '创建上传任务响应:', data);
            
            if (data && data.code === '0' && data.data) {
                let uploadUrl = data.data;
                // 检查上传URL是否使用了正确的端口（8081）
                if (uploadUrl.includes('localhost:8080')) {
                    uploadUrl = uploadUrl.replace('localhost:8080', 'localhost:8081');
                    this.log('info', '修正上传URL端口:', uploadUrl);
                }
                this.log('info', '上传URL获取成功:', uploadUrl);
                
                // 第二步：实际上传文件
                const formData = new FormData();
                formData.append('file', uploadFile);
                
                return fetch(uploadUrl, {
                    method: 'PUT',
                    headers: {
                        'Zfile-Token': this.uploadToken,
                        'Axios-Request': 'true',
                        'Axios-From': this.zfileServerUrl
                    },
                    body: formData
                });
            } else {
                throw new Error('创建上传任务失败');
            }
        })
        .then(response => response.json())
        .then(data => {
            this.log('debug', '文件上传响应:', data);
            
            if (data && data.code === '0') {
                this.log('info', '图片上传成功');
                // 构建完整的图片URL
                const imageUrl = `${this.zfileServerUrl}/pd/chatroom-files/chatroom${uploadPath}/${encodeURIComponent(uploadFile.name)}`;
                const iv = uploadFile.iv || null;
                this.sendImageMessage(imageUrl, iv);
            } else {
                throw new Error('文件上传失败');
            }
        })
        .catch(error => {
            this.log('error', `图片上传失败: ${error.message}`);
        })
        .finally(() => {
            this.uploadToken = null;
            this.pendingImageUpload = null;
        });
    },
    
    sendImageMessage: function(imageUrl, iv = null) {
        const message = {
            type: MessageType.IMAGE,
            from: this.username,
            to: this.currentRoom,
            content: imageUrl,
            time: getLocalTime(),
            isNSFW: this.pendingImageNSFW || false,
            iv: iv
        };
        
        this.pendingImageNSFW = false;
        this.sendMessage(message);
        this.log('info', '发送图片消息');
    },
    
    handleImageUpload: function(file) {
        if (!file || !file.type.startsWith('image/')) {
            this.log('error', '请选择有效的图片文件');
            return;
        }
        
        const maxSize = 10 * 1024 * 1024;
        if (file.size > maxSize) {
            this.log('error', '图片大小不能超过 10MB');
            return;
        }
        
        this.pendingImageUpload = file;
        this.showImageUploadPreview(file);
    },
    
    showImageUploadPreview: function(file) {
        const reader = new FileReader();
        reader.onload = function(e) {
            document.getElementById('upload-preview-image').src = e.target.result;
            document.getElementById('nsfw-checkbox').checked = false;
            document.getElementById('image-upload-modal').style.display = 'block';
        };
        reader.readAsDataURL(file);
    },
    
    confirmImageUpload: function() {
        const isNSFW = document.getElementById('nsfw-checkbox').checked;
        this.pendingImageNSFW = isNSFW;
        document.getElementById('image-upload-modal').style.display = 'none';
        this.requestUploadToken();
    },
    
    encryptFile: async function(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            
            reader.onload = async function(e) {
                try {
                    const fileData = e.target.result;
                    
                    const encrypted = await encryptData(fileData);
                    
                    const encryptedData = new Uint8Array(encrypted.data);
                    
                    const encryptedFile = new File(
                        [encryptedData],
                        file.name + '.encrypted',
                        { type: 'application/octet-stream' }
                    );
                    
                    encryptedFile.iv = encrypted.iv;
                    encryptedFile.originalName = file.name;
                    encryptedFile.originalType = file.type;
                    
                    resolve(encryptedFile);
                } catch (error) {
                    reject(error);
                }
            };
            
            reader.onerror = function() {
                reject(new Error('读取文件失败'));
            };
            
            reader.readAsDataURL(file);
        });
    },
    
    decryptImage: async function(imageUrl, iv) {
        try {
            const response = await fetch(imageUrl);
            const encryptedData = await response.arrayBuffer();
            
            const ivArray = JSON.parse(iv);
            
            const decrypted = await decryptData(new Uint8Array(encryptedData), ivArray);
            
            const blob = this.dataURLtoBlob(decrypted);
            return URL.createObjectURL(blob);
        } catch (error) {
            throw new Error(`解密图片失败: ${error.message}`);
        }
    },
    
    dataURLtoBlob: function(dataURL) {
        const parts = dataURL.split(',');
        const mime = parts[0].match(/:(.*?);/)[1];
        const bstr = atob(parts[1]);
        let n = bstr.length;
        const u8arr = new Uint8Array(n);
        while (n--) {
            u8arr[n] = bstr.charCodeAt(n);
        }
        return new Blob([u8arr], { type: mime });
    },
    
    cancelImageUpload: function() {
        this.pendingImageUpload = null;
        this.pendingImageNSFW = false;
        document.getElementById('image-upload-modal').style.display = 'none';
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
        let roomName = message.to || 'system';
        let content = message.content;
        
        // 首先检查是否是带房间信息的私人消息（包含[room:]前缀）
        if (content.startsWith('[room:')) {
            // 这是一个私人消息，但是我们已经在服务器端处理过了
            // 客户端只需要按照正常消息处理
            console.log('收到带房间信息的私人消息:', message);
        } 
        // 然后检查是否是普通的私人消息（接收者是用户名而不是房间名）
        else if (message.from && message.to && message.from !== message.to) {
            // 判断接收者是否为房间名：检查是否存在于已知房间列表中
            let isRecipientRoom = false;
            for (const room of this.rooms) {
                if (room.name === message.to) {
                    isRecipientRoom = true;
                    break;
                }
            }
            
            // 如果接收者不是房间名，则视为私人消息
            if (!isRecipientRoom) {
                // 对于私人消息，创建或使用虚拟房间
                const privateRoomName = `好友${message.from}`;
                roomName = privateRoomName;
            }
        }
        
        // 如果消息是自己发送的，跳过添加，因为sendMessage函数已经添加了
        const username = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
        if (message.from === username) {
            console.log('跳过自己发送的消息，因为sendMessage函数已经添加');
            return;
        }
        
        if (!this.messages[roomName]) {
            this.messages[roomName] = [];
        }
        
        const localMessage = {
            content: content,
            from: message.from,
            to: message.to,
            time: message.time,
            isSystem: false,
            id: message.id || this.generateMessageId('TEXT', roomName)
        };
        
        // 去重检查 - 使用消息ID优先，然后使用内容+时间+发送者
        const isDuplicate = this.messages[roomName].some(m => 
            (localMessage.id && m.id === localMessage.id) || 
            (!localMessage.id && m.content === content && 
             m.from === message.from && 
             m.time === message.time)
        );
        
        if (!isDuplicate) {
            this.messages[roomName].push(localMessage);
            
            // 保存消息到本地存储
            if (this.messageStorage) {
                this.saveMessageToLocal(roomName, localMessage);
            }
            
            // 更新当前窗口UI
            if (this.currentRoom === roomName) {
                this.updateMessagesArea(roomName);
            }
            
            // 广播到其他窗口
            this.broadcastToWindows('NEW_MESSAGE', {
                roomName: roomName,
                message: localMessage
            }, roomName);
        }
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
        
        // Check if this is a room users list message (JSON format)
        if (message.content.startsWith('{') && message.content.includes('"users":[')) {
            try {
                const usersData = JSON.parse(message.content);
                if (usersData.users && Array.isArray(usersData.users)) {
                    // 获取当前房间名称（从h3元素中获取）
                    const currentRoomName = document.getElementById('current-room-name')?.textContent || 'system';
                    this.displayRoomUsers(usersData.users, currentRoomName);
                    return;
                }
            } catch (e) {
                console.error('Failed to parse room users JSON:', e);
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
            this.broadcastToWindows('ROOM_LIST_UPDATE', {
                rooms: this.rooms
            });
        }
    },
    
    // Display room users list
    displayRoomUsers: function(users, roomName) {
        // Create or update users list panel
        let usersPanel = document.getElementById('room-users-panel');
        
        // If panel doesn't exist, create it
        if (!usersPanel) {
            usersPanel = document.createElement('div');
            usersPanel.id = 'room-users-panel';
            usersPanel.className = 'users-panel';
            usersPanel.style.display = 'block'; // Show panel by default when created
            
            const panelHeader = document.createElement('div');
            panelHeader.className = 'panel-header';
            panelHeader.innerHTML = `<h3>${roomName} - Members</h3>`;
            usersPanel.appendChild(panelHeader);
            
            const usersList = document.createElement('div');
            usersList.id = 'room-users-list';
            usersList.className = 'users-list';
            usersPanel.appendChild(usersList);
            
            // Add to the chat content container
            const chatContent = document.querySelector('.chat-content');
            if (chatContent) {
                chatContent.appendChild(usersPanel);
            }
        } else {
            // Update panel header with current room name
            const panelHeader = usersPanel.querySelector('.panel-header');
            if (panelHeader) {
                panelHeader.innerHTML = `<h3>${roomName} - Members</h3>`;
            }
        }
        
        // Update users list
        const usersList = document.getElementById('room-users-list');
        if (usersList) {
            usersList.innerHTML = '';
            
            // Get current logged-in username
            const currentUsername = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
            
            users.forEach(user => {
                const isCurrentUser = user.username === currentUsername;
                const userItem = document.createElement('div');
                userItem.className = `user-item ${user.isOnline ? 'user-online' : 'user-offline'} ${isCurrentUser ? 'user-self' : ''}`;
                
                // Add visual indicator for current user
                const usernameDisplay = isCurrentUser ? `${user.username} (我)` : user.username;
                
                userItem.innerHTML = `
                    <div class="user-status-indicator"></div>
                    <div class="user-name">${usernameDisplay}</div>
                `;
                
                // Only add click event if not the current user
                if (!isCurrentUser) {
                    userItem.addEventListener('click', () => {
                        // Switch to private chat with this user
                        this.switchToPrivateChat(user.username);
                    });
                }
                
                usersList.appendChild(userItem);
            });
        }
    },
    
    handleJoinMessage: function(message) {
        // Join message is room-specific
        // Ensure we have valid room name to display the notification
        const roomName = message.to && message.to.trim() !== '' ? message.to : this.currentRoom;
        if (roomName) {
            this.showMessage(`[System] ${message.from} joined ${roomName}`, true, roomName);
        }
    },
    
    handleLeaveMessage: function(message) {
        // Leave message is room-specific
        this.showMessage(`[System] ${message.from} left ${message.to}`, true, message.to);
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
        // Exit private chat mode if currently in private chat
        if (this.isInPrivateChat) {
            this.isInPrivateChat = false;
            this.privateChatRecipient = null;
            this.previousRoom = null;
            this.previousRoomType = null;
            
            // Hide return to room button
            const returnButton = document.getElementById('return-to-room-btn');
            if (returnButton) {
                returnButton.style.display = 'none';
            }
        }
        
        this.currentRoom = roomName;
        this.currentRoomType = roomType;
        document.getElementById('current-room-name').textContent = roomName;
        this.updateRoomsList();
        
        // Update message input placeholder to indicate room chat
        const messageInput = document.getElementById('message-input');
        if (messageInput) {
            messageInput.placeholder = 'Type your message...';
        }
        
        // Send JOIN message to server to switch room
        this.sendMessage(MessageType.JOIN, roomName, '');
        
        // 加载该房间的本地消息
        this.loadLocalMessages(roomName);
        
        // 检查本地消息是否最新
        this.checkAndSyncMessages(roomName);
        
        // Update messages area with stored messages for this room
        this.updateMessagesArea(roomName);
        
        // Refresh users list if users panel is visible
        const usersPanel = document.getElementById('room-users-panel');
        if (usersPanel && usersPanel.style.display !== 'none') {
            this.sendMessage(MessageType.LIST_ROOM_USERS, roomName, '');
        }
        
        // Update Members button availability based on room type and name
        this.updateMembersButtonAvailability(roomName, roomType);
    },
    
    // Update Members button availability based on room type and name
    updateMembersButtonAvailability: function(roomName, roomType) {
        const membersButton = document.getElementById('private-msg-btn');
        if (!membersButton) return;
        
        // Disable button in system room or private rooms
        if (roomName === 'system' || roomType === 'PRIVATE') {
            membersButton.disabled = true;
            membersButton.classList.add('disabled');
            
            // Hide users panel if it's visible
            const usersPanel = document.getElementById('room-users-panel');
            if (usersPanel && usersPanel.style.display !== 'none') {
                usersPanel.style.display = 'none';
            }
        } else {
            // Enable button in public rooms (excluding system)
            membersButton.disabled = false;
            membersButton.classList.remove('disabled');
        }
    },
    
    // Switch to private chat with a specific user
    switchToPrivateChat: function(username) {
        if (!username || username === this.username) {
            return;
        }
        
        // Save current room information for potential return
        this.previousRoom = this.currentRoom;
        this.previousRoomType = this.currentRoomType;
        
        // Switch to private chat mode
        this.isInPrivateChat = true;
        this.privateChatRecipient = username;
        
        // Update current room to a virtual room named after the recipient
        this.currentRoom = `好友${username}`;
        this.currentRoomType = 'PRIVATE'; // Mark as private chat
        
        // Update h3 title to show private chat recipient
        const currentRoomNameElement = document.getElementById('current-room-name');
        if (currentRoomNameElement) {
            currentRoomNameElement.textContent = `与${username}聊天`;
        }
        
        // Update rooms list to reflect the new current room
        this.updateRoomsList();
        
        // Hide users panel after selecting a user
        const usersPanel = document.getElementById('room-users-panel');
        if (usersPanel) {
            usersPanel.style.display = 'none';
        }
        
        // Update message input placeholder to indicate private chat
        const messageInput = document.getElementById('message-input');
        if (messageInput) {
            messageInput.placeholder = `Type message to ${username}...`;
        }
        
        // Update members button availability - disable in private chat
        const membersButton = document.getElementById('private-msg-btn');
        if (membersButton) {
            membersButton.disabled = true;
            membersButton.classList.add('disabled');
        }
        
        // Add return button to room controls if not already present
        const roomControls = document.querySelector('.room-controls');
        if (roomControls) {
            // Check if return button already exists
            let returnButton = document.getElementById('return-to-room-btn');
            if (!returnButton) {
                returnButton = document.createElement('button');
                returnButton.id = 'return-to-room-btn';
                returnButton.textContent = 'Back to Room';
                returnButton.className = 'return-button';
                
                // Add click event to return to previous room
                returnButton.addEventListener('click', () => {
                    this.returnToPreviousRoom();
                });
                
                roomControls.appendChild(returnButton);
            } else {
                // Show the button if it already exists but is hidden
                returnButton.style.display = 'inline-block';
            }
        }
        
        // 加载历史私聊消息
        this.loadLocalMessages(this.currentRoom);
        
        // 请求服务器拉取私聊历史消息（包括离线期间的消息，不传入时间戳，让方法自己从IndexedDB获取本地最晚消息时间戳）
        this.requestMessageHistory(username);
        
        // Update messages area with stored private messages for this user
        this.updateMessagesArea(this.currentRoom);
    },
    
    // Return to previous room from private chat
    returnToPreviousRoom: function() {
        if (this.isInPrivateChat && this.previousRoom) {
            // Restore previous room
            this.currentRoom = this.previousRoom;
            this.currentRoomType = this.previousRoomType;
            
            // Update h3 title to show previous room name
            const currentRoomNameElement = document.getElementById('current-room-name');
            if (currentRoomNameElement) {
                currentRoomNameElement.textContent = this.previousRoom;
            }
            
            // Exit private chat mode
        this.isInPrivateChat = false;
        this.privateChatRecipient = null;
        this.previousRoom = null;
        this.previousRoomType = null;
        
        // Hide return to room button
        const returnButton = document.getElementById('return-to-room-btn');
        if (returnButton) {
            returnButton.style.display = 'none';
        }
            
            // Update message input placeholder to indicate room chat
            const messageInput = document.getElementById('message-input');
            if (messageInput) {
                messageInput.placeholder = 'Type your message...';
            }
            
            // Update members button availability based on previous room type
            this.updateMembersButtonAvailability(this.currentRoom, this.currentRoomType);
            
            // Update rooms list
            this.updateRoomsList();
            
            // Update messages area with previous room messages
            this.updateMessagesArea(this.currentRoom);
        }
    },
    
    // Send private message to a specific user
    sendPrivateMessage: function(to, content) {
        if (!to || !content) {
            this.showMessage('Please enter a username and message', true);
            return;
        }
        
        // For private messages, always include room info in the format [room:房间名]消息内容
        const roomName = this.previousRoom || 'unknown';
        const contentWithRoom = `[room:${roomName}]${content}`;
        
        // Send private message through WebSocket
        this.sendMessage(MessageType.TEXT, to, contentWithRoom);
        
        // Store private message in a virtual room named after the recipient
        const privateRoomName = `好友${to}`;
        if (!this.messages[privateRoomName]) {
            this.messages[privateRoomName] = [];
        }
        
        const privateMessage = {
            content: content,
            from: this.username,
            to: to,
            time: getLocalTime(),
            isSystem: false,
            isPrivate: true
        };
        
        this.messages[privateRoomName].push(privateMessage);
        
        // 保存私聊消息到本地存储
        if (this.messageStorage) {
            // 确保消息有唯一ID
            if (!privateMessage.id) {
                privateMessage.id = this.generateMessageId();
            }
            this.saveMessageToLocal(privateRoomName, privateMessage);
        }
        
        // 同步私聊消息到其他窗口
        this.broadcastToWindows('NEW_MESSAGE', {
            roomName: privateRoomName,
            message: privateMessage
        }, privateRoomName);
        
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

    // Open a room in a new window - simplified version inspired by test_sync.html
    openRoomInNewWindow: function(roomName, roomType) {
        this.log('info', `在新窗口打开房间: ${roomName} (${roomType})`);
        
        if (this.childWindows[roomName] && !this.childWindows[roomName].closed) {
            this.childWindows[roomName].focus();
            this.log('debug', `房间 ${roomName} 的窗口已存在，聚焦窗口`);
            return;
        }
        
        if (!this.messages[roomName]) {
            this.messages[roomName] = [];
        }
        
        const newWindow = window.open(
            'room.jsp?room=' + encodeURIComponent(roomName) + '&type=' + encodeURIComponent(roomType),
            'room_' + roomName,
            'width=800,height=600'
        );
        
        if (newWindow) {
            this.childWindows[roomName] = newWindow;
            
            newWindow.addEventListener('beforeunload', () => {
                delete this.childWindows[roomName];
                this.log('debug', `子窗口关闭: ${roomName}`);
            });
            
            this.showMessage(`已打开房间 "${roomName}" 的新窗口`, true);
            this.log('info', `成功打开房间 ${roomName} 的新窗口`);
        } else {
            this.showMessage('无法打开新窗口，请检查浏览器弹窗设置', true);
            this.log('warn', `打开房间 ${roomName} 新窗口失败: 弹窗被阻止`);
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
    
    // Update Members button availability based on initial room
    chatClient.updateMembersButtonAvailability(chatClient.currentRoom, chatClient.currentRoomType);
    
    // Initialize message synchronization mechanism first
    chatClient.initMessageSync();
    
    // Initialize message persistence
    chatClient.initMessagePersistence();
    
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
    
    // Image upload button functionality
    document.getElementById('image-btn').addEventListener('click', function() {
        const imageInput = document.getElementById('image-input');
        imageInput.click();
    });
    
    document.getElementById('image-input').addEventListener('change', function(e) {
        if (e.target.files && e.target.files.length > 0) {
            const file = e.target.files[0];
            chatClient.handleImageUpload(file);
            e.target.value = '';
        }
    });
    
    // Room members button functionality
    document.getElementById('private-msg-btn').addEventListener('click', function() {
        // Check if button is disabled
        if (this.disabled) {
            return;
        }
        
        const usersPanel = document.getElementById('room-users-panel');
        
        if (usersPanel) {
            // If panel exists, toggle visibility
            if (usersPanel.style.display === 'none' || usersPanel.style.display === '') {
                // If hidden or not set, show it
                usersPanel.style.display = 'block';
                // Refresh the user list
                chatClient.sendMessage(MessageType.LIST_ROOM_USERS, chatClient.currentRoom, '');
            } else {
                // If visible, hide it
                usersPanel.style.display = 'none';
            }
        } else {
            // If panel doesn't exist, create it and request users list
            chatClient.sendMessage(MessageType.LIST_ROOM_USERS, chatClient.currentRoom, '');
        }
    });
    
    function sendMessage() {
        const input = document.getElementById('message-input');
        const message = input.value.trim();
        if (message) {
            let recipient = chatClient.currentRoom;
            let content = message;
            
            // If in private chat, use the private message method
            if (chatClient.isInPrivateChat && chatClient.privateChatRecipient) {
                // 使用sendPrivateMessage方法发送私聊消息，确保正确存储和同步
                chatClient.sendPrivateMessage(chatClient.privateChatRecipient, message);
            } else {
                // 普通房间消息发送
                chatClient.sendMessage(MessageType.TEXT, recipient, content);
            }
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
                // Refresh users list if users panel is visible
                const usersPanel = document.getElementById('room-users-panel');
                if (usersPanel && usersPanel.style.display !== 'none') {
                    chatClient.sendMessage(MessageType.LIST_ROOM_USERS, roomName, '');
                }
                
                // 请求加入的房间的历史消息（不传入时间戳，让方法自己从IndexedDB获取本地最晚消息时间戳）
                chatClient.requestMessageHistory(roomName);
            }, 100);
        }
    });
    
    // Image modal functionality
    const imageModal = document.getElementById('image-modal');
    const modalImage = document.getElementById('modal-image');
    const imageModalCloseBtn = imageModal.querySelector('.close');
    
    // Image upload modal functionality
    const imageUploadModal = document.getElementById('image-upload-modal');
    const imageUploadCloseBtn = imageUploadModal.querySelector('.close');
    
    // Close image upload modal when close button is clicked
    imageUploadCloseBtn.addEventListener('click', function() {
        chatClient.cancelImageUpload();
    });
    
    // Cancel upload button
    document.getElementById('cancel-upload-btn').addEventListener('click', function() {
        chatClient.cancelImageUpload();
    });
    
    // Confirm upload button
    document.getElementById('confirm-upload-btn').addEventListener('click', function() {
        chatClient.confirmImageUpload();
    });
    
    // NSFW checkbox toggle
    document.getElementById('nsfw-checkbox').addEventListener('change', function() {
        const warningDiv = document.getElementById('nsfw-warning');
        if (this.checked) {
            warningDiv.style.display = 'flex';
        } else {
            warningDiv.style.display = 'none';
        }
    });
    
    // Function to open image modal
    window.openImageModal = function(imageSrc) {
        modalImage.src = imageSrc;
        imageModal.style.display = 'block';
    };
    
    // Function to toggle NSFW image visibility
    window.toggleNSFWImage = async function(wrapperId) {
        const wrapper = document.getElementById(wrapperId);
        if (!wrapper) return;
        
        const img = wrapper.querySelector('img');
        const btn = wrapper.querySelector('.nsfw-toggle-btn');
        
        if (img.classList.contains('showing')) {
            img.classList.remove('showing');
            btn.textContent = '显示NSFW内容';
            btn.classList.remove('hidden');
        } else {
            const imageUrl = img.getAttribute('data-original-url') || img.src;
            const iv = img.getAttribute('data-iv');
            
            if (iv) {
                btn.textContent = '解密中...';
                btn.disabled = true;
                
                try {
                    const decryptedUrl = await chatClient.decryptImage(imageUrl, iv);
                    img.src = decryptedUrl;
                    img.classList.add('showing');
                    btn.textContent = '隐藏NSFW内容';
                    setTimeout(() => {
                        btn.classList.add('hidden');
                    }, 2000);
                } catch (error) {
                    console.error('解密图片失败:', error);
                    btn.textContent = '解密失败';
                    setTimeout(() => {
                        btn.textContent = '显示NSFW内容';
                    }, 2000);
                } finally {
                    btn.disabled = false;
                }
            } else {
                img.classList.add('showing');
                btn.textContent = '隐藏NSFW内容';
                setTimeout(() => {
                    btn.classList.add('hidden');
                }, 2000);
            }
        }
    };
    
    // Close image modal when close button is clicked
    imageModalCloseBtn.addEventListener('click', function() {
        imageModal.style.display = 'none';
        modalImage.src = '';
    });
    
    // Close image modal when clicking outside
    window.addEventListener('click', function(e) {
        if (e.target === imageModal) {
            imageModal.style.display = 'none';
            modalImage.src = '';
        }
    });
    
    // Close image modal with Escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && imageModal.style.display === 'block') {
            imageModal.style.display = 'none';
            modalImage.src = '';
        }
    });
    
    document.getElementById('leave-room-btn').addEventListener('click', function() {
        chatClient.sendMessage(MessageType.LEAVE, chatClient.currentRoom, chatClient.username + ' left the room');
        chatClient.currentRoom = 'system';
        chatClient.currentRoomType = 'PUBLIC';
        document.getElementById('current-room-name').textContent = 'System Room';
        
        // Refresh room list to reflect the leave
        chatClient.sendMessage(MessageType.LIST_ROOMS, 'server', '');
        
        // Refresh users list for system room if users panel is visible
        const usersPanel = document.getElementById('room-users-panel');
        if (usersPanel && usersPanel.style.display !== 'none') {
            chatClient.sendMessage(MessageType.LIST_ROOM_USERS, 'system', '');
        }
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
        
        // Refresh users list for system room if users panel is visible
        const usersPanel = document.getElementById('room-users-panel');
        if (usersPanel && usersPanel.style.display !== 'none') {
            chatClient.sendMessage(MessageType.LIST_ROOM_USERS, 'system', '');
        }
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

// Expose chatClient to global scope for child windows
window.chatClient = chatClient;