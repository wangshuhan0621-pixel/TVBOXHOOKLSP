package com.example.tvboxhook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook SO 加载，记录 SO 加载信息
 * 简化版本 - 只记录日志，不读取内存避免崩溃
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static Context appContext;
    private static Handler handler;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        appContext = context;
        handler = new Handler(Looper.getMainLooper());
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook Runtime.loadLibrary0
        hookRuntimeLoadLibrary(lpparam);
        
        // Hook System.load
        hookSystemLoad(lpparam);
        
        // 定时扫描 maps
        startMapsScanner();
    }
    
    private static void hookRuntimeLoadLibrary(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "loadLibrary0", ClassLoader.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[1];
                        XposedBridge.log("[" + TAG + "] Runtime.loadLibrary0: " + libName);
                        if (libName != null && libName.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 ftyguard 加载: " + libName);
                            saveMapsToFile();
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
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libPath = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] System.load: " + libPath);
                        if (libPath != null && libPath.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 ftyguard 文件加载: " + libPath);
                            saveMapsToFile();
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.load Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.load Hook 失败: " + e.getMessage());
        }
    }
    
    private static void startMapsScanner() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanAndSaveMaps();
                handler.postDelayed(this, 5000);
            }
        }, 3000);
        XposedBridge.log("[" + TAG + "] Maps 扫描器已启动");
    }
    
    private static void scanAndSaveMaps() {
        try {
            List<String> ftyguardLines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream("/proc/self/maps")));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("ftyguard")) {
                    ftyguardLines.add(line);
                    XposedBridge.log("[" + TAG + "] [!!!] Maps 中发现 ftyguard: " + line);
                }
            }
            reader.close();
            
            if (!ftyguardLines.isEmpty()) {
                saveFtyguardInfo(ftyguardLines);
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Maps 扫描失败: " + e.getMessage());
        }
    }
    
    private static void saveMapsToFile() {
        try {
            File outputFile = new File(LOG_DIR, "maps_full.txt");
            FileOutputStream fos = new FileOutputStream(outputFile);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream("/proc/self/maps")));
            String line;
            
            while ((line = reader.readLine()) != null) {
                fos.write((line + "\n").getBytes());
            }
            reader.close();
            fos.close();
            
            XposedBridge.log("[" + TAG + "] [+] Maps 已保存: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 保存 Maps 失败: " + e.getMessage());
        }
    }
    
    private static void saveFtyguardInfo(List<String> lines) {
        try {
            File outputFile = new File(LOG_DIR, "ftyguard_maps.txt");
            FileOutputStream fos = new FileOutputStream(outputFile);
            for (String line : lines) {
                fos.write((line + "\n").getBytes());
            }
            fos.close();
            XposedBridge.log("[" + TAG + "] [+] ftyguard 信息已保存: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 保存 ftyguard 信息失败: " + e.getMessage());
        }
    }
}
