package com.example.tvboxhook;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook guard 文件解密过程
 */
public class GuardDecryptHook {
    private static final String TAG = "GuardDecryptHook";
    private static String LOG_DIR;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 初始化 Guard 解密 Hook...");
        
        // Hook 文件读取
        hookFileRead(lpparam);
        
        // Hook 字节数组操作
        hookByteArrayOperations(lpparam);
        
        // Hook 加密相关类
        hookCryptoOperations(lpparam);
        
        XposedBridge.log("[" + TAG + "] Guard 解密 Hook 初始化完成");
    }
    
    private static void hookFileRead(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook FileInputStream.read
            XposedHelpers.findAndHookMethod("java.io.FileInputStream", lpparam.classLoader,
                "read", byte[].class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int bytesRead = (int) param.getResult();
                        if (bytesRead > 0) {
                            byte[] buffer = (byte[]) param.args[0];
                            String fileName = getFileName(param.thisObject);
                            
                            if (fileName != null && fileName.contains("guard")) {
                                XposedBridge.log("[" + TAG + "] [!!!] 读取 guard 文件: " + fileName + 
                                    " (" + bytesRead + " bytes)");
                                
                                // 保存读取的数据
                                saveData(buffer, bytesRead, "guard_read_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] FileInputStream.read Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] FileInputStream.read Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookByteArrayOperations(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 常见的解密方法签名
            // 查找返回 byte[] 且参数包含 byte[] 的方法
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "arraycopy", Object.class, int.class, Object.class, int.class, int.class, 
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object src = param.args[0];
                        if (src instanceof byte[]) {
                            byte[] srcArray = (byte[]) src;
                            int length = (int) param.args[4];
                            
                            // 检查是否可能是解密后的数据
                            if (length > 1000 && isPrintable(srcArray, Math.min(length, 100))) {
                                XposedBridge.log("[" + TAG + "] 可能的解密数据 arraycopy, 长度: " + length);
                                saveData(srcArray, Math.min(length, srcArray.length), 
                                    "arraycopy_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] System.arraycopy Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] System.arraycopy Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookCryptoOperations(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook javax.crypto.Cipher.doFinal
            XposedHelpers.findAndHookMethod("javax.crypto.Cipher", lpparam.classLoader,
                "doFinal", byte[].class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] result = (byte[]) param.getResult();
                        if (result != null && result.length > 100) {
                            XposedBridge.log("[" + TAG + "] [!!!] Cipher.doFinal 解密, 长度: " + result.length);
                            
                            // 检查是否是 ZIP
                            if (result.length > 4 && result[0] == 0x50 && result[1] == 0x4B) {
                                XposedBridge.log("[" + TAG + "] [!!!] 解密结果是 ZIP 文件！");
                                saveData(result, result.length, "decrypted_guard.zip");
                            } else {
                                saveData(result, result.length, "cipher_result_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] Cipher.doFinal Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Cipher.doFinal Hook 失败: " + e.getMessage());
        }
        
        // Hook Cipher.update
        try {
            XposedHelpers.findAndHookMethod("javax.crypto.Cipher", lpparam.classLoader,
                "update", byte[].class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] result = (byte[]) param.getResult();
                        if (result != null && result.length > 1000) {
                            XposedBridge.log("[" + TAG + "] Cipher.update, 长度: " + result.length);
                            saveData(result, result.length, "cipher_update_" + System.currentTimeMillis() + ".bin");
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] Cipher.update Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Cipher.update Hook 失败: " + e.getMessage());
        }
    }
    
    private static String getFileName(Object fileInputStream) {
        try {
            java.lang.reflect.Field pathField = FileInputStream.class.getDeclaredField("path");
            pathField.setAccessible(true);
            return (String) pathField.get(fileInputStream);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static boolean isPrintable(byte[] data, int len) {
        int printable = 0;
        for (int i = 0; i < len && i < data.length; i++) {
            byte b = data[i];
            if ((b >= 32 && b < 127) || b == '\n' || b == '\r' || b == '\t') {
                printable++;
            }
        }
        return printable > len * 0.7; // 70% 以上是可打印字符
    }
    
    private static void saveData(byte[] data, int length, String filename) {
        try {
            File outputFile = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(data, 0, Math.min(length, data.length));
            fos.close();
            XposedBridge.log("[" + TAG + "] [+] 已保存: " + filename + " (" + length + " bytes)");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 保存失败: " + e.getMessage());
        }
    }
}
