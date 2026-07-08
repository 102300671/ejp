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
    
    UPDATE user SET last_login_time = NOW() WHERE id = p_user_id;
    
    SET p_result = 0;
    SET p_message = '登录成功';
    
    COMMIT;
END //

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