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

### 安装方法

1. **克隆项目**
```bash
git clone https://github.com/yourusername/ejp.git
cd ejp
```

2. **编译项目**
```bash
# 编译客户端
cd chatroom/client && javac -cp .:lib/* $(find . -name "*.java")

# 编译服务器端
cd chatroom/server && javac -cp .:lib/* $(find . -name "*.java")
```

3. **配置数据库**（可选）
- 创建数据库
- 修改 `server/sql/database.properties` 配置文件

4. **运行项目**
```bash
# 运行服务器端
cd chatroom/server && java -cp .:bin:lib/* server.ChatServer [port]

# 运行客户端
cd chatroom/client && java -cp .:bin:lib/* client.Client localhost/[ip] [port]
```

## 使用说明

### 启动服务器

```bash
# 使用默认配置启动服务器
cd chatroom/server && java -cp .:bin:lib/* server.ChatServer

# 指定端口启动服务器
cd chatroom/server && java -cp .:bin:lib/* server.ChatServer [port]
```

### 启动客户端

```bash
# 使用默认配置连接服务器
java -cp chatroom/client/bin:chatroom/client/lib/* client.Client

# 指定服务器地址和端口连接
cd chatroom/client && java -cp .:bin:lib/* client.Client localhost/[ip] [port]
```

### Web客户端

1. 启动服务器
2. 打开浏览器访问 `http://localhost:[port]`
3. 输入用户名和密码登录

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