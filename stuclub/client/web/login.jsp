<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>学生社团管理系统 - 登录/注册</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>欢迎来到学生社团管理系统</h2>
            </div>
            
            <!-- Current Connection Info -->
            <div id="connection-info" class="connection-info">
                <div class="connection-details">
                    <span id="server-ip-port"></span>
                </div>
                <button id="disconnect-btn" class="disconnect-btn">断开连接</button>
            </div>
            
            <div class="auth-container">
                <div class="auth-tabs">
                    <button class="tab-btn active" onclick="switchTab('login')">登录</button>
                    <button class="tab-btn" onclick="switchTab('register')">注册</button>
                </div>
                
                <!-- Login Form -->
                <div id="login-tab" class="tab-content active">
                    <form id="login-form">
                        <div class="form-group">
                            <label for="login-student-id">学号/工号:</label>
                            <input type="text" id="login-student-id" name="studentId" placeholder="请输入学号或工号" required>
                        </div>
                        <div class="form-group">
                            <label for="login-password">密码:</label>
                            <input type="password" id="login-password" name="password" required>
                        </div>
                        <div class="form-group">
                            <button type="submit">登录</button>
                        </div>
                    </form>
                </div>
                
                <!-- Register Form -->
                <div id="register-tab" class="tab-content">
                    <form id="register-form">
                        <div class="form-group avatar-upload-group">
                            <label>头像（可选）:</label>
                            <div class="avatar-upload-container">
                                <div class="avatar-preview-wrapper">
                                    <img id="register-avatar-preview" src="" alt="头像预览" class="avatar-preview">
                                    <div class="avatar-placeholder">+</div>
                                </div>
                                <button type="button" id="upload-avatar-btn" class="upload-avatar-btn">上传头像</button>
                                <button type="button" id="remove-avatar-btn" class="remove-avatar-btn" style="display: none;">移除</button>
                                <input type="file" id="register-avatar-input" accept="image/*" style="display: none;">
                            </div>
                            <p class="avatar-hint">点击上传头像文件（可选）</p>
                        </div>
                        <div class="form-group">
                            <label for="register-username">用户名:</label>
                            <input type="text" id="register-username" name="username" required>
                        </div>
                        <div class="form-group">
                            <label for="register-real-name">真实姓名:</label>
                            <input type="text" id="register-real-name" name="realName" required>
                        </div>
                        <div class="form-group">
                            <label for="register-role">身份:</label>
                            <select id="register-role" name="role" required>
                                <option value="">请选择身份</option>
                                <option value="STUDENT">学生</option>
                                <option value="TEACHER">老师</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="register-student-id" id="register-id-label">学号:</label>
                            <input type="text" id="register-student-id" name="studentId" required>
                        </div>
                        <div class="form-group">
                            <label for="register-password">密码:</label>
                            <input type="password" id="register-password" name="password" required>
                        </div>
                        <div class="form-group">
                            <label for="register-confirm">确认密码:</label>
                            <input type="password" id="register-confirm" name="confirm" required>
                        </div>
                        <div class="form-group">
                            <button type="submit">注册</button>
                        </div>
                    </form>
                </div>
                
                <div id="message" class="message"></div>
            </div>
        </div>
    </div>
    
    <script src="js/chat.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            console.log('sessionStorage contents:', sessionStorage);
            
            let serverIp = sessionStorage.getItem('serverIp');
            let wsPort = sessionStorage.getItem('wsPort');
            
            if (!serverIp || !wsPort) {
                console.log('Trying to get server info from URL parameters...');
                const urlParams = new URLSearchParams(window.location.search);
                serverIp = serverIp || urlParams.get('serverIp');
                wsPort = wsPort || urlParams.get('wsPort');
            }
            
            console.log('Retrieved serverIp:', serverIp);
            console.log('Retrieved wsPort:', wsPort);
            
            serverIp = (serverIp || '').trim();
            wsPort = (wsPort || '').trim();
            
            if (!serverIp || !wsPort) {
                window.location.href = 'connect.jsp';
                return;
            }
            
            sessionStorage.setItem('serverIp', serverIp);
            sessionStorage.setItem('wsPort', wsPort);
            
            const serverIpPortElement = document.getElementById('server-ip-port');
            serverIpPortElement.textContent = `Connected to: ${serverIp}`;
            
            const roleSelect = document.getElementById('register-role');
            const idLabel = document.getElementById('register-id-label');
            const idInput = document.getElementById('register-student-id');
            
            roleSelect.addEventListener('change', function() {
                if (this.value === 'TEACHER') {
                    idLabel.textContent = '工号:';
                    idInput.placeholder = '请输入工号';
                } else {
                    idLabel.textContent = '学号:';
                    idInput.placeholder = '请输入学号';
                }
            });
            
            chatClient.connect();
            initLogin();
            
            const disconnectBtn = document.getElementById('disconnect-btn');
            disconnectBtn.addEventListener('click', function() {
                if (chatClient.ws && chatClient.ws.readyState === WebSocket.OPEN) {
                    chatClient.ws.close();
                }
                
                sessionStorage.removeItem('serverIp');
                sessionStorage.removeItem('serverPort');
                
                window.location.href = 'connect.jsp';
            });
        });
    </script>
</body>
</html>