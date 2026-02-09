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
    FILE: 'FILE',
    PRIVATE_CHAT: 'PRIVATE_CHAT',
    REQUEST_PRIVATE_USERS: 'REQUEST_PRIVATE_USERS',
    PRIVATE_USERS_RESPONSE: 'PRIVATE_USERS_RESPONSE',
    FRIEND_REQUEST: 'FRIEND_REQUEST',
    FRIEND_REQUEST_RESPONSE: 'FRIEND_REQUEST_RESPONSE',
    FRIEND_LIST: 'FRIEND_LIST',
    REQUEST_FRIEND_LIST: 'REQUEST_FRIEND_LIST',
    SEARCH_USERS: 'SEARCH_USERS',
    USERS_SEARCH_RESULT: 'USERS_SEARCH_RESULT',
    REQUEST_ALL_FRIEND_REQUESTS: 'REQUEST_ALL_FRIEND_REQUESTS',
    ALL_FRIEND_REQUESTS: 'ALL_FRIEND_REQUESTS',
    SEARCH_ROOMS: 'SEARCH_ROOMS',
    ROOMS_SEARCH_RESULT: 'ROOMS_SEARCH_RESULT',
    REQUEST_ROOM_JOIN: 'REQUEST_ROOM_JOIN',
    ROOM_JOIN_REQUEST: 'ROOM_JOIN_REQUEST',
    ROOM_JOIN_RESPONSE: 'ROOM_JOIN_RESPONSE',
    SET_ROOM_ADMIN: 'SET_ROOM_ADMIN',
    REMOVE_ROOM_ADMIN: 'REMOVE_ROOM_ADMIN',
    SERVICE_CONFIG: 'SERVICE_CONFIG',
    REQUEST_USER_STATS: 'REQUEST_USER_STATS',
    USER_STATS_RESPONSE: 'USER_STATS_RESPONSE'
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
    let dataBytes;
    
    if (data instanceof ArrayBuffer || data instanceof Uint8Array) {
        dataBytes = data instanceof ArrayBuffer ? new Uint8Array(data) : data;
    } else if (typeof data === 'string') {
        const encoder = new TextEncoder();
        dataBytes = encoder.encode(data);
    } else {
        throw new Error('不支持的数据类型');
    }
    
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
    
    return decrypted;
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
    isInPrivateChat: false, // 是否在私聊模式
    privateChatRecipient: null, // 私聊接收者
    isTemporaryChat: true, // 是否为临时聊天（默认true，后续好友聊天设为false）
    isFriendChat: false, // 是否为好友聊天（默认false）
    friends: [], // 好友列表
    receivedFriendRequests: [], // 收到的好友请求
    sentFriendRequests: [], // 发送的好友请求
    receivedRoomRequests: [], // 收到的房间加入请求
    sentRoomRequests: [], // 发送的房间加入请求
    pendingImageUpload: null, // 待上传的图片文件
    pendingImageNSFW: false, // 待上传图片是否为NSFW
    pendingFileUpload: null, // 待上传的文件
    pendingFileType: null, // 待上传文件的类型
    uploadToken: null, // 上传token
    
    // ========== 新增：服务配置相关属性 ==========
    serviceConfig: {
        zfileServerUrl: null
    }, // 服务配置，从服务器动态获取
    
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
                        type: msg.type || 'TEXT',
                        isNSFW: msg.isNSFW || false,
                        iv: msg.iv || null
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
                                const internalMsg = {
                                    content: msg.content,
                                    from: msg.from,
                                    time: msg.createTime,
                                    isSystem: msg.isSystem || false,
                                    id: msg.id,
                                    type: msg.type || 'TEXT',
                                    isNSFW: msg.isNSFW || false,
                                    iv: msg.iv || null
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
            isNSFW: message.isNSFW || false,
            iv: message.iv || null,
            id: message.id || this.generateMessageId(message.type || 'TEXT', roomName)
        };
        
        console.log('Storage message to save:', JSON.stringify(storageMsg, null, 2));
        console.log('Storage message ID:', storageMsg.id, 'Type:', typeof storageMsg.id);
        
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
    
    // 请求用户统计数据
    requestUserStats: function() {
        if (!this.isConnected) return;
        
        this.log('info', '向服务器请求用户统计数据');
        
        const requestMsg = {
            type: MessageType.REQUEST_USER_STATS,
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
    
    // 处理用户统计数据响应
    handleUserStatsResponse: function(message) {
        this.log('info', '收到服务器返回的用户统计数据');
        
        try {
            const stats = message.content ? JSON.parse(message.content) : {};
            
            this.log('info', `用户统计数据: 消息数=${stats.messageCount}, 房间数=${stats.roomCount}, 加入时间=${stats.joinTime}`);
            
            // 更新UI显示统计数据
            this.updateUserStats(stats);
        } catch (error) {
            this.log('error', `处理用户统计数据响应失败: ${error.message}`);
        }
    },
    
    // 处理服务配置
    handleServiceConfig: function(message) {
        this.log('info', '收到服务器服务配置');
        
        try {
            const config = message.content ? JSON.parse(message.content) : {};
            
            if (config.zfileServerUrl) {
                this.serviceConfig.zfileServerUrl = config.zfileServerUrl;
                this.log('info', `ZFile服务器URL: ${config.zfileServerUrl}`);
            }
            
            this.log('info', '服务配置已更新');
            
            // 配置更新后，重新渲染当前房间的消息
            if (this.currentRoom) {
                this.log('debug', '配置已更新，重新渲染当前房间的消息');
                this.updateMessagesArea(this.currentRoom);
            }
        } catch (error) {
            this.log('error', `处理服务配置失败: ${error.message}`);
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
                id: msg.id || this.generateMessageId(msg.type || 'TEXT', roomName),
                type: msg.type || 'TEXT',
                isNSFW: msg.isNSFW || false,
                iv: msg.iv || null
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
    connectToServer: function(ip, wsPort, wsProtocol) {
        if (window.opener) {
            // This is a child window, don't connect directly to server
            this.log('debug', 'Child window detected, not connecting to server directly');
            return;
        }
        
        this.serverIp = ip;
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
                    window.location.href = `login.jsp?serverIp=${encodeURIComponent(ip)}&wsPort=${encodeURIComponent(wsPort)}`;
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
        if (!this.serverIp || !this.wsPort) {
            // Try to get from sessionStorage first, then localStorage
            this.serverIp = sessionStorage.getItem('serverIp') || localStorage.getItem('serverIp');
            this.wsPort = sessionStorage.getItem('wsPort') || localStorage.getItem('wsPort');
            
            // 自动检测WebSocket协议：HTTPS页面使用wss://，HTTP页面使用ws://
            this.wsProtocol = sessionStorage.getItem('wsProtocol') || localStorage.getItem('wsProtocol') || (window.location.protocol === 'https:' ? 'wss' : 'ws');
            
            // If no server info, redirect to connect page
            if (!this.serverIp || !this.wsPort) {
                window.location.href = 'connect.jsp';
                return;
            }
        } else {
            // 如果有服务器信息，也自动检测协议
            this.wsProtocol = (window.location.protocol === 'https:' ? 'wss' : 'ws');
        }
        
        // Connect to WebSocket server
        const wsPort = parseInt(this.wsPort);
        this.log('info', `Using saved WebSocket port: ${wsPort}`);
        
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
                
                // Mark connection message as system message (only on chat.jsp page)
                if (window.location.pathname.includes('chat.jsp')) {
                    chatClient.showMessage('Connected to chat server via WebSocket', true);
                }
                
                // Send UUID authentication on chat.jsp or user-profile.jsp page
                if (window.location.pathname.includes('chat.jsp') || window.location.pathname.includes('user-profile.jsp')) {
                    // Try to authenticate with UUID if available
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
                console.log('Parsed message:', JSON.stringify(message, null, 2));
                console.log('Message ID:', message.id, 'Type:', typeof message.id);
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
        
        // 如果是文本消息或图片消息或文件消息，立即在本地显示并同步
        if (message.type === MessageType.TEXT || message.type === MessageType.IMAGE || message.type === MessageType.FILE) {
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
                id: message.id || this.generateMessageId(message.type || 'TEXT', roomName),
                type: message.type,
                isNSFW: message.isNSFW || false,
                iv: message.iv || null
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
            case MessageType.FILE:
                this.handleFileMessage(message);
                break;
            case MessageType.PRIVATE_CHAT:
                this.handlePrivateChatMessage(message);
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
            case MessageType.FRIEND_REQUEST:
                this.handleFriendRequest(message);
                break;
            case MessageType.FRIEND_REQUEST_RESPONSE:
                this.handleFriendRequestResponse(message);
                break;
            case MessageType.FRIEND_LIST:
                this.handleFriendList(message);
                break;
            case MessageType.SEARCH_USERS:
                this.handleSearchUsersRequest(message);
                break;
            case MessageType.USERS_SEARCH_RESULT:
                this.handleUsersSearchResult(message);
                break;
            case MessageType.REQUEST_ALL_FRIEND_REQUESTS:
                this.handleRequestAllFriendRequests(message);
                break;
            case MessageType.ALL_FRIEND_REQUESTS:
                this.handleAllFriendRequests(message);
                break;
            case MessageType.ROOMS_SEARCH_RESULT:
                this.handleRoomsSearchResult(message);
                break;
            case MessageType.REQUEST_ROOM_JOIN:
                this.handleRoomJoinRequest(message);
                break;
            case MessageType.ROOM_JOIN_REQUEST:
                this.handleRoomJoinRequest(message);
                break;
            case MessageType.ROOM_JOIN_RESPONSE:
                this.handleRoomJoinResponse(message);
                break;
            case MessageType.SET_ROOM_ADMIN:
                this.handleSetRoomAdminResponse(message);
                break;
            case MessageType.REMOVE_ROOM_ADMIN:
                this.handleRemoveRoomAdminResponse(message);
                break;
            case MessageType.USER_STATS_RESPONSE:
                this.handleUserStatsResponse(message);
                break;
            case MessageType.SERVICE_CONFIG:
                this.handleServiceConfig(message);
                break;
            default:
                console.log('Unknown message type:', message.type);
        }
    },
    


    // Show message in the UI
    showMessage: function(message, isSystem = false, roomName = null) {
        // 如果在user-profile页面，不显示消息
        if (window.location.pathname.includes('user-profile.jsp')) {
            return;
        }
        
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
                messageObj.id = this.generateMessageId('SYSTEM', targetRoom);
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
                        
                        const messageHeader = document.createElement('div');
                        messageHeader.className = 'message-header';
                        
                        const avatarImg = document.createElement('img');
                        avatarImg.className = 'message-avatar';
                        avatarImg.alt = displayedUsername;
                        loadUserAvatar(avatarImg, displayedUsername);
                        messageHeader.appendChild(avatarImg);
                        
                        const usernameDiv = document.createElement('div');
                        usernameDiv.className = 'message-username';
                        usernameDiv.textContent = displayedUsername;
                        messageHeader.appendChild(usernameDiv);
                        
                        messageWrapper.appendChild(messageHeader);
                        
                        const messageDiv = document.createElement('div');
                        messageDiv.className = isSent ? 'sent-message' : 'received-message';
                        
                        let contentHtml = '';
                        if (msg.type === MessageType.IMAGE || msg.type === MessageType.PRIVATE_CHAT) {
                            const username = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
                            const isSender = msg.from === username;
                            
                            if (msg.type === MessageType.IMAGE) {
                                // 动态拼接ZFile地址
                                let imageUrl = msg.content;
                                const zfileBaseUrl = this.serviceConfig.zfileServerUrl || 'http://localhost:8081';
                                this.log('debug', `原始图片URL: ${imageUrl}, ZFile地址: ${zfileBaseUrl}`);
                                if (!imageUrl.startsWith('http://') && !imageUrl.startsWith('https://')) {
                                    // 相对路径，直接拼接
                                    imageUrl = zfileBaseUrl + imageUrl;
                                } else {
                                    // 完整URL，提取路径部分并重新拼接
                                    try {
                                        const urlObj = new URL(imageUrl);
                                        const path = urlObj.pathname + urlObj.search;
                                        imageUrl = zfileBaseUrl + path;
                                        this.log('debug', `替换后图片URL: ${imageUrl}`);
                                    } catch (error) {
                                        this.log('warn', '替换图片URL失败:', error.message);
                                    }
                                }
                                
                                if (msg.isNSFW) {
                                    const messageId = `nsfw-${msg.id || Date.now()}`;
                                    const ivAttr = msg.iv ? `data-iv='${msg.iv.replace(/'/g, "\\'")}'` : '';
                                    contentHtml = `
                                        <div class="nsfw-image-wrapper" id="${messageId}">
                                            <img src="${imageUrl}" alt="图片" ${ivAttr} data-encrypted-url="${imageUrl}" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;">
                                            <button class="nsfw-toggle-btn" onclick="toggleNSFWImage('${messageId}')">显示NSFW内容</button>
                                        </div>
                                    `;
                                } else {
                                    contentHtml = `<img src="${imageUrl}" alt="图片" style="max-width: 300px; max-height: 300px; border-radius: 8px; cursor: pointer;" onclick="openImageModal('${imageUrl}')">`;
                                }
                            } else if (msg.type === MessageType.PRIVATE_CHAT) {
                                // 私聊消息直接显示文本内容
                                contentHtml = this.escapeHtml(msg.content);
                            }
                        } else if (msg.type === MessageType.FILE) {
                            try {
                                const fileInfo = JSON.parse(msg.content);
                                const icon = fileInfo.type === 'code' ? '📄' : (fileInfo.type === 'text' ? '📝' : '📎');
                                const fileClass = fileInfo.type === 'code' ? 'code-file' : (fileInfo.type === 'text' ? 'text-file' : 'binary-file');
                                const isNSFW = msg.isNSFW || false;
                                
                                // 动态拼接ZFile地址
                                let fileUrl = fileInfo.url;
                                const zfileBaseUrl = this.serviceConfig.zfileServerUrl || 'http://localhost:8081';
                                if (!fileUrl.startsWith('http://') && !fileUrl.startsWith('https://')) {
                                    // 相对路径，直接拼接
                                    fileUrl = zfileBaseUrl + fileUrl;
                                } else {
                                    // 完整URL，提取路径部分并重新拼接
                                    try {
                                        const urlObj = new URL(fileUrl);
                                        const path = urlObj.pathname + urlObj.search;
                                        fileUrl = zfileBaseUrl + path;
                                    } catch (error) {
                                        this.log('warn', '替换文件URL失败:', error.message);
                                    }
                                }
                                
                                contentHtml = `
                                    <div class="file-message ${fileClass}" onclick="openFileModal('${fileUrl}', '${fileInfo.name}', '${fileInfo.type}', ${isNSFW})" style="cursor: pointer;">
                                        <div class="file-header">
                                            <span class="file-icon">${icon}</span>
                                            <div class="file-info">
                                                <span class="file-name">${this.escapeHtml(fileInfo.name)}</span>
                                                <span class="file-size">${fileInfo.size}</span>
                                            </div>
                                        </div>
                                        ${isNSFW ? '<div class="nsfw-badge">NSFW</div>' : ''}
                                    </div>
                                `;
                            } catch (error) {
                                contentHtml = this.escapeHtml(msg.content);
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
                
                // 自动解密NSFW图片并应用模糊效果
                const nsfwImages = messagesArea.querySelectorAll('.nsfw-image-wrapper img[data-iv]');
                nsfwImages.forEach(async img => {
                    const iv = img.getAttribute('data-iv');
                    const encryptedUrl = img.getAttribute('data-encrypted-url');
                    if (iv && encryptedUrl && !img.classList.contains('decrypted')) {
                        try {
                            const decryptedUrl = await chatClient.decryptImage(encryptedUrl, iv);
                            img.src = decryptedUrl;
                            img.classList.add('decrypted');
                        } catch (error) {
                            console.error('自动解密图片失败:', error);
                        }
                    }
                });
                
                // 更新所有NSFW图片的onclick，传递当前解密后的URL
                const allNsfwImages = messagesArea.querySelectorAll('.nsfw-image-wrapper img');
                allNsfwImages.forEach(img => {
                    img.onclick = function() {
                        openImageModal(img.src);
                    };
                });
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
                // 判断是否是私聊消息：首先检查to是否是房间名
                let isRecipientRoom = false;
                for (const room of this.rooms) {
                    if (room.name === firstMessage.to) {
                        isRecipientRoom = true;
                        break;
                    }
                }
                
                // 只有当to不是房间名，且from和to不同时，才认为是私聊消息
                if (!isRecipientRoom && firstMessage.from && firstMessage.to && firstMessage.from !== firstMessage.to) {
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
        
        // Upload avatar to server if selected during registration
        const tempAvatarFile = localStorage.getItem('tempAvatarFile');
        const tempAvatarData = localStorage.getItem('tempAvatarData');
        
        if (tempAvatarFile && tempAvatarData) {
            // Convert base64 data back to File object
            const fileData = JSON.parse(tempAvatarFile);
            const byteCharacters = atob(tempAvatarData.split(',')[1]);
            const byteNumbers = new Array(byteCharacters.length);
            
            for (let i = 0; i < byteCharacters.length; i++) {
                byteNumbers[i] = byteCharacters.charCodeAt(i);
            }
            
            const byteArray = new Uint8Array(byteNumbers);
            const blob = new Blob([byteArray], { type: fileData.type });
            const file = new File([blob], fileData.name, { type: fileData.type });
            
            // Upload to server
            uploadAvatarToServer(username, file, function(success, result) {
                if (success) {
                    console.log('Avatar uploaded successfully:', result);
                } else {
                    console.error('Avatar upload failed:', result);
                }
                
                // Clean up temporary data
                localStorage.removeItem('tempAvatarFile');
                localStorage.removeItem('tempAvatarData');
            });
        }
        
        // Set join date for new users
        if (!localStorage.getItem('joinedDate')) {
            localStorage.setItem('joinedDate', new Date().toLocaleDateString());
        }
        
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
        
        // 请求好友列表
        this.requestFriendList();
        
        this.updateMessagesArea(currentRoomName);
        
        // 如果在user-profile页面，请求用户统计数据
        if (window.location.pathname.includes('user-profile.jsp')) {
            this.log('info', '检测到user-profile页面，请求用户统计数据');
            this.requestUserStats();
        }
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
            
            if (this.serviceConfig.zfileServerUrl) {
                this.zfileServerUrl = this.serviceConfig.zfileServerUrl;
                this.log('info', '使用服务配置中的ZFile URL:', this.zfileServerUrl);
            }
            
            this.log('info', '上传 token 获取成功');
            
            if (this.pendingImageUpload) {
                this.uploadImageToZfile(this.pendingImageUpload);
            } else if (this.pendingFileUpload) {
                this.uploadFileToZfile(this.pendingFileUpload, this.pendingFileType);
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
                iv: iv,
                id: message.id || this.generateMessageId('IMAGE', to)
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
    
    handleFileMessage: function(message) {
        this.log('debug', '收到文件消息');
        
        try {
            const fileContent = message.content;
            const from = message.from;
            const to = message.to;
            const time = message.time;
            const messageObj = {
                type: MessageType.FILE,
                content: fileContent,
                from: from,
                to: to,
                time: time,
                isSystem: false,
                isNSFW: message.isNSFW || false,
                iv: null,
                id: message.id || this.generateMessageId('FILE', to)
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
            this.log('error', `处理文件消息失败: ${error.message}`);
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
    
    getViewToken: function() {
        return new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error('获取token超时'));
            }, 10000);
            
            const originalHandleTokenResponse = this.handleTokenResponse;
            this.handleTokenResponse = function(message) {
                clearTimeout(timeout);
                this.handleTokenResponse = originalHandleTokenResponse;
                originalHandleTokenResponse.call(this, message);
                resolve(this.uploadToken);
            };
            
            this.sendMessage(MessageType.REQUEST_TOKEN, 'server', '');
        });
    },
    
    uploadImageToZfile: async function(file) {
        // 检查是否在临时聊天模式下
        if (this.isTemporaryChat) {
            this.showMessage('Temporary chat only supports text messages', true);
            this.pendingImageUpload = null;
            this.pendingImageNSFW = false;
            return;
        }
        
        this.log('info', '开始上传图片到 zfile');
        
        let uploadFile = file;
        let originalImageDataUrl = null;
        
        // 保存原始图片的 dataURL，用于发送者自己查看
        const reader = new FileReader();
        originalImageDataUrl = await new Promise((resolve) => {
            reader.onload = (e) => resolve(e.target.result);
            reader.readAsDataURL(file);
        });
        
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
        if (this.isInPrivateChat && this.privateChatRecipient) {
            basePath = `/images/private/${this.privateChatRecipient}`;
        } else if (this.currentRoomType === 'PUBLIC') {
            basePath = `/images/group/public/${this.currentRoom}`;
        } else if (this.currentRoomType === 'PRIVATE') {
            basePath = `/images/group/private/${this.currentRoom}`;
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
                
                const configuredZfileUrl = this.serviceConfig.zfileServerUrl || this.zfileServerUrl;
                if (configuredZfileUrl) {
                    try {
                        const urlObj = new URL(uploadUrl);
                        const configUrlObj = new URL(configuredZfileUrl);
                        urlObj.protocol = configUrlObj.protocol;
                        urlObj.host = configUrlObj.host;
                        uploadUrl = urlObj.toString();
                        this.log('info', '使用配置的ZFile URL修正上传URL:', uploadUrl);
                    } catch (error) {
                        this.log('warn', '修正上传URL失败，使用原始URL:', error.message);
                    }
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
                // 只存储相对路径，渲染时动态拼接ZFile地址
                const relativePath = `/pd/chatroom-files/chatroom${uploadPath}/${encodeURIComponent(uploadFile.name)}`;
                const iv = uploadFile.iv || null;
                this.sendImageMessage(relativePath, iv, originalImageDataUrl);
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
            iv: iv ? JSON.stringify(iv) : null
        };
        
        this.pendingImageNSFW = false;
        this.sendMessage(message);
        this.log('info', '发送图片消息');
    },
    
    handleImageUpload: function(file) {
        // 检查是否在临时聊天模式下
        if (this.isTemporaryChat) {
            this.showMessage('Temporary chat only supports text messages', true);
            return;
        }
        
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
    
    detectFileType: function(file) {
        const fileName = file.name.toLowerCase();
        const mimeType = file.type.toLowerCase();
        
        const textExtensions = ['.txt', '.md', '.log', '.csv', '.json', '.xml', '.yaml', '.yml', '.ini', '.conf', '.cfg'];
        const codeExtensions = ['.js', '.java', '.py', '.c', '.cpp', '.h', '.hpp', '.cs', '.php', '.rb', '.go', '.rs', '.swift', '.kt', '.ts', '.tsx', '.jsx', '.vue', '.html', '.css', '.scss', '.sass', '.less', '.sql', '.sh', '.bash', '.bat', '.ps1', '.pl', '.lua', '.r', '.m', '.scala', '.groovy', '.dart', '.fl', '.fs', '.v', '.nim', '.zig', '.jl', '.ex', '.exs', '.erl', '.hs', '.clj', '.cljs', '.cljc', '.edn', '.lisp', '.scm', '.rkt', '.ml', '.mli', '.fsi', '.fsx', '.vbs', '.vbe', '.wsf', '.wsc', '.ws', '.asp', '.aspx', '.jsp', '.jspx', '.cfm', '.cfc', '.hbm', '.xhtml', '.xsl', '.xslt', '.xquery', '.xpath', '.xproc', '.xinclude', '.xlink', '.xbase', '.xforms', '.xhtml', '.xhtml2', '.xhtml+xml', '.xhtml+svg', '.xhtml+mathml', '.xhtml+rdfa', '.xhtml+aria', '.xhtml+role', '.xhtml+microdata', '.xhtml+jsonld', '.xhtml+microdata+jsonld', '.xhtml+aria+microdata', '.xhtml+aria+jsonld', '.xhtml+aria+microdata+jsonld', '.xhtml+role+microdata', '.xhtml+role+jsonld', '.xhtml+role+microdata+jsonld', '.xhtml+aria+role+microdata', '.xhtml+aria+role+jsonld', '.xhtml+aria+role+microdata+jsonld'];
        
        const textMimeTypes = ['text/plain', 'text/markdown', 'text/csv', 'text/json', 'text/xml', 'text/yaml', 'text/x-yaml', 'application/json', 'application/xml', 'application/yaml', 'application/x-yaml'];
        const codeMimeTypes = ['text/javascript', 'text/typescript', 'text/x-java-source', 'text/x-python', 'text/x-c', 'text/x-c++', 'text/x-csharp', 'text/x-php', 'text/x-ruby', 'text/x-go', 'text/x-rust', 'text/x-swift', 'text/x-kotlin', 'text/x-scala', 'text/x-groovy', 'text/x-dart', 'text/x-lua', 'text/x-perl', 'text/x-r', 'text/x-haskell', 'text/x-erlang', 'text/x-clojure', 'text/x-lisp', 'text/x-scheme', 'text/x-ocaml', 'text/x-fsharp', 'text/x-vb', 'text/x-powershell', 'text/x-shellscript', 'application/javascript', 'application/typescript', 'application/x-java-archive', 'application/x-python-code', 'application/x-ruby', 'application/x-go', 'application/x-rust', 'application/x-swift', 'application/x-kotlin', 'application/x-scala', 'application/x-groovy', 'application/x-dart', 'application/x-lua', 'application/x-perl', 'application/x-r', 'application/x-haskell', 'application/x-erlang', 'application/x-clojure', 'application/x-lisp', 'application/x-scheme', 'application/x-ocaml', 'application/x-fsharp', 'application/x-vb', 'application/x-powershell', 'application/x-shellscript'];
        
        const extension = fileName.substring(fileName.lastIndexOf('.'));
        
        if (codeExtensions.includes(extension) || codeMimeTypes.includes(mimeType)) {
            return 'code';
        } else if (textExtensions.includes(extension) || textMimeTypes.includes(mimeType) || mimeType.startsWith('text/')) {
            return 'text';
        } else {
            return 'binary';
        }
    },
    
    handleFileUpload: function(file) {
        // 检查是否在临时聊天模式下
        if (this.isTemporaryChat) {
            this.showMessage('Temporary chat only supports text messages', true);
            return;
        }
        
        if (!file) {
            this.log('error', '请选择有效的文件');
            return;
        }
        
        const maxSize = 50 * 1024 * 1024;
        if (file.size > maxSize) {
            this.log('error', '文件大小不能超过 50MB');
            return;
        }
        
        const fileType = this.detectFileType(file);
        this.log('info', `检测到文件类型: ${fileType}, 文件名: ${file.name}`);
        
        this.pendingFileUpload = file;
        this.pendingFileType = fileType;
        this.showFileUploadPreview(file, fileType);
    },
    
    uploadFileToZfile: async function(file, fileType) {
        // 检查是否在临时聊天模式下
        if (this.isTemporaryChat) {
            this.showMessage('Temporary chat only supports text messages', true);
            this.pendingFileUpload = null;
            this.pendingFileType = null;
            return;
        }
        
        this.log('info', '开始上传文件到 zfile');
        
        const uploadFile = file;
        
        // 生成日期路径 (YYYY/MM/DD)
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const datePath = `${year}/${month}/${day}`;
        
        // 根据聊天类型构建基础路径
        let basePath = '';
        if (this.isInPrivateChat && this.privateChatRecipient) {
            basePath = `/files/private/${this.privateChatRecipient}`;
        } else if (this.currentRoomType === 'PUBLIC') {
            basePath = `/files/group/public/${this.currentRoom}`;
        } else if (this.currentRoomType === 'PRIVATE') {
            basePath = `/files/group/private/${this.currentRoom}`;
        } else {
            basePath = '/files';
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
                
                const configuredZfileUrl = this.serviceConfig.zfileServerUrl || this.zfileServerUrl;
                if (configuredZfileUrl) {
                    try {
                        const urlObj = new URL(uploadUrl);
                        const configUrlObj = new URL(configuredZfileUrl);
                        urlObj.protocol = configUrlObj.protocol;
                        urlObj.host = configUrlObj.host;
                        uploadUrl = urlObj.toString();
                        this.log('info', '使用配置的ZFile URL修正上传URL:', uploadUrl);
                    } catch (error) {
                        this.log('warn', '修正上传URL失败，使用原始URL:', error.message);
                    }
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
                this.log('info', '文件上传成功');
                // 只存储相对路径，渲染时动态拼接ZFile地址
                const relativePath = `/pd/chatroom-files/chatroom${uploadPath}/${encodeURIComponent(uploadFile.name)}`;
                let fileName = file.name;
                let fileSize = this.formatFileSize(file.size);
                let fileTypeDisplay = fileType === 'code' ? '代码' : (fileType === 'text' ? '文本' : '文件');
                
                let messageContent = JSON.stringify({
                    type: fileType,
                    name: fileName,
                    size: fileSize,
                    url: relativePath
                });
                
                const message = {
                    type: MessageType.FILE,
                    from: this.username,
                    to: this.currentRoom,
                    content: messageContent,
                    time: getLocalTime(),
                    isNSFW: this.pendingFileNSFW || false
                };
                
                this.sendMessage(message);
                this.log('info', '发送文件消息');
            } else {
                throw new Error('文件上传失败');
            }
        })
        .catch(error => {
            this.log('error', `上传文件失败: ${error.message}`);
        })
        .finally(() => {
            this.uploadToken = null;
            this.pendingFileUpload = null;
            this.pendingFileType = null;
        });
    },
    
    readFileAsText: function(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = (e) => resolve(e.target.result);
            reader.onerror = (e) => reject(new Error('读取文件失败'));
            reader.readAsText(file);
        });
    },
    
    formatFileSize: function(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    },
    
    showFileUploadPreview: function(file, fileType) {
        const reader = new FileReader();
        reader.onload = (e) => {
            const fileContent = e.target.result;
            const fileName = file.name;
            const fileSize = this.formatFileSize(file.size);
            const fileExtension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
            
            const fileIcon = this.getFileIcon(fileExtension, fileType);
            
            let previewContent = '';
            let isExpandable = false;
            
            if (fileType === 'text' || fileType === 'code') {
                const lines = fileContent.split('\n');
                const previewLines = lines.slice(0, 10).join('\n');
                const totalLines = lines.length;
                isExpandable = totalLines > 10;
                
                previewContent = `
                    <div class="file-preview-content">
                        <div class="file-preview-summary">
                            <pre class="file-preview-text">${this.escapeHtml(previewLines)}</pre>
                            ${isExpandable ? `<div class="file-preview-truncated">... 共 ${totalLines} 行，点击展开查看完整内容</div>` : ''}
                        </div>
                        ${isExpandable ? `
                            <div class="file-preview-full" style="display: none;">
                                <pre class="file-preview-text">${this.escapeHtml(fileContent)}</pre>
                            </div>
                        ` : ''}
                    </div>
                `;
            } else {
                const canPreviewWithOnlyOffice = this.canPreviewWithOnlyOffice(fileExtension);
                previewContent = `
                    <div class="file-preview-binary">
                        <div class="binary-file-icon">${fileIcon}</div>
                        <div class="binary-file-info">
                            <p>二进制文件${canPreviewWithOnlyOffice ? '，可通过OnlyOffice预览' : '，无法预览内容'}</p>
                            <p class="binary-file-warning">${canPreviewWithOnlyOffice ? '点击文件可在线预览和编辑' : '文件将以原始格式传输'}</p>
                        </div>
                    </div>
                `;
            }
            
            const modalHtml = `
                <div id="file-upload-modal" class="modal">
                    <div class="modal-content file-upload-modal-content">
                        <span class="close" onclick="document.getElementById('file-upload-modal').style.display='none'">&times;</span>
                        <h3>文件上传预览</h3>
                        
                        <div class="file-card">
                            <div class="file-card-header">
                                <div class="file-card-icon">${fileIcon}</div>
                                <div class="file-card-info">
                                    <h4 class="file-card-name">${this.escapeHtml(fileName)}</h4>
                                    <div class="file-card-meta">
                                        <span class="file-size">${fileSize}</span>
                                        <span class="file-type">${fileType}</span>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="file-card-body">
                                ${previewContent}
                            </div>
                        </div>
                        
                        <div class="form-group">
                            <label class="nsfw-checkbox-label">
                                <input type="checkbox" id="file-nsfw-checkbox">
                                <span>标记为NSFW（敏感内容）</span>
                            </label>
                        </div>
                        
                        <div class="nsfw-warning" id="file-nsfw-warning" style="display: none;">
                            <div class="warning-icon">⚠️</div>
                            <div class="warning-content">
                                <strong>重要提示</strong>
                                <p>NSFW内容将被加密传输并默认不自动展开</p>
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
                            <button type="button" id="cancel-file-upload-btn">取消</button>
                            <button type="button" id="confirm-file-upload-btn">发送</button>
                        </div>
                    </div>
                </div>
            `;
            
            const existingModal = document.getElementById('file-upload-modal');
            if (existingModal) {
                existingModal.remove();
            }
            
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            
            const modal = document.getElementById('file-upload-modal');
            modal.style.display = 'block';
            
            const nsfwCheckbox = document.getElementById('file-nsfw-checkbox');
            const nsfwWarning = document.getElementById('file-nsfw-warning');
            
            nsfwCheckbox.addEventListener('change', function() {
                nsfwWarning.style.display = this.checked ? 'block' : 'none';
            });
            
            if (isExpandable) {
                const summaryDiv = modal.querySelector('.file-preview-summary');
                const fullDiv = modal.querySelector('.file-preview-full');
                const truncatedDiv = modal.querySelector('.file-preview-truncated');
                
                summaryDiv.addEventListener('click', function() {
                    if (fullDiv.style.display === 'none') {
                        fullDiv.style.display = 'block';
                        summaryDiv.style.display = 'none';
                    }
                });
                
                fullDiv.addEventListener('click', function() {
                    summaryDiv.style.display = 'block';
                    fullDiv.style.display = 'none';
                });
            }
            
            document.getElementById('cancel-file-upload-btn').addEventListener('click', () => {
                modal.style.display = 'none';
                this.pendingFileUpload = null;
                this.pendingFileType = null;
            });
            
            document.getElementById('confirm-file-upload-btn').addEventListener('click', () => {
                const isNSFW = nsfwCheckbox.checked;
                this.pendingFileNSFW = isNSFW;
                modal.style.display = 'none';
                this.requestUploadToken();
            });
            
            const closeBtn = modal.querySelector('.close');
            closeBtn.addEventListener('click', () => {
                modal.style.display = 'none';
                this.pendingFileUpload = null;
                this.pendingFileType = null;
            });
        };
        
        if (fileType === 'text' || fileType === 'code') {
            reader.readAsText(file);
        } else {
            reader.readAsDataURL(file);
        }
    },
    
    canPreviewWithOnlyOffice: function(extension) {
        const onlyOfficeExtensions = [
            '.doc', '.docx', '.docm', '.dot', '.dotx', '.dotm', '.odt', '.fodt',
            '.xls', '.xlsx', '.xlsm', '.xlt', '.xltx', '.xltm', '.ods', '.fods',
            '.ppt', '.pptx', '.pptm', '.pps', '.ppsx', '.ppsm', '.odp', '.fodp',
            '.pdf', '.txt', '.rtf', '.csv', '.html', '.htm', '.xml', '.mht', '.mhtml',
            '.epub', '.djvu', '.xps', '.oxps'
        ];
        return onlyOfficeExtensions.includes(extension.toLowerCase());
    },
    
    getFileIcon: function(extension, fileType) {
        const iconMap = {
            '.js': '📜',
            '.ts': '📜',
            '.java': '☕',
            '.py': '🐍',
            '.c': '🔧',
            '.cpp': '🔧',
            '.h': '📄',
            '.hpp': '📄',
            '.cs': '💻',
            '.php': '🐘',
            '.rb': '💎',
            '.go': '🔵',
            '.rs': '🦀',
            '.swift': '🍎',
            '.kt': '🎯',
            '.html': '🌐',
            '.css': '🎨',
            '.scss': '🎨',
            '.sass': '🎨',
            '.json': '📋',
            '.xml': '📋',
            '.yaml': '📋',
            '.yml': '📋',
            '.md': '📝',
            '.txt': '📄',
            '.log': '📋',
            '.csv': '📊',
            '.sql': '🗄️',
            '.sh': '⌨️',
            '.bash': '⌨️',
            '.ps1': '💻',
            '.dockerfile': '🐳',
            '.zip': '📦',
            '.rar': '📦',
            '.7z': '📦',
            '.tar': '📦',
            '.gz': '📦',
            '.pdf': '📕',
            '.doc': '📘',
            '.docx': '📘',
            '.xls': '📗',
            '.xlsx': '📗',
            '.ppt': '📙',
            '.pptx': '📙',
            '.mp3': '🎵',
            '.mp4': '🎬',
            '.avi': '🎬',
            '.mov': '🎬',
            '.wav': '🎵',
            '.png': '🖼️',
            '.jpg': '🖼️',
            '.jpeg': '🖼️',
            '.gif': '🖼️',
            '.svg': '🖼️',
            '.bmp': '🖼️'
        };
        
        if (iconMap[extension]) {
            return iconMap[extension];
        }
        
        if (fileType === 'code') return '💻';
        if (fileType === 'text') return '📄';
        return '📁';
    },
    
    escapeHtml: function(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
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
            
            reader.readAsArrayBuffer(file);
        });
    },
    
    decryptImage: async function(imageUrl, iv) {
        try {
            console.log('开始解密图片:', imageUrl);
            console.log('原始IV字符串:', iv);
            console.log('IV类型:', typeof iv);
            
            const response = await fetch(imageUrl);
            const encryptedData = await response.arrayBuffer();
            console.log('加密数据长度:', encryptedData.byteLength);
            
            let ivArray;
            try {
                ivArray = JSON.parse(iv);
                console.log('解析后的IV数组:', ivArray);
                console.log('IV数组长度:', ivArray.length);
                console.log('IV数组类型:', Array.isArray(ivArray));
            } catch (e) {
                console.error('解析IV失败:', e);
                throw new Error('IV格式错误');
            }
            
            if (!Array.isArray(ivArray) || ivArray.length !== 12) {
                console.error('IV长度不正确，期望12字节，实际:', ivArray.length);
                throw new Error(`IV长度不正确，期望12字节，实际${ivArray.length}`);
            }
            
            const decrypted = await decryptData(new Uint8Array(encryptedData), ivArray);
            console.log('解密成功，数据长度:', decrypted.byteLength);
            
            const blob = new Blob([decrypted], { type: 'image/png' });
            const url = URL.createObjectURL(blob);
            console.log('创建Blob URL:', url);
            return url;
        } catch (error) {
            console.error('解密图片失败:', error);
            console.error('错误堆栈:', error.stack);
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
    
    handlePrivateChatMessage: function(message) {
        // 私聊消息处理
        const from = message.from;
        const to = message.to;
        const content = message.content;
        const currentUsername = this.username || sessionStorage.getItem('username') || localStorage.getItem('username') || 'unknown';
        
        // 判断是发送者还是接收者
        const isSender = from === currentUsername;
        const otherParty = isSender ? to : from;
        
        // 使用对方用户名作为虚拟房间名
        const privateRoomName = `好友${otherParty}`;
        
        // 如果消息是自己发送的，跳过添加，因为sendPrivateMessage函数已经添加了
        if (isSender) {
            console.log('跳过自己发送的私聊消息');
            return;
        }
        
        if (!this.messages[privateRoomName]) {
            this.messages[privateRoomName] = [];
        }
        
        const localMessage = {
            content: content,
            from: from,
            to: to,
            time: message.time,
            isSystem: false,
            isPrivate: true,
            id: message.id || this.generateMessageId('PRIVATE_CHAT', privateRoomName),
            type: MessageType.PRIVATE_CHAT
        };
        
        // 去重检查
        const isDuplicate = this.messages[privateRoomName].some(m => 
            (localMessage.id && m.id === localMessage.id) || 
            (!localMessage.id && m.content === content && 
             m.from === from && 
             m.time === message.time)
        );
        
        if (!isDuplicate) {
            this.messages[privateRoomName].push(localMessage);
            
            // 保存私聊消息到本地存储
            if (this.messageStorage) {
                this.saveMessageToLocal(privateRoomName, localMessage);
            }
            
            // 更新当前窗口UI
            if (this.currentRoom === privateRoomName) {
                this.updateMessagesArea(privateRoomName);
            }
            
            // 广播到其他窗口
            this.broadcastToWindows('NEW_MESSAGE', {
                roomName: privateRoomName,
                message: localMessage
            }, privateRoomName);
        }
    },

    handleSystemMessage: function(message) {
        // Check if this is a friend request related system message
        if (message.content.includes('好友请求')) {
            // Show as toast notification instead of chat message
            this.showToast(message.content, 'info');
            return;
        }
        
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
                
                // Create avatar image
                const avatarImg = document.createElement('img');
                avatarImg.className = 'user-avatar';
                avatarImg.alt = user.username;
                loadUserAvatar(avatarImg, user.username);
                
                // Create status indicator
                const statusIndicator = document.createElement('div');
                statusIndicator.className = 'user-status-indicator';
                
                // Create username display
                const usernameDiv = document.createElement('div');
                usernameDiv.className = 'user-name';
                usernameDiv.textContent = usernameDisplay;
                
                // Append elements to user item
                userItem.appendChild(avatarImg);
                userItem.appendChild(statusIndicator);
                userItem.appendChild(usernameDiv);
                
                // Only add click event if not the current user
                if (!isCurrentUser) {
                    // Check if this user is already a friend
                    const isFriend = this.friends.some(friend => friend.username === user.username);
                    
                    // Add friend request button (+) if not already friends
                    if (!isFriend) {
                        const addFriendBtn = document.createElement('button');
                        addFriendBtn.className = 'add-friend-btn';
                        addFriendBtn.textContent = '+';
                        addFriendBtn.title = 'Send friend request';
                        addFriendBtn.addEventListener('click', (e) => {
                            e.stopPropagation();
                            this.sendFriendRequest(user.username);
                        });
                        userItem.appendChild(addFriendBtn);
                    }
                    
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
        try {
            const data = JSON.parse(message.content);
            const users = data.users || [];
            const currentUserRole = data.currentUserRole || 'MEMBER';
            const ownerId = data.ownerId;
            const adminIds = data.adminIds || [];
            
            // 存储当前房间的用户信息和角色
            this.currentRoomUsers = users;
            this.currentUserRole = currentUserRole;
            this.currentRoomOwnerId = ownerId;
            this.currentRoomAdminIds = adminIds;
            
            // 显示用户列表
            this.displayRoomUsers(users, currentUserRole, ownerId, adminIds);
            
            this.log('info', `Received room users list: ${users.length} users, current role: ${currentUserRole}`);
        } catch (error) {
            this.log('error', `Failed to parse room users list: ${error.message}`);
            // Fallback to old format parsing
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
        }
    },
    
    // Display room users with role information
    displayRoomUsers: function(users, currentUserRole, ownerId, adminIds) {
        const usersList = document.getElementById('room-users-list');
        if (!usersList) return;
        
        usersList.innerHTML = '';
        
        if (users.length === 0) {
            usersList.innerHTML = '<div style="text-align: center; padding: 20px; color: #6c757d;">No users in this room</div>';
            return;
        }
        
        users.forEach(user => {
            const userItem = document.createElement('div');
            userItem.className = 'user-item';
            
            const avatarImg = document.createElement('img');
            avatarImg.className = 'user-avatar';
            avatarImg.src = 'https://api.dicebear.com/7.x/identicon/svg?seed=' + encodeURIComponent(user.username);
            avatarImg.alt = user.username;
            
            const userInfo = document.createElement('div');
            userInfo.className = 'user-info';
            
            const usernameDiv = document.createElement('div');
            usernameDiv.className = 'user-username';
            usernameDiv.textContent = user.username;
            
            // 添加角色标签
            const roleDiv = document.createElement('div');
            roleDiv.className = 'user-role';
            roleDiv.textContent = this.getRoleText(user.role);
            roleDiv.style.color = this.getRoleColor(user.role);
            
            const statusDiv = document.createElement('div');
            statusDiv.className = 'user-status';
            statusDiv.textContent = user.isOnline ? 'Online' : 'Offline';
            statusDiv.style.color = user.isOnline ? '#28a745' : '#6c757d';
            
            userInfo.appendChild(usernameDiv);
            userInfo.appendChild(roleDiv);
            userInfo.appendChild(statusDiv);
            
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'user-actions';
            
            // 房主可以指定管理员
            if (currentUserRole === 'OWNER' && user.role === 'MEMBER') {
                const setAdminBtn = document.createElement('button');
                setAdminBtn.className = 'set-admin-btn';
                setAdminBtn.textContent = 'Set Admin';
                setAdminBtn.addEventListener('click', () => {
                    this.setRoomAdmin(user.userId, user.username);
                });
                actionsDiv.appendChild(setAdminBtn);
            }
            
            // 房主可以移除管理员
            if (currentUserRole === 'OWNER' && user.role === 'ADMIN') {
                const removeAdminBtn = document.createElement('button');
                removeAdminBtn.className = 'remove-admin-btn';
                removeAdminBtn.textContent = 'Remove Admin';
                removeAdminBtn.addEventListener('click', () => {
                    this.removeRoomAdmin(user.userId, user.username);
                });
                actionsDiv.appendChild(removeAdminBtn);
            }
            
            userItem.appendChild(avatarImg);
            userItem.appendChild(userInfo);
            userItem.appendChild(actionsDiv);
            
            usersList.appendChild(userItem);
        });
    },
    
    // Get role text
    getRoleText: function(role) {
        switch (role) {
            case 'OWNER': return 'Owner';
            case 'ADMIN': return 'Admin';
            case 'MEMBER': return 'Member';
            default: return role;
        }
    },
    
    // Get role color
    getRoleColor: function(role) {
        switch (role) {
            case 'OWNER': return '#dc3545';
            case 'ADMIN': return '#ffc107';
            case 'MEMBER': return '#6c757d';
            default: return '#6c757d';
        }
    },
    
    // Set room admin
    setRoomAdmin: function(userId, username) {
        if (!confirm(`Set ${username} as admin?`)) {
            return;
        }
        
        const message = {
            type: 'SET_ROOM_ADMIN',
            from: this.username,
            to: this.currentRoom,
            content: userId,
            time: getLocalTime(),
            id: this.generateMessageId('SET_ROOM_ADMIN', userId)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Sent set admin request for ${username} in ${this.currentRoom}`);
    },
    
    // Remove room admin
    removeRoomAdmin: function(userId, username) {
        if (!confirm(`Remove admin from ${username}?`)) {
            return;
        }
        
        const message = {
            type: 'REMOVE_ROOM_ADMIN',
            from: this.username,
            to: this.currentRoom,
            content: userId,
            time: getLocalTime(),
            id: this.generateMessageId('REMOVE_ROOM_ADMIN', userId)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Sent remove admin request for ${username} in ${this.currentRoom}`);
    },
    
    // UI updates
    updateRoomsList: function() {
        const roomsList = document.getElementById('rooms-list');
        if (!roomsList) {
            return;
        }
        
        roomsList.innerHTML = '';
        
        // Display rooms
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
        
        // Display friends
        if (this.friends.length > 0) {
            const friendsDivider = document.createElement('div');
            friendsDivider.className = 'rooms-divider';
            friendsDivider.innerHTML = '<div class="divider-line"></div><span class="divider-text">Friends</span><div class="divider-line"></div>';
            roomsList.appendChild(friendsDivider);
            
            this.friends.forEach(friend => {
                const friendDiv = document.createElement('div');
                friendDiv.className = `room-item friend-item ${this.currentRoom === '好友' + friend.username ? 'active' : ''}`;
                friendDiv.innerHTML = `
                    <div class="room-info" onclick="chatClient.switchToFriendChat('${friend.username}')">
                        <strong>${friend.username}</strong> (FRIEND)
                    </div>
                    <button class="new-window-btn" onclick="event.stopPropagation(); chatClient.openFriendChatInNewWindow('${friend.username}')">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16">
                            <path d="M8.636 3.5a.5.5 0 0 0-.5-.5H1.5A1.5 1.5 0 0 0 0 4.5v10A1.5 1.5 0 0 0 1.5 16h10a1.5 1.5 0 0 0 1.5-1.5V7.864a.5.5 0 0 0-1 0V14.5a.5.5 0 0 1-.5.5h-10a.5.5 0 0 1-.5-.5v-10a.5.5 0 0 1 .5-.5h6.636a.5.5 0 0 0 .5-.5z"/>
                            <path d="M16 .5a.5.5 0 0 0-.5-.5h-5a.5.5 0 0 0 0 1h3.793L6.146 9.146a.5.5 0 1 0 .708.708L15 1.707V5.5a.5.5 0 0 0 1 0v-5z"/>
                        </svg>
                    </button>
                `;
                roomsList.appendChild(friendDiv);
            });
        }
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
    
    // 私聊架构说明：
    // 1. 临时聊天（isTemporaryChat = true, isFriendChat = false）：
    //    - 当前实现，只能发送文本消息
    //    - 禁用图片和文件上传功能
    //    - 用于临时性的私聊沟通
    // 
    // 2. 好友聊天（isTemporaryChat = false, isFriendChat = true）：
    //    - 后续实现，可以发送文本、图片和文件
    //    - 完整的私聊功能
    //    - 用于好友间的正式聊天
    //
    // 两者都是私聊模式（isInPrivateChat = true），但功能限制不同
    
    // Switch to temporary private chat with a specific user
    switchToPrivateChat: function(username) {
        if (!username || username === this.username) {
            return;
        }
        
        // Check if this user is already a friend
        const friend = this.friends.find(friend => friend.username === username);
        if (friend) {
            // Switch to friend chat mode
            this.switchToFriendChat(username);
            return;
        }
        
        // Save current room information for potential return
        this.previousRoom = this.currentRoom;
        this.previousRoomType = this.currentRoomType;
        
        // Switch to private chat mode - temporary chat
        this.isInPrivateChat = true;
        this.privateChatRecipient = username;
        this.isTemporaryChat = true; // 设置为临时聊天
        this.isFriendChat = false; // 不是好友聊天
        
        // Update current room to a virtual room named after the recipient
        this.currentRoom = `好友${username}`;
        this.currentRoomType = 'PRIVATE'; // Mark as private chat
        
        // Update h3 title to show private chat recipient
        const currentRoomNameElement = document.getElementById('current-room-name');
        if (currentRoomNameElement) {
            currentRoomNameElement.textContent = `与${username}临时聊天`;
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
        
        // Disable image and file upload buttons in temporary chat mode
        const imageBtn = document.getElementById('image-btn');
        const fileBtn = document.getElementById('file-btn');
        if (imageBtn) {
            imageBtn.disabled = true;
            imageBtn.classList.add('disabled');
            imageBtn.title = 'Temporary chat only supports text messages';
        }
        if (fileBtn) {
            fileBtn.disabled = true;
            fileBtn.classList.add('disabled');
            fileBtn.title = 'Temporary chat only supports text messages';
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
    
    // Switch to friend chat with a specific user
    switchToFriendChat: function(username) {
        if (!username || username === this.username) {
            return;
        }
        
        // Save current room information for potential return
        this.previousRoom = this.currentRoom;
        this.previousRoomType = this.currentRoomType;
        
        // Switch to friend chat mode
        this.isInPrivateChat = true;
        this.privateChatRecipient = username;
        this.isTemporaryChat = false; // 不是临时聊天
        this.isFriendChat = true; // 是好友聊天
        
        // Update current room to a virtual room named after the recipient
        this.currentRoom = `好友${username}`;
        this.currentRoomType = 'PRIVATE';
        
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
        
        // Enable image and file upload buttons for friend chat
        const imageBtn = document.getElementById('image-btn');
        const fileBtn = document.getElementById('file-btn');
        if (imageBtn) {
            imageBtn.disabled = false;
            imageBtn.classList.remove('disabled');
            imageBtn.title = 'Send Image';
        }
        if (fileBtn) {
            fileBtn.disabled = false;
            fileBtn.classList.remove('disabled');
            fileBtn.title = 'Send File';
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
        
        // 请求服务器拉取私聊历史消息
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
        
        // Re-enable image and file upload buttons when returning to room
        const imageBtn = document.getElementById('image-btn');
        const fileBtn = document.getElementById('file-btn');
        if (imageBtn) {
            imageBtn.disabled = false;
            imageBtn.classList.remove('disabled');
            imageBtn.title = 'Send Image';
        }
        if (fileBtn) {
            fileBtn.disabled = false;
            fileBtn.classList.remove('disabled');
            fileBtn.title = 'Send File';
        }
        
        // Reset temporary chat and friend chat flags
        this.isTemporaryChat = true; // 重置为默认值
        this.isFriendChat = false; // 重置为默认值
            
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
    
    // 未来扩展：好友聊天功能
    // 当实现好友关系系统后，添加以下功能：
    // 
    // switchToFriendChat: function(username) {
    //     // 切换到好友聊天模式
    //     this.isInPrivateChat = true;
    //     this.privateChatRecipient = username;
    //     this.isTemporaryChat = false; // 不是临时聊天
    //     this.isFriendChat = true; // 是好友聊天
    //     
    //     // 启用图片和文件上传功能
    //     const imageBtn = document.getElementById('image-btn');
    //     const fileBtn = document.getElementById('file-btn');
    //     if (imageBtn) {
    //         imageBtn.disabled = false;
    //         imageBtn.classList.remove('disabled');
    //         imageBtn.title = 'Send Image';
    //     }
    //     if (fileBtn) {
    //         fileBtn.disabled = false;
    //         fileBtn.classList.remove('disabled');
    //         fileBtn.title = 'Send File';
    //     }
    //     
    //     // 其他UI更新...
    // },
    //
    // 服务器端需要相应扩展：
    // 1. 数据库添加好友关系表
    // 2. 添加好友管理API（添加、删除、查询好友）
    // 3. 扩展PRIVATE_CHAT消息处理，支持好友聊天标记
    // 4. 添加好友聊天历史记录查询// },
    
    // Send friend request to a user
    sendFriendRequest: function(toUsername) {
        if (!toUsername || toUsername === this.username) {
            this.showMessage('Invalid username', true);
            return;
        }
        
        // Check if already friends
        const isFriend = this.friends.some(friend => friend.username === toUsername);
        if (isFriend) {
            this.showMessage(`${toUsername} is already your friend`, true);
            return;
        }
        
        // Check if there's already a sent request
        const hasSentRequest = this.sentFriendRequests.some(req => req.to === toUsername && req.status === 'PENDING');
        if (hasSentRequest) {
            this.showMessage(`You already have a pending friend request to ${toUsername}`, true);
            return;
        }
        
        // Send friend request
        const message = {
            type: MessageType.FRIEND_REQUEST,
            from: this.username,
            to: toUsername,
            content: `Would you like to be friends?`,
            time: getLocalTime(),
            id: this.generateMessageId('FRIEND_REQUEST', toUsername)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Sent friend request to ${toUsername}`);
    },
    
    // Handle friend request
    handleFriendRequest: function(message) {
        const fromUsername = message.from;
        const content = message.content;
        
        // Add to received requests
        if (!this.receivedFriendRequests.some(req => req.from === fromUsername)) {
            this.receivedFriendRequests.push({
                from: fromUsername,
                content: content,
                time: message.time,
                status: 'PENDING'
            });
            
            // Save to localStorage for persistence
            this.saveFriendRequests();
        }
        
        // Don't show friend request as chat message
        // Only show friend request notification in UI
        this.showFriendRequestNotification(fromUsername, content);
        
        // Log the friend request
        this.log('info', `Received friend request from ${fromUsername}`);
    },
    
    // Show friend request notification
    showFriendRequestNotification: function(fromUsername, content) {
        // Don't add notification to chat area
        // Friend requests are handled in the friend requests page
        // Just update the friend requests UI if the modal is open
        const friendRequestsDiv = document.getElementById('friend-requests');
        if (friendRequestsDiv && friendRequestsDiv.style.display !== 'none') {
            this.displayAllFriendRequests();
        }
        
        // Show a toast notification to inform user about the friend request
        this.showToast(`New friend request from ${fromUsername}`, 'info');
    },
    
    // Show toast notification
    showToast: function(message, type = 'info') {
        // Create toast element
        const toast = document.createElement('div');
        toast.className = 'toast-notification';
        toast.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            background-color: ${type === 'info' ? '#17a2b8' : '#28a745'};
            color: white;
            border-radius: 4px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.2);
            z-index: 10000;
            animation: slideIn 0.3s ease;
        `;
        toast.textContent = message;
        
        // Add to body
        document.body.appendChild(toast);
        
        // Remove after 3 seconds
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => {
                toast.remove();
            }, 300);
        }, 3000);
    },
    
    // Respond to friend request
    respondToFriendRequest: function(fromUsername, response) {
        const message = {
            type: MessageType.FRIEND_REQUEST_RESPONSE,
            from: this.username,
            to: fromUsername,
            content: `${response}:${this.username}`,
            time: getLocalTime(),
            id: this.generateMessageId('FRIEND_REQUEST_RESPONSE', fromUsername)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Responded to friend request from ${fromUsername}: ${response}`);
        
        // Don't remove from requests, just update status
        // The UI will be updated when server responds
    },
    
    // Handle friend request response
    handleFriendRequestResponse: function(message) {
        const content = message.content;
        const fromUsername = message.from;
        
        if (content.startsWith('accept:')) {
            // Friend request accepted
            const friendUsername = content.substring(7);
            
            // Add to friends list
            if (!this.friends.some(friend => friend.username === friendUsername)) {
                this.friends.push({
                    username: friendUsername,
                    createdAt: new Date().toISOString()
                });
            }
            
            this.showMessage(`[Friend] ${friendUsername} accepted your friend request!`, true);
            this.log('info', `Friend request accepted by ${friendUsername}`);
            
            // Update UI to show friend status
            this.updateFriendListUI();
            
            // Update rooms list to show new friend
            this.updateRoomsList();
            
            // Update sent request status
            const sentRequest = this.sentFriendRequests.find(req => req.to === friendUsername);
            if (sentRequest) {
                sentRequest.status = 'ACCEPTED';
                this.saveFriendRequests();
                this.displayAllFriendRequests();
            }
        } else if (content.startsWith('reject:')) {
            // Friend request rejected
            const friendUsername = content.substring(7);
            this.showMessage(`[Friend] ${friendUsername} rejected your friend request.`, true);
            this.log('info', `Friend request rejected by ${friendUsername}`);
            
            // Update sent request status
            const sentRequest = this.sentFriendRequests.find(req => req.to === friendUsername);
            if (sentRequest) {
                sentRequest.status = 'REJECTED';
                this.saveFriendRequests();
                this.displayAllFriendRequests();
            }
        }
    },
    
    // Handle friend list
    handleFriendList: function(message) {
        try {
            const friendList = JSON.parse(message.content);
            this.friends = friendList;
            this.log('info', `Received friend list: ${friendList.length} friends`);
            this.updateFriendListUI();
        } catch (error) {
            this.log('error', `Failed to parse friend list: ${error.message}`);
        }
    },
    
    // Update friend list UI
    updateFriendListUI: function() {
        // Update user list to show friend status
        const usersList = document.getElementById('room-users-list');
        if (usersList) {
            // Refresh the user list to show/hide add friend buttons
            const currentRoomName = document.getElementById('current-room-name')?.textContent;
            if (currentRoomName && currentRoomName !== 'system') {
                this.sendMessage(MessageType.LIST_ROOM_USERS, currentRoomName, '');
            }
        }
    },
    
    // Request friend list from server
    requestFriendList: function() {
        const message = {
            type: MessageType.REQUEST_FRIEND_LIST,
            from: this.username,
            to: 'server',
            content: '',
            time: getLocalTime(),
            id: this.generateMessageId('REQUEST_FRIEND_LIST', 'server')
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', 'Requested friend list from server');
    },
    
    // Request all friend requests from server
    requestAllFriendRequests: function() {
        const message = {
            type: MessageType.REQUEST_ALL_FRIEND_REQUESTS,
            from: this.username,
            to: 'server',
            content: '',
            time: getLocalTime(),
            id: this.generateMessageId('REQUEST_ALL_FRIEND_REQUESTS', 'server')
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', 'Requested all friend requests from server');
    },
    
    // Handle request all friend requests
    handleRequestAllFriendRequests: function(message) {
        // This is handled by the server
        this.log('debug', 'Request all friend requests sent');
    },
    
    // Handle all friend requests
    handleAllFriendRequests: function(message) {
        try {
            const allRequests = JSON.parse(message.content);
            
            // Separate into received and sent requests
            this.receivedFriendRequests = [];
            this.sentFriendRequests = [];
            
            allRequests.forEach(request => {
                if (request.isReceived) {
                    this.receivedFriendRequests.push({
                        from: request.fromUsername,
                        to: request.toUsername,
                        content: 'Would you like to be friends?',
                        time: request.createdAt,
                        status: request.status,
                        id: request.id
                    });
                } else {
                    this.sentFriendRequests.push({
                        from: request.fromUsername,
                        to: request.toUsername,
                        content: 'Would you like to be friends?',
                        time: request.createdAt,
                        status: request.status,
                        id: request.id
                    });
                }
            });
            
            // Save to localStorage
            this.saveFriendRequests();
            
            // Update UI
            this.displayAllFriendRequests();
            
            this.log('info', `Received all friend requests: ${allRequests.length} total`);
        } catch (error) {
            this.log('error', `Failed to parse all friend requests: ${error.message}`);
        }
    },
    
    // Save room requests to localStorage
    saveRoomRequests: function() {
        try {
            const roomRequestsData = {
                received: this.receivedRoomRequests,
                sent: this.sentRoomRequests
            };
            localStorage.setItem('roomRequests', JSON.stringify(roomRequestsData));
            this.log('info', 'Saved room requests to localStorage');
        } catch (error) {
            this.log('error', `Failed to save room requests: ${error.message}`);
        }
    },
    
    // Load room requests from localStorage
    loadRoomRequests: function() {
        try {
            const storedRequests = localStorage.getItem('roomRequests');
            if (storedRequests) {
                const roomRequestsData = JSON.parse(storedRequests);
                this.receivedRoomRequests = roomRequestsData.received || [];
                this.sentRoomRequests = roomRequestsData.sent || [];
            }
            
            // Display all room requests
            this.displayAllRoomRequests();
        } catch (error) {
            this.log('error', `Failed to load room requests: ${error.message}`);
        }
    },
    
    // Save friend requests to localStorage
    saveFriendRequests: function() {
        try {
            const friendRequestsData = {
                received: this.receivedFriendRequests,
                sent: this.sentFriendRequests
            };
            localStorage.setItem('friendRequests', JSON.stringify(friendRequestsData));
            this.log('info', 'Saved friend requests to localStorage');
        } catch (error) {
            this.log('error', `Failed to save friend requests: ${error.message}`);
        }
    },
    
    // Load friend requests from localStorage
    loadFriendRequests: function() {
        try {
            const storedRequests = localStorage.getItem('friendRequests');
            if (storedRequests) {
                const friendRequestsData = JSON.parse(storedRequests);
                this.receivedFriendRequests = friendRequestsData.received || [];
                this.sentFriendRequests = friendRequestsData.sent || [];
            }
            
            // Display all friend requests
            this.displayAllFriendRequests();
        } catch (error) {
            this.log('error', `Failed to load friend requests: ${error.message}`);
        }
    },
    
    // Display all friend requests
    displayAllFriendRequests: function() {
        const requestsDiv = document.getElementById('friend-requests');
        if (!requestsDiv) return;
        
        requestsDiv.innerHTML = '';
        
        const hasRequests = this.receivedFriendRequests.length > 0 || this.sentFriendRequests.length > 0;
        
        if (!hasRequests) {
            requestsDiv.innerHTML = '<div style="text-align: center; padding: 20px; color: #6c757d;">No friend requests</div>';
            return;
        }
        
        // Display received requests
        if (this.receivedFriendRequests.length > 0) {
            const receivedSection = document.createElement('div');
            receivedSection.className = 'friend-requests-section';
            receivedSection.innerHTML = '<h4 style="margin: 15px 0 10px 0; color: #4a6fa5; border-bottom: 2px solid #4a6fa5; padding-bottom: 5px;">Received Requests</h4>';
            
            this.receivedFriendRequests.forEach(request => {
                const requestItem = this.createFriendRequestItem(request, true);
                receivedSection.appendChild(requestItem);
            });
            
            requestsDiv.appendChild(receivedSection);
        }
        
        // Display sent requests
        if (this.sentFriendRequests.length > 0) {
            const sentSection = document.createElement('div');
            sentSection.className = 'friend-requests-section';
            sentSection.innerHTML = '<h4 style="margin: 15px 0 10px 0; color: #6c757d; border-bottom: 2px solid #6c757d; padding-bottom: 5px;">Sent Requests</h4>';
            
            this.sentFriendRequests.forEach(request => {
                const requestItem = this.createFriendRequestItem(request, false);
                sentSection.appendChild(requestItem);
            });
            
            requestsDiv.appendChild(sentSection);
        }
    },
    
    // Create friend request item
    createFriendRequestItem: function(request, isReceived) {
        const requestItem = document.createElement('div');
        requestItem.className = 'friend-request-item';
        
        // Create avatar
        const avatarImg = document.createElement('img');
        avatarImg.className = 'friend-request-avatar';
        avatarImg.alt = isReceived ? request.from : request.to;
        loadUserAvatar(avatarImg, isReceived ? request.from : request.to);
        
        // Create info
        const infoDiv = document.createElement('div');
        infoDiv.className = 'friend-request-info';
        
        const usernameDiv = document.createElement('div');
        usernameDiv.className = 'friend-request-username';
        usernameDiv.textContent = isReceived ? request.from : request.to;
        
        const statusDiv = document.createElement('div');
        statusDiv.style.fontSize = '12px';
        statusDiv.style.marginTop = '4px';
        statusDiv.textContent = this.getStatusText(request.status);
        statusDiv.style.color = this.getStatusColor(request.status);
        
        const timeDiv = document.createElement('div');
        timeDiv.style.fontSize = '11px';
        timeDiv.style.color = '#6c757d';
        timeDiv.style.marginTop = '2px';
        timeDiv.textContent = request.time ? request.time.substring(0, 16) : '';
        
        infoDiv.appendChild(usernameDiv);
        infoDiv.appendChild(statusDiv);
        infoDiv.appendChild(timeDiv);
        
        // Create actions
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'friend-request-actions';
        
        if (isReceived) {
            if (request.status === 'PENDING') {
                const acceptBtn = document.createElement('button');
                acceptBtn.className = 'accept-friend-btn';
                acceptBtn.textContent = 'Accept';
                acceptBtn.addEventListener('click', () => {
                    this.respondToFriendRequest(request.from, 'accept');
                    request.status = 'ACCEPTED';
                    this.saveFriendRequests();
                    this.displayAllFriendRequests();
                });
                
                const rejectBtn = document.createElement('button');
                rejectBtn.className = 'reject-friend-btn';
                rejectBtn.textContent = 'Reject';
                rejectBtn.addEventListener('click', () => {
                    this.respondToFriendRequest(request.from, 'reject');
                    request.status = 'REJECTED';
                    this.saveFriendRequests();
                    this.displayAllFriendRequests();
                });
                
                actionsDiv.appendChild(acceptBtn);
                actionsDiv.appendChild(rejectBtn);
            } else {
                const statusLabel = document.createElement('span');
                statusLabel.className = 'search-result-btn';
                statusLabel.style.backgroundColor = this.getStatusColor(request.status);
                statusLabel.style.color = 'white';
                statusLabel.textContent = this.getStatusText(request.status);
                actionsDiv.appendChild(statusLabel);
            }
        } else {
            const statusLabel = document.createElement('span');
            statusLabel.className = 'search-result-btn';
            statusLabel.style.backgroundColor = this.getStatusColor(request.status);
            statusLabel.style.color = 'white';
            statusLabel.textContent = this.getStatusText(request.status);
            actionsDiv.appendChild(statusLabel);
        }
        
        requestItem.appendChild(avatarImg);
        requestItem.appendChild(infoDiv);
        requestItem.appendChild(actionsDiv);
        
        return requestItem;
    },
    
    // Get status text
    getStatusText: function(status) {
        switch (status) {
            case 'PENDING': return 'Pending';
            case 'ACCEPTED': return 'Accepted';
            case 'REJECTED': return 'Rejected';
            default: return status;
        }
    },
    
    // Get status color
    getStatusColor: function(status) {
        switch (status) {
            case 'PENDING': return '#ffc107';
            case 'ACCEPTED': return '#28a745';
            case 'REJECTED': return '#dc3545';
            default: return '#6c757d';
        }
    },
    
    // Open user search modal
    openUserSearchModal: function() {
        const modal = document.getElementById('user-search-modal');
        if (modal) {
            modal.style.display = 'block';
            
            // Request all friend requests from server
            this.requestAllFriendRequests();
            
            // Setup tab switching
            this.setupSearchModalTabs();
        }
    },
    
    // Setup search modal tabs
    setupSearchModalTabs: function() {
        const tabBtns = document.querySelectorAll('.tab-btn');
        const searchResults = document.getElementById('search-results');
        const friendRequests = document.getElementById('friend-requests');
        
        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                // Remove active class from all tabs
                tabBtns.forEach(b => b.classList.remove('active'));
                
                // Add active class to clicked tab
                btn.classList.add('active');
                
                // Show/hide corresponding content
                const tab = btn.getAttribute('data-tab');
                if (tab === 'search') {
                    searchResults.style.display = 'block';
                    friendRequests.style.display = 'none';
                } else if (tab === 'requests') {
                    searchResults.style.display = 'none';
                    friendRequests.style.display = 'block';
                }
            });
        });
        
        // Setup search button
        const searchBtn = document.getElementById('search-users-btn');
        const searchInput = document.getElementById('user-search-input');
        
        searchBtn.addEventListener('click', () => {
            const searchTerm = searchInput.value.trim();
            if (searchTerm) {
                this.searchUsers(searchTerm);
            }
        });
        
        // Allow Enter key to trigger search
        searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                const searchTerm = searchInput.value.trim();
                if (searchTerm) {
                    this.searchUsers(searchTerm);
                }
            }
        });
    },
    
    // Search users
    searchUsers: function(searchTerm) {
        if (!searchTerm || searchTerm.trim().length === 0) {
            this.showMessage('Please enter a search term', true);
            return;
        }
        
        const message = {
            type: MessageType.SEARCH_USERS,
            from: this.username,
            to: 'server',
            content: searchTerm,
            time: getLocalTime(),
            id: this.generateMessageId('SEARCH_USERS', searchTerm)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Searching for users: ${searchTerm}`);
    },
    
    // Handle search users request
    handleSearchUsersRequest: function(message) {
        // This is handled by the server
        this.log('debug', 'Search users request sent');
    },
    
    // Handle users search result
    handleUsersSearchResult: function(message) {
        try {
            const users = JSON.parse(message.content);
            this.displaySearchResults(users);
            this.log('info', `Received search results: ${users.length} users`);
        } catch (error) {
            this.log('error', `Failed to parse search results: ${error.message}`);
        }
    },
    
    // Display search results
    displaySearchResults: function(users) {
        const searchResultsDiv = document.getElementById('search-results');
        if (!searchResultsDiv) return;
        
        searchResultsDiv.innerHTML = '';
        
        if (users.length === 0) {
            searchResultsDiv.innerHTML = '<div style="text-align: center; padding: 20px; color: #6c757d;">No users found</div>';
            return;
        }
        
        users.forEach(user => {
            const isFriend = this.friends.some(friend => friend.username === user.username);
            const isSelf = user.username === this.username;
            
            const resultItem = document.createElement('div');
            resultItem.className = 'search-result-item';
            
            // Create avatar
            const avatarImg = document.createElement('img');
            avatarImg.className = 'search-result-avatar';
            avatarImg.alt = user.username;
            loadUserAvatar(avatarImg, user.username);
            
            // Create info
            const infoDiv = document.createElement('div');
            infoDiv.className = 'search-result-info';
            
            const usernameDiv = document.createElement('div');
            usernameDiv.className = 'search-result-username';
            usernameDiv.textContent = user.username;
            
            infoDiv.appendChild(usernameDiv);
            
            // Create actions
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'search-result-actions';
            
            if (!isSelf && !isFriend) {
                const addFriendBtn = document.createElement('button');
                addFriendBtn.className = 'search-result-btn add-friend-from-search-btn';
                addFriendBtn.textContent = 'Add Friend';
                addFriendBtn.addEventListener('click', () => {
                    this.sendFriendRequest(user.username);
                });
                actionsDiv.appendChild(addFriendBtn);
            } else if (isFriend) {
                const friendLabel = document.createElement('span');
                friendLabel.className = 'search-result-btn';
                friendLabel.style.backgroundColor = '#28a745';
                friendLabel.style.color = 'white';
                friendLabel.textContent = 'Already Friends';
                actionsDiv.appendChild(friendLabel);
            } else if (isSelf) {
                const selfLabel = document.createElement('span');
                selfLabel.className = 'search-result-btn';
                selfLabel.style.backgroundColor = '#6c757d';
                selfLabel.style.color = 'white';
                selfLabel.textContent = 'You';
                actionsDiv.appendChild(selfLabel);
            }
            
            resultItem.appendChild(avatarImg);
            resultItem.appendChild(infoDiv);
            resultItem.appendChild(actionsDiv);
            
            searchResultsDiv.appendChild(resultItem);
        });
    },
    
    // Open room search modal
    openRoomSearchModal: function() {
        const modal = document.getElementById('room-search-modal');
        if (modal) {
            modal.style.display = 'block';
            
            // Load room requests from localStorage
            this.loadRoomRequests();
            
            // Setup room search modal tabs
            this.setupRoomSearchModalTabs();
        }
    },
    
    // Setup room search modal tabs
    setupRoomSearchModalTabs: function() {
        const tabBtns = document.querySelectorAll('#room-search-modal .tab-btn');
        const searchResults = document.getElementById('room-search-results');
        const roomRequests = document.getElementById('room-requests');
        
        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                // Remove active class from all tabs
                tabBtns.forEach(b => b.classList.remove('active'));
                
                // Add active class to clicked tab
                btn.classList.add('active');
                
                // Show/hide corresponding content
                const tab = btn.getAttribute('data-tab');
                if (tab === 'search') {
                    searchResults.style.display = 'block';
                    roomRequests.style.display = 'none';
                } else if (tab === 'requests') {
                    searchResults.style.display = 'none';
                    roomRequests.style.display = 'block';
                }
            });
        });
        
        // Setup search button
        const searchBtn = document.getElementById('search-rooms-btn');
        const searchInput = document.getElementById('room-search-input');
        
        searchBtn.addEventListener('click', () => {
            const searchTerm = searchInput.value.trim();
            if (searchTerm) {
                this.searchRooms(searchTerm);
            }
        });
        
        // Allow Enter key to trigger search
        searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                const searchTerm = searchInput.value.trim();
                if (searchTerm) {
                    this.searchRooms(searchTerm);
                }
            }
        });
    },
    
    // Search rooms
    searchRooms: function(searchTerm) {
        if (!searchTerm || searchTerm.trim().length === 0) {
            this.showMessage('Please enter a search term', true);
            return;
        }
        
        const message = {
            type: MessageType.SEARCH_ROOMS,
            from: this.username,
            to: 'server',
            content: searchTerm,
            time: getLocalTime(),
            id: this.generateMessageId('SEARCH_ROOMS', searchTerm)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('debug', 'Search rooms request sent');
    },
    
    // Handle rooms search result
    handleRoomsSearchResult: function(message) {
        try {
            const rooms = JSON.parse(message.content);
            this.displayRoomSearchResults(rooms);
            this.log('info', `Received room search results: ${rooms.length} rooms`);
        } catch (error) {
            this.log('error', `Failed to parse room search results: ${error.message}`);
        }
    },
    
    // Display room search results
    displayRoomSearchResults: function(rooms) {
        const searchResultsDiv = document.getElementById('room-search-results');
        if (!searchResultsDiv) return;
        
        searchResultsDiv.innerHTML = '';
        
        if (rooms.length === 0) {
            searchResultsDiv.innerHTML = '<div style="text-align: center; padding: 20px; color: #6c757d;">No rooms found</div>';
            return;
        }
        
        rooms.forEach(room => {
            const isInRoom = this.rooms.some(r => r.name === room.name);
            
            const resultItem = document.createElement('div');
            resultItem.className = 'search-result-item-room';
            
            const avatarImg = document.createElement('img');
            avatarImg.className = 'search-result-avatar-room';
            avatarImg.src = 'https://api.dicebear.com/7.x/identicon/svg?seed=' + encodeURIComponent(room.name);
            avatarImg.alt = room.name;
            
            const infoDiv = document.createElement('div');
            infoDiv.className = 'search-result-info-room';
            
            const nameDiv = document.createElement('div');
            nameDiv.className = 'search-result-username-room';
            nameDiv.textContent = room.name;
            
            const typeDiv = document.createElement('div');
            typeDiv.style.fontSize = '12px';
            typeDiv.style.color = '#6c757d';
            typeDiv.textContent = `Type: ${room.type} | Members: ${room.memberCount || 0}`;
            
            infoDiv.appendChild(nameDiv);
            infoDiv.appendChild(typeDiv);
            
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'search-result-actions-room';
            
            if (isInRoom) {
                const joinedLabel = document.createElement('span');
                joinedLabel.className = 'search-result-btn-room';
                joinedLabel.style.backgroundColor = '#28a745';
                joinedLabel.style.color = 'white';
                joinedLabel.textContent = 'Joined';
                actionsDiv.appendChild(joinedLabel);
            } else {
                const requestBtn = document.createElement('button');
                requestBtn.className = 'search-result-btn-room request-join-btn';
                requestBtn.textContent = 'Request Join';
                requestBtn.addEventListener('click', () => {
                    this.requestRoomJoin(room.name);
                });
                actionsDiv.appendChild(requestBtn);
            }
            
            resultItem.appendChild(avatarImg);
            resultItem.appendChild(infoDiv);
            resultItem.appendChild(actionsDiv);
            
            searchResultsDiv.appendChild(resultItem);
        });
    },
    
    // Request room join
    requestRoomJoin: function(roomName) {
        const message = {
            type: MessageType.REQUEST_ROOM_JOIN,
            from: this.username,
            to: 'server',
            content: roomName,
            time: getLocalTime(),
            id: this.generateMessageId('REQUEST_ROOM_JOIN', roomName)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Sent room join request for ${roomName}`);
        
        // Add to sent requests
        this.sentRoomRequests.push({
            roomName: roomName,
            time: getLocalTime(),
            status: 'PENDING'
        });
        
        // Save to localStorage
        this.saveRoomRequests();
        
        // Update UI
        this.displayAllRoomRequests();
        
        this.showMessage(`Room join request sent to ${roomName}`, true);
    },
    
    // Handle room join request
    handleRoomJoinRequest: function(message) {
        const fromUsername = message.from;
        const roomName = message.content;
        
        // 检查当前用户是否为房主或管理员
        const isOwnerOrAdmin = this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
        
        // 只有房主或管理员才处理房间加入请求
        if (!isOwnerOrAdmin) {
            this.log('debug', `Ignoring room join request from ${fromUsername} - not owner or admin`);
            return;
        }
        
        // Add to received requests
        if (!this.receivedRoomRequests.some(req => req.from === fromUsername && req.roomName === roomName)) {
            this.receivedRoomRequests.push({
                from: fromUsername,
                roomName: roomName,
                time: message.time,
                status: 'PENDING'
            });
            
            // Save to localStorage for persistence
            this.saveRoomRequests();
            
            // Update room requests UI if modal is open
            const roomRequestsDiv = document.getElementById('room-requests');
            if (roomRequestsDiv && roomRequestsDiv.style.display !== 'none') {
                this.displayAllRoomRequests();
            }
        }
        
        // Show toast notification
        this.showToast(`New room join request from ${fromUsername} for ${roomName}`, 'info');
        
        // Log the room join request
        this.log('info', `Received room join request from ${fromUsername} for ${roomName}`);
    },
    
    // Display all room requests
    displayAllRoomRequests: function() {
        const requestsDiv = document.getElementById('room-requests');
        if (!requestsDiv) return;
        
        requestsDiv.innerHTML = '';
        
        const hasRequests = this.receivedRoomRequests.length > 0 || this.sentRoomRequests.length > 0;
        
        if (!hasRequests) {
            requestsDiv.innerHTML = '<div style="text-align: center; padding: 20px; color: #6c757d;">No room requests</div>';
            return;
        }
        
        // Display received requests
        if (this.receivedRoomRequests.length > 0) {
            const receivedSection = document.createElement('div');
            receivedSection.className = 'room-requests-section';
            receivedSection.innerHTML = '<h4 style="margin: 15px 0 10px 0; color: #4a6fa5; border-bottom: 2px solid #4a6fa5; padding-bottom: 5px;">Received Requests</h4>';
            
            this.receivedRoomRequests.forEach(request => {
                const requestItem = this.createRoomRequestItem(request, true);
                receivedSection.appendChild(requestItem);
            });
            
            requestsDiv.appendChild(receivedSection);
        }
        
        // Display sent requests
        if (this.sentRoomRequests.length > 0) {
            const sentSection = document.createElement('div');
            sentSection.className = 'room-requests-section';
            sentSection.innerHTML = '<h4 style="margin: 15px 0 10px 0; color: #6c757d; border-bottom: 2px solid #6c757d; padding-bottom: 5px;">Sent Requests</h4>';
            
            this.sentRoomRequests.forEach(request => {
                const requestItem = this.createRoomRequestItem(request, false);
                sentSection.appendChild(requestItem);
            });
            
            requestsDiv.appendChild(sentSection);
        }
    },
    
    // Create room request item
    createRoomRequestItem: function(request, isReceived) {
        const requestItem = document.createElement('div');
        requestItem.className = 'room-request-item-room';
        
        const avatarImg = document.createElement('img');
        avatarImg.className = 'room-request-avatar-room';
        avatarImg.src = 'https://api.dicebear.com/7.x/identicon/svg?seed=' + encodeURIComponent(isReceived ? request.from : request.roomName);
        avatarImg.alt = isReceived ? request.from : request.roomName;
        
        const infoDiv = document.createElement('div');
        infoDiv.className = 'room-request-info-room';
        
        const nameDiv = document.createElement('div');
        nameDiv.className = 'room-request-username-room';
        nameDiv.textContent = isReceived ? request.from : request.roomName;
        
        const roomDiv = document.createElement('div');
        roomDiv.style.fontSize = '12px';
        roomDiv.style.color = '#6c757d';
        roomDiv.textContent = isReceived ? `Room: ${request.roomName}` : `Request sent to room`;
        
        const timeDiv = document.createElement('div');
        timeDiv.style.fontSize = '11px';
        timeDiv.style.color = '#6c757d';
        timeDiv.textContent = request.time;
        
        infoDiv.appendChild(nameDiv);
        infoDiv.appendChild(roomDiv);
        infoDiv.appendChild(timeDiv);
        
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'room-request-actions-room';
        
        if (isReceived) {
            if (request.status === 'PENDING') {
                // 检查当前用户是否为房主或管理员
                const isOwnerOrAdmin = this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
                
                if (isOwnerOrAdmin) {
                    const acceptBtn = document.createElement('button');
                    acceptBtn.className = 'room-request-btn-room request-join-btn';
                    acceptBtn.textContent = 'Accept';
                    acceptBtn.addEventListener('click', () => {
                        this.respondToRoomJoinRequest(request.from, request.roomName, 'accept');
                        request.status = 'ACCEPTED';
                        this.saveRoomRequests();
                        this.displayAllRoomRequests();
                    });
                    
                    const rejectBtn = document.createElement('button');
                    rejectBtn.className = 'room-request-btn-room request-cancel-btn';
                    rejectBtn.textContent = 'Reject';
                    rejectBtn.addEventListener('click', () => {
                        this.respondToRoomJoinRequest(request.from, request.roomName, 'reject');
                        request.status = 'REJECTED';
                        this.saveRoomRequests();
                        this.displayAllRoomRequests();
                    });
                    
                    actionsDiv.appendChild(acceptBtn);
                    actionsDiv.appendChild(rejectBtn);
                } else {
                    const infoLabel = document.createElement('span');
                    infoLabel.className = 'room-request-btn-room';
                    infoLabel.style.backgroundColor = '#6c757d';
                    infoLabel.style.color = 'white';
                    infoLabel.textContent = 'Wait for owner/admin';
                    actionsDiv.appendChild(infoLabel);
                }
            } else {
                const statusLabel = document.createElement('span');
                statusLabel.className = 'room-request-btn-room';
                statusLabel.style.backgroundColor = this.getStatusColor(request.status);
                statusLabel.style.color = 'white';
                statusLabel.textContent = this.getStatusText(request.status);
                actionsDiv.appendChild(statusLabel);
            }
        } else {
            const statusLabel = document.createElement('span');
            statusLabel.className = 'room-request-btn-room';
            statusLabel.style.backgroundColor = this.getStatusColor(request.status);
            statusLabel.style.color = 'white';
            statusLabel.textContent = this.getStatusText(request.status);
            actionsDiv.appendChild(statusLabel);
        }
        
        requestItem.appendChild(avatarImg);
        requestItem.appendChild(infoDiv);
        requestItem.appendChild(actionsDiv);
        
        return requestItem;
    },
    
    // Respond to room join request
    respondToRoomJoinRequest: function(fromUsername, roomName, response) {
        const message = {
            type: MessageType.ROOM_JOIN_RESPONSE,
            from: this.username,
            to: fromUsername,
            content: `${response}:${roomName}`,
            time: getLocalTime(),
            id: this.generateMessageId('ROOM_JOIN_RESPONSE', fromUsername)
        };
        
        this.ws.send(JSON.stringify(message));
        this.log('info', `Responded to room join request from ${fromUsername} for ${roomName}: ${response}`);
        
        // Don't remove from requests, just update status
        // The UI will be updated when server responds
    },
    
    // Handle room join response
    handleRoomJoinResponse: function(message) {
        const content = message.content;
        const fromUsername = message.from;
        
        if (content.startsWith('accept:')) {
            const roomName = content.substring(7);
            
            this.showMessage(`[Room] Your request to join ${roomName} was accepted!`, true);
            this.log('info', `Room join request accepted by ${fromUsername} for ${roomName}`);
            
            // Update sent request status
            const sentRequest = this.sentRoomRequests.find(req => req.roomName === roomName);
            if (sentRequest) {
                sentRequest.status = 'ACCEPTED';
                this.saveRoomRequests();
                this.displayAllRoomRequests();
            }
        } else if (content.startsWith('reject:')) {
            const roomName = content.substring(7);
            this.showMessage(`[Room] Your request to join ${roomName} was rejected.`, true);
            this.log('info', `Room join request rejected by ${fromUsername} for ${roomName}`);
            
            // Update sent request status
            const sentRequest = this.sentRoomRequests.find(req => req.roomName === roomName);
            if (sentRequest) {
                sentRequest.status = 'REJECTED';
                this.saveRoomRequests();
                this.displayAllRoomRequests();
            }
        }
    },
    
    // Handle set room admin response
    handleSetRoomAdminResponse: function(message) {
        const content = message.content;
        
        if (content.includes('已成功设置管理员')) {
            this.showMessage('[Room] Successfully set admin!', true);
            this.log('info', 'Set admin successful');
            
            // Refresh room users list
            if (this.currentRoom && this.currentRoom !== 'system') {
                this.sendMessage(MessageType.LIST_ROOM_USERS, this.currentRoom, '');
            }
        } else {
            this.showMessage('[Room] Failed to set admin: ' + content, true);
            this.log('error', 'Set admin failed: ' + content);
        }
    },
    
    // Handle remove room admin response
    handleRemoveRoomAdminResponse: function(message) {
        const content = message.content;
        
        if (content.includes('已成功移除管理员')) {
            this.showMessage('[Room] Successfully removed admin!', true);
            this.log('info', 'Remove admin successful');
            
            // Refresh room users list
            if (this.currentRoom && this.currentRoom !== 'system') {
                this.sendMessage(MessageType.LIST_ROOM_USERS, this.currentRoom, '');
            }
        } else {
            this.showMessage('[Room] Failed to remove admin: ' + content, true);
            this.log('error', 'Remove admin failed: ' + content);
        }
    },
    
    // Send private message to a specific user
    // 支持临时聊天和好友聊天两种模式
    sendPrivateMessage: function(to, content) {
        if (!to || !content) {
            this.showMessage('Please enter a username and message', true);
            return;
        }
        
        // 根据聊天类型确定消息类型
        // 临时聊天：只能发送文本消息
        // 好友聊天：可以发送文本、图片和文件（后续实现）
        let messageType = MessageType.PRIVATE_CHAT;
        
        // 使用新的PRIVATE_CHAT消息类型，直接发送私聊消息
        const message = {
            type: messageType,
            from: this.username,
            to: to,
            content: content,
            time: getLocalTime(),
            id: this.generateMessageId('PRIVATE_CHAT', to),
            isTemporaryChat: this.isTemporaryChat, // 标记是否为临时聊天
            isFriendChat: this.isFriendChat // 标记是否为好友聊天
        };
        
        // Send private message through WebSocket
        this.ws.send(JSON.stringify(message));
        
        // Store private message using recipient username as key
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
            isPrivate: true,
            id: message.id,
            type: MessageType.PRIVATE_CHAT
        };
        
        this.messages[privateRoomName].push(privateMessage);
        
        // 保存私聊消息到本地存储
        if (this.messageStorage) {
            this.saveMessageToLocal(privateRoomName, privateMessage);
        }
        
        // 同步私聊消息到其他窗口
        this.broadcastToWindows('NEW_MESSAGE', {
            roomName: privateRoomName,
            message: privateMessage
        }, privateRoomName);
        
        // Update UI if current room is this private conversation
        const currentRoomName = document.getElementById('current-room-name')?.textContent;
        if (currentRoomName === `与${to}聊天`) {
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
    
    // Open friend chat in new window
    openFriendChatInNewWindow: function(friendUsername) {
        this.log('info', `在新窗口打开好友聊天: ${friendUsername}`);
        
        const windowName = 'friend_' + friendUsername;
        
        if (this.childWindows[windowName] && !this.childWindows[windowName].closed) {
            this.childWindows[windowName].focus();
            this.log('debug', `好友聊天 ${friendUsername} 的窗口已存在，聚焦窗口`);
            return;
        }
        
        const newWindow = window.open(
            'friend-chat.jsp?friend=' + encodeURIComponent(friendUsername),
            windowName,
            'width=800,height=600'
        );
        
        if (newWindow) {
            this.childWindows[windowName] = newWindow;
            
            newWindow.addEventListener('beforeunload', () => {
                delete this.childWindows[windowName];
                this.log('debug', `好友聊天窗口关闭: ${friendUsername}`);
            });
            
            this.showMessage(`已打开好友 "${friendUsername}" 的新窗口`, true);
            this.log('info', `成功打开好友 ${friendUsername} 的新窗口`);
        } else {
            this.showMessage('无法打开新窗口，请检查浏览器弹窗设置', true);
            this.log('warn', `打开好友聊天 ${friendUsername} 新窗口失败: 弹窗被阻止`);
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
    },

    // 更新用户统计数据
    updateUserStats: function(stats) {
        // 更新消息数
        const messagesElement = document.getElementById('stat-messages');
        if (messagesElement) {
            messagesElement.textContent = stats.messageCount || 0;
        }
        
        const totalMessagesElement = document.getElementById('detail-total-messages');
        if (totalMessagesElement) {
            totalMessagesElement.textContent = stats.messageCount || 0;
        }
        
        // 更新房间数
        const roomsElement = document.getElementById('stat-rooms');
        if (roomsElement) {
            roomsElement.textContent = stats.roomCount || 0;
        }
        
        const roomsJoinedElement = document.getElementById('detail-rooms-joined');
        if (roomsJoinedElement) {
            roomsJoinedElement.textContent = stats.roomCount || 0;
        }
        
        // 更新加入时间
        const joinedElement = document.getElementById('detail-joined');
        if (joinedElement && stats.joinTime) {
            joinedElement.textContent = stats.joinTime;
        }
        
        // 更新图片数
        const imagesSentElement = document.getElementById('detail-images-sent');
        if (imagesSentElement) {
            imagesSentElement.textContent = stats.imageCount || 0;
        }
        
        // 更新文件数
        const filesSharedElement = document.getElementById('detail-files-shared');
        if (filesSharedElement) {
            filesSharedElement.textContent = stats.fileCount || 0;
        }
        
        this.log('info', '用户统计数据已更新');
    }
};

// Login functionality
function initLogin() {
    // Avatar upload functionality
    initAvatarUpload();
    
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
        
        // Check if avatar is selected
        const avatarInput = document.getElementById('register-avatar-input');
        const avatarPreview = document.getElementById('register-avatar-preview');
        const hasAvatar = avatarPreview.src && avatarPreview.classList.contains('show');
        
        // Save avatar file reference for upload after registration
        if (hasAvatar && avatarInput.files && avatarInput.files.length > 0) {
            localStorage.setItem('tempAvatarFile', JSON.stringify({
                name: avatarInput.files[0].name,
                size: avatarInput.files[0].size,
                type: avatarInput.files[0].type
            }));
            localStorage.setItem('tempAvatarData', avatarPreview.src);
        } else {
            localStorage.removeItem('tempAvatarFile');
            localStorage.removeItem('tempAvatarData');
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

// Avatar upload functionality
function initAvatarUpload() {
    const avatarInput = document.getElementById('register-avatar-input');
    const avatarPreview = document.getElementById('register-avatar-preview');
    const uploadAvatarBtn = document.getElementById('upload-avatar-btn');
    const removeAvatarBtn = document.getElementById('remove-avatar-btn');
    const avatarPreviewWrapper = document.querySelector('.avatar-preview-wrapper');
    
    if (!avatarInput || !avatarPreview) return;
    
    // Click on placeholder or upload button to open file dialog
    if (avatarPreviewWrapper) {
        avatarPreviewWrapper.addEventListener('click', function() {
            avatarInput.click();
        });
    }
    
    if (uploadAvatarBtn) {
        uploadAvatarBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            avatarInput.click();
        });
    }
    
    // Handle file selection
    avatarInput.addEventListener('change', function(e) {
        if (e.target.files && e.target.files.length > 0) {
            const file = e.target.files[0];
            
            // Validate file type
            if (!file.type.startsWith('image/')) {
                alert('Please select an image file');
                return;
            }
            
            // Validate file size (max 2MB)
            if (file.size > 2 * 1024 * 1024) {
                alert('Image size must be less than 2MB');
                return;
            }
            
            // Read and preview the image
            const reader = new FileReader();
            reader.onload = function(event) {
                avatarPreview.src = event.target.result;
                avatarPreview.classList.add('show');
                
                if (removeAvatarBtn) {
                    removeAvatarBtn.style.display = 'inline-block';
                }
            };
            reader.readAsDataURL(file);
        }
    });
    
    // Remove avatar
    if (removeAvatarBtn) {
        removeAvatarBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            avatarPreview.src = '';
            avatarPreview.classList.remove('show');
            avatarInput.value = '';
            removeAvatarBtn.style.display = 'none';
        });
    }
}

// Upload avatar to server
function uploadAvatarToServer(username, file, callback) {
    const zfileServerUrl = chatClient.serviceConfig.zfileServerUrl || chatClient.zfileServerUrl || 'http://localhost:8081';
    
    // 生成文件扩展名
    const fileName = file.name;
    const fileExtension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    
    // 构建上传路径
    const uploadPath = `/avatars/users/${username}`;
    const uploadFileName = `avatar${fileExtension}`;
    
    // 第一步：请求上传token
    const timeout = setTimeout(() => {
        callback(false, '获取token超时');
    }, 10000);
    
    const originalHandleTokenResponse = chatClient.handleTokenResponse;
    chatClient.handleTokenResponse = function(message) {
        clearTimeout(timeout);
        chatClient.handleTokenResponse = originalHandleTokenResponse;
        originalHandleTokenResponse.call(chatClient, message);
        
        // 第二步：创建上传任务（使用获取的token）
        const createUploadUrl = `${zfileServerUrl}/api/file/operator/upload/file`;
        
        fetch(createUploadUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Zfile-Token': chatClient.uploadToken,
                'Axios-Request': 'true',
                'Axios-From': zfileServerUrl
            },
            body: JSON.stringify({
                storageKey: 'chatroom-files',
                path: uploadPath,
                name: uploadFileName,
                size: file.size,
                password: ''
            })
        })
        .then(response => response.json())
        .then(data => {
            if (data && data.code === '0' && data.data) {
                let uploadUrl = data.data;
                
                // 修正URL以匹配配置的zfile服务器
                try {
                    const urlObj = new URL(uploadUrl);
                    const configUrlObj = new URL(zfileServerUrl);
                    urlObj.protocol = configUrlObj.protocol;
                    urlObj.host = configUrlObj.host;
                    uploadUrl = urlObj.toString();
                } catch (error) {
                    console.warn('修正上传URL失败:', error.message);
                }
                
                // 第三步：实际上传文件
                const formData = new FormData();
                formData.append('file', file);
                
                return fetch(uploadUrl, {
                    method: 'PUT',
                    headers: {
                        'Zfile-Token': chatClient.uploadToken,
                        'Axios-Request': 'true',
                        'Axios-From': zfileServerUrl
                    },
                    body: formData
                })
                .then(response => response.json())
                .then(data => {
                    if (data && data.code === '0') {
                        // 返回相对路径
                        const relativePath = `/pd/chatroom-files/chatroom${uploadPath}/${encodeURIComponent(uploadFileName)}`;
                        callback(true, relativePath);
                    } else {
                        callback(false, '上传失败');
                    }
                });
            } else {
                callback(false, '创建上传任务失败');
            }
        })
        .catch(error => {
            console.error('上传头像失败:', error);
            callback(false, '网络错误');
        });
    };
    
    // 发送token请求
    chatClient.sendMessage(MessageType.REQUEST_TOKEN, 'server', '');
}

// Get avatar URL from zfile server
function getAvatarUrl(username) {
    const zfileServerUrl = chatClient.serviceConfig.zfileServerUrl || 'http://localhost:8081';
    const avatarPath = `/pd/chatroom-files/chatroom/avatars/users/${username}/avatar`;
    
    // 尝试常见的图片扩展名
    const extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];
    
    // 返回第一个可能的URL（实际加载时会尝试所有扩展名）
    return `${zfileServerUrl}${avatarPath}${extensions[0]}`;
}

// Load user avatar with fallback
function loadUserAvatar(imgElement, username) {
    const zfileServerUrl = chatClient.serviceConfig.zfileServerUrl || 'http://localhost:8081';
    const avatarPath = `/pd/chatroom-files/chatroom/avatars/users/${username}/avatar`;
    const extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];
    
    let currentExtensionIndex = 0;
    
    const tryLoadAvatar = (index) => {
        if (index >= extensions.length) {
            // 所有扩展名都尝试失败，显示默认头像
            const firstLetter = username.charAt(0).toUpperCase();
            const defaultAvatar = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"%3E%3Ccircle cx="50" cy="50" r="45" fill="%234a6fa5"/%3E%3Ctext x="50" y="60" font-size="40" text-anchor="middle" fill="white"%3E' + firstLetter + '%3C/text%3E%3C/svg%3E';
            imgElement.src = defaultAvatar;
            imgElement.style.opacity = '1';
            return;
        }
        
        const avatarUrl = `${zfileServerUrl}${avatarPath}${extensions[index]}`;
        imgElement.style.opacity = '0.5';
        imgElement.src = avatarUrl;
        
        imgElement.onload = function() {
            imgElement.style.opacity = '1';
        };
        
        imgElement.onerror = function() {
            // 尝试下一个扩展名
            tryLoadAvatar(index + 1);
        };
    };
    
    tryLoadAvatar(0);
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
    
    // Initialize user menu
    initUserMenu();
    
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
        if (chatClient.isTemporaryChat) {
            chatClient.showMessage('Temporary chat only supports text messages', true);
            return;
        }
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
    
    // File upload button functionality
    document.getElementById('file-btn').addEventListener('click', function() {
        if (chatClient.isTemporaryChat) {
            chatClient.showMessage('Temporary chat only supports text messages', true);
            return;
        }
        const fileInput = document.getElementById('file-input');
        fileInput.click();
    });
    
    document.getElementById('file-input').addEventListener('change', function(e) {
        if (e.target.files && e.target.files.length > 0) {
            const file = e.target.files[0];
            chatClient.handleFileUpload(file);
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
        chatClient.openRoomSearchModal();
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
    const modalNsfwToggleBtn = document.getElementById('modal-nsfw-toggle-btn');
    
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
                        const decryptedUrl = await chatClient.decryptImage(encryptedUrl, iv);
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
    
    window.openFileModal = async function(fileUrl, fileName, fileType, isNSFW = false) {
        let fileModal = document.getElementById('file-modal');
        const previewContainerId = 'file-preview-container';
        
        if (!fileModal || !document.getElementById(previewContainerId)) {
            const existingModal = document.getElementById('file-modal');
            if (existingModal) {
                existingModal.remove();
            }
            
            const modalHtml = `
                <div id="file-modal" class="modal">
                    <div class="modal-content file-modal-content">
                        <span class="close" onclick="document.getElementById('file-modal').style.display='none'">&times;</span>
                        <h3 id="file-title">${fileName}</h3>
                        <div id="file-loading" style="text-align: center; padding: 20px;">加载中...</div>
                        <div id="${previewContainerId}"></div>
                        <pre id="file-content" class="file-content-display"></pre>
                    </div>
                </div>
                <div id="office-preview-mask" hidden>
                    <div id="office-preview">
                        <span class="close" onclick="document.getElementById('office-preview-mask').style.display='none'">&times;</span>
                        <div id="office-body"></div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            fileModal = document.getElementById('file-modal');
        }
        
        const fileContent = document.getElementById('file-content');
        const fileTitle = document.getElementById('file-title');
        const previewContainer = document.getElementById(previewContainerId);
        
        fileTitle.textContent = fileName;
        fileContent.textContent = '';
        fileContent.style.display = 'none';
        previewContainer.innerHTML = '';
        previewContainer.style.display = 'none';
        document.getElementById('file-loading').style.display = 'block';
        fileModal.style.display = 'block';
        
        const fileExtension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        const canPreviewWithOnlyOffice = chatClient.canPreviewWithOnlyOffice(fileExtension);
        
        console.log('文件名:', fileName);
        console.log('文件扩展名:', fileExtension);
        console.log('是否支持OnlyOffice预览:', canPreviewWithOnlyOffice);
        console.log('是否NSFW:', isNSFW);
        console.log('文件类型:', fileType);
        
        try {
            if (canPreviewWithOnlyOffice && !isNSFW) {
                console.log('进入OnlyOffice预览分支');
                
                const urlObj = new URL(fileUrl);
                let filePath = urlObj.pathname;
                
                if (filePath.startsWith('/pd/chatroom-files/chatroom')) {
                    filePath = filePath.replace('/pd/chatroom-files/chatroom', '');
                }
                
                const pathParts = filePath.split('/');
                const encodedFileName = pathParts[pathParts.length - 1];
                const decodedFileName = decodeURIComponent(encodedFileName);
                pathParts[pathParts.length - 1] = decodedFileName;
                filePath = pathParts.join('/');
                
                const configData = {
                    storageKey: 'chatroom-files',
                    path: filePath,
                    password: ''
                };
                
                console.log('原始文件URL:', fileUrl);
                console.log('OnlyOffice配置请求:', configData);
                
                try {
                    document.getElementById('file-loading').textContent = '正在获取访问权限...';
                    console.log('开始获取token');
                    await chatClient.getViewToken();
                    console.log('Token获取成功:', chatClient.uploadToken);
                    document.getElementById('file-loading').textContent = '加载中...';
                    
                    console.log('开始请求OnlyOffice配置');
                    const onlyOfficeConfigUrl = `${chatClient.serviceConfig.zfileServerUrl || 'http://localhost:8081'}/onlyOffice/config/token`;
                    const configResponse = await fetch(onlyOfficeConfigUrl, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Zfile-Token': chatClient.uploadToken || ''
                        },
                        body: JSON.stringify(configData)
                    });
                    
                    console.log('OnlyOffice配置响应状态:', configResponse.status);
                    
                    if (!configResponse.ok) {
                        throw new Error(`配置请求失败: ${configResponse.status}`);
                    }
                    
                    const configResult = await configResponse.json();
                    
                    console.log('OnlyOffice配置响应:', configResult);
                    
                    if (configResult.code !== '0') {
                        throw new Error(`配置错误: ${configResult.msg}`);
                    }
                    
                    const config = configResult.data;
                    
                    console.log('OnlyOffice配置:', JSON.stringify(config, null, 2));
                    
                    if (config.document && config.document.url) {
                        console.log('文档URL:', config.document.url);
                        
                        const testUrl = new URL(config.document.url);
                        console.log('文档URL主机:', testUrl.hostname);
                        console.log('文档URL路径:', testUrl.pathname);
                    }
                    
                    console.log('开始初始化OnlyOffice编辑器');
                    
                    console.log('测试文档URL是否可访问...');
                    try {
                        const testResponse = await fetch(config.document.url, { method: 'HEAD' });
                        console.log('文档URL访问状态:', testResponse.status);
                    } catch (error) {
                        console.error('文档URL访问失败:', error);
                    }
                    
                    if (typeof DocsAPI === 'undefined') {
                        console.log('OnlyOffice API未加载，开始加载...');
                        const onlyOfficeApiUrl = chatClient.serviceConfig.onlyOfficeApiUrl || 'http://localhost:8082/web-apps/apps/api/documents/api.js';
                        await new Promise((resolve, reject) => {
                            const script = document.createElement('script');
                            script.type = 'text/javascript';
                            script.charset = 'UTF-8';
                            script.src = onlyOfficeApiUrl;
                            
                            script.addEventListener('load', () => {
                                console.log('OnlyOffice API加载成功');
                                resolve();
                            }, false);
                            
                            script.addEventListener('error', () => {
                                console.error('OnlyOffice API加载失败');
                                reject(new Error('加载OnlyOffice API失败'));
                            }, false);
                            
                            document.head.appendChild(script);
                        });
                    } else {
                        console.log('OnlyOffice API已加载');
                    }
                    
                    console.log('开始创建DocEditor实例');
                    try {
                        console.log('当前页面URL:', window.location.href);
                        console.log('当前页面Origin:', window.location.origin);
                        console.log('当前页面protocol:', window.location.protocol);
                        console.log('当前页面host:', window.location.host);
                        
                        const documentUrl = new URL(config.document.url);
                        console.log('文档URL hostname:', documentUrl.hostname);
                        console.log('文档URL protocol:', documentUrl.protocol);
                        
                        let officePreviewMask = document.getElementById('office-preview-mask');
                        if (officePreviewMask) {
                            officePreviewMask.remove();
                        }
                        
                        const modalHtml = `
                            <div id="office-preview-mask">
                                <div id="office-preview">
                                    <span class="close" onclick="document.getElementById('office-preview-mask').style.display='none'">&times;</span>
                                    <div id="office-body"></div>
                                </div>
                            </div>
                        `;
                        document.body.insertAdjacentHTML('beforeend', modalHtml);
                        
                        officePreviewMask = document.getElementById('office-preview-mask');
                        const officePreview = document.getElementById('office-preview');
                        const officeBody = document.getElementById('office-body');
                        
                        officePreviewMask.style.display = 'block';
                        
                        console.log('已创建并显示OnlyOffice预览容器');
                        
                        new DocsAPI.DocEditor('office-body', config);
                        console.log('DocEditor实例创建成功');
                        
                        console.log('等待2秒后检查office-body元素...');
                        setTimeout(() => {
                            console.log('开始检查office-body元素...');
                            if (officeBody) {
                                console.log('office-body元素存在');
                                console.log('office-body内容:', officeBody.innerHTML.substring(0, 500));
                                console.log('office-body子元素数量:', officeBody.children.length);
                                
                                if (officeBody.children.length > 0) {
                                    for (let i = 0; i < officeBody.children.length; i++) {
                                        console.log(`子元素${i}:`, officeBody.children[i].tagName, officeBody.children[i].className);
                                    }
                                }
                                
                                const iframe = officeBody.querySelector('iframe');
                                if (iframe) {
                                    console.log('OnlyOffice iframe已创建');
                                    console.log('iframe src:', iframe.src);
                                    console.log('iframe width:', iframe.width);
                                    console.log('iframe height:', iframe.height);
                                    console.log('iframe computed width:', window.getComputedStyle(iframe).width);
                                    console.log('iframe computed height:', window.getComputedStyle(iframe).height);
                                    console.log('office-body computed width:', window.getComputedStyle(officeBody).width);
                                    console.log('office-body computed height:', window.getComputedStyle(officeBody).height);
                                    
                                    const urlParams = new URLSearchParams(new URL(iframe.src).search);
                                    console.log('iframe parentOrigin:', urlParams.get('parentOrigin'));
                                    
                                    setTimeout(() => {
                                        console.log('5秒后再次检查iframe...');
                                        console.log('iframe当前src:', iframe.src);
                                        console.log('iframe当前width:', iframe.width);
                                        console.log('iframe currentheight:', iframe.height);
                                    }, 5000);
                                } else {
                                    console.log('未找到iframe，检查是否有其他元素');
                                }
                            } else {
                                console.log('office-body元素不存在');
                            }
                        }, 2000);
                    } catch (error) {
                        console.error('DocEditor实例创建失败:', error);
                        throw error;
                    }
                    
                    document.getElementById('file-loading').style.display = 'none';
                    fileModal.style.display = 'none';
                    
                    console.log('file-modal已隐藏');
                    
                    chatClient.uploadToken = null;
                } catch (error) {
                    console.error('OnlyOffice预览失败:', error);
                    document.getElementById('file-loading').style.display = 'none';
                    previewContainer.innerHTML = `
                        <div style="text-align: center; padding: 20px; color: #dc3545;">
                            <p>预览失败: ${error.message}</p>
                            <p style="font-size: 12px; margin-top: 10px;">请检查OnlyOffice服务是否正常运行</p>
                        </div>
                    `;
                    previewContainer.style.display = 'block';
                }
            } else {
                const response = await fetch(fileUrl);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const text = await response.text();
                
                document.getElementById('file-loading').style.display = 'none';
                
                if (isNSFW && (fileType === 'text' || fileType === 'code')) {
                    const lines = text.split('\n');
                    const previewLines = lines.slice(0, 10).join('\n');
                    const totalLines = lines.length;
                    const isExpandable = totalLines > 10;
                    
                    const language = getPrismLanguage(fileName);
                    
                    previewContainer.innerHTML = `
                        <div class="file-preview-content">
                            <div class="file-preview-summary" id="nsfw-preview-summary">
                                <pre class="file-preview-text">${escapeHtml(previewLines)}</pre>
                                ${isExpandable ? `<div class="file-preview-truncated">... 共 ${totalLines} 行，点击展开查看完整内容</div>` : ''}
                            </div>
                            ${isExpandable ? `
                                <div class="file-preview-full" id="nsfw-preview-full" style="display: none;">
                                    <pre class="file-preview-text language-${language}">${escapeHtml(text)}</pre>
                                </div>
                            ` : ''}
                        </div>
                    `;
                    previewContainer.style.display = 'block';
                    
                    if (isExpandable) {
                        const summaryDiv = document.getElementById('nsfw-preview-summary');
                        const fullDiv = document.getElementById('nsfw-preview-full');
                        
                        summaryDiv.addEventListener('click', function() {
                            if (fullDiv.style.display === 'none') {
                                fullDiv.style.display = 'block';
                                summaryDiv.style.display = 'none';
                                if (typeof Prism !== 'undefined') {
                                    Prism.highlightElement(fullDiv.querySelector('pre'));
                                }
                            }
                        });
                        
                        fullDiv.addEventListener('click', function() {
                            summaryDiv.style.display = 'block';
                            fullDiv.style.display = 'none';
                        });
                    }
                } else {
                    if (fileType === 'code') {
                        const language = getPrismLanguage(fileName);
                        fileContent.className = `file-content-display language-${language}`;
                        fileContent.textContent = text;
                        if (typeof Prism !== 'undefined') {
                            Prism.highlightElement(fileContent);
                        }
                    } else {
                        fileContent.className = 'file-content-display';
                        fileContent.textContent = text;
                    }
                    
                    fileContent.style.display = 'block';
                }
            }
        } catch (error) {
            console.error('加载文件失败:', error);
            fileContent.textContent = '加载失败: ' + error.message;
            document.getElementById('file-loading').style.display = 'none';
            fileContent.style.display = 'block';
        }
    };
    
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    function getPrismLanguage(fileName) {
        const extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        const languageMap = {
            '.js': 'javascript',
            '.ts': 'typescript',
            '.java': 'java',
            '.py': 'python',
            '.c': 'c',
            '.cpp': 'cpp',
            '.h': 'c',
            '.hpp': 'cpp',
            '.cs': 'csharp',
            '.php': 'php',
            '.rb': 'ruby',
            '.go': 'go',
            '.rs': 'rust',
            '.swift': 'swift',
            '.kt': 'kotlin',
            '.scala': 'scala',
            '.groovy': 'groovy',
            '.dart': 'dart',
            '.lua': 'lua',
            '.r': 'r',
            '.m': 'objectivec',
            '.swift': 'swift',
            '.pl': 'perl',
            '.sh': 'bash',
            '.bash': 'bash',
            '.zsh': 'bash',
            '.ps1': 'powershell',
            '.sql': 'sql',
            '.html': 'html',
            '.htm': 'html',
            '.xml': 'xml',
            '.css': 'css',
            '.scss': 'scss',
            '.sass': 'sass',
            '.less': 'less',
            '.json': 'json',
            '.yaml': 'yaml',
            '.yml': 'yaml',
            '.toml': 'toml',
            '.ini': 'ini',
            '.conf': 'ini',
            '.cfg': 'ini',
            '.md': 'markdown',
            '.tex': 'latex',
            '.vue': 'vue',
            '.jsx': 'jsx',
            '.tsx': 'tsx',
            '.tsv': 'tsv',
            '.csv': 'csv',
            '.dockerfile': 'docker',
            '.docker': 'docker',
            '.makefile': 'makefile',
            '.cmake': 'cmake',
            '.gradle': 'gradle',
            '.maven': 'xml',
            '.pom': 'xml',
            '.gitignore': 'ignore',
            '.gitattributes': 'ignore',
            '.editorconfig': 'ini',
            '.eslintrc': 'json',
            '.prettierrc': 'json',
            '.babelrc': 'json',
            '.tsconfig': 'json',
            '.package': 'json',
            '.lock': 'json'
        };
        
        return languageMap[extension] || 'plaintext';
    }
    
    // Function to toggle NSFW image visibility
    window.toggleNSFWImage = async function(wrapperId) {
        const wrapper = document.getElementById(wrapperId);
        if (!wrapper) return;
        
        const img = wrapper.querySelector('img');
        const btn = wrapper.querySelector('.nsfw-toggle-btn');
        
        if (img.classList.contains('showing')) {
            img.classList.remove('showing');
            btn.textContent = '显示NSFW内容';
            btn.classList.remove('minimized');
        } else {
            const iv = img.getAttribute('data-iv');
            
            if (iv) {
                btn.textContent = '解密中...';
                btn.disabled = true;
                
                try {
                    const encryptedUrl = img.getAttribute('data-encrypted-url') || img.src;
                    const decryptedUrl = await chatClient.decryptImage(encryptedUrl, iv);
                    img.src = decryptedUrl;
                    img.classList.add('showing');
                    
                    btn.textContent = '隐藏';
                    btn.classList.add('minimized');
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
                btn.textContent = '隐藏';
                btn.classList.add('minimized');
            }
        }
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
    
    // Add friend button functionality
    document.getElementById('add-friend-btn').addEventListener('click', function() {
        chatClient.openUserSearchModal();
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

// User Menu Functionality
function initUserMenu() {
    const userMenuBtn = document.getElementById('user-menu-btn');
    const userMenuDropdown = document.getElementById('user-menu-dropdown');
    const userAvatar = document.getElementById('user-avatar');
    
    if (userAvatar) {
        const username = sessionStorage.getItem('username') || localStorage.getItem('username');
        if (username) {
            loadUserAvatar(userAvatar, username);
        }
    }
    
    if (userMenuBtn && userMenuDropdown) {
        userMenuBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            userMenuDropdown.classList.toggle('show');
        });
        
        document.addEventListener('click', function(e) {
            if (!userMenuDropdown.contains(e.target) && !userMenuBtn.contains(e.target)) {
                userMenuDropdown.classList.remove('show');
            }
        });
        
        const viewProfileBtn = document.getElementById('view-profile-btn');
        const viewSettingsBtn = document.getElementById('view-settings-btn');
        const logoutBtn = document.getElementById('logout-btn');
        
        if (viewProfileBtn) {
            viewProfileBtn.addEventListener('click', function() {
                window.location.href = 'user-profile.jsp';
            });
        }
        
        if (viewSettingsBtn) {
            viewSettingsBtn.addEventListener('click', function() {
                window.location.href = 'user-settings.jsp';
            });
        }
        
        if (logoutBtn) {
            logoutBtn.addEventListener('click', function() {
                if (confirm('Are you sure you want to logout?')) {
                    if (chatClient.ws && chatClient.ws.readyState === WebSocket.OPEN) {
                        chatClient.ws.close();
                    }
                    sessionStorage.clear();
                    window.location.href = 'login.jsp';
                }
            });
        }
    }
}

// User Profile Page Functionality
function initUserProfile() {
    const backToChatBtn = document.getElementById('back-to-chat-btn');
    
    if (backToChatBtn) {
        backToChatBtn.addEventListener('click', function() {
            window.location.href = 'chat.jsp';
        });
    }
    
    const editProfileBtn = document.getElementById('edit-profile-btn');
    const viewSettingsBtn = document.getElementById('view-settings-btn');
    const changeAvatarBtn = document.getElementById('change-avatar-btn');
    const avatarInput = document.getElementById('avatar-input');
    
    if (editProfileBtn) {
        editProfileBtn.addEventListener('click', function() {
            window.location.href = 'user-settings.jsp';
        });
    }
    
    if (viewSettingsBtn) {
        viewSettingsBtn.addEventListener('click', function() {
            window.location.href = 'user-settings.jsp';
        });
    }
    
    if (changeAvatarBtn && avatarInput) {
        changeAvatarBtn.addEventListener('click', function() {
            avatarInput.click();
        });
        
        avatarInput.addEventListener('change', function(e) {
            if (e.target.files && e.target.files.length > 0) {
                const file = e.target.files[0];
                const username = sessionStorage.getItem('username');
                
                if (username) {
                    uploadAvatarToServer(username, file, function(success, result) {
                        if (success) {
                            alert('Avatar updated successfully!');
                            loadUserAvatar(document.getElementById('profile-avatar'), username);
                        } else {
                            alert('Avatar update failed: ' + result);
                        }
                    });
                }
            }
        });
    }
    
    loadUserProfile();
}

function loadUserProfile() {
    const username = sessionStorage.getItem('username');
    
    if (username) {
        document.getElementById('profile-username').textContent = username;
        document.getElementById('detail-username').textContent = username;
        
        const displayName = localStorage.getItem('displayName') || username;
        document.getElementById('profile-display-name').textContent = displayName;
        document.getElementById('detail-display-name').textContent = displayName;
        
        const email = localStorage.getItem('email') || 'Not set';
        document.getElementById('detail-email').textContent = email;
        
        const joinedDate = localStorage.getItem('joinedDate') || new Date().toLocaleDateString();
        document.getElementById('detail-joined').textContent = joinedDate;
        
        const lastActive = new Date().toLocaleString();
        document.getElementById('detail-last-active').textContent = lastActive;
        
        const totalMessages = localStorage.getItem('totalMessages') || '0';
        document.getElementById('stat-messages').textContent = totalMessages;
        document.getElementById('detail-total-messages').textContent = totalMessages;
        
        const roomsJoined = localStorage.getItem('roomsJoined') || '0';
        document.getElementById('stat-rooms').textContent = roomsJoined;
        document.getElementById('detail-rooms-joined').textContent = roomsJoined;
        
        const imagesSent = localStorage.getItem('imagesSent') || '0';
        document.getElementById('detail-images-sent').textContent = imagesSent;
        
        const filesShared = localStorage.getItem('filesShared') || '0';
        document.getElementById('detail-files-shared').textContent = filesShared;
        
        const onlineTime = localStorage.getItem('onlineTime') || '0';
        document.getElementById('stat-online-time').textContent = onlineTime + 'h';
        
        // Load avatar from server
        const profileAvatar = document.getElementById('profile-avatar');
        if (profileAvatar) {
            loadUserAvatar(profileAvatar, username);
        }
        
        // 注意：用户统计数据会在WebSocket认证成功后自动请求（在handleUUIDAuthSuccess中）
    }
}

// User Settings Page Functionality
function initUserSettings() {
    const backToChatBtn = document.getElementById('back-to-chat-btn');
    
    if (backToChatBtn) {
        backToChatBtn.addEventListener('click', function() {
            window.location.href = 'chat.jsp';
        });
    }
    
    initSettingsTabs();
    loadSettings();
    initSettingsActions();
}

function initSettingsTabs() {
    const tabBtns = document.querySelectorAll('.settings-tab-btn');
    const tabContents = document.querySelectorAll('.settings-tab-content');
    
    tabBtns.forEach(btn => {
        btn.addEventListener('click', function() {
            const tabName = this.getAttribute('data-tab');
            
            tabBtns.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));
            
            this.classList.add('active');
            document.getElementById('tab-' + tabName).classList.add('active');
        });
    });
}

function loadSettings() {
    const settings = JSON.parse(localStorage.getItem('userSettings')) || {};
    
    document.getElementById('theme-select').value = settings.theme || 'light';
    document.getElementById('font-size-select').value = settings.fontSize || 'medium';
    document.getElementById('bubble-style-select').value = settings.bubbleStyle || 'rounded';
    document.getElementById('show-timestamps').checked = settings.showTimestamps !== false;
    document.getElementById('desktop-notifications').checked = settings.desktopNotifications || false;
    document.getElementById('sound-notifications').checked = settings.soundNotifications !== false;
    document.getElementById('message-preview').checked = settings.messagePreview !== false;
    document.getElementById('mute-all').checked = settings.muteAll || false;
    document.getElementById('show-online-status').checked = settings.showOnlineStatus !== false;
    document.getElementById('read-receipts').checked = settings.readReceipts !== false;
    document.getElementById('profile-visibility').value = settings.profileVisibility || 'everyone';
    document.getElementById('allow-nsfw').checked = settings.allowNsfw || false;
    document.getElementById('message-storage').checked = settings.messageStorage !== false;
    document.getElementById('storage-type').value = settings.storageType || 'indexeddb';
    document.getElementById('max-messages').value = settings.maxMessages || '200';
    document.getElementById('sync-interval').value = settings.syncInterval || '30';
    
    applyTheme(settings.theme || 'light');
    applyFontSize(settings.fontSize || 'medium');
}

function saveSettings() {
    const settings = {
        theme: document.getElementById('theme-select').value,
        fontSize: document.getElementById('font-size-select').value,
        bubbleStyle: document.getElementById('bubble-style-select').value,
        showTimestamps: document.getElementById('show-timestamps').checked,
        desktopNotifications: document.getElementById('desktop-notifications').checked,
        soundNotifications: document.getElementById('sound-notifications').checked,
        messagePreview: document.getElementById('message-preview').checked,
        muteAll: document.getElementById('mute-all').checked,
        showOnlineStatus: document.getElementById('show-online-status').checked,
        readReceipts: document.getElementById('read-receipts').checked,
        profileVisibility: document.getElementById('profile-visibility').value,
        allowNsfw: document.getElementById('allow-nsfw').checked,
        messageStorage: document.getElementById('message-storage').checked,
        storageType: document.getElementById('storage-type').value,
        maxMessages: document.getElementById('max-messages').value,
        syncInterval: document.getElementById('sync-interval').value
    };
    
    localStorage.setItem('userSettings', JSON.stringify(settings));
    
    applyTheme(settings.theme);
    applyFontSize(settings.fontSize);
    
    alert('Settings saved successfully!');
}

function resetSettings() {
    if (confirm('Are you sure you want to reset all settings to default?')) {
        localStorage.removeItem('userSettings');
        loadSettings();
        alert('Settings reset to default!');
    }
}

function applyTheme(theme) {
    document.body.className = '';
    
    if (theme === 'dark') {
        document.body.classList.add('dark-theme');
    } else if (theme === 'auto') {
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            document.body.classList.add('dark-theme');
        }
    }
}

function applyFontSize(fontSize) {
    document.body.style.fontSize = '';
    
    if (fontSize === 'small') {
        document.body.style.fontSize = '14px';
    } else if (fontSize === 'large') {
        document.body.style.fontSize = '18px';
    } else {
        document.body.style.fontSize = '16px';
    }
}

function initSettingsActions() {
    const saveSettingsBtn = document.getElementById('save-settings-btn');
    const resetSettingsBtn = document.getElementById('reset-settings-btn');
    const changePasswordBtn = document.getElementById('change-password-btn');
    const changeDisplayNameBtn = document.getElementById('change-display-name-btn');
    const changeEmailBtn = document.getElementById('change-email-btn');
    const deleteAccountBtn = document.getElementById('delete-account-btn');
    const clearDataBtn = document.getElementById('clear-data-btn');
    
    if (saveSettingsBtn) {
        saveSettingsBtn.addEventListener('click', saveSettings);
    }
    
    if (resetSettingsBtn) {
        resetSettingsBtn.addEventListener('click', resetSettings);
    }
    
    if (changePasswordBtn) {
        changePasswordBtn.addEventListener('click', function() {
            const newPassword = prompt('Enter new password:');
            if (newPassword && newPassword.length >= 6) {
                localStorage.setItem('password', newPassword);
                alert('Password changed successfully!');
            } else if (newPassword) {
                alert('Password must be at least 6 characters long!');
            }
        });
    }
    
    if (changeDisplayNameBtn) {
        changeDisplayNameBtn.addEventListener('click', function() {
            const newDisplayName = prompt('Enter new display name:');
            if (newDisplayName && newDisplayName.trim()) {
                localStorage.setItem('displayName', newDisplayName.trim());
                alert('Display name changed successfully!');
            }
        });
    }
    
    if (changeEmailBtn) {
        changeEmailBtn.addEventListener('click', function() {
            const newEmail = prompt('Enter new email address:');
            if (newEmail && newEmail.includes('@')) {
                localStorage.setItem('email', newEmail);
                alert('Email changed successfully!');
            } else if (newEmail) {
                alert('Please enter a valid email address!');
            }
        });
    }
    
    if (deleteAccountBtn) {
        deleteAccountBtn.addEventListener('click', function() {
            if (confirm('Are you sure you want to delete your account? This action cannot be undone!')) {
                if (confirm('This will permanently delete all your data. Are you absolutely sure?')) {
                    localStorage.clear();
                    sessionStorage.clear();
                    alert('Account deleted successfully!');
                    window.location.href = 'login.jsp';
                }
            }
        });
    }
    
    if (clearDataBtn) {
        clearDataBtn.addEventListener('click', function() {
            if (confirm('Are you sure you want to clear all locally stored data?')) {
                localStorage.clear();
                alert('All data cleared successfully!');
                loadSettings();
            }
        });
    }
    
    const desktopNotificationsCheckbox = document.getElementById('desktop-notifications');
    if (desktopNotificationsCheckbox) {
        desktopNotificationsCheckbox.addEventListener('change', function() {
            if (this.checked) {
                if ('Notification' in window) {
                    Notification.requestPermission().then(permission => {
                        if (permission !== 'granted') {
                            alert('Notification permission denied!');
                            this.checked = false;
                        }
                    });
                } else {
                    alert('Your browser does not support notifications!');
                    this.checked = false;
                }
            }
        });
    }
}