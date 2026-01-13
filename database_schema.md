# 数据库结构文档

## 数据库列表

### 1. ebook_platform (主数据库)
项目使用的 MySQL 数据库

### 2. information_schema (MySQL 系统数据库)
MySQL 系统信息数据库

### 3. performance_schema (MySQL 系统数据库)
MySQL 性能监控数据库

### 4. note_db (MongoDB 数据库)
MongoDB 数据库 (uri: mongodb://localhost:27017/note_db)

---

## ebook_platform 数据库表结构

### 1. conversation_relation (会话关系表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| conversation_id | varchar | NO | NULL | UNI | | 对应 MongoDB Conversation _id |
| user1_id | bigint | NO | NULL | MUL | | 用户ID1，约定小ID |
| user2_id | bigint | NO | NULL | | | 用户ID2，约定大ID |
| create_time | datetime | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |

### 2. flyway_schema_history (Flyway 迁移历史表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| installed_rank | int | NO | NULL | PRI | | |
| version | varchar | YES | NULL | | | |
| description | varchar | NO | NULL | | | |
| type | varchar | NO | NULL | | | |
| script | varchar | NO | NULL | | | |
| checksum | int | YES | NULL | | | |
| installed_by | varchar | NO | NULL | | | |
| installed_on | timestamp | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| execution_time | int | NO | NULL | | | |
| success | tinyint | NO | NULL | MUL | | |

### 3. note_moderation (笔记审核表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| note_id | bigint | NO | NULL | MUL | | |
| status | varchar | NO | NULL | MUL | | |
| risk_level | varchar | YES | NULL | | | |
| score | int | YES | NULL | | | |
| categories_json | text | YES | NULL | | | |
| findings_json | text | YES | NULL | | | |
| source | varchar | YES | LLM | | | |
| created_at | timestamp | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| is_handled | tinyint | YES | 0 | | | |
| admin_comment | varchar | YES | NULL | | | |

### 4. note_spaces (笔记空间表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| name | varchar | NO | NULL | | | |
| user_id | bigint | NO | NULL | MUL | | |
| tag_id | bigint | NO | NULL | MUL | | |
| created_at | timestamp | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| updated_at | timestamp | YES | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | |

### 5. note_stats (笔记统计表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| note_id | bigint | NO | NULL | PRI | | |
| author_name | varchar | NO | | | | |
| views | bigint | NO | 0 | | | |
| likes | bigint | NO | 0 | | | |
| favorites | bigint | NO | 0 | | | |
| comments | bigint | NO | 0 | | | |
| last_activity_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| version | bigint | NO | 0 | | | |
| updated_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | |

### 6. note_stats_compensation (笔记统计补偿表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| note_id | bigint | NO | NULL | MUL | | |
| author_name | varchar | NO | | | | |
| views | bigint | NO | 0 | | | |
| likes | bigint | NO | 0 | | | |
| favorites | bigint | NO | 0 | | | |
| comments | bigint | NO | 0 | | | |
| last_activity_at | timestamp | YES | NULL | | | |
| status | varchar | NO | PENDING | | | |
| retry_count | int | NO | 0 | | | |
| created_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| updated_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | |

### 7. notebooks (笔记本表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| name | varchar | NO | NULL | | | |
| space_id | bigint | NO | NULL | MUL | | |
| tag_id | bigint | NO | NULL | MUL | | |
| created_at | timestamp | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| updated_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | |

### 8. notes (笔记表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| title | varchar | NO | NULL | | | |
| filename | varchar | YES | NULL | | | |
| notebook_id | bigint | NO | NULL | MUL | | |
| file_type | varchar | YES | MD | | | |
| created_at | timestamp | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| updated_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | |

### 9. roles (角色表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| name | varchar | NO | NULL | UNI | | |

### 10. tags (标签表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| name | varchar | NO | NULL | UNI | | |

### 11. user_favorite_note (用户收藏笔记表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| user_id | bigint | NO | NULL | MUL | | 用户ID |
| note_id | bigint | NO | NULL | MUL | | 笔记ID |
| favorite_time | datetime | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | 收藏时间 |

### 12. user_follow (用户关注表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| follower_id | bigint | NO | NULL | MUL | | 关注者用户ID |
| followee_id | bigint | NO | NULL | MUL | | 被关注者用户ID |
| follow_time | datetime | NO | NULL | | | 关注时间 |

### 13. user_roles (用户角色关联表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| user_id | bigint | NO | NULL | PRI | | |
| role_id | bigint | NO | NULL | PRI | | |

### 14. users (用户表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| username | varchar | NO | NULL | UNI | | |
| password_hash | varchar | NO | NULL | | | |
| enabled | tinyint | NO | 0 | | | |
| role | varchar | NO | User | | | |
| studentNumber | varchar | NO | NULL | UNI | | |
| email | varchar | NO | NULL | UNI | | |
| created_at | timestamp | NO | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |
| updated_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED on update CURRENT_TIMESTAMP | |
| avatar_url | varchar | YES | NULL | | | 用户头像URL |

### 15. verification_tokens (验证令牌表)

| 列名 | 数据类型 | 是否可空 | 默认值 | 键类型 | 额外信息 | 注释 |
|------|---------|---------|--------|--------|---------|------|
| id | bigint | NO | NULL | PRI | auto_increment | |
| email | varchar | NO | NULL | MUL | | |
| token | varchar | NO | NULL | MUL | | |
| type | varchar | NO | NULL | | | |
| expires_at | timestamp | NO | NULL | | | |
| created_at | timestamp | YES | CURRENT_TIMESTAMP | | DEFAULT_GENERATED | |

---

## 表关系说明

### 核心业务表
- **users**: 用户基础信息表
- **notes**: 笔记表，关联到 notebooks
- **notebooks**: 笔记本表，关联到 note_spaces
- **note_spaces**: 笔记空间表，关联到 users 和 tags

### 关联关系表
- **user_follow**: 用户关注关系
- **user_favorite_note**: 用户收藏笔记关系
- **user_roles**: 用户角色关联
- **conversation_relation**: 会话关系（关联 MongoDB）

### 统计和审核表
- **note_stats**: 笔记统计数据
- **note_stats_compensation**: 笔记统计补偿（用于异步更新）
- **note_moderation**: 笔记审核记录

### 基础数据表
- **tags**: 标签表
- **roles**: 角色表

### 系统表
- **flyway_schema_history**: 数据库迁移历史
- **verification_tokens**: 验证令牌（用于邮箱验证等）

---

## 键类型说明

- **PRI**: 主键 (Primary Key)
- **UNI**: 唯一键 (Unique Key)
- **MUL**: 索引键 (Multiple Key / Index)

---

*文档生成时间: 2024年*
*数据库: MySQL*
*数据库名: ebook_platform*
