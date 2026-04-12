package com.example.tvboxhook;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook Hawk 库的解密方法，直接获取明文数据
 */
public class HawkDecryptHook {
    private static final String TAG = "HawkDecryptHook";
    private static String LOG_DIR;
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        // Hook Hawk 的 get 方法
        hookHawkGet(lpparam);
        
        // Hook Hawk 内部存储类
        hookHawkStorage(lpparam);
        
        // Hook 加密/解密相关类
        hookEncryption(lpparam);
    }
    
    private static void hookHawkGet(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook com.orhanobut.hawk.Hawk 的 get 方法
            Class<?> hawkClass = XposedHelpers.findClass("com.orhanobut.hawk.Hawk", lpparam.classLoader);
            
            // Hook get(String key) 方法
            XposedHelpers.findAndHookMethod(hawkClass, "get", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[0];
                    Object result = param.getResult();
                    
                    if (result != null) {
                        logDecryptedData(key, result);
                    }
                }
            });
            
            // Hook get(String key, T defaultValue) 方法
            XposedHelpers.findAndHookMethod(hawkClass, "get", String.class, Object.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[0];
                    Object result = param.getResult();
                    
                    if (result != null) {
                        logDecryptedData(key, result);
                    }
                }
            });
            
            XposedBridge.log("[" + TAG + "] Hawk.get() Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Hawk.get() Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookHawkStorage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Hawk 的内部存储实现
            // 通常是 SharedPreferencesStorage 或 SqliteStorage
            
            // 尝试 Hook SharedPreferencesStorage
            try {
                Class<?> storageClass = XposedHelpers.findClass("com.orhanobut.hawk.SharedPreferencesStorage", lpparam.classLoader);
                hookStorageGetMethod(storageClass);
                XposedBridge.log("[" + TAG + "] SharedPreferencesStorage Hook 成功");
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] SharedPreferencesStorage 未找到");
            }
            
            // 尝试 Hook SqliteStorage
            try {
                Class<?> storageClass = XposedHelpers.findClass("com.orhanobut.hawk.SqliteStorage", lpparam.classLoader);
                hookStorageGetMethod(storageClass);
                XposedBridge.log("[" + TAG + "] SqliteStorage Hook 成功");
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] SqliteStorage 未找到");
            }
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Storage Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookStorageGetMethod(Class<?> storageClass) {
        try {
            XposedHelpers.findAndHookMethod(storageClass, "get", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[0];
                    Object result = param.getResult();
                    
                    if (result instanceof String) {
                        String encrypted = (String) result;
                        if (encrypted != null && encrypted.contains("@")) {
                            XposedBridge.log("[" + TAG + "] [!!!] 从 Storage 获取加密数据 - Key: " + key);
                            XposedBridge.log("[" + TAG + "] 加密值: " + encrypted.substring(0, Math.min(100, encrypted.length())));
                        }
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Storage get() Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookEncryption(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 加密/解密相关类
            // 通常是 Encryption 接口的实现
            
            // 尝试 Hook AES 加密类
            try {
                Class<?> aesClass = XposedHelpers.findClass("com.orhanobut.hawk.AesEncryption", lpparam.classLoader);
                hookDecryptMethod(aesClass);
                XposedBridge.log("[" + TAG + "] AesEncryption Hook 成功");
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] AesEncryption 未找到");
            }
            
            // 尝试 Hook Conceal 加密（Facebook 的加密库）
            try {
                Class<?> concealClass = XposedHelpers.findClass("com.orhanobut.hawk.ConcealEncryption", lpparam.classLoader);
                hookDecryptMethod(concealClass);
                XposedBridge.log("[" + TAG + "] ConcealEncryption Hook 成功");
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] ConcealEncryption 未找到");
            }
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Encryption Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookDecryptMethod(Class<?> encryptionClass) {
        try {
            // Hook decrypt 方法
            XposedHelpers.findAndHookMethod(encryptionClass, "decrypt", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String ciphertext = (String) param.args[0];
                    String plaintext = (String) param.getResult();
                    
                    if (plaintext != null && !plaintext.isEmpty()) {
                        XposedBridge.log("[" + TAG + "] [***] 解密成功!");
                        XposedBridge.log("[" + TAG + "] 密文: " + ciphertext.substring(0, Math.min(50, ciphertext.length())) + "...");
                        XposedBridge.log("[" + TAG + "] 明文: " + plaintext.substring(0, Math.min(200, plaintext.length())));
                        
                        // 保存到文件
                        savePlaintext("decrypted_" + System.currentTimeMillis() + ".txt", 
                            "Ciphertext: " + ciphertext + "\n\nPlaintext: " + plaintext);
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] decrypt() Hook 失败: " + e.getMessage());
        }
    }
    
    private static void logDecryptedData(String key, Object data) {
        try {
            String value = data.toString();
            XposedBridge.log("[" + TAG + "] [!!!] 解密数据 - Key: " + key);
            XposedBridge.log("[" + TAG + "] 值: " + value.substring(0, Math.min(500, value.length())));
            
            // 保存到文件
            String filename = "hawk_decrypted_" + key + ".txt";
            savePlaintext(filename, "Key: " + key + "\n\nValue: " + value);
            
            // 如果是 JSON，尝试解析
            if (value.startsWith("{") || value.startsWith("[")) {
                XposedBridge.log("[" + TAG + "] [JSON] 检测到 JSON 数据");
            }
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 记录数据失败: " + e.getMessage());
        }
    }
    
    private static void savePlaintext(String filename, String content) {
        if (LOG_DIR == null) return;
        
        executor.execute(() -> {
            try {
                File file = new File(LOG_DIR, filename);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(content.getBytes());
                fos.close();
                XposedBridge.log("[" + TAG + "] [+] 已保存: " + filename);
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] 保存失败: " + e.getMessage());
            }
        });
    }
}
