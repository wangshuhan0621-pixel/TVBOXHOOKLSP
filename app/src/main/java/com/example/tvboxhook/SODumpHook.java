package com.example.tvboxhook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook SO 加载，Dump 解密后的代码
 * 捕获所有可能的 SO 加载方式
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static Context appContext;
    private static Handler handler;
    private static boolean hasDumped = false;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        appContext = context;
        handler = new Handler(Looper.getMainLooper());
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook Runtime.loadLibrary0
        hookRuntimeLoadLibrary(lpparam);
        
        // Hook System.load (从文件路径加载)
        hookSystemLoad(lpparam);
        
        // 定时扫描 maps 查找 ftyguard
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
                            scheduleDump(libName);
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
            // Hook System.load - 用于从文件路径加载 SO
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "load", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libPath = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] System.load: " + libPath);
                        if (libPath != null && libPath.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 ftyguard 文件加载: " + libPath);
                            scheduleDump("ftyguard");
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.load Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.load Hook 失败: " + e.getMessage());
        }
    }
    
    private static void startMapsScanner() {
        // 延迟 5 秒后开始扫描，每 3 秒扫描一次
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanMapsForFtyguard();
                if (!hasDumped) {
                    handler.postDelayed(this, 3000);
                }
            }
        }, 5000);
        XposedBridge.log("[" + TAG + "] Maps 扫描器已启动");
    }
    
    private static void scanMapsForFtyguard() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream("/proc/self/maps")));
            String line;
            boolean found = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("ftyguard")) {
                    found = true;
                    XposedBridge.log("[" + TAG + "] [!!!] Maps 中发现 ftyguard: " + line);
                }
            }
            reader.close();
            
            if (found && !hasDumped) {
                hasDumped = true;
                XposedBridge.log("[" + TAG + "] [!!!] 发现 ftyguard，开始 Dump...");
                dumpSO("ftyguard");
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Maps 扫描失败: " + e.getMessage());
        }
    }
    
    private static void scheduleDump(String libName) {
        handler.postDelayed(() -> {
            dumpSO(libName);
        }, 2000);
    }
    
    private static void dumpSO(String libName) {
        try {
            XposedBridge.log("[" + TAG + "] 开始 Dump SO: " + libName);
            
            List<MemoryRegion> regions = parseMaps(libName);
            
            if (regions.isEmpty()) {
                XposedBridge.log("[" + TAG + "] 未找到 SO 内存映射: " + libName);
                return;
            }
            
            XposedBridge.log("[" + TAG + "] 找到 " + regions.size() + " 个内存区域");
            
            for (MemoryRegion region : regions) {
                XposedBridge.log("[" + TAG + "] Dump 区域: " + region);
                dumpMemoryRegion(region, libName);
            }
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Dump 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<MemoryRegion> parseMaps(String libName) {
        List<MemoryRegion> regions = new ArrayList<>();
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream("/proc/self/maps")));
            String line;
            Pattern pattern = Pattern.compile("^([0-9a-f]+)-([0-9a-f]+)\\s+([rwxp-]+)\\s+([0-9a-f]+)\\s+([0-9a-f:]+)\\s+([0-9]+)\\s*(.*)$");
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("ftyguard")) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        long start = Long.parseLong(matcher.group(1), 16);
                        long end = Long.parseLong(matcher.group(2), 16);
                        String perms = matcher.group(3);
                        String name = matcher.group(7).trim();
                        
                        if (perms.contains("r")) {
                            regions.add(new MemoryRegion(start, end, perms, name));
                            XposedBridge.log("[" + TAG + "] 区域: " + Long.toHexString(start) + "-" + Long.toHexString(end) + " " + perms);
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 解析 maps 失败: " + e.getMessage());
        }
        
        return regions;
    }
    
    private static void dumpMemoryRegion(MemoryRegion region, String libName) {
        try {
            long size = region.end - region.start;
            if (size <= 0 || size > 50 * 1024 * 1024) {
                return;
            }
            
            byte[] buffer = new byte[(int) size];
            RandomAccessFile mem = new RandomAccessFile("/proc/self/mem", "r");
            mem.seek(region.start);
            int read = mem.read(buffer);
            mem.close();
            
            if (read > 0) {
                String filename = String.format("dump_%s_%x_%x.bin", 
                    libName.replaceAll("[^a-zA-Z0-9]", "_"),
                    region.start, region.end);
                File outputFile = new File(LOG_DIR, filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(buffer, 0, read);
                fos.close();
                
                XposedBridge.log("[" + TAG + "] [+] Dump 完成: " + filename + " (" + read + " bytes)");
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Dump 区域失败: " + e.getMessage());
        }
    }
    
    private static class MemoryRegion {
        long start;
        long end;
        String perms;
        String name;
        
        MemoryRegion(long start, long end, String perms, String name) {
            this.start = start;
            this.end = end;
            this.perms = perms;
            this.name = name;
        }
        
        @Override
        public String toString() {
            return String.format("%x-%x %s %s", start, end, perms, name);
        }
    }
}
