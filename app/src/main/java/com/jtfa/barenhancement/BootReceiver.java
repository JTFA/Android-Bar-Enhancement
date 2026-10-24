package com.jtfa.barenhancement;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * 开机自启 / 覆盖安装恢复广播接收器
 *
 * - 开机完成：用户开启了「开机自启」才启动悬浮窗服务
 * - 覆盖安装（MY_PACKAGE_REPLACED）：用户上次把悬浮窗服务开关留着「开」就自动恢复，
 *   无需先打开应用
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "ai_window_settings";
    private static final String KEY_BOOT_START_ENABLED = "boot_start_enabled";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "BootReceiver.onReceive 被调用");
        Log.d(TAG, "Action: " + (intent != null ? intent.getAction() : "null"));

        if (intent == null) {
            Log.e(TAG, "Intent 为 null，退出");
            return;
        }

        String action = intent.getAction();

        // 覆盖安装：按用户上次的开关意图恢复服务
        if (ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false);
            Log.d(TAG, "覆盖安装完成，悬浮窗服务开关意图: " + serviceEnabled);
            if (!serviceEnabled) {
                Log.d(TAG, "用户未开启悬浮窗服务，跳过");
                return;
            }
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "⚠️ 悬浮窗权限未授予，无法启动服务");
                return;
            }
            startFloatWindowService(context);
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.d(TAG, "收到开机完成广播: " + action);
            Log.d(TAG, "Android 版本: " + Build.VERSION.SDK_INT);

            // 检查用户是否开启了开机自启
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean bootStartEnabled = prefs.getBoolean(KEY_BOOT_START_ENABLED, false);
            Log.d(TAG, "开机自启设置: " + bootStartEnabled);

            if (!bootStartEnabled) {
                Log.d(TAG, "开机自启未开启，跳过启动悬浮窗服务");
                return;
            }

            // 检查悬浮窗权限
            boolean hasPermission = Settings.canDrawOverlays(context);
            Log.d(TAG, "悬浮窗权限状态: " + hasPermission);
            
            if (!hasPermission) {
                Log.w(TAG, "⚠️ 悬浮窗权限未授予，无法启动服务");
                Log.w(TAG, "请先手动打开应用并授予悬浮窗权限");
                return;
            }

            startFloatWindowService(context);
        } else {
            Log.d(TAG, "忽略其他 Action: " + action);
        }
        
        Log.d(TAG, "========================================");
    }

    /**
     * 启动悬浮窗前台服务（异常不外抛，避免广播接收器崩溃）
     */
    private void startFloatWindowService(Context context) {
        try {
            Log.d(TAG, "准备启动悬浮窗服务...");
            Intent serviceIntent = new Intent(context, FloatWindowService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "使用 startForegroundService (API " + Build.VERSION.SDK_INT + ")");
                context.startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "使用 startService (API " + Build.VERSION.SDK_INT + ")");
                context.startService(serviceIntent);
            }
            Log.d(TAG, "✅ 悬浮窗服务启动命令已发送");
        } catch (Exception e) {
            Log.e(TAG, "❌ 启动悬浮窗服务失败", e);
            e.printStackTrace();
        }
    }
}
