# TVBoxHook - LSPosed 模块

## 项目概述

TVBoxHook 是一个 LSPosed/Xposed 模块，用于 Hook TVBox 应用 (com.ysc.tvbox) 并捕获解密过程。

## 功能特性

### 1. SO 加载监控
- 监控 `ftyguard_v7.so` / `ftyguard_v8.so` 加载
- 记录加载时间和路径

### 2. 文件操作监控
- 监控 `ftyshinidie.guard` 文件读取
- 捕获解密后的 ZIP/DEX 数据
- 监控临时文件写入

### 3. 加密操作监控
- 监控 `javax.crypto.Cipher` 操作
- 捕获加密/解密数据
- 自动识别 ZIP/DEX 格式

### 4. SharedPreferences 分析
- 自动分析 `Hawk2.xml`
- 提取加密配置
- 保存到单独文件

## 项目结构

```
TVBoxHook/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/tvboxhook/
│   │       │   ├── TVBoxHook.java      # 主 Hook 代码
│   │       │   └── MainActivity.java   # 主界面
│   │       ├── assets/
│   │       │   └── xposed_init         # Xposed 入口
│   │       ├── res/
│   │       │   └── xml/
│   │       │       └── file_paths.xml  # 文件路径配置
│   │       └── AndroidManifest.xml     # 清单文件
│   └── build.gradle                     # 构建配置
├── build.gradle                         # 项目构建配置
├── settings.gradle                      # 项目设置
├── build_apk.bat                        # Windows 构建脚本
├── quick_build.py                       # Python 构建脚本
├── direct_hook.sh                       # 直接 Hook 脚本
├── INSTALL.md                           # 安装指南
└── README.md                            # 本文件
```

## 安装方法

### 方法 1: 使用预构建 APK

1. 下载 `TVBoxHook-signed.apk`
2. 安装 APK: `adb install TVBoxHook-signed.apk`
3. 在 LSPosed 中启用模块并选择 TVBox 应用
4. 重启 TVBox 应用

### 方法 2: 自行构建

#### 环境要求
- Android Studio 或 Gradle
- Android SDK (API 24+)
- Java 8+

#### 构建步骤

**使用脚本构建:**
```bash
# Windows
build_apk.bat

# 或 Python
python quick_build.py
```

**手动构建:**
```bash
cd TVBoxHook
gradle clean assembleRelease
```

**签名并安装:**
```bash
# 生成密钥
keytool -genkey -v -keystore tvboxhook.keystore -alias tvboxhook -keyalg RSA -validity 10000

# 签名
apksigner sign --ks tvboxhook.keystore --out TVBoxHook-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk

# 安装
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

## 捕获的数据

### 文件位置
所有数据保存在 `/sdcard/TVBoxHook/` 目录:

- `hook.log` - 操作日志
- `decrypted_zip_*.bin` - 解密后的 ZIP 数据
- `decrypted_dex_*.dex` - 解密后的 DEX 数据
- `hawk_*.txt` - Hawk 配置数据
- `strace.log` - 系统调用日志 (如果使用 direct_hook.sh)
- `maps.txt` - 内存映射

### 数据格式

**解密数据识别:**
- ZIP 格式: 以 `PK\x03\x04` 开头
- DEX 格式: 以 `dex\n` 开头

**Hawk 数据格式:**
```
type##version@base64_encoded_data
```

## 技术细节

### 目标应用
- 包名: `com.ysc.tvbox`
- 主 Activity: `com.github.tvbox.osc.ui.activity.HomeActivity`

### 监控的文件
- `ftyshinidie.guard` - 加密数据文件 (744KB)
- `ftyguard_v7.so` - ARM 32-bit 加密库
- `ftyguard_v8.so` - ARM 64-bit 加密库
- `Hawk2.xml` - 加密配置

### Hook 点
1. `System.loadLibrary()` - SO 加载
2. `FileInputStream` - 文件读取
3. `FileOutputStream` - 文件写入
4. `Cipher.doFinal()` - 加密/解密
5. `Application.attach()` - 应用启动

## 故障排除

### 模块未生效
1. 确认 LSPosed 框架正常运行
2. 确认模块已启用并勾选 TVBox
3. 重启 TVBox 应用

### 未捕获到数据
1. 确保 TVBox 应用正常运行
2. 操作 TVBox 应用触发解密 (浏览频道)
3. 检查 `/sdcard/TVBoxHook/` 目录权限
4. 检查 SELinux 状态: `getenforce`

### 构建失败
1. 检查 Android SDK 环境变量
2. 检查 Gradle 是否安装
3. 检查 Java 版本 (需要 Java 8+)

## 替代方案

如果 LSPosed 不可用，可以使用 `direct_hook.sh`:

```bash
# 推送到设备
adb push direct_hook.sh /data/local/tmp/
adb shell chmod +x /data/local/tmp/direct_hook.sh

# 运行
adb shell su -c /data/local/tmp/direct_hook.sh
```

## 安全提示

⚠️ **警告**: 此模块仅用于学习和研究目的。请勿用于非法用途。

## 更新日志

### v1.0 (2026-04-12)
- 初始版本
- 支持 SO 加载监控
- 支持文件操作监控
- 支持加密操作监控
- 支持 Hawk 数据分析

## 许可证

MIT License

## 作者

逆向工程研究

---

**注意**: 使用本模块需要 root 权限和 LSPosed 框架。
