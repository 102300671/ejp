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
ADMIN_DIR="$SERVER_DIR"

# 主类
CLIENT_MAIN="client.Client"
SERVER_MAIN="server.ChatServer"
ADMIN_MAIN="admin.AdminApplication"

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

# 检查Maven是否安装
check_maven() {
    log_info "检查Maven是否安装..."
    if command -v mvn &> /dev/null; then
        MAVEN_VERSION=$(mvn -version | head -n 1 | awk '{print $3}')
        log_success "Maven已安装 (版本: $MAVEN_VERSION)"
        return 0
    else
        log_warning "Maven未安装，管理后台功能将不可用"
        log_info "如需使用管理后台，请先安装Maven: https://maven.apache.org/download.cgi"
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

# 编译管理后台
compile_admin() {
    log_info "编译Spring Boot管理后台..."
    
    cd "$ADMIN_DIR"
    
    # 检查Maven是否安装
    if ! check_maven; then
        log_error "Maven未安装，无法编译管理后台"
        return 1
    fi
    
    # 使用Maven编译
    mvn clean compile
    
    if [ $? -eq 0 ]; then
        log_success "管理后台编译成功"
        return 0
    else
        log_error "管理后台编译失败"
        return 1
    fi
}

# 打包管理后台
package_admin() {
    log_info "打包Spring Boot管理后台..."
    
    cd "$ADMIN_DIR"
    
    # 检查Maven是否安装
    if ! check_maven; then
        log_error "Maven未安装，无法打包管理后台"
        return 1
    fi
    
    # 使用Maven打包
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        log_success "管理后台打包成功"
        return 0
    else
        log_error "管理后台打包失败"
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
    
    # 编译服务器端和客户端
    if compile_server && compile_client; then
        log_success "服务器端和客户端编译成功"
    else
        log_error "服务器端或客户端编译失败"
        return 1
    fi
    
    # 编译管理后台（如果Maven可用）
    if check_maven; then
        if compile_admin; then
            log_success "管理后台编译成功"
        else
            log_warning "管理后台编译失败，但服务器端和客户端编译成功"
        fi
    else
        log_warning "Maven未安装，跳过管理后台编译"
    fi
    
    log_success "所有代码编译完成"
    return 0
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

# 运行管理后台
run_admin() {
    log_info "======================================"
    log_info "            运行管理后台              "
    log_info "======================================"
    
    cd "$ADMIN_DIR"
    
    # 检查Maven是否安装
    if ! check_maven; then
        log_error "Maven未安装，无法运行管理后台"
        return 1
    fi
    
    # 检查是否需要编译
    if [ ! -d "target/classes" ]; then
        log_warning "管理后台未编译，正在编译..."
        if ! compile_admin; then
            return 1
        fi
    fi
    
    # 运行管理后台
    mvn spring-boot:run "$@"
}

# 显示帮助信息
show_help() {
    log_info "======================================"
    log_info "        EJP 聊天应用运行脚本          "
    log_info "======================================"
    echo -e "
用法: ./run.sh [选项] [参数]

选项:
  -c, --compile     编译所有代码（包括管理后台）
  -s, --server      运行服务器端
  -cl, --client     运行客户端
  -a, --admin       运行Spring Boot管理后台
  -pa, --package    打包管理后台（生成JAR文件）
  -h, --help        显示帮助信息

参数:
  服务器: ./run.sh -s [端口]            指定服务器端口
  客户端: ./run.sh -cl [服务器地址] [端口]  指定服务器地址和端口
  管理后台: ./run.sh -a [端口]          指定管理后台端口（默认8083）

示例:
  ./run.sh -c                            编译所有代码
  ./run.sh -s                            运行服务器（默认端口）
  ./run.sh -s 8080                       运行服务器（指定端口）
  ./run.sh -cl                           运行客户端（默认配置）
  ./run.sh -cl localhost 8080            运行客户端（指定服务器）
  ./run.sh -a                            运行管理后台（默认端口8083）
  ./run.sh -a 9090                       运行管理后台（指定端口9090）
  ./run.sh -pa                           打包管理后台
  ./run.sh -c && ./run.sh -s 8080        编译并运行服务器
  ./run.sh -c && ./run.sh -a             编译并运行管理后台
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
        -a|--admin)
            shift
            run_admin "$@"
            ;;
        -pa|--package)
            package_admin
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