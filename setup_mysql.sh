#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log() {
    echo -e "${1}${2}${NC}"
}

log_success() {
    log "${GREEN}" "✓ ${1}"
}

log_error() {
    log "${RED}" "✗ ${1}"
}

log_warning() {
    log "${YELLOW}" "⚠ ${1}"
}

log_info() {
    log "" "${1}"
}

# 检测MySQL是否安装
check_mysql_installed() {
    log_info "检测MySQL是否安装..."
    if command -v mysql &> /dev/null; then
        log_success "MySQL已安装"
        return 0
    else
        log_error "MySQL未安装"
        return 1
    fi
}

# 检测操作系统类型
detect_os() {
    if [ -f /etc/debian_version ]; then
        echo "debian"
    elif [ -f /etc/redhat-release ]; then
        echo "redhat"
    else
        echo "unknown"
    fi
}

# 安装MySQL
install_mysql() {
    log_info "开始安装MySQL..."
    
    OS=$(detect_os)
    
    case "$OS" in
        debian)
            log_info "检测到Debian/Ubuntu系统"
            sudo apt-get update
            sudo apt-get install -y mysql-server
            ;;
        redhat)
            log_info "检测到CentOS/RHEL系统"
            sudo yum update
            sudo yum install -y mysql-server
            ;;
        *)
            log_error "不支持的操作系统"
            exit 1
            ;;
    esac
    
    log_success "MySQL安装完成"
}

# 启动MySQL服务
start_mysql_service() {
    log_info "启动MySQL服务..."
    
    OS=$(detect_os)
    
    case "$OS" in
        debian)
            sudo systemctl start mysql
            sudo systemctl enable mysql
            ;;
        redhat)
            sudo systemctl start mysqld
            sudo systemctl enable mysqld
            ;;
    esac
    
    log_success "MySQL服务已启动"
}

# 安全配置MySQL
secure_mysql() {
    log_info "运行MySQL安全配置..."
    
    if [ -f /etc/debian_version ]; then
        sudo mysql_secure_installation
    elif [ -f /etc/redhat-release ]; then
        sudo mysql_secure_installation
    fi
    
    log_success "MySQL安全配置完成"
}

# 获取MySQL root密码
get_mysql_root_password() {
    # 检查是否需要root密码
    if sudo mysql -u root -e "SELECT 1;" &> /dev/null; then
        log_success "MySQL root用户使用sudo认证，无需密码"
        MYSQL_ROOT_PASSWORD=""
        MYSQL_ROOT_COMMAND="sudo mysql -u root"
    else
        while true; do
            read -s -p "请输入MySQL root密码: " MYSQL_ROOT_PASSWORD
            echo
            read -s -p "请再次输入MySQL root密码: " MYSQL_ROOT_PASSWORD_CONFIRM
            echo
            
            if [ "$MYSQL_ROOT_PASSWORD" = "$MYSQL_ROOT_PASSWORD_CONFIRM" ]; then
                MYSQL_ROOT_COMMAND="mysql -u root -p\"$MYSQL_ROOT_PASSWORD\""
                break
            else
                log_error "两次输入的密码不匹配，请重新输入"
            fi
        done
    fi
}

# 获取新用户信息
get_new_user_info() {
    read -p "请输入要创建的MySQL用户名: " MYSQL_USER
    
    while true; do
        read -s -p "请输入新用户的密码: " MYSQL_PASSWORD
        echo
        read -s -p "请再次输入新用户的密码: " MYSQL_PASSWORD_CONFIRM
        echo
        
        if [ "$MYSQL_PASSWORD" = "$MYSQL_PASSWORD_CONFIRM" ]; then
            break
        else
            log_error "两次输入的密码不匹配，请重新输入"
        fi
    done
}

# 获取数据库名称
get_database_name() {
    read -p "请输入要创建的数据库名称: " DATABASE_NAME
}

# 创建MySQL用户
create_mysql_user() {
    log_info "创建MySQL用户 $MYSQL_USER..."
    
    eval "$MYSQL_ROOT_COMMAND -e \"CREATE USER '$MYSQL_USER'@'localhost' IDENTIFIED BY '$MYSQL_PASSWORD';\""
    
    if [ $? -eq 0 ]; then
        log_success "MySQL用户 $MYSQL_USER 创建成功"
    else
        log_error "创建MySQL用户失败"
        exit 1
    fi
}

# 创建数据库
create_database() {
    log_info "创建数据库 $DATABASE_NAME..."
    
    eval "$MYSQL_ROOT_COMMAND -e \"CREATE DATABASE $DATABASE_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\""
    
    if [ $? -eq 0 ]; then
        log_success "数据库 $DATABASE_NAME 创建成功"
    else
        log_error "创建数据库失败"
        exit 1
    fi
}

# 授权用户访问数据库
grant_user_privileges() {
    log_info "授权用户 $MYSQL_USER 访问数据库 $DATABASE_NAME..."
    
    eval "$MYSQL_ROOT_COMMAND -e \"GRANT ALL PRIVILEGES ON $DATABASE_NAME.* TO '$MYSQL_USER'@'localhost';\""
    eval "$MYSQL_ROOT_COMMAND -e \"FLUSH PRIVILEGES;\""
    
    if [ $? -eq 0 ]; then
        log_success "用户权限设置成功"
    else
        log_error "设置用户权限失败"
        exit 1
    fi
}

# 创建表结构
create_tables() {
    log_info "创建数据库表结构..."
    
    # 创建user表
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"
    CREATE TABLE IF NOT EXISTS `user` (
      `id` int NOT NULL AUTO_INCREMENT,
      `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
      `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
      `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`) USING BTREE,
      UNIQUE KEY `username` (`username`) USING BTREE
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\";"
    
    # 创建room表
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"
    CREATE TABLE IF NOT EXISTS `room` (
      `id` int NOT NULL AUTO_INCREMENT,
      `room_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
      `room_type` enum('PUBLIC','PRIVATE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
      `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`) USING BTREE
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\";"
    
    # 创建room_member表
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"
    CREATE TABLE IF NOT EXISTS `room_member` (
      `room_id` int NOT NULL,
      `user_id` int NOT NULL,
      `joined_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`room_id`,`user_id`) USING BTREE,
      KEY `user_id` (`user_id`) USING BTREE,
      CONSTRAINT `room_member_ibfk_1` FOREIGN KEY (`room_id`) REFERENCES `room` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
      CONSTRAINT `room_member_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\";"
    
    # 创建user_uuid表
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"
    CREATE TABLE IF NOT EXISTS `user_uuid` (
      `user_id` int NOT NULL,
      `uuid` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
      `issued_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`user_id`) USING BTREE,
      UNIQUE KEY `uuid` (`uuid`) USING BTREE,
      CONSTRAINT `user_uuid_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\";"
    
    # 创建friendships表
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"
    CREATE TABLE IF NOT EXISTS `friendships` (
      `id` int NOT NULL AUTO_INCREMENT,
      `user1_id` int NOT NULL,
      `user2_id` int NOT NULL,
      `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`) USING BTREE,
      UNIQUE KEY `unique_friendship` (`user1_id`, `user2_id`) USING BTREE,
      KEY `user2_id` (`user2_id`) USING BTREE,
      CONSTRAINT `friendships_ibfk_1` FOREIGN KEY (`user1_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
      CONSTRAINT `friendships_ibfk_2` FOREIGN KEY (`user2_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\";"
    
    # 创建friend_requests表
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"
    CREATE TABLE IF NOT EXISTS `friend_requests` (
      `id` int NOT NULL AUTO_INCREMENT,
      `from_user_id` int NOT NULL,
      `to_user_id` int NOT NULL,
      `status` enum('PENDING','ACCEPTED','REJECTED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
      `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
      `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (`id`) USING BTREE,
      KEY `from_user_id` (`from_user_id`) USING BTREE,
      KEY `to_user_id` (`to_user_id`) USING BTREE,
      KEY `status` (`status`) USING BTREE,
      CONSTRAINT `friend_requests_ibfk_1` FOREIGN KEY (`from_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
      CONSTRAINT `friend_requests_ibfk_2` FOREIGN KEY (`to_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
    ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;\";"
    
    # 创建system房间
    eval "$MYSQL_ROOT_COMMAND $DATABASE_NAME -e \"INSERT IGNORE INTO `room` (`room_name`, `room_type`) VALUES ('system', 'PUBLIC');\";"
    
    if [ $? -eq 0 ]; then
        log_success "数据库表结构创建成功"
    else
        log_error "创建数据库表结构失败"
        exit 1
    fi
}

# 显示时区选择
show_timezone_selection() {
    log_info "请选择您的时区:"
    echo "1) Asia/Shanghai (中国标准时间)"
    echo "2) Asia/Beijing (北京)"
    echo "3) Asia/Hong_Kong (香港)"
    echo "4) Asia/Taipei (台北)"
    echo "5) UTC (协调世界时)"
    echo "6) Europe/London (伦敦)"
    echo "7) America/New_York (纽约)"
    echo "8) America/Los_Angeles (洛杉矶)"
    echo "9) Australia/Sydney (悉尼)"
    echo "10) Asia/Tokyo (东京)"
    
    while true; do
        read -p "请输入选择 (1-10): " timezone_choice
        
        case $timezone_choice in
            1)
                SELECTED_TIMEZONE="Asia/Shanghai"
                break
                ;;
            2)
                SELECTED_TIMEZONE="Asia/Beijing"
                break
                ;;
            3)
                SELECTED_TIMEZONE="Asia/Hong_Kong"
                break
                ;;
            4)
                SELECTED_TIMEZONE="Asia/Taipei"
                break
                ;;
            5)
                SELECTED_TIMEZONE="UTC"
                break
                ;;
            6)
                SELECTED_TIMEZONE="Europe/London"
                break
                ;;
            7)
                SELECTED_TIMEZONE="America/New_York"
                break
                ;;
            8)
                SELECTED_TIMEZONE="America/Los_Angeles"
                break
                ;;
            9)
                SELECTED_TIMEZONE="Australia/Sydney"
                break
                ;;
            10)
                SELECTED_TIMEZONE="Asia/Tokyo"
                break
                ;;
            *)
                log_error "无效的选择，请重新输入"
                ;;
        esac
    done
    
    log_success "已选择时区: $SELECTED_TIMEZONE"
}

# 更新database.properties文件
update_database_properties() {
    log_info "更新database.properties文件..."
    
    PROPERTIES_FILE="chatroom/server/sql/database.properties"
    
    # 创建目录（如果不存在）
    mkdir -p "$(dirname "$PROPERTIES_FILE")"
    
    # 创建配置文件
    cat > "$PROPERTIES_FILE" << EOL
# 数据库驱动配置
db.driver=com.mysql.cj.jdbc.Driver

# 数据库连接配置
db.url=jdbc:mysql://localhost:3306/$DATABASE_NAME?useSSL=false&serverTimezone=$SELECTED_TIMEZONE&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
db.user=$MYSQL_USER
db.password=$MYSQL_PASSWORD
EOL
    
    if [ $? -eq 0 ]; then
        log_success "database.properties文件创建并更新成功"
        # 显示更新后的配置
        log_info "更新后的数据库配置:"
        cat "$PROPERTIES_FILE"
    else
        log_error "更新database.properties文件失败"
        exit 1
    fi
}

# 更新Spring Boot application.properties文件
update_application_properties() {
    log_info "更新Spring Boot application.properties文件..."
    
    APPLICATION_PROPERTIES="chatroom/server/src/main/resources/application.properties"
    
    # 创建目录（如果不存在）
    mkdir -p "$(dirname "$APPLICATION_PROPERTIES")"
    
    # 检查文件是否存在，如果存在则备份
    if [ -f "$APPLICATION_PROPERTIES" ]; then
        cp "$APPLICATION_PROPERTIES" "${APPLICATION_PROPERTIES}.backup"
        log_info "已备份原application.properties文件"
    fi
    
    # 创建配置文件
    cat > "$APPLICATION_PROPERTIES" << EOL
server.port=8083
spring.application.name=chatroom-admin

spring.datasource.url=jdbc:mysql://localhost:3306/$DATABASE_NAME?useSSL=false&serverTimezone=$SELECTED_TIMEZONE&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
spring.datasource.username=$MYSQL_USER
spring.datasource.password=$MYSQL_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.mode=HTML
EOL
    
    if [ $? -eq 0 ]; then
        log_success "application.properties文件创建并更新成功"
        # 显示更新后的配置
        log_info "更新后的Spring Boot数据库配置:"
        grep -E "^spring.datasource\." "$APPLICATION_PROPERTIES"
    else
        log_error "更新application.properties文件失败"
        exit 1
    fi
}

# 主函数
main() {
    log_info "======================================"
    log_info "        MySQL 自动配置脚本           "
    log_info "======================================"
    
    # 检查是否以root用户运行
    if [ "$EUID" -ne 0 ]; then
        log_error "请以root用户运行此脚本"
        exit 1
    fi
    
    # 检查MySQL是否安装
    if ! check_mysql_installed; then
        read -p "是否安装MySQL? (y/n): " install_choice
        if [ "$install_choice" = "y" ] || [ "$install_choice" = "Y" ]; then
            install_mysql
            start_mysql_service
            secure_mysql
        else
            log_error "MySQL未安装，无法继续"
            exit 1
        fi
    fi
    
    # 获取MySQL root密码
    get_mysql_root_password
    
    # 获取新用户信息
    get_new_user_info
    
    # 获取数据库名称
    get_database_name
    
    # 选择时区
    show_timezone_selection
    
    # 执行数据库配置
    create_mysql_user
    create_database
    grant_user_privileges
    create_tables
    update_database_properties
    update_application_properties
    
    log_info "======================================"
    log_info "        配置完成!                    "
    log_info "======================================"
    log_success "MySQL数据库已成功配置"
    log_info "管理后台配置文件已更新: chatroom/server/src/main/resources/application.properties"
    log_info "启动管理后台: cd chatroom/server && mvn spring-boot:run"
    log_info "访问管理后台: http://localhost:8083"
}

# 运行主函数
main