package com.example.aiwindow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;

/**
 * 透明 Activity 实现的悬浮窗
 * 支持亚克力/液态玻璃模糊效果（圆角矩形内部模糊显示后方内容，外部透明）
 * AI 窗口和快捷方式窗口统一入口
 */
public class OverlayWindowActivity extends AppCompatActivity {

    private static final String TAG = "OverlayWindowActivity";

    // ─── Intent 参数键 ───
    public static final String EXTRA_WINDOW_TYPE = "window_type";
    public static final String EXTRA_START_X = "start_x";
    public static final String EXTRA_START_Y = "start_y";
    public static final String EXTRA_START_WIDTH = "start_width";
    public static final String EXTRA_START_HEIGHT = "start_height";
    public static final String EXTRA_TARGET_WIDTH = "target_width";
    public static final String EXTRA_TARGET_HEIGHT = "target_height";
    public static final String EXTRA_TARGET_POS_X = "target_pos_x";
    public static final String EXTRA_TARGET_POS_Y = "target_pos_y";
    public static final String EXTRA_ANIM_DURATION = "anim_duration";
    public static final String EXTRA_CORNER_RADIUS = "corner_radius";
    public static final String EXTRA_BORDER_WIDTH = "border_width";
    public static final String EXTRA_BORDER_COLOR = "border_color";
    public static final String EXTRA_BORDER_ALPHA = "border_alpha";
    public static final String EXTRA_SHOW_CLOSE_BUTTON = "show_close_button";
    public static final String EXTRA_SHOW_REFRESH_BUTTON = "show_refresh_button";
    public static final String EXTRA_SHOW_BACK_BUTTON = "show_back_button";
    public static final String EXTRA_CLOSE_ON_OUTSIDE = "close_on_outside";
    public static final String EXTRA_BACK_PRIORITIZE_WEBVIEW = "back_prioritize_webview";

    // AI 窗口
    public static final String EXTRA_AI_URL = "ai_url";
    public static final String EXTRA_AI_STYLE = "ai_style";
    public static final String EXTRA_AI_BLUR_RADIUS = "ai_blur_radius";
    public static final String EXTRA_AI_OVERLAY_ALPHA = "ai_overlay_alpha";
    // 快捷方式
    public static final String EXTRA_SHORTCUT_ICON_SIZE = "shortcut_icon_size";
    public static final String EXTRA_SHORTCUT_STYLE = "shortcut_style";
    public static final String EXTRA_SHORTCUT_BLUR_RADIUS = "shortcut_blur_radius";
    public static final String EXTRA_SHORTCUT_OVERLAY_ALPHA = "shortcut_overlay_alpha";

    public static final int WINDOW_TYPE_AI = 0;
    public static final int WINDOW_TYPE_SHORTCUT = 1;

    // ─── 动画参数 ───
    private static final float SMOOTH_FACTOR = 0.65f;
    private final PathInterpolator animInterpolator = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    private final OvershootInterpolator expandInterpolator = new OvershootInterpolator(0.5f);

    // ─── Intent 参数 ───
    private int windowType;
    private int startX, startY, startWidth, startHeight;
    private int targetWidth, targetHeight, targetPosX, targetPosY;
    private int animDuration;
    private int cornerRadius;
    private int borderWidth;
    private String borderColor;
    private float borderAlpha;
    private boolean showCloseButton, showRefreshButton, showBackButton;
    private boolean closeOnOutside, backPrioritizeWebview;
    private String aiUrl;
    private int shortcutIconSize, shortcutStyle, shortcutBlurRadius, shortcutOverlayAlpha;
    
    // AI 窗口模糊效果
    private int aiStyle, aiBlurRadius, aiOverlayAlpha;
    private String customCss; // 自定义 CSS
    private boolean webpageBgTransparent = false; // 网页背景透明
    
    // 背景遮罩设置
    private int overlayAlpha = 50; // 遮罩透明度 (0-100)
    private String overlayColor = "#000000"; // 遮罩颜色
    private int overlayAnimDuration = 100; // 遮罩渐变时长 (ms)

    // ─── 视图 ───
    private FrameLayout rootLayout;
    private FrameLayout contentContainer;
    private View blurBackgroundView; // 独立模糊背景层（仅显示模糊桌面，不含子内容）
    private View overlayView; // 背景遮罩视图
    private WebView webView;
    private ImageView backButtonRef;
    private boolean isExpanded = false;
    private boolean isCollapsing = false;
    private BroadcastReceiver restoreBarReceiver;
    
    // ─── WebView 状态保持 ───
    private static WebView sPersistentWebView; // 静态 WebView，跨 Activity 生命周期保持
    
    // ─── 模糊窗口渐变显示标志 ───
    private boolean blurWindowFadeInSent = false; // 标记是否已发送模糊窗口渐变显示广播

    // ─── 输入法避让相关 ───
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private boolean isKeyboardVisible = false;
    private int keyboardOffset = 397; // 默认上移距离（px），从 SharedPreferences 加载
    private int keyboardAnimDuration = 250; // 平移动画时长（ms）
    private boolean isKeyboardAnimating = false;

    // ─── 动画初始值 ───
    private float initScaleX, initScaleY;
    private float initTranslationX, initTranslationY;

    // ─── 屏幕尺寸（解析度无关像素，但 getRealMetrics 是 px） ───
    private int screenWidth, screenHeight;

    // ═══════════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // 1. 解析参数
        parseIntent(getIntent());

        // 2. 获取全屏尺寸
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        // 3. 配置窗口（透明、全屏）
        setupWindow();

        // 4. 创建根布局
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.TRANSPARENT);
        rootLayout.setClipChildren(false);
        rootLayout.setClickable(true);
        setContentView(rootLayout);

        // 5. 创建内容视图
        createContent();

        // 6. 注册广播
        registerRestoreReceiver();

        // 8. 返回键处理
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (windowType == WINDOW_TYPE_AI && backPrioritizeWebview
                        && webView != null && webView.canGoBack()) {
                    webView.goBack();
                    updateBackButtonState();
                    return;
                }
                collapseAndFinish();
            }
        });

        // 9. 布局完成后执行展开动画
        contentContainer.post(this::performExpandAnimation);
        
        // 10. 设置输入法检测监听器
        setupKeyboardListener(rootLayout);
    }

    @Override
    protected void onDestroy() {
        // 通知下层模糊窗口关闭
        BlurWindowActivity.finishIfAlive();
        // 移除键盘监听器
        removeKeyboardListener();
        if (restoreBarReceiver != null) {
            try { unregisterReceiver(restoreBarReceiver); } catch (Exception ignored) {}
        }
        
        // 关键修复：从父容器中移除 WebView，但不销毁它，以便下次打开时复用
        if (webView != null && sPersistentWebView == webView) {
            android.view.ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
                Log.d(TAG, "WebView 已从父容器移除，保持状态");
            }
        }
        // 不清空 webView 引用，sPersistentWebView 保持引用
        
        super.onDestroy();
    }

    // ═══════════════════════════════════════════════════════════
    // 窗口配置（亚克力模糊）
    // ═══════════════════════════════════════════════════════════

    private void setupWindow() {
        Window window = getWindow();

        // 透明状态栏和导航栏
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    /**
     * 创建静态模糊背景层（截屏 + 模糊）
     * 在 Activity 启动瞬间截取下方屏幕内容，裁剪到 contentContainer 区域，
     * 对截图应用 RenderEffect 模糊 + 圆角裁剪，显示在 contentContainer 下方。
     * 静态（非实时）模糊，不模糊窗口内文字/图标。
     */
    private void createBlurBackgroundLayer() {
        // 模糊由下层 BlurWindowActivity 提供，此方法仅保留以保持兼容
        // OverlayWindowActivity 本身保持全透明，通过半透明 contentContainer 底色
        // 透出下层 BlurWindowActivity 的窗口级模糊效果
        Log.d(TAG, "模糊由下层 BlurWindowActivity 提供");
    }
    
    /**
     * 创建背景遮罩视图
     * 在窗口外部设置半透明遮罩，避免窗口与外部元素混淆
     */
    private void createBackgroundOverlay() {
        try {
            overlayView = new View(this);
            
            // 解析颜色
            int colorInt = Color.parseColor(overlayColor);
            int r = (colorInt >> 16) & 0xFF;
            int g = (colorInt >> 8) & 0xFF;
            int b = colorInt & 0xFF;
            // overlayAlpha 是 0-100，转换为 0-255
            int alpha = (int)(overlayAlpha * 255.0 / 100.0);
            
            // 设置背景色
            overlayView.setBackgroundColor(Color.argb(alpha, r, g, b));
            
            // 设置 LayoutParams 覆盖整个屏幕
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            overlayView.setLayoutParams(params);
            
            // 初始状态为完全透明
            overlayView.setAlpha(0f);
            
            // 插入到 rootLayout 最底层（contentContainer 之前）
            rootLayout.addView(overlayView, 0);
            
            Log.d(TAG, "背景遮罩已创建 - 颜色: " + overlayColor + ", 透明度: " + overlayAlpha + "%");
        } catch (Exception e) {
            Log.e(TAG, "创建背景遮罩失败", e);
            overlayView = null;
        }
    }
    
    /**
     * 显示背景遮罩（渐变动画）
     */
    private void showBackgroundOverlay() {
        if (overlayView == null) return;
        
        // 计算目标透明度
        float targetAlpha = overlayAlpha / 100.0f;
        
        // 执行渐变动画
        overlayView.animate()
                .alpha(targetAlpha)
                .setDuration(overlayAnimDuration)
                .start();
        
        Log.d(TAG, "背景遮罩开始显示 - 目标透明度: " + targetAlpha);
    }
    
    /**
     * 隐藏背景遮罩（渐变动画）
     */
    private void hideBackgroundOverlay() {
        if (overlayView == null) return;
        
        // 执行渐变动画到完全透明
        overlayView.animate()
                .alpha(0f)
                .setDuration(overlayAnimDuration)
                .start();
        
        Log.d(TAG, "背景遮罩开始隐藏");
    }

    // ═══════════════════════════════════════════════════════════
    // 解析 Intent
    // ═══════════════════════════════════════════════════════════

    private void parseIntent(Intent intent) {
        windowType = intent.getIntExtra(EXTRA_WINDOW_TYPE, WINDOW_TYPE_SHORTCUT);
        startX = intent.getIntExtra(EXTRA_START_X, 0);
        startY = intent.getIntExtra(EXTRA_START_Y, 0);
        startWidth = intent.getIntExtra(EXTRA_START_WIDTH, 200);
        startHeight = intent.getIntExtra(EXTRA_START_HEIGHT, 20);
        targetWidth = intent.getIntExtra(EXTRA_TARGET_WIDTH, 350);
        targetHeight = intent.getIntExtra(EXTRA_TARGET_HEIGHT, 350);
        targetPosX = intent.getIntExtra(EXTRA_TARGET_POS_X, 0);
        targetPosY = intent.getIntExtra(EXTRA_TARGET_POS_Y, 231);
        animDuration = intent.getIntExtra(EXTRA_ANIM_DURATION, 385);
        cornerRadius = intent.getIntExtra(EXTRA_CORNER_RADIUS, 24);
        borderWidth = intent.getIntExtra(EXTRA_BORDER_WIDTH, 1);
        borderColor = intent.getStringExtra(EXTRA_BORDER_COLOR);
        if (borderColor == null) borderColor = "#000000";
        borderAlpha = intent.getFloatExtra(EXTRA_BORDER_ALPHA, 0.1f);
        showCloseButton = intent.getBooleanExtra(EXTRA_SHOW_CLOSE_BUTTON, true);
        showRefreshButton = intent.getBooleanExtra(EXTRA_SHOW_REFRESH_BUTTON, true);
        showBackButton = intent.getBooleanExtra(EXTRA_SHOW_BACK_BUTTON, true);
        closeOnOutside = intent.getBooleanExtra(EXTRA_CLOSE_ON_OUTSIDE, true);
        backPrioritizeWebview = intent.getBooleanExtra(EXTRA_BACK_PRIORITIZE_WEBVIEW, true);

        if (windowType == WINDOW_TYPE_AI) {
            aiUrl = intent.getStringExtra(EXTRA_AI_URL);
            if (aiUrl == null) aiUrl = "https://www.doubao.com";
            aiStyle = intent.getIntExtra(EXTRA_AI_STYLE, 0);
            aiBlurRadius = intent.getIntExtra(EXTRA_AI_BLUR_RADIUS, 25);
            aiOverlayAlpha = intent.getIntExtra(EXTRA_AI_OVERLAY_ALPHA, 204);
            
            // 加载输入法避让设置
            loadKeyboardSettings();
            
            // 加载自定义 CSS
            loadCustomCss();
            
            // 加载网页背景透明设置
            loadWebpageBgTransparentSetting();
        } else {
            shortcutIconSize = intent.getIntExtra(EXTRA_SHORTCUT_ICON_SIZE, 100);
            shortcutStyle = intent.getIntExtra(EXTRA_SHORTCUT_STYLE, 0);
            shortcutBlurRadius = intent.getIntExtra(EXTRA_SHORTCUT_BLUR_RADIUS, 20);
            shortcutOverlayAlpha = intent.getIntExtra(EXTRA_SHORTCUT_OVERLAY_ALPHA, 180);
        }
        
        // 加载背景遮罩设置（所有窗口类型）
        loadOverlaySettings();
    }
    
    /**
     * 从 SharedPreferences 加载输入法避让设置
     */
    private void loadKeyboardSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            keyboardOffset = prefs.getInt("keyboard_offset", 397);
            keyboardAnimDuration = prefs.getInt("keyboard_anim_duration", 250);
            Log.d(TAG, "已加载输入法设置: offset=" + keyboardOffset + "px, duration=" + keyboardAnimDuration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "加载输入法设置失败", e);
        }
    }
    
    /**
     * 从 SharedPreferences 加载自定义 CSS
     */
    private void loadCustomCss() {
        try {
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            customCss = prefs.getString("custom_css", "");
            if (!TextUtils.isEmpty(customCss)) {
                Log.d(TAG, "已加载自定义 CSS，长度: " + customCss.length());
            }
        } catch (Exception e) {
            Log.e(TAG, "加载自定义 CSS 失败", e);
        }
    }
    
    /**
     * 从 SharedPreferences 加载网页背景透明设置
     */
    private void loadWebpageBgTransparentSetting() {
        try {
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            webpageBgTransparent = prefs.getBoolean("webpage_bg_transparent", false);
            Log.d(TAG, "已加载网页背景透明设置: " + webpageBgTransparent);
        } catch (Exception e) {
            Log.e(TAG, "加载网页背景透明设置失败", e);
        }
    }
    
    /**
     * 从 SharedPreferences 加载背景遮罩设置
     */
    private void loadOverlaySettings() {
        try {
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 50);
            overlayColor = prefs.getString("overlay_color", "#000000");
            overlayAnimDuration = prefs.getInt("overlay_anim_duration", 100);
            Log.d(TAG, "已加载遮罩设置 - 透明度: " + overlayAlpha + "%, 颜色: " + overlayColor + ", 动画时长: " + overlayAnimDuration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "加载遮罩设置失败", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 创建内容
    // ═══════════════════════════════════════════════════════════

    private void createContent() {
        // 内容容器：圆角矩形，半透明亚克力底色
        contentContainer = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(targetWidth, targetHeight);
        params.gravity = Gravity.CENTER;
        params.leftMargin = targetPosX;
        params.topMargin = targetPosY;
        params.rightMargin = -targetPosX;
        params.bottomMargin = -targetPosY;
        contentContainer.setLayoutParams(params);
        contentContainer.setClipChildren(false);

        // 设置连续圆角 + 半透明亚克力背景
        final float density = getResources().getDisplayMetrics().density;
        AcrylicRoundRectDrawable bg = new AcrylicRoundRectDrawable(
                getAcrylicBgColor(), dpToPx(cornerRadius), density);
        if (borderWidth > 0) {
            int borderColorWithAlpha = parseColorWithAlpha(borderColor, borderAlpha);
            bg.setStroke(dpToPx(borderWidth), borderColorWithAlpha);
        }
        contentContainer.setBackground(bg);

        // 禁止点击穿透到外部
        contentContainer.setOnClickListener(v -> { /* consume */ });

        // 根据类型添加内容
        if (windowType == WINDOW_TYPE_AI) {
            addAiContent(contentContainer);
            // AI 窗口添加关闭/刷新/返回按钮
            addButtons(contentContainer);
        } else {
            addShortcutContent(contentContainer);
            // 快捷窗口无按钮，点击外部关闭
        }

        // 外部点击检测 - 使用 contentContainer 实际布局位置判断
        rootLayout.setOnTouchListener(new View.OnTouchListener() {
            private boolean mOutsideDown = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!closeOnOutside) return false;
                float x = event.getX();
                float y = event.getY();
                // 检测触摸是否在 contentContainer 内部
                boolean onContent = x >= contentContainer.getLeft()
                        && x <= contentContainer.getRight()
                        && y >= contentContainer.getTop()
                        && y <= contentContainer.getBottom();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        mOutsideDown = !onContent;
                        return mOutsideDown;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (mOutsideDown) {
                            mOutsideDown = false;
                            collapseAndFinish();
                            return true;
                        }
                        mOutsideDown = false;
                        return false;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        mOutsideDown = false;
                        return true;
                    default:
                        return mOutsideDown;
                }
            }
        });

        rootLayout.addView(contentContainer);

        // 创建独立模糊背景层（插入到 contentContainer 下方）
        createBlurBackgroundLayer();
        
        // 创建背景遮罩（插入到 rootLayout 最底层）
        createBackgroundOverlay();
    }

    private int getAcrylicBgColor() {
        if (windowType == WINDOW_TYPE_SHORTCUT) {
            // 快捷窗口：根据样式决定背景透明度
            if (shortcutStyle == 0) {
                // 不透明样式：白色背景完全不透明
                return isDarkMode() ? Color.parseColor("#2B2B2B") : Color.WHITE;
            } else {
                // 亚克力/液态玻璃：使用叠加色透明度
                int alpha = Math.min(255, Math.max(0, shortcutOverlayAlpha));
                int baseColor = isDarkMode() ? 0xFF2B2B2B : 0xFFFFFFFF;
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                return Color.argb(alpha, r, g, b);
            }
        } else {
            // AI 窗口：根据样式决定背景透明度
            if (aiStyle == 0) {
                // 不透明样式：白色背景完全不透明
                return isDarkMode() ? Color.parseColor("#2B2B2B") : Color.WHITE;
            } else {
                // 亚克力/液态玻璃：使用叠加色透明度（背景透明度）
                int alpha = Math.min(255, Math.max(0, aiOverlayAlpha));
                int baseColor = isDarkMode() ? 0xFF2B2B2B : 0xFFFFFFFF;
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                return Color.argb(alpha, r, g, b);
            }
        }
    }

    private boolean isDarkMode() {
        int nightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    // ─── AI 内容 ───

    @SuppressLint("SetJavaScriptEnabled")
    private void addAiContent(FrameLayout container) {
        // 检查是否有持久化的 WebView
        if (sPersistentWebView == null) {
            // 首次创建，新建 WebView
            sPersistentWebView = new WebView(this.getApplicationContext());
            sPersistentWebView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            
            // Activity 中的 WebView 可以正常接收输入法输入，无需 IME 代理
            WebSettings settings = sPersistentWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
            }

            // 深色模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isDarkMode()) {
                    settings.setForceDark(WebSettings.FORCE_DARK_ON);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        settings.setAlgorithmicDarkeningAllowed(true);
                    }
                }
            }

            // WebChromeClient（权限 + 文件选择）
            sPersistentWebView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    try { request.grant(request.getResources()); } catch (Exception ignored) {}
                }
                
                // Android 5.0+ 文件选择
                @Override
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                                 FileChooserParams fileChooserParams) {
                    openFileChooser(filePathCallback);
                    return true;
                }
            });

            // WebViewClient
            sPersistentWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    // 根据深色模式设置背景色（防止闪白）
                    if (isDarkMode()) {
                        view.setBackgroundColor(Color.parseColor("#111111"));
                    } else {
                        view.setBackgroundColor(Color.WHITE);
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    updateBackButtonState();
                    
                    // 页面加载完成后按顺序注入所有 CSS（确保 DOM 已准备好）
                    // 1. 根据"网页背景透明"设置决定是否注入透明背景 CSS
                    if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && webpageBgTransparent) {
                        injectTransparentBackground(view);
                    }
                    // 2. 再注入自定义 CSS
                    if (windowType == WINDOW_TYPE_AI && !TextUtils.isEmpty(customCss)) {
                        injectCustomCss(view);
                    }
                    // 3. 最后注入深色模式 CSS（文字反色，图片不反色）- 必须最后注入确保不被覆盖
                    injectDarkModeCSS(view);
                    
                    // 注入 JavaScript 检测输入框聚焦
                    injectInputFocusScript(view);
                    
                    // 等待 CSS 生效后设置 WebView 背景
                    view.postDelayed(() -> {
                        // 如果开启了"网页背景透明"，WebView 保持透明
                        // 否则根据 aiOverlayAlpha 决定透明度
                        if (webpageBgTransparent) {
                            view.setBackgroundColor(Color.TRANSPARENT);
                            Log.d(TAG, "页面加载完成，网页背景透明已启用，WebView 背景设为透明");
                        } else {
                            // 未开启网页背景透明，WebView 背景跟随 aiOverlayAlpha
                            if (aiStyle != 0 && aiOverlayAlpha < 255) {
                                int alpha = (int)(aiOverlayAlpha / 255.0f * 255);
                                view.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
                                Log.d(TAG, "页面加载完成，网页背景透明未启用，WebView 背景透明度=" + alpha);
                            } else {
                                view.setBackgroundColor(Color.TRANSPARENT);
                                Log.d(TAG, "页面加载完成，WebView 背景设为透明（不透明样式）");
                            }
                        }
                    }, 50);
                }

                @Override
                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    super.doUpdateVisitedHistory(view, url, isReload);
                    updateBackButtonState();
                }
            });

            // Activity 的 WebView 可以使用原生 ActionMode，无需 JS 选中
            sPersistentWebView.setFocusable(true);
            sPersistentWebView.setFocusableInTouchMode(true);
            
            // 添加 JavaScript 桥接接口用于 blob 下载
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                sPersistentWebView.addJavascriptInterface(new Object() {
                    @android.webkit.JavascriptInterface
                    public void downloadFile(String base64Data, String mimetype) {
                        runOnUiThread(() -> saveBlobFile(base64Data, mimetype));
                    }
                }, "AndroidBridge");
            }

            // WebView 保持透明背景
            sPersistentWebView.setBackgroundColor(Color.TRANSPARENT);
            sPersistentWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            
            // 添加触摸监听器检测输入框聚焦
            setupWebViewInputDetection(sPersistentWebView);
            
            // 首次加载 URL
            Log.d(TAG, "首次创建 WebView，加载 URL: " + aiUrl);
            sPersistentWebView.loadUrl(aiUrl);
        } else {
            // 复用已有 WebView
            Log.d(TAG, "复用已有 WebView，当前 URL: " + sPersistentWebView.getUrl());
            // 从父容器中移除（如果还在其他容器中）
            android.view.ViewParent parent = sPersistentWebView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(sPersistentWebView);
            }
            // 如果是亚克力/液态玻璃模式且开启了网页背景透明，立即注入透明背景 CSS
            if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && webpageBgTransparent) {
                injectTransparentBackground(sPersistentWebView);
            }
            // 注入自定义 CSS
            if (windowType == WINDOW_TYPE_AI && !TextUtils.isEmpty(customCss)) {
                injectCustomCss(sPersistentWebView);
            }
            // 最后注入深色模式 CSS（确保不被覆盖）
            injectDarkModeCSS(sPersistentWebView);
        }
        
        webView = sPersistentWebView;

        // 设置圆角裁剪（连续曲率 Path）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final int crPx = dpToPx(cornerRadius);
            webView.post(() -> {
                int w = webView.getWidth();
                int h = webView.getHeight();
                if (w > 0 && h > 0) {
                    final Path path = buildContinuousRoundRectPath(w, h, crPx);
                    webView.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setConvexPath(path);
                        }
                    });
                    webView.setClipToOutline(true);
                }
            });
        }
        
        // AI 窗口：应用网页透明度设置
        // 当开启"网页背景透明"时，aiOverlayAlpha 仅控制 contentContainer 背景透明度
        // 当关闭"网页背景透明"时，aiOverlayAlpha 同时控制 contentContainer 和 WebView 透明度
        if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && aiOverlayAlpha < 255) {
            if (webpageBgTransparent) {
                Log.d(TAG, "启用亚克力/液态玻璃模式 + 网页背景透明，aiOverlayAlpha 仅控制窗口背景透明度");
            } else {
                Log.d(TAG, "启用亚克力/液态玻璃模式，aiOverlayAlpha 同时控制窗口背景和 WebView 透明度");
            }
        }
        
        // WebView 背景透明度处理
        // 如果开启了"网页背景透明"，WebView 始终保持透明（网页背景通过CSS透明化）
        // 否则 WebView 透明度跟随 aiOverlayAlpha
        if (webpageBgTransparent) {
            webView.setBackgroundColor(Color.TRANSPARENT);
        } else if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && aiOverlayAlpha < 255) {
            int alpha = (int)(aiOverlayAlpha / 255.0f * 255);
            webView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        } else {
            webView.setBackgroundColor(Color.TRANSPARENT);
        }
        
        // 设置下载监听器
        setupDownloadListener();

        container.addView(webView);
    }

    // ─── 快捷方式内容 ───

    private void addShortcutContent(FrameLayout container) {
        ArrayList<ShortcutItem> shortcuts = loadShortcutList();
        if (shortcuts.isEmpty()) {
            shortcuts = ShortcutItem.getDefaultShortcuts();
        }

        int gridPadding = dpToPx(12);

        // 创建 GridLayout
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(GridLayout.UNDEFINED);
        grid.setPadding(gridPadding, gridPadding, gridPadding, gridPadding);

        // 计算均匀分布的单元格宽度
        int containerWidth = container.getLayoutParams().width;
        int maxHeight = container.getLayoutParams().height;
        int cellWidth = (containerWidth - gridPadding * 2) / 4;

        for (final ShortcutItem item : shortcuts) {
            View cell = createShortcutCell(item, cellWidth);
            grid.addView(cell);
        }

        // 测量 grid 自然高度
        grid.measure(
                View.MeasureSpec.makeMeasureSpec(containerWidth - gridPadding * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int contentHeight = grid.getMeasuredHeight();
        // actualHeight 以内容高度为准，超过 maxHeight 则锁定 maxHeight
        int actualHeight = Math.min(contentHeight + gridPadding, maxHeight);

        // 调整容器高度为实际内容高度
        FrameLayout.LayoutParams containerLp = (FrameLayout.LayoutParams) container.getLayoutParams();
        int originalTargetHeight = this.targetHeight;
        this.targetHeight = actualHeight;
        containerLp.height = actualHeight;
        // 底部位置固定：当高度缩小时，将窗口向上偏移 (originalMaxHeight - actualHeight) / 4
        int adjustedTopMargin = targetPosY + (originalTargetHeight - actualHeight) / 4;
        containerLp.topMargin = adjustedTopMargin;
        containerLp.bottomMargin = -adjustedTopMargin;
        container.setLayoutParams(containerLp);
        
        // 【关键修复】快捷窗口需要根据实际高度和位置更新模糊窗口
        // 等待布局完成后获取真实坐标
        container.post(() -> {
            int blurX = container.getLeft();
            int blurY = container.getTop();
            int blurWidth = container.getWidth();
            int blurHeight = container.getHeight();
            try {
                Intent updateIntent = new Intent("UPDATE_BLUR_WINDOW_SIZE");
                updateIntent.setPackage(getPackageName());
                updateIntent.putExtra("blur_x", blurX);
                updateIntent.putExtra("blur_y", blurY);
                updateIntent.putExtra("blur_width", blurWidth);
                updateIntent.putExtra("blur_height", blurHeight);
                updateIntent.putExtra("blur_corner_radius", dpToPx(cornerRadius));
                sendBroadcast(updateIntent);
                Log.d(TAG, "已发送快捷窗口模糊更新: x=" + blurX + " y=" + blurY
                        + " w=" + blurWidth + " h=" + blurHeight + " 圆角=" + cornerRadius + "dp");
            } catch (Exception e) {
                Log.e(TAG, "发送模糊窗口更新失败", e);
            }
        });

        // 添加内容视图（超出最大高度时允许滚动）
        View contentView;
        if (contentHeight + gridPadding > maxHeight) {
            ScrollView scrollView = new ScrollView(this);
            scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.addView(grid);
            contentView = scrollView;
        } else {
            FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            gridParams.gravity = Gravity.CENTER;
            grid.setLayoutParams(gridParams);
            contentView = grid;
        }
        contentView.setBackgroundColor(Color.TRANSPARENT);
        container.addView(contentView);
    }

    private View createShortcutCell(final ShortcutItem item, int cellWidth) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cellWidth;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        cell.setLayoutParams(lp);
        cell.setClickable(true);
        cell.setFocusable(true);

        // 图标大小缩放因子
        final float iconScale = Math.max(0.5f, Math.min(1.5f, shortcutIconSize / 100.0f));
        int iconSize = Math.round((cellWidth - dpToPx(16)) * iconScale);

        if ("image".equals(item.getIconType())) {
            // ===== 图片图标（圆角矩形） =====
            FrameLayout iconContainer = new FrameLayout(this);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER;
            iconContainer.setLayoutParams(iconParams);

            // 圆角矩形背景
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.RECTANGLE);
            iconBg.setCornerRadius(dpToPx(12));

            // 尝试加载自定义图片
            Bitmap bitmap = loadShortcutIcon(item);
            if (bitmap != null) {
                ImageView imageView = new ImageView(this);
                imageView.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageBitmap(bitmap);
                iconContainer.setBackground(iconBg);
                iconContainer.setClipToOutline(true);
                iconContainer.addView(imageView);
            } else {
                // 无自定义图片 → 显示纯色占位图
                iconBg.setColor(getShortcutPlaceholderColor(item.getId()));
                iconContainer.setBackground(iconBg);
                iconContainer.setClipToOutline(true);

                // 在占位图上显示名称首字符
                TextView placeholderText = new TextView(this);
                placeholderText.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                placeholderText.setGravity(Gravity.CENTER);
                String firstChar = item.getName() != null && item.getName().length() > 0
                        ? item.getName().substring(0, 1) : "?";
                placeholderText.setText(firstChar);
                placeholderText.setTextColor(0xFFFFFFFF);
                placeholderText.setTextSize(Math.round(22 * iconScale));
                iconContainer.addView(placeholderText);
            }
            cell.addView(iconContainer);
        } else {
            // ===== Emoji 图标 =====
            TextView iconView = new TextView(this);
            iconView.setText(item.getIcon() != null ? item.getIcon() : "⚡");
            iconView.setTextSize(Math.round(30 * iconScale));
            iconView.setGravity(Gravity.CENTER);
            iconView.setPadding(0, 0, 0, dpToPx(4));
            cell.addView(iconView);
        }

        // 名称
        TextView nameView = new TextView(this);
        nameView.setText(item.getName());
        nameView.setTextSize(Math.max(10, Math.round(11 * iconScale)));
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        if (isDarkMode()) {
            nameView.setTextColor(0xFFCCCCCC);
        } else {
            nameView.setTextColor(0xFF222222);
        }
        nameView.setGravity(Gravity.CENTER);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setMaxLines(1);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dpToPx(4);
        nameView.setLayoutParams(nameParams);
        cell.addView(nameView);

        // 点击事件（使用 ShortcutLaunchConfig 启动，与服务行为一致）
        cell.setOnClickListener(v -> {
            launchShortcutApp(item);
            collapseAndFinish();
        });

        // 按压缩放动画
        cell.setOnTouchListener(new View.OnTouchListener() {
            private boolean mIsPressed = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mIsPressed = true;
                        v.animate().cancel();
                        v.animate().scaleX(0.90f).scaleY(0.90f)
                                .setDuration(120)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (mIsPressed) {
                            mIsPressed = false;
                            v.animate().scaleX(1.0f).scaleY(1.0f)
                                    .setDuration(250)
                                    .setInterpolator(new OvershootInterpolator(2.0f))
                                    .start();
                        }
                        return false;
                }
                return false;
            }
        });

        return cell;
    }

    private Bitmap loadShortcutIcon(ShortcutItem item) {
        if (item == null || item.getIconPath() == null || item.getIconPath().isEmpty()) {
            return null;
        }
        try {
            File file = new File(item.getIconPath());
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "加载快捷方式图标失败: " + item.getName(), e);
        }
        return null;
    }

    private int getShortcutPlaceholderColor(String id) {
        if (id == null) return 0xFF1677FF;
        switch (id) {
            case "alipay_scan":
            case "alipay_pay":
                return 0xFF1677FF;
            case "wechat_scan":
            case "wechat_pay":
                return 0xFF07C160;
            default: {
                int hash = id.hashCode();
                int r = (hash & 0xFF) % 200 + 55;
                int g = ((hash >> 8) & 0xFF) % 200 + 55;
                int b = ((hash >> 16) & 0xFF) % 200 + 55;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private void launchShortcutApp(ShortcutItem item) {
        if (item == null || TextUtils.isEmpty(item.getPackageName())
                || TextUtils.isEmpty(item.getClassName())) {
            Log.d(TAG, "快捷方式包名或类名为空");
            return;
        }
        try {
            Intent launchIntent = createShortcutLaunchIntent(item);
            startActivity(launchIntent);
            Log.d(TAG, "已打开快捷方式: " + item.getName());
        } catch (android.content.ActivityNotFoundException e) {
            Log.w(TAG, "URL Scheme 打开失败，尝试打开应用主界面: " + item.getName(), e);
            try {
                Intent fallbackIntent = new Intent(Intent.ACTION_MAIN);
                fallbackIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                fallbackIntent.setPackage(item.getPackageName());
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallbackIntent);
                Log.d(TAG, "已打开应用主界面: " + item.getName());
            } catch (Exception e2) {
                Log.e(TAG, "打开快捷方式失败: " + item.getName(), e2);
                Toast.makeText(this, "打开 " + item.getName() + " 失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开快捷方式失败: " + item.getName(), e);
            Toast.makeText(this, "打开 " + item.getName() + " 失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Intent createShortcutLaunchIntent(ShortcutItem item) {
        SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        ShortcutLaunchConfig config = ShortcutLaunchConfig.load(prefs, item.getId());
        Intent configIntent = config.buildLaunchIntent();
        if (configIntent != null) {
            return configIntent;
        }
        Intent intent = new Intent();
        intent.setClassName(item.getPackageName(), item.getClassName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private ArrayList<ShortcutItem> loadShortcutList() {
        try {
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            String jsonStr = prefs.getString("shortcut_list", "");
            if (jsonStr.isEmpty()) {
                return ShortcutItem.getDefaultShortcuts();
            }
            ArrayList<ShortcutItem> list = new ArrayList<>();
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                ShortcutItem item = ShortcutItem.fromJson(jsonArray.getJSONObject(i));
                if (item.isEnabled()) {
                    list.add(item);
                }
            }
            return list.isEmpty() ? ShortcutItem.getDefaultShortcuts() : list;
        } catch (Exception e) {
            return ShortcutItem.getDefaultShortcuts();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 按钮
    // ═══════════════════════════════════════════════════════════

    private void addButtons(FrameLayout container) {
        int buttonSize = dpToPx(32);
        int gap = dpToPx(10);
        int rightMargin = dpToPx(10);

        // 关闭按钮（最右侧）
        if (showCloseButton) {
            ImageView closeBtn = createButton(android.R.drawable.ic_menu_close_clear_cancel, "#666666");
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(buttonSize, buttonSize);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.setMargins(dpToPx(10), dpToPx(10), rightMargin, dpToPx(10));
            closeBtn.setLayoutParams(lp);
            closeBtn.setOnClickListener(v -> collapseAndFinish());
            container.addView(closeBtn);
            rightMargin += buttonSize + gap;
        }

        // 刷新按钮
        if (windowType == WINDOW_TYPE_AI && showRefreshButton) {
            ImageView refreshBtn = createButton(android.R.drawable.ic_menu_rotate, "#666666");
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(buttonSize, buttonSize);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.setMargins(dpToPx(10), dpToPx(10), rightMargin, dpToPx(10));
            refreshBtn.setLayoutParams(lp);
            refreshBtn.setOnClickListener(v -> {
                if (webView != null) {
                    webView.reload();
                }
            });
            container.addView(refreshBtn);
            rightMargin += buttonSize + gap;
        }

        // 返回按钮
        if (windowType == WINDOW_TYPE_AI && showBackButton) {
            final ImageView backBtn = createButton(android.R.drawable.ic_menu_revert, "#666666");
            backButtonRef = backBtn;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(buttonSize, buttonSize);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.setMargins(dpToPx(10), dpToPx(10), rightMargin, dpToPx(10));
            backBtn.setLayoutParams(lp);
            backBtn.setOnClickListener(v -> {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    updateBackButtonState();
                }
            });
            container.addView(backBtn);
            updateBackButtonState();
        }
    }

    private ImageView createButton(int iconRes, String tintColor) {
        ImageView btn = new ImageView(this);
        btn.setImageResource(iconRes);
        btn.setColorFilter(Color.parseColor(tintColor));
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#22000000"));
        btn.setBackground(bg);
        return btn;
    }

    private void updateBackButtonState() {
        if (backButtonRef != null && webView != null) {
            backButtonRef.setAlpha(webView.canGoBack() ? 1.0f : 0.3f);
        }
    }

    /**
     * 获取系统导航栏高度（像素）
     * 通过系统资源 ID 获取，适用于有虚拟导航栏或手势导航条的场景
     *
     * @return 导航栏高度（px），若不存在则返回 0
     */
    private int getNavigationBarHeight() {
        android.content.res.Resources resources = getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════
    // 展开动画（Choreographer 驱动）
    // ═══════════════════════════════════════════════════════════

    private void performExpandAnimation() {
        // 计算初始变换
        initScaleX = (float) startWidth / targetWidth;
        initScaleY = (float) startHeight / targetHeight;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) contentContainer.getLayoutParams();
        initTranslationX = startX - 2 * lp.leftMargin;
        initTranslationY = startY - 2 * lp.topMargin - getNavigationBarHeight()/2;

        // 限制最小比例，防止除零
        if (initScaleX <= 0) initScaleX = 0.01f;
        if (initScaleY <= 0) initScaleY = 0.01f;

        contentContainer.setPivotX(targetWidth / 2f);
        contentContainer.setPivotY(targetHeight / 2f);
        contentContainer.setScaleX(initScaleX);
        contentContainer.setScaleY(initScaleY);
        contentContainer.setTranslationX(initTranslationX);
        contentContainer.setTranslationY(initTranslationY);
        contentContainer.setAlpha(0f);
        contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // blurBackgroundView 同步初始状态
        if (blurBackgroundView != null) {
            blurBackgroundView.setPivotX(targetWidth / 2f);
            blurBackgroundView.setPivotY(targetHeight / 2f);
            blurBackgroundView.setScaleX(initScaleX);
            blurBackgroundView.setScaleY(initScaleY);
            blurBackgroundView.setTranslationX(initTranslationX);
            blurBackgroundView.setTranslationY(initTranslationY);
            blurBackgroundView.setAlpha(0f);
        }

        if (webView != null) {
            webView.setAlpha(0f);
        }

        isExpanded = true;

        final long startNanos = System.nanoTime();

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (isCollapsing || isFinishing()) return;

                float progress = (float) (frameTimeNanos - startNanos) / 1_000_000f / animDuration;
                progress = Math.min(progress, 1.0f);
                float eased = expandInterpolator.getInterpolation(progress);

                float curScaleX = initScaleX + (1f - initScaleX) * eased;
                float curScaleY = initScaleY + (1f - initScaleY) * eased;
                float curTransX = initTranslationX * (1f - eased);
                float curTransY = initTranslationY * (1f - eased);

                contentContainer.setAlpha(1f);
                contentContainer.setScaleX(curScaleX);
                contentContainer.setScaleY(curScaleY);
                contentContainer.setTranslationX(curTransX);
                contentContainer.setTranslationY(curTransY);
                
                // 背景遮罩同步渐入（与窗口动画同时）
                if (overlayView != null) {
                    float targetAlpha = overlayAlpha / 100.0f;
                    overlayView.setAlpha(targetAlpha * eased);
                }

                // blurBackgroundView 同步动画
                if (blurBackgroundView != null) {
                    blurBackgroundView.setAlpha(1f);
                    blurBackgroundView.setScaleX(curScaleX);
                    blurBackgroundView.setScaleY(curScaleY);
                    blurBackgroundView.setTranslationX(curTransX);
                    blurBackgroundView.setTranslationY(curTransY);
                }

                // WebView 后 50% 渐入
                if (webView != null) {
                    if (progress < 0.5f) {
                        webView.setAlpha(0f);
                    } else {
                        float alphaProgress = (progress - 0.5f) * 2f;
                        // AI 窗口：最终透明度根据"网页背景透明"设置决定
                        float targetAlpha = 1.0f;
                        if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && aiOverlayAlpha < 255) {
                            // 如果开启了"网页背景透明"，WebView 始终保持100%不透明（网页内容通过CSS透明化）
                            // 否则 WebView 透明度跟随 aiOverlayAlpha
                            if (!webpageBgTransparent) {
                                targetAlpha = aiOverlayAlpha / 255.0f;
                            }
                        }
                        webView.setAlpha(targetAlpha * easeOutCubic(alphaProgress));
                    }
                }
                
                // 当动画进度达到70%时，通知模糊窗口开始渐变显示
                if (progress >= 0.6f && !blurWindowFadeInSent) {
                    blurWindowFadeInSent = true;
                    sendBlurWindowFadeInBroadcast(progress);
                    Log.d(TAG, "展开动画进度达到70%，已发送模糊窗口渐变显示广播");
                }

                if (progress < 1.0f) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    // 完成
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);
                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (blurBackgroundView != null) {
                        blurBackgroundView.setScaleX(1f);
                        blurBackgroundView.setScaleY(1f);
                        blurBackgroundView.setTranslationX(0f);
                        blurBackgroundView.setTranslationY(0f);
                    }
                    if (webView != null) {
                        // AI 窗口：应用网页透明度设置
                        // 如果开启了"网页背景透明"，WebView 保持100%不透明
                        // 否则 WebView 透明度跟随 aiOverlayAlpha
                        if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && aiOverlayAlpha < 255) {
                            if (!webpageBgTransparent) {
                                webView.setAlpha(aiOverlayAlpha / 255.0f);
                            } else {
                                webView.setAlpha(1.0f);
                            }
                        } else {
                            webView.setAlpha(1f);
                        }
                        webView.invalidate();
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // 收起动画 + finish
    // ═══════════════════════════════════════════════════════════

    private void collapseAndFinish() {
        if (!isExpanded || isCollapsing) return;
        isCollapsing = true;
        isExpanded = false;
        
        // 通知模糊窗口开始渐变消失（在动画开始前立即发送）
        sendBlurWindowFadeOutBroadcast();
        Log.d(TAG, "collapseAndFinish: 已发送模糊窗口渐变消失广播");

        final float endScaleX = initScaleX;
        final float endScaleY = initScaleY;
        final float endTransX = initTranslationX;
        final float endTransY = initTranslationY;
        final long startNanos = System.nanoTime();

        contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                float progress = (float) (frameTimeNanos - startNanos) / 1_000_000f / animDuration;
                progress = Math.min(progress, 1.0f);
                float eased = animInterpolator.getInterpolation(progress);

                float curScaleX = 1f + (endScaleX - 1f) * eased;
                float curScaleY = 1f + (endScaleY - 1f) * eased;
                float curTransX = endTransX * eased;
                float curTransY = endTransY * eased;

                // 末尾淡出：最后 20% 进度渐隐
                float containerAlpha = 1.0f;
                if (progress > 0.7f) {
                    float fadeProgress = (progress - 0.7f) / 0.3f;
                    containerAlpha = 1.0f - easeOutCubic(fadeProgress);
                }
                contentContainer.setAlpha(containerAlpha);
                
                // 背景遮罩同步渐出（与窗口动画同时）
                if (overlayView != null) {
                    float targetAlpha = overlayAlpha / 100.0f;
                    overlayView.setAlpha(targetAlpha * (1.0f - eased));
                }

                contentContainer.setScaleX(curScaleX);
                contentContainer.setScaleY(curScaleY);
                contentContainer.setTranslationX(curTransX);
                contentContainer.setTranslationY(curTransY);

                // blurBackgroundView 同步收起动画
                if (blurBackgroundView != null) {
                    blurBackgroundView.setAlpha(containerAlpha);
                    blurBackgroundView.setScaleX(curScaleX);
                    blurBackgroundView.setScaleY(curScaleY);
                    blurBackgroundView.setTranslationX(curTransX);
                    blurBackgroundView.setTranslationY(curTransY);
                }

                // WebView 前 50% 渐出
                if (webView != null) {
                    if (progress < 0.5f) {
                        float alphaProgress = progress * 2f;
                        webView.setAlpha(1f - easeOutCubic(alphaProgress));
                    } else {
                        webView.setAlpha(0f);
                    }
                }

                if (progress < 1.0f) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    // 动画完成，先隐藏内容防止闪烁
                    contentContainer.setVisibility(View.INVISIBLE);
                    if (blurBackgroundView != null) {
                        blurBackgroundView.setVisibility(View.INVISIBLE);
                    }
                    
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);
                    contentContainer.setAlpha(1f);
                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (blurBackgroundView != null) {
                        blurBackgroundView.setScaleX(1f);
                        blurBackgroundView.setScaleY(1f);
                        blurBackgroundView.setTranslationX(0f);
                        blurBackgroundView.setTranslationY(0f);
                        blurBackgroundView.setAlpha(1f);
                    }
                    if (webView != null) {
                        webView.setAlpha(0f);
                    }
                    sendRestoreBarBroadcast();
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // 广播
    // ═══════════════════════════════════════════════════════════

    private void sendRestoreBarBroadcast() {
        try {
            Intent intent = new Intent("com.example.aiwindow.RESTORE_BAR");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }
    
    private void sendBlurWindowFadeOutBroadcast() {
        try {
            Intent intent = new Intent("BLUR_WINDOW_FADE_OUT");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
            Log.d(TAG, "已发送模糊窗口渐变消失广播");
        } catch (Exception e) {
            Log.e(TAG, "发送模糊窗口渐变消失广播失败", e);
        }
    }
    
    /**
     * 发送模糊窗口渐变显示广播
     * @param progress 当前动画进度（0.0-1.0）
     */
    private void sendBlurWindowFadeInBroadcast(float progress) {
        try {
            Intent intent = new Intent("BLUR_WINDOW_FADE_IN");
            intent.setPackage(getPackageName());
            intent.putExtra("progress", progress);
            sendBroadcast(intent);
            Log.d(TAG, "已发送模糊窗口渐变显示广播，progress=" + progress);
        } catch (Exception e) {
            Log.e(TAG, "发送模糊窗口渐变显示广播失败", e);
        }
    }
    
    /**
     * 发送模糊层平移广播（让 BlurWindowActivity 同步移动）
     * @param translationY 内容窗口的 Y 方向平移量
     */
    private void sendBlurWindowTranslateBroadcast(float translationY) {
        try {
            Intent intent = new Intent("BLUR_WINDOW_TRANSLATE");
            intent.setPackage(getPackageName());
            intent.putExtra("translation_y", translationY);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "发送模糊窗口平移广播失败", e);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerRestoreReceiver() {
        restoreBarReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received RESTORE_BAR broadcast");
            }
        };
        IntentFilter filter = new IntentFilter("com.example.aiwindow.RESTORE_BAR");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(restoreBarReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(restoreBarReceiver, filter);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int parseColorWithAlpha(String colorHex, float alpha) {
        try {
            int color = Color.parseColor(colorHex);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            int alphaInt = Math.round(alpha * 255);
            return Color.argb(alphaInt, red, green, blue);
        } catch (Exception e) {
            return Color.parseColor("#4CAF50");
        }
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    /**
     * 构建连续曲率圆角 Path（与 Service 中 SMOOTH_FACTOR=0.65 一致）
     */
    private static Path buildContinuousRoundRectPath(int w, int h, float r) {
        Path path = new Path();
        if (w <= 0 || h <= 0) return path;
        float radius = Math.min(r, Math.min(w, h) / 2f);
        if (radius <= 0) {
            path.addRect(0, 0, w, h, Path.Direction.CW);
            return path;
        }
        float c = radius * SMOOTH_FACTOR;
        path.moveTo(radius, 0);
        path.lineTo(w - radius, 0);
        path.cubicTo(w - radius + c, 0, w, radius - c, w, radius);
        path.lineTo(w, h - radius);
        path.cubicTo(w, h - radius + c, w - radius + c, h, w - radius, h);
        path.lineTo(radius, h);
        path.cubicTo(radius - c, h, 0, h - radius + c, 0, h - radius);
        path.lineTo(0, radius);
        path.cubicTo(0, radius - c, radius - c, 0, radius, 0);
        path.close();
        return path;
    }

    // ═══════════════════════════════════════════════════════════
    // 亚克力圆角矩形 Drawable
    // ═══════════════════════════════════════════════════════════

    /**
     * 连续曲率圆角矩形 Drawable，半透明底色透出窗口模糊背景，
     * 实现亚克力/液态玻璃效果
     */
    private static class AcrylicRoundRectDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float cornerRadius;
        private int width, height;
        private final float density;

        AcrylicRoundRectDrawable(int fillColor, float cornerRadiusPx, float density) {
            this.density = density;
            fillPaint.setColor(fillColor);
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.TRANSPARENT);
            strokePaint.setStrokeWidth(0);
            this.cornerRadius = cornerRadiusPx;
            int glassHeight = Math.round(80 * density);
            // 玻璃反射渐变：顶部淡淡高光到底部透明
            LinearGradient glassGradient = new LinearGradient(0, 0, 0, glassHeight,
                    Color.argb(35, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP);
            glassPaint.setShader(glassGradient);
        }

        void setStroke(int widthPx, int color) {
            strokePaint.setStrokeWidth(widthPx);
            strokePaint.setColor(color);
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            this.width = bounds.width();
            this.height = bounds.height();
            buildPath();
        }

        private void buildPath() {
            path.reset();
            if (width <= 0 || height <= 0) return;
            float r = Math.min(cornerRadius, Math.min(width, height) / 2f);
            if (r <= 0) {
                path.addRect(0, 0, width, height, Path.Direction.CW);
                return;
            }
            float c = r * SMOOTH_FACTOR;
            float w = width;
            float h = height;

            path.moveTo(r, 0);
            path.lineTo(w - r, 0);
            path.cubicTo(w - r + c, 0, w, r - c, w, r);
            path.lineTo(w, h - r);
            path.cubicTo(w, h - r + c, w - r + c, h, w - r, h);
            path.lineTo(r, h);
            path.cubicTo(r - c, h, 0, h - r + c, 0, h - r);
            path.lineTo(0, r);
            path.cubicTo(0, r - c, r - c, 0, r, 0);
            path.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            // 1. 填充半透明底色（透出模糊背景）
            canvas.drawPath(path, fillPaint);
            // 2. 绘制玻璃反射高光
            canvas.save();
            canvas.clipPath(path);
            canvas.drawRect(0, 0, width, Math.min(height, Math.round(80 * density)), glassPaint);
            canvas.restore();
            // 3. 绘制边框
            if (strokePaint.getStrokeWidth() > 0
                    && strokePaint.getColor() != Color.TRANSPARENT) {
                canvas.drawPath(path, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            glassPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            glassPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 输入法避让功能
    // ═══════════════════════════════════════════════════════════

    /**
     * 输入法出现/消失时的平移动画（参照 FloatWindowService 实现）
     *
     * @param show           true=输入法出现（窗口上移），false=输入法消失（窗口恢复）
     * @param detectedHeight 检测到的输入法高度（像素）- 仅用于判断是否显示，不用于计算偏移
     */
    private void animateForKeyboard(final boolean show, final int detectedHeight) {
        if (contentContainer == null) return;

        final float startTranslationY = contentContainer.getTranslationY();
        // 始终使用设置项中的固定避让高度，不根据检测到的键盘高度动态调整
        final int offset = keyboardOffset;
        // 在 Gravity.CENTER 下，topMargin 改变 d 像素等价于视觉移动 2*d 像素
        // 因此 translationY 的变化量也需要用 2*offset，以保持相同的避让幅度
        final float targetTranslationY = show ? (-2f * offset) : 0f;

        // 如果已在目标位置，无需动画
        if (startTranslationY == targetTranslationY) return;

        Log.d(TAG, "Keyboard anim: show=" + show + " startTranslationY=" + startTranslationY
                + " targetTranslationY=" + targetTranslationY + " offset=" + offset
                + " detectedHeight=" + detectedHeight);

        isKeyboardAnimating = true;
        final long startTime = System.nanoTime();
        final int duration = keyboardAnimDuration;
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                try {
                    long elapsed = (frameTimeNanos - startTime) / 1000000; // ns -> ms
                    float progress = Math.min((float) elapsed / duration, 1.0f);
                    float easedProgress = easeOutCubic(progress);

                    float currentTranslationY = startTranslationY + (targetTranslationY - startTranslationY) * easedProgress;
                    contentContainer.setTranslationY(currentTranslationY);
                    
                    // 同步通知模糊层平移
                    sendBlurWindowTranslateBroadcast(currentTranslationY);

                    if (progress < 1.0f) {
                        Choreographer.getInstance().postFrameCallback(this);
                    } else {
                        isKeyboardAnimating = false;
                        Log.d(TAG, "Keyboard anim complete: translationY=" + currentTranslationY);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Keyboard anim frame error", e);
                    isKeyboardAnimating = false;
                }
            }
        });
    }

    /**
     * 安全兜底：直接恢复窗口位置
     */
    private void resetKeyboardOffset() {
        // 正在执行键盘动画时不要打断
        if (isKeyboardAnimating) {
            return;
        }
        if (contentContainer == null) return;
        try {
            if (contentContainer.getTranslationY() != 0f) {
                contentContainer.setTranslationY(0f);
                Log.d(TAG, "resetKeyboardOffset: translationY -> 0");
            }
        } catch (Exception e) {
            Log.e(TAG, "resetKeyboardOffset failed", e);
        }
        isKeyboardAnimating = false;
    }

    /**
     * 设置 WebView 输入框检测（参照 FloatWindowService）
     */
    private void setupWebViewInputDetection(WebView webView) {
        // 不需要 OnTouchListener，由 JavaScript 注入的 focus/blur 事件来处理
        // 移除原有的触摸监听器，避免每次点击都触发输入法切换
        Log.d(TAG, "WebView input detection: using JS injection only");
    }

    /**
     * WebView 焦点变化时的处理
     * 注意：这个方法只应该由 JavaScript 注入的 focus/blur 事件调用，不应该由触摸事件触发
     */
    private void onWebViewFocusedChanged(boolean focused) {
        if (focused && !isKeyboardVisible) {
            // 输入框聚焦且键盘未显示时，弹出输入法
            Log.d(TAG, "Input field focused, showing keyboard");
            isKeyboardVisible = true;
            try {
                android.view.inputmethod.InputMethodManager imm = 
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0);
                    Log.d(TAG, "IME shown");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to show IME", e);
            }
            animateForKeyboard(true, 0);
        } else if (!focused && isKeyboardVisible) {
            // 输入框失焦，恢复窗口位置
            Log.d(TAG, "Input field blurred, hiding keyboard");
            onWebViewFocusedChanged(false, true);
        }
    }

    /**
     * WebView 焦点变化处理（可控制是否隐藏键盘）
     */
    private void onWebViewFocusedChanged(boolean focused, boolean hideKeyboard) {
        if (!focused && hideKeyboard) {
            isKeyboardVisible = false;
            // 隐藏输入法
            try {
                android.view.inputmethod.InputMethodManager imm = 
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
                    Log.d(TAG, "IME hidden");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide IME", e);
            }
            animateForKeyboard(false, 0);
            resetKeyboardOffset();
        }
    }

    /**
     * 使用 OnGlobalLayoutListener 检测屏幕可见区域变化来推断输入法状态
     */
    private void setupKeyboardListener(View rootView) {
        if (rootView == null) return;
        
        keyboardLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int lastKeyboardHeight = -1; // 初始值设为 -1，表示尚未初始化
            
            @Override
            public void onGlobalLayout() {
                android.graphics.Rect r = new android.graphics.Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keyboardHeight = screenHeight - r.bottom;
                
                // 首次初始化：记录初始高度但不触发动画
                if (lastKeyboardHeight == -1) {
                    lastKeyboardHeight = keyboardHeight;
                    Log.d(TAG, "Keyboard listener initialized: initial height=" + keyboardHeight);
                    return;
                }
                
                // 去抖：变化量小于 50px 忽略
                if (Math.abs(keyboardHeight - lastKeyboardHeight) < 50) return;
                lastKeyboardHeight = keyboardHeight;
                
                boolean shown = keyboardHeight > screenHeight * 0.15f;
                
                if (shown && !isKeyboardVisible) {
                    Log.d(TAG, "Keyboard shown: height=" + keyboardHeight);
                    isKeyboardVisible = true;
                    animateForKeyboard(true, keyboardHeight);
                } else if (!shown && isKeyboardVisible) {
                    Log.d(TAG, "Keyboard hidden detected");
                    isKeyboardVisible = false;
                    animateForKeyboard(false, 0);
                }
            }
        };
        
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
        Log.d(TAG, "Keyboard listener set up");
    }
    
    /**
     * 移除输入法检测监听器
     */
    private void removeKeyboardListener() {
        if (keyboardLayoutListener != null && rootLayout != null) {
            try {
                rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
                Log.d(TAG, "Keyboard listener removed");
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove keyboard listener", e);
            }
        }
        keyboardLayoutListener = null;
    }

    /**
     * 注入 JavaScript 检测输入框聚焦事件
     */
    private void injectInputFocusScript(WebView webView) {
        String js = "(function(){" +
                "var inputs = document.querySelectorAll('input, textarea, [contenteditable]');" +
                "for(var i=0;i<inputs.length;i++){" +
                "  (function(el){" +
                "    el.addEventListener('focus', function() {" +
                "      if(window.AndroidBridge) AndroidBridge.onInputFocus();" +
                "    });" +
                "    el.addEventListener('blur', function() {" +
                "      if(window.AndroidBridge) AndroidBridge.onInputBlur();" +
                "    });" +
                "  })(inputs[i]);" +
                "}" +
                "})()";
        webView.evaluateJavascript(js, null);
        Log.d(TAG, "Injected input focus script");
    }
    
    /**
     * 注入透明背景 CSS（仅用于亚克力/液态玻璃模式）
     * 让网页背景透明，但保持所有元素（文字、图片等）100% 不透明
     */
    private void injectTransparentBackground(WebView webView) {
        // 强大的 CSS：先清除所有元素背景，再恢复内容元素
        String css = "/* 第一步：清除所有元素背景 */" +
                "* {" +
                "  background-color: transparent !important;" +
                "  background-image: none !important;" +
                "  background: none !important;" +
                "}" +
                "/* 第二步：恢复 html/body 基本样式 */" +
                "html, body {" +
                "  background: transparent !important;" +
                "}";
        
        // 如果有自定义 CSS，追加到后面
        if (!TextUtils.isEmpty(customCss)) {
            css += "\n/* 自定义 CSS */\n" + customCss;
        }
        
        String js = "(function(){" +
                // 注入 CSS
                "var style = document.createElement('style');" +
                "style.id = 'transparent-bg-style';" +
                "style.textContent = '" + css.replace("'", "\\'") + "';" +
                "if (document.head) {" +
                "  document.head.appendChild(style);" +
                "} else {" +
                "  document.documentElement.appendChild(style);" +
                "}" +
                
                "console.log('Transparent background CSS injected');" +
                "return 'CSS injection complete';" +
                "})()";
        
        webView.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "Transparent background injection result: " + value);
                // 注意：不在这里设置透明背景，让页面保持深色/浅色背景直到加载完成
                // WebView 背景会在 onPageFinished 后由其他逻辑处理
            }
        });
        Log.d(TAG, "Injected transparent background CSS with wildcard selector");
    }
    
    /**
     * 注入 CSS filter 实现深色模式
     * 使用 filter: invert(0.85) hue-rotate(180deg) 反转页面颜色，
     * 与硬编码颜色不同，它保留页面原有设计关系，图片双重反转保持正常显示。
     */
    private void injectDarkModeCSS(WebView view) {
        String js;
        if (isDarkMode()) {
            // 注入 CSS filter 实现深色模式（同时设置背景色防止闪白）
            js = "(function(){" +
                    "var s=document.getElementById('__dark_mode_filter');" +
                    "if(!s){" +
                    "s=document.createElement('style');" +
                    "s.id='__dark_mode_filter';" +
                    "s.textContent='html{-webkit-filter:invert(0.85)hue-rotate(180deg);filter:invert(0.85)hue-rotate(180deg);background-color:#111!important}' +" +
                    "'img,video,canvas,svg,[style*=\"background-image\"]{-webkit-filter:invert(0.85)hue-rotate(180deg);filter:invert(0.85)hue-rotate(180deg)}' +" +
                    "':root{color-scheme:dark!important}';" +
                    "var h=document.head;if(h){h.appendChild(s);}else{document.documentElement.appendChild(s);}" +
                    "}" +
                    "})()";
        } else {
            // 浅色模式：移除之前注入的深色 filter
            js = "(function(){" +
                    "var s=document.getElementById('__dark_mode_filter');" +
                    "if(s){s.parentNode.removeChild(s);}" +
                    "})()";
        }
    
        try {
            // 同步执行，确保 CSS 立即生效
            view.evaluateJavascript(js, null);
            Log.d(TAG, isDarkMode() ? "Dark mode CSS filter injected" : "Dark mode CSS filter removed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to " + (isDarkMode() ? "inject" : "remove") + " dark mode CSS filter", e);
        }
    }
    
    /**
     * 注入自定义 CSS（无论是否透明模式都注入）
     */
    private void injectCustomCss(WebView webView) {
        if (TextUtils.isEmpty(customCss)) return;
        
        String js = "(function(){" +
                "var style = document.createElement('style');" +
                "style.id = 'custom-css-style';" +
                "style.textContent = '" + customCss.replace("'", "\\'") + "';" +
                "if (document.head) {" +
                "  document.head.appendChild(style);" +
                "} else {" +
                "  document.documentElement.appendChild(style);" +
                "}" +
                "console.log('Custom CSS injected');" +
                "return 'Custom CSS injection complete';" +
                "})()";
        
        webView.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "Custom CSS injection result: " + value);
            }
        });
        Log.d(TAG, "Injected custom CSS");
    }
    
    /**
     * 设置下载监听器
     */
    private void setupDownloadListener() {
        webView.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                try {
                    // 检查是否为 blob URL
                    if (url.startsWith("blob:")) {
                        // 对于 blob URL，需要通过 JavaScript 下载
                        downloadBlobUrl(url, contentDisposition, mimetype);
                        return;
                    }
                    
                    // 普通 HTTP/HTTPS URL 使用 DownloadManager
                    android.net.Uri uri = android.net.Uri.parse(url);
                    String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype);
                    
                    android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(uri);
                    request.setTitle(fileName);
                    request.setDescription("正在下载..." );
                    request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.setAllowedOverMetered(true);
                    request.setAllowedOverRoaming(true);
                    
                    // 设置 MIME 类型
                    if (mimetype != null && !mimetype.isEmpty()) {
                        request.setMimeType(mimetype);
                    }
                    
                    // 添加 HTTP headers
                    request.addRequestHeader("User-Agent", userAgent);
                    
                    android.app.DownloadManager downloadManager = 
                        (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    downloadManager.enqueue(request);
                    
//                    Toast.makeText(OverlayWindowActivity.this,
//                        "开始下载: " + fileName, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Download started: " + fileName);
                } catch (Exception e) {
                    Log.e(TAG, "Download failed", e);
                    Toast.makeText(OverlayWindowActivity.this, 
                        "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    
    /**
     * 下载 blob URL 文件
     */
    private void downloadBlobUrl(String blobUrl, String contentDisposition, String mimetype) {
        String js = "(function() {" +
                "fetch('" + blobUrl.replace("'", "\\'") + "')" +
                ".then(response => response.blob())" +
                ".then(blob => {" +
                "  const reader = new FileReader();" +
                "  reader.onload = function() {" +
                "    const base64Data = reader.result.split(',')[1];" +
                "    AndroidBridge.downloadFile(base64Data, '" + mimetype + "');" +
                "  };" +
                "  reader.readAsDataURL(blob);" +
                "})" +
                ".catch(err => console.error('Blob download failed:', err));" +
                "})()";
        
        webView.evaluateJavascript(js, null);
//        Toast.makeText(this, "正在处理 Blob 下载...", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 保存 blob 数据为文件
     */
    private void saveBlobFile(String base64Data, String mimetype) {
        try {
            // 解码 base64 数据
            byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            
            // 根据 MIME 类型生成文件名
            String extension = getFileExtensionFromMimeType(mimetype);
            String fileName = "blob_" + System.currentTimeMillis() + "." + extension;
            
            // 保存到 Downloads 目录
            java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File file = new java.io.File(downloadsDir, fileName);
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(data);
            fos.close();
            
            // 更新媒体库
            android.media.MediaScannerConnection.scanFile(
                    this, new String[]{file.getAbsolutePath()}, null, null);
            
            runOnUiThread(() -> {
                Toast.makeText(this, "已保存: " + fileName, Toast.LENGTH_LONG).show();
            });
            
            Log.d(TAG, "Blob file saved: " + fileName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save blob file", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }
    
    /**
     * 根据 MIME 类型获取文件扩展名
     */
    private String getFileExtensionFromMimeType(String mimetype) {
        if (mimetype == null || mimetype.isEmpty()) return "bin";
        
        switch (mimetype.toLowerCase()) {
            case "application/pdf": return "pdf";
            case "image/jpeg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "text/plain": return "txt";
            case "application/zip": return "zip";
            case "application/x-zip-compressed": return "zip";
            case "application/msword": return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
            case "application/vnd.ms-excel": return "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "xlsx";
            default:
                // 从 MIME 类型中提取扩展名
                String[] parts = mimetype.split("/");
                return parts.length > 1 ? parts[1] : "bin";
        }
    }
    
    // 文件选择回调
    private ValueCallback<Uri[]> mFilePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    
    /**
     * 打开文件选择器
     */
    private void openFileChooser(ValueCallback<Uri[]> filePathCallback) {
        mFilePathCallback = filePathCallback;
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        
        try {
            startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file chooser", e);
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
                mFilePathCallback = null;
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (mFilePathCallback == null) return;
            
            Uri[] results = null;
            
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        }
    }
}
