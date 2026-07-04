package com.example.aiwindow;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * 模糊子窗口 Activity
 * 显示在 OverlayWindowActivity 下方，尺寸和位置与内容区域一致，
 * 通过 getWindow().setBackgroundBlurRadius() 仅模糊窗口背后的桌面区域，
 * 供上层的 OverlayWindowActivity 通过半透明底色透出模糊效果。
 */
public class BlurWindowActivity extends Activity {

    private static final String TAG = "BlurWindowActivity";
    private static WeakReference<BlurWindowActivity> sInstance;
    
    private BroadcastReceiver sizeUpdateReceiver;
    private int originalBlurRadius = 25;
    private FrameLayout root;

    public static final String EXTRA_BLUR_X = "blur_x";
    public static final String EXTRA_BLUR_Y = "blur_y";
    public static final String EXTRA_BLUR_WIDTH = "blur_width";
    public static final String EXTRA_BLUR_HEIGHT = "blur_height";
    public static final String EXTRA_BLUR_RADIUS = "blur_radius";
    public static final String EXTRA_BLUR_CORNER_RADIUS = "blur_corner_radius";

    /**
     * 关闭当前存活的 BlurWindowActivity（由 OverlayWindowActivity 在销毁时调用）
     */
    public static void finishIfAlive() {
        BlurWindowActivity act = sInstance != null ? sInstance.get() : null;
        if (act != null && !act.isFinishing()) {
            Log.d(TAG, "接收到关闭信号，主动 finish");
            act.finish();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = new WeakReference<>(this);
        Log.d(TAG, "onCreate");

        // 读取位置/尺寸/半径
        int blurX = getIntent().getIntExtra(EXTRA_BLUR_X, 0);
        int blurY = getIntent().getIntExtra(EXTRA_BLUR_Y, 0);
        int blurWidth = getIntent().getIntExtra(EXTRA_BLUR_WIDTH, 200);
        int blurHeight = getIntent().getIntExtra(EXTRA_BLUR_HEIGHT, 200);
        originalBlurRadius = getIntent().getIntExtra(EXTRA_BLUR_RADIUS, 50);
        int cornerRadius = getIntent().getIntExtra(EXTRA_BLUR_CORNER_RADIUS, 0);

        // 配置窗口：全透明、不聚焦、不触摸（让上层 OverlayWindowActivity 接收触摸）
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        // 关键：设置窗口背景为圆角矩形（半透明或完全透明）
        // 这样模糊效果会透过这个圆角背景显示出来
        if (cornerRadius > 0) {
            android.graphics.drawable.GradientDrawable windowBackground = new android.graphics.drawable.GradientDrawable();
            windowBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            windowBackground.setCornerRadius(cornerRadius);
            // 使用完全透明的颜色，让模糊效果穿透显示
            windowBackground.setColor(Color.TRANSPARENT);
            getWindow().setBackgroundDrawable(windowBackground);
            Log.d(TAG, "已设置窗口圆角背景: " + cornerRadius + "px");
        } else {
            // 没有圆角时使用默认透明背景
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 空根布局，全透明，仅用于承载窗口
        root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.TRANSPARENT);
        
        setContentView(root);

        // setContentView 之后 DecorView 已创建，此时才能设置窗口属性和模糊
        // 设置窗口位置和尺寸（绝对坐标，TOP|START 原点在屏幕左上角）
        WindowManager.LayoutParams wmParams = getWindow().getAttributes();
        wmParams.gravity = Gravity.TOP | Gravity.START;
        wmParams.x = blurX;
        wmParams.y = blurY;
        wmParams.width = blurWidth;
        wmParams.height = blurHeight;

        getWindow().setAttributes(wmParams);

        Log.d(TAG, "窗口位置: (" + blurX + "," + blurY + ") 尺寸: " + blurWidth + "x" + blurHeight);
        
        // 注册广播接收器，用于接收快捷窗口的模糊窗口大小更新
        sizeUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("UPDATE_BLUR_WINDOW_SIZE".equals(intent.getAction())) {
                    int newBlurX = intent.getIntExtra("blur_x", 0);
                    int newBlurY = intent.getIntExtra("blur_y", 0);
                    int newBlurWidth = intent.getIntExtra("blur_width", 200);
                    int newBlurHeight = intent.getIntExtra("blur_height", 400);
                    int newCornerRadius = intent.getIntExtra("blur_corner_radius", 0);
                    
                    WindowManager.LayoutParams params = getWindow().getAttributes();
                    params.x = newBlurX;
                    params.y = newBlurY;
                    params.width = newBlurWidth;
                    params.height = newBlurHeight;
                    getWindow().setAttributes(params);
                    
                    // 更新窗口圆角背景
                    if (newCornerRadius > 0) {
                        android.graphics.drawable.GradientDrawable windowBackground = new android.graphics.drawable.GradientDrawable();
                        windowBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                        windowBackground.setCornerRadius(newCornerRadius);
                        windowBackground.setColor(Color.TRANSPARENT);
                        getWindow().setBackgroundDrawable(windowBackground);
                        Log.d(TAG, "模糊窗口圆角已更新: " + newCornerRadius + "px");
                    }
                    
                    Log.d(TAG, "模糊窗口已更新: (" + newBlurX + "," + newBlurY + ") " 
                            + newBlurWidth + "x" + newBlurHeight);
                }
            }
        };
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sizeUpdateReceiver, new IntentFilter("UPDATE_BLUR_WINDOW_SIZE"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sizeUpdateReceiver, new IntentFilter("UPDATE_BLUR_WINDOW_SIZE"));
        }
        Log.d(TAG, "已注册模糊窗口大小更新广播接收器");

        // 窗口级背景模糊（API 31+）：模糊窗口背后那小块桌面内容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setBackgroundBlurRadius(originalBlurRadius);
            Log.d(TAG, "已启用窗口背景模糊，半径: " + originalBlurRadius);
        } else {
            Log.d(TAG, "API < 31，跳过模糊");
        }

        Log.d(TAG, "BlurWindowActivity 创建完成");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (sInstance != null && sInstance.get() == this) {
            sInstance = null;
        }
        // 注销广播接收器
        if (sizeUpdateReceiver != null) {
            try {
                unregisterReceiver(sizeUpdateReceiver);
                Log.d(TAG, "已注销模糊窗口大小更新广播接收器");
            } catch (Exception e) {
                Log.e(TAG, "注销广播接收器失败", e);
            }
        }
    }
}
