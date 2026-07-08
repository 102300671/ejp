# 学生社团管理系统 - 物理结构设计

---

## 1. 数据库环境说明

### 1.1 数据库管理系统

| 参数 | 值 |
| :--- | :--- |
| 数据库系统 | MySQL 8.0+ |
| 字符集 | utf8mb4 |
| 排序规则 | utf8mb4_unicode_ci |
| 存储引擎 | InnoDB |
| 事务隔离级别 | REPEATABLE READ（默认） |

### 1.2 服务器配置建议

| 参数 | 建议值 | 说明 |
| :--- | :--- | :--- |
| innodb_buffer_pool_size | 系统内存的50%-70% | InnoDB缓冲池大小 |
| innodb_log_file_size | 256MB-1GB | 重做日志文件大小 |
| innodb_log_buffer_size | 64MB | 日志缓冲区大小 |
| max_connections | 1000 | 最大连接数 |
| query_cache_type | OFF | 查询缓存（MySQL 8.0已移除） |
| tmp_table_size | 64MB | 临时表大小 |
| max_heap_table_size | 64MB | 堆表大小 |

---

## 2. 事务分析

### 2.1 事务列表

| 事务编号 | 事务名称 | 功能描述 | 涉及表 |
| :--- | :--- | :--- | :--- |
| T-001 | 用户注册 | 用户提交注册信息，系统验证并创建用户记录 | user |
| T-002 | 用户登录 | 用户验证身份，生成会话令牌 | user, user_uuid |
| T-003 | 创建社团 | 用户发起社团创建申请 | club |
| T-004 | 审核社团 | 系统管理员审核社团申请 | club |
| T-005 | 申请入社 | 用户提交入社申请 | join_requests |
| T-006 | 审核入社申请 | 社团管理员审核入社申请 | join_requests, club_member, conversation_member |
| T-007 | 创建活动 | 社团管理员创建活动 | activities |
| T-008 | 发布活动 | 发布活动并更新状态 | activities |
| T-009 | 报名活动 | 用户报名参加活动 | activity_registrations, activities |
| T-010 | 审核报名 | 活动管理员审核报名 | activity_registrations, activities |
| T-011 | 活动签到 | 用户在活动现场签到 | activity_registrations |
| T-012 | 创建公告 | 创建公告并保存为草稿 | announcements |
| T-013 | 发布公告 | 发布公告并更新状态 | announcements |
| T-014 | 发送消息 | 用户发送消息到会话 | messages, conversation |
| T-015 | 创建群聊 | 创建社团或活动的群聊会话 | conversation, conversation_member |

### 2.2 事务详细设计

#### T-001: 用户注册

**事务流程**：
1. 检查学号/工号是否已存在
2. 检查用户名是否已存在
3. 验证密码强度（业务层）
4. 对密码进行BCrypt加密
5. 插入用户记录
6. 返回注册成功

**事务特性**：
- 原子性：所有操作必须全部成功或全部回滚
- 一致性：确保用户数据完整且唯一约束不被破坏
- 隔离性：使用默认隔离级别
- 持久性：事务提交后数据永久保存

**SQL实现**：
```sql
START TRANSACTION;

-- 检查学号是否已存在
SELECT COUNT(*) INTO @student_id_count FROM user WHERE student_id = '2024001';
IF @student_id_count > 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '学号/工号已存在';
END IF;

-- 检查用户名是否已存在
SELECT COUNT(*) INTO @username_count FROM user WHERE username = 'zhangsan';
IF @username_count > 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户名已存在';
END IF;

-- 插入用户记录（密码已在应用层加密）
INSERT INTO user (username, password, real_name, student_id, role, status)
VALUES ('zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', '2024001', 'STUDENT', 'ACTIVE');

COMMIT;
```

#### T-002: 用户登录

**事务流程**：
1. 根据学号/工号查询用户
2. 验证密码（BCrypt）
3. 检查账号状态
4. 删除旧的会话令牌
5. 生成新的会话令牌
6. 更新最后登录时间
7. 返回登录成功

**SQL实现**：
```sql
START TRANSACTION;

-- 查询用户
SELECT id, password, status INTO @user_id, @hashed_password, @status 
FROM user WHERE student_id = '2024001';

-- 验证密码（在应用层使用BCrypt验证）
-- 检查状态
IF @status != 'ACTIVE' THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '账号状态异常';
END IF;

-- 删除旧令牌
DELETE FROM user_uuid WHERE user_id = @user_id;

-- 插入新令牌
INSERT INTO user_uuid (user_id, uuid, issued_at)
VALUES (@user_id, 'new-uuid-token', NOW());

-- 更新最后登录时间
UPDATE user SET last_login_time = NOW() WHERE id = @user_id;

COMMIT;
```

#### T-005: 申请入社

**事务流程**：
1. 检查用户是否已在社团中
2. 检查是否已有待审核的申请
3. 插入入社申请记录

**SQL实现**：
```sql
START TRANSACTION;

-- 检查是否已在社团中
SELECT COUNT(*) INTO @member_count FROM club_member WHERE club_id = 1 AND user_id = 2;
IF @member_count > 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已加入该社团';
END IF;

-- 检查是否已有待审核申请
SELECT COUNT(*) INTO @request_count FROM join_requests WHERE club_id = 1 AND user_id = 2 AND status = 'PENDING';
IF @request_count > 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已有待审核的申请';
END IF;

-- 插入申请记录
INSERT INTO join_requests (user_id, club_id, reason, status)
VALUES (2, 1, '我对计算机技术很感兴趣', 'PENDING');

COMMIT;
```

#### T-006: 审核入社申请

**事务流程**：
1. 查询申请记录
2. 更新申请状态
3. 如果通过，添加到社团成员表
4. 添加到社团群聊
5. 更新社团成员计数

**SQL实现**：
```sql
START TRANSACTION;

-- 更新申请状态
UPDATE join_requests 
SET status = 'APPROVED', reviewed_by = 1, reviewed_at = NOW()
WHERE id = 1;

-- 获取申请信息
SELECT user_id, club_id INTO @user_id, @club_id FROM join_requests WHERE id = 1;

-- 添加到社团成员
INSERT INTO club_member (club_id, user_id, role, status, approved_at)
VALUES (@club_id, @user_id, 'MEMBER', 'APPROVED', NOW());

-- 获取社团群聊ID
SELECT id INTO @conversation_id FROM conversation WHERE club_id = @club_id AND type = 'CLUB';

-- 添加到群聊
INSERT INTO conversation_member (conversation_id, user_id, role)
VALUES (@conversation_id, @user_id, 'MEMBER');

COMMIT;
```

#### T-009: 报名活动

**事务流程**：
1. 检查活动状态是否为已发布
2. 检查报名是否已截止
3. 检查是否已报名
4. 检查报名人数是否已满
5. 插入报名记录
6. 更新活动当前报名人数

**SQL实现**：
```sql
START TRANSACTION;

-- 检查活动状态
SELECT status, max_participants, current_participants, registration_deadline 
INTO @status, @max_participants, @current_participants, @deadline 
FROM activities WHERE id = 1;

IF @status != 'PUBLISHED' THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '活动未发布';
END IF;

IF @deadline IS NOT NULL AND NOW() > @deadline THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '报名已截止';
END IF;

-- 检查是否已报名
SELECT COUNT(*) INTO @reg_count FROM activity_registrations WHERE activity_id = 1 AND user_id = 2;
IF @reg_count > 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已报名该活动';
END IF;

-- 检查人数是否已满
IF @max_participants > 0 AND @current_participants >= @max_participants THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '报名人数已满';
END IF;

-- 插入报名记录
INSERT INTO activity_registrations (activity_id, user_id, real_name, student_id, status)
VALUES (1, 2, '张三', '2024001', 'PENDING');

-- 更新报名人数
UPDATE activities SET current_participants = current_participants + 1 WHERE id = 1;

COMMIT;
```

#### T-014: 发送消息

**事务流程**：
1. 检查用户是否在会话中
2. 插入消息记录
3. 更新会话最后更新时间

**SQL实现**：
```sql
START TRANSACTION;

-- 检查用户是否在会话中
SELECT COUNT(*) INTO @member_count FROM conversation_member WHERE conversation_id = 1 AND user_id = 2;
IF @member_count = 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '用户不在该会话中';
END IF;

-- 插入消息
INSERT INTO messages (type, user_id, conversation_id, content, create_time)
VALUES ('TEXT', 2, 1, 'Hello World', NOW());

-- 更新会话最后更新时间
UPDATE conversation SET updated_at = NOW() WHERE id = 1;

COMMIT;
```

---

## 3. 索引设计

### 3.1 现有索引

根据数据库schema，系统已定义以下索引：

| 表名 | 索引名 | 索引列 | 索引类型 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| user | PRIMARY | id | 主键索引 | 用户ID |
| user | username | username | 唯一索引 | 用户名唯一 |
| user | idx_role | role | 普通索引 | 按角色查询 |
| user | idx_status | status | 普通索引 | 按状态查询 |
| club | PRIMARY | id | 主键索引 | 社团ID |
| club | name | name | 唯一索引 | 社团名称唯一 |
| club | idx_category | category | 普通索引 | 按类别查询 |
| club | idx_status | status | 普通索引 | 按状态查询 |
| club | idx_founder_id | founder_id | 普通索引 | 按创始人查询 |
| club_member | PRIMARY | id | 主键索引 | 记录ID |
| club_member | unique_club_member | (club_id, user_id) | 唯一索引 | 用户在社团中唯一 |
| club_member | idx_user_id | user_id | 普通索引 | 按用户查询 |
| club_member | idx_club_id | club_id | 普通索引 | 按社团查询 |
| club_member | idx_role | role | 普通索引 | 按角色查询 |
| club_member | idx_status | status | 普通索引 | 按状态查询 |
| activities | PRIMARY | id | 主键索引 | 活动ID |
| activities | idx_club_id | club_id | 普通索引 | 按社团查询 |
| activities | idx_status | status | 普通索引 | 按状态查询 |
| activities | idx_start_time | start_time | 普通索引 | 按时间排序 |
| activities | idx_created_by | created_by | 普通索引 | 按创建者查询 |
| activity_registrations | PRIMARY | id | 主键索引 | 记录ID |
| activity_registrations | unique_activity_registration | (activity_id, user_id) | 唯一索引 | 用户在活动中唯一 |
| activity_registrations | idx_activity_id | activity_id | 普通索引 | 按活动查询 |
| activity_registrations | idx_user_id | user_id | 普通索引 | 按用户查询 |
| activity_registrations | idx_status | status | 普通索引 | 按状态查询 |
| activity_registrations | idx_check_in_code | check_in_code | 普通索引 | 按签到码查询 |
| announcements | PRIMARY | id | 主键索引 | 公告ID |
| announcements | idx_club_id | club_id | 普通索引 | 按社团查询 |
| announcements | idx_status | status | 普通索引 | 按状态查询 |
| announcements | idx_priority | priority | 普通索引 | 按优先级查询 |
| announcements | idx_is_pinned | is_pinned | 普通索引 | 按置顶查询 |
| join_requests | PRIMARY | id | 主键索引 | 记录ID |
| join_requests | unique_pending_request | (user_id, club_id, status) | 唯一索引 | 避免重复申请 |
| join_requests | idx_user_id | user_id | 普通索引 | 按用户查询 |
| join_requests | idx_club_id | club_id | 普通索引 | 按社团查询 |
| join_requests | idx_status | status | 普通索引 | 按状态查询 |
| conversation | PRIMARY | id | 主键索引 | 会话ID |
| conversation | idx_type | type | 普通索引 | 按类型查询 |
| conversation | idx_club_id | club_id | 普通索引 | 按社团查询 |
| conversation | idx_activity_id | activity_id | 普通索引 | 按活动查询 |
| conversation_member | PRIMARY | (conversation_id, user_id) | 复合主键 | 会话成员唯一 |
| conversation_member | idx_conversation_id | conversation_id | 普通索引 | 按会话查询 |
| conversation_member | idx_user_id | user_id | 普通索引 | 按用户查询 |
| messages | PRIMARY | id | 主键索引 | 消息ID |
| messages | idx_user_id | user_id | 普通索引 | 按发送者查询 |
| messages | idx_conversation_id | conversation_id | 普通索引 | 按会话查询 |
| messages | idx_create_time | create_time | 普通索引 | 按时间排序 |
| user_uuid | PRIMARY | user_id | 主键索引 | 用户ID |
| user_uuid | uuid | uuid | 唯一索引 | 令牌唯一 |

### 3.2 建议新增索引

根据查询模式分析，建议新增以下索引：

| 表名 | 索引名 | 索引列 | 索引类型 | 理由 |
| :--- | :--- | :--- | :--- | :--- |
| user | idx_student_id | student_id | 普通索引 | 登录时按学号/工号查询，提高登录性能 |
| user | idx_real_name | real_name | 普通索引 | 按姓名搜索用户时使用 |
| club | idx_founder_id_status | (founder_id, status) | 复合索引 | 创始人查看自己创建的社团 |
| activities | idx_activity_type_status | (activity_type, status) | 复合索引 | 按类型和状态筛选活动 |
| activities | idx_club_id_status | (club_id, status) | 复合索引 | 查看社团的活动列表 |
| activity_registrations | idx_activity_id_status | (activity_id, status) | 复合索引 | 查看活动的报名列表并按状态筛选 |
| announcements | idx_club_id_status_pinned | (club_id, status, is_pinned) | 复合索引 | 查看社团公告并按状态和置顶排序 |
| messages | idx_conversation_id_create_time | (conversation_id, create_time) | 复合索引 | 查询会话历史消息，按时间排序 |

**新增索引SQL**：
```sql
-- user表
CREATE INDEX idx_student_id ON user(student_id);
CREATE INDEX idx_real_name ON user(real_name);

-- club表
CREATE INDEX idx_founder_id_status ON club(founder_id, status);

-- activities表
CREATE INDEX idx_activity_type_status ON activities(activity_type, status);
CREATE INDEX idx_club_id_status ON activities(club_id, status);

-- activity_registrations表
CREATE INDEX idx_activity_id_status ON activity_registrations(activity_id, status);

-- announcements表
CREATE INDEX idx_club_id_status_pinned ON announcements(club_id, status, is_pinned);

-- messages表
CREATE INDEX idx_conversation_id_create_time ON messages(conversation_id, create_time);
```

### 3.3 索引使用分析

#### 查询场景分析

| 查询场景 | 涉及表 | 查询条件 | 使用索引 | 性能影响 |
| :--- | :--- | :--- | :--- | :--- |
| 用户登录 | user | student_id = ? | idx_student_id（建议） | 高，频繁操作 |
| 用户搜索 | user | real_name LIKE ? | idx_real_name（建议） | 中，管理后台使用 |
| 社团列表 | club | status = 'APPROVED' | idx_status | 高，首页展示 |
| 社团详情 | club_member | club_id = ? | idx_club_id | 高，查看成员 |
| 活动列表 | activities | status = 'PUBLISHED' | idx_status | 高，首页展示 |
| 活动详情 | activity_registrations | activity_id = ? | idx_activity_id | 中，查看报名 |
| 公告列表 | announcements | status = 'PUBLISHED' | idx_status | 中，首页展示 |
| 消息历史 | messages | conversation_id = ?, create_time < ? | idx_conversation_id_create_time（建议） | 高，频繁操作 |
| 入社申请 | join_requests | club_id = ?, status = 'PENDING' | idx_club_id + idx_status | 中，审核操作 |

#### 索引优化原则

1. **避免过多索引**：索引会降低写入性能，只在频繁查询的列上创建索引
2. **复合索引顺序**：将选择性高的列放在前面
3. **覆盖索引**：如果查询只需要索引列，可以避免回表查询
4. **前缀索引**：对于长字符串列，使用前缀索引减少索引大小
5. **定期维护**：定期重建索引，优化查询性能

---

## 4. 存储安排

### 4.1 表空间设计

#### 4.1.1 默认表空间

所有表默认使用 `stuclub_db` 数据库的默认表空间。

#### 4.1.2 分区表设计（可选）

对于数据量大的表，可以考虑分区：

**messages表分区（按时间）**：
```sql
ALTER TABLE messages 
PARTITION BY RANGE (TO_DAYS(create_time)) (
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

**activity_registrations表分区（按活动ID范围）**：
```sql
ALTER TABLE activity_registrations 
PARTITION BY RANGE (activity_id) (
    PARTITION p0_100 VALUES LESS THAN (101),
    PARTITION p101_200 VALUES LESS THAN (201),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### 4.2 数据文件存储

#### 4.2.1 MySQL数据目录

| 文件类型 | 默认路径 | 建议配置 |
| :--- | :--- | :--- |
| 数据文件 | /var/lib/mysql/ | 独立磁盘分区 |
| 日志文件 | /var/lib/mysql/ | 独立磁盘分区 |
| 临时文件 | /tmp/ | 独立磁盘分区 |

#### 4.2.2 存储设备建议

| 用途 | 推荐存储类型 | 容量建议 |
| :--- | :--- | :--- |
| 数据文件 | SSD | 50GB+ |
| 日志文件 | SSD | 20GB+ |
| 备份文件 | HDD | 200GB+ |

### 4.3 日志管理

#### 4.3.1 二进制日志（Binary Log）

| 参数 | 建议值 | 说明 |
| :--- | :--- | :--- |
| log_bin | ON | 开启二进制日志 |
| log_bin_basename | /var/lib/mysql/binlog | 日志文件前缀 |
| expire_logs_days | 7 | 日志保留天数 |
| max_binlog_size | 100MB | 单个日志文件大小 |

**用途**：
- 数据恢复
- 主从复制

#### 4.3.2 错误日志（Error Log）

| 参数 | 建议值 | 说明 |
| :--- | :--- | :--- |
| log_error | /var/log/mysql/error.log | 错误日志路径 |

**用途**：
- 故障诊断
- 性能问题排查

#### 4.3.3 慢查询日志（Slow Query Log）

| 参数 | 建议值 | 说明 |
| :--- | :--- | :--- |
| slow_query_log | ON | 开启慢查询日志 |
| slow_query_log_file | /var/log/mysql/slow.log | 慢查询日志路径 |
| long_query_time | 2 | 慢查询阈值（秒） |
| log_queries_not_using_indexes | ON | 记录未使用索引的查询 |

**用途**：
- 性能优化
- 查询分析

---

## 5. 备份与恢复策略

### 5.1 备份策略

#### 5.1.1 全量备份

| 参数 | 值 | 说明 |
| :--- | :--- | :--- |
| 频率 | 每日凌晨2:00 | 使用crontab定时执行 |
| 工具 | mysqldump | MySQL官方备份工具 |
| 保留 | 保留最近7天 | 超过7天自动删除 |

**备份命令**：
```bash
mysqldump -u root -p --databases stuclub_db --single-transaction --routines --triggers > /backup/stuclub_db_$(date +%Y%m%d).sql
```

#### 5.1.2 增量备份

| 参数 | 值 | 说明 |
| :--- | :--- | :--- |
| 频率 | 每小时 | 基于二进制日志 |
| 工具 | mysqlbinlog | 二进制日志解析工具 |
| 保留 | 保留最近24小时 | 超过24小时自动删除 |

**增量恢复命令**：
```bash
mysqlbinlog binlog.000001 binlog.000002 | mysql -u root -p stuclub_db
```

#### 5.1.3 备份验证

每次备份后验证备份文件的完整性：
```bash
mysqlcheck --check --all-databases
```

### 5.2 恢复策略

#### 5.2.1 全量恢复

```bash
mysql -u root -p stuclub_db < /backup/stuclub_db_20260626.sql
```

#### 5.2.2 增量恢复

```bash
# 先恢复全量备份
mysql -u root -p stuclub_db < /backup/stuclub_db_20260626.sql

# 再应用增量日志
mysqlbinlog binlog.000001 binlog.000002 | mysql -u root -p stuclub_db
```

#### 5.2.3 单点恢复

恢复特定表：
```bash
mysql -u root -p stuclub_db < /backup/stuclub_db_20260626.sql --tables user
```

---

## 6. 性能优化建议

### 6.1 查询优化

| 优化项 | 说明 | 示例 |
| :--- | :--- | :--- |
| 使用索引 | 确保查询条件列有索引 | WHERE student_id = ? |
| 避免SELECT * | 只查询需要的列 | SELECT id, name FROM club |
| 限制结果集 | 使用LIMIT限制返回行数 | LIMIT 20 |
| 避免子查询 | 使用JOIN替代子查询 | JOIN club c ON a.club_id = c.id |
| 使用批量操作 | 批量插入/更新减少连接开销 | INSERT INTO ... VALUES (...), (...) |

### 6.2 连接优化

| 参数 | 建议值 | 说明 |
| :--- | :--- | :--- |
| wait_timeout | 600 | 连接空闲超时时间（秒） |
| interactive_timeout | 600 | 交互式连接超时时间（秒） |
| connection_pool_size | 100 | 连接池大小 |

### 6.3 缓存优化

| 缓存类型 | 说明 | 实现方式 |
| :--- | :--- | :--- |
| 查询缓存 | MySQL 8.0已移除 | 应用层缓存（Redis） |
| 对象缓存 | 缓存热点数据 | Redis缓存社团、活动信息 |
| 页面缓存 | 缓存静态页面 | CDN或反向代理缓存 |

### 6.4 读写分离（可选）

当系统负载较高时，可以考虑读写分离：

| 角色 | 服务器 | 职责 |
| :--- | :--- | :--- |
| 主服务器 | Master | 处理写操作 |
| 从服务器 | Slave | 处理读操作 |

**配置示例**：
```sql
-- 在主服务器上配置
CHANGE MASTER TO 
    MASTER_HOST='master.example.com',
    MASTER_USER='repl',
    MASTER_PASSWORD='password',
    MASTER_LOG_FILE='binlog.000001',
    MASTER_LOG_POS=107;
```

---

## 7. 安全策略

### 7.1 访问控制

| 用户 | 权限 | 用途 |
| :--- | :--- | :--- |
| admin | 全部权限 | 系统管理员 |
| application | SELECT, INSERT, UPDATE, DELETE | 应用程序连接 |
| read_only | SELECT | 只读查询 |

**权限分配**：
```sql
GRANT ALL PRIVILEGES ON stuclub_db.* TO 'admin'@'localhost' IDENTIFIED BY 'password';
GRANT SELECT, INSERT, UPDATE, DELETE ON stuclub_db.* TO 'application'@'localhost' IDENTIFIED BY 'password';
GRANT SELECT ON stuclub_db.* TO 'read_only'@'localhost' IDENTIFIED BY 'password';
```

### 7.2 数据加密

| 加密类型 | 实现方式 | 说明 |
| :--- | :--- | :--- |
| 密码加密 | BCrypt | 用户密码存储使用BCrypt加密 |
| 传输加密 | SSL/TLS | 数据库连接使用SSL |
| 敏感数据加密 | AES | 敏感字段（如手机号）应用层加密 |

**SSL配置**：
```ini
[mysqld]
ssl-ca=/etc/mysql/cacert.pem
ssl-cert=/etc/mysql/server-cert.pem
ssl-key=/etc/mysql/server-key.pem
```

### 7.3 审计日志

开启审计日志记录所有数据库操作：
```sql
-- 使用MySQL Enterprise Audit（商业版）
-- 或使用第三方工具如Percona Audit Log Plugin
```

---

*文档版本：1.0*  
*创建日期：2026年6月*  
*适用系统：学生社团管理系统 (stuclub)