-- 学生社团管理系统数据库脚本
-- 数据库: stuclub_db
-- 基于 chatroom_db 改造

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
-- /*!50503 NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
-- /*!40111 SET @OLD_NOTES=@@NOTES, NOTES=0 */;

-- ------------------------------------------------------
-- 创建数据库
-- ------------------------------------------------------
CREATE DATABASE IF NOT EXISTS stuclub_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE stuclub_db;

-- ------------------------------------------------------
-- 删除所有引用其他表的表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `activity_registrations`;
DROP TABLE IF EXISTS `activity_signups`;
DROP TABLE IF EXISTS `conversation_member`;
DROP TABLE IF EXISTS `messages`;
DROP TABLE IF EXISTS `club_member`;
DROP TABLE IF EXISTS `room_member`;
DROP TABLE IF EXISTS `user_uuid`;
DROP TABLE IF EXISTS `friend_requests`;
DROP TABLE IF EXISTS `friendships`;

-- ------------------------------------------------------
-- 删除被引用的表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
DROP TABLE IF EXISTS `activities`;
DROP TABLE IF EXISTS `club`;
DROP TABLE IF EXISTS `room`;
DROP TABLE IF EXISTS `user`;

-- ------------------------------------------------------
-- Table structure for table `user` (用户表 - 改造)
-- ------------------------------------------------------
CREATE TABLE `user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL COMMENT '用户名/学号',
  `password` VARCHAR(255) NOT NULL COMMENT '密码(bcrypt加密)',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `student_id` VARCHAR(20) DEFAULT NULL COMMENT '学号',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `role` ENUM('STUDENT', 'ADMIN', 'SUPER_ADMIN') NOT NULL DEFAULT 'STUDENT' COMMENT '用户角色：STUDENT-学生，ADMIN-社团管理员，SUPER_ADMIN-系统管理员',
  `status` ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `last_login_time` TIMESTAMP NULL DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------
-- Table structure for table `club` (社团表 - 改造自room)
-- ------------------------------------------------------
CREATE TABLE `club` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '社团名称',
  `description` TEXT COMMENT '社团简介',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '社团类别（如：学术科技、文化艺术、体育健身、志愿服务等）',
  `logo_url` VARCHAR(255) DEFAULT NULL COMMENT '社团logo URL',
  `advisor` VARCHAR(50) DEFAULT NULL COMMENT '指导老师',
  `advisor_contact` VARCHAR(100) DEFAULT NULL COMMENT '指导老师联系方式',
  `max_members` INT DEFAULT 100 COMMENT '最大成员数',
  `status` ENUM('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED') NOT NULL DEFAULT 'PENDING' COMMENT '社团状态：待审核/已通过/已拒绝/已停用',
  `founder_id` INT DEFAULT NULL COMMENT '创始人ID',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `approved_at` TIMESTAMP NULL DEFAULT NULL COMMENT '审核通过时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_founder_id` (`founder_id`),
  CONSTRAINT `fk_club_founder` FOREIGN KEY (`founder_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社团表';

-- ------------------------------------------------------
-- Table structure for table `club_member` (社团成员表 - 改造自room_member)
-- ------------------------------------------------------
CREATE TABLE `club_member` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `club_id` INT NOT NULL COMMENT '社团ID',
  `user_id` INT NOT NULL COMMENT '用户ID',
  `role` ENUM('PRESIDENT', 'VICE_PRESIDENT', 'ADMIN', 'MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：社长/副社长/管理员/普通成员',
  `join_type` ENUM('APPLY', 'INVITE') NOT NULL DEFAULT 'APPLY' COMMENT '加入方式：申请/邀请',
  `join_reason` VARCHAR(255) DEFAULT NULL COMMENT '加入理由',
  `status` ENUM('PENDING', 'APPROVED', 'REJECTED', 'QUIT', 'EXPELLED') NOT NULL DEFAULT 'PENDING' COMMENT '状态：待审核/已通过/已拒绝/已退出/已开除',
  `display_name` VARCHAR(50) DEFAULT NULL COMMENT '在社团中的显示名称',
  `joined_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请加入时间',
  `approved_at` TIMESTAMP NULL DEFAULT NULL COMMENT '审核通过时间',
  `quit_at` TIMESTAMP NULL DEFAULT NULL COMMENT '退出时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_club_member` (`club_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_club_member_club` FOREIGN KEY (`club_id`) REFERENCES `club` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_club_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社团成员表';

-- ------------------------------------------------------
-- Table structure for table `activities` (活动表 - 新增)
-- ------------------------------------------------------
CREATE TABLE `activities` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `club_id` INT NOT NULL COMMENT '所属社团ID',
  `title` VARCHAR(200) NOT NULL COMMENT '活动标题',
  `description` TEXT COMMENT '活动详情',
  `activity_type` ENUM('INTERNAL', 'CLUB_ONLY', 'PUBLIC', 'COMPETITION', 'TRAINING') NOT NULL DEFAULT 'INTERNAL' COMMENT '活动类型',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `location` VARCHAR(200) DEFAULT NULL COMMENT '活动地点',
  `max_participants` INT DEFAULT 0 COMMENT '最大参与人数(0表示不限)',
  `current_participants` INT DEFAULT 0 COMMENT '当前报名人数',
  `registration_deadline` DATETIME DEFAULT NULL COMMENT '报名截止时间',
  `poster_url` VARCHAR(255) DEFAULT NULL COMMENT '活动海报URL',
  `budget` DECIMAL(10,2) DEFAULT 0.00 COMMENT '活动预算',
  `status` ENUM('DRAFT', 'PUBLISHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT' COMMENT '活动状态',
  `created_by` INT NOT NULL COMMENT '创建者ID',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `published_at` TIMESTAMP NULL DEFAULT NULL COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_created_by` (`created_by`),
  CONSTRAINT `fk_activities_club` FOREIGN KEY (`club_id`) REFERENCES `club` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_activities_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动表';

-- ------------------------------------------------------
-- Table structure for table `activity_registrations` (活动报名表 - 新增)
-- ------------------------------------------------------
CREATE TABLE `activity_registrations` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `activity_id` INT NOT NULL COMMENT '活动ID',
  `user_id` INT NOT NULL COMMENT '报名用户ID',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `student_id` VARCHAR(20) DEFAULT NULL COMMENT '学号',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `status` ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '报名状态',
  `registration_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  `approval_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `approved_by` INT DEFAULT NULL COMMENT '审核人ID',
  `is_checked_in` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已签到',
  `check_in_time` DATETIME DEFAULT NULL COMMENT '签到时间',
  `check_in_code` VARCHAR(20) DEFAULT NULL COMMENT '签到码',
  `notes` TEXT COMMENT '备注信息',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_activity_registration` (`activity_id`, `user_id`),
  KEY `idx_activity_id` (`activity_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_check_in_code` (`check_in_code`),
  CONSTRAINT `fk_registration_activity` FOREIGN KEY (`activity_id`) REFERENCES `activities` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_registration_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_registration_approver` FOREIGN KEY (`approved_by`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动报名表';

-- ------------------------------------------------------
-- Table structure for table `announcements` (公告表 - 新增)
-- ------------------------------------------------------
CREATE TABLE `announcements` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `club_id` INT DEFAULT NULL COMMENT '所属社团( NULL表示系统公告)',
  `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content` TEXT NOT NULL COMMENT '公告内容',
  `priority` ENUM('NORMAL', 'IMPORTANT', 'URGENT') NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
  `is_pinned` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否置顶',
  `status` ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
  `created_by` INT NOT NULL COMMENT '创建者ID',
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `published_at` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_is_pinned` (`is_pinned`),
  CONSTRAINT `fk_announcements_club` FOREIGN KEY (`club_id`) REFERENCES `club` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_announcements_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- ------------------------------------------------------
-- Table structure for table `join_requests` (入社申请表 - 改造自friend_requests)
-- ------------------------------------------------------
CREATE TABLE `join_requests` (
  `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL COMMENT '申请用户ID',
  `club_id` INT NOT NULL COMMENT '申请社团ID',
  `reason` TEXT COMMENT '申请理由',
  `status` ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '申请状态',
  `reviewed_by` INT DEFAULT NULL COMMENT '审核人ID',
  `reviewed_at` TIMESTAMP NULL DEFAULT NULL COMMENT '审核时间',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `unique_pending_request` (`user_id`, `club_id`, `status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_join_requests_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_join_requests_club` FOREIGN KEY (`club_id`) REFERENCES `club` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_join_requests_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入社申请表';

-- ------------------------------------------------------
-- Table structure for table `conversation` (会话表 - 保留)
-- ------------------------------------------------------
CREATE TABLE `conversation` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `type` ENUM('CLUB', 'PRIVATE', 'ACTIVITY', 'SYSTEM') NOT NULL COMMENT '会话类型：社团群聊/私聊/活动讨论/系统通知',
  `name` VARCHAR(100) DEFAULT NULL COMMENT '会话名称',
  `club_id` INT DEFAULT NULL COMMENT '关联社团ID',
  `activity_id` INT DEFAULT NULL COMMENT '关联活动ID',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '会话头像',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_club_id` (`club_id`),
  KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ------------------------------------------------------
-- Table structure for table `conversation_member` (会话成员表 - 保留)
-- ------------------------------------------------------
CREATE TABLE `conversation_member` (
  `conversation_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `role` ENUM('OWNER', 'ADMIN', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
  `display_name` VARCHAR(50) DEFAULT NULL COMMENT '在会话中的显示名称',
  `joined_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_read_time` DATETIME DEFAULT NULL COMMENT '最后已读时间',
  PRIMARY KEY (`conversation_id`, `user_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_conversation_member_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conversation_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话成员表';

-- ------------------------------------------------------
-- Table structure for table `messages` (消息表 - 保留)
-- ------------------------------------------------------
CREATE TABLE `messages` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `type` VARCHAR(20) NOT NULL COMMENT '消息类型 (TEXT, SYSTEM, IMAGE, FILE等)',
  `user_id` INT NOT NULL COMMENT '发送者用户ID',
  `conversation_id` INT NOT NULL COMMENT '会话ID',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `extra_data` TEXT DEFAULT NULL COMMENT '扩展数据(JSON格式)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_recalled` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已撤回',
  `recalled_at` DATETIME DEFAULT NULL COMMENT '撤回时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_conversation_id` (`conversation_id`),
  INDEX `idx_create_time` (`create_time`),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_messages_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_messages_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ------------------------------------------------------
-- Table structure for table `user_uuid` (会话令牌表 - 保留)
-- ------------------------------------------------------
CREATE TABLE `user_uuid` (
  `user_id` INT NOT NULL,
  `uuid` CHAR(36) NOT NULL,
  `device_info` VARCHAR(255) DEFAULT NULL COMMENT '设备信息',
  `issued_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` TIMESTAMP NULL DEFAULT NULL COMMENT '过期时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uuid` (`uuid`),
  CONSTRAINT `fk_user_uuid_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话令牌表';

-- ------------------------------------------------------
-- 初始化数据
-- ------------------------------------------------------

-- 创建超级管理员账号 (密码: admin123)
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'SUPER_ADMIN', 'ACTIVE');

-- 创建示例社团
INSERT INTO `club` (`name`, `description`, `category`, `advisor`, `status`, `founder_id`, `approved_at`) VALUES
('计算机协会', '致力于推广计算机技术，为同学们提供编程学习和交流的平台', '学术科技', '张教授', 'APPROVED', 1, NOW()),
('音乐社', '校园音乐爱好者聚集地，欢迎所有热爱音乐的同学加入', '文化艺术', '李老师', 'APPROVED', 1, NOW()),
('篮球俱乐部', '强身健体，以球会友', '体育健身', '王教练', 'APPROVED', 1, NOW()),
('志愿者协会', '奉献爱心，服务社会', '志愿服务', '陈老师', 'APPROVED', 1, NOW());

-- 为社团创建群聊会话
INSERT INTO `conversation` (`type`, `name`, `club_id`) VALUES
('CLUB', '计算机协会群聊', 1),
('CLUB', '音乐社群聊', 2),
('CLUB', '篮球俱乐部群聊', 3),
('CLUB', '志愿者协会群聊', 4);

-- 为社团添加创始人作为社长
INSERT INTO `club_member` (`club_id`, `user_id`, `role`, `status`, `approved_at`) VALUES
(1, 1, 'PRESIDENT', 'APPROVED', NOW()),
(2, 1, 'PRESIDENT', 'APPROVED', NOW()),
(3, 1, 'PRESIDENT', 'APPROVED', NOW()),
(4, 1, 'PRESIDENT', 'APPROVED', NOW());

-- 为群聊添加创始人
INSERT INTO `conversation_member` (`conversation_id`, `user_id`, `role`) VALUES
(1, 1, 'OWNER'),
(2, 1, 'OWNER'),
(3, 1, 'OWNER'),
(4, 1, 'OWNER');

-- 创建示例活动
INSERT INTO `activities` (`club_id`, `title`, `description`, `activity_type`, `start_time`, `end_time`, `location`, `max_participants`, `registration_deadline`, `status`, `created_by`, `published_at`) VALUES
(1, 'Python编程入门培训', '面向零基础同学的Python编程培训', 'TRAINING', ADDDATE(CURDATE(), 7), ADDTIME(ADDDATE(CURDATE(), 7), '03:00:00'), '教学楼A101', 50, ADDDATE(CURDATE(), 5), 'PUBLISHED', 1, NOW()),
(2, '校园歌手大赛', '展现你的歌声，角逐校园歌王', 'COMPETITION', ADDDATE(CURDATE(), 14), ADDTIME(ADDDATE(CURDATE(), 14), '05:00:00'), '大学生活动中心', 100, ADDDATE(CURDATE(), 10), 'PUBLISHED', 1, NOW());

-- 创建系统公告
INSERT INTO `announcements` (`title`, `content`, `priority`, `is_pinned`, `status`, `created_by`, `published_at`) VALUES
('欢迎使用学生社团管理系统', '欢迎各位同学使用本校学生社团管理系统，在这里你可以浏览社团信息、报名参加活动。', 'IMPORTANT', TRUE, 'PUBLISHED', 1, NOW()),
('社团招新季开始了', '新学期社团招新活动正式开始，欢迎同学们积极报名加入心仪的社团！', 'NORMAL', FALSE, 'PUBLISHED', 1, NOW());

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
-- /*!40111 SET NOTES=@OLD_NOTES */;

-- Dump completed
