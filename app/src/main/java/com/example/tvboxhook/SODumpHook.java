package com.example.tvboxhook;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook SO 加载，记录 SO 加载信息
 * 极简版本 - 只记录日志到 LSPosed，不操作文件
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook Runtime.loadLibrary0
        hookRuntimeLoadLibrary(lpparam);
        
        // Hook System.load
        hookSystemLoad(lpparam);
        
        XposedBridge.log("[" + TAG + "] SO Dump Hook 初始化完成");
    }
    
    private static void hookRuntimeLoadLibrary(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "loadLibrary0", ClassLoader.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[1];
                        XposedBridge.log("[" + TAG + "] [BEFORE] Runtime.loadLibrary0: " + libName);
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[1];
                        XposedBridge.log("[" + TAG + "] [AFTER] Runtime.loadLibrary0: " + libName);
                        if (libName != null && libName.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 ftyguard 加载: " + libName);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] Runtime.loadLibrary0 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Runtime.loadLibrary0 Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookSystemLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "load", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String libPath = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] [BEFORE] System.load: " + libPath);
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libPath = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] [AFTER] System.load: " + libPath);
                        if (libPath != null && libPath.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 ftyguard 文件加载: " + libPath);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.load Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.load Hook 失败: " + e.getMessage());
        }
    }
}
