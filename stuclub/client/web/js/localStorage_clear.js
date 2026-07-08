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
    DB_VERSION: 6,
    STORE_NAME: 'messages',
    LAST_SYNC_STORE: 'lastSync',
    
    // 检查IndexedDB是否可用
    isIndexedDBSupported: function() {
        return !!(window.indexedDB || window.webkitIndexedDB || window.mozIndexedDB || window.msIndexedDB);
    },
    
    // ========== 备用存储机制（使用localStorage） ==========
    // 当IndexedDB不可用时，使用localStorage作为备用存储
    
    // 获取备用存储中的所有消息
    getBackupMessages: function(chatName) {
        try {
            const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
            return allMessages[chatName] || [];
        } catch (e) {
            console.error('Error reading backup messages:', e);
            return [];
        }
    },
    
    // 保存消息到备用存储
    saveBackupMessage: function(chatName, message) {
        try {
            const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
            if (!allMessages[chatName]) {
                allMessages[chatName] = [];
            }
            allMessages[chatName].push(message);
            localStorage.setItem('chat_messages_backup', JSON.stringify(allMessages));
        } catch (e) {
            console.error('Error saving backup message:', e);
        }
    },
    
    // 清空备用存储中的指定房间消息
    clearBackupMessages: function(chatName) {
        try {
            const allMessages = JSON.parse(localStorage.getItem('chat_messages_backup') || '{}');
            if (allMessages[chatName]) {
                delete allMessages[chatName];
                localStorage.setItem('chat_messages_backup', JSON.stringify(allMessages));
            }
        } catch (e) {
            console.error('Error clearing backup messages:', e);
        }
    },
    
    // ========== IndexedDB存储机制 ==========
    
    // 打开IndexedDB数据库
    openDB: function() {
        return new Promise((resolve, reject) => {
            const request = window.indexedDB.open(this.DB_NAME, this.DB_VERSION);
            
            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                console.log('Database upgrade needed, current version:', event.oldVersion, 'new version:', this.DB_VERSION);
                
                // 版本6：添加conversation_id支持
                if (event.oldVersion < 6) {
                    console.log('Upgrading to version 6 - adding conversation_id support');
                    // 删除旧的消息存储，重新创建以包含新字段
                    if (db.objectStoreNames.contains(self.STORE_NAME)) {
                        db.deleteObjectStore(self.STORE_NAME);
                        console.log('Deleted old message store for version 6 upgrade');
                    }
                }
                
                // 创建消息存储对象
                if (!db.objectStoreNames.contains(self.STORE_NAME)) {
                    console.log('Creating message store:', self.STORE_NAME);
                    const messageStore = db.createObjectStore(self.STORE_NAME, {
                        keyPath: 'id'
                    });
                    
                    // 创建索引
                    console.log('Creating indexes for message store');
                    messageStore.createIndex('byChat', 'chatName', { unique: false });
                    messageStore.createIndex('bySender', 'from', { unique: false });
                    messageStore.createIndex('byTime', 'createTime', { unique: false });
                    messageStore.createIndex('byType', 'messageType', { unique: false });
                    messageStore.createIndex('byNSFW', 'isNSFW', { unique: false });
                    messageStore.createIndex('byConversation', 'conversationId', { unique: false });
                    console.log('Message store and indexes created successfully');
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
        });
    },
    
    // 保存消息到IndexedDB
    saveMessage: function(chatName, message) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                
                const request = store.put(message);
                
                request.onsuccess = () => {
                    console.log('Message saved to IndexedDB:', message.id);
                    resolve();
                };
                
                request.onerror = () => {
                    console.error('Failed to save message to IndexedDB:', request.error);
                    // 如果IndexedDB失败，使用备用存储
                    this.saveBackupMessage(chatName, message);
                    resolve();
                };
            });
        }).catch(error => {
            console.error('Error saving message to IndexedDB:', error);
            throw error;
        });
    },
    
    // 获取指定房间的所有消息
    getMessages: function(chatName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readonly');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byChat');
                
                const request = index.getAll(IDBKeyRange.only(chatName));
                
                request.onsuccess = () => {
                    const messages = request.result.sort((a, b) => new Date(a.createTime) - new Date(b.createTime));
                    console.log('Retrieved messages from IndexedDB:', messages.length, 'for chat:', chatName);
                    resolve(messages);
                };
                
                request.onerror = () => {
                    console.error('Failed to get messages from IndexedDB:', request.error);
                    // 如果IndexedDB失败，使用备用存储
                    const backupMessages = this.getBackupMessages(chatName);
                    resolve(backupMessages);
                };
            });
        }).catch(error => {
            console.error('Error getting messages from IndexedDB:', error);
            throw error;
        });
    },
    
    // 清空指定房间的消息
    clearRoomMessages: function(chatName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readwrite');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byChat');
                
                const request = index.openCursor(IDBKeyRange.only(chatName));
                
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
    getMessagesByTimeRange: function(chatName, startTime, endTime) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readonly');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byChat');
                
                const request = index.getAll(IDBKeyRange.only(chatName));
                
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
    getLatestMessageTimestamp: function(chatName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.STORE_NAME, 'readonly');
                const store = transaction.objectStore(this.STORE_NAME);
                const index = store.index('byChat');
                
                const request = index.getAll(IDBKeyRange.only(chatName));
                
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
    
    // 设置最后同步时间
    setLastSyncTime: function(chatName, timestamp = new Date()) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.LAST_SYNC_STORE, 'readwrite');
                const store = transaction.objectStore(this.LAST_SYNC_STORE);
                
                const syncData = {
                    id: chatName,
                    timestamp: timestamp.toISOString()
                };
                
                const request = store.put(syncData);
                
                request.onsuccess = () => {
                    console.log('Last sync time saved:', chatName, timestamp);
                    resolve();
                };
                
                request.onerror = () => reject(new Error('Failed to save last sync time: ' + request.error));
            });
        });
    },
    
    // 获取最后同步时间
    getLastSyncTime: function(chatName) {
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction(this.LAST_SYNC_STORE, 'readonly');
                const store = transaction.objectStore(this.LAST_SYNC_STORE);
                
                const request = store.get(chatName);
                
                request.onsuccess = () => {
                    if (request.result) {
                        resolve(new Date(request.result.timestamp));
                    } else {
                        resolve(null);
                    }
                };
                
                request.onerror = () => reject(new Error('Failed to get last sync time: ' + request.error));
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
    },
    
    // 清空所有消息和同步时间
    clearAll: function() {
        console.log('Clearing all messages from IndexedDB and localStorage');
        
        // 清空IndexedDB
        return this.openDB().then(db => {
            return new Promise((resolve, reject) => {
                const transaction = db.transaction([this.STORE_NAME, this.LAST_SYNC_STORE], 'readwrite');
                const messageStore = transaction.objectStore(this.STORE_NAME);
                const syncStore = transaction.objectStore(this.LAST_SYNC_STORE);
                
                // 清空消息存储
                const clearMessagesRequest = messageStore.clear();
                clearMessagesRequest.onsuccess = () => {
                    console.log('All messages cleared from IndexedDB');
                };
                clearMessagesRequest.onerror = () => {
                    console.error('Failed to clear messages:', clearMessagesRequest.error);
                };
                
                // 清空同步时间存储
                const clearSyncRequest = syncStore.clear();
                clearSyncRequest.onsuccess = () => {
                    console.log('All sync times cleared from IndexedDB');
                };
                clearSyncRequest.onerror = () => {
                    console.error('Failed to clear sync times:', clearSyncRequest.error);
                };
                
                transaction.oncomplete = () => {
                    console.log('IndexedDB clear transaction completed');
                    
                    // 同时清空localStorage中的备份数据
                    try {
                        localStorage.removeItem('chat_messages_backup');
                        localStorage.removeItem('lastSyncTime');
                        console.log('Backup storage cleared from localStorage');
                        resolve();
                    } catch (e) {
                        console.error('Error clearing backup storage:', e);
                        reject(new Error('Failed to clear backup storage: ' + e.message));
                    }
                };
                
                transaction.onerror = () => {
                    reject(new Error('Failed to clear IndexedDB: ' + transaction.error));
                };
            });
        }).catch(error => {
            console.error('Error clearing IndexedDB:', error);
            throw error;
        });
    }
};

// 将MessageStorage挂载到window对象，确保全局可用
if (typeof window !== 'undefined') {
    window.MessageStorage = MessageStorage;
    console.log('MessageStorage has been attached to window object');
}
