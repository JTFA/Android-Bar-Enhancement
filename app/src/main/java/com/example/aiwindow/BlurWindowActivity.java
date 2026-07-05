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
import android.view.View;
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
    private int originalY = 0; // 保存原始 Y 位置
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
        // 初始透明度为0，等待展开动画到70%时开始渐变
        wmParams.alpha = 0f;

        getWindow().setAttributes(wmParams);

        Log.d(TAG, "窗口位置: (" + blurX + "," + blurY + ") 尺寸: " + blurWidth + "x" + blurHeight);
        
        // 保存原始 Y 位置
        originalY = blurY;
        
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
                } else if ("BLUR_WINDOW_FADE_OUT".equals(intent.getAction())) {
                    // 收到渐变消失通知，执行透明度动画
                    Log.d(TAG, "收到 BLUR_WINDOW_FADE_OUT 广播，开始渐变消失");
                    fadeOutAndFinish();
                } else if ("BLUR_WINDOW_TRANSLATE".equals(intent.getAction())) {
                    // 收到平移通知，同步移动窗口位置
                    float translationY = intent.getFloatExtra("translation_y", 0f);
                    WindowManager.LayoutParams params = getWindow().getAttributes();
                    // translationY 是 contentContainer 相对于原始位置的偏移量
                    // 所以新的 y = originalY + translationY
                    params.y = originalY + (int)translationY;
                    getWindow().setAttributes(params);
                    Log.d(TAG, "模糊窗口平移: translationY=" + translationY + ", newY=" + params.y);
                } else if ("BLUR_WINDOW_FADE_IN".equals(intent.getAction())) {
                    // 收到渐变显示通知，执行透明度动画（从70%进度开始）
                    float progress = intent.getFloatExtra("progress", 0.7f);
                    Log.d(TAG, "收到 BLUR_WINDOW_FADE_IN 广播，progress=" + progress);
                    fadeInFromProgress(progress);
                }
            }
        };
        
        // 统一注册所有广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction("UPDATE_BLUR_WINDOW_SIZE");
        filter.addAction("BLUR_WINDOW_FADE_OUT");
        filter.addAction("BLUR_WINDOW_TRANSLATE");
        filter.addAction("BLUR_WINDOW_FADE_IN");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sizeUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sizeUpdateReceiver, filter);
        }
        
        Log.d(TAG, "已注册模糊窗口广播接收器（4个动作）");

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
    
    /**
     * 从指定进度开始渐变显示动画
     * @param startProgress 起始进度（0.0-1.0），通常为0.7表示从70%进度开始
     */
    private void fadeInFromProgress(float startProgress) {
        Log.d(TAG, "fadeInFromProgress: 开始执行，startProgress=" + startProgress);
        
        if (root == null) {
            Log.w(TAG, "fadeInFromProgress: root 为 null，直接返回");
            return;
        }
        
        // 先显示 View
        root.setVisibility(View.VISIBLE);
        
        // 只使用窗口 alpha 实现渐变（模糊效果由窗口级别控制）
        final WindowManager.LayoutParams wmParams = (WindowManager.LayoutParams) getWindow().getAttributes();
        // 从当前 alpha（0）渐变到 1.0
        final float startAlpha = wmParams.alpha;
        final long startTime = System.currentTimeMillis();
        final int duration = 100; // 100ms 完成渐变
        
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / duration, 1.0f);
                
                // 同步更新窗口 alpha，从 startAlpha 渐变到 1.0
                wmParams.alpha = startAlpha + (1.0f - startAlpha) * progress;
                getWindow().setAttributes(wmParams);
                
                if (progress < 1.0f) {
                    handler.postDelayed(this, 16); // ~60fps
                } else {
                    Log.d(TAG, "fadeInFromProgress: 动画完成，alpha=" + wmParams.alpha);
                }
            }
        });
        
        Log.d(TAG, "fadeInFromProgress: 窗口渐变动画已启动");
    }
    
    /**
     * 渐变消失动画
     */
    private void fadeOutAndFinish() {
        Log.d(TAG, "fadeoutAndFinish: 开始执行，root=" + (root != null ? "存在" : "null")
                + ", isFinishing=" + isFinishing()
                + ", isDestroyed=" + isDestroyed());
        
        if (root == null) {
            Log.w(TAG, "fadeoutAndFinish: root 为 null，直接 finish");
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        
        // 先隐藏 View，防止闪烁
        root.setVisibility(View.INVISIBLE);
        
        // 只使用窗口 alpha 实现渐变（模糊效果由窗口级别控制）
        final WindowManager.LayoutParams wmParams = (WindowManager.LayoutParams) getWindow().getAttributes();
        final float startWindowAlpha = wmParams.alpha;
        final long startTime = System.currentTimeMillis();
        final int duration = 100;
        
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / duration, 1.0f);
                
                // 同步更新窗口 alpha
                wmParams.alpha = startWindowAlpha * (1f - progress);
                getWindow().setAttributes(wmParams);
                
                if (progress < 1.0f) {
                    handler.postDelayed(this, 16); // ~60fps
                } else {
                    Log.d(TAG, "fadeoutAndFinish: 动画完成，准备 finish, finalAlpha=" + wmParams.alpha);
                    if (!isFinishing()) {
                        finish();
                        overridePendingTransition(0, 0);
                        Log.d(TAG, "fadeoutAndFinish: finish() 已调用");
                    } else {
                        Log.w(TAG, "fadeoutAndFinish: Activity 已经在 finishing 状态");
                    }
                }
            }
        });
        
        Log.d(TAG, "fadeoutAndFinish: 窗口渐变动画已启动");
    }
}
