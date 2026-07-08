# 学生社团管理系统 - 逻辑结构设计

---

## 1. ER图到关系模型的转换

### 1.1 转换规则

根据ER图转换为关系模型的标准规则：

| 规则 | 说明 |
| :--- | :--- |
| 实体转换 | 每个实体转换为一个关系（表） |
| 属性转换 | 实体的每个属性转换为关系的列 |
| 主键转换 | 实体的主键转换为关系的主键 |
| 1:N关系转换 | 在N端关系中添加外键，引用1端关系的主键 |
| 1:1关系转换 | 在任意一端添加外键，引用另一端的主键 |
| M:N关系转换 | 创建中间关系（连接表），包含两端实体的主键作为外键 |
| 弱实体转换 | 添加强实体的主键作为外键，并与弱实体自身的部分键组成复合主键 |

### 1.2 转换结果

#### 1.2.1 用户实体 → user表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 用户唯一标识 |
| username | username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名/登录账号 |
| password | password | VARCHAR(255) | NOT NULL | 密码（BCrypt加密） |
| real_name | real_name | VARCHAR(50) | NULL | 真实姓名 |
| student_id | student_id | VARCHAR(20) | NULL, UNIQUE | 学号/工号 |
| phone | phone | VARCHAR(20) | NULL | 联系电话 |
| email | email | VARCHAR(100) | NULL | 邮箱 |
| avatar_url | avatar_url | VARCHAR(255) | NULL | 头像URL |
| role | role | ENUM('STUDENT','ADMIN','SUPER_ADMIN') | NOT NULL, DEFAULT 'STUDENT' | 用户角色 |
| status | status | ENUM('ACTIVE','INACTIVE','SUSPENDED') | NOT NULL, DEFAULT 'ACTIVE' | 账号状态 |
| created_at | created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| last_login_time | last_login_time | TIMESTAMP | NULL | 最后登录时间 |

**关系模式**：
```
user(id, username, password, real_name, student_id, phone, email, avatar_url, role, status, created_at, updated_at, last_login_time)
```

**主键**：`id`  
**候选键**：`username`, `student_id`  
**外键**：无

#### 1.2.2 社团实体 → club表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 社团唯一标识 |
| name | name | VARCHAR(100) | NOT NULL, UNIQUE | 社团名称 |
| description | description | TEXT | NULL | 社团简介 |
| category | category | VARCHAR(50) | NULL | 社团类别 |
| logo_url | logo_url | VARCHAR(255) | NULL | 社团logo URL |
| advisor | advisor | VARCHAR(50) | NULL | 指导老师 |
| advisor_contact | advisor_contact | VARCHAR(100) | NULL | 指导老师联系方式 |
| max_members | max_members | INT | DEFAULT 100 | 最大成员数 |
| status | status | ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') | NOT NULL, DEFAULT 'PENDING' | 社团状态 |
| founder_id | founder_id | INT | FOREIGN KEY REFERENCES user(id) | 创始人ID |
| created_at | created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| approved_at | approved_at | TIMESTAMP | NULL | 审核通过时间 |

**关系模式**：
```
club(id, name, description, category, logo_url, advisor, advisor_contact, max_members, status, founder_id, created_at, updated_at, approved_at)
```

**主键**：`id`  
**候选键**：`name`  
**外键**：`founder_id` → `user(id)`

#### 1.2.3 社团成员关系（M:N）→ club_member表

用户与社团之间是M:N关系，通过club_member表实现。

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 记录唯一标识 |
| club_id | club_id | INT | NOT NULL, FOREIGN KEY REFERENCES club(id) | 社团ID |
| user_id | user_id | INT | NOT NULL, FOREIGN KEY REFERENCES user(id) | 用户ID |
| role | role | ENUM('PRESIDENT','VICE_PRESIDENT','ADMIN','MEMBER') | NOT NULL, DEFAULT 'MEMBER' | 成员角色 |
| join_type | join_type | ENUM('APPLY','INVITE') | NOT NULL, DEFAULT 'APPLY' | 加入方式 |
| join_reason | join_reason | VARCHAR(255) | NULL | 加入理由 |
| status | status | ENUM('PENDING','APPROVED','REJECTED','QUIT','EXPELLED') | NOT NULL, DEFAULT 'PENDING' | 成员状态 |
| display_name | display_name | VARCHAR(50) | NULL | 显示名称 |
| joined_at | joined_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 申请时间 |
| approved_at | approved_at | TIMESTAMP | NULL | 审核通过时间 |
| quit_at | quit_at | TIMESTAMP | NULL | 退出时间 |

**关系模式**：
```
club_member(id, club_id, user_id, role, join_type, join_reason, status, display_name, joined_at, approved_at, quit_at)
```

**主键**：`id`  
**唯一约束**：`(club_id, user_id)`  
**外键**：`club_id` → `club(id)`, `user_id` → `user(id)`

#### 1.2.4 入社申请关系（M:N）→ join_requests表

用户与社团之间的入社申请关系，通过join_requests表实现。

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 记录唯一标识 |
| user_id | user_id | INT | NOT NULL, FOREIGN KEY REFERENCES user(id) | 申请用户ID |
| club_id | club_id | INT | NOT NULL, FOREIGN KEY REFERENCES club(id) | 申请社团ID |
| reason | reason | TEXT | NULL | 申请理由 |
| status | status | ENUM('PENDING','APPROVED','REJECTED') | NOT NULL, DEFAULT 'PENDING' | 申请状态 |
| reviewed_by | reviewed_by | INT | FOREIGN KEY REFERENCES user(id) | 审核人ID |
| reviewed_at | reviewed_at | TIMESTAMP | NULL | 审核时间 |
| created_at | created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**关系模式**：
```
join_requests(id, user_id, club_id, reason, status, reviewed_by, reviewed_at, created_at, updated_at)
```

**主键**：`id`  
**唯一约束**：`(user_id, club_id, status)`  
**外键**：`user_id` → `user(id)`, `club_id` → `club(id)`, `reviewed_by` → `user(id)`

#### 1.2.5 活动实体 → activities表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 活动唯一标识 |
| club_id | club_id | INT | NOT NULL, FOREIGN KEY REFERENCES club(id) | 所属社团ID |
| title | title | VARCHAR(200) | NOT NULL | 活动标题 |
| description | description | TEXT | NULL | 活动详情 |
| activity_type | activity_type | ENUM('INTERNAL','CLUB_ONLY','PUBLIC','COMPETITION','TRAINING') | NOT NULL, DEFAULT 'INTERNAL' | 活动类型 |
| start_time | start_time | DATETIME | NOT NULL | 开始时间 |
| end_time | end_time | DATETIME | NOT NULL | 结束时间 |
| location | location | VARCHAR(200) | NULL | 活动地点 |
| max_participants | max_participants | INT | DEFAULT 0 | 最大参与人数 |
| current_participants | current_participants | INT | DEFAULT 0 | 当前报名人数 |
| registration_deadline | registration_deadline | DATETIME | NULL | 报名截止时间 |
| poster_url | poster_url | VARCHAR(255) | NULL | 活动海报URL |
| budget | budget | DECIMAL(10,2) | DEFAULT 0.00 | 活动预算 |
| status | status | ENUM('DRAFT','PUBLISHED','IN_PROGRESS','COMPLETED','CANCELLED') | NOT NULL, DEFAULT 'DRAFT' | 活动状态 |
| created_by | created_by | INT | NOT NULL, FOREIGN KEY REFERENCES user(id) | 创建者ID |
| created_at | created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| published_at | published_at | TIMESTAMP | NULL | 发布时间 |

**关系模式**：
```
activities(id, club_id, title, description, activity_type, start_time, end_time, location, max_participants, current_participants, registration_deadline, poster_url, budget, status, created_by, created_at, updated_at, published_at)
```

**主键**：`id`  
**外键**：`club_id` → `club(id)`, `created_by` → `user(id)`

#### 1.2.6 活动报名关系（M:N）→ activity_registrations表

用户与活动之间的报名关系，通过activity_registrations表实现。

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 记录唯一标识 |
| activity_id | activity_id | INT | NOT NULL, FOREIGN KEY REFERENCES activities(id) | 活动ID |
| user_id | user_id | INT | NOT NULL, FOREIGN KEY REFERENCES user(id) | 用户ID |
| real_name | real_name | VARCHAR(50) | NOT NULL | 真实姓名 |
| student_id | student_id | VARCHAR(20) | NULL | 学号 |
| phone | phone | VARCHAR(20) | NULL | 联系电话 |
| status | status | ENUM('PENDING','APPROVED','REJECTED','CANCELLED') | NOT NULL, DEFAULT 'PENDING' | 报名状态 |
| registration_time | registration_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 报名时间 |
| approval_time | approval_time | DATETIME | NULL | 审核时间 |
| approved_by | approved_by | INT | FOREIGN KEY REFERENCES user(id) | 审核人ID |
| is_checked_in | is_checked_in | BOOLEAN | DEFAULT FALSE | 是否已签到 |
| check_in_time | check_in_time | DATETIME | NULL | 签到时间 |
| check_in_code | check_in_code | VARCHAR(20) | NULL | 签到码 |
| notes | notes | TEXT | NULL | 备注信息 |

**关系模式**：
```
activity_registrations(id, activity_id, user_id, real_name, student_id, phone, status, registration_time, approval_time, approved_by, is_checked_in, check_in_time, check_in_code, notes)
```

**主键**：`id`  
**唯一约束**：`(activity_id, user_id)`  
**外键**：`activity_id` → `activities(id)`, `user_id` → `user(id)`, `approved_by` → `user(id)`

#### 1.2.7 公告实体 → announcements表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 公告唯一标识 |
| club_id | club_id | INT | FOREIGN KEY REFERENCES club(id) | 所属社团（NULL为系统公告） |
| title | title | VARCHAR(200) | NOT NULL | 公告标题 |
| content | content | TEXT | NOT NULL | 公告内容 |
| priority | priority | ENUM('NORMAL','IMPORTANT','URGENT') | NOT NULL, DEFAULT 'NORMAL' | 优先级 |
| is_pinned | is_pinned | BOOLEAN | DEFAULT FALSE | 是否置顶 |
| status | status | ENUM('DRAFT','PUBLISHED','ARCHIVED') | NOT NULL, DEFAULT 'DRAFT' | 状态 |
| created_by | created_by | INT | NOT NULL, FOREIGN KEY REFERENCES user(id) | 创建者ID |
| created_at | created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| published_at | published_at | TIMESTAMP | NULL | 发布时间 |

**关系模式**：
```
announcements(id, club_id, title, content, priority, is_pinned, status, created_by, created_at, updated_at, published_at)
```

**主键**：`id`  
**外键**：`club_id` → `club(id)`, `created_by` → `user(id)`

#### 1.2.8 会话实体 → conversation表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 会话唯一标识 |
| type | type | ENUM('CLUB','PRIVATE','ACTIVITY','SYSTEM') | NOT NULL | 会话类型 |
| name | name | VARCHAR(100) | NULL | 会话名称 |
| club_id | club_id | INT | FOREIGN KEY REFERENCES club(id) | 关联社团ID |
| activity_id | activity_id | INT | FOREIGN KEY REFERENCES activities(id) | 关联活动ID |
| avatar_url | avatar_url | VARCHAR(255) | NULL | 会话头像 |
| created_at | created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | updated_at | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**关系模式**：
```
conversation(id, type, name, club_id, activity_id, avatar_url, created_at, updated_at)
```

**主键**：`id`  
**外键**：`club_id` → `club(id)`, `activity_id` → `activities(id)`

#### 1.2.9 会话成员关系（M:N）→ conversation_member表

用户与会话之间的参与关系，通过conversation_member表实现。

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| conversation_id | conversation_id | INT | PRIMARY KEY, FOREIGN KEY REFERENCES conversation(id) | 会话ID |
| user_id | user_id | INT | PRIMARY KEY, FOREIGN KEY REFERENCES user(id) | 用户ID |
| role | role | ENUM('OWNER','ADMIN','MEMBER') | NOT NULL, DEFAULT 'MEMBER' | 角色 |
| display_name | display_name | VARCHAR(50) | NULL | 显示名称 |
| joined_at | joined_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 加入时间 |
| last_read_time | last_read_time | DATETIME | NULL | 最后已读时间 |

**关系模式**：
```
conversation_member(conversation_id, user_id, role, display_name, joined_at, last_read_time)
```

**主键**：`(conversation_id, user_id)`  
**外键**：`conversation_id` → `conversation(id)`, `user_id` → `user(id)`

#### 1.2.10 消息实体 → messages表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| id | id | INT | PRIMARY KEY, AUTO_INCREMENT | 消息唯一标识 |
| type | type | VARCHAR(20) | NOT NULL | 消息类型 |
| user_id | user_id | INT | NOT NULL, FOREIGN KEY REFERENCES user(id) | 发送者ID |
| conversation_id | conversation_id | INT | NOT NULL, FOREIGN KEY REFERENCES conversation(id) | 会话ID |
| content | content | TEXT | NOT NULL | 消息内容 |
| extra_data | extra_data | TEXT | NULL | 扩展数据（JSON） |
| create_time | create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| is_recalled | is_recalled | BOOLEAN | DEFAULT FALSE | 是否已撤回 |
| recalled_at | recalled_at | DATETIME | NULL | 撤回时间 |

**关系模式**：
```
messages(id, type, user_id, conversation_id, content, extra_data, create_time, is_recalled, recalled_at)
```

**主键**：`id`  
**外键**：`user_id` → `user(id)`, `conversation_id` → `conversation(id)`

#### 1.2.11 会话令牌实体 → user_uuid表

| 属性 | 列名 | 数据类型 | 约束 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| user_id | user_id | INT | PRIMARY KEY, FOREIGN KEY REFERENCES user(id) | 用户ID |
| uuid | uuid | CHAR(36) | NOT NULL, UNIQUE | 会话令牌 |
| device_info | device_info | VARCHAR(255) | NULL | 设备信息 |
| issued_at | issued_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 颁发时间 |
| expires_at | expires_at | TIMESTAMP | NULL | 过期时间 |

**关系模式**：
```
user_uuid(user_id, uuid, device_info, issued_at, expires_at)
```

**主键**：`user_id`  
**唯一约束**：`uuid`  
**外键**：`user_id` → `user(id)`

---

## 2. 关系模式汇总

### 2.1 完整关系模式列表

```
user(id, username, password, real_name, student_id, phone, email, avatar_url, role, status, created_at, updated_at, last_login_time)
club(id, name, description, category, logo_url, advisor, advisor_contact, max_members, status, founder_id, created_at, updated_at, approved_at)
club_member(id, club_id, user_id, role, join_type, join_reason, status, display_name, joined_at, approved_at, quit_at)
join_requests(id, user_id, club_id, reason, status, reviewed_by, reviewed_at, created_at, updated_at)
activities(id, club_id, title, description, activity_type, start_time, end_time, location, max_participants, current_participants, registration_deadline, poster_url, budget, status, created_by, created_at, updated_at, published_at)
activity_registrations(id, activity_id, user_id, real_name, student_id, phone, status, registration_time, approval_time, approved_by, is_checked_in, check_in_time, check_in_code, notes)
announcements(id, club_id, title, content, priority, is_pinned, status, created_by, created_at, updated_at, published_at)
conversation(id, type, name, club_id, activity_id, avatar_url, created_at, updated_at)
conversation_member(conversation_id, user_id, role, display_name, joined_at, last_read_time)
messages(id, type, user_id, conversation_id, content, extra_data, create_time, is_recalled, recalled_at)
user_uuid(user_id, uuid, device_info, issued_at, expires_at)
```

### 2.2 外键关系汇总

| 外键 | 所在表 | 引用表 | 引用列 | 删除行为 |
| :--- | :--- | :--- | :--- | :--- |
| founder_id | club | user | id | SET NULL |
| club_id | club_member | club | id | CASCADE |
| user_id | club_member | user | id | CASCADE |
| user_id | join_requests | user | id | CASCADE |
| club_id | join_requests | club | id | CASCADE |
| reviewed_by | join_requests | user | id | SET NULL |
| club_id | activities | club | id | CASCADE |
| created_by | activities | user | id | - |
| activity_id | activity_registrations | activities | id | CASCADE |
| user_id | activity_registrations | user | id | CASCADE |
| approved_by | activity_registrations | user | id | SET NULL |
| club_id | announcements | club | id | CASCADE |
| created_by | announcements | user | id | - |
| club_id | conversation | club | id | - |
| activity_id | conversation | activities | id | - |
| conversation_id | conversation_member | conversation | id | CASCADE |
| user_id | conversation_member | user | id | CASCADE |
| user_id | messages | user | id | CASCADE |
| conversation_id | messages | conversation | id | CASCADE |
| user_id | user_uuid | user | id | CASCADE |

---

## 3. 范式检查

### 3.1 第一范式（1NF）

所有关系模式均满足第一范式：
- 每个列都是原子值
- 没有重复的列组
- 每行都有唯一标识（主键）

### 3.2 第二范式（2NF）

所有关系模式均满足第二范式：
- 非主键属性完全依赖于主键
- 不存在部分依赖

### 3.3 第三范式（3NF）

所有关系模式均满足第三范式：
- 非主键属性不依赖于其他非主键属性
- 不存在传递依赖

**验证示例**：

| 表名 | 检查项 | 验证结果 |
| :--- | :--- | :--- |
| user | 非主键属性不依赖于其他非主键属性 | 通过 |
| club | founder_id → user(id)，但user信息不在club表中 | 通过 |
| club_member | role、status等直接依赖于(club_id, user_id) | 通过 |
| activities | created_by → user(id)，但user信息不在activities表中 | 通过 |
| activity_registrations | approved_by → user(id)，但user信息不在本表中 | 通过 |

### 3.4 BCNF（巴斯范式）

大部分关系模式满足BCNF：
- 所有决定因素都是候选键
- 不存在非平凡函数依赖

**可能不满足BCNF的表**：
- `club_member`：`role`可能部分依赖于`club_id`（同一社团的成员角色有固定含义），但这不影响数据一致性

---

## 4. 完整性约束

### 4.1 实体完整性

| 表名 | 主键约束 | 说明 |
| :--- | :--- | :--- |
| user | PRIMARY KEY(id) | 用户ID非空且唯一 |
| club | PRIMARY KEY(id) | 社团ID非空且唯一 |
| club_member | PRIMARY KEY(id) | 记录ID非空且唯一 |
| join_requests | PRIMARY KEY(id) | 记录ID非空且唯一 |
| activities | PRIMARY KEY(id) | 活动ID非空且唯一 |
| activity_registrations | PRIMARY KEY(id) | 记录ID非空且唯一 |
| announcements | PRIMARY KEY(id) | 公告ID非空且唯一 |
| conversation | PRIMARY KEY(id) | 会话ID非空且唯一 |
| conversation_member | PRIMARY KEY(conversation_id, user_id) | 复合主键 |
| messages | PRIMARY KEY(id) | 消息ID非空且唯一 |
| user_uuid | PRIMARY KEY(user_id) | 用户ID非空且唯一 |

### 4.2 参照完整性

| 外键 | 参照表 | 删除规则 | 更新规则 |
| :--- | :--- | :--- | :--- |
| club.founder_id | user(id) | SET NULL | CASCADE |
| club_member.club_id | club(id) | CASCADE | CASCADE |
| club_member.user_id | user(id) | CASCADE | CASCADE |
| join_requests.user_id | user(id) | CASCADE | CASCADE |
| join_requests.club_id | club(id) | CASCADE | CASCADE |
| join_requests.reviewed_by | user(id) | SET NULL | CASCADE |
| activities.club_id | club(id) | CASCADE | CASCADE |
| activities.created_by | user(id) | RESTRICT | CASCADE |
| activity_registrations.activity_id | activities(id) | CASCADE | CASCADE |
| activity_registrations.user_id | user(id) | CASCADE | CASCADE |
| activity_registrations.approved_by | user(id) | SET NULL | CASCADE |
| announcements.club_id | club(id) | CASCADE | CASCADE |
| announcements.created_by | user(id) | RESTRICT | CASCADE |
| conversation.club_id | club(id) | RESTRICT | CASCADE |
| conversation.activity_id | activities(id) | RESTRICT | CASCADE |
| conversation_member.conversation_id | conversation(id) | CASCADE | CASCADE |
| conversation_member.user_id | user(id) | CASCADE | CASCADE |
| messages.user_id | user(id) | CASCADE | CASCADE |
| messages.conversation_id | conversation(id) | CASCADE | CASCADE |
| user_uuid.user_id | user(id) | CASCADE | CASCADE |

### 4.3 域完整性

| 表名 | 列名 | 域约束 |
| :--- | :--- | :--- |
| user | role | ENUM('STUDENT','ADMIN','SUPER_ADMIN') |
| user | status | ENUM('ACTIVE','INACTIVE','SUSPENDED') |
| club | status | ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') |
| club_member | role | ENUM('PRESIDENT','VICE_PRESIDENT','ADMIN','MEMBER') |
| club_member | join_type | ENUM('APPLY','INVITE') |
| club_member | status | ENUM('PENDING','APPROVED','REJECTED','QUIT','EXPELLED') |
| activities | activity_type | ENUM('INTERNAL','CLUB_ONLY','PUBLIC','COMPETITION','TRAINING') |
| activities | status | ENUM('DRAFT','PUBLISHED','IN_PROGRESS','COMPLETED','CANCELLED') |
| activity_registrations | status | ENUM('PENDING','APPROVED','REJECTED','CANCELLED') |
| announcements | priority | ENUM('NORMAL','IMPORTANT','URGENT') |
| announcements | status | ENUM('DRAFT','PUBLISHED','ARCHIVED') |
| conversation | type | ENUM('CLUB','PRIVATE','ACTIVITY','SYSTEM') |
| conversation_member | role | ENUM('OWNER','ADMIN','MEMBER') |

### 4.4 用户自定义完整性

| 约束编号 | 约束描述 | 实现方式 |
| :--- | :--- | :--- |
| UC-001 | 学号/工号唯一 | UNIQUE KEY(student_id) |
| UC-002 | 用户名唯一 | UNIQUE KEY(username) |
| UC-003 | 社团名称唯一 | UNIQUE KEY(name) |
| UC-004 | 用户不能重复加入同一社团 | UNIQUE KEY(club_id, user_id) |
| UC-005 | 用户不能重复报名同一活动 | UNIQUE KEY(activity_id, user_id) |
| UC-006 | 用户不能同时存在多个待审核的入社申请 | UNIQUE KEY(user_id, club_id, status) |
| UC-007 | 会话令牌唯一 | UNIQUE KEY(uuid) |
| UC-008 | 密码非空 | NOT NULL |
| UC-009 | 活动标题非空 | NOT NULL |
| UC-010 | 公告标题和内容非空 | NOT NULL |

---

## 5. 逻辑设计验证

### 5.1 数据插入验证

**验证场景**：创建一个社团并添加成员

```sql
-- 1. 创建用户（创始人）
INSERT INTO user (username, password, real_name, student_id, role) 
VALUES ('zhangsan', 'password123', '张三', '2024001', 'STUDENT');

-- 2. 创建社团
INSERT INTO club (name, description, category, founder_id, status)
VALUES ('计算机协会', '学术科技类社团', '学术科技', 1, 'APPROVED');

-- 3. 添加创始人作为社长
INSERT INTO club_member (club_id, user_id, role, status)
VALUES (1, 1, 'PRESIDENT', 'APPROVED');

-- 4. 创建社团群聊
INSERT INTO conversation (type, name, club_id)
VALUES ('CLUB', '计算机协会群聊', 1);

-- 5. 添加创始人到群聊
INSERT INTO conversation_member (conversation_id, user_id, role)
VALUES (1, 1, 'OWNER');
```

### 5.2 数据查询验证

**验证场景**：查询某个用户加入的所有社团

```sql
SELECT c.id, c.name, c.category, cm.role, cm.status
FROM club c
JOIN club_member cm ON c.id = cm.club_id
WHERE cm.user_id = 1;
```

**验证场景**：查询某个活动的所有报名人员

```sql
SELECT ar.id, u.real_name, u.student_id, ar.status, ar.registration_time
FROM activity_registrations ar
JOIN user u ON ar.user_id = u.id
WHERE ar.activity_id = 1;
```

### 5.3 数据更新验证

**验证场景**：审核入社申请

```sql
-- 审核通过
UPDATE join_requests 
SET status = 'APPROVED', reviewed_by = 1, reviewed_at = NOW()
WHERE id = 1;

-- 同时添加到社团成员表
INSERT INTO club_member (club_id, user_id, role, status)
SELECT club_id, user_id, 'MEMBER', 'APPROVED'
FROM join_requests
WHERE id = 1;
```

### 5.4 数据删除验证

**验证场景**：删除一个社团（级联删除测试）

```sql
-- 删除社团，应级联删除：club_member, activities, announcements, conversation_member, messages
DELETE FROM club WHERE id = 1;
```

---

*文档版本：1.0*  
*创建日期：2026年6月*  
*适用系统：学生社团管理系统 (stuclub)