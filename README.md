# EJP Chat Application

EJP是一个基于Java开发的多功能聊天应用，支持TCP Socket和WebSocket连接，提供了客户端和服务器端的完整实现。

## 功能特点

### 核心功能
- **双协议支持**：同时支持TCP Socket和WebSocket连接
- **多客户端类型**：命令行客户端和Web客户端
- **用户认证**：支持用户登录和注册功能
- **房间管理**：支持创建、加入、离开房间
- **消息管理**：支持文本消息、系统消息等多种消息类型
- **数据库集成**：使用数据库存储房间信息和用户数据

### 高级功能
- **自动重连机制**：客户端断线后自动尝试重连
- **消息路由**：基于消息类型和目标的智能路由系统
- **多线程处理**：高性能的多线程架构
- **配置灵活**：支持命令行参数和配置文件

## 系统架构

### 服务器端
- **ServerListener**：处理TCP Socket连接
- **WebSocketServer**：处理WebSocket连接
- **MessageRouter**：消息路由和分发
- **DatabaseManager**：数据库操作管理
- **Room**：房间管理系统

### 客户端
- **ClientConnection**：与服务器的网络连接
- **UserInterface**：用户界面
- **AuthenticationInterface**：用户认证界面
- **Message**：消息处理系统

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

## 项目结构

```
ejp/
├── chatroom/
│   ├── client/            # 客户端代码
│   │   ├── bin/           # 编译输出目录
│   │   ├── lib/           # 客户端依赖库
│   │   ├── message/       # 消息相关类
│   │   ├── network/       # 网络连接类
│   │   ├── ui/            # 用户界面类
│   │   ├── util/          # 工具类
│   │   ├── web/           # Web客户端代码
│   │   └── Client.java    # 客户端主类
│   └── server/            # 服务器端代码
│       ├── bin/           # 编译输出目录
│       ├── lib/           # 服务器端依赖库
│       ├── message/       # 消息相关类
│       ├── network/       # 网络连接类
│       │   ├── socket/    # Socket相关类
│       │   └── websocket/ # WebSocket相关类
│       ├── room/          # 房间相关类
│       ├── sql/           # 数据库相关类
│       ├── user/          # 用户相关类
│       └── ChatServer.java # 服务器主类
├── LICENSE                # 许可证文件
└── README.md              # 项目说明文件
```

## 技术栈

### 后端技术
- Java 8+
- Socket API
- WebSocket API
- JDBC
- MySQL（可选）

### 前端技术
- HTML5
- CSS3
- JavaScript
- WebSocket API

## 开发说明

### 代码规范
- 遵循Java编码规范
- 使用Javadoc注释
- 采用MVC设计模式

### 调试方法
- 使用Java日志系统进行调试
- 服务器端日志输出到控制台
- 客户端支持DEBUG模式

## 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 贡献

欢迎提交Issue和Pull Request！

## 联系方式

如有问题或建议，请通过以下方式联系：
- Email: jy2193807541@gmail.com
- GitHub: https://github.com/102300671/ejp

---

© 2025 EJP Chat Application. All rights reserved.