package com.example.tvboxhook;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TVBoxHook implements IXposedHookLoadPackage {
    private static final String TAG = "TVBoxHook";
    private static final String TARGET_PACKAGE = "com.ysc.tvbox";
    private static final String LOG_DIR = "/sdcard/TVBoxHook";
    
    private static Map<String, byte[]> capturedData = new HashMap<>();
    private static StringBuilder logBuilder = new StringBuilder();
    private static int captureCount = 0;
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }
        
        XposedBridge.log("[TVBoxHook] ================================");
        XposedBridge.log("[TVBoxHook] 目标应用已加载: " + lpparam.packageName);
        XposedBridge.log("[TVBoxHook] ================================");
        
        log("应用已加载: " + lpparam.packageName);
        
        // 创建日志目录
        createLogDir();
        
        // Hook 各种方法
        hookSystemLoadLibrary(lpparam);
        hookFileOperations(lpparam);
        hookCryptoOperations(lpparam);
        hookNativeMethods(lpparam);
        hookApplication(lpparam);
    }
    
    private void createLogDir() {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            XposedBridge.log("[TVBoxHook] 创建目录失败: " + e.getMessage());
        }
    }
    
    private void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    log("Application attached, context: " + context);
                    
                    // Hook SharedPreferences
                    hookSharedPreferences(context);
                }
            });
        } catch (Exception e) {
            log("Hook Application 失败: " + e.getMessage());
        }
    }
    
    private void hookSharedPreferences(Context context) {
        try {
            // 监控 Hawk2.xml 的读取
            File hawkFile = new File(context.getFilesDir().getParent(), "shared_prefs/Hawk2.xml");
            if (hawkFile.exists()) {
                log("发现 Hawk2.xml: " + hawkFile.getAbsolutePath());
                
                // 读取并分析 Hawk2.xml
                String content = readFile(hawkFile);
                if (content != null) {
                    analyzeHawkContent(content);
                }
            }
        } catch (Exception e) {
            log("Hook SharedPreferences 失败: " + e.getMessage());
        }
    }
    
    private String readFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            fis.close();
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
    
    private void analyzeHawkContent(String content) {
        log("分析 Hawk2.xml 内容...");
        
        // 查找所有加密数据
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.contains("##") && line.contains("@")) {
                // 提取 key 和 value
                int keyStart = line.indexOf("\"");
                int keyEnd = line.indexOf("\"", keyStart + 1);
                if (keyStart != -1 && keyEnd != -1) {
                    String key = line.substring(keyStart + 1, keyEnd);
                    log("发现加密数据 - Key: " + key);
                    
                    // 保存到文件
                    saveToFile("hawk_" + key + ".txt", line.getBytes());
                }
            }
        }
    }
    
    private void hookSystemLoadLibrary(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "loadLibrary", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[0];
                        log("System.loadLibrary(): " + libName);
                        
                        if (libName.contains("guard") || libName.contains("fty")) {
                            log("[!!!] 加载 guard SO: " + libName);
                            XposedBridge.log("[TVBoxHook] [!!!] 加载 guard SO: " + libName);
                        }
                    }
                });
        } catch (Exception e) {
            log("Hook System.loadLibrary 失败: " + e.getMessage());
        }
    }
    
    private void hookFileOperations(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook FileInputStream 构造函数
            XposedHelpers.findAndHookConstructor("java.io.FileInputStream", lpparam.classLoader,
                File.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        File file = (File) param.args[0];
                        String path = file.getAbsolutePath();
                        
                        if (path.contains("ftyshinidie") || path.contains("ftyguard")) {
                            log("[!!!] FileInputStream 打开: " + path);
                            XposedBridge.log("[TVBoxHook] [!!!] 打开文件: " + path);
                        }
                    }
                });
            
            // Hook FileInputStream read
            XposedHelpers.findAndHookMethod("java.io.FileInputStream", lpparam.classLoader,
                "read", byte[].class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int bytesRead = (int) param.getResult();
                        if (bytesRead > 10000) {
                            byte[] buffer = (byte[]) param.args[0];
                            log("[!!!] 读取大文件: " + bytesRead + " bytes");
                            log("    前16字节: " + bytesToHex(buffer, 16));
                            
                            // 检查是否是 ZIP 格式
                            if (buffer[0] == 0x50 && buffer[1] == 0x4B && 
                                buffer[2] == 0x03 && buffer[3] == 0x04) {
                                log("[***] 发现解密后的 ZIP 数据!");
                                saveDecryptedData(buffer, bytesRead, "decrypted_zip_" + System.currentTimeMillis() + ".bin");
                            }
                            
                            // 检查是否是 DEX 格式
                            if (buffer[0] == 0x64 && buffer[1] == 0x65 && 
                                buffer[2] == 0x78 && buffer[3] == 0x0A) {
                                log("[***] 发现 DEX 数据!");
                                saveDecryptedData(buffer, bytesRead, "decrypted_dex_" + System.currentTimeMillis() + ".dex");
                            }
                        }
                    }
                });
                
            // Hook FileOutputStream write
            XposedHelpers.findAndHookMethod("java.io.FileOutputStream", lpparam.classLoader,
                "write", byte[].class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] buffer = (byte[]) param.args[0];
                        int offset = (int) param.args[1];
                        int count = (int) param.args[2];
                        
                        if (count > 10000) {
                            log("[!!!] 写入大文件: " + count + " bytes");
                            log("    前16字节: " + bytesToHex(buffer, 16));
                            
                            // 检查是否是 ZIP 格式
                            if (buffer[offset] == 0x50 && buffer[offset+1] == 0x4B) {
                                log("[***] 写入 ZIP 数据!");
                                saveDecryptedData(buffer, count, "written_zip_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
                
        } catch (Exception e) {
            log("Hook FileOperations 失败: " + e.getMessage());
        }
    }
    
    private void hookCryptoOperations(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook javax.crypto.Cipher
            XposedHelpers.findAndHookMethod("javax.crypto.Cipher", lpparam.classLoader,
                "doFinal", byte[].class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] input = (byte[]) param.args[0];
                        if (input != null && input.length > 100) {
                            log("[!!!] Cipher.doFinal() 输入: " + input.length + " bytes");
                            log("    前16字节: " + bytesToHex(input, 16));
                        }
                    }
                    
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] output = (byte[]) param.getResult();
                        if (output != null && output.length > 100) {
                            log("[!!!] Cipher.doFinal() 输出: " + output.length + " bytes");
                            log("    前16字节: " + bytesToHex(output, 16));
                            
                            // 检查是否是 ZIP 格式
                            if (output[0] == 0x50 && output[1] == 0x4B) {
                                log("[***] 解密后得到 ZIP 数据!");
                                saveDecryptedData(output, output.length, "cipher_decrypted_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
        } catch (Exception e) {
            log("Hook CryptoOperations 失败: " + e.getMessage());
        }
    }
    
    private void hookNativeMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Runtime.loadLibrary0
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "loadLibrary0", Class.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String libName = (String) param.args[1];
                        log("Runtime.loadLibrary0(): " + libName);
                        
                        if (libName.contains("guard")) {
                            log("[!!!] 加载 guard 库: " + libName);
                        }
                    }
                });
            
            // Hook native 方法调用
            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader,
                "nativeLoad", String.class, ClassLoader.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String filename = (String) param.args[0];
                        log("Runtime.nativeLoad(): " + filename);
                    }
                });
        } catch (Exception e) {
            log("Hook NativeMethods 失败: " + e.getMessage());
        }
    }
    
    private void saveDecryptedData(byte[] data, int length, String filename) {
        try {
            File outputFile = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(data, 0, length);
            fos.close();
            
            log("[+] 已保存数据: " + outputFile.getAbsolutePath() + " (" + length + " bytes)");
            XposedBridge.log("[TVBoxHook] [+] 保存数据: " + filename);
            
            captureCount++;
        } catch (IOException e) {
            log("保存数据失败: " + e.getMessage());
        }
    }
    
    private void saveToFile(String filename, byte[] data) {
        try {
            File outputFile = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(data);
            fos.close();
            log("[+] 已保存文件: " + filename);
        } catch (IOException e) {
            log("保存文件失败: " + e.getMessage());
        }
    }
    
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, length); i++) {
            sb.append(String.format("%02x ", bytes[i]));
        }
        return sb.toString();
    }
    
    private void log(String message) {
        String logMsg = "[" + System.currentTimeMillis() + "] " + message;
        logBuilder.append(logMsg).append("\n");
        XposedBridge.log("[TVBoxHook] " + message);
        
        // 保存日志
        saveLog();
    }
    
    private void saveLog() {
        try {
            File logFile = new File(LOG_DIR, "hook.log");
            FileOutputStream fos = new FileOutputStream(logFile);
            fos.write(logBuilder.toString().getBytes());
            fos.close();
        } catch (IOException e) {
            XposedBridge.log("[TVBoxHook] 保存日志失败: " + e.getMessage());
        }
    }
}
