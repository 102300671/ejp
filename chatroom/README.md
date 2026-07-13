# EJP Chatroom

基于 WebSocket 的实时网络聊天室系统，支持用户注册登录、好友管理、聊天室创建与消息推送。

## 技术栈

- **服务器**: Java 21 + WebSocket + MySQL + ZFile
- **JavaFX 客户端**: Java 21 + JavaFX 21 + WebSocket + SQLite + Maven

## 5 分钟快速开始

### 1. 环境准备

```bash
# 安装 JDK 21
sudo apt install openjdk-21-jdk

# 安装 MySQL
sudo apt install mysql-server
sudo systemctl start mysql

# 安装 Maven（仅 JavaFX 客户端）
sudo apt install maven
```

### 2. 创建数据库

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库和用户
CREATE DATABASE chatroom_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'chatroom'@'localhost' IDENTIFIED BY 'chatroom';
GRANT ALL PRIVILEGES ON chatroom_db.* TO 'chatroom'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 3. 创建数据库表

使用初始化脚本一键创建所有表和测试数据：

```bash
cd server
mysql -u chatroom -p chatroom_db < sql/init.sql
```

输入密码 `chatroom` 即可完成初始化。脚本会创建：
- 所有必要的数据库表
- 3 个测试用户（admin、alice、bob，密码均为 `123456`）
- 2 个示例公共房间（技术交流群、闲聊群）

### 4. 启动服务器

```bash
cd server

# 确保 lib/ 目录包含以下依赖：
# - bcrypt-0.10.2.jar
# - Java-WebSocket-1.5.7.jar
# - mysql-connector-j-9.5.0.jar
# - gson-2.13.2.jar
# - slf4j-api-1.7.36.jar
# - slf4j-simple-1.7.36.jar

# 编译
chmod +x compile.sh
./compile.sh

# 运行（默认端口 8888）
chmod +x run.sh
./run.sh

# 查看日志
tail -f log/log.txt
```

### 5. 启动 JavaFX 客户端

```bash
cd client/javafx

# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/chatroom-javafx-1.0.0.jar
```

### 6. 测试账号

系统已预置以下测试用户（密码均为 `123456`）：

| 用户名 | 密码 | 状态 |
|--------|------|------|
| admin | 123456 | 离线 |
| alice | 123456 | 离线 |
| bob | 123456 | 离线 |

登录后可进行以下操作：
- 创建/加入社团（聊天室）
- 添加好友
- 发送消息

## 目录说明

```
chatroom/
├── server/                 # 服务器端
│   ├── ChatServer.java     # 服务器主入口
│   ├── compile.sh          # 编译脚本
│   ├── run.sh              # 运行脚本
│   ├── config/             # 服务配置（SSL、ZFile）
│   ├── sql/                # 数据库访问层
│   │   ├── DatabaseManager.java
│   │   ├── database.properties
│   │   ├── user/           # 用户数据访问
│   │   ├── friend/         # 好友关系数据访问
│   │   ├── room/           # 房间数据访问
│   │   ├── message/        # 消息数据访问
│   │   └── conversation/   # 会话数据访问
│   ├── network/            # 网络层（WebSocket）
│   ├── user/               # 用户业务逻辑
│   ├── room/               # 房间业务逻辑
│   ├── message/            # 消息处理逻辑
│   ├── zfile/              # ZFile 文件服务集成
│   ├── util/               # 工具类
│   └── log/                # 日志文件
│
├── client/
│   ├── javafx/             # JavaFX 客户端
│   │   ├── pom.xml         # Maven 配置
│   │   ├── src/main/java/client/javafx/
│   │   │   ├── Main.java   # 程序入口
│   │   │   ├── MainApp.java # JavaFX 应用主类
│   │   │   ├── controller/ # UI 控制器（登录、聊天、个人资料）
│   │   │   ├── model/      # 数据模型（好友、房间）
│   │   │   ├── network/    # 网络层（WebSocket、ZFile、OnlyOffice）
│   │   │   ├── storage/    # 本地消息存储（SQLite）
│   │   │   ├── protocol/   # 协议定义（消息类型、消息结构）
│   │   │   └── util/       # 工具类（日志、跨平台路径）
│   │   └── src/main/resources/ # FXML 和资源文件
│   ├── gui/                # Swing 客户端（旧版）
│   ├── cli/                # 命令行客户端（旧版）
│   └── web/                # Web 客户端（旧版）
│
├── dashboard/              # 管理后台
├── PRD.md                  # 产品需求文档
├── PRD-网络聊天室.md       # 详细产品需求文档
└── .gitignore              # Git 忽略配置
```

## 注意事项

1. 运行服务器前，确保 `server/config/service.properties` 中的 ZFile 配置正确（可选）
2. JavaFX 客户端依赖 Java 21，确保 `JAVA_HOME` 指向正确的 JDK
3. 数据库连接配置位于 `server/sql/database.properties`
4. 不要提交 `target/`、`lib/`、`keystore.*` 和缓存数据到版本库