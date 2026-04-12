package com.example.tvboxhook;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setText("TVBox Hook 模块\n\n" +
                   "此模块用于 Hook TVBox 应用并捕获解密过程。\n\n" +
                   "请在 LSPosed 中启用此模块并选择 TVBox 应用。\n\n" +
                   "捕获的日志将保存在 /sdcard/TVBoxHook/hook.log");
        tv.setPadding(20, 20, 20, 20);
        setContentView(tv);
    }
}
