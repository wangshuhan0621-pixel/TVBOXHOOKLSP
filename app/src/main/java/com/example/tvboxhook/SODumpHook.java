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
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static Context appContext;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        appContext = context;
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook System.loadLibrary
        hookSystemLoadLibrary(lpparam);
        
        // Hook dlopen
        hookDlopen(lpparam);
        
        // Hook JNI_OnLoad
        hookJNIOnLoad(lpparam);
    }
    
    private static void hookSystemLoadLibrary(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "loadLibrary", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[0];
                        if (libName != null && libName.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] System.loadLibrary: " + libName);
                        }
                    }
                    
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[0];
                        if (libName != null && libName.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] SO 已加载: " + libName);
                            // 延迟 Dump，等待解密完成
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                dumpSO(libName);
                            }, 1000);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.loadLibrary Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.loadLibrary Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookDlopen(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook dlopen
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "loadLibrary0", ClassLoader.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[1];
                        if (libName != null && libName.contains("ftyguard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] Runtime.loadLibrary0: " + libName);
                        }
                    }
                });
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] loadLibrary0 Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookJNIOnLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        // 在 SO 中找到 JNI_OnLoad 并 Hook
        // 由于 SO 是动态加载的，我们需要在加载后 Hook
        XposedBridge.log("[" + TAG + "] JNI_OnLoad Hook 将在 SO 加载后设置");
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
            
            // 合并 Dump 文件
            mergeDumps(libName);
            
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
            if (size <= 0 || size > 100 * 1024 * 1024) {  // 限制 100MB
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
    
    private static void mergeDumps(String libName) {
        // 合并所有 Dump 文件
        try {
            File dir = new File(LOG_DIR);
            String prefix = "dump_" + libName.replaceAll("[^a-zA-Z0-9]", "_") + "_";
            
            File[] dumps = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".bin"));
            
            if (dumps != null && dumps.length > 0) {
                XposedBridge.log("[" + TAG + "] 合并 " + dumps.length + " 个 Dump 文件");
                
                // 这里可以实现合并逻辑
                // 暂时只记录信息
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 合并失败: " + e.getMessage());
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
