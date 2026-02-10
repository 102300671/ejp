# EJP Chat Application

EJP是一个基于Java开发的多功能聊天应用，支持TCP Socket和WebSocket连接，提供了客户端和服务器端的完整实现。

## 功能特点

### 核心功能
- **双协议支持**：同时支持TCP Socket和WebSocket连接
- **多客户端类型**：命令行客户端和Web客户端
- **用户认证**：支持用户登录和注册功能
- **房间管理**：支持创建、加入、离开房间（公开房间和私有房间）
- **消息管理**：支持文本消息、系统消息等多种消息类型
- **数据库集成**：使用MySQL数据库存储房间信息、用户数据和消息历史
- **消息历史**：支持房间消息和私人消息的历史记录查询
- **消息加密**：使用AES-GCM加密敏感消息内容
- **好友系统**：支持好友请求、好友关系管理和好友聊天
- **管理后台**：基于Spring Boot的Web管理界面，提供用户、房间、消息管理功能

### 高级功能
- **自动重连机制**：客户端断线后自动尝试重连
- **消息路由**：基于消息类型和目标的智能路由系统
- **多线程处理**：高性能的多线程架构
- **配置灵活**：支持命令行参数和配置文件
- **会话管理**：基于UUID的用户会话管理
- **文件上传集成**：支持ZFile文件服务器集成
- **跨窗口同步**：Web客户端支持多窗口消息同步
- **NSFW内容检测**：支持敏感内容标记和加密存储

## 系统架构

### 服务器端
- **ServerListener**：处理TCP Socket连接
- **WebSocketServer**：处理WebSocket连接
- **MessageRouter**：消息路由和分发
- **DatabaseManager**：数据库操作管理
- **Room**：房间管理系统（公开房间和私有房间）
- **Session**：用户会话管理
- **MessageDAO**：消息数据访问对象，处理消息持久化
- **UserDAO**：用户数据访问对象
- **RoomDAO**：房间数据访问对象
- **UUIDGenerator**：UUID生成器，用于用户会话标识
- **AESUtil**：AES-GCM加密工具类
- **ZFileTokenManager**：ZFile文件服务器集成管理
- **ZFileConfig**：ZFile配置管理
- **FriendshipDAO**：好友关系数据访问对象
- **FriendRequestDAO**：好友请求数据访问对象
- **ServiceConfig**：服务配置管理

### 管理后台（Spring Boot）
- **AdminApplication**：Spring Boot主应用类
- **MainController**：主页面控制器，提供仪表板、用户、房间、消息管理界面
- **UserController**：用户管理API，提供用户CRUD、搜索、好友管理等功能
- **RoomController**：房间管理API
- **MessageController**：消息管理API
- **UserService**：用户服务层，处理用户相关业务逻辑
- **RoomService**：房间服务层，处理房间相关业务逻辑
- **MessageService**：消息服务层，处理消息相关业务逻辑

### 客户端
- **ClientConnection**：与服务器的网络连接
- **UserInterface**：用户界面
- **AuthenticationInterface**：用户认证界面
- **Message**：消息处理系统
- **MessageCodec**：消息编解码器
- **UUIDCache**：UUID缓存管理
- **Web客户端**：基于HTML/CSS/JavaScript的Web界面
  - 支持WebSocket实时通信
  - 支持localStorage数据持久化
  - 支持BroadcastChannel跨窗口消息同步

## 安装步骤

### 环境要求
- Java 8 或更高版本
- MySQL 数据库（可选，用于持久化存储）

### 依赖库

#### 服务器端依赖 (`chatroom/server/lib/`)
- `bcrypt-0.10.2.jar` - 密码加密库
- `bytes-1.5.0.jar` - 字节处理工具（bcrypt依赖）
- `gson-2.13.2.jar` - JSON数据处理
- `Java-WebSocket-1.5.7.jar` - WebSocket协议支持
- `mysql-connector-j-9.5.0.jar` - MySQL数据库驱动
- `slf4j-api-1.7.36.jar` - 日志框架API
- `slf4j-simple-1.7.36.jar` - 简单日志实现

#### 管理后台依赖 (`chatroom/server/pom.xml`)
- Spring Boot 3.2.0 - Spring Boot框架
- Spring Boot Starter Web - Web应用支持
- Spring Boot Starter Thymeleaf - 模板引擎
- Spring Boot Starter Data JPA - JPA数据访问
- MySQL Connector Java 8.0.33 - MySQL数据库驱动
- Lombok - 简化Java代码
- bcrypt 0.10.2 - 密码加密

#### 客户端依赖 (`chatroom/client/lib/`)
- `gson-2.13.2.jar` - JSON数据处理

### 安装方法

1. **克隆项目**
```bash
git clone https://github.com/yourusername/ejp.git
cd ejp
```

2. **使用自动化脚本**（推荐）
项目提供了一个自动化脚本 `run.sh`，可以简化编译和运行过程：

```bash
# 查看脚本帮助信息
./run.sh -h

# 编译所有代码（自动下载依赖）
./run.sh -c

# 运行服务器端
./run.sh -s [port]

# 运行客户端
./run.sh -cl [server_address] [port]
```

**脚本功能说明：**
- 自动检查Java环境
- 自动下载所需依赖库
- 支持编译客户端和服务器端
- 提供简单的运行命令
- 支持命令行参数传递

**脚本选项：**
- `-c, --compile` - 编译所有代码
- `-s, --server` - 运行服务器端
- `-cl, --client` - 运行客户端
- `-h, --help` - 显示帮助信息

3. **手动编译项目**（可选）
```bash
# 编译客户端
javac -cp .:chatroom/client/lib/* -d chatroom/client/bin $(find chatroom/client -name "*.java")

# 编译服务器端
javac -cp .:chatroom/server/lib/* -d chatroom/server/bin $(find chatroom/server -name "*.java")
```

4. **配置数据库**（可选）
- 创建数据库
- 执行数据库脚本创建表结构：`mysql -u username -p new_database < sql/chatroom/schema.sql`
- 修改 `server/sql/database.properties` 配置文件
- 将修改后的配置文件复制到bin目录：`cp chatroom/server/sql/database.properties chatroom/server/bin/server/sql`

**数据库表结构：**
- `user`：用户表，存储用户信息
- `room`：房间表，存储房间信息（公开/私有）
- `room_member`：房间成员表，管理用户与房间的关联
- `user_uuid`：用户UUID表，管理用户会话标识
- `messages`：消息表，存储所有消息历史（房间消息和私人消息）
- `friendships`：好友关系表，存储用户之间的好友关系
- `friend_requests`：好友请求表，管理好友请求状态（PENDING/ACCEPTED/REJECTED）

5. **MySQL自动配置**
项目提供了 `setup_mysql.sh` 脚本，可以帮助您自动配置MySQL数据库：

```bash
# 运行MySQL配置脚本（需要root权限）
./setup_mysql.sh
```

脚本功能：
- 检测并安装MySQL
- 创建数据库和用户
- 设置权限
- 创建表结构
- 配置时区
- 更新database.properties文件

6. **运行项目**

## 使用说明

### 启动服务器

```bash
# 使用自动化脚本启动服务器（推荐）
./run.sh -s

# 指定端口启动服务器
./run.sh -s [port]

# 使用手动命令启动服务器
java -cp .:chatroom/server/bin:chatroom/server/lib/* server.ChatServer

# 指定端口启动服务器
java -cp .:chatroom/server/bin:chatroom/server/lib/* server.ChatServer [port]
```

### 启动管理后台

```bash
# 使用Maven启动Spring Boot管理后台
cd chatroom/server
mvn spring-boot:run

# 或者先打包再运行
mvn clean package
java -jar target/chatroom-admin-1.0.0.jar
```

管理后台默认运行在 `http://localhost:8083`，提供以下功能：
- **仪表板**：查看系统统计信息（用户数、房间数、消息数）
- **用户管理**：查看、创建、删除用户，修改用户密码，查看用户好友和房间
- **房间管理**：查看和管理所有房间
- **消息管理**：查看消息历史和统计信息

### 启动客户端

```bash
# 使用自动化脚本启动客户端（推荐）
./run.sh -cl

# 指定服务器地址和端口连接
./run.sh -cl localhost/[ip] [port]

# 使用手动命令启动客户端
java -cp .:chatroom/client/bin:chatroom/client/lib/* client.Client

# 指定服务器地址和端口连接
java -cp .:chatroom/client/bin:chatroom/client/lib/* client.Client localhost/[ip] [port]
```

### Web客户端

1. 启动服务器
2. 打开浏览器访问 `http://localhost:[port]`
3. 注册或登录账号
4. 创建房间或加入房间
5. 开始聊天

### 好友功能使用说明

好友系统允许用户之间建立好友关系并进行私人聊天：

1. **发送好友请求**：
   - 在聊天客户端中使用 `/addfriend <username>` 命令发送好友请求
   - 系统会向目标用户发送好友请求通知

2. **处理好友请求**：
   - 使用 `/accept <username>` 接受好友请求
   - 使用 `/reject <username>` 拒绝好友请求
   - 使用 `/friends` 查看所有好友请求

3. **好友聊天**：
   - 添加好友后，可以使用 `/chat <username>` 开始私人聊天
   - 好友之间的消息是私密的，只有双方可见

4. **管理好友**：
   - 使用 `/removefriend <username>` 删除好友
   - 使用 `/listfriends` 查看好友列表

## 项目结构

```
ejp/
├── chatroom/
│   ├── client/            # 客户端代码
│   │   ├── bin/           # 编译输出目录
│   │   ├── lib/           # 客户端依赖库
│   │   ├── message/       # 消息相关类
│   │   │   ├── Message.java
│   │   │   ├── MessageCodec.java
│   │   │   └── MessageType.java
│   │   ├── network/       # 网络连接类
│   │   │   └── ClientConnection.java
│   │   ├── ui/            # 用户界面类
│   │   │   ├── UserInterface.java
│   │   │   └── AuthenticationInterface.java
│   │   ├── util/          # 工具类
│   │   │   └── UUIDCache.java
│   │   ├── web/           # Web客户端代码
│   │   │   ├── css/
│   │   │   │   └── style.css
│   │   │   ├── js/
│   │   │   │   ├── chat.js
│   │   │   │   └── localStorage.js
│   │   │   └── test_sync.html
│   │   ├── user_cache.properties
│   │   └── Client.java    # 客户端主类
│   └── server/            # 服务器端代码
│       ├── bin/           # 编译输出目录
│       ├── lib/           # 服务器端依赖库
│       ├── src/           # Spring Boot管理后台源码
│       │   └── main/
│       │       ├── java/admin/     # 管理后台Java代码
│       │       │   ├── AdminApplication.java
│       │       │   ├── controller/    # 控制器
│       │       │   │   ├── MainController.java
│       │       │   │   ├── UserController.java
│       │       │   │   ├── RoomController.java
│       │       │   │   └── MessageController.java
│       │       │   └── service/       # 服务层
│       │       │       ├── UserService.java
│       │       │       ├── RoomService.java
│       │       │       └── MessageService.java
│       │       └── resources/         # 资源文件
│       │           ├── application.properties
│       │           └── templates/     # Thymeleaf模板
│       │               ├── index.html
│       │               ├── users.html
│       │               ├── rooms.html
│       │               └── messages.html
│       ├── target/      # Maven编译输出目录
│       ├── pom.xml      # Maven项目配置文件
│       ├── message/     # 消息相关类
│       │   ├── Message.java
│       │   ├── MessageCodec.java
│       │   └── MessageType.java
│       ├── network/     # 网络连接类
│       │   ├── router/
│       │   │   └── MessageRouter.java
│       │   ├── socket/    # Socket相关类
│       │   │   ├── ServerListener.java
│       │   │   ├── ClientConnection.java
│       │   │   └── Session.java
│       │   └── websocket/ # WebSocket相关类
│       │       ├── WebSocketServer.java
│       │       ├── WebSocketConnection.java
│       │       └── WebSocketClientConnectionAdapter.java
│       ├── room/        # 房间相关类
│       │   ├── Room.java
│       │   ├── PublicRoom.java
│       │   └── PrivateRoom.java
│       ├── sql/         # 数据库相关类
│       │   ├── DatabaseManager.java
│       │   ├── database.properties
│       │   ├── user/
│       │   │   ├── UserDAO.java
│       │   │   └── uuid/
│       │   │       └── UUIDGenerator.java
│       │   ├── room/
│       │   │   └── RoomDAO.java
│       │   ├── message/
│       │   │   └── MessageDAO.java
│       │   └── friend/    # 好友相关类
│       │       ├── FriendshipDAO.java
│       │       └── FriendRequestDAO.java
│       ├── user/        # 用户相关类
│       │   └── User.java
│       ├── util/        # 工具类
│       │   └── AESUtil.java
│       ├── config/      # 配置类
│       │   └── ServiceConfig.java
│       ├── zfile/       # ZFile文件服务器集成
│       │   ├── ZFileConfig.java
│       │   └── ZFileTokenManager.java
│       └── ChatServer.java # 服务器主类
├── run.sh                # 自动化编译和运行脚本
├── setup_mysql.sh        # MySQL自动配置脚本
├── .gitignore            # Git忽略文件配置
├── LICENSE               # 许可证文件
└── README.md             # 项目说明文件
```

## 技术栈

### 后端技术
- Java 8+ / Java 17（管理后台）
- Socket API
- WebSocket API
- JDBC
- MySQL（可选）
- Spring Boot 3.2.0（管理后台）
- Spring MVC（管理后台）
- Spring Data JPA（管理后台）
- Thymeleaf（管理后台模板引擎）

### 前端技术
- HTML5
- CSS3
- JavaScript
- WebSocket API
- Thymeleaf（管理后台）

## 开发说明

### 代码规范
- 遵循Java编码规范
- 使用Javadoc注释
- 采用MVC设计模式

### 调试方法
- 使用Java日志系统进行调试
- 服务器端日志输出到控制台
- 客户端支持DEBUG模式

### 核心功能说明

#### 消息加密
项目使用AES-GCM加密算法对敏感消息内容进行加密：
- 加密密钥：基于SHA-256派生
- 加密模式：AES/GCM/NoPadding
- IV长度：12字节
- 标签长度：128位

#### 会话管理
使用UUID作为用户会话标识：
- 每次登录生成唯一UUID
- UUID存储在数据库的`user_uuid`表中
- 支持多设备同时登录

#### ZFile集成
支持与ZFile文件服务器集成：
- 自动获取上传Token
- 支持文件上传功能
- 可配置ZFile服务器地址和凭证

#### 消息历史
支持消息历史记录查询：
- 房间消息历史
- 私人消息历史
- 支持分页查询
- 支持时间范围查询

#### Web客户端特性
- 实时WebSocket通信
- localStorage数据持久化
- BroadcastChannel跨窗口消息同步
- 响应式设计

#### 好友系统
- 好友请求机制：支持发送、接受、拒绝好友请求
- 好友关系管理：建立和删除好友关系
- 好友聊天：支持好友之间的私人聊天
- 好友查询：支持查询用户的好友列表和好友请求状态
- 数据持久化：好友关系和请求存储在数据库中

#### 管理后台特性
- 基于Spring Boot的Web管理界面
- RESTful API设计
- Thymeleaf模板引擎
- 用户管理：查看、创建、删除用户，修改密码
- 房间管理：查看和管理所有房间
- 消息管理：查看消息历史和统计信息
- 响应式设计，支持移动端访问

### 数据库配置

#### database.properties配置示例
```properties
# 数据库驱动配置
db.driver=com.mysql.cj.jdbc.Driver

# 数据库连接配置
db.url=jdbc:mysql://localhost:3306/dbname?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
db.user=your_username
db.password=your_password
```

### ZFile配置

#### 什么是ZFile？
ZFile是一个开源的文件管理系统，本项目集成ZFile用于支持文件上传功能。通过ZFile，用户可以在聊天中发送图片和文件。

#### ZFile集成说明
本项目通过以下方式与ZFile集成：
- **ZFileTokenManager**：管理ZFile服务器的认证token
- **ZFileConfig**：配置ZFile服务器连接信息
- **消息流程**：
  1. 客户端发送`REQUEST_TOKEN`消息请求上传token
  2. 服务器通过ZFileTokenManager获取ZFile的认证token
  3. 服务器返回`TOKEN_RESPONSE`消息给客户端
  4. 客户端使用token直接向ZFile服务器上传文件
  5. 上传成功后，客户端发送文件URL消息到聊天室

#### ZFile服务器要求
- **ZFile版本**：建议使用ZFile 3.x或更高版本
- **网络要求**：EJP服务器需要能够访问ZFile服务器
- **认证要求**：需要配置ZFile的用户名和密码
- **API要求**：ZFile需要提供以下API：
  - `/user/login` - 用户登录接口（返回token）

#### ZFile配置步骤

**1. 安装和配置ZFile服务器**
```bash
# 参考ZFile官方文档安装ZFile
# https://github.com/zfile-dev/zfile
```

**2. 修改ZFileTokenManager配置**
编辑文件：`chatroom/server/zfile/ZFileTokenManager.java`

找到构造函数中的配置行：
```java
config = new ZFileConfig("http://ip:port", "username", "password");
```

修改为你的ZFile服务器配置：
```java
config = new ZFileConfig("http://your-zfile-server:port", "your-username", "your-password");
```

**配置参数说明：**
- `http://ip:port`：ZFile服务器地址和端口
- `username`：ZFile登录用户名
- `password`：ZFile登录密码

**3. 重新编译服务器**
```bash
./run.sh -c
```

**4. 启动服务器**
```bash
./run.sh -s
```

#### 使用示例

**客户端发送文件：**
```javascript
// 1. 请求上传token
chatClient.sendMessage(MessageType.REQUEST_TOKEN, 'server', '');

// 2. 收到TOKEN_RESPONSE后，使用token上传文件到ZFile
// 3. 上传成功后，发送文件URL消息
chatClient.sendMessage(MessageType.FILE, 'room-name', 'http://zfile-server/file-url');
```

#### 注意事项
- **安全性**：ZFile密码明文存储在代码中，生产环境建议使用配置文件或环境变量
- **网络**：确保EJP服务器能够访问ZFile服务器
- **Token管理**：ZFileTokenManager实现了token缓存机制，减少重复登录
- **可选功能**：如果不使用文件上传功能，可以忽略ZFile配置

## 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 贡献

欢迎提交Issue和Pull Request！

## 联系方式

如有问题或建议，请通过以下方式联系：
- Email: jy2193807541@gmail.com
- GitHub: https://github.com/102300671/ejp

---

© 2025-2026 EJP Chat Application. All rights reserved.