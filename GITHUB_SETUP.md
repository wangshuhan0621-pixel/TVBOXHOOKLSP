# GitHub 自动构建设置指南 (自动签名版)

## 概述

本项目配置了 GitHub Actions 自动构建，**无需手动配置签名密钥**！每次推送代码到 GitHub 时会自动构建、签名 APK 并发布。

## 设置步骤

### 1. 创建 GitHub 仓库

1. 访问 https://github.com/new
2. 输入仓库名称: `TVBoxHook`
3. 选择 "Public" 或 "Private"
4. 点击 "Create repository"

### 2. 上传代码到 GitHub

```bash
# 在项目目录中执行
cd TVBoxHook

# 初始化 git
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit"

# 添加远程仓库 (替换为你的仓库地址)
git remote add origin https://github.com/你的用户名/TVBoxHook.git

# 推送
git push -u origin main
```

### 3. 完成！🎉

**不需要配置任何 Secrets！** GitHub Actions 会自动:
1. ✅ 生成签名密钥
2. ✅ 构建 APK
3. ✅ 签名 APK
4. ✅ 创建 Release

### 4. 下载 APK

#### 从 Actions 下载
1. 打开 GitHub 仓库页面
2. 点击 "Actions" 标签
3. 选择最新的工作流运行
4. 在 "Artifacts" 部分下载 `TVBoxHook-signed`

#### 从 Releases 下载 (推荐)
- 每次推送到 main 分支会自动创建 Release
- 访问 `https://github.com/你的用户名/TVBoxHook/releases`
- 下载最新版本的 APK

## 工作流说明

### 构建流程

1. **检出代码** - 从仓库获取最新代码
2. **设置环境** - 配置 JDK 11 和 Android SDK
3. **缓存依赖** - 缓存 Gradle 依赖加快构建
4. **构建 APK** - 使用 Gradle 构建 Release APK
5. **生成密钥** - 自动生成签名密钥 (无需配置)
6. **签名 APK** - 使用自动生成的密钥签名
7. **验证签名** - 验证 APK 签名成功
8. **上传产物** - 上传签名后的 APK
9. **创建 Release** - 自动创建 GitHub Release

### 构建配置

工作流文件: `.github/workflows/build.yml`

主要配置:
- **Java 版本**: 11
- **Gradle 版本**: 8.0
- **构建类型**: Release
- **签名**: 自动生成密钥

## 触发构建

### 自动触发
- 每次推送到 `main` 或 `master` 分支会自动触发构建
- 每次 Pull Request 也会触发构建

### 手动触发
1. 打开 GitHub 仓库页面
2. 点击 "Actions" 标签
3. 选择 "Build TVBoxHook APK" 工作流
4. 点击 "Run workflow"

## 更新代码

每次更新代码后，推送到 GitHub 会自动触发构建:

```bash
# 修改代码后
git add .
git commit -m "更新说明"
git push origin main
```

然后等待 GitHub Actions 完成构建，下载最新的 APK。

## 故障排除

### 构建失败

1. **检查代码**
   - 确保没有语法错误
   - 检查 `build.gradle` 配置

2. **查看日志**
   - 在 Actions 页面查看详细日志
   - 查找错误信息

### 无法下载 APK

1. **检查 Artifacts**
   - 确保构建成功
   - 检查 Artifacts 是否上传

2. **检查 Releases**
   - 确认 Release 已创建
   - 检查 Release 中的附件

## 项目结构

```
TVBoxHook/
├── .github/
│   └── workflows/
│       └── build.yml          # GitHub Actions 配置 (自动签名)
├── app/
│   └── src/
│       └── main/
│           ├── java/          # Java 源代码
│           ├── assets/        # Xposed 入口
│           └── res/           # 资源文件
├── gradle/
│   └── wrapper/               # Gradle wrapper
├── build_apk.bat              # Windows 本地构建脚本
├── quick_build.py             # Python 本地构建脚本
├── direct_hook.sh             # 直接 Hook 脚本
├── README.md                  # 项目文档
├── INSTALL.md                 # 安装指南
└── GITHUB_SETUP.md            # 本文件
```

## 本地构建 (可选)

如果你想在本地构建，可以使用以下方法:

### 使用脚本
```bash
# Windows
build_apk.bat

# Python
python quick_build.py
```

### 手动构建
```bash
./gradlew assembleRelease
```

## 参考链接

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Android 构建指南](https://developer.android.com/studio/build)
- [LSPosed 开发文档](https://github.com/LSPosed/LSPosed/wiki)

---

**注意**: 
- 首次构建可能需要 5-10 分钟
- 每次构建都会生成新的签名密钥
- 构建完成后会自动发布 Release

**无需配置任何 Secrets，开箱即用！** 🚀
