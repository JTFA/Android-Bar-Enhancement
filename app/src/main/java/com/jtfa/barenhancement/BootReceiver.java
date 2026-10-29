package com.jtfa.barenhancement;

import android.app.AlarmManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * 开机自启 / 覆盖安装恢复广播接收器
 *
 * - 开机完成：用户开启了「开机自启」才启动悬浮窗服务
 * - 覆盖安装（MY_PACKAGE_REPLACED）：用户上次把悬浮窗服务开关留着「开」就自动恢复
 *
 * ColorOS 等 ROM 上，开机广播能收到、startForegroundService 也不报错，
 * 但进程会在广播返回后立刻被回收，服务根本没机会 onCreate。
 * 所以开机广播后额外安排两次「补拉」闹钟（+15s / +60s），
 * 到点若服务没在跑就再启动一次。
 *
 * 每次广播/补拉都会把结果写入 ai_window_settings，首页连点「开机自启」5 次查看。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "ai_window_settings";
    private static final String KEY_BOOT_START_ENABLED = "boot_start_enabled";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";

    /** 启动来源：boot=开机广播，retry=开机补拉闹钟，manual=用户手动 */
    public static final String EXTRA_START_SOURCE = "start_source";
    public static final String SOURCE_BOOT = "boot";
    public static final String SOURCE_RETRY = "retry";

    /** 开机后补拉：广播返回后进程可能立刻被系统回收，隔一会儿再拉一次 */
    static final String ACTION_BOOT_RETRY = "com.jtfa.barenhancement.BOOT_RETRY";
    /** 开机 3 分钟后的最终检查：服务还没起来就发通知提醒用户 */
    static final String ACTION_BOOT_VERIFY = "com.jtfa.barenhancement.BOOT_VERIFY";
    private static final long[] RETRY_DELAYS_MS = {15_000L, 60_000L};
    private static final long VERIFY_DELAY_MS = 180_000L;
    private static final int RETRY_REQUEST_BASE = 7301;
    private static final int VERIFY_REQUEST_CODE = 7309;

    // 诊断信息（首页「开机自启」文字连点 5 次查看）
    private static final String KEY_DIAG_LAST_ACTION = "diag_boot_last_action";
    private static final String KEY_DIAG_LAST_TIME = "diag_boot_last_time";
    private static final String KEY_DIAG_BOOT_ENABLED = "diag_boot_enabled";
    private static final String KEY_DIAG_OVERLAY = "diag_boot_overlay";
    private static final String KEY_DIAG_RESULT = "diag_boot_result";
    private static final String KEY_DIAG_COUNT = "diag_boot_count";

    private static final String KEY_DIAG_RETRY_TIME = "diag_retry_last_time";
    private static final String KEY_DIAG_RETRY_RESULT = "diag_retry_last_result";
    private static final String KEY_DIAG_RETRY_COUNT = "diag_retry_count";

    // 启动被系统拦截时的恢复提醒
    private static final String GUARD_CHANNEL_ID = "boot_guard_channel";
    private static final int GUARD_NOTIFICATION_ID = 1002;

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
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean overlayGranted = Settings.canDrawOverlays(context);

        // 开机补拉闹钟
        if (ACTION_BOOT_RETRY.equals(action)) {
            handleBootRetry(context, prefs, overlayGranted);
            return;
        }

        // 开机 3 分钟后的最终检查
        if (ACTION_BOOT_VERIFY.equals(action)) {
            handleBootVerify(context, prefs);
            return;
        }

        // 覆盖安装：按用户上次的开关意图恢复服务
        if (ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            boolean serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false);
            Log.d(TAG, "覆盖安装完成，悬浮窗服务开关意图: " + serviceEnabled);
            if (!serviceEnabled) {
                record(prefs, action, prefs.getBoolean(KEY_BOOT_START_ENABLED, false),
                        overlayGranted, "覆盖安装：服务开关关闭，跳过");
                return;
            }
            if (!overlayGranted) {
                Log.w(TAG, "⚠️ 悬浮窗权限未授予，无法启动服务");
                record(prefs, action, prefs.getBoolean(KEY_BOOT_START_ENABLED, false),
                        false, "覆盖安装：悬浮窗权限未授予");
                return;
            }
            String result = startFloatWindowService(context, SOURCE_BOOT);
            record(prefs, action, prefs.getBoolean(KEY_BOOT_START_ENABLED, false), true,
                    "覆盖安装：" + result);
            if (!result.startsWith("已")) {
                notifyGuard(context, result);
            }
            return;
        }

        boolean isBootAction = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);

        if (!isBootAction) {
            Log.d(TAG, "忽略其他 Action: " + action);
            return;
        }

        Log.d(TAG, "收到开机完成广播: " + action);
        Log.d(TAG, "Android 版本: " + Build.VERSION.SDK_INT);

        // 检查用户是否开启了开机自启
        boolean bootStartEnabled = prefs.getBoolean(KEY_BOOT_START_ENABLED, false);
        Log.d(TAG, "开机自启设置: " + bootStartEnabled);

        if (!bootStartEnabled) {
            Log.d(TAG, "开机自启未开启，跳过启动悬浮窗服务");
            record(prefs, action, false, overlayGranted, "开机自启未开启，跳过");
            return;
        }

        // 检查悬浮窗权限
        Log.d(TAG, "悬浮窗权限状态: " + overlayGranted);
        if (!overlayGranted) {
            Log.w(TAG, "⚠️ 悬浮窗权限未授予，无法启动服务");
            record(prefs, action, true, false, "悬浮窗权限未授予，未启动");
            notifyGuard(context, "悬浮窗权限未授予");
            return;
        }

        String result = startFloatWindowService(context, SOURCE_BOOT);
        record(prefs, action, true, true, result);
        if (result.startsWith("已")) {
            // 关键：广播返回后进程可能被系统回收，服务来不及 onCreate。
            // 安排两次补拉，到点确认服务是否真的起来了。
            scheduleBootRetries(context);
        } else {
            notifyGuard(context, result);
        }

        Log.d(TAG, "========================================");
    }

    /** 补拉闹钟到点：服务没在跑就再启动一次 */
    private void handleBootRetry(Context context, SharedPreferences prefs, boolean overlayGranted) {
        boolean bootStartEnabled = prefs.getBoolean(KEY_BOOT_START_ENABLED, false);
        if (!bootStartEnabled) {
            recordRetry(prefs, "开机自启已关闭，跳过");
            return;
        }
        if (FloatWindowService.isRunning()) {
            recordRetry(prefs, "服务已在运行，跳过");
            return;
        }
        if (!overlayGranted) {
            recordRetry(prefs, "悬浮窗权限未授予");
            notifyGuard(context, "悬浮窗权限未授予");
            return;
        }

        Log.d(TAG, "开机补拉：服务未运行，再次启动");
        String result = startFloatWindowService(context, SOURCE_RETRY);
        recordRetry(prefs, result);
        if (!result.startsWith("已")) {
            notifyGuard(context, result);
        }
    }

    /** 开机 3 分钟后：服务还没在跑就发通知，避免用户完全无感 */
    private void handleBootVerify(Context context, SharedPreferences prefs) {
        if (!prefs.getBoolean(KEY_BOOT_START_ENABLED, false)) {
            recordRetry(prefs, "最终检查：开机自启已关闭");
            return;
        }
        if (FloatWindowService.isRunning()) {
            recordRetry(prefs, "最终检查：服务运行中");
            return;
        }
        Log.w(TAG, "最终检查：服务仍未运行，发通知提醒");
        recordRetry(prefs, "最终检查：服务未运行，已通知");
        notifyGuard(context, "开机补拉后服务仍未运行");
    }

    /** 安排 +15s / +60s 两次补拉（无需精确闹钟权限） */
    private void scheduleBootRetries(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.w(TAG, "AlarmManager 不可用，无法安排开机补拉");
            return;
        }
        for (int i = 0; i < RETRY_DELAYS_MS.length; i++) {
            Intent retryIntent = new Intent(context, BootReceiver.class)
                    .setAction(ACTION_BOOT_RETRY);
            PendingIntent pi = PendingIntent.getBroadcast(context, RETRY_REQUEST_BASE + i,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + RETRY_DELAYS_MS[i];
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                Log.d(TAG, "已安排开机补拉 #" + i + "，延迟 " + RETRY_DELAYS_MS[i] + "ms");
            } catch (Exception e) {
                Log.e(TAG, "安排开机补拉失败 #" + i, e);
            }
        }

        // 3 分钟后最终检查一次，服务仍没起来就发通知
        Intent verifyIntent = new Intent(context, BootReceiver.class)
                .setAction(ACTION_BOOT_VERIFY);
        PendingIntent verifyPi = PendingIntent.getBroadcast(context, VERIFY_REQUEST_CODE,
                verifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + VERIFY_DELAY_MS, verifyPi);
            Log.d(TAG, "已安排开机最终检查，延迟 " + VERIFY_DELAY_MS + "ms");
        } catch (Exception e) {
            Log.e(TAG, "安排开机最终检查失败", e);
        }
    }

    /**
     * 启动悬浮窗前台服务（异常不外抛，避免广播接收器崩溃）。
     *
     * @return 结果描述，以「已」开头表示启动命令已成功发出
     */
    private String startFloatWindowService(Context context, String source) {
        try {
            Log.d(TAG, "准备启动悬浮窗服务，来源: " + source);
            FloatWindowService.sPendingStartSource = source;
            Intent serviceIntent = new Intent(context, FloatWindowService.class)
                    .putExtra(EXTRA_START_SOURCE, source);
            context.startForegroundService(serviceIntent);
            Log.d(TAG, "✅ 悬浮窗服务启动命令已发送");
            return "已发送启动命令";
        } catch (ForegroundServiceStartNotAllowedException e) {
            // Android 12+ 后台启动前台服务限制；Android 16 上 specialUse 类型也偶发此异常
            Log.e(TAG, "❌ 系统拒绝从开机广播启动前台服务", e);
            return "被系统拒绝(ForegroundServiceStartNotAllowedException)";
        } catch (Exception e) {
            Log.e(TAG, "❌ 启动悬浮窗服务失败", e);
            e.printStackTrace();
            return "异常(" + e.getClass().getSimpleName() + ")";
        }
    }

    /** 记录本次开机/安装广播的诊断信息 */
    private static void record(SharedPreferences prefs, String action, boolean bootEnabled,
                               boolean overlayGranted, String result) {
        prefs.edit()
                .putString(KEY_DIAG_LAST_ACTION, action == null ? "null" : action)
                .putLong(KEY_DIAG_LAST_TIME, System.currentTimeMillis())
                .putBoolean(KEY_DIAG_BOOT_ENABLED, bootEnabled)
                .putBoolean(KEY_DIAG_OVERLAY, overlayGranted)
                .putString(KEY_DIAG_RESULT, result)
                .putInt(KEY_DIAG_COUNT, prefs.getInt(KEY_DIAG_COUNT, 0) + 1)
                .apply();
    }

    /** 记录补拉闹钟的诊断信息 */
    private static void recordRetry(SharedPreferences prefs, String result) {
        prefs.edit()
                .putLong(KEY_DIAG_RETRY_TIME, System.currentTimeMillis())
                .putString(KEY_DIAG_RETRY_RESULT, result)
                .putInt(KEY_DIAG_RETRY_COUNT, prefs.getInt(KEY_DIAG_RETRY_COUNT, 0) + 1)
                .apply();
    }

    /**
     * 启动失败时发一条可点击的通知。
     * 用户点击后进入应用，属于「用户操作」，此时再启动前台服务不会被系统拦截。
     */
    static void notifyGuard(Context context, String reason) {
        try {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel channel = new NotificationChannel(
                    GUARD_CHANNEL_ID,
                    "开机自启恢复",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("开机自启被系统拦截时提醒恢复悬浮窗");
            nm.createNotificationChannel(channel);

            Intent open = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String text = reason + "，点此打开应用恢复悬浮窗";
            Notification notification = new NotificationCompat.Builder(context, GUARD_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("开机自启未生效")
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(
                            text + "；如仍无效，请在系统设置中允许本应用「自启动 / 后台运行」并关闭电池优化。"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .build();
            nm.notify(GUARD_NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "发送恢复提醒通知失败", e);
        }
    }
}
