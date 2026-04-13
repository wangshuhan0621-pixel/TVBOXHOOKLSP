package com.example.tvboxhook;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook guard 文件读取和解密过程
 * 专门捕获 ftyshinidie.guard 的解密数据
 */
public class GuardFileHook {
    private static final String TAG = "GuardFileHook";
    private static String LOG_DIR;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 初始化 Guard 文件 Hook...");
        
        // Hook FileInputStream 的 read 方法
        hookFileInputStream(lpparam);
        
        // Hook ByteArrayOutputStream 的 write 方法（可能用于缓冲解密数据）
        hookByteArrayOutputStream(lpparam);
        
        XposedBridge.log("[" + TAG + "] Guard 文件 Hook 初始化完成");
    }
    
    private static void hookFileInputStream(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook read(byte[] b, int off, int len)
            XposedHelpers.findAndHookMethod("java.io.FileInputStream", lpparam.classLoader,
                "read", byte[].class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int bytesRead = (int) param.getResult();
                        if (bytesRead > 0) {
                            byte[] buffer = (byte[]) param.args[0];
                            int offset = (int) param.args[1];
                            
                            // 获取文件名
                            String filePath = getFilePath(param.thisObject);
                            
                            if (filePath != null && filePath.contains("ftyshinidie.guard")) {
                                XposedBridge.log("[" + TAG + "] [!!!] 读取 guard 文件: " + 
                                    bytesRead + " bytes");
                                
                                // 保存读取的原始数据
                                saveData(Arrays.copyOfRange(buffer, offset, offset + bytesRead), 
                                    "guard_raw_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] FileInputStream.read Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] FileInputStream.read Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookByteArrayOutputStream(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook write(byte[] b, int off, int len)
            XposedHelpers.findAndHookMethod("java.io.ByteArrayOutputStream", lpparam.classLoader,
                "write", byte[].class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int len = (int) param.args[2];
                        if (len > 1000) {  // 只关注大数据块
                            byte[] buffer = (byte[]) param.args[0];
                            int offset = (int) param.args[1];
                            
                            byte[] data = Arrays.copyOfRange(buffer, offset, offset + len);
                            
                            // 检查是否是解密后的数据（ZIP 或 DEX）
                            if (isDecryptedData(data)) {
                                XposedBridge.log("[" + TAG + "] [!!!] 捕获到解密数据: " + len + " bytes");
                                saveData(data, "guard_decrypted_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
            
            // Hook toByteArray()
            XposedHelpers.findAndHookMethod("java.io.ByteArrayOutputStream", lpparam.classLoader,
                "toByteArray", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] result = (byte[]) param.getResult();
                        if (result != null && result.length > 10000) {  // 大数组
                            if (isDecryptedData(result)) {
                                XposedBridge.log("[" + TAG + "] [!!!] toByteArray 返回解密数据: " + 
                                    result.length + " bytes");
                                saveData(result, "guard_tobytearray_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] ByteArrayOutputStream Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] ByteArrayOutputStream Hook 失败: " + e.getMessage());
        }
    }
    
    private static String getFilePath(Object fileInputStream) {
        try {
            java.lang.reflect.Field fdField = FileInputStream.class.getDeclaredField("fd");
            fdField.setAccessible(true);
            Object fd = fdField.get(fileInputStream);
            
            if (fd != null) {
                java.lang.reflect.Field pathField = fd.getClass().getDeclaredField("path");
                pathField.setAccessible(true);
                return (String) pathField.get(fd);
            }
        } catch (Exception e) {
            // 尝试另一种方式
            try {
                java.lang.reflect.Field pathField = FileInputStream.class.getDeclaredField("path");
                pathField.setAccessible(true);
                return (String) pathField.get(fileInputStream);
            } catch (Exception e2) {
            }
        }
        return null;
    }
    
    private static boolean isDecryptedData(byte[] data) {
        if (data.length < 4) return false;
        
        // 检查 ZIP 头
        if (data[0] == 'P' && data[1] == 'K' && data[2] == 0x03 && data[3] == 0x04) {
            return true;
        }
        
        // 检查 DEX 头
        if (data[0] == 'd' && data[1] == 'e' && data[2] == 'x' && data[3] == '\n') {
            return true;
        }
        
        // 检查 ELF 头
        if (data[0] == 0x7f && data[1] == 'E' && data[2] == 'L' && data[3] == 'F') {
            return true;
        }
        
        return false;
    }
    
    private static void saveData(byte[] data, String filename) {
        try {
            File outputFile = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(data);
            fos.close();
            XposedBridge.log("[" + TAG + "] [+] 已保存: " + filename + " (" + data.length + " bytes)");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 保存失败: " + e.getMessage());
        }
    }
}
