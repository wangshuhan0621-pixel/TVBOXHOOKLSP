package com.example.tvboxhook;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.util.Enumeration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook Hawk 库的密钥生成和存储，捕获解密密钥
 */
public class HawkKeyHook {
    private static final String TAG = "HawkKeyHook";
    private static String LOG_DIR;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 开始捕获 Hawk 密钥...");
        
        // Hook Hawk 初始化
        hookHawkInit(lpparam);
        
        // Hook 密钥生成
        hookKeyGeneration(lpparam);
        
        // Hook Android KeyStore
        hookKeyStore(lpparam);
        
        // Hook 加密相关类
        hookEncryptionClasses(lpparam);
    }
    
    private static void hookHawkInit(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Hawk.init()
            Class<?> hawkClass = XposedHelpers.findClass("com.orhanobut.hawk.Hawk", lpparam.classLoader);
            
            XposedHelpers.findAndHookMethod(hawkClass, "init", Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[" + TAG + "] [!!!] Hawk.init() 被调用");
                }
            });
            
            // Hook HawkBuilder
            try {
                Class<?> builderClass = XposedHelpers.findClass("com.orhanobut.hawk.HawkBuilder", lpparam.classLoader);
                
                // Hook setPassword
                XposedHelpers.findAndHookMethod(builderClass, "setPassword", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String password = (String) param.args[0];
                        XposedBridge.log("[" + TAG + "] [!!!] Hawk 密码: " + password);
                        saveKey("hawk_password.txt", password);
                    }
                });
                
                // Hook build
                XposedHelpers.findAndHookMethod(builderClass, "build", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("[" + TAG + "] [!!!] HawkBuilder.build() 完成");
                        // 尝试获取 builder 中的密钥
                        Object builder = param.thisObject;
                        dumpObjectFields(builder, "HawkBuilder");
                    }
                });
                
            } catch (Exception e) {
                XposedBridge.log("[" + TAG + "] HawkBuilder 未找到: " + e.getMessage());
            }
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Hawk Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookKeyGeneration(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook SecretKeySpec 构造函数
        try {
            XposedHelpers.findAndHookConstructor(SecretKeySpec.class, byte[].class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    byte[] keyBytes = (byte[]) param.args[0];
                    String algorithm = (String) param.args[1];
                    
                    if (keyBytes != null && keyBytes.length > 0) {
                        String keyHex = bytesToHex(keyBytes);
                        String keyBase64 = Base64.encodeToString(keyBytes, Base64.DEFAULT);
                        
                        XposedBridge.log("[" + TAG + "] [!!!] SecretKeySpec 创建");
                        XposedBridge.log("[" + TAG + "] 算法: " + algorithm);
                        XposedBridge.log("[" + TAG + "] Key Hex: " + keyHex);
                        XposedBridge.log("[" + TAG + "] Key Base64: " + keyBase64);
                        
                        saveKey("secretkeyspec_" + algorithm + ".txt", 
                            "Hex: " + keyHex + "\nBase64: " + keyBase64);
                    }
                }
            });
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] SecretKeySpec Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookKeyStore(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook KeyStore.getKey
            XposedHelpers.findAndHookMethod(KeyStore.class, "getKey", String.class, char[].class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String alias = (String) param.args[0];
                    Key key = (Key) param.getResult();
                    
                    if (key != null) {
                        XposedBridge.log("[" + TAG + "] [!!!] KeyStore.getKey(): " + alias);
                        
                        if (key instanceof SecretKey) {
                            byte[] encoded = key.getEncoded();
                            if (encoded != null) {
                                String keyHex = bytesToHex(encoded);
                                String keyBase64 = Base64.encodeToString(encoded, Base64.DEFAULT);
                                
                                XposedBridge.log("[" + TAG + "] Key Hex: " + keyHex);
                                XposedBridge.log("[" + TAG + "] Key Base64: " + keyBase64);
                                
                                saveKey("keystore_" + alias + ".txt",
                                    "Hex: " + keyHex + "\nBase64: " + keyBase64);
                            }
                        }
                    }
                }
            });
            
            // Hook KeyStore.load
            XposedHelpers.findAndHookMethod(KeyStore.class, "load", java.security.KeyStore.LoadStoreParameter.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("[" + TAG + "] [!!!] KeyStore.load() 被调用");
                    
                    // 列出所有别名
                    KeyStore keyStore = (KeyStore) param.thisObject;
                    try {
                        Enumeration<String> aliases = keyStore.aliases();
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            XposedBridge.log("[" + TAG + "] KeyStore 别名: " + alias);
                        }
                    } catch (Exception e) {
                    }
                }
            });
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] KeyStore Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookEncryptionClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Conceal 库（Facebook 的加密库，Hawk 可能使用）
        String[] concealClasses = {
            "com.facebook.crypto.Crypto",
            "com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain",
            "com.facebook.crypto.keychain.KeyChain"
        };
        
        for (String className : concealClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                XposedBridge.log("[" + TAG + "] 找到加密类: " + className);
                
                // Hook 获取密钥的方法
                XposedHelpers.findAndHookMethod(clazz, "getCipherKey", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] key = (byte[]) param.getResult();
                        if (key != null) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 CipherKey: " + bytesToHex(key));
                            saveKey("conceal_cipherkey.txt", bytesToHex(key));
                        }
                    }
                });
                
                XposedHelpers.findAndHookMethod(clazz, "getMacKey", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        byte[] key = (byte[]) param.getResult();
                        if (key != null) {
                            XposedBridge.log("[" + TAG + "] [!!!] 捕获 MacKey: " + bytesToHex(key));
                            saveKey("conceal_mackey.txt", bytesToHex(key));
                        }
                    }
                });
                
            } catch (Exception e) {
                // 类不存在，继续
            }
        }
        
        // Hook AesEncryption
        try {
            Class<?> aesClass = XposedHelpers.findClass("com.orhanobut.hawk.AesEncryption", lpparam.classLoader);
            XposedBridge.log("[" + TAG + "] 找到 AesEncryption 类");
            
            // Hook 构造函数
            XposedHelpers.findAndHookConstructor(aesClass, byte[].class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    byte[] key = (byte[]) param.args[0];
                    if (key != null) {
                        XposedBridge.log("[" + TAG + "] [!!!] AesEncryption 密钥: " + bytesToHex(key));
                        saveKey("aes_encryption_key.txt", bytesToHex(key));
                    }
                }
            });
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] AesEncryption 未找到: " + e.getMessage());
        }
    }
    
    private static void dumpObjectFields(Object obj, String name) {
        try {
            XposedBridge.log("[" + TAG + "] Dump " + name + " 字段:");
            Class<?> clazz = obj.getClass();
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) {
                    XposedBridge.log("[" + TAG + "]   " + field.getName() + " = " + value.toString().substring(0, Math.min(100, value.toString().length())));
                }
            }
        } catch (Exception e) {
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private static void saveKey(String filename, String content) {
        if (LOG_DIR == null) return;
        
        try {
            File file = new File(LOG_DIR, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
            XposedBridge.log("[" + TAG + "] [+] 密钥已保存: " + filename);
        } catch (Exception e) {
        }
    }
}
