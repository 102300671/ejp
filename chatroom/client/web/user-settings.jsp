<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>ChatRoom - Settings</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>Settings</h2>
                <div class="header-actions">
                    <button id="back-to-chat-btn">Back to Chat</button>
                </div>
            </div>
            
            <div class="settings-container">
                <div class="settings-tabs">
                    <button class="settings-tab-btn active" data-tab="account">Account</button>
                    <button class="settings-tab-btn" data-tab="appearance">Appearance</button>
                    <button class="settings-tab-btn" data-tab="notifications">Notifications</button>
                    <button class="settings-tab-btn" data-tab="privacy">Privacy</button>
                    <button class="settings-tab-btn" data-tab="storage">Storage</button>
                </div>
                
                <div class="settings-content">
                    <div id="tab-account" class="settings-tab-content active">
                        <div class="settings-section">
                            <h3>Account Settings</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Change Password</h4>
                                    <p>Update your password to keep your account secure</p>
                                </div>
                                <button id="change-password-btn" class="settings-btn">Change</button>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Email Address</h4>
                                    <p>Update your email for account recovery</p>
                                </div>
                                <button id="change-email-btn" class="settings-btn">Edit</button>
                            </div>
                            
                            <div class="settings-item danger">
                                <div class="settings-item-info">
                                    <h4>Delete Account</h4>
                                    <p>Permanently delete your account and all data</p>
                                </div>
                                <button id="delete-account-btn" class="settings-btn danger-btn">Delete</button>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-appearance" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>Appearance Settings</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Theme</h4>
                                    <p>Choose your preferred color theme</p>
                                </div>
                                <select id="theme-select" class="settings-select">
                                    <option value="light">Light</option>
                                    <option value="dark">Dark</option>
                                    <option value="auto">Auto (System)</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Font Size</h4>
                                    <p>Adjust the text size for better readability</p>
                                </div>
                                <select id="font-size-select" class="settings-select">
                                    <option value="small">Small</option>
                                    <option value="medium" selected>Medium</option>
                                    <option value="large">Large</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Message Bubbles</h4>
                                    <p>Choose message bubble style</p>
                                </div>
                                <select id="bubble-style-select" class="settings-select">
                                    <option value="rounded">Rounded</option>
                                    <option value="square">Square</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Show Timestamps</h4>
                                    <p>Display timestamps on messages</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="show-timestamps" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-notifications" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>Notification Settings</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Desktop Notifications</h4>
                                    <p>Receive notifications when you're not in the app</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="desktop-notifications">
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Sound Notifications</h4>
                                    <p>Play sound when receiving new messages</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="sound-notifications" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Message Preview</h4>
                                    <p>Show message content in notifications</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="message-preview" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Mute All Notifications</h4>
                                    <p>Temporarily disable all notifications</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="mute-all">
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-privacy" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>Privacy Settings</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Accept Temporary Chat</h4>
                                    <p>Allow non-friends to send you temporary chat messages</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="accept-temporary-chat" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Online Status</h4>
                                    <p>Show when you're online to other users</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="show-online-status" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Read Receipts</h4>
                                    <p>Let others know when you've read their messages</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="read-receipts" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Profile Visibility</h4>
                                    <p>Control who can see your profile</p>
                                </div>
                                <select id="profile-visibility" class="settings-select">
                                    <option value="everyone">Everyone</option>
                                    <option value="contacts">Contacts Only</option>
                                    <option value="private">Private</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>NSFW Content</h4>
                                    <p>Allow viewing NSFW content</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="allow-nsfw">
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                        </div>
                    </div>
                    
                    <div id="tab-storage" class="settings-tab-content">
                        <div class="settings-section">
                            <h3>Storage Settings</h3>
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Message Storage</h4>
                                    <p>Store messages locally for offline access</p>
                                </div>
                                <label class="toggle-switch">
                                    <input type="checkbox" id="message-storage" checked>
                                    <span class="toggle-slider"></span>
                                </label>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Storage Type</h4>
                                    <p>Choose storage method for messages</p>
                                </div>
                                <select id="storage-type" class="settings-select">
                                    <option value="indexeddb">IndexedDB (Recommended)</option>
                                    <option value="localstorage">localStorage</option>
                                </select>
                            </div>
                            
                            <div class="settings-item">
                                <div class="settings-item-info">
                                    <h4>Max Messages per Room</h4>
                                    <p>Limit the number of messages stored per room</p>
                                </div>
                                <select id="max-messages" class="settings-select">
                                    <option value="100">100</option>
                                    <option value="200" selected>200</option>
                                    <option value="500">500</option>
                                    <option value="1000">1000</option>
                                </select>
                            </div>
                            
                            <div class="settings-item danger">
                                <div class="settings-item-info">
                                    <h4>Clear All Data</h4>
                                    <p>Delete all locally stored messages and settings</p>
                                </div>
                                <button id="clear-data-btn" class="settings-btn danger-btn">Clear</button>
                            </div>
                        </div>
                    </div>
                </div>
                
                <div class="settings-footer">
                    <button id="save-settings-btn" class="action-btn primary">Save Settings</button>
                    <button id="reset-settings-btn" class="action-btn secondary">Reset to Default</button>
                </div>
            </div>
        </div>
    </div>
    
    <script src="js/chat.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            initUserSettings();
        });
    </script>
</body>
</html>
