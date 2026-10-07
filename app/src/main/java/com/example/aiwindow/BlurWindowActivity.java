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
    
    /**
     * 标志位：当通过快捷方式点击启动外部应用时设为 true。
     * 用于进程冻结恢复后的兜底清理——onResume 中检测到此标志时自销毁模糊层。
     */
    static volatile boolean sShortcutLaunched = false;
    
    private BroadcastReceiver sizeUpdateReceiver;
    private int originalBlurRadius = 25;
    private int originalY = 0; // 保存原始 Y 位置
    private FrameLayout root;
    // 窗口圆角背景复用（拖动期间会被逐帧 UPDATE_BLUR_WINDOW_SIZE 调用，避免每帧 new 对象）
    private android.graphics.drawable.GradientDrawable windowBackgroundDrawable;
    private int appliedCornerRadius = -1; // 当前已应用的圆角，未变化时跳过更新

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
    
    /**
     * 淡出并关闭当前存活的 BlurWindowActivity（由 OverlayWindowActivity 在动画开始时调用）
     * 直接通过静态方法调用，不经过广播，轻量且同步
     */
    public static void fadeOutIfAlive() {
        BlurWindowActivity act = sInstance != null ? sInstance.get() : null;
        if (act != null && !act.isFinishing()) {
            act.fadeOutAndFinish();
        }
    }

    /**
     * 逐帧直连更新模糊窗口矩形（同进程同主线程静态调用，替代每帧 UPDATE 广播：
     * 消除 Intent 分配 / AMS 广播往返 / 接收调度延迟，setAttributes 与内容变换同帧生效）。
     * 调用方已完成屏幕钳制、去重与量化。alpha &lt; 0 表示保持透明度现值。
     */
    public static void applyRect(int x, int y, int w, int h, int cornerRadius, float alpha) {
        BlurWindowActivity act = sInstance != null ? sInstance.get() : null;
        if (act != null && !act.isFinishing()) {
            act.applyWindowRect(x, y, w, h, cornerRadius, alpha);
        }
    }

    /**
     * 直连同步模糊窗口平移（键盘避让动画逐帧调用，替代 BLUR_WINDOW_TRANSLATE 广播）
     * @param translationY 内容窗口的 Y 方向平移量
     */
    public static void applyTranslate(float translationY) {
        BlurWindowActivity act = sInstance != null ? sInstance.get() : null;
        if (act != null && !act.isFinishing()) {
            act.applyWindowTranslate(translationY);
        }
    }

    /**
     * 应用窗口矩形/透明度（广播接收与直连静态调用共用的实现）。
     * @param alpha 透明度；&lt; 0 表示保持现值
     */
    private void applyWindowRect(int x, int y, int w, int h, int cornerRadius, float alpha) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.x = x;
        params.y = y;
        params.width = w;
        params.height = h;
        if (alpha >= 0f) {
            params.alpha = alpha;
        }
        getWindow().setAttributes(params);
        // 更新窗口圆角背景（复用 Drawable，半径未变时早退，避免每帧失效重绘）
        applyWindowCornerRadius(cornerRadius);
    }

    /**
     * 平移模糊窗口：translationY 是 contentContainer 相对于原始位置的偏移量，
     * 新的 y = originalY + translationY（广播接收与直连调用共用）
     */
    private void applyWindowTranslate(float translationY) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.y = originalY + (int) translationY;
        getWindow().setAttributes(params);
    }

    /**
     * 设置/更新窗口圆角背景。背景为完全透明的圆角矩形，
     * 让窗口级模糊（setBackgroundBlurRadius）穿透显示；复用同一 Drawable 实例。
     * 半径未变化时直接返回，避免逐帧 setCornerRadius 导致 drawable 失效重绘。
     */
    private void applyWindowCornerRadius(int cornerRadius) {
        if (cornerRadius > 0) {
            if (windowBackgroundDrawable != null && appliedCornerRadius == cornerRadius) {
                return;
            }
            appliedCornerRadius = cornerRadius;
            if (windowBackgroundDrawable == null) {
                windowBackgroundDrawable = new android.graphics.drawable.GradientDrawable();
                windowBackgroundDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                // 使用完全透明的颜色，让模糊效果穿透显示
                windowBackgroundDrawable.setColor(Color.TRANSPARENT);
                getWindow().setBackgroundDrawable(windowBackgroundDrawable);
                Log.d(TAG, "已设置窗口圆角背景: " + cornerRadius + "px");
            }
            windowBackgroundDrawable.setCornerRadius(cornerRadius - 7);
        } else if (windowBackgroundDrawable != null) {
            appliedCornerRadius = 0;
            windowBackgroundDrawable = null;
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 重置兜底标志位：新实例创建时清除上次生命周期遗留的残值
        // 防止 onResume 误判导致新 BlurWindowActivity 被立即销毁
        sShortcutLaunched = false;
        sInstance = new WeakReference<>(this);
        Log.d(TAG, "onCreate");

        // 读取位置/尺寸/半径
        int blurX = getIntent().getIntExtra(EXTRA_BLUR_X, 0);
        int blurY = getIntent().getIntExtra(EXTRA_BLUR_Y, 0);
        int blurWidth = getIntent().getIntExtra(EXTRA_BLUR_WIDTH, 200);
        int blurHeight = getIntent().getIntExtra(EXTRA_BLUR_HEIGHT, 200);
        originalBlurRadius = getIntent().getIntExtra(EXTRA_BLUR_RADIUS, 50);
        int cornerRadius = getIntent().getIntExtra(EXTRA_BLUR_CORNER_RADIUS, 0);

        // 配置窗口:全透明、不聚焦、不触摸(让上层 OverlayWindowActivity 接收触摸)
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        
        // 关键：设置窗口背景为圆角矩形（半透明或完全透明）
        // 这样模糊效果会透过这个圆角背景显示出来
        applyWindowCornerRadius(cornerRadius);

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
        // 初始透明度为 0，由 OverlayWindowActivity 展开动画逐帧广播的 blur_alpha 驱动显示
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
                    // 同步透明度；不带该 extra 的广播（配置变更同步）保持现值
                    float newAlpha = intent.getFloatExtra("blur_alpha", Float.NaN);
                    applyWindowRect(newBlurX, newBlurY, newBlurWidth, newBlurHeight,
                            newCornerRadius, newAlpha);
                } else if ("BLUR_WINDOW_FADE_OUT".equals(intent.getAction())) {
                    // 收到渐变消失通知，执行透明度动画
                    Log.d(TAG, "收到 BLUR_WINDOW_FADE_OUT 广播，开始渐变消失");
                    fadeOutAndFinish();
                } else if ("BLUR_WINDOW_TRANSLATE".equals(intent.getAction())) {
                    // 收到平移通知，同步移动窗口位置
                    applyWindowTranslate(intent.getFloatExtra("translation_y", 0f));
                } else if ("BLUR_WINDOW_FADE_IN".equals(intent.getAction())) {
                    // 收到渐变显示通知，执行透明度动画（从70%进度开始）
                    float progress = intent.getFloatExtra("progress", 0.7f);
                    Log.d(TAG, "收到 BLUR_WINDOW_FADE_IN 广播，progress=" + progress);
                    fadeInFromProgress(progress);
                }
            }
        };
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sizeUpdateReceiver, new IntentFilter("UPDATE_BLUR_WINDOW_SIZE"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sizeUpdateReceiver, new IntentFilter("UPDATE_BLUR_WINDOW_SIZE"));
        }
        
        // 额外注册 BLUR_WINDOW_FADE_OUT 广播
        IntentFilter fadeOutFilter = new IntentFilter("BLUR_WINDOW_FADE_OUT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sizeUpdateReceiver, fadeOutFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sizeUpdateReceiver, fadeOutFilter);
        }
        
        // 额外注册 BLUR_WINDOW_TRANSLATE 广播
        IntentFilter translateFilter = new IntentFilter("BLUR_WINDOW_TRANSLATE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sizeUpdateReceiver, translateFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sizeUpdateReceiver, translateFilter);
        }
        
        // 额外注册 BLUR_WINDOW_FADE_IN 广播
        IntentFilter fadeInFilter = new IntentFilter("BLUR_WINDOW_FADE_IN");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sizeUpdateReceiver, fadeInFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sizeUpdateReceiver, fadeInFilter);
        }
        
        Log.d(TAG, "已注册模糊窗口大小更新、渐变消失、平移和渐变显示广播接收器");

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
    protected void onResume() {
        super.onResume();
        // 兜底清理：当快捷方式点击启动外部应用后进程被冻结
        // 导致 fadeOutAndFinish 的 Handler 渐变动画未执行，
        // 进程恢复后 BlurWindowActivity 回到前台时在此处检测并自清理。
        if (sShortcutLaunched) {
            sShortcutLaunched = false;
            if (!isFinishing()) {
                Log.d(TAG, "onResume: 检测到快捷方式已启动，清理模糊层");
                finish();
            }
        }
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
        Log.d(TAG, "fadeoutAndFinish: 开始执行，root=" + (root != null ? "存在" : "null"));
        
        if (root == null) {
            Log.w(TAG, "fadeoutAndFinish: root 为 null，直接 finish");
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        
        // 先隐藏 View，防止闪烁
        root.setVisibility(View.INVISIBLE);
        
        // 窗口 alpha + 模糊半径同步渐变动画
        // 模糊半径必须在此循环中**逐帧递减**，原因：
        // intent_extra 使用 FLAG_ACTIVITY_CLEAR_TOP 导致任务切换极快，
        // ColorOS 等 ROM 会立即冻结后台进程，Handler 动画可能在中途被冻结。
        // 如果只靠动画结束时 finish() 移除窗口，冻结时 finish() 不执行，
        // 窗口保留在 SurfaceFlinger，模糊持续可见。
        // 逐帧递减模糊半径确保每一帧的改动都在 SurfaceFlinger 层生效，
        // 即使进程冻结了，已减小的半径不反弹。
        final WindowManager.LayoutParams wmParams = (WindowManager.LayoutParams) getWindow().getAttributes();
        final float startWindowAlpha = wmParams.alpha;
        final int startBlurRadius = originalBlurRadius;
        final long startTime = System.currentTimeMillis();
        final int duration = 100;
        
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / duration, 1.0f);
                
                // alpha 渐隐
                wmParams.alpha = startWindowAlpha * (1f - progress);
                getWindow().setAttributes(wmParams);
                
                // 模糊半径同步递减：每一帧的改动都直接送达 SurfaceFlinger
                // 进程冻结后已改动的半径不回弹，保证模糊至少被部分清除
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    int curRadius = Math.max(0, Math.round(startBlurRadius * (1f - progress)));
                    getWindow().setBackgroundBlurRadius(curRadius);
                }
                
                if (progress < 1.0f) {
                    handler.postDelayed(this, 16); // ~60fps
                } else {
                    // 确保最终半径归零
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getWindow().setBackgroundBlurRadius(0);
                    }
                    Log.d(TAG, "fadeoutAndFinish: 动画完成，准备 finish");
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });
        
        Log.d(TAG, "fadeoutAndFinish: 窗口渐变动画+模糊半径渐隐已启动");
    }
}
