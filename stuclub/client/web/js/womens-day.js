// 妇女节/女神节主题特效

// 创建花瓣飘落效果
function createFlowerPetals() {
    const petalCount = 40;
    
    for (let i = 0; i < petalCount; i++) {
        createPetal();
    }
    
    // 定期创建新花瓣
    setInterval(createPetal, 1500);
}

function createPetal() {
    const petal = document.createElement('div');
    petal.className = 'flower-petal';
    
    // 随机大小
    const size = Math.random() * 15 + 8;
    petal.style.width = size + 'px';
    petal.style.height = size + 'px';
    
    // 随机位置
    petal.style.left = Math.random() * 100 + '%';
    petal.style.top = '-20px';
    
    // 随机颜色
    const colorClass = ['pink', 'rose', 'lavender'][Math.floor(Math.random() * 3)];
    petal.classList.add(colorClass);
    
    // 随机动画持续时间
    const duration = Math.random() * 12 + 8;
    petal.style.animationDuration = duration + 's';
    
    // 随机延迟
    petal.style.animationDelay = Math.random() * 3 + 's';
    
    // 随机旋转
    petal.style.transform = `rotate(${Math.random() * 360}deg)`;
    
    document.body.appendChild(petal);
    
    // 动画结束后移除花瓣
    setTimeout(() => {
        if (petal.parentNode) {
            petal.parentNode.removeChild(petal);
        }
    }, (duration + 3) * 1000);
}

// 创建爱心装饰
function createHeartDecorations() {
    const heartCount = 8;
    
    for (let i = 0; i < heartCount; i++) {
        setTimeout(() => {
            createHeart();
        }, i * 500);
    }
    
    // 定期创建新爱心
    setInterval(createHeart, 4000);
}

function createHeart() {
    const heart = document.createElement('div');
    heart.className = 'heart-decoration';
    heart.innerHTML = '❤️';
    heart.style.fontSize = (Math.random() * 20 + 15) + 'px';
    heart.style.left = Math.random() * 90 + 5 + '%';
    heart.style.top = Math.random() * 80 + 10 + '%';
    heart.style.opacity = Math.random() * 0.5 + 0.3;
    
    document.body.appendChild(heart);
    
    // 5秒后移除爱心
    setTimeout(() => {
        if (heart.parentNode) {
            heart.parentNode.removeChild(heart);
        }
    }, 5000);
}

// 切换到妇女节主题
function switchToWomensDayTheme() {
    // 移除其他主题
    const links = document.querySelectorAll('link[rel="stylesheet"]');
    links.forEach(link => {
        if (link.href.includes('spring-festival.css') || 
            link.href.includes('lantern-festival.css')) {
            link.remove();
        }
    });
    
    // 添加妇女节主题
    const themeLink = document.createElement('link');
    themeLink.rel = 'stylesheet';
    themeLink.href = 'css/womens-day.css';
    document.head.appendChild(themeLink);
    
    // 启动特效
    createFlowerPetals();
    createHeartDecorations();
    
    // 存储主题偏好
    localStorage.setItem('theme', 'womens-day');
    
    console.log('切换到妇女节主题');
}

// 页面加载时检查主题偏好
function checkThemePreference() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'womens-day') {
        switchToWomensDayTheme();
    }
}

// 导出函数
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        switchToWomensDayTheme,
        checkThemePreference
    };
}
