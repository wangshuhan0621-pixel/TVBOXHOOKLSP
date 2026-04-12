#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速构建 TVBoxHook LSPosed 模块
"""

import os
import subprocess
import sys

def check_android_sdk():
    """检查 Android SDK"""
    sdk_root = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk_root:
        print("[错误] 未找到 Android SDK")
        print("请设置 ANDROID_SDK_ROOT 或 ANDROID_HOME 环境变量")
        return None
    return sdk_root

def check_gradle():
    """检查 Gradle"""
    try:
        result = subprocess.run(['gradle', '--version'], capture_output=True, text=True)
        if result.returncode == 0:
            return True
    except:
        pass
    
    # 检查 gradlew
    if os.path.exists('gradlew'):
        return 'gradlew'
    
    return None

def build_apk():
    """构建 APK"""
    print("=" * 60)
    print("TVBoxHook LSPosed 模块构建")
    print("=" * 60)
    
    # 检查 SDK
    sdk = check_android_sdk()
    if not sdk:
        return False
    print(f"[+] Android SDK: {sdk}")
    
    # 检查 Gradle
    gradle = check_gradle()
    if not gradle:
        print("[错误] 未找到 Gradle")
        print("请安装 Gradle 或下载包含 gradlew 的项目")
        return False
    print(f"[+] Gradle: {gradle}")
    
    # 构建
    print("\n[*] 开始构建...")
    try:
        if gradle == 'gradlew':
            if sys.platform == 'win32':
                cmd = ['gradlew.bat', 'clean', 'assembleRelease']
            else:
                cmd = ['./gradlew', 'clean', 'assembleRelease']
        else:
            cmd = ['gradle', 'clean', 'assembleRelease']
        
        result = subprocess.run(cmd, capture_output=False, text=True)
        
        if result.returncode == 0:
            print("\n" + "=" * 60)
            print("[+] 构建成功!")
            print("=" * 60)
            print("\nAPK 位置:")
            print("  app/build/outputs/apk/release/app-release-unsigned.apk")
            print("\n下一步:")
            print("1. 签名 APK (如果需要)")
            print("2. 安装到设备: adb install app/build/outputs/apk/release/app-release-unsigned.apk")
            print("3. 在 LSPosed 中启用模块")
            print("4. 重启 TVBox 应用")
            return True
        else:
            print("\n[错误] 构建失败")
            return False
            
    except Exception as e:
        print(f"\n[错误] 构建异常: {e}")
        return False

def generate_keystore():
    """生成签名密钥"""
    print("\n[*] 生成签名密钥...")
    try:
        cmd = [
            'keytool', '-genkey', '-v',
            '-keystore', 'tvboxhook.keystore',
            '-alias', 'tvboxhook',
            '-keyalg', 'RSA',
            '-validity', '10000',
            '-storepass', 'tvboxhook',
            '-keypass', 'tvboxhook',
            '-dname', 'CN=TVBoxHook'
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0:
            print("[+] 密钥已生成: tvboxhook.keystore")
            return True
        else:
            print(f"[-] 生成失败: {result.stderr}")
            return False
    except Exception as e:
        print(f"[-] 错误: {e}")
        return False

def sign_apk():
    """签名 APK"""
    print("\n[*] 签名 APK...")
    
    # 检查密钥
    if not os.path.exists('tvboxhook.keystore'):
        print("[!] 未找到密钥，生成新密钥...")
        if not generate_keystore():
            return False
    
    # 查找 apksigner
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    apksigner = os.path.join(sdk, 'build-tools', '33.0.0', 'apksigner')
    if sys.platform == 'win32':
        apksigner += '.bat'
    
    if not os.path.exists(apksigner):
        # 尝试其他版本
        build_tools = os.path.join(sdk, 'build-tools')
        if os.path.exists(build_tools):
            versions = os.listdir(build_tools)
            if versions:
                apksigner = os.path.join(build_tools, versions[0], 'apksigner')
                if sys.platform == 'win32':
                    apksigner += '.bat'
    
    if not os.path.exists(apksigner):
        print("[错误] 未找到 apksigner")
        return False
    
    # 签名
    try:
        cmd = [
            apksigner, 'sign',
            '--ks', 'tvboxhook.keystore',
            '--ks-pass', 'pass:tvboxhook',
            '--key-pass', 'pass:tvboxhook',
            '--out', 'TVBoxHook-signed.apk',
            'app/build/outputs/apk/release/app-release-unsigned.apk'
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode == 0:
            print("[+] 签名成功: TVBoxHook-signed.apk")
            return True
        else:
            print(f"[-] 签名失败: {result.stderr}")
            return False
    except Exception as e:
        print(f"[-] 错误: {e}")
        return False

def install_apk():
    """安装 APK"""
    print("\n[*] 安装 APK...")
    
    apk_file = 'TVBoxHook-signed.apk'
    if not os.path.exists(apk_file):
        apk_file = 'app/build/outputs/apk/release/app-release-unsigned.apk'
    
    if not os.path.exists(apk_file):
        print("[错误] 未找到 APK 文件")
        return False
    
    try:
        result = subprocess.run(['adb', 'install', '-r', apk_file], capture_output=True, text=True)
        if result.returncode == 0:
            print("[+] 安装成功!")
            print("\n请在 LSPosed 中启用模块并选择 TVBox 应用")
            return True
        else:
            print(f"[-] 安装失败: {result.stderr}")
            return False
    except Exception as e:
        print(f"[-] 错误: {e}")
        return False

def main():
    print("\nTVBoxHook 构建工具")
    print("=" * 60)
    print("1. 构建 APK")
    print("2. 签名 APK")
    print("3. 安装 APK")
    print("4. 全部执行")
    print("0. 退出")
    print("=" * 60)
    
    choice = input("\n请选择: ").strip()
    
    if choice == '1':
        build_apk()
    elif choice == '2':
        sign_apk()
    elif choice == '3':
        install_apk()
    elif choice == '4':
        if build_apk():
            if sign_apk():
                install_apk()
    elif choice == '0':
        print("再见!")
    else:
        print("无效选择")

if __name__ == '__main__':
    main()
