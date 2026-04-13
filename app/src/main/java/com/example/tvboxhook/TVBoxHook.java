package com.example.tvboxhook;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TVBoxHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.ysc.tvbox";
    private static String LOG_DIR = null;
    private static volatile boolean isLogDirReady = false;
    private static volatile boolean isInitialized = false;
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }
        
        // 防止重复初始化
        if (isInitialized) {
            XposedBridge.log("[TVBoxHook] 已经初始化，跳过");
            return;
        }
        
        XposedBridge.log("[TVBoxHook] ================================");
        XposedBridge.log("[TVBoxHook] 目标应用已加载: " + lpparam.packageName);
        XposedBridge.log("[TVBoxHook] ================================");
        
        hookApplication(lpparam);
    }
    
    private void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // 防止重复执行
                    if (isInitialized) {
                        return;
                    }
                    isInitialized = true;
                    
                    final Context context = (Context) param.args[0];
                    
                    XposedBridge.log("[TVBoxHook] Application attached");
                    
                    // 初始化日志目录
                    initLogDir(context);
                    
                    if (LOG_DIR == null) {
                        XposedBridge.log("[TVBoxHook] 无法获取日志目录!");
                        return;
                    }
                    
                    createLogDir();
                    
                    log("应用已加载: " + context.getPackageName());
                    log("日志目录: " + LOG_DIR);
                    
                    // 初始化 SO Dump Hook（立即执行）
                    try {
                        SODumpHook.init(lpparam, context, LOG_DIR);
                        log("SO Dump Hook 初始化完成");
                    } catch (Exception e) {
                        log("SO Dump Hook 初始化失败: " + e.getMessage());
                    }
                    
                    // 延迟初始化 Hawk Hook
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            // 先初始化 Key Hook（捕获密钥）
                            HawkKeyHook.init(lpparam, context, LOG_DIR);
                            log("Hawk Key Hook 初始化完成");
                            
                            // 再初始化 Decrypt Hook
                            HawkDecryptHook.init(lpparam, context, LOG_DIR);
                            log("Hawk Decrypt Hook 初始化完成");
                        } catch (Exception e) {
                            log("Hawk Hook 初始化失败: " + e.getMessage());
                        }
                    }, 3000);
                    
                    // 初始化 Guard 解密 Hook
                    try {
                        GuardDecryptHook.init(lpparam, context, LOG_DIR);
                        log("Guard Decrypt Hook 初始化完成");
                    } catch (Exception e) {
                        log("Guard Decrypt Hook 初始化失败: " + e.getMessage());
                    }
                    
                    // 初始化 Guard 文件 Hook（专门捕获 guard 文件解密）
                    try {
                        GuardFileHook.init(lpparam, context, LOG_DIR);
                        log("Guard File Hook 初始化完成");
                    } catch (Exception e) {
                        log("Guard File Hook 初始化失败: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[TVBoxHook] Hook Application 失败: " + e.getMessage());
        }
    }
    
    private void initLogDir(Context context) {
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                LOG_DIR = externalDir.getAbsolutePath() + "/TVBoxHook";
                return;
            }
        } catch (Exception e) {
        }
        
        try {
            File filesDir = context.getFilesDir();
            if (filesDir != null) {
                LOG_DIR = filesDir.getAbsolutePath() + "/TVBoxHook";
                return;
            }
        } catch (Exception e) {
        }
        
        LOG_DIR = "/data/local/tmp/TVBoxHook";
    }
    
    private void createLogDir() {
        if (LOG_DIR == null) return;
        
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            isLogDirReady = dir.exists();
        } catch (Exception e) {
            isLogDirReady = false;
        }
    }
    
    private void log(String message) {
        String logMsg = "[" + System.currentTimeMillis() + "] " + message;
        XposedBridge.log("[TVBoxHook] " + logMsg);
        
        if (isLogDirReady && LOG_DIR != null) {
            try {
                File logFile = new File(LOG_DIR, "hook.log");
                FileOutputStream fos = new FileOutputStream(logFile, true);
                fos.write((logMsg + "\n").getBytes());
                fos.close();
            } catch (IOException e) {
            }
        }
    }
}
