// 元宵主题特效

// 创建彩色粒子效果
function createLanternParticles() {
    const particleCount = 50;
    
    for (let i = 0; i < particleCount; i++) {
        createParticle();
    }
    
    // 定期创建新粒子
    setInterval(createParticle, 2000);
}

function createParticle() {
    const particle = document.createElement('div');
    particle.className = 'lantern-particle';
    
    // 随机大小
    const size = Math.random() * 10 + 5;
    particle.style.width = size + 'px';
    particle.style.height = size + 'px';
    
    // 随机位置
    particle.style.left = Math.random() * 100 + '%';
    particle.style.bottom = '-20px';
    
    // 随机颜色
    const colorClass = 'color' + (Math.floor(Math.random() * 3) + 1);
    particle.classList.add(colorClass);
    
    // 随机动画持续时间
    const duration = Math.random() * 15 + 10;
    particle.style.animationDuration = duration + 's';
    
    // 随机延迟
    particle.style.animationDelay = Math.random() * 5 + 's';
    
    document.body.appendChild(particle);
    
    // 动画结束后移除粒子
    setTimeout(() => {
        if (particle.parentNode) {
            particle.parentNode.removeChild(particle);
        }
    }, (duration + 5) * 1000);
}

// 切换到元宵主题
function switchToLanternFestivalTheme() {
    // 移除其他主题
    const links = document.querySelectorAll('link[rel="stylesheet"]');
    links.forEach(link => {
        if (link.href.includes('spring-festival.css')) {
            link.remove();
        }
    });
    
    // 添加元宵主题
    const themeLink = document.createElement('link');
    themeLink.rel = 'stylesheet';
    themeLink.href = 'css/lantern-festival.css';
    document.head.appendChild(themeLink);
    
    // 启动特效
    createLanternParticles();
    
    // 存储主题偏好
    localStorage.setItem('theme', 'lantern-festival');
    
    console.log('切换到元宵主题');
}

// 页面加载时检查主题偏好
function checkThemePreference() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'lantern-festival') {
        switchToLanternFestivalTheme();
    }
}

// 导出函数
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        switchToLanternFestivalTheme,
        checkThemePreference
    };
}
