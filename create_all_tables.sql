-- ============================================
-- 数据库表创建脚本（基于实际数据库结构）
-- 数据库名: ebook_platform
-- 数据库类型: MySQL
-- 字符集: utf8mb4
-- 引擎: InnoDB
-- 生成时间: 2026-01-13
-- ============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS ebook_platform 
    DEFAULT CHARACTER SET utf8mb4 
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ebook_platform;

-- ============================================
-- 1. 用户相关表
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS `users` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL,
    `password_hash` varchar(255) NOT NULL,
    `enabled` tinyint(1) NOT NULL DEFAULT '0',
    `role` varchar(50) NOT NULL DEFAULT 'User',
    `studentNumber` varchar(255) NOT NULL,
    `email` varchar(255) NOT NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `avatar_url` varchar(500) DEFAULT NULL COMMENT '用户头像URL',
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`),
    UNIQUE KEY `studentNumber` (`studentNumber`),
    UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 验证令牌表
CREATE TABLE IF NOT EXISTS `verification_tokens` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `email` varchar(255) NOT NULL,
    `token` varchar(128) NOT NULL,
    `type` varchar(30) NOT NULL,
    `expires_at` timestamp NOT NULL,
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `token` (`token`),
    KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- 2. 角色权限相关表
-- ============================================

-- 角色表
CREATE TABLE IF NOT EXISTS `roles` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(50) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_roles` (
    `user_id` bigint NOT NULL,
    `role_id` bigint NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `fk_userroles_role` (`role_id`),
    CONSTRAINT `fk_userroles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_userroles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 初始化角色数据
INSERT INTO `roles` (`name`) VALUES ('ADMIN') ON DUPLICATE KEY UPDATE `name`=`name`;
INSERT INTO `roles` (`name`) VALUES ('USER') ON DUPLICATE KEY UPDATE `name`=`name`;

-- ============================================
-- 3. 笔记系统相关表
-- ============================================

-- 标签表
CREATE TABLE IF NOT EXISTS `tags` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(50) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 笔记空间表
CREATE TABLE IF NOT EXISTS `note_spaces` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `user_id` bigint NOT NULL,
    `tag_id` bigint NOT NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notespaces_user_id` (`user_id`),
    KEY `idx_notespaces_tag_id` (`tag_id`),
    CONSTRAINT `fk_notespaces_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_notespaces_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 笔记本表
CREATE TABLE IF NOT EXISTS `notebooks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `space_id` bigint NOT NULL,
    `tag_id` bigint NOT NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notebooks_space_id` (`space_id`),
    KEY `idx_notebooks_tag_id` (`tag_id`),
    CONSTRAINT `fk_notebooks_space` FOREIGN KEY (`space_id`) REFERENCES `note_spaces` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_notebooks_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 笔记表
CREATE TABLE IF NOT EXISTS `notes` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(255) NOT NULL,
    `filename` varchar(255) DEFAULT NULL,
    `notebook_id` bigint NOT NULL,
    `file_type` varchar(10) DEFAULT 'MD',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notes_notebook_id` (`notebook_id`),
    CONSTRAINT `fk_notes_notebook` FOREIGN KEY (`notebook_id`) REFERENCES `notebooks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- 4. 笔记统计相关表
-- ============================================

-- 笔记统计表
CREATE TABLE IF NOT EXISTS `note_stats` (
    `note_id` bigint NOT NULL,
    `author_name` varchar(64) NOT NULL DEFAULT '',
    `views` bigint NOT NULL DEFAULT '0',
    `likes` bigint NOT NULL DEFAULT '0',
    `favorites` bigint NOT NULL DEFAULT '0',
    `comments` bigint NOT NULL DEFAULT '0',
    `last_activity_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `version` bigint NOT NULL DEFAULT '0',
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`note_id`),
    CONSTRAINT `fk_notestats_note` FOREIGN KEY (`note_id`) REFERENCES `notes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 笔记统计补偿表（当批量落盘重试耗尽时写入）
CREATE TABLE IF NOT EXISTS `note_stats_compensation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `note_id` bigint NOT NULL,
    `author_name` varchar(64) NOT NULL DEFAULT '',
    `views` bigint NOT NULL DEFAULT '0',
    `likes` bigint NOT NULL DEFAULT '0',
    `favorites` bigint NOT NULL DEFAULT '0',
    `comments` bigint NOT NULL DEFAULT '0',
    `last_activity_at` timestamp NULL DEFAULT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'PENDING',
    `retry_count` int NOT NULL DEFAULT '0',
    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- 5. 用户关系相关表
-- ============================================

-- 用户关注表
CREATE TABLE IF NOT EXISTS `user_follow` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `follower_id` bigint NOT NULL COMMENT '关注者用户ID',
    `followee_id` bigint NOT NULL COMMENT '被关注者用户ID',
    `follow_time` datetime NOT NULL COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
    KEY `idx_follower_id` (`follower_id`),
    KEY `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系表';

-- 用户收藏笔记表
CREATE TABLE IF NOT EXISTS `user_favorite_note` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `note_id` bigint NOT NULL COMMENT '笔记ID',
    `favorite_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_note` (`user_id`, `note_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏笔记关系表';

-- 用户点赞笔记表
CREATE TABLE IF NOT EXISTS `user_like_note` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `note_id` bigint NOT NULL COMMENT '笔记ID',
    `like_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_note` (`user_id`, `note_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户点赞笔记关系表';

-- ============================================
-- 6. 会话相关表
-- ============================================

-- 会话关系表（关联 MongoDB Conversation）
CREATE TABLE IF NOT EXISTS `conversation_relation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `conversation_id` varchar(64) NOT NULL COMMENT '对应 MongoDB Conversation _id',
    `user1_id` bigint NOT NULL COMMENT '用户ID1，约定小ID',
    `user2_id` bigint NOT NULL COMMENT '用户ID2，约定大ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pair` (`user1_id`, `user2_id`),
    UNIQUE KEY `uk_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话关系表';

-- ============================================
-- 7. 审核相关表
-- ============================================

-- 笔记审核表
CREATE TABLE IF NOT EXISTS `note_moderation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `note_id` bigint NOT NULL,
    `status` varchar(16) NOT NULL,
    `risk_level` varchar(16) DEFAULT NULL,
    `score` int DEFAULT NULL,
    `categories_json` text,
    `findings_json` text,
    `source` varchar(16) DEFAULT 'LLM',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_handled` tinyint(1) DEFAULT '0',
    `admin_comment` varchar(255) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_note_moderation_note_id` (`note_id`),
    KEY `idx_note_moderation_status_handled` (`status`, `is_handled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- 说明
-- ============================================
-- 1. 所有表使用 InnoDB 引擎，支持事务和外键约束
-- 2. 字符集统一使用 utf8mb4，支持完整的 Unicode 字符（包括 emoji）
-- 3. 外键约束使用 ON DELETE CASCADE，确保数据一致性
-- 4. flyway_schema_history 表由 Flyway 自动创建，无需手动创建
-- 5. 执行顺序：先创建基础表（users, tags, roles），再创建依赖表
-- 6. 注意：conversation_relation、user_follow、user_favorite_note 表在实际数据库中没有外键约束
-- 7. 本脚本基于实际数据库结构生成，确保完全匹配
-- ============================================
