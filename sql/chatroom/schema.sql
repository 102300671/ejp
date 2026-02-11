-- MySQL dump 10.13  Distrib 8.0.44, for Linux (x86_64)
--
-- Host: localhost    Database: chatroom_db
-- ------------------------------------------------------
-- Server version	8.0.44-0ubuntu0.24.04.2

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `room`
--

DROP TABLE IF EXISTS `room`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room` (
  `id` int NOT NULL AUTO_INCREMENT,
  `room_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `room_type` enum('PUBLIC','PRIVATE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `room_member`
--

DROP TABLE IF EXISTS `room_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_member` (
  `room_id` int NOT NULL,
  `user_id` int NOT NULL,
  `joined_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `role` ENUM('OWNER', 'ADMIN', 'MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '用户在房间中的角色：OWNER-房主，ADMIN-管理员，MEMBER-普通成员',
  `accept_temporary_chat` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '在该房间是否接受临时聊天',
  PRIMARY KEY (`room_id`,`user_id`) USING BTREE,
  KEY `user_id` (`user_id`) USING BTREE,
  KEY `idx_role` (`role`) USING BTREE,
  CONSTRAINT `room_member_ibfk_1` FOREIGN KEY (`room_id`) REFERENCES `room` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `room_member_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `accept_temporary_chat` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否接受临时聊天',
  `status` ENUM('ONLINE', 'OFFLINE', 'AWAY', 'BUSY') NOT NULL DEFAULT 'OFFLINE' COMMENT '用户状态：ONLINE-在线，OFFLINE-离线，AWAY-离开，BUSY-忙碌',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `username` (`username`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_uuid`
--

DROP TABLE IF EXISTS `user_uuid`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_uuid` (
  `user_id` int NOT NULL,
  `uuid` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `issued_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE KEY `uuid` (`uuid`) USING BTREE,
  CONSTRAINT `user_uuid_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friend_requests`
--

DROP TABLE IF EXISTS `friend_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `friend_requests` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `from_user_id` INT NOT NULL COMMENT '发送者用户ID',
    `to_user_id` INT NOT NULL COMMENT '接收者用户ID',
    `status` ENUM('PENDING', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '请求状态',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `unique_pending_request` (`from_user_id`, `to_user_id`, `status`) COMMENT '防止重复发送好友请求',
    KEY `idx_from_user_id` (`from_user_id`),
    KEY `idx_to_user_id` (`to_user_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_friend_requests_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_friend_requests_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友请求表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friendships`
--

DROP TABLE IF EXISTS `friendships`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `friendships` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user1_id` INT NOT NULL COMMENT '用户1的ID',
    `user2_id` INT NOT NULL COMMENT '用户2的ID',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立好友关系的时间',
    UNIQUE KEY `unique_friendship` (`user1_id`, `user2_id`) COMMENT '确保同一对好友关系不重复',
    KEY `idx_user1_id` (`user1_id`),
    KEY `idx_user2_id` (`user2_id`),
    CONSTRAINT `fk_friendships_user1` FOREIGN KEY (`user1_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_friendships_user2` FOREIGN KEY (`user2_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `messages` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `type` VARCHAR(20) NOT NULL COMMENT '消息类型 (TEXT, SYSTEM, JOIN, LEAVE等)',
    `from_username` VARCHAR(50) NOT NULL COMMENT '发送者用户名',
    `to_username` VARCHAR(50) NOT NULL COMMENT '接收者用户名或房间名',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',
    `message_type` VARCHAR(20) NOT NULL COMMENT '消息类别 (ROOM:房间消息, PRIVATE:私人消息)',
    `is_nsfw` BOOLEAN DEFAULT FALSE COMMENT '是否为不适宜内容',
    `iv` VARCHAR(255) DEFAULT NULL COMMENT '加密初始化向量',
    INDEX `idx_from_username` (`from_username`),
    INDEX `idx_to_username` (`to_username`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_message_type` (`message_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- Dump completed on 2026-01-18 10:30:03
