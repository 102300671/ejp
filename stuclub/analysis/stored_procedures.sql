# 学生社团管理系统 - 存储过程

---

## 1. 用户管理存储过程

### 1.1 注册用户

```sql
DELIMITER //

CREATE PROCEDURE sp_register_user(
    IN p_username VARCHAR(50),
    IN p_password VARCHAR(255),
    IN p_real_name VARCHAR(50),
    IN p_student_id VARCHAR(20),
    IN p_role VARCHAR(20),
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '注册失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SET p_result = 0;
    
    SELECT COUNT(*) INTO @count FROM user WHERE student_id = p_student_id;
    IF @count > 0 THEN
        SET p_result = 1;
        SET p_message = '注册失败：学号/工号已存在';
        ROLLBACK;
        LEAVE;
    END IF;
    
    SELECT COUNT(*) INTO @count FROM user WHERE username = p_username;
    IF @count > 0 THEN
        SET p_result = 2;
        SET p_message = '注册失败：用户名已存在';
        ROLLBACK;
        LEAVE;
    END IF;
    
    INSERT INTO user (username, password, real_name, student_id, role, status)
    VALUES (p_username, p_password, p_real_name, p_student_id, COALESCE(p_role, 'STUDENT'), 'ACTIVE');
    
    SET p_result = 0;
    SET p_message = '注册成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 1.2 用户登录

```sql
DELIMITER //

CREATE PROCEDURE sp_user_login(
    IN p_student_id VARCHAR(20),
    IN p_password VARCHAR(255),
    OUT p_user_id INT,
    OUT p_username VARCHAR(50),
    OUT p_real_name VARCHAR(50),
    OUT p_role VARCHAR(20),
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '登录失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SET p_user_id = NULL;
    SET p_username = NULL;
    SET p_real_name = NULL;
    SET p_role = NULL;
    
    SELECT id, username, real_name, role, status INTO p_user_id, p_username, p_real_name, p_role, @status
    FROM user WHERE student_id = p_student_id;
    
    IF p_user_id IS NULL THEN
        SET p_result = 1;
        SET p_message = '登录失败：用户不存在';
        ROLLBACK;
        LEAVE;
    END IF;
    
    IF @status != 'ACTIVE' THEN
        SET p_result = 2;
        SET p_message = '登录失败：账号状态异常';
        ROLLBACK;
        LEAVE;
    END IF;
    
    SELECT password INTO @hashed_password FROM user WHERE id = p_user_id;
    
    UPDATE user SET last_login_time = NOW() WHERE id = p_user_id;
    
    SET p_result = 0;
    SET p_message = '登录成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 1.3 查询用户列表

```sql
DELIMITER //

CREATE PROCEDURE sp_get_users(
    IN p_role VARCHAR(20),
    IN p_status VARCHAR(20),
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    IF p_role IS NOT NULL AND p_role != '' THEN
        IF p_status IS NOT NULL AND p_status != '' THEN
            SELECT id, username, real_name, student_id, role, status, created_at 
            FROM user WHERE role = p_role AND status = p_status;
        ELSE
            SELECT id, username, real_name, student_id, role, status, created_at 
            FROM user WHERE role = p_role;
        END IF;
    ELSE
        IF p_status IS NOT NULL AND p_status != '' THEN
            SELECT id, username, real_name, student_id, role, status, created_at 
            FROM user WHERE status = p_status;
        ELSE
            SELECT id, username, real_name, student_id, role, status, created_at 
            FROM user;
        END IF;
    END IF;
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 2. 社团管理存储过程

### 2.1 创建社团

```sql
DELIMITER //

CREATE PROCEDURE sp_create_club(
    IN p_name VARCHAR(100),
    IN p_description TEXT,
    IN p_category VARCHAR(50),
    IN p_advisor VARCHAR(50),
    IN p_max_members INT,
    IN p_founder_id INT,
    OUT p_club_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '创建失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT COUNT(*) INTO @count FROM club WHERE name = p_name;
    IF @count > 0 THEN
        SET p_result = 1;
        SET p_message = '创建失败：社团名称已存在';
        ROLLBACK;
        LEAVE;
    END IF;
    
    INSERT INTO club (name, description, category, advisor, max_members, founder_id, status)
    VALUES (p_name, p_description, p_category, p_advisor, COALESCE(p_max_members, 100), p_founder_id, 'PENDING');
    
    SELECT LAST_INSERT_ID() INTO p_club_id;
    
    SET p_result = 0;
    SET p_message = '创建成功，等待审核';
    
    COMMIT;
END //

DELIMITER ;
```

### 2.2 审核社团

```sql
DELIMITER //

CREATE PROCEDURE sp_review_club(
    IN p_club_id INT,
    IN p_status VARCHAR(20),
    IN p_reviewed_by INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '审核失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    UPDATE club SET status = p_status, approved_at = NOW() WHERE id = p_club_id;
    
    IF p_status = 'APPROVED' THEN
        INSERT INTO club_member (club_id, user_id, role, status, approved_at)
        SELECT p_club_id, founder_id, 'PRESIDENT', 'APPROVED', NOW() 
        FROM club WHERE id = p_club_id;
        
        SELECT name INTO @club_name FROM club WHERE id = p_club_id;
        INSERT INTO conversation (type, name, club_id)
        VALUES ('CLUB', CONCAT(@club_name, '群聊'), p_club_id);
        
        SELECT LAST_INSERT_ID() INTO @conv_id;
        SELECT founder_id INTO @founder_id FROM club WHERE id = p_club_id;
        INSERT INTO conversation_member (conversation_id, user_id, role)
        VALUES (@conv_id, @founder_id, 'OWNER');
    END IF;
    
    SET p_result = 0;
    SET p_message = CONCAT('审核成功：', CASE WHEN p_status = 'APPROVED' THEN '已通过' WHEN p_status = 'REJECTED' THEN '已拒绝' ELSE '状态已更新' END);
    
    COMMIT;
END //

DELIMITER ;
```

### 2.3 查询社团列表

```sql
DELIMITER //

CREATE PROCEDURE sp_get_clubs(
    IN p_category VARCHAR(50),
    IN p_status VARCHAR(20),
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    IF p_category IS NOT NULL AND p_category != '' THEN
        IF p_status IS NOT NULL AND p_status != '' THEN
            SELECT c.id, c.name, c.description, c.category, c.advisor, c.max_members, 
                   c.status, c.created_at, u.real_name as founder_name,
                   (SELECT COUNT(*) FROM club_member WHERE club_id = c.id AND status = 'APPROVED') as member_count
            FROM club c LEFT JOIN user u ON c.founder_id = u.id
            WHERE c.category = p_category AND c.status = p_status;
        ELSE
            SELECT c.id, c.name, c.description, c.category, c.advisor, c.max_members, 
                   c.status, c.created_at, u.real_name as founder_name,
                   (SELECT COUNT(*) FROM club_member WHERE club_id = c.id AND status = 'APPROVED') as member_count
            FROM club c LEFT JOIN user u ON c.founder_id = u.id
            WHERE c.category = p_category;
        END IF;
    ELSE
        IF p_status IS NOT NULL AND p_status != '' THEN
            SELECT c.id, c.name, c.description, c.category, c.advisor, c.max_members, 
                   c.status, c.created_at, u.real_name as founder_name,
                   (SELECT COUNT(*) FROM club_member WHERE club_id = c.id AND status = 'APPROVED') as member_count
            FROM club c LEFT JOIN user u ON c.founder_id = u.id
            WHERE c.status = p_status;
        ELSE
            SELECT c.id, c.name, c.description, c.category, c.advisor, c.max_members, 
                   c.status, c.created_at, u.real_name as founder_name,
                   (SELECT COUNT(*) FROM club_member WHERE club_id = c.id AND status = 'APPROVED') as member_count
            FROM club c LEFT JOIN user u ON c.founder_id = u.id;
        END IF;
    END IF;
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

### 2.4 获取社团成员

```sql
DELIMITER //

CREATE PROCEDURE sp_get_club_members(
    IN p_club_id INT,
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT cm.id, cm.user_id, u.username, u.real_name, cm.role, cm.status, cm.joined_at, cm.approved_at
    FROM club_member cm JOIN user u ON cm.user_id = u.id
    WHERE cm.club_id = p_club_id
    ORDER BY FIELD(cm.role, 'PRESIDENT', 'VICE_PRESIDENT', 'ADMIN', 'MEMBER'), cm.joined_at;
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 3. 入社申请存储过程

### 3.1 提交入社申请

```sql
DELIMITER //

CREATE PROCEDURE sp_submit_join_request(
    IN p_user_id INT,
    IN p_club_id INT,
    IN p_reason TEXT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '申请失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT COUNT(*) INTO @count FROM club_member WHERE club_id = p_club_id AND user_id = p_user_id;
    IF @count > 0 THEN
        SET p_result = 1;
        SET p_message = '申请失败：已加入该社团';
        ROLLBACK;
        LEAVE;
    END IF;
    
    SELECT COUNT(*) INTO @count FROM join_requests WHERE club_id = p_club_id AND user_id = p_user_id AND status = 'PENDING';
    IF @count > 0 THEN
        SET p_result = 2;
        SET p_message = '申请失败：已有待审核的申请';
        ROLLBACK;
        LEAVE;
    END IF;
    
    INSERT INTO join_requests (user_id, club_id, reason, status)
    VALUES (p_user_id, p_club_id, p_reason, 'PENDING');
    
    SET p_result = 0;
    SET p_message = '申请提交成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 3.2 审核入社申请

```sql
DELIMITER //

CREATE PROCEDURE sp_review_join_request(
    IN p_request_id INT,
    IN p_status VARCHAR(20),
    IN p_reviewed_by INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '审核失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT user_id, club_id INTO @user_id, @club_id FROM join_requests WHERE id = p_request_id;
    
    UPDATE join_requests SET status = p_status, reviewed_by = p_reviewed_by, reviewed_at = NOW()
    WHERE id = p_request_id;
    
    IF p_status = 'APPROVED' THEN
        INSERT INTO club_member (club_id, user_id, role, status, approved_at)
        VALUES (@club_id, @user_id, 'MEMBER', 'APPROVED', NOW());
        
        SELECT id INTO @conv_id FROM conversation WHERE club_id = @club_id AND type = 'CLUB';
        IF @conv_id IS NOT NULL THEN
            INSERT INTO conversation_member (conversation_id, user_id, role)
            VALUES (@conv_id, @user_id, 'MEMBER');
        END IF;
    END IF;
    
    SET p_result = 0;
    SET p_message = CONCAT('审核成功：', CASE WHEN p_status = 'APPROVED' THEN '已通过' ELSE '已拒绝' END);
    
    COMMIT;
END //

DELIMITER ;
```

### 3.3 获取入社申请列表

```sql
DELIMITER //

CREATE PROCEDURE sp_get_join_requests(
    IN p_club_id INT,
    IN p_status VARCHAR(20),
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    IF p_status IS NOT NULL AND p_status != '' THEN
        SELECT jr.id, jr.user_id, u.real_name, u.student_id, jr.reason, jr.status, 
               jr.created_at, jr.reviewed_at, ru.real_name as reviewed_name
        FROM join_requests jr 
        JOIN user u ON jr.user_id = u.id
        LEFT JOIN user ru ON jr.reviewed_by = ru.id
        WHERE jr.club_id = p_club_id AND jr.status = p_status
        ORDER BY jr.created_at DESC;
    ELSE
        SELECT jr.id, jr.user_id, u.real_name, u.student_id, jr.reason, jr.status, 
               jr.created_at, jr.reviewed_at, ru.real_name as reviewed_name
        FROM join_requests jr 
        JOIN user u ON jr.user_id = u.id
        LEFT JOIN user ru ON jr.reviewed_by = ru.id
        WHERE jr.club_id = p_club_id
        ORDER BY jr.created_at DESC;
    END IF;
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 4. 活动管理存储过程

### 4.1 创建活动

```sql
DELIMITER //

CREATE PROCEDURE sp_create_activity(
    IN p_club_id INT,
    IN p_title VARCHAR(200),
    IN p_description TEXT,
    IN p_activity_type VARCHAR(20),
    IN p_start_time DATETIME,
    IN p_end_time DATETIME,
    IN p_location VARCHAR(200),
    IN p_max_participants INT,
    IN p_registration_deadline DATETIME,
    IN p_budget DECIMAL(10,2),
    IN p_created_by INT,
    OUT p_activity_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '创建失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    INSERT INTO activities (club_id, title, description, activity_type, start_time, end_time,
                           location, max_participants, current_participants, registration_deadline,
                           budget, status, created_by)
    VALUES (p_club_id, p_title, p_description, COALESCE(p_activity_type, 'INTERNAL'), 
            p_start_time, p_end_time, p_location, COALESCE(p_max_participants, 0), 0,
            p_registration_deadline, COALESCE(p_budget, 0.00), 'DRAFT', p_created_by);
    
    SELECT LAST_INSERT_ID() INTO p_activity_id;
    
    SET p_result = 0;
    SET p_message = '活动创建成功（草稿状态）';
    
    COMMIT;
END //

DELIMITER ;
```

### 4.2 发布活动

```sql
DELIMITER //

CREATE PROCEDURE sp_publish_activity(
    IN p_activity_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '发布失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT status INTO @status FROM activities WHERE id = p_activity_id;
    IF @status != 'DRAFT' THEN
        SET p_result = 1;
        SET p_message = '发布失败：活动状态不是草稿';
        ROLLBACK;
        LEAVE;
    END IF;
    
    UPDATE activities SET status = 'PUBLISHED', published_at = NOW() WHERE id = p_activity_id;
    
    SET p_result = 0;
    SET p_message = '活动发布成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 4.3 报名活动

```sql
DELIMITER //

CREATE PROCEDURE sp_register_activity(
    IN p_activity_id INT,
    IN p_user_id INT,
    IN p_real_name VARCHAR(50),
    IN p_student_id VARCHAR(20),
    IN p_phone VARCHAR(20),
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '报名失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT status, max_participants, current_participants, registration_deadline 
    INTO @status, @max_participants, @current_participants, @deadline 
    FROM activities WHERE id = p_activity_id;
    
    IF @status != 'PUBLISHED' THEN
        SET p_result = 1;
        SET p_message = '报名失败：活动未发布';
        ROLLBACK;
        LEAVE;
    END IF;
    
    IF @deadline IS NOT NULL AND NOW() > @deadline THEN
        SET p_result = 2;
        SET p_message = '报名失败：报名已截止';
        ROLLBACK;
        LEAVE;
    END IF;
    
    SELECT COUNT(*) INTO @count FROM activity_registrations WHERE activity_id = p_activity_id AND user_id = p_user_id;
    IF @count > 0 THEN
        SET p_result = 3;
        SET p_message = '报名失败：已报名该活动';
        ROLLBACK;
        LEAVE;
    END IF;
    
    IF @max_participants > 0 AND @current_participants >= @max_participants THEN
        SET p_result = 4;
        SET p_message = '报名失败：报名人数已满';
        ROLLBACK;
        LEAVE;
    END IF;
    
    INSERT INTO activity_registrations (activity_id, user_id, real_name, student_id, phone, status)
    VALUES (p_activity_id, p_user_id, p_real_name, p_student_id, p_phone, 'PENDING');
    
    UPDATE activities SET current_participants = current_participants + 1 WHERE id = p_activity_id;
    
    SET p_result = 0;
    SET p_message = '报名成功，等待审核';
    
    COMMIT;
END //

DELIMITER ;
```

### 4.4 审核活动报名

```sql
DELIMITER //

CREATE PROCEDURE sp_review_activity_registration(
    IN p_registration_id INT,
    IN p_status VARCHAR(20),
    IN p_approved_by INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '审核失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    UPDATE activity_registrations 
    SET status = p_status, approval_time = NOW(), approved_by = p_approved_by
    WHERE id = p_registration_id;
    
    IF p_status = 'REJECTED' THEN
        SELECT activity_id INTO @activity_id FROM activity_registrations WHERE id = p_registration_id;
        UPDATE activities SET current_participants = current_participants - 1 WHERE id = @activity_id;
    END IF;
    
    SET p_result = 0;
    SET p_message = CONCAT('审核成功：', CASE WHEN p_status = 'APPROVED' THEN '已通过' ELSE '已拒绝' END);
    
    COMMIT;
END //

DELIMITER ;
```

### 4.5 活动签到

```sql
DELIMITER //

CREATE PROCEDURE sp_check_in_activity(
    IN p_registration_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '签到失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT status, is_checked_in INTO @status, @checked_in FROM activity_registrations WHERE id = p_registration_id;
    
    IF @status != 'APPROVED' THEN
        SET p_result = 1;
        SET p_message = '签到失败：报名未通过审核';
        ROLLBACK;
        LEAVE;
    END IF;
    
    IF @checked_in THEN
        SET p_result = 2;
        SET p_message = '签到失败：已签到';
        ROLLBACK;
        LEAVE;
    END IF;
    
    UPDATE activity_registrations SET is_checked_in = TRUE, check_in_time = NOW() WHERE id = p_registration_id;
    
    SET p_result = 0;
    SET p_message = '签到成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 4.6 获取活动列表

```sql
DELIMITER //

CREATE PROCEDURE sp_get_activities(
    IN p_club_id INT,
    IN p_status VARCHAR(20),
    IN p_activity_type VARCHAR(20),
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SET @sql = 'SELECT a.id, a.title, a.description, a.activity_type, a.start_time, a.end_time, 
                      a.location, a.max_participants, a.current_participants, a.status, 
                      c.name as club_name, u.real_name as creator_name, a.created_at
               FROM activities a
               LEFT JOIN club c ON a.club_id = c.id
               LEFT JOIN user u ON a.created_by = u.id
               WHERE 1=1';
    
    IF p_club_id IS NOT NULL THEN
        SET @sql = CONCAT(@sql, ' AND a.club_id = ', p_club_id);
    END IF;
    
    IF p_status IS NOT NULL AND p_status != '' THEN
        SET @sql = CONCAT(@sql, " AND a.status = '", p_status, "'");
    END IF;
    
    IF p_activity_type IS NOT NULL AND p_activity_type != '' THEN
        SET @sql = CONCAT(@sql, " AND a.activity_type = '", p_activity_type, "'");
    END IF;
    
    SET @sql = CONCAT(@sql, ' ORDER BY a.start_time DESC');
    
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 5. 公告管理存储过程

### 5.1 创建公告

```sql
DELIMITER //

CREATE PROCEDURE sp_create_announcement(
    IN p_title VARCHAR(200),
    IN p_content TEXT,
    IN p_priority VARCHAR(20),
    IN p_is_pinned BOOLEAN,
    IN p_club_id INT,
    IN p_created_by INT,
    OUT p_announcement_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '创建失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    INSERT INTO announcements (title, content, priority, is_pinned, status, club_id, created_by)
    VALUES (p_title, p_content, COALESCE(p_priority, 'NORMAL'), COALESCE(p_is_pinned, FALSE), 'DRAFT', p_club_id, p_created_by);
    
    SELECT LAST_INSERT_ID() INTO p_announcement_id;
    
    SET p_result = 0;
    SET p_message = '公告创建成功（草稿状态）';
    
    COMMIT;
END //

DELIMITER ;
```

### 5.2 发布公告

```sql
DELIMITER //

CREATE PROCEDURE sp_publish_announcement(
    IN p_announcement_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '发布失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT status INTO @status FROM announcements WHERE id = p_announcement_id;
    IF @status != 'DRAFT' THEN
        SET p_result = 1;
        SET p_message = '发布失败：公告状态不是草稿';
        ROLLBACK;
        LEAVE;
    END IF;
    
    UPDATE announcements SET status = 'PUBLISHED', published_at = NOW() WHERE id = p_announcement_id;
    
    SET p_result = 0;
    SET p_message = '公告发布成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 5.3 获取公告列表

```sql
DELIMITER //

CREATE PROCEDURE sp_get_announcements(
    IN p_club_id INT,
    IN p_status VARCHAR(20),
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    IF p_club_id IS NOT NULL THEN
        IF p_status IS NOT NULL AND p_status != '' THEN
            SELECT a.id, a.title, a.content, a.priority, a.is_pinned, a.status, 
                   a.created_at, a.published_at, u.real_name as creator_name, c.name as club_name
            FROM announcements a
            LEFT JOIN user u ON a.created_by = u.id
            LEFT JOIN club c ON a.club_id = c.id
            WHERE a.club_id = p_club_id AND a.status = p_status
            ORDER BY a.is_pinned DESC, COALESCE(a.published_at, a.created_at) DESC;
        ELSE
            SELECT a.id, a.title, a.content, a.priority, a.is_pinned, a.status, 
                   a.created_at, a.published_at, u.real_name as creator_name, c.name as club_name
            FROM announcements a
            LEFT JOIN user u ON a.created_by = u.id
            LEFT JOIN club c ON a.club_id = c.id
            WHERE a.club_id = p_club_id
            ORDER BY a.is_pinned DESC, COALESCE(a.published_at, a.created_at) DESC;
        END IF;
    ELSE
        IF p_status IS NOT NULL AND p_status != '' THEN
            SELECT a.id, a.title, a.content, a.priority, a.is_pinned, a.status, 
                   a.created_at, a.published_at, u.real_name as creator_name, c.name as club_name
            FROM announcements a
            LEFT JOIN user u ON a.created_by = u.id
            LEFT JOIN club c ON a.club_id = c.id
            WHERE a.status = p_status
            ORDER BY a.is_pinned DESC, COALESCE(a.published_at, a.created_at) DESC;
        ELSE
            SELECT a.id, a.title, a.content, a.priority, a.is_pinned, a.status, 
                   a.created_at, a.published_at, u.real_name as creator_name, c.name as club_name
            FROM announcements a
            LEFT JOIN user u ON a.created_by = u.id
            LEFT JOIN club c ON a.club_id = c.id
            ORDER BY a.is_pinned DESC, COALESCE(a.published_at, a.created_at) DESC;
        END IF;
    END IF;
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 6. 消息管理存储过程

### 6.1 发送消息

```sql
DELIMITER //

CREATE PROCEDURE sp_send_message(
    IN p_type VARCHAR(20),
    IN p_user_id INT,
    IN p_conversation_id INT,
    IN p_content TEXT,
    IN p_extra_data TEXT,
    OUT p_message_id INT,
    OUT p_result INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        SET p_message = '发送失败：数据库错误';
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT COUNT(*) INTO @count FROM conversation_member WHERE conversation_id = p_conversation_id AND user_id = p_user_id;
    IF @count = 0 THEN
        SET p_result = 1;
        SET p_message = '发送失败：用户不在该会话中';
        ROLLBACK;
        LEAVE;
    END IF;
    
    INSERT INTO messages (type, user_id, conversation_id, content, extra_data, create_time)
    VALUES (COALESCE(p_type, 'TEXT'), p_user_id, p_conversation_id, p_content, p_extra_data, NOW());
    
    SELECT LAST_INSERT_ID() INTO p_message_id;
    
    UPDATE conversation SET updated_at = NOW() WHERE id = p_conversation_id;
    
    SET p_result = 0;
    SET p_message = '消息发送成功';
    
    COMMIT;
END //

DELIMITER ;
```

### 6.2 获取会话消息

```sql
DELIMITER //

CREATE PROCEDURE sp_get_messages(
    IN p_conversation_id INT,
    IN p_limit INT,
    IN p_offset INT,
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT m.id, m.type, m.content, m.extra_data, m.create_time, m.is_recalled,
           u.username, u.real_name, u.avatar_url
    FROM messages m
    JOIN user u ON m.user_id = u.id
    WHERE m.conversation_id = p_conversation_id
    ORDER BY m.create_time DESC
    LIMIT COALESCE(p_offset, 0), COALESCE(p_limit, 50);
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 7. 统计查询存储过程

### 7.1 获取系统统计数据

```sql
DELIMITER //

CREATE PROCEDURE sp_get_system_stats(
    OUT p_total_users INT,
    OUT p_total_clubs INT,
    OUT p_total_activities INT,
    OUT p_total_members INT,
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT COUNT(*) INTO p_total_users FROM user WHERE status = 'ACTIVE';
    
    SELECT COUNT(*) INTO p_total_clubs FROM club WHERE status = 'APPROVED';
    
    SELECT COUNT(*) INTO p_total_activities FROM activities WHERE status IN ('PUBLISHED', 'IN_PROGRESS');
    
    SELECT COUNT(*) INTO p_total_members FROM club_member WHERE status = 'APPROVED';
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

### 7.2 获取社团统计数据

```sql
DELIMITER //

CREATE PROCEDURE sp_get_club_stats(
    IN p_club_id INT,
    OUT p_member_count INT,
    OUT p_pending_requests INT,
    OUT p_upcoming_activities INT,
    OUT p_result INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = -1;
        ROLLBACK;
    END;
    
    START TRANSACTION;
    
    SELECT COUNT(*) INTO p_member_count FROM club_member WHERE club_id = p_club_id AND status = 'APPROVED';
    
    SELECT COUNT(*) INTO p_pending_requests FROM join_requests WHERE club_id = p_club_id AND status = 'PENDING';
    
    SELECT COUNT(*) INTO p_upcoming_activities FROM activities WHERE club_id = p_club_id 
        AND status IN ('PUBLISHED', 'IN_PROGRESS') AND start_time > NOW();
    
    SET p_result = 0;
    
    COMMIT;
END //

DELIMITER ;
```

---

## 8. 存储过程调用示例

### 8.1 注册用户

```sql
SET @result = 0;
SET @message = '';
CALL sp_register_user('zhangsan', '$2a$10$...', '张三', '2024001', 'STUDENT', @result, @message);
SELECT @result, @message;
```

### 8.2 创建并审核社团

```sql
SET @club_id = 0;
SET @result = 0;
SET @message = '';

CALL sp_create_club('编程社', '学习编程技术', '学术科技', '王老师', 50, 1, @club_id, @result, @message);
SELECT @club_id, @result, @message;

CALL sp_review_club(@club_id, 'APPROVED', 1, @result, @message);
SELECT @result, @message;
```

### 8.3 创建活动并报名

```sql
SET @activity_id = 0;
SET @result = 0;
SET @message = '';

CALL sp_create_activity(1, 'Python培训', '学习Python编程', 'TRAINING', 
    '2026-07-01 14:00:00', '2026-07-01 17:00:00', '教学楼A101', 
    30, '2026-06-28 23:59:59', 500.00, 1, @activity_id, @result, @message);
SELECT @activity_id, @result, @message;

CALL sp_publish_activity(@activity_id, @result, @message);
SELECT @result, @message;

CALL sp_register_activity(@activity_id, 2, '李四', '2024002', '13800138000', @result, @message);
SELECT @result, @message;
```

### 8.4 获取统计数据

```sql
SET @total_users = 0;
SET @total_clubs = 0;
SET @total_activities = 0;
SET @total_members = 0;
SET @result = 0;

CALL sp_get_system_stats(@total_users, @total_clubs, @total_activities, @total_members, @result);
SELECT @total_users as total_users, @total_clubs as total_clubs, 
       @total_activities as total_activities, @total_members as total_members;
```

---

*文档版本：1.0*  
*创建日期：2026年6月*  
*适用系统：学生社团管理系统 (stuclub)