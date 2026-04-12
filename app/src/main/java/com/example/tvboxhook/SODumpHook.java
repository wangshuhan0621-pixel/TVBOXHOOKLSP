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
 * 简化版本 - 只 Hook Runtime.loadLibrary0，不 Hook System.loadLibrary
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static Context appContext;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        appContext = context;
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // 只 Hook Runtime.loadLibrary0，不 Hook System.loadLibrary
        hookRuntimeLoadLibrary(lpparam);
    }
    
    private static void hookRuntimeLoadLibrary(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Runtime.loadLibrary0 - 这是实际加载 SO 的方法
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "loadLibrary0", ClassLoader.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[1];
                        if (libName != null && libName.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] Runtime.loadLibrary0: " + libName);
                            // 延迟 Dump，等待解密完成
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                dumpSO(libName);
                            }, 2000);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] Runtime.loadLibrary0 Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Runtime.loadLibrary0 Hook 失败: " + e.getMessage());
        }
    }
    
    private static void dumpSO(String libName) {
        try {
            XposedBridge.log("[" + TAG + "] 开始 Dump SO: " + libName);
            
            // 从 maps 读取内存映射
            List<MemoryRegion> regions = parseMaps(libName);
            
            if (regions.isEmpty()) {
                XposedBridge.log("[" + TAG + "] 未找到 SO 内存映射: " + libName);
                return;
            }
            
            XposedBridge.log("[" + TAG + "] 找到 " + regions.size() + " 个内存区域");
            
            // Dump 每个区域
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
                        
                        // 只 Dump 可执行和可读的代码段
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
            if (size <= 0 || size > 50 * 1024 * 1024) {  // 限制 50MB
                return;
            }
            
            // 读取内存
            byte[] buffer = new byte[(int) size];
            RandomAccessFile mem = new RandomAccessFile("/proc/self/mem", "r");
            mem.seek(region.start);
            int read = mem.read(buffer);
            mem.close();
            
            if (read > 0) {
                // 保存到文件
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
