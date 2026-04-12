package com.example.tvboxhook;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook SO 加载，复制解密后的 SO 文件
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static int dumpCount = 0;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook System.load - 这是 ftyguard 实际使用的加载方式
        hookSystemLoad(lpparam);
        
        XposedBridge.log("[" + TAG + "] SO Dump Hook 初始化完成");
    }
    
    private static void hookSystemLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "load", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String libPath = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] [BEFORE] System.load: " + libPath);
                        
                        // 检查是否是 ftyguard 相关的 SO
                        if (libPath != null && (libPath.contains("fty") || libPath.contains("guard"))) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获到 ftyguard SO: " + libPath);
                            dumpSO(libPath);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.load Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.load Hook 失败: " + e.getMessage());
        }
    }
    
    private static void dumpSO(String libPath) {
        try {
            File srcFile = new File(libPath);
            if (!srcFile.exists()) {
                XposedBridge.log("[" + TAG + "] SO 文件不存在: " + libPath);
                return;
            }
            
            // 创建目标文件名
            String filename = "ftyguard_dump_" + dumpCount + ".so";
            File destFile = new File(LOG_DIR, filename);
            
            // 复制文件
            FileInputStream fis = new FileInputStream(srcFile);
            FileOutputStream fos = new FileOutputStream(destFile);
            
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, read);
                total += read;
            }
            
            fis.close();
            fos.close();
            
            dumpCount++;
            XposedBridge.log("[" + TAG + "] [+] SO 已复制: " + destFile.getAbsolutePath() + " (" + total + " bytes)");
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 复制 SO 失败: " + e.getMessage());
        }
    }
}
