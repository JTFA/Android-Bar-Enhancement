package com.example.aiwindow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 简化版悬浮窗服务 - 用于测试基础功能
 */
public class SimpleFloatService extends Service {

    private static final String TAG = "SimpleFloatService";
    private static final String CHANNEL_ID = "simple_channel";
    private static final int NOTIFICATION_ID = 2001;

    private WindowManager windowManager;
    private View testView;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== 简化版服务启动 ===");
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        showSimpleBar();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "简化测试服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("简化测试服务")
                .setContentText("红色条显示中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }

    /**
     * 显示简单的红色测试条
     */
    private void showSimpleBar() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // 创建红色视图
        testView = new View(this);
        testView.setBackgroundColor(Color.RED); // 纯红色，容易看到
        
        // 设置布局参数（最简单配置）
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        
        // 窗口类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // 格式
        params.format = PixelFormat.RGBA_8888;
        
        // 标志位（最简化）
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        
        // 位置和大小
        params.gravity = Gravity.BOTTOM;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = dpToPx(100); // 100dp 高度
        
        Log.d(TAG, "准备添加红色测试条");
        Log.d(TAG, "宽度: MATCH_PARENT, 高度: 100dp");
        Log.d(TAG, "位置: 底部");
        
        try {
            windowManager.addView(testView, params);
            Log.d(TAG, "✓✓✓ 红色测试条已成功显示！");
            Log.d(TAG, "请在屏幕底部查看红色条");
        } catch (Exception e) {
            Log.e(TAG, "✗✗✗ 添加视图失败", e);
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "服务销毁");
        
        if (testView != null && windowManager != null) {
            try {
                windowManager.removeView(testView);
                Log.d(TAG, "视图已移除");
            } catch (Exception e) {
                Log.e(TAG, "移除视图失败", e);
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
