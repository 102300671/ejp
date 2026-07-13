-- EJP Chatroom 数据库初始化脚本
-- 使用方法: mysql -u chatroom -p chatroom_db < init.sql

-- 创建用户表
CREATE TABLE IF NOT EXISTS user (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    avatar VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accept_temporary_chat BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'OFFLINE',
    last_logout_time TIMESTAMP NULL
);

-- 创建用户UUID表
CREATE TABLE IF NOT EXISTS user_uuid (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- 创建房间表
CREATE TABLE IF NOT EXISTS room (
    id INT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL UNIQUE,
    room_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    announcement TEXT
);

-- 创建房间成员表
CREATE TABLE IF NOT EXISTS room_member (
    room_id INT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accept_temporary_chat BOOLEAN DEFAULT TRUE,
    display_name VARCHAR(50),
    PRIMARY KEY (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- 创建会话表
CREATE TABLE IF NOT EXISTS conversation (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    room_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE CASCADE
);

-- 创建会话成员表
CREATE TABLE IF NOT EXISTS conversation_member (
    conversation_id INT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- 创建消息表
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    user_id INT NOT NULL,
    conversation_id INT NOT NULL,
    content TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    message_type VARCHAR(20),
    is_nsfw BOOLEAN DEFAULT FALSE,
    iv VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);

-- 创建好友关系表
CREATE TABLE IF NOT EXISTS friendships (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user1_id INT NOT NULL,
    user2_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user1_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES user(id) ON DELETE CASCADE,
    UNIQUE KEY (user1_id, user2_id)
);

-- 创建好友请求表
CREATE TABLE IF NOT EXISTS friend_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    from_user_id INT NOT NULL,
    to_user_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (to_user_id) REFERENCES user(id) ON DELETE CASCADE,
    UNIQUE KEY (from_user_id, to_user_id)
);

-- 创建用户状态日志表
CREATE TABLE IF NOT EXISTS user_status_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    username VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    change_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- 插入测试用户（密码均为 "123456"）
INSERT INTO user (username, password, avatar, created_at, accept_temporary_chat, status) VALUES 
('admin', '$2b$12$jGunVDK4QlYK5uQzHmWDBu3rgW2052gDqChfBc3LfE1ug6bSOy/2a', NULL, NOW(), TRUE, 'OFFLINE'),
('alice', '$2b$12$jGunVDK4QlYK5uQzHmWDBu3rgW2052gDqChfBc3LfE1ug6bSOy/2a', NULL, NOW(), TRUE, 'OFFLINE'),
('bob', '$2b$12$jGunVDK4QlYK5uQzHmWDBu3rgW2052gDqChfBc3LfE1ug6bSOy/2a', NULL, NOW(), TRUE, 'OFFLINE');

-- 创建示例公共房间
INSERT INTO room (room_name, room_type, announcement) VALUES 
('技术交流群', 'PUBLIC', '欢迎来到技术交流群，讨论编程技术！'),
('闲聊群', 'PUBLIC', '随便聊聊，畅所欲言');

-- 将admin添加为房间管理员
SET @admin_id = (SELECT id FROM user WHERE username = 'admin');
SET @tech_room_id = (SELECT id FROM room WHERE room_name = '技术交流群');
SET @chat_room_id = (SELECT id FROM room WHERE room_name = '闲聊群');

INSERT INTO room_member (room_id, user_id, role) VALUES 
(@tech_room_id, @admin_id, 'OWNER'),
(@chat_room_id, @admin_id, 'OWNER');

-- 创建房间会话
INSERT INTO conversation (type, name, room_id) VALUES 
('ROOM', '技术交流群', @tech_room_id),
('ROOM', '闲聊群', @chat_room_id);

-- 添加会话成员
SET @tech_conv_id = (SELECT id FROM conversation WHERE name = '技术交流群');
SET @chat_conv_id = (SELECT id FROM conversation WHERE name = '闲聊群');

INSERT INTO conversation_member (conversation_id, user_id, role) VALUES 
(@tech_conv_id, @admin_id, 'OWNER'),
(@chat_conv_id, @admin_id, 'OWNER');

SELECT '数据库初始化完成' AS result;