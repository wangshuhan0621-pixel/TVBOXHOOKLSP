# TVBoxHook LSPosed 模块安装指南

## 前置条件

1. **已 root 的安卓设备**
2. **已安装 LSPosed 框架** (Zygisk 或 Riru 版本)
3. **已安装 TVBox 应用** (com.ysc.tvbox)

## 安装步骤

### 方法 1: 使用预构建 APK (推荐)

1. 下载 `TVBoxHook.apk`
2. 安装 APK:
   ```bash
   adb install TVBoxHook.apk
   ```
3. 打开 LSPosed 管理器
4. 找到 "TVBox Hook" 模块
5. 启用模块并勾选 TVBox 应用
6. 重启 TVBox 应用

### 方法 2: 自行构建

#### 环境要求
- Android Studio 或 Gradle
- Android SDK
- Java 8 或更高版本

#### 构建步骤

1. **克隆/下载项目**
   ```bash
   cd TVBoxHook
   ```

2. **构建 APK**
   ```bash
   # Windows
   build_apk.bat
   
   # 或手动构建
   gradle clean assembleRelease
   ```

3. **签名 APK** (如果需要)
   ```bash
   # 生成签名密钥
   keytool -genkey -v -keystore tvboxhook.keystore -alias tvboxhook -keyalg RSA -validity 10000
   
   # 签名 APK
   apksigner sign --ks tvboxhook.keystore --out TVBoxHook-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
   ```

4. **安装 APK**
   ```bash
   adb install TVBoxHook-signed.apk
   ```

## 使用方法

1. **启用模块**
   - 打开 LSPosed 管理器
   - 找到 "TVBox Hook"
   - 启用模块
   - 在作用域中选择 "TVBox"
   - 重启 TVBox 应用

2. **触发解密**
   - 打开 TVBox 应用
   - 浏览频道或加载视频
   - 模块会自动捕获解密过程

3. **查看结果**
   - 日志: `/sdcard/TVBoxHook/hook.log`
   - 解密数据: `/sdcard/TVBoxHook/decrypted_*.bin`
   - Hawk 数据: `/sdcard/TVBoxHook/hawk_*.txt`

## 功能说明

### Hook 的功能

1. **SO 加载监控**
   - 监控 guard SO 加载
   - 记录加载时间和路径

2. **文件操作监控**
   - 监控 ftyshinidie.guard 读取
   - 捕获解密后的 ZIP/DEX 数据
   - 监控临时文件写入

3. **加密操作监控**
   - 监控 javax.crypto.Cipher
   - 捕获加密/解密数据

4. **SharedPreferences 分析**
   - 自动分析 Hawk2.xml
   - 提取加密配置

### 捕获的数据

- **解密后的 guard 文件**: `decrypted_*.bin`
- **Hawk 配置**: `hawk_*.txt`
- **操作日志**: `hook.log`

## 故障排除

### 模块未生效
1. 确认 LSPosed 框架正常运行
2. 确认模块已启用并勾选 TVBox
3. 重启 TVBox 应用

### 未捕获到数据
1. 确保 TVBox 应用正常运行
2. 操作 TVBox 应用触发解密 (浏览频道)
3. 检查 `/sdcard/TVBoxHook/` 目录权限

### 日志为空
1. 检查存储权限是否授予
2. 手动创建 `/sdcard/TVBoxHook/` 目录
3. 检查 SELinux 状态: `getenforce`

## 安全提示

⚠️ **警告**: 此模块仅用于学习和研究目的。请勿用于非法用途。

## 技术细节

### 目标应用
- 包名: `com.ysc.tvbox`
- 主 Activity: `com.github.tvbox.osc.ui.activity.HomeActivity`

### 监控的文件
- `ftyshinidie.guard` - 加密数据文件
- `ftyguard_v7.so` / `ftyguard_v8.so` - 加密库
- `Hawk2.xml` - 加密配置

## 卸载

1. 在 LSPosed 中禁用模块
2. 卸载 TVBoxHook 应用
3. 删除 `/sdcard/TVBoxHook/` 目录

---

**版本**: 1.0
**更新日期**: 2026-04-12
