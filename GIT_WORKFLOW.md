# Git 协作工作流

## 分支策略

### 主分支

| 分支名称 | 用途 | 保护状态 |
|----------|------|----------|
| `main` | 生产环境代码，稳定版本 | 受保护 |
| `develop` | 开发整合分支，日常开发 | 受保护 |

### 功能分支

从 `develop` 分支创建，命名规范：

```
feature/功能名称
feature/user-login
feature/javaFX-ui
```

### 修复分支

从 `develop` 或 `main` 分支创建，命名规范：

```
fix/问题描述
fix/user-status-update
```

## 工作流程

### 1. 初始化本地仓库

```bash
git clone https://github.com/102300671/ejp.git
cd ejp
git checkout develop
```

### 2. 创建功能分支

```bash
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name
```

### 3. 开发和提交

```bash
# 开发代码
git add .
git commit -m "feat(模块): 功能描述"
```

### 4. 推送和创建PR

```bash
git push --set-upstream origin feature/your-feature-name
```

在 GitHub 上创建 Pull Request 到 `develop` 分支。

### 5. 代码审查和合并

- 等待团队成员审查
- 通过后合并到 `develop`
- 删除功能分支

### 6. 发布到生产

```bash
# 从 develop 创建发布分支
git checkout develop
git checkout -b release/v1.0.0

# 完成发布准备后合并到 main
git checkout main
git merge --no-ff release/v1.0.0

# 打标签
git tag -a v1.0.0 -m "版本 1.0.0"
git push origin v1.0.0

# 合并回 develop
git checkout develop
git merge --no-ff release/v1.0.0

# 删除发布分支
git branch -d release/v1.0.0
```

## 提交规范

使用 Conventional Commits 规范：

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复bug |
| `docs` | 文档更新 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `test` | 测试 |
| `chore` | 构建/工具 |

示例：

```
feat(javaFX): 实现消息列表本地存储

1. 添加SQLite数据库支持
2. 实现会话状态管理
3. 支持置顶和隐藏功能
```

## 冲突解决

```bash
git pull origin develop
# 解决冲突
git add .
git commit -m "Merge branch 'develop' into feature/your-feature"
git push
```

## 常见命令

```bash
# 查看远程分支
git remote -v

# 查看所有分支
git branch -a

# 更新本地分支列表
git fetch origin

# 删除本地分支
git branch -d feature/your-feature

# 删除远程分支
git push origin --delete feature/your-feature
```

## 注意事项

1. 不要直接提交到 `main` 和 `develop` 分支
2. 每次提交前确保代码编译通过
3. 编写清晰的提交信息
4. 定期同步上游分支
5. 功能完成后及时清理分支