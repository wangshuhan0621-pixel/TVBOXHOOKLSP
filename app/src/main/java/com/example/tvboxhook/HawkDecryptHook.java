package com.example.tvboxhook;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 简化版 Hawk Hook - 只 Hook get 方法
 */
public class HawkDecryptHook {
    private static final String TAG = "HawkDecryptHook";
    private static String LOG_DIR;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 初始化 Hawk Hook...");
        
        try {
            Class<?> hawkClass = XposedHelpers.findClass("com.orhanobut.hawk.Hawk", lpparam.classLoader);
            XposedBridge.log("[" + TAG + "] 找到 Hawk 类");
            
            // 只 Hook get(String key, T defaultValue) 方法
            XposedHelpers.findAndHookMethod(hawkClass, "get", String.class, Object.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[0];
                    Object result = param.getResult();
                    
                    if (result != null) {
                        String value = result.toString();
                        // 只记录重要的 key
                        if (key.contains("api") || key.contains("url") || key.contains("source") || key.contains("config")) {
                            XposedBridge.log("[" + TAG + "] [!!!] " + key + " = " + value.substring(0, Math.min(200, value.length())));
                            saveToFile("hawk_" + key + ".txt", value);
                        }
                    }
                }
            });
            
            XposedBridge.log("[" + TAG + "] Hawk Hook 成功");
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Hawk Hook 失败: " + e.getMessage());
        }
    }
    
    private static void saveToFile(String filename, String content) {
        if (LOG_DIR == null) return;
        
        filename = filename.replaceAll("[^a-zA-Z0-9_.-]", "_");
        
        try {
            File file = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
        } catch (Exception e) {
            // 忽略保存错误
        }
    }
}
