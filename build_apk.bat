@echo off
chcp 65001
echo ========================================
echo TVBoxHook LSPosed 模块构建脚本
echo ========================================
echo.

REM 检查 Android SDK
if not defined ANDROID_SDK_ROOT (
    if not defined ANDROID_HOME (
        echo [错误] 未找到 Android SDK
        echo 请设置 ANDROID_SDK_ROOT 或 ANDROID_HOME 环境变量
        exit /b 1
    ) else (
        set "ANDROID_SDK=%ANDROID_HOME%"
    )
) else (
    set "ANDROID_SDK=%ANDROID_SDK_ROOT%"
)

echo [+] Android SDK: %ANDROID_SDK%

REM 检查 gradle
where gradle >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未找到 Gradle
    echo 请安装 Gradle 或检查环境变量
    exit /b 1
)

echo [+] 开始构建...
cd /d "%~dp0"

REM 清理并构建
call gradle clean assembleRelease

if %errorlevel% neq 0 (
    echo [错误] 构建失败
    exit /b 1
)

echo.
echo ========================================
echo [+] 构建成功!
echo [+] APK 位置: app\build\outputs\apk\release\app-release-unsigned.apk
echo ========================================
echo.
echo 下一步:
echo 1. 签名 APK (如果需要)
echo 2. 安装到设备: adb install app\build\outputs\apk\release\app-release-unsigned.apk
echo 3. 在 LSPosed 中启用模块
echo 4. 重启 TVBox 应用

pause
