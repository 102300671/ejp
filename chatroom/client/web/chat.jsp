<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>ChatRoom - Main</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>ChatRoom</h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <button id="logout-btn">Logout</button>
                </div>
            </div>
            
            <div class="chat-content">
                <!-- Rooms List -->
                <div class="rooms-panel">
                    <div class="panel-header">
                        <h3>Rooms</h3>
                        <button id="create-room-btn">Create Room</button>
                    </div>
                    <div id="rooms-list" class="rooms-list">
                        <!-- Rooms will be populated here -->
                    </div>
                    <div class="panel-footer">
                        <button id="refresh-rooms-btn">Refresh</button>
                    </div>
                </div>
                
                <!-- Messages Area -->
                <div class="messages-panel">
                    <div class="panel-header">
                        <h3 id="current-room-name">system</h3>
                        <div class="room-controls">
                                <button id="join-room-btn">Join</button>
                                <button id="leave-room-btn">Leave</button>
                                <button id="exit-room-btn">Exit</button>
                            </div>
                    </div>
                    <div id="messages-area" class="messages-area">
                        <!-- Messages will be displayed here -->
                    </div>
                    <div class="message-input">
                        <input type="file" id="image-input" accept="image/*" style="display: none;">
                        <button id="image-btn" title="Send Image">📷</button>
                        <input type="text" id="message-input" placeholder="Type your message...">
                        <button id="send-btn">Send</button>
                        <button id="private-msg-btn">Members</button>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Create Room Modal -->
        <div id="create-room-modal" class="modal">
            <div class="modal-content">
                <span class="close">&times;</span>
                <h3>Create New Room</h3>
                <form id="create-room-form">
                    <div class="form-group">
                        <label for="room-name">Room Name:</label>
                        <input type="text" id="room-name" required>
                    </div>
                    <div class="form-group">
                        <label for="room-type">Room Type:</label>
                        <select id="room-type">
                            <option value="PUBLIC">Public</option>
                            <option value="PRIVATE">Private</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <button type="submit">Create</button>
                    </div>
                </form>
            </div>
        </div>
        
        <!-- Join Room Modal -->
        <div id="join-room-modal" class="modal">
            <div class="modal-content">
                <span class="close">&times;</span>
                <h3>Join Room</h3>
                <form id="join-room-form">
                    <div class="form-group">
                        <label for="join-room-name">Room Name:</label>
                        <input type="text" id="join-room-name" required placeholder="Enter room name to join">
                    </div>
                    <div class="form-group">
                        <button type="submit">Join</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
    
    <script src="js/localStorage.js"></script>
    <script src="js/chat.js"></script>
    <script>
        // Initialize the chat functionality
        document.addEventListener('DOMContentLoaded', function() {
            initChat();
        });
    </script>
</body>
</html>