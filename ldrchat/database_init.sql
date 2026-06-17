-- ==============================================
-- 异地恋情侣聊天室数据库初始化脚本
-- ==============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS ldrchat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ldrchat_db;

-- ==============================================
-- 用户表
-- ==============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `accept_temporary_chat` BOOLEAN DEFAULT FALSE COMMENT '是否接受临时聊天',
    `status` VARCHAR(20) DEFAULT 'OFFLINE' COMMENT '用户状态（ONLINE/OFFLINE/AWAY/BUSY）',
    `last_logout_time` TIMESTAMP NULL COMMENT '最后下线时间',
    INDEX `idx_username` (`username`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ==============================================
-- 用户UUID表
-- ==============================================
CREATE TABLE IF NOT EXISTS `user_uuid` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `user_id` INT NOT NULL COMMENT '用户ID',
    `uuid` VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID标识',
    `issued_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_uuid` (`uuid`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户UUID表';

-- ==============================================
-- CP绑定表（情侣绑定）
-- ==============================================
CREATE TABLE IF NOT EXISTS `cp_bindings` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '绑定ID',
    `user1_id` INT NOT NULL COMMENT '用户1ID',
    `user2_id` INT NOT NULL COMMENT '用户2ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    INDEX `idx_user1_id` (`user1_id`),
    INDEX `idx_user2_id` (`user2_id`),
    FOREIGN KEY (`user1_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user2_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `unique_cp_pair` (`user1_id`, `user2_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CP绑定表';

-- ==============================================
-- CP请求表（绑定请求）
-- ==============================================
CREATE TABLE IF NOT EXISTS `cp_requests` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '请求ID',
    `from_user_id` INT NOT NULL COMMENT '发起请求用户ID',
    `to_user_id` INT NOT NULL COMMENT '接收请求用户ID',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING/ACCEPTED/REJECTED）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP NULL COMMENT '更新时间',
    INDEX `idx_from_user_id` (`from_user_id`),
    INDEX `idx_to_user_id` (`to_user_id`),
    INDEX `idx_status` (`status`),
    FOREIGN KEY (`from_user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`to_user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CP请求表';

-- ==============================================
-- 会话表
-- ==============================================
CREATE TABLE IF NOT EXISTS `conversations` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    `name` VARCHAR(100) NOT NULL COMMENT '会话名称',
    `type` VARCHAR(20) NOT NULL COMMENT '会话类型（CP/FRIEND/TEMP/ROOM）',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ==============================================
-- 会话成员表
-- ==============================================
CREATE TABLE IF NOT EXISTS `conversation_members` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `conversation_id` INT NOT NULL COMMENT '会话ID',
    `user_id` INT NOT NULL COMMENT '用户ID',
    `joined_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_user_id` (`user_id`),
    FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `unique_conversation_user` (`conversation_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员表';

-- ==============================================
-- 消息表
-- ==============================================
CREATE TABLE IF NOT EXISTS `messages` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    `type` VARCHAR(20) NOT NULL COMMENT '消息类型（TEXT/IMAGE/FILE/SYSTEM等）',
    `user_id` INT NOT NULL COMMENT '发送用户ID',
    `conversation_id` INT NOT NULL COMMENT '会话ID',
    `content` TEXT COMMENT '消息内容',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `message_type` VARCHAR(20) COMMENT '消息类别（ROOM/PRIVATE/CP）',
    `is_nsfw` BOOLEAN DEFAULT FALSE COMMENT '是否为NSFW内容',
    `iv` VARCHAR(50) COMMENT '加密IV（用于AES加密）',
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`conversation_id`) REFERENCES `conversations`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ==============================================
-- 插入示例数据（可选）
-- ==============================================
-- INSERT INTO `user` (`username`, `password`) VALUES 
-- ('user1', '$2a$12$EixZaYbB.rK4fl8x2q7Meu6Q6D2V5fF5Q5Q5Q5Q5Q5Q5Q5Q5Q5Q'),
-- ('user2', '$2a$12$EixZaYbB.rK4fl8x2q7Meu6Q6D2V5fF5Q5Q5Q5Q5Q5Q5Q5Q5Q');

SELECT '数据库初始化完成' AS message;
