package com.example.tvboxhook;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;

import javax.crypto.spec.SecretKeySpec;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 简化版 Hawk 密钥捕获 - 只捕获 SecretKeySpec
 */
public class HawkKeyHook {
    private static final String TAG = "HawkKeyHook";
    private static String LOG_DIR;
    private static boolean hasCapturedKey = false;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 初始化密钥捕获...");
        
        // 只 Hook SecretKeySpec，这是最关键的
        hookSecretKeySpec(lpparam);
    }
    
    private static void hookSecretKeySpec(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookConstructor(SecretKeySpec.class, byte[].class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // 只捕获一次密钥
                    if (hasCapturedKey) {
                        return;
                    }
                    
                    byte[] keyBytes = (byte[]) param.args[0];
                    String algorithm = (String) param.args[1];
                    
                    if (keyBytes != null && keyBytes.length > 0) {
                        hasCapturedKey = true;
                        
                        String keyHex = bytesToHex(keyBytes);
                        String keyBase64 = Base64.encodeToString(keyBytes, Base64.DEFAULT);
                        
                        XposedBridge.log("[" + TAG + "] [!!!] 捕获到密钥!");
                        XposedBridge.log("[" + TAG + "] 算法: " + algorithm);
                        XposedBridge.log("[" + TAG + "] Key Hex: " + keyHex);
                        XposedBridge.log("[" + TAG + "] Key Base64: " + keyBase64);
                        
                        saveKey("hawk_secret_key.txt", 
                            "Algorithm: " + algorithm + "\n" +
                            "Hex: " + keyHex + "\n" +
                            "Base64: " + keyBase64 + "\n" +
                            "Length: " + keyBytes.length + " bytes");
                    }
                }
            });
            
            XposedBridge.log("[" + TAG + "] SecretKeySpec Hook 成功");
            
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] SecretKeySpec Hook 失败: " + e.getMessage());
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
            XposedBridge.log("[" + TAG + "] 保存失败: " + e.getMessage());
        }
    }
}
