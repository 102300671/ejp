// 客户端消息持久化存储管理
// 确保MessageStorage在全局作用域可用
const MessageStorage = {
    // 添加一个简单的测试方法
    test: function() {
        console.log('MessageStorage.test() called');
        return 'MessageStorage is working!';
    },
    // IndexedDB数据库名称和版本
    DB_NAME: 'ChatMessageDB',
    DB_VERSION: 3,
    STORE_NAME: 'messages',
    LAST_SYNC_STORE: 'lastSync',
    
    // 检查IndexedDB是否可用
    isIndexedDBSupported: function() {
        return !!(window.indexedDB || window.webkitIndexedDB || window.mozIndexedDB || window.msIndexedDB);
    },
    
    // ========== 备用存储机制（使用localStorage） ==========
    // 当IndexedDB不可用时，使用localStorage作为备用存储
    
    // 获取备用存储中的所有消息
    getBackupMessages: function(roomName) {
        try {
            const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
            return allMessages[roomName] || [];
        } catch (e) {
            console.error('Error reading backup messages:', e);
            return [];
        }
    },
    
    // 保存消息到备用存储
    saveBackupMessage: function(message) {
        try {
            const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
            const roomName = message.roomName;
            
            if (!allMessages[roomName]) {
                allMessages[roomName] = [];
            }
            
            // 检查消息是否已存在
            const exists = allMessages[roomName].some(m => m.id === message.id);
            if (!exists) {
                allMessages[roomName].push({
                    id: message.id,
                    roomName: message.roomName,
                    from: message.from,
                    to: message.to,
                    content: message.content,
                    createTime: message.createTime,
                    type: message.type,
                    messageType: message.messageType,
                    isSystem: message.isSystem || false,
                    isNSFW: message.isNSFW || false,
                    iv: message.iv || null
                });
                
                // 限制每个房间最多保存200条消息
                if (allMessages[roomName].length > 200) {
                    allMessages[roomName] = allMessages[roomName].slice(-200);
                }
                
                localStorage.setItem('chat_messages_backup', JSON.stringify(allMessages));
            }
        } catch (e) {
            console.error('Error saving backup message:', e);
        }
    },
    
    // 清理备用存储中的旧消息
    cleanupBackupMessages: function(roomName, keepCount = 200) {
        try {
            const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
            if (allMessages[roomName] && allMessages[roomName].length > keepCount) {
                allMessages[roomName] = allMessages[roomName].slice(-keepCount);
                localStorage.setItem('chat_messages_backup', JSON.stringify(allMessages));
            }
        } catch (e) {
            console.error('Error cleaning up backup messages:', e);
        }
    },
    
    // 打开数据库连接
    openDB: function() {
        console.log('openDB called:', this.DB_NAME, this.DB_VERSION);
        
        // 检查IndexedDB支持
        if (!this.isIndexedDBSupported()) {
            console.error('IndexedDB is not supported by this browser!');
            return Promise.reject(new Error('IndexedDB is not supported'));
        }
        
        // 获取正确的IndexedDB实现
        const indexedDB = window.indexedDB || window.webkitIndexedDB || window.mozIndexedDB || window.msIndexedDB;
        
        // 保存MessageStorage对象的引用，确保在事件处理函数中可以访问到正确的this
        const self = this;
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(self.DB_NAME, self.DB_VERSION);
            console.log('IndexedDB open request created:', request);
            
            request.onupgradeneeded = (event) => {
                console.log('onupgradeneeded event triggered, old version:', event.oldVersion, 'new version:', event.newVersion);
                const db = event.target.result;
                console.log('Database object:', db);
                
                // 如果旧版本小于3，需要重建消息存储对象（因为keyPath配置改变了）
                if (event.oldVersion < 3 && db.objectStoreNames.contains(self.STORE_NAME)) {
                    console.log('Deleting old message store to rebuild with new keyPath configuration');
                    db.deleteObjectStore(self.STORE_NAME);
                }
                
                // 创建消息存储对象
                if (!db.objectStoreNames.contains(self.STORE_NAME)) {
                    console.log('Creating message store:', self.STORE_NAME);
                    const messageStore = db.createObjectStore(self.STORE_NAME, {
                        keyPath: 'id'
                    });
                    
                    // 创建索引
                    console.log('Creating indexes for message store');
                    messageStore.createIndex('byRoom', 'roomName', { unique: false });
                    messageStore.createIndex('bySender', 'from', { unique: false });
                    messageStore.createIndex('byTime', 'createTime', { unique: false });
                    messageStore.createIndex('byType', 'messageType', { unique: false });
                    messageStore.createIndex('byNSFW', 'isNSFW', { unique: false });
                    console.log('Message store and indexes created successfully');
                } else {
                    // 如果存储对象已存在，添加新的索引（用于数据库升级）
                    const messageStore = event.target.transaction.objectStore(self.STORE_NAME);
                    if (!messageStore.indexNames.contains('byNSFW')) {
                        messageStore.createIndex('byNSFW', 'isNSFW', { unique: false });
                        console.log('Added byNSFW index to existing message store');
                    }
                }
                
                // 创建最后同步时间存储对象
                if (!db.objectStoreNames.contains(self.LAST_SYNC_STORE)) {
                    console.log('Creating last sync store:', self.LAST_SYNC_STORE);
                    db.createObjectStore(self.LAST_SYNC_STORE, {
                        keyPath: 'id'
                    });
                    console.log('Last sync store created successfully');
                }
            };
            
            request.onsuccess = (event) => {
                console.log('IndexedDB open request succeeded');
                const db = event.target.result;
                console.log('Database connection established:', db);
                console.log('Available object stores:', db.objectStoreNames);
                resolve(db);
            };
            
            request.onerror = (event) => {
                console.error('IndexedDB open request failed:', event.target.error);
                reject(new Error('Failed to open IndexedDB: ' + event.target.error));
            };
            
            request.onblocked = (event) => {
                console.warn('IndexedDB open request blocked:', event);
            };
        });
    },
    
    // 保存消息到数据库
    saveMessage: function(message) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                
                const request = store.put({
                    id: message.id,
                    roomName: message.roomName,
                    from: message.from,
                    to: message.to,
                    content: message.content,
                    createTime: message.createTime,
                    type: message.type,
                    messageType: message.messageType,
                    isSystem: message.isSystem || false,
                    isNSFW: message.isNSFW || false,
                    iv: message.iv || null
                });
                
                request.onsuccess = () => resolve(request.result);
                request.onerror = () => reject(new Error('Failed to save message: ' + request.error));
            });
        });
    },
    
    // 批量保存消息
    saveMessages: function(messages) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                
                let count = 0;
                messages.forEach(message => {
                    const request = store.put({
                        id: message.id,
                        roomName: message.roomName,
                        from: message.from,
                        to: message.to,
                        content: message.content,
                        createTime: message.createTime,
                        type: message.type,
                        messageType: message.messageType,
                        isSystem: message.isSystem || false,
                        isNSFW: message.isNSFW || false,
                        iv: message.iv || null
                    });
                    
                    request.onsuccess = () => {
                        count++;
                        if (count === messages.length) resolve(count);
                    };
                    
                    request.onerror = () => reject(new Error('Failed to save messages: ' + request.error));
                });
            });
        });
    },
    
    // 获取指定房间的消息
    getRoomMessages: function(roomName, limit = 100) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readonly');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byRoom');
                
                const request = index.getAll(IDBKeyRange.only(roomName));
                
                request.onsuccess = () => {
                    // 按时间排序并限制数量
                    const messages = request.result
                        .sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
                        .slice(-limit);
                    resolve(messages);
                };
                
                request.onerror = () => reject(new Error('Failed to get room messages: ' + request.error));
            });
        });
    },
    
    // 获取最后同步时间
    getLastSyncTime: function(roomName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.LAST_SYNC_STORE, 'readonly');
                const store = transaction.objectStore(this.LAST_SYNC_STORE);
                
                const request = store.get(roomName);
                
                request.onsuccess = () => {
                    resolve(request.result ? new Date(request.result.timestamp) : null);
                };
                
                request.onerror = () => reject(new Error('Failed to get last sync time: ' + request.error));
            });
        });
    },
    
    // 设置最后同步时间
    setLastSyncTime: function(roomName, timestamp = new Date()) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.LAST_SYNC_STORE, 'readwrite');
                const store = transaction.objectStore(this.LAST_SYNC_STORE);
                
                const request = store.put({
                    id: roomName,
                    timestamp: timestamp.toISOString()
                });
                
                request.onsuccess = () => resolve();
                request.onerror = () => reject(new Error('Failed to set last sync time: ' + request.error));
            });
        });
    },
    
    // 检查消息是否最新（通过比较本地最后一条消息的时间戳与服务器的最新时间戳）
    isMessagesUpToDate: function(roomName, serverLatestTimestamp) {
        return this.getLastSyncTime(roomName).then(localTimestamp => {
            if (!localTimestamp) return false;
            if (!serverLatestTimestamp) return true;
            
            return localTimestamp >= new Date(serverLatestTimestamp);
        });
    },
    
    // 清理旧消息（保留最近N条）
    cleanupOldMessages: function(roomName, keepCount = 200) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byRoom');
                
                const request = index.getAll(IDBKeyRange.only(roomName));
                
                request.onsuccess = () => {
                    const messages = request.result;
                    if (messages.length <= keepCount) {
                        resolve(0);
                        return;
                    }
                    
                    // 按时间排序并删除旧消息
                    const messagesToDelete = messages
                        .sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
                        .slice(0, messages.length - keepCount);
                    
                    let deletedCount = 0;
                    messagesToDelete.forEach(message => {
                        const deleteRequest = store.delete(message.id);
                        deleteRequest.onsuccess = () => {
                            deletedCount++;
                            if (deletedCount === messagesToDelete.length) {
                                resolve(deletedCount);
                            }
                        };
                    });
                };
                
                request.onerror = () => reject(new Error('Failed to cleanup old messages: ' + request.error));
            });
        });
    },
    
    // 清空指定房间的消息
    clearRoomMessages: function(roomName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byRoom');
                
                const request = index.openCursor(IDBKeyRange.only(roomName));
                
                request.onsuccess = (event) => {
                    const cursor = event.target.result;
                    if (cursor) {
                        cursor.delete();
                        cursor.continue();
                    } else {
                        resolve();
                    }
                };
                
                request.onerror = () => reject(new Error('Failed to clear room messages: ' + request.error));
            });
        });
    },
    
    // 获取指定时间段内的消息
    getMessagesByTimeRange: function(roomName, startTime, endTime) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readonly');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byRoom');
                
                const request = index.getAll(IDBKeyRange.only(roomName));
                
                request.onsuccess = () => {
                    const messages = request.result.filter(message => {
                        const messageTime = new Date(message.createTime);
                        return messageTime >= startTime && messageTime <= endTime;
                    }).sort((a, b) => new Date(a.createTime) - new Date(b.createTime));
                    
                    resolve(messages);
                };
                
                request.onerror = () => reject(new Error('Failed to get messages by time range: ' + request.error));
            });
        });
    },
    
    // 获取指定房间的最晚一条消息的时间戳
    getLatestMessageTimestamp: function(roomName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readonly');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byRoom');
                
                const request = index.getAll(IDBKeyRange.only(roomName));
                
                request.onsuccess = () => {
                    if (request.result.length === 0) {
                        resolve(null);
                        return;
                    }
                    
                    // 按时间排序，获取最晚的一条消息
                    const latestMessage = request.result
                        .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))[0];
                    
                    resolve(latestMessage.createTime);
                };
                
                request.onerror = () => reject(new Error('Failed to get latest message timestamp: ' + request.error));
            });
        });
    },
    
    // 删除指定消息
    deleteMessage: function(roomName, messageId) {
        // 先从IndexedDB删除
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                
                const request = store.delete(messageId);
                
                request.onsuccess = () => {
                    console.log('Message deleted from IndexedDB:', messageId);
                    resolve();
                };
                
                request.onerror = () => {
                    console.error('Failed to delete message from IndexedDB:', request.error);
                    reject(new Error('Failed to delete message: ' + request.error));
                };
            });
        }).then(() => {
            // 同时从备用存储删除
            try {
                const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
                if (allMessages[roomName]) {
                    allMessages[roomName] = allMessages[roomName].filter(m => m.id !== messageId);
                    localStorage.setItem('chat_messages_backup', JSON.stringify(allMessages));
                    console.log('Message deleted from backup storage:', messageId);
                }
            } catch (e) {
                console.error('Error deleting message from backup storage:', e);
            }
        }).catch(error => {
            console.error('Error deleting message:', error);
            throw error;
        });
    }
};

// 将MessageStorage挂载到window对象，确保全局可用
if (typeof window !== 'undefined') {
    window.MessageStorage = MessageStorage;
    console.log('MessageStorage has been attached to window object');
}
