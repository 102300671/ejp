#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
CLIENT_DIR="$PROJECT_ROOT/chatroom/client"
SERVER_DIR="$PROJECT_ROOT/chatroom/server"
CLIENT_LIB_DIR="$CLIENT_DIR/lib"
SERVER_LIB_DIR="$SERVER_DIR/lib"

# 主类
CLIENT_MAIN="client.Client"
SERVER_MAIN="server.ChatServer"

# 依赖库定义
SERVER_DEPENDENCIES=(
    "at.favre.lib:bcrypt:0.10.2:bcrypt-0.10.2.jar"
    "at.favre.lib:bytes:1.5.0:bytes-1.5.0.jar"
    "org.java-websocket:Java-WebSocket:1.5.7:Java-WebSocket-1.5.7.jar"
    "com.mysql:mysql-connector-j:9.5.0:mysql-connector-j-9.5.0.jar"
    "org.slf4j:slf4j-api:1.7.36:slf4j-api-1.7.36.jar"
    "org.slf4j:slf4j-simple:1.7.36:slf4j-simple-1.7.36.jar"
    "com.google.code.gson:gson:2.13.2:gson-2.13.2.jar"
)

CLIENT_DEPENDENCIES=(
    "com.google.code.gson:gson:2.13.2:gson-2.13.2.jar"
)

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
    log "${BLUE}" "ℹ ${1}"
}

# 检查下载工具是否安装
check_download_tool() {
    log_info "检查下载工具是否安装..."
    if command -v wget &> /dev/null; then
        DOWNLOAD_TOOL="wget"
        log_success "已找到wget"
        return 0
    elif command -v curl &> /dev/null; then
        DOWNLOAD_TOOL="curl"
        log_success "已找到curl"
        return 0
    else
        log_error "未找到wget或curl，请先安装下载工具"
        return 1
    fi
}

# 下载依赖库
download_dependency() {
    local group_id="${1%%:*}"
    local artifact_id="${2%%:*}"
    local version="${3%%:*}"
    local jar_name="${4}"
    local dest_dir="${5}"
    
    # 创建目标目录
    mkdir -p "$dest_dir"
    
    # 检查文件是否已存在，如果存在且大小为0则删除
    if [ -f "$dest_dir/$jar_name" ]; then
        if [ ! -s "$dest_dir/$jar_name" ]; then
            log_warning "依赖 $jar_name 已存在但为空，将重新下载"
            rm -f "$dest_dir/$jar_name"
        else
            log_warning "依赖 $jar_name 已存在，跳过下载"
            return 0
        fi
    fi
    
    log_info "下载依赖: $jar_name"
    
    # Maven中央仓库URL
    local maven_url="https://repo1.maven.org/maven2/${group_id//.//}/${artifact_id}/${version}/${jar_name}"
    
    # 根据可用工具下载
    if [ "$DOWNLOAD_TOOL" = "wget" ]; then
        wget -q -O "$dest_dir/$jar_name" "$maven_url"
    else
        curl -s -L -o "$dest_dir/$jar_name" "$maven_url"
    fi
    
    # 检查下载是否成功且文件不为空
    if [ $? -eq 0 ] && [ -s "$dest_dir/$jar_name" ]; then
        log_success "依赖 $jar_name 下载成功"
        return 0
    else
        # 删除空文件
        rm -f "$dest_dir/$jar_name"
        log_error "依赖 $jar_name 下载失败或文件为空"
        return 1
    fi
}

# 下载所有依赖
download_all_dependencies() {
    log_info "======================================"
    log_info "            下载依赖库                 "
    log_info "======================================"
    
    # 检查下载工具
    if ! check_download_tool; then
        return 1
    fi
    
    # 下载服务器端依赖
    log_info "下载服务器端依赖..."
    for dep in "${SERVER_DEPENDENCIES[@]}"; do
        IFS=':' read -r -a dep_parts <<< "$dep"
        if [ ${#dep_parts[@]} -ne 4 ]; then
            log_error "依赖格式错误: $dep"
            continue
        fi
        if ! download_dependency "${dep_parts[@]}" "$SERVER_LIB_DIR"; then
            return 1
        fi
    done
    
    # 下载客户端依赖
    log_info "下载客户端依赖..."
    for dep in "${CLIENT_DEPENDENCIES[@]}"; do
        IFS=':' read -r -a dep_parts <<< "$dep"
        if [ ${#dep_parts[@]} -ne 4 ]; then
            log_error "依赖格式错误: $dep"
            continue
        fi
        if ! download_dependency "${dep_parts[@]}" "$CLIENT_LIB_DIR"; then
            return 1
        fi
    done
    
    log_success "所有依赖下载完成"
    return 0
}

# 检查Java是否安装
check_java() {
    log_info "检查Java是否安装..."
    if command -v java &> /dev/null && command -v javac &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
        log_success "Java已安装 (版本: $JAVA_VERSION)"
        return 0
    else
        log_error "Java或javac未安装，请先安装Java开发环境"
        return 1
    fi
}

# 编译客户端
compile_client() {
    log_info "编译客户端..."
    
    cd "$CLIENT_DIR"
    
    # 创建bin目录（如果不存在）
    mkdir -p bin
    
    # 编译客户端代码
    javac -cp .:lib/* -d bin $(find . -name "*.java")
    
    if [ $? -eq 0 ]; then
        log_success "客户端编译成功"
        return 0
    else
        log_error "客户端编译失败"
        return 1
    fi
}

# 编译服务器端
compile_server() {
    log_info "编译服务器端..."
    
    cd "$SERVER_DIR"
    
    # 创建bin目录（如果不存在）
    mkdir -p bin
    
    # 编译服务器端代码
    javac -cp .:lib/* -d bin $(find . -name "*.java")
    
    if [ $? -eq 0 ]; then
        # 复制数据库配置文件到bin目录
        mkdir -p bin/server/sql
        if [ -f sql/database.properties ]; then
            cp sql/database.properties bin/server/sql/
            log_info "数据库配置文件已复制到bin目录"
        fi
        log_success "服务器端编译成功"
        return 0
    else
        log_error "服务器端编译失败"
        return 1
    fi
}

# 编译所有代码
compile_all() {
    log_info "======================================"
    log_info "             编译所有代码             "
    log_info "======================================"
    
    # 下载依赖
    if ! download_all_dependencies; then
        log_warning "依赖下载失败，尝试使用已有依赖继续编译"
    fi
    
    if compile_server && compile_client; then
        log_success "所有代码编译成功"
        return 0
    else
        log_error "编译失败"
        return 1
    fi
}

# 运行客户端
run_client() {
    log_info "======================================"
    log_info "              运行客户端              "
    log_info "======================================"
    
    cd "$CLIENT_DIR"
    
    # 检查bin目录是否存在
    if [ ! -d "bin" ]; then
        log_warning "客户端bin目录不存在，正在编译..."
        if ! compile_client; then
            return 1
        fi
    fi
    
    # 运行客户端
    java -cp "bin:lib/*" $CLIENT_MAIN "$@"
}

# 运行服务器端
run_server() {
    log_info "======================================"
    log_info "              运行服务器              "
    log_info "======================================"
    
    cd "$SERVER_DIR"
    
    # 检查bin目录是否存在
    if [ ! -d "bin" ]; then
        log_warning "服务器端bin目录不存在，正在编译..."
        if ! compile_server; then
            return 1
        fi
    fi
    
    # 运行服务器
    java -cp "bin:lib/*" $SERVER_MAIN "$@"
}

# 显示帮助信息
show_help() {
    log_info "======================================"
    log_info "        EJP 聊天应用运行脚本          "
    log_info "======================================"
    echo -e "
用法: ./run.sh [选项] [参数]

选项:
  -c, --compile     编译所有代码
  -s, --server      运行服务器端
  -cl, --client     运行客户端
  -h, --help        显示帮助信息

参数:
  服务器: ./run.sh -s [端口]            指定服务器端口
  客户端: ./run.sh -cl [服务器地址] [端口]  指定服务器地址和端口

示例:
  ./run.sh -c                            编译所有代码
  ./run.sh -s                            运行服务器（默认端口）
  ./run.sh -s 8080                       运行服务器（指定端口）
  ./run.sh -cl                           运行客户端（默认配置）
  ./run.sh -cl localhost 8080            运行客户端（指定服务器）
  ./run.sh -c && ./run.sh -s 8080        编译并运行服务器
"
}

# 主函数
main() {
    # 检查Java
    if ! check_java; then
        exit 1
    fi
    
    # 解析命令行参数
    if [ $# -eq 0 ]; then
        show_help
        exit 0
    fi
    
    case "$1" in
        -c|--compile)
            compile_all
            ;;
        -s|--server)
            shift
            run_server "$@"
            ;;
        -cl|--client)
            shift
            run_client "$@"
            ;;
        -h|--help)
            show_help
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 运行主函数
main "$@"