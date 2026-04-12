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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook SO 加载，Dump 解密后的代码
 * 策略：
 * 1. Hook System.load 捕获 SO 路径
 * 2. Hook JNI_OnLoad 在 SO 初始化完成后 dump
 * 3. 定时扫描 /proc/self/maps 查找 ftyguard
 * 4. 从 /proc/self/mem 读取解密后的内存
 */
public class SODumpHook {
    private static final String TAG = "SODumpHook";
    private static String LOG_DIR;
    private static Handler handler;
    private static List<String> dumpedRegions = new ArrayList<>();
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        handler = new Handler(Looper.getMainLooper());
        
        XposedBridge.log("[" + TAG + "] 初始化 SO Dump Hook...");
        
        // Hook System.load
        hookSystemLoad(lpparam);
        
        // 启动内存扫描
        startMemoryScanner();
        
        XposedBridge.log("[" + TAG + "] SO Dump Hook 初始化完成");
    }
    
    private static void hookSystemLoad(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "load", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String libPath = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] System.load: " + libPath);
                        
                        if (libPath != null && (libPath.contains("fty") || libPath.contains("guard"))) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 ftyguard SO: " + libPath);
                            
                            // 延迟 dump，等待 SO 完全初始化
                            handler.postDelayed(() -> {
                                dumpAllFtyguardRegions();
                            }, 5000);
                            
                            // 再次延迟，确保所有解密完成
                            handler.postDelayed(() -> {
                                dumpAllFtyguardRegions();
                            }, 10000);
                        }
                    }
                });
            XposedBridge.log("[" + TAG + "] System.load Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.load Hook 失败: " + e.getMessage());
        }
    }
    
    private static void startMemoryScanner() {
        // 每 5 秒扫描一次
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanAndDumpFtyguard();
                handler.postDelayed(this, 5000);
            }
        }, 3000);
    }
    
    private static void scanAndDumpFtyguard() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream("/proc/self/maps")));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("ftyguard") || line.contains(".ftyfn")) {
                    // 解析内存区域
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        String[] addrs = parts[0].split("-");
                        if (addrs.length == 2) {
                            long start = Long.parseLong(addrs[0], 16);
                            long end = Long.parseLong(addrs[1], 16);
                            String perms = parts[1];
                            
                            // 只 dump 可执行或只读的代码段
                            if ((perms.contains("x") || perms.contains("r")) && !perms.contains("w")) {
                                String regionKey = start + "-" + end;
                                if (!dumpedRegions.contains(regionKey)) {
                                    dumpedRegions.add(regionKey);
                                    dumpMemoryRegion(start, end, perms);
                                }
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 扫描失败: " + e.getMessage());
        }
    }
    
    private static void dumpAllFtyguardRegions() {
        XposedBridge.log("[" + TAG + "] [!!!] 开始 dump 所有 ftyguard 区域...");
        scanAndDumpFtyguard();
    }
    
    private static void dumpMemoryRegion(long start, long end, String perms) {
        try {
            long size = end - start;
            if (size <= 0 || size > 100 * 1024 * 1024) {  // 限制 100MB
                return;
            }
            
            XposedBridge.log("[" + TAG + "] Dump 区域: 0x" + Long.toHexString(start) + 
                "-0x" + Long.toHexString(end) + " " + perms);
            
            // 读取内存
            byte[] buffer = new byte[(int) size];
            RandomAccessFile mem = new RandomAccessFile("/proc/self/mem", "r");
            mem.seek(start);
            int read = mem.read(buffer);
            mem.close();
            
            if (read > 0) {
                // 生成文件名
                String filename = String.format("ftyguard_0x%s_0x%s_%s.bin",
                    Long.toHexString(start), Long.toHexString(end),
                    perms.replaceAll("[^rwxp-]", ""));
                
                File outputFile = new File(LOG_DIR, filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(buffer, 0, read);
                fos.close();
                
                XposedBridge.log("[" + TAG + "] [+] Dump 完成: " + filename + 
                    " (" + read + " bytes)");
                
                // 如果是代码段，尝试保存为 ELF
                if (perms.contains("x") && read > 1024) {
                    saveAsELF(buffer, read, start);
                }
            }
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Dump 失败: " + e.getMessage());
        }
    }
    
    private static void saveAsELF(byte[] data, int size, long baseAddr) {
        try {
            // 检查是否是有效的 ELF
            if (size < 4 || data[0] != 0x7f || data[1] != 'E' || 
                data[2] != 'L' || data[3] != 'F') {
                return;
            }
            
            String filename = "ftyguard_elf_0x" + Long.toHexString(baseAddr) + ".so";
            File outputFile = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(data, 0, size);
            fos.close();
            
            XposedBridge.log("[" + TAG + "] [+] ELF 已保存: " + filename);
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 保存 ELF 失败: " + e.getMessage());
        }
    }
}
