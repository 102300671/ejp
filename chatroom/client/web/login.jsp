<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>ChatRoom - Login/Register</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>Welcome to ChatRoom</h2>
            </div>
            
            <!-- Current Connection Info -->
            <div id="connection-info" class="connection-info">
                <div class="connection-details">
                    <span id="server-ip-port"></span>
                </div>
                <button id="disconnect-btn" class="disconnect-btn">Disconnect</button>
            </div>
            
            <div class="auth-container">
                <div class="auth-tabs">
                    <button class="tab-btn active" onclick="switchTab('login')">Login</button>
                    <button class="tab-btn" onclick="switchTab('register')">Register</button>
                </div>
                
                <!-- Login Form -->
                <div id="login-tab" class="tab-content active">
                    <form id="login-form">
                        <div class="form-group">
                            <label for="login-username">Username:</label>
                            <input type="text" id="login-username" name="username" required>
                        </div>
                        <div class="form-group">
                            <label for="login-password">Password:</label>
                            <input type="password" id="login-password" name="password" required>
                        </div>
                        <div class="form-group">
                            <button type="submit">Login</button>
                        </div>
                    </form>
                </div>
                
                <!-- Register Form -->
                <div id="register-tab" class="tab-content">
                    <form id="register-form">
                        <div class="form-group">
                            <label for="register-username">Username:</label>
                            <input type="text" id="register-username" name="username" required>
                        </div>
                        <div class="form-group">
                            <label for="register-password">Password:</label>
                            <input type="password" id="register-password" name="password" required>
                        </div>
                        <div class="form-group">
                            <label for="register-confirm">Confirm Password:</label>
                            <input type="password" id="register-confirm" name="confirm" required>
                        </div>
                        <div class="form-group">
                            <button type="submit">Register</button>
                        </div>
                    </form>
                </div>
                
                <div id="message" class="message"></div>
            </div>
        </div>
    </div>
    
    <script src="js/chat.js"></script>
    <script>
        // Check if server connection info exists
        document.addEventListener('DOMContentLoaded', function() {
            // Debug: Log sessionStorage contents
            console.log('sessionStorage contents:', sessionStorage);
            
            // Get server info from sessionStorage first
            let serverIp = sessionStorage.getItem('serverIp');
            let wsPort = sessionStorage.getItem('wsPort');
            
            // If sessionStorage doesn't have the info, try to get from URL parameters
            if (!serverIp || !wsPort) {
                console.log('Trying to get server info from URL parameters...');
                const urlParams = new URLSearchParams(window.location.search);
                serverIp = serverIp || urlParams.get('serverIp');
                wsPort = wsPort || urlParams.get('wsPort');
            }
            
            console.log('Retrieved serverIp:', serverIp);
            console.log('Retrieved wsPort:', wsPort);
            
            // Ensure values are not null or empty strings
            serverIp = (serverIp || '').trim();
            wsPort = (wsPort || '').trim();
            
            if (!serverIp || !wsPort) {
                // No valid server info, redirect to connect page
                window.location.href = 'connect.jsp';
                return;
            }
            
            // Save to sessionStorage
            sessionStorage.setItem('serverIp', serverIp);
            sessionStorage.setItem('wsPort', wsPort);
            
            // Display current connection info
            const serverIpPortElement = document.getElementById('server-ip-port');
            serverIpPortElement.textContent = `Connected to: ${serverIp}`;
            
            // Establish WebSocket connection first
            chatClient.connect();
            
            // Then initialize login
            initLogin();
            
            // Add disconnect button functionality
            const disconnectBtn = document.getElementById('disconnect-btn');
            disconnectBtn.addEventListener('click', function() {
                // Close WebSocket connection if it exists
                if (chatClient.ws && chatClient.ws.readyState === WebSocket.OPEN) {
                    chatClient.ws.close();
                }
                
                // Clear connection info from storage
                sessionStorage.removeItem('serverIp');
                sessionStorage.removeItem('serverPort');
                
                // Redirect to connect page
                window.location.href = 'connect.jsp';
            });
        });
    </script>
</body>
</html>