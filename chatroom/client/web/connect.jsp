<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>ChatRoom - Connect to Server</title>
    <link rel="stylesheet" href="css/style.css">
    <style>
        .connect-container {
            width: 400px;
            max-width: 90vw;
            padding: 40px;
            background-color: white;
            border-radius: 10px;
            box-shadow: 0 0 20px rgba(0, 0, 0, 0.1);
            overflow-y: auto;
            -webkit-overflow-scrolling: touch;
            max-height: 90vh;
        }
        
        @media (max-width: 768px) {
            .connect-container {
                width: 90vw;
                max-width: 90vw;
                padding: 20px;
                max-height: 85vh;
                border-radius: 8px;
            }
            
            .logo h1 {
                font-size: 24px;
            }
            
            h2 {
                font-size: 18px;
            }
            
            p {
                font-size: 14px;
            }
        }
        
        .connect-form {
            margin-top: 20px;
        }
        
        .logo {
            text-align: center;
            margin-bottom: 30px;
        }
        
        .logo h1 {
            color: #4a6fa5;
            font-size: 32px;
        }
        
        .status {
            margin-top: 20px;
            padding: 10px;
            border-radius: 5px;
            text-align: center;
        }
        
        .status.success {
            background-color: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        
        .status.error {
            background-color: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
        
        .status.connecting {
            background-color: #e2e3e5;
            color: #383d41;
            border: 1px solid #d6d8db;
        }
        
        .status.info {
            background-color: #d1ecf1;
            color: #0c5460;
            border: 1px solid #bee5eb;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="connect-container">
            <div class="logo">
                <h1>聊天室</h1>
            </div>
            <h2>连接到聊天服务器</h2>
            <p>请输入服务器地址和端口进行连接:</p>
            
            <form class="connect-form">
                <div class="form-group">
                    <label for="server-ip">服务器IP:</label>
                    <input type="text" id="server-ip" name="serverIp" value="localhost" required>
                </div>
                <div class="form-group">
                    <label for="ws-port">WebSocket端口:</label>
                    <input type="number" id="ws-port" name="wsPort" value="8889" min="1" max="65535" required>
                </div>
                <div class="form-group">
                    <label for="ws-protocol">WebSocket协议:</label>
                    <select id="ws-protocol" name="wsProtocol" required>
                        <option value="ws">ws:// (用于HTTP页面)</option>
                        <option value="wss">wss:// (用于HTTPS页面)</option>
                    </select>
                    <small style="display: block; margin-top: 5px; color: #666;">根据当前页面协议自动检测</small>
                </div>
                <div class="form-group">
                    <button type="submit" id="connect-btn">连接</button>
                </div>
            </form>
            
            <div id="status" class="status info">
                准备连接...
            </div>
        </div>
    </div>
    
    <script src="js/chat.js"></script>
    <script>
        // Initialize connection page
        document.addEventListener('DOMContentLoaded', function() {
            const connectForm = document.querySelector('.connect-form');
            const connectBtn = document.getElementById('connect-btn');
            const statusDiv = document.getElementById('status');
            const wsProtocolSelect = document.getElementById('ws-protocol');
            
            // 自动检测WebSocket协议：HTTPS页面使用wss://，HTTP页面使用ws://
            const currentProtocol = window.location.protocol;
            const autoDetectedProtocol = currentProtocol === 'https:' ? 'wss' : 'ws';
            wsProtocolSelect.value = autoDetectedProtocol;
            
            console.log('当前页面协议:', currentProtocol);
            console.log('自动检测的WebSocket协议:', autoDetectedProtocol);
            
            connectForm.addEventListener('submit', function(e) {
                e.preventDefault();
                
                const serverIp = document.getElementById('server-ip').value.trim();
                const wsPort = document.getElementById('ws-port').value.trim();
                const wsProtocol = document.getElementById('ws-protocol').value;
                
                // Debug: Log form values
                console.log('Form submitted with serverIp:', serverIp);
                console.log('Form submitted with wsPort:', wsPort);
                console.log('Form submitted with wsProtocol:', wsProtocol);
                
                // Validate inputs
                if (!serverIp || !wsPort || !wsProtocol) {
                    showStatus('Please enter all required fields', 'error');
                    return;
                }
                
                // Disable button during connection
                connectBtn.disabled = true;
                connectBtn.textContent = 'Connecting...';
                
                showStatus('Connecting to ' + serverIp + ' (WebSocket: ' + wsProtocol + '://' + serverIp + ':' + wsPort + ')...', 'info');
                
                // Save to sessionStorage explicitly
                sessionStorage.setItem('serverIp', serverIp);
                sessionStorage.setItem('wsPort', wsPort);
                sessionStorage.setItem('wsProtocol', wsProtocol);
                
                // Connect to server
                chatClient.connectToServer(serverIp, wsPort, wsProtocol);
            });
            
            function showStatus(message, type) {
                statusDiv.textContent = message;
                statusDiv.className = 'status ' + type;
            }
        });
    </script>
</body>
</html>