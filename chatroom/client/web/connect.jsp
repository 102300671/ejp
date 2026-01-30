<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>ChatRoom - Connect to Server</title>
    <link rel="stylesheet" href="css/style.css">
    <style>
        .connect-container {
            width: 400px;
            padding: 40px;
            background-color: white;
            border-radius: 10px;
            box-shadow: 0 0 20px rgba(0, 0, 0, 0.1);
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
                <h1>ChatRoom</h1>
            </div>
            <h2>Connect to Chat Server</h2>
            <p>Please enter the server address and port to connect:</p>
            
            <form class="connect-form">
                <div class="form-group">
                    <label for="server-ip">Server IP:</label>
                    <input type="text" id="server-ip" name="serverIp" value="localhost" required>
                </div>
                <div class="form-group">
                    <label for="server-port">TCP Port:</label>
                    <input type="number" id="server-port" name="serverPort" value="8080" min="1" max="65535" required>
                </div>
                <div class="form-group">
                    <label for="ws-port">WebSocket Port:</label>
                    <input type="number" id="ws-port" name="wsPort" value="8081" min="1" max="65535" required>
                    <small style="display: block; margin-top: 5px; color: #666;">Default: TCP Port + 1</small>
                </div>
                <div class="form-group">
                    <label for="ws-protocol">WebSocket Protocol:</label>
                    <select id="ws-protocol" name="wsProtocol" required>
                        <option value="ws">ws:// (Default)</option>
                        <option value="wss">wss:// (For HTTPS pages)</option>
                    </select>
                </div>
                <div class="form-group">
                    <button type="submit" id="connect-btn">Connect</button>
                </div>
            </form>
            
            <div id="status" class="status info">
                Ready to connect...
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
            
            connectForm.addEventListener('submit', function(e) {
                e.preventDefault();
                
                const serverIp = document.getElementById('server-ip').value.trim();
                const serverPort = document.getElementById('server-port').value.trim();
                const wsPort = document.getElementById('ws-port').value.trim();
                const wsProtocol = document.getElementById('ws-protocol').value;
                
                // Debug: Log form values
                console.log('Form submitted with serverIp:', serverIp);
                console.log('Form submitted with serverPort:', serverPort);
                console.log('Form submitted with wsPort:', wsPort);
                console.log('Form submitted with wsProtocol:', wsProtocol);
                
                // Validate inputs
                if (!serverIp || !serverPort || !wsPort || !wsProtocol) {
                    showStatus('Please enter all required fields', 'error');
                    return;
                }
                
                // Disable button during connection
                connectBtn.disabled = true;
                connectBtn.textContent = 'Connecting...';
                
                showStatus('Connecting to ' + serverIp + ':' + serverPort + ' (WebSocket: ' + wsProtocol + '://' + serverIp + ':' + wsPort + ')...', 'info');
                
                // Save to sessionStorage explicitly
                sessionStorage.setItem('serverIp', serverIp);
                sessionStorage.setItem('serverPort', serverPort);
                sessionStorage.setItem('wsPort', wsPort);
                sessionStorage.setItem('wsProtocol', wsProtocol);
                
                // Connect to server
                chatClient.connectToServer(serverIp, serverPort, wsPort, wsProtocol);
            });
            
            function showStatus(message, type) {
                statusDiv.textContent = message;
                statusDiv.className = 'status ' + type;
            }
        });
    </script>
</body>
</html>