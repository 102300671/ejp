#!/bin/bash

echo "测试system房间功能"
echo "===================="

# 编译服务器端
echo "正在编译服务器端..."
cd /home/jianying/ejp/chatroom/server
javac -cp ".:lib/*:../lib/*" -d . src/**/*.java

# 编译客户端
echo "正在编译客户端..."
cd /home/jianying/ejp/chatroom/client
javac -cp ".:lib/*" -d . src/**/*.java

echo "编译完成"
echo "启动服务器..."
# 后台启动服务器
cd /home/jianying/ejp/chatroom/server
java -cp ".:lib/*" ChatServer 8080 > server.log 2>&1 &
SERVER_PID=$!

# 等待服务器启动
sleep 5

echo "启动客户端1..."
# 启动第一个客户端
cd /home/jianying/ejp/chatroom/client
java -cp ".:lib/*" Client localhost 8080 > client1.log 2>&1 &
CLIENT1_PID=$!

# 等待客户端连接
sleep 3

echo "启动客户端2..."
# 启动第二个客户端
cd /home/jianying/ejp/chatroom/client
java -cp ".:lib/*" Client localhost 8080 > client2.log 2>&1 &
CLIENT2_PID=$!

# 等待客户端连接
sleep 3

echo "测试完成，检查日志..."

# 查看服务器日志
echo "\n服务器日志："
grep -i "system" /home/jianying/ejp/chatroom/server/server.log

# 查看客户端日志
echo "\n客户端1日志："
grep -i "system" /home/jianying/ejp/chatroom/client/client1.log

echo "\n客户端2日志："
grep -i "system" /home/jianying/ejp/chatroom/client/client2.log

echo "\n所有测试完成！"

echo "\n清理进程..."
# 终止进程
kill $CLIENT1_PID $CLIENT2_PID $SERVER_PID

# 等待进程终止
sleep 2

echo "测试脚本执行完毕"