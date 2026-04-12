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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook SO 加载，从内存 dump 解密后的 SO
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static int dumpCount = 0;
    private static Handler handler;
    private static List<String> soPaths = new ArrayList<>();
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        handler = new Handler(Looper.getMainLooper());
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook System.load
        hookSystemLoad(lpparam);
        
        // 启动内存扫描器
        startMemoryScanner();
        
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
                        
                        if (libPath != null && (libPath.contains("fty") || libPath.contains("guard"))) {
                            soPaths.add(libPath);
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获到 ftyguard SO: " + libPath);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.load Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.load Hook 失败: " + e.getMessage());
        }
    }
    
    private static void startMemoryScanner() {
        // 延迟 5 秒后开始扫描
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanAndDumpFromMemory();
                // 继续扫描
                handler.postDelayed(this, 3000);
            }
        }, 5000);
        XposedBridge.log("[" + TAG + "] 内存扫描器已启动");
    }
    
    private static void scanAndDumpFromMemory() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream("/proc/self/maps")));
            String line;
            
            while ((line = reader.readLine()) != null) {
                // 查找 ftyguard 的内存映射
                if (line.contains("fty") || line.contains("guard")) {
                    XposedBridge.log("[" + TAG + "] [!!!] Maps 中发现: " + line);
                    
                    // 解析内存地址
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        String[] addrs = parts[0].split("-");
                        if (addrs.length == 2) {
                            long start = Long.parseLong(addrs[0], 16);
                            long end = Long.parseLong(addrs[1], 16);
                            long size = end - start;
                            
                            // 检查是否是可执行区域（代码段）
                            if (parts[1].contains("x") && size > 0 && size < 50 * 1024 * 1024) {
                                dumpMemoryRegion(start, end, "ftyguard_mem_" + dumpCount);
                                dumpCount++;
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 内存扫描失败: " + e.getMessage());
        }
    }
    
    private static void dumpMemoryRegion(long start, long end, String name) {
        try {
            long size = end - start;
            if (size <= 0 || size > 50 * 1024 * 1024) {
                return;
            }
            
            // 读取内存
            byte[] buffer = new byte[(int) size];
            RandomAccessFile mem = new RandomAccessFile("/proc/self/mem", "r");
            mem.seek(start);
            int read = mem.read(buffer);
            mem.close();
            
            if (read > 0) {
                // 保存到文件
                String filename = name + "_" + Long.toHexString(start) + "_" + Long.toHexString(end) + ".bin";
                File outputFile = new File(LOG_DIR, filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(buffer, 0, read);
                fos.close();
                
                XposedBridge.log("[" + TAG + "] [+] 内存已 dump: " + filename + " (" + read + " bytes)");
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Dump 内存失败: " + e.getMessage());
        }
    }
}
