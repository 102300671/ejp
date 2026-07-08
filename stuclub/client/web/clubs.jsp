<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
    <title>社团列表 - 学生社团管理系统</title>
    <link rel="stylesheet" href="css/style.css">
    <style>
        .club-container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        .page-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 30px;
        }
        .page-header h1 {
            margin: 0;
            color: #333;
        }
        .search-box {
            margin-bottom: 20px;
        }
        .search-box input {
            padding: 10px 15px;
            border: 1px solid #ddd;
            border-radius: 4px;
            width: 300px;
            font-size: 14px;
        }
        .search-box button {
            padding: 10px 20px;
            background: #007bff;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            margin-left: 10px;
        }
        .category-tabs {
            margin-bottom: 20px;
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }
        .category-tab {
            padding: 8px 16px;
            background: #f0f0f0;
            border: none;
            border-radius: 20px;
            cursor: pointer;
            transition: all 0.3s;
        }
        .category-tab:hover, .category-tab.active {
            background: #007bff;
            color: white;
        }
        .club-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
        }
        .club-card {
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            padding: 20px;
            transition: transform 0.3s;
        }
        .club-card:hover {
            transform: translateY(-5px);
        }
        .club-logo {
            width: 80px;
            height: 80px;
            border-radius: 50%;
            background: #007bff;
            color: white;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 32px;
            margin-bottom: 15px;
        }
        .club-name {
            font-size: 18px;
            font-weight: bold;
            margin-bottom: 10px;
            color: #333;
        }
        .club-category {
            display: inline-block;
            padding: 4px 12px;
            background: #e9ecef;
            border-radius: 12px;
            font-size: 12px;
            color: #666;
            margin-bottom: 10px;
        }
        .club-description {
            color: #666;
            font-size: 14px;
            margin-bottom: 15px;
            line-height: 1.5;
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .club-info {
            font-size: 12px;
            color: #999;
            margin-bottom: 15px;
        }
        .club-info span {
            margin-right: 15px;
        }
        .club-actions {
            display: flex;
            gap: 10px;
        }
        .club-actions button {
            flex: 1;
            padding: 10px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
        }
        .btn-join {
            background: #007bff;
            color: white;
        }
        .btn-view {
            background: #6c757d;
            color: white;
        }
        .btn-apply {
            background: #28a745;
            color: white;
        }
        .btn-pending {
            background: #ffc107;
            color: #333;
        }
        .empty-state {
            text-align: center;
            padding: 50px;
            color: #999;
        }
        .my-clubs-section {
            margin-bottom: 40px;
            padding: 20px;
            background: #f8f9fa;
            border-radius: 8px;
        }
        .my-clubs-section h2 {
            margin-top: 0;
            margin-bottom: 20px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="chat-box">
            <div class="chat-header">
                <h2>学生社团管理系统</h2>
                <div class="user-info">
                    <span id="current-user"></span>
                    <button id="my-clubs-btn" onclick="showMyClubs()">我的社团</button>
                    <button id="activities-btn" onclick="showActivities()">活动中心</button>
                    <button id="logout-btn" onclick="logout()">退出登录</button>
                </div>
            </div>
            
            <div class="chat-content">
                <div class="club-container">
                    <!-- 我的社团 -->
                    <div id="my-clubs-section" class="my-clubs-section" style="display: none;">
                        <h2>我的社团</h2>
                        <div id="my-clubs-list" class="club-grid"></div>
                    </div>
                    
                    <!-- 所有社团 -->
                    <div id="all-clubs-section">
                        <div class="page-header">
                            <h1>社团列表</h1>
                            <button id="create-club-btn" class="btn-apply" onclick="showCreateClub()" style="display: none;">
                                创建社团
                            </button>
                        </div>
                        
                        <div class="search-box">
                            <input type="text" id="search-input" placeholder="搜索社团名称、类别...">
                            <button onclick="searchClubs()">搜索</button>
                        </div>
                        
                        <div class="category-tabs" id="category-tabs">
                            <button class="category-tab active" onclick="filterByCategory('')">全部</button>
                            <button class="category-tab" onclick="filterByCategory('学术科技')">学术科技</button>
                            <button class="category-tab" onclick="filterByCategory('文化艺术')">文化艺术</button>
                            <button class="category-tab" onclick="filterByCategory('体育健身')">体育健身</button>
                            <button class="category-tab" onclick="filterByCategory('志愿服务')">志愿服务</button>
                            <button class="category-tab" onclick="filterByCategory('其他')">其他</button>
                        </div>
                        
                        <div id="club-grid" class="club-grid"></div>
                        <div id="empty-state" class="empty-state" style="display: none;">
                            <p>暂无社团</p>
                        </div>
                    </div>
                    
                    <!-- 活动列表 -->
                    <div id="activities-section" style="display: none;">
                        <div class="page-header">
                            <h1>活动中心</h1>
                            <button onclick="showClubs()">返回社团列表</button>
                        </div>
                        <div id="activities-list" class="club-grid"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    
    <!-- 创建社团弹窗 -->
    <div id="create-club-modal" class="modal" style="display: none;">
        <div class="modal-content">
            <span class="close" onclick="closeCreateClubModal()">&times;</span>
            <h2>创建社团</h2>
            <form id="create-club-form">
                <div class="form-group">
                    <label>社团名称 *</label>
                    <input type="text" name="name" required>
                </div>
                <div class="form-group">
                    <label>社团类别 *</label>
                    <select name="category" required>
                        <option value="">请选择类别</option>
                        <option value="学术科技">学术科技</option>
                        <option value="文化艺术">文化艺术</option>
                        <option value="体育健身">体育健身</option>
                        <option value="志愿服务">志愿服务</option>
                        <option value="其他">其他</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>社团简介 *</label>
                    <textarea name="description" rows="4" required></textarea>
                </div>
                <div class="form-group">
                    <label>指导老师</label>
                    <input type="text" name="advisor">
                </div>
                <div class="form-actions">
                    <button type="submit" class="btn-apply">提交申请</button>
                    <button type="button" onclick="closeCreateClubModal()">取消</button>
                </div>
            </form>
        </div>
    </div>
    
    <!-- 社团详情弹窗 -->
    <div id="club-detail-modal" class="modal" style="display: none;">
        <div class="modal-content" style="max-width: 600px;">
            <span class="close" onclick="closeDetailModal()">&times;</span>
            <div id="club-detail-content"></div>
        </div>
    </div>
    
    <script>
        const API_BASE = 'http://localhost:8086';
        let currentUser = null;
        let clubs = [];
        let myClubs = [];
        let activities = [];
        let currentCategory = '';
        
        // 初始化
        document.addEventListener('DOMContentLoaded', function() {
            loadCurrentUser();
            loadClubs();
        });
        
        function loadCurrentUser() {
            const username = sessionStorage.getItem('username');
            const uuid = sessionStorage.getItem('uuid');
            const role = sessionStorage.getItem('role');
            
            if (username) {
                currentUser = {
                    username: username,
                    uuid: uuid,
                    role: role || 'USER'
                };
                document.getElementById('current-user').textContent = username;
                
                if (role === 'ADMIN' || role === 'SUPER_ADMIN') {
                    document.getElementById('create-club-btn').style.display = 'block';
                }
            }
        }
        
        function loadClubs() {
            fetch(API_BASE + '/clubs/api')
                .then(response => response.json())
                .then(data => {
                    clubs = data.map(club => ({
                        id: club.id,
                        name: club.name,
                        category: club.category,
                        description: club.description || '',
                        advisor: club.advisor || '',
                        memberCount: club.member_count || 0,
                        status: club.status || 'APPROVED'
                    }));
                    renderClubs();
                })
                .catch(error => {
                    console.error('加载社团列表失败:', error);
                    clubs = [];
                    renderClubs();
                });
        }
        
        function renderClubs() {
            const grid = document.getElementById('club-grid');
            const emptyState = document.getElementById('empty-state');
            
            let filteredClubs = clubs;
            if (currentCategory) {
                filteredClubs = clubs.filter(c => c.category === currentCategory);
            }
            
            if (filteredClubs.length === 0) {
                grid.style.display = 'none';
                emptyState.style.display = 'block';
                return;
            }
            
            grid.style.display = 'grid';
            emptyState.style.display = 'none';
            
            grid.innerHTML = filteredClubs.map(club => {
                const initial = club.name.charAt(0);
                const isMember = myClubs.some(m => m.id === club.id);
                const hasPending = false; // 检查是否有待处理的申请
                
                let actionBtn = '';
                if (isMember) {
                    actionBtn = `<button class="btn-view" onclick="viewClub(${club.id})">进入社团</button>`;
                } else if (hasPending) {
                    actionBtn = `<button class="btn-pending" disabled>申请中</button>`;
                } else {
                    actionBtn = `<button class="btn-apply" onclick="applyJoin(${club.id})">申请加入</button>`;
                }
                
                return `
                    <div class="club-card">
                        <div class="club-logo">${initial}</div>
                        <div class="club-name">${club.name}</div>
                        <div class="club-category">${club.category}</div>
                        <div class="club-description">${club.description}</div>
                        <div class="club-info">
                            <span>👥 ${club.memberCount}人</span>
                            <span>👤 ${club.advisor || '待定'}</span>
                        </div>
                        <div class="club-actions">
                            <button class="btn-view" onclick="viewClub(${club.id})">查看详情</button>
                            ${actionBtn}
                        </div>
                    </div>
                `;
            }).join('');
        }
        
        function filterByCategory(category) {
            currentCategory = category;
            
            // 更新标签样式
            document.querySelectorAll('.category-tab').forEach(tab => {
                tab.classList.remove('active');
                if (tab.textContent === (category || '全部')) {
                    tab.classList.add('active');
                }
            });
            
            renderClubs();
        }
        
        function searchClubs() {
            const keyword = document.getElementById('search-input').value.trim();
            if (!keyword) {
                loadClubs();
                return;
            }
            
            fetch(API_BASE + '/clubs/api/search?term=' + encodeURIComponent(keyword))
                .then(response => response.json())
                .then(data => {
                    const searchResults = data.map(club => ({
                        id: club.id,
                        name: club.name,
                        category: club.category,
                        description: club.description || '',
                        advisor: club.advisor || '',
                        memberCount: club.member_count || 0,
                        status: club.status || 'APPROVED'
                    }));
                    
                    const grid = document.getElementById('club-grid');
                    const emptyState = document.getElementById('empty-state');
                    
                    if (searchResults.length === 0) {
                        grid.style.display = 'none';
                        emptyState.style.display = 'block';
                        return;
                    }
                    
                    grid.style.display = 'grid';
                    emptyState.style.display = 'none';
                    
                    grid.innerHTML = searchResults.map(club => {
                        const initial = club.name.charAt(0);
                        return `
                            <div class="club-card">
                                <div class="club-logo">${initial}</div>
                                <div class="club-name">${club.name}</div>
                                <div class="club-category">${club.category}</div>
                                <div class="club-description">${club.description}</div>
                                <div class="club-info">
                                    <span>👥 ${club.memberCount}人</span>
                                    <span>👤 ${club.advisor || '待定'}</span>
                                </div>
                                <div class="club-actions">
                                    <button class="btn-view" onclick="viewClub(${club.id})">查看详情</button>
                                    <button class="btn-apply" onclick="applyJoin(${club.id})">申请加入</button>
                                </div>
                            </div>
                        `;
                    }).join('');
                })
                .catch(error => {
                    console.error('搜索失败:', error);
                    alert('搜索失败，请重试');
                });
        }
        
        function showMyClubs() {
            document.getElementById('my-clubs-section').style.display = 'block';
            document.getElementById('all-clubs-section').style.display = 'none';
            document.getElementById('activities-section').style.display = 'none';
            
            renderMyClubs();
        }
        
        function renderMyClubs() {
            const list = document.getElementById('my-clubs-list');
            if (myClubs.length === 0) {
                list.innerHTML = '<p style="color: #999; text-align: center;">您还没有加入任何社团</p>';
                return;
            }
            
            list.innerHTML = myClubs.map(club => {
                const initial = club.name.charAt(0);
                return `
                    <div class="club-card">
                        <div class="club-logo">${initial}</div>
                        <div class="club-name">${club.name}</div>
                        <div class="club-category">${club.category}</div>
                        <div class="club-description">${club.description}</div>
                        <div class="club-actions">
                            <button class="btn-join" onclick="enterClub(${club.id})">进入社团</button>
                            <button class="btn-view" onclick="viewClub(${club.id})">查看详情</button>
                        </div>
                    </div>
                `;
            }).join('');
        }
        
        function showClubs() {
            document.getElementById('my-clubs-section').style.display = 'none';
            document.getElementById('all-clubs-section').style.display = 'block';
            document.getElementById('activities-section').style.display = 'none';
        }
        
        function showActivities() {
            document.getElementById('my-clubs-section').style.display = 'none';
            document.getElementById('all-clubs-section').style.display = 'none';
            document.getElementById('activities-section').style.display = 'block';
            renderActivities();
        }
        
        function renderActivities() {
            // 模拟活动数据
            activities = [
                {
                    id: 1,
                    title: 'Python编程入门培训',
                    clubName: '计算机协会',
                    startTime: '2026-06-20 14:00',
                    location: '教学楼A101',
                    status: 'PUBLISHED',
                    maxParticipants: 50,
                    currentParticipants: 32
                },
                {
                    id: 2,
                    title: '校园歌手大赛',
                    clubName: '音乐社',
                    startTime: '2026-06-25 18:00',
                    location: '大学生活动中心',
                    status: 'PUBLISHED',
                    maxParticipants: 100,
                    currentParticipants: 78
                }
            ];
            
            const list = document.getElementById('activities-list');
            list.innerHTML = activities.map(activity => `
                <div class="club-card">
                    <div class="club-name">${activity.title}</div>
                    <div class="club-category">${activity.clubName}</div>
                    <div class="club-info">
                        <span>📅 ${activity.startTime}</span>
                        <span>📍 ${activity.location}</span>
                    </div>
                    <div class="club-info">
                        <span>👥 ${activity.currentParticipants}/${activity.maxParticipants}</span>
                    </div>
                    <div class="club-actions">
                        <button class="btn-view" onclick="viewActivity(${activity.id})">查看详情</button>
                        <button class="btn-apply" onclick="registerActivity(${activity.id})">立即报名</button>
                    </div>
                </div>
            `).join('');
        }
        
        function showCreateClub() {
            document.getElementById('create-club-modal').style.display = 'block';
        }
        
        function closeCreateClubModal() {
            document.getElementById('create-club-modal').style.display = 'none';
        }
        
        function viewClub(id) {
            const club = clubs.find(c => c.id === id);
            if (!club) return;
            
            document.getElementById('club-detail-content').innerHTML = `
                <h2>${club.name}</h2>
                <div class="club-category">${club.category}</div>
                <div style="margin: 20px 0;">
                    <h4>社团简介</h4>
                    <p>${club.description}</p>
                </div>
                <div class="club-info">
                    <p><strong>指导老师：</strong>${club.advisor || '待定'}</p>
                    <p><strong>成员数量：</strong>${club.memberCount}人</p>
                    <p><strong>社团状态：</strong>${club.status === 'APPROVED' ? '已认证' : '审核中'}</p>
                </div>
                <div class="club-actions" style="margin-top: 20px;">
                    <button class="btn-apply" onclick="applyJoin(${club.id})">申请加入</button>
                    <button class="btn-view" onclick="closeDetailModal()">关闭</button>
                </div>
            `;
            document.getElementById('club-detail-modal').style.display = 'block';
        }
        
        function closeDetailModal() {
            document.getElementById('club-detail-modal').style.display = 'none';
        }
        
        function applyJoin(clubId) {
            const reason = prompt('请输入申请理由：');
            if (reason && currentUser) {
                fetch(API_BASE + '/clubs/api/' + clubId + '/apply', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        username: currentUser.username,
                        reason: reason
                    })
                })
                .then(response => response.text())
                .then(message => {
                    alert(message);
                    closeDetailModal();
                })
                .catch(error => {
                    console.error('提交申请失败:', error);
                    alert('提交申请失败，请重试');
                });
            } else if (!currentUser) {
                alert('请先登录！');
                window.location.href = 'login.jsp';
            }
        }
        
        function viewActivity(id) {
            alert('活动详情页面开发中...');
        }
        
        function registerActivity(id) {
            alert('报名功能开发中...');
        }
        
        function enterClub(id) {
            window.location.href = `club-chat.jsp?club=${id}`;
        }
        
        function logout() {
            localStorage.clear();
            window.location.href = 'login.jsp';
        }
        
        // 表单提交
        document.getElementById('create-club-form').addEventListener('submit', function(e) {
            e.preventDefault();
            const formData = new FormData(this);
            const data = Object.fromEntries(formData);
            
            console.log('创建社团:', data);
            alert('社团创建申请已提交！');
            closeCreateClubModal();
        });
    </script>
</body>
</html>
