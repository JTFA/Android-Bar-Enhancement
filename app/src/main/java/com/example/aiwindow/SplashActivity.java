package com.example.aiwindow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 透明代理 Activity，作为桌面启动入口
 * 根据"在最近任务中隐藏"设置，决定是否带 FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS 启动 MainActivity
 * 自身永远不出现在最近任务中
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        boolean excludeFromRecents = prefs.getBoolean("exclude_from_recents", false);

        Log.d(TAG, "启动 MainActivity, excludeFromRecents=" + excludeFromRecents);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (excludeFromRecents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        // 携带原始 Intent 的 action，保持桌面启动行为
        if (getIntent() != null) {
            intent.setAction(getIntent().getAction());
        }
        startActivity(intent);
        finish();
    }
}
