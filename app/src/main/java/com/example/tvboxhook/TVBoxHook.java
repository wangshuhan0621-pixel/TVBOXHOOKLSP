package com.example.tvboxhook;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TVBoxHook implements IXposedHookLoadPackage {
    private static final String TAG = "TVBoxHook";
    private static final String TARGET_PACKAGE = "com.ysc.tvbox";
    private static String LOG_DIR = "/sdcard/TVBoxHook";
    
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static boolean isLogDirReady = false;
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }
        
        XposedBridge.log("[TVBoxHook] ================================");
        XposedBridge.log("[TVBoxHook] 目标应用已加载: " + lpparam.packageName);
        XposedBridge.log("[TVBoxHook] ================================");
        
        // Hook Application.attach 来获取 Context
        hookApplication(lpparam);
    }
    
    private void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    
                    // 使用应用私有目录，避免权限问题
                    File externalDir = context.getExternalFilesDir(null);
                    if (externalDir != null) {
                        LOG_DIR = externalDir.getAbsolutePath() + "/TVBoxHook";
                    } else {
                        LOG_DIR = context.getFilesDir().getAbsolutePath() + "/TVBoxHook";
                    }
                    
                    XposedBridge.log("[TVBoxHook] 日志目录: " + LOG_DIR);
                    
                    // 创建日志目录
                    createLogDir();
                    
                    log("应用已加载: " + context.getPackageName());
                    log("日志目录: " + LOG_DIR);
                    
                    // 延迟初始化其他 Hook，避免启动时冲突
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        initHooks(lpparam, context);
                    }, 2000);
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[TVBoxHook] Hook Application 失败: " + e.getMessage());
        }
    }
    
    private void initHooks(XC_LoadPackage.LoadPackageParam lpparam, Context context) {
        try {
            log("初始化 Hooks...");
            // 不 Hook System.loadLibrary，改用监控文件操作
            hookFileOperations(lpparam);
            hookCryptoOperations(lpparam);
            hookSharedPreferences(context);
        } catch (Exception e) {
            log("初始化 Hooks 失败: " + e.getMessage());
        }
    }
    
    private void createLogDir() {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                isLogDirReady = created;
                XposedBridge.log("[TVBoxHook] 创建目录 " + (created ? "成功" : "失败") + ": " + LOG_DIR);
            } else {
                isLogDirReady = true;
            }
        } catch (Exception e) {
            XposedBridge.log("[TVBoxHook] 创建目录异常: " + e.getMessage());
            isLogDirReady = false;
        }
    }
    
    private void hookSharedPreferences(Context context) {
        try {
            // 监控 Hawk2.xml 的读取
            File hawkFile = new File(context.getFilesDir().getParent(), "shared_prefs/Hawk2.xml");
            if (hawkFile.exists()) {
                log("发现 Hawk2.xml: " + hawkFile.getAbsolutePath());
                
                // 异步读取文件
                executor.execute(() -> {
                    try {
                        String content = readFile(hawkFile);
                        if (content != null) {
                            analyzeHawkContent(content);
                        }
                    } catch (Exception e) {
                        log("读取 Hawk2.xml 失败: " + e.getMessage());
                    }
                });
            } else {
                log("Hawk2.xml 不存在");
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
                int keyStart = line.indexOf("\"");
                int keyEnd = line.indexOf("\"", keyStart + 1);
                if (keyStart != -1 && keyEnd != -1) {
                    String key = line.substring(keyStart + 1, keyEnd);
                    log("发现加密数据 - Key: " + key);
                    saveToFile("hawk_" + key + ".txt", line.getBytes());
                }
            }
        }
    }
    
    private void hookFileOperations(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook FileInputStream 构造函数 - 只监控目标文件
            XposedHelpers.findAndHookConstructor("java.io.FileInputStream", lpparam.classLoader,
                File.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        File file = (File) param.args[0];
                        if (file == null) return;
                        String path = file.getAbsolutePath();
                        
                        // 只监控包含 guard 或 fty 的文件路径
                        if (path.contains("guard") || path.contains("fty")) {
                            log("[!!!] FileInputStream 打开: " + path);
                        }
                    }
                });
            
            // Hook FileInputStream read - 只监控大文件读取
            XposedHelpers.findAndHookMethod("java.io.FileInputStream", lpparam.classLoader,
                "read", byte[].class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int bytesRead = (int) param.getResult();
                        // 只监控大于 100KB 的读取
                        if (bytesRead > 100000) {
                            byte[] buffer = (byte[]) param.args[0];
                            if (buffer != null && buffer.length >= 4) {
                                log("[!!!] 读取大文件: " + bytesRead + " bytes");
                                
                                // 检查是否是 ZIP 格式
                                if (buffer[0] == 0x50 && buffer[1] == 0x4B) {
                                    log("[***] 发现 ZIP 数据!");
                                    saveDecryptedData(buffer, bytesRead, "zip_" + System.currentTimeMillis() + ".bin");
                                }
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
            XposedHelpers.findAndHookMethod("javax.crypto.Cipher", lpparam.classLoader,
                "doFinal", byte[].class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] output = (byte[]) param.getResult();
                        // 只监控大于 100KB 的输出
                        if (output != null && output.length > 100000) {
                            log("[!!!] Cipher.doFinal() 输出: " + output.length + " bytes");
                            
                            // 检查是否是 ZIP 格式
                            if (output.length >= 4 && output[0] == 0x50 && output[1] == 0x4B) {
                                log("[***] 解密后得到 ZIP 数据!");
                                saveDecryptedData(output, output.length, "decrypted_" + System.currentTimeMillis() + ".bin");
                            }
                        }
                    }
                });
        } catch (Exception e) {
            log("Hook CryptoOperations 失败: " + e.getMessage());
        }
    }
    
    private void saveDecryptedData(byte[] data, int length, String filename) {
        if (!isLogDirReady) {
            XposedBridge.log("[TVBoxHook] 日志目录未就绪，无法保存数据");
            return;
        }
        
        executor.execute(() -> {
            try {
                File outputFile = new File(LOG_DIR, filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(data, 0, Math.min(length, data.length));
                fos.close();
                XposedBridge.log("[TVBoxHook] [+] 已保存: " + filename);
            } catch (IOException e) {
                XposedBridge.log("[TVBoxHook] 保存数据失败: " + e.getMessage());
            }
        });
    }
    
    private void saveToFile(String filename, byte[] data) {
        if (!isLogDirReady) return;
        
        executor.execute(() -> {
            try {
                File outputFile = new File(LOG_DIR, filename);
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(data);
                fos.close();
            } catch (IOException e) {
                XposedBridge.log("[TVBoxHook] 保存文件失败: " + e.getMessage());
            }
        });
    }
    
    private void log(String message) {
        String logMsg = "[" + System.currentTimeMillis() + "] " + message;
        XposedBridge.log("[TVBoxHook] " + logMsg);
        
        // 异步保存日志
        if (isLogDirReady) {
            executor.execute(() -> {
                try {
                    File logFile = new File(LOG_DIR, "hook.log");
                    FileOutputStream fos = new FileOutputStream(logFile, true);
                    fos.write((logMsg + "\n").getBytes());
                    fos.close();
                } catch (IOException e) {
                    // 忽略日志保存错误
                }
            });
        }
    }
}
