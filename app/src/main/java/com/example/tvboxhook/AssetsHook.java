package com.example.tvboxhook;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook AssetManager，捕获 guard 文件从 assets 加载的过程
 */
public class AssetsHook {
    private static final String TAG = "AssetsHook";
    private static String LOG_DIR;
    
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, Context context, String logDir) {
        LOG_DIR = logDir;
        
        XposedBridge.log("[" + TAG + "] 初始化 Assets Hook...");
        
        // Hook AssetManager.open
        hookAssetManager(lpparam);
        
        // Hook Context.getAssets
        hookContextGetAssets(lpparam);
        
        XposedBridge.log("[" + TAG + "] Assets Hook 初始化完成");
    }
    
    private static void hookAssetManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook AssetManager.open(String fileName)
            XposedHelpers.findAndHookMethod("android.content.res.AssetManager", lpparam.classLoader,
                "open", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String fileName = (String) param.args[0];
                        
                        if (fileName != null && fileName.contains("guard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] AssetManager.open: " + fileName);
                            
                            // 获取返回的 InputStream
                            InputStream is = (InputStream) param.getResult();
                            if (is != null) {
                                // 读取全部数据
                                byte[] data = readAllBytes(is);
                                
                                XposedBridge.log("[" + TAG + "] 读取到 " + data.length + " bytes");
                                
                                // 保存原始数据
                                saveData(data, "assets_guard_raw_" + System.currentTimeMillis() + ".bin");
                                
                                // 检查是否是解密后的数据
                                if (isDecryptedData(data)) {
                                    XposedBridge.log("[" + TAG + "] [!!!] 是解密后的数据!");
                                    saveData(data, "assets_guard_decrypted_" + System.currentTimeMillis() + ".bin");
                                }
                                
                                // 重新创建 InputStream 返回
                                param.setResult(new java.io.ByteArrayInputStream(data));
                            }
                        }
                    }
                });
            
            // Hook AssetManager.open(String fileName, int accessMode)
            XposedHelpers.findAndHookMethod("android.content.res.AssetManager", lpparam.classLoader,
                "open", String.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String fileName = (String) param.args[0];
                        
                        if (fileName != null && fileName.contains("guard")) {
                            XposedBridge.log("[" + TAG + "] [!!!] AssetManager.open (with mode): " + fileName);
                            
                            InputStream is = (InputStream) param.getResult();
                            if (is != null) {
                                byte[] data = readAllBytes(is);
                                
                                XposedBridge.log("[" + TAG + "] 读取到 " + data.length + " bytes");
                                
                                saveData(data, "assets_guard_raw_mode_" + System.currentTimeMillis() + ".bin");
                                
                                if (isDecryptedData(data)) {
                                    XposedBridge.log("[" + TAG + "] [!!!] 是解密后的数据!");
                                    saveData(data, "assets_guard_decrypted_mode_" + System.currentTimeMillis() + ".bin");
                                }
                                
                                param.setResult(new java.io.ByteArrayInputStream(data));
                            }
                        }
                    }
                });
            
            XposedBridge.log("[" + TAG + "] AssetManager.open Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] AssetManager.open Hook 失败: " + e.getMessage());
        }
    }
    
    private static void hookContextGetAssets(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Context.getAssets()
            XposedHelpers.findAndHookMethod("android.content.Context", lpparam.classLoader,
                "getAssets", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 可以在这里监控 AssetManager 的使用
                        XposedBridge.log("[" + TAG + "] Context.getAssets() 被调用");
                    }
                });
            
            XposedBridge.log("[" + TAG + "] Context.getAssets Hook 成功");
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] Context.getAssets Hook 失败: " + e.getMessage());
        }
    }
    
    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) > 0) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
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
        
        // 检查是否是可读的 JSON/XML
        if (data.length > 10) {
            try {
                String start = new String(data, 0, Math.min(100, data.length), "UTF-8");
                if (start.trim().startsWith("{") || start.trim().startsWith("[") || 
                    start.trim().startsWith("<") || start.trim().startsWith("http")) {
                    return true;
                }
            } catch (Exception e) {
                // 忽略编码错误
            }
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
