package com.example.aiwindow;

import static android.webkit.WebSettings.FORCE_DARK_OFF;
import static android.webkit.WebSettings.FORCE_DARK_ON;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
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
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
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
import androidx.core.content.FileProvider;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    // 顶部小横条透明度
    public static final String EXTRA_TOP_BAR_TRANSPARENCY = "top_bar_transparency";

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
    private int topBarTransparency = 40; // 顶部小横条背景透明度 0-100
    private boolean isTopBarExpanded = false;
    private boolean closeOnOutside, backPrioritizeWebview;
    private String aiUrl;
    private int shortcutIconSize, shortcutStyle, shortcutBlurRadius, shortcutOverlayAlpha;

    // ─── ColorOS 风格效果开关（默认开启，下次打开窗口生效） ───
    private boolean shortcutEdgeRefraction = true;   // 边缘折射光
    private boolean shortcutFingerGlow = true;       // 手指光晕跟随
    private boolean shortcutDragStretch = true;      // 拖动灵动拉伸回弹
    
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
//    private View blurBackgroundView; // 独立模糊背景层（仅显示模糊桌面，不含子内容）
    private View overlayView; // 背景遮罩视图
    private WebView webView;
    private ImageView backButtonRef;
    // 顶部小横条/按钮组
    private FrameLayout topBarView;
    private LinearLayout topBarButtonRow;
    private int topBarExpandedW, topBarExpandedH; // 展开尺寸（px）
    private int topBarCollapsedW, topBarCollapsedH; // 收起尺寸（px）
    private boolean isExpanded = false;
    private boolean isCollapsing = false;
    private boolean isExpandAnimating = false; // 展开动画进行中（期间禁入手势）
    private BroadcastReceiver restoreBarReceiver;

    // ─── ColorOS 风格效果 ───
    private FingerGlowView fingerGlowView;          // 手指光晕层（contentContainer 最上层）
    private WindowGestureController gestureController; // 拖动拉伸/光晕手势控制器
    
    // ─── WebView 状态保持 ───
    private static WebView sPersistentWebView; // 静态 WebView，跨 Activity 生命周期保持
    private static String sLoadedAiUrl = null; // 记录 WebView 实际加载的初始 AI URL（用于判断设置中的 URL 是否被用户修改，不受页面内部导航影响）
    private static boolean sWebViewClientInitialized = false; // ← 新增：标记 Client 是否已初始化
    
    // ─── 模糊窗口渐变标志 ───
    private boolean blurWindowFadeInSent = false; // 标记是否已发送模糊窗口渐变显示广播
    private boolean blurFadeOutStarted = false; // 标记收起动画中是否已触发模糊淡出
    // ─── 文件上传相关 ───
    private boolean mIsStartingFileChooser = false; // 标记是否正在启动文件选择器
    private static ValueCallback<Uri[]> sPendingUploadCallback; // 待投递的 WebView 回调
    private static Uri[] sPendingFileUris; // 待投递的文件 URI

    // ─── 输入法避让相关 ───
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private boolean isKeyboardVisible = false;
    private int keyboardOffset = 397; // 默认上移距离（px），从 SharedPreferences 加载
    private int keyboardAnimDuration = 250; // 平移动画时长（ms）
    private boolean isKeyboardAnimating = false;

    // ─── 动画初始值 ───
    private float initScaleX, initScaleY;
    private float initTranslationX, initTranslationY;


    // ─── 待启动快捷方式（收起动画完成后启动，避免 startActivity 立即触发 onUserLeaveHint 中断动画） ───
    private ShortcutItem pendingShortcutItem = null;

    // ─── 屏幕尺寸（解析度无关像素，但 getRealMetrics 是 px） ───
    private int screenWidth, screenHeight;

    // ═══════════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
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

        // 4. 创建根布局（TouchObserverFrameLayout：旁路观察所有触摸事件，供手势控制器使用）
        rootLayout = new TouchObserverFrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.TRANSPARENT);
        rootLayout.setClipChildren(false);
        rootLayout.setClickable(true);
        setContentView(rootLayout);
        // 初始隐藏根布局，避免系统默认的 Activity 转场淡入动画导致窗口透明度渐变
        rootLayout.setVisibility(View.INVISIBLE);

        // 5. 创建内容视图
        createContent();

        // 6. 注册广播
        registerRestoreReceiver();

        // 7. 为AI窗口添加WebView触摸监听（点击网页收起展开的小横条）
        if (windowType == WINDOW_TYPE_AI) {
            contentContainer.post(() -> {
                if (webView != null) {
                    webView.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            if (isTopBarExpanded) {
                                collapseTopBar();
                            }
                        }
                        return false;
                    });
                }
            });
        }

        // 8. 返回键处理
        // 如果小横条处于展开状态，先收回小横条再执行返回
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isTopBarExpanded) {
                    collapseTopBar();
                    return;
                }
                if (windowType == WINDOW_TYPE_AI && backPrioritizeWebview
                        && webView != null && webView.canGoBack()) {
                    webView.goBack();
                    updateBackButtonState();
                    return;
                }
                collapseAndFinish();
            }
        });

        // 9. 布局完成后，延迟执行展开动画
        contentContainer.postDelayed(this::performExpandAnimation, 180);

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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: sPendingUploadCallback=" + (sPendingUploadCallback != null ? "SET" : "null")
                + ", mIsStartingFileChooser was=" + mIsStartingFileChooser);
        mIsStartingFileChooser = false;
        // 有挂起的文件上传回调 → 用 evaluateJavascript 探测 WebView 渲染器是否就绪
        // 渲染器响应后才投递回调，避免 Handler.post 时序不可靠导致的竞态条件
        if (sPendingUploadCallback != null) {
            final ValueCallback<Uri[]> cb = sPendingUploadCallback;
            final Uri[] uris = sPendingFileUris;
            sPendingUploadCallback = null;
            sPendingFileUris = null;
            Log.d(TAG, "onResume: probing WebView renderer before callback, uris=" + (uris != null ? uris.length : 0));

            if (sPersistentWebView != null) {
                sPersistentWebView.evaluateJavascript("1+1",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            Log.d(TAG, "Renderer probe returned: " + value + ", now delivering callback");
                            try {
                                cb.onReceiveValue(uris);
                                Log.d(TAG, "WebView callback invoked successfully (after renderer probe)");
                            } catch (Exception e) {
                                Log.e(TAG, "Error invoking WebView callback", e);
                            }
                        }
                    });
            } else {
                try {
                    cb.onReceiveValue(uris);
                    Log.d(TAG, "WebView callback invoked (no persistent WebView)");
                } catch (Exception e) {
                    Log.e(TAG, "Error invoking WebView callback", e);
                }
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        Log.d(TAG, "onUserLeaveHint: mIsStartingFileChooser=" + mIsStartingFileChooser
                + ", isFinishing=" + isFinishing());
        // 用户主动离开（Home键/最近任务/底部上滑手势）→ 销毁窗口
        if (!mIsStartingFileChooser && !isFinishing()) {
            if (rootLayout != null) rootLayout.setVisibility(View.GONE);
            BlurWindowActivity.fadeOutIfAlive();
            sendRestoreBarBroadcast();
            Log.d(TAG, "onUserLeaveHint: finishing activity");
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: mIsStartingFileChooser=" + mIsStartingFileChooser
                + ", isFinishing=" + isFinishing()
                + ", sPendingUploadCallback=" + (sPendingUploadCallback != null ? "SET" : "null"));
        if (!mIsStartingFileChooser && !isFinishing()) {
            if (rootLayout != null) rootLayout.setVisibility(View.GONE);
            BlurWindowActivity.fadeOutIfAlive();
            sendRestoreBarBroadcast();
            Log.d(TAG, "onStop: finishing activity");
            finish();
            overridePendingTransition(0, 0);
        }
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

            // ColorOS 风格效果开关（直接读 SharedPreferences，同输入法避让的加载模式）
            loadShortcutEffectSettings();
        }

        // 读取顶部小横条透明度
        topBarTransparency = intent.getIntExtra(EXTRA_TOP_BAR_TRANSPARENCY, 40);

        // 加载背景遮罩设置（所有窗口类型）
        loadOverlaySettings();
    }

    /**
     * 从 SharedPreferences 加载 ColorOS 风格效果开关（仅快捷方式窗口）
     */
    private void loadShortcutEffectSettings() {
        try {
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            shortcutEdgeRefraction = prefs.getBoolean("shortcut_edge_refraction", true);
            shortcutFingerGlow = prefs.getBoolean("shortcut_finger_glow", true);
            shortcutDragStretch = prefs.getBoolean("shortcut_drag_stretch", true);
        } catch (Exception e) {
            Log.e(TAG, "加载快捷方式效果开关失败", e);
        }
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
        bg.setBrightnessFactor(0.9f);
        // 边缘折射光（上下亮、左右暗，仅快捷方式窗口）
        bg.setRefractionEnabled(windowType == WINDOW_TYPE_SHORTCUT && shortcutEdgeRefraction);
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
            // AI 窗口添加顶部小横条按钮组
            addTopBar(contentContainer);
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

        // ColorOS 风格效果：手势控制器（观察全部触点）+ 手指光晕层（仅快捷方式窗口）
        if (windowType == WINDOW_TYPE_SHORTCUT) {
            gestureController = new WindowGestureController();
            if (shortcutFingerGlow) {
                fingerGlowView = new FingerGlowView(this);
                contentContainer.addView(fingerGlowView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
    }

//    private int getAcrylicBgColor() {
//        if (windowType == WINDOW_TYPE_SHORTCUT) {
//            // 快捷窗口：根据样式决定背景透明度
//            if (shortcutStyle == 0) {
//                // 不透明样式：白色背景完全不透明
//                return isDarkMode() ? Color.parseColor("#2B2B2B") : Color.WHITE;
//            } else {
//                // 亚克力/液态玻璃：使用叠加色透明度
//                int alpha = Math.min(255, Math.max(0, shortcutOverlayAlpha));
//                int baseColor = isDarkMode() ? 0xFF2B2B2B : 0xFFFFFFFF;
//                int r = (baseColor >> 16) & 0xFF;
//                int g = (baseColor >> 8) & 0xFF;
//                int b = baseColor & 0xFF;
//                return Color.argb(alpha, r, g, b);
//            }
//        } else {
//            // AI 窗口：根据样式决定背景透明度
//            if (aiStyle == 0) {
//                // 不透明样式：白色背景完全不透明
//                return isDarkMode() ? Color.parseColor("#2B2B2B") : Color.WHITE;
//            } else {
//                // 亚克力/液态玻璃：使用叠加色透明度（背景透明度）
//                int alpha = Math.min(255, Math.max(0, aiOverlayAlpha));
//                int baseColor = isDarkMode() ? 0xFF2B2B2B : 0xFFFFFFFF;
//                int r = (baseColor >> 16) & 0xFF;
//                int g = (baseColor >> 8) & 0xFF;
//                int b = baseColor & 0xFF;
//                return Color.argb(alpha, r, g, b);
//            }
//        }
//    }
    private int getAcrylicBgColor() {
        // 背景本身完全不透明，透明度由 Drawable.setAlpha 控制
        return isDarkMode() ? 0xFF2B2B2B : 0xFFFFFFFF;
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
            // ===== 首次创建：新建 WebView 并初始化所有设置 =====
            sPersistentWebView = new WebView(this.getApplicationContext());
            sPersistentWebView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                settings.setForceDark(FORCE_DARK_OFF);
            }

            // ===== 设置 WebChromeClient（权限 + 文件选择） =====
            sPersistentWebView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    try { request.grant(request.getResources()); } catch (Exception ignored) {}
                }

                @Override
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                                 FileChooserParams fileChooserParams) {
                    openFileChooser(filePathCallback, fileChooserParams);
                    return true;
                }
            });

            // ===== 设置 WebViewClient =====
            sPersistentWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    updateBackButtonState();
                    applyCssAndBackground(view);  // 统一调用
                }

                @Override
                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    super.doUpdateVisitedHistory(view, url, isReload);
                    updateBackButtonState();
                }
            });

            // 其它 WebView 基础设置
            sPersistentWebView.setFocusable(true);
            sPersistentWebView.setFocusableInTouchMode(true);
            sPersistentWebView.setBackgroundColor(Color.TRANSPARENT);
            sPersistentWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // 标记 Client 已初始化
            sWebViewClientInitialized = true;

            // 首次加载 URL
            Log.d(TAG, "首次创建 WebView，加载 URL: " + aiUrl);
            sPersistentWebView.loadUrl(aiUrl);
            sLoadedAiUrl = aiUrl;

        } else {
            // ===== 复用已有 WebView（跳过 Client 设置） =====
            Log.d(TAG, "复用已有 WebView，URL: " + sPersistentWebView.getUrl());
            android.view.ViewParent parent = sPersistentWebView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(sPersistentWebView);
            }

            // 关键修复：比较设置中的 AI URL 与上次实际加载的初始 URL
            // - 用户修改了 URL → 重新加载新 URL（sLoadedAiUrl 记录初始 URL，不受页面内部导航/重定向影响，避免误刷新）
            // - 未修改 URL（即使点击了保存）→ 保留上次退出时的页面状态，不清除缓存
            if (aiUrl != null && !aiUrl.isEmpty()
                    && (sLoadedAiUrl == null || !sLoadedAiUrl.equals(aiUrl))) {
                Log.d(TAG, "AI URL 已修改，从 " + sLoadedAiUrl + " 重新加载到 " + aiUrl);
                sPersistentWebView.loadUrl(aiUrl);
                sLoadedAiUrl = aiUrl;
            }

            // 不需要重新设置 WebChromeClient / WebViewClient，因为已经在首次创建时设置好了
            // 只需更新 CSS/背景（因为页面可能已经变化，或者需要应用当前的透明/暗色设置）
            applyCssAndBackground(sPersistentWebView);
        }

        // 将 webView 引用指向静态实例
        webView = sPersistentWebView;

        // 设置圆角裁剪（每次复用都需要重新应用，因为尺寸可能变化）
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

        // 设置下载监听器（复用已有 WebView 时也需要重新设置，因为 DownloadListener 会持有 Activity 引用）
        setupDownloadListener();

        // 将 WebView 添加到容器
        container.addView(webView);
    }

    /**
     * 对 WebView 应用当前的 CSS 和背景设置（透明模式、深色模式、自定义 CSS）
     * 在页面加载完成或复用 WebView 时调用
     */
    private void applyCssAndBackground(WebView view) {
        if (view == null) return;

        // 1. 透明背景 CSS（仅 AI 窗口且亚克力/液态玻璃模式）
        if (windowType == WINDOW_TYPE_AI && aiStyle != 0) {
            if (webpageBgTransparent) {
                injectTransparentBackground(view);
            } else {
                removeTransparentBackground(view);
            }
        }

        // 2. 深色模式 CSS（文字反色）
        injectDarkModeCSS(view);

        // 3. 自定义 CSS
        if (windowType == WINDOW_TYPE_AI && !TextUtils.isEmpty(customCss)) {
            injectCustomCss(view);
        }

        // 4. 设置 WebView 背景透明度
        if (webpageBgTransparent) {
            view.setBackgroundColor(Color.TRANSPARENT);
            Log.d(TAG, "网页背景透明已启用，WebView 背景透明");
        } else if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && aiOverlayAlpha < 255) {
            int alpha = (int)(aiOverlayAlpha / 255.0f * 255);
            view.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
            Log.d(TAG, "WebView 背景透明度=" + alpha);
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
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
        // 注意：将应用启动延迟到收起动画完成后，避免 startActivity 立即触发 onUserLeaveHint 中断动画
        cell.setOnClickListener(v -> {
            // 拖动过窗口则抑制本次点击，避免拖动松手时误启动应用
            if (gestureController != null && gestureController.dragMoved) return;
            if (windowType == WINDOW_TYPE_SHORTCUT) {
                // 设置兜底标志位：即使进程冻结导致 fadeOutAndFinish 的 Handler 动画未执行，
                // 进程恢复后 BlurWindowActivity.onResume 也会检测到此标志并自销毁
                BlurWindowActivity.sShortcutLaunched = true;
                BlurWindowActivity.fadeOutIfAlive();
            }
            pendingShortcutItem = item;
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
        if (item == null || TextUtils.isEmpty(item.getPackageName())) {
            Log.d(TAG, "快捷方式包名为空");
            return;
        }
        try {
            Intent launchIntent = createShortcutLaunchIntent(item);
            // 检查是否使用系统小窗打开
            SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            ShortcutLaunchConfig config = ShortcutLaunchConfig.load(prefs, item.getId());
            if (config.openInWindow) {
                Bundle bundle = new Bundle();
                bundle.putInt("android.activity.windowingMode", 100);
                startActivity(launchIntent, bundle);
            } else {
                Bundle bundle = new Bundle();
                bundle.putInt("android.activity.windowingMode", 1);
                startActivity(launchIntent, bundle);
            }
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

        // 如果类名为空，使用包名启动（类似单击提示条的方式）
        if (TextUtils.isEmpty(item.getClassName())) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(item.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                return intent;
            }
            // 降级方案：使用 CATEGORY_LAUNCHER
            Intent fallbackIntent = new Intent(Intent.ACTION_MAIN);
            fallbackIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            fallbackIntent.setPackage(item.getPackageName());
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return fallbackIntent;
        }

        // 有类名时使用显式组件启动
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

    private void addTopBar(FrameLayout container) {
        // 只有AI窗口需要顶部小横条
        if (windowType != WINDOW_TYPE_AI) return;

        // 统计启用的按钮数量
        boolean hasClose = showCloseButton;
        boolean hasRefresh = showRefreshButton;
        boolean hasBack = showBackButton;
        int activeCount = (hasClose ? 1 : 0) + (hasRefresh ? 1 : 0) + (hasBack ? 1 : 0);
        if (activeCount == 0) return; // 所有按钮都关闭时不显示小横条

        final float density = getResources().getDisplayMetrics().density;
        final int btnSize = dpToPx(32);
        final int pad = dpToPx(8);
        final int gap = dpToPx(10);
        topBarCollapsedW = Math.round(60 * density);
        topBarCollapsedH = Math.round(6 * density);

        // 计算展开尺寸
        int btnsWidth = activeCount * btnSize + Math.max(0, activeCount - 1) * gap;
        topBarExpandedW = btnsWidth + pad * 2;
        topBarExpandedH = btnSize + pad * 2;
        final int expandedCorner = Math.round(10 * density);

        // ── 主容器（LayoutParams始终为展开尺寸，收起时通过scale缩小） ──
        topBarView = new FrameLayout(this);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(topBarExpandedW, topBarExpandedH);
        barLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        barLp.topMargin = dpToPx(8);
        topBarView.setLayoutParams(barLp);
        topBarView.setClipChildren(false);

        // 背景Drawable（灰色半透明圆角矩形）
        int bgAlpha255 = (int)(Math.min(100, Math.max(0, topBarTransparency)) * 255.0f / 100.0f);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setShape(GradientDrawable.RECTANGLE);
        barBg.setColor(Color.argb(bgAlpha255, 0x88, 0x88, 0x88));
        topBarView.setBackground(barBg);

        // ── 展开状态的按钮行 ──
        topBarButtonRow = new LinearLayout(this);
        topBarButtonRow.setPadding(pad,pad,pad,pad);
        topBarButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        topBarButtonRow.setGravity(Gravity.CENTER_VERTICAL);

        // 从左到右：返回、刷新、关闭
        if (hasBack) {
            ImageView backBtn = createTopBarButton(android.R.drawable.ic_menu_revert);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            if (hasRefresh || hasClose) lp.rightMargin = gap;
            backBtn.setLayoutParams(lp);
            backBtn.setOnClickListener(v -> {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    updateBackButtonState();
                }
            });
            backButtonRef = backBtn;
            topBarButtonRow.addView(backBtn);
        } else {
            backButtonRef = null;
        }

        if (hasRefresh) {
            ImageView refreshBtn = createTopBarButton(android.R.drawable.ic_menu_rotate);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            if (hasClose) lp.rightMargin = gap;
            refreshBtn.setLayoutParams(lp);
            refreshBtn.setOnClickListener(v -> {
                if (webView != null) webView.reload();
            });
            topBarButtonRow.addView(refreshBtn);
        }

        if (hasClose) {
            ImageView closeBtn = createTopBarButton(android.R.drawable.ic_menu_close_clear_cancel);
            closeBtn.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
            closeBtn.setOnClickListener(v -> collapseAndFinish());
            topBarButtonRow.addView(closeBtn);
        }

        topBarView.addView(topBarButtonRow);

        // ── 初始化为收起状态 ──
        // 使用 GradientDrawable.setCornerRadii 分别设置 x/y 预失真圆角，
        // 补偿非等比 scale：rx = (collapsedH/2)/initSx, ry = (collapsedH/2)/initSy
        // 经 scale 后视觉 rx=ry=collapsedH/2，呈纯正胶囊形。
        topBarView.setPivotX(topBarExpandedW / 2f);
        topBarView.setPivotY(0);
        float initSx = (float) topBarCollapsedW / topBarExpandedW;
        float initSy = (float) topBarCollapsedH / topBarExpandedH;
        topBarView.setScaleX(initSx);
        topBarView.setScaleY(initSy);
        float collapsedRX = (topBarCollapsedH / 2f) / initSx;
        float collapsedRY = (topBarCollapsedH / 2f) / initSy;
        barBg.setCornerRadii(new float[]{
                collapsedRX, collapsedRY,
                collapsedRX, collapsedRY,
                collapsedRX, collapsedRY,
                collapsedRX, collapsedRY
        });
        topBarButtonRow.setAlpha(0f);
        topBarButtonRow.setVisibility(View.INVISIBLE);
        isTopBarExpanded = false;

        // ── 点击小横条切换展开/收起 ──
        topBarView.setOnClickListener(v -> {
            if (isTopBarExpanded) {
                collapseTopBar();
            } else {
                expandTopBar();
            }
        });

        // ── 为 WebView 添加顶部边距，避免小横条遮挡网页内容 ──
        // 让小横条区域不重叠 WebView，方便点击小横条不误触网页
        if (webView != null) {
            FrameLayout.LayoutParams wvLp = (FrameLayout.LayoutParams) webView.getLayoutParams();
            wvLp.topMargin = dpToPx(8);
            webView.setLayoutParams(wvLp);
        }

        container.addView(topBarView);

        // ── 扩展小横条触控区域（收起时可视区域极小，难以点击） ──
        // 添加透明覆盖层，触控区域向外扩展 dpToPx(8)；展开状态下不拦截事件，让触摸穿透到下方按钮
        int extPx = dpToPx(8);
        View barTouchExtender = new View(this);
        FrameLayout.LayoutParams extLp = new FrameLayout.LayoutParams(
                topBarCollapsedW + extPx * 2,
                topBarCollapsedH + extPx * 2
        );
        extLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        extLp.topMargin = dpToPx(8) - extPx;
        barTouchExtender.setLayoutParams(extLp);
        barTouchExtender.setClickable(false);
        barTouchExtender.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (isTopBarExpanded) {
                    return false; // 展开时不拦截，让事件穿透到下方按钮
                } else {
                    expandTopBar();
                    return true;  // 收起时消费事件，触发展开
                }
            }
            return isTopBarExpanded ? false : true;
        });
        container.addView(barTouchExtender);
    }

    private ImageView createTopBarButton(int iconRes) {
        ImageView btn = new ImageView(this);
        btn.setImageResource(iconRes);
        btn.setColorFilter(Color.parseColor("#DDDDDD"));
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#33000000"));
        btn.setBackground(bg);
        return btn;
    }

    private void expandTopBar() {
        if (isTopBarExpanded) return;
        isTopBarExpanded = true;

        // scale展开，同时 GradientDrawable 圆角从预失真值过渡到展开圆角
        topBarView.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(250)
                .setInterpolator(new OvershootInterpolator(0.5f))
                .start();

        GradientDrawable bg = (GradientDrawable) topBarView.getBackground();
        if (bg != null) {
            float sx = (float) topBarCollapsedW / topBarExpandedW;
            float sy = (float) topBarCollapsedH / topBarExpandedH;
            float collapsedRX = (topBarCollapsedH / 2f) / sx;
            float collapsedRY = (topBarCollapsedH / 2f) / sy;
            float expandedR = dpToPx(10);
            ValueAnimator cornerAnim = ValueAnimator.ofFloat(0f, 1f);
            cornerAnim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float rx = collapsedRX + (expandedR - collapsedRX) * t;
                float ry = collapsedRY + (expandedR - collapsedRY) * t;
                bg.setCornerRadii(new float[]{rx, ry, rx, ry, rx, ry, rx, ry});
            });
            cornerAnim.setDuration(250).start();
        }

        topBarButtonRow.setVisibility(View.VISIBLE);
        topBarButtonRow.animate()
                .alpha(1f).setDuration(250).start();

        updateBackButtonState();
    }

    private void collapseTopBar() {
        if (!isTopBarExpanded) return;
        isTopBarExpanded = false;

        float sx = (float) topBarCollapsedW / topBarExpandedW;
        float sy = (float) topBarCollapsedH / topBarExpandedH;

        // scale缩小，同时 GradientDrawable 圆角从展开圆角过渡到预失真值
        topBarView.animate()
                .scaleX(sx).scaleY(sy)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        GradientDrawable bg = (GradientDrawable) topBarView.getBackground();
        if (bg != null) {
            float collapsedRX = (topBarCollapsedH / 2f) / sx;
            float collapsedRY = (topBarCollapsedH / 2f) / sy;
            float expandedR = dpToPx(10);
            ValueAnimator cornerAnim = ValueAnimator.ofFloat(0f, 1f);
            cornerAnim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float rx = expandedR + (collapsedRX - expandedR) * t;
                float ry = expandedR + (collapsedRY - expandedR) * t;
                bg.setCornerRadii(new float[]{rx, ry, rx, ry, rx, ry, rx, ry});
            });
            cornerAnim.setDuration(250).start();
        }

        topBarButtonRow.animate()
                .alpha(0f).setDuration(150)
                .withEndAction(() -> topBarButtonRow.setVisibility(View.INVISIBLE))
                .start();
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
//        initScaleY = (float) startHeight / targetHeight;
        initScaleY = 0.01f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) contentContainer.getLayoutParams();
        initTranslationX = startX - 2 * lp.leftMargin;
        initTranslationY = startY - 2 * lp.topMargin - getNavigationBarHeight()/2;

        // 展开动画即将开始，恢复根布局可见性
        // 之前隐藏是为了避免系统 Activity 转场淡入动画导致窗口透明度渐变
        rootLayout.setVisibility(View.VISIBLE);

        // 限制最小比例，防止除零
        if (initScaleX <= 0) initScaleX = 0.01f;
        if (initScaleY <= 0) initScaleY = 0.01f;

        contentContainer.setPivotX(targetWidth / 2f);
        contentContainer.setPivotY(targetHeight / 2f);
        contentContainer.setScaleX(initScaleX);
        contentContainer.setScaleY(initScaleY);
        contentContainer.setTranslationX(initTranslationX);
        contentContainer.setTranslationY(initTranslationY);

        contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // blurBackgroundView 同步初始状态
//        if (blurBackgroundView != null) {
//            blurBackgroundView.setPivotX(targetWidth / 2f);
//            blurBackgroundView.setPivotY(targetHeight / 2f);
//            blurBackgroundView.setScaleX(initScaleX);
//            blurBackgroundView.setScaleY(initScaleY);
//            blurBackgroundView.setTranslationX(initTranslationX);
//            blurBackgroundView.setTranslationY(initTranslationY);
//        }

        if (webView != null) {
            webView.setAlpha(0f);
        }

        isExpanded = true;
        isExpandAnimating = true;

        // 缓存背景 Drawable 和目标透明度，避免每帧重复获取
        final Drawable expandBg = contentContainer.getBackground();
        final AcrylicRoundRectDrawable expandAcrylicBg = (expandBg instanceof AcrylicRoundRectDrawable) ? (AcrylicRoundRectDrawable) expandBg : null;
        final int expandTargetAlpha = (windowType == WINDOW_TYPE_AI) ? aiOverlayAlpha : shortcutOverlayAlpha;

        final long startNanos = System.nanoTime();

        // 在 doFrame 回调外部（但仍在方法内）声明局部数组：
        final float[] lastDrawableAlpha = { -1f };
        final float[] lastOverlayAlpha = { -1f };
        final float[] lastWebAlpha = { -1f };

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (isCollapsing || isFinishing()) return;

                float progress = (float) (frameTimeNanos - startNanos) / 1_000_000f / animDuration;
                progress = Math.max(0f, Math.min(progress, 1.0f));
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
                    float targetAlpha = overlayAlpha / 100.0f * eased;
                    if (Math.abs(targetAlpha - lastOverlayAlpha[0]) > 0.01f) {
                        overlayView.setAlpha(targetAlpha);
                        lastOverlayAlpha[0] = targetAlpha;
                    }
                }

                // blurBackgroundView 同步动画
//                if (blurBackgroundView != null) {
//                    blurBackgroundView.setAlpha(1f);
//                    blurBackgroundView.setScaleX(curScaleX);
//                    blurBackgroundView.setScaleY(curScaleY);
//                    blurBackgroundView.setTranslationX(curTransX);
//                    blurBackgroundView.setTranslationY(curTransY);
//                }

                // 计算当前背景透明度
                if (expandAcrylicBg != null) {
                    int currentAlpha;
                    if (progress <= 0.6f) {
                        currentAlpha = 255; // 前60%完全不透明
                    } else {
                        float t = (progress - 0.6f) / 0.4f;
                        currentAlpha = (int)(255 + (expandTargetAlpha - 255) * t);
                    }
                    if (Math.abs(currentAlpha - lastDrawableAlpha[0]) >= 10) {
                        expandAcrylicBg.updateFillColorAndAlpha(currentAlpha, expandTargetAlpha);
                        lastDrawableAlpha[0] = currentAlpha;
                    }
                }

                // WebView 后 50% 渐入
                if (webView != null) {
                    float targetWebAlpha;
                    if (progress < 0.5f) {
                        targetWebAlpha = 0f;
                    } else {
                        float p = (progress - 0.5f) * 2f;
                        // 保留原有的透明度计算逻辑
                        float base = easeOutCubic(p);
                        if (windowType == WINDOW_TYPE_AI && aiStyle != 0 && aiOverlayAlpha < 255 && !webpageBgTransparent) {
                            targetWebAlpha = (aiOverlayAlpha / 255.0f) * base;
                        } else {
                            targetWebAlpha = base;
                        }
                    }
                    if (Math.abs(targetWebAlpha - lastWebAlpha[0]) > 0.01f) {
                        webView.setAlpha(targetWebAlpha);
                        lastWebAlpha[0] = targetWebAlpha;
                    }
                }

                // 当动画进度达到60%时，通知模糊窗口开始渐变显示
                if (progress >= 0.6f && !blurWindowFadeInSent) {
                    blurWindowFadeInSent = true;
                    sendBlurWindowFadeInBroadcast(progress);
                    Log.d(TAG, "展开动画进度达到60%，已发送模糊窗口渐变显示广播");
                }

                if (progress < 1.0f) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    // 完成
                    isExpandAnimating = false;
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);

                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
//                    if (blurBackgroundView != null) {
//                        blurBackgroundView.setScaleX(1f);
//                        blurBackgroundView.setScaleY(1f);
//                        blurBackgroundView.setTranslationX(0f);
//                        blurBackgroundView.setTranslationY(0f);
//                    }
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
                    if (expandAcrylicBg != null) {
                        expandAcrylicBg.updateFillColorAndAlpha(expandTargetAlpha, expandTargetAlpha);
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
        isExpandAnimating = false;

        // 交出变换控制权：取消拖动/回弹（不重置当前 transform，收起动画从当前值开始）
        if (gestureController != null) {
            gestureController.cancelAll();
        }

        // 从当前变换状态插值到收起目标（拖动/回弹中途触发收起时避免视觉跳变；
        // 静止时 start 值即 scale=1/translation=0，行为与原来一致）
        final float startScaleX = contentContainer.getScaleX();
        final float startScaleY = contentContainer.getScaleY();
        final float startTransX = contentContainer.getTranslationX();
        final float startTransY = contentContainer.getTranslationY();
        final float endScaleX = initScaleX;
        final float endScaleY = initScaleY;
        final float endTransX = initTranslationX;
        final float endTransY = initTranslationY;
        final long startNanos = System.nanoTime();

        // 缓存背景 Drawable 和目标透明度，避免每帧重复获取
        final Drawable bg = contentContainer.getBackground();
        final AcrylicRoundRectDrawable acrylicBg = (bg instanceof AcrylicRoundRectDrawable) ? (AcrylicRoundRectDrawable) bg : null;
        final int collapseTargetAlpha = (windowType == WINDOW_TYPE_AI) ? aiOverlayAlpha : shortcutOverlayAlpha;

        contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        blurFadeOutStarted = false;

        // ===== 优化：添加阈值跟踪，减少不必要的属性更新 =====
        final float[] lastDrawableAlpha = { -1f };
        final float[] lastOverlayAlpha = { -1f };
        final float[] lastWebAlpha = { -1f };

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                // 只触发一次模糊淡出，避免重复
                if (!blurFadeOutStarted) {
                    blurFadeOutStarted = true;
                    BlurWindowActivity.fadeOutIfAlive();
                }

                float progress = (float) (frameTimeNanos - startNanos) / 1_000_000f / animDuration;
                progress = Math.max(0f, Math.min(progress, 1.0f));
                float eased = animInterpolator.getInterpolation(progress);

                float curScaleX = startScaleX + (endScaleX - startScaleX) * eased;
                float curScaleY = startScaleY + (endScaleY - startScaleY) * eased;
                float curTransX = startTransX + (endTransX - startTransX) * eased;
                float curTransY = startTransY + (endTransY - startTransY) * eased;

                // 末尾淡出：最后 30% 进度渐隐
                float containerAlpha = 1.0f;
                if (progress > 0.7f) {
                    float fadeProgress = (progress - 0.7f) / 0.3f;
                    containerAlpha = 1.0f - easeOutCubic(fadeProgress);
                }
                contentContainer.setAlpha(containerAlpha);

                // ===== 优化：背景遮罩阈值更新 =====
                if (overlayView != null) {
                    float overlayTargetAlpha = overlayAlpha / 100.0f;
                    float currentOverlayAlpha = overlayTargetAlpha * (1.0f - eased);
                    if (Math.abs(currentOverlayAlpha - lastOverlayAlpha[0]) > 0.01f) {
                        overlayView.setAlpha(currentOverlayAlpha);
                        lastOverlayAlpha[0] = currentOverlayAlpha;
                    }
                }

                contentContainer.setScaleX(curScaleX);
                contentContainer.setScaleY(curScaleY);
                contentContainer.setTranslationX(curTransX);
                contentContainer.setTranslationY(curTransY);

                // ===== 优化：Drawable 透明度阈值更新 =====
                if (acrylicBg != null) {
                    int currentAlpha;
                    if (progress <= 0.3f) {
                        float t = progress / 0.3f;
                        currentAlpha = (int)(collapseTargetAlpha + (255 - collapseTargetAlpha) * t);
                    } else {
                        currentAlpha = 255; // 后70%保持完全不透明
                    }
                    if (Math.abs(currentAlpha - lastDrawableAlpha[0]) >= 10) {
                        acrylicBg.updateFillColorAndAlpha(currentAlpha, collapseTargetAlpha);
                        lastDrawableAlpha[0] = currentAlpha;
                    }
                }

                // ===== 优化：WebView 透明度阈值更新 =====
                if (webView != null) {
                    float targetWebAlpha;
                    if (progress < 0.5f) {
                        float alphaProgress = progress * 2f;
                        targetWebAlpha = 1f - easeOutCubic(alphaProgress);
                    } else {
                        targetWebAlpha = 0f;
                    }
                    // 钳位到合法范围
                    targetWebAlpha = Math.max(0f, Math.min(1f, targetWebAlpha));
                    if (Math.abs(targetWebAlpha - lastWebAlpha[0]) > 0.01f) {
                        webView.setAlpha(targetWebAlpha);
                        lastWebAlpha[0] = targetWebAlpha;
                    }
                }

                if (progress < 1.0f) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    // 动画完成，隐藏内容防止闪烁
                    contentContainer.setVisibility(View.INVISIBLE);
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);
                    contentContainer.setAlpha(1f);

                    // ===== 优化：恢复硬件层 =====
                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);

                    if (webView != null) {
                        webView.setAlpha(0f);
                    }
                    // 如果有待启动的快捷方式，在此启动
                    if (pendingShortcutItem != null) {
                        launchShortcutApp(pendingShortcutItem);
                        pendingShortcutItem = null;
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

    private float dpToPxF(float dp) {
        return dp * getResources().getDisplayMetrics().density;
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
        private final Paint darkOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // 边缘折射光：多层描边由宽到窄、由暗到亮叠加，内侧形成渐变过渡（无明显分界）
        // 边缘折射光：20 层平滑衰减描边（替代旧 5 层固定描边，消除肉眼可见的同心台阶）
        private static final int REFRACTION_LAYERS = 20;
        private static final float REFRACTION_BAND_DP = 8f;      // 边缘光可见带宽
        private static final float REFRACTION_PEAK = 0.42f;      // 顶部边缘目标复合不透明度（透出底层颜色）
        private static final float REFRACTION_FALLOFF = 1.8f;    // 衰减曲线指数
        private final Paint[] refractionLayerPaints = new Paint[REFRACTION_LAYERS];
        private final float[] refractionBaseAlpha = new float[REFRACTION_LAYERS];
        private boolean refractionEnabled = false;
        private final Path path = new Path();
        private float cornerRadius;
        private int width, height;
        private final float density;

        // 黑色遮罩层的最大透明度（展开/收起动画中调节亮度用，替代修改颜色值方案）
        private int mMaxOverlayAlpha = 26; // 默认对应亮度因子 0.9

        AcrylicRoundRectDrawable(int fillColor, float cornerRadiusPx, float density) {
            this.density = density;
            fillPaint.setColor(fillColor);
            darkOverlayPaint.setColor(Color.BLACK);
            darkOverlayPaint.setStyle(Paint.Style.FILL);
            darkOverlayPaint.setAlpha(0);
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
            // 折射光平滑衰减描边：20 层由宽到窄叠加，每层 alpha 按目标曲线反解，
            // 使复合结果逼近 PEAK·(1-d/B)^FALLOFF；开 dither 消除竖向渐变的量化色带
            // sTop：折射渐变顶部（最亮处）的 shader 不透明度系数，反解时折算进去
            float sTop = 110f / 255f;
            float prefix = 1f; // Π(1 - a·sTop) 前缀积
            for (int i = 0; i < REFRACTION_LAYERS; i++) {
                float target = REFRACTION_PEAK
                        * (float) Math.pow((i + 1f) / REFRACTION_LAYERS, REFRACTION_FALLOFF);
                float layerEff = 1f - (1f - target) / prefix;
                prefix = 1f - target;
                float baseAlpha = layerEff / sTop * 255f;
                refractionBaseAlpha[i] = baseAlpha;
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * REFRACTION_BAND_DP * density
                        * (REFRACTION_LAYERS - i) / REFRACTION_LAYERS);
                p.setAlpha(Math.round(baseAlpha));
                refractionLayerPaints[i] = p;
            }
        }

        void setRefractionEnabled(boolean enabled) {
            refractionEnabled = enabled;
            invalidateSelf();
        }

        private void buildRefractionShader() {
            if (height <= 0) return;
            // 顶部/底部亮、中部（即左右竖边所在区域）暗 → 模拟玻璃片边缘折射
            LinearGradient refraction = new LinearGradient(0, 0, 0, height,
                    new int[]{
                            Color.argb(110, 255, 255, 255),
                            Color.argb(35, 255, 255, 255),
                            Color.argb(10, 255, 255, 255),
                            Color.argb(35, 255, 255, 255),
                            Color.argb(110, 255, 255, 255)},
                    new float[]{0f, 0.14f, 0.5f, 0.86f, 1f},
                    Shader.TileMode.CLAMP);
            for (Paint p : refractionLayerPaints) {
                if (p != null) p.setShader(refraction);
            }
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
            buildRefractionShader();
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
            // 2. 黑色遮罩层（展开/收起动画中调节亮度用，仅修改 alpha 比修改颜色值性能更高）
            if (darkOverlayPaint.getAlpha() > 0) {
                canvas.drawPath(path, darkOverlayPaint);
            }
            // 3. 绘制玻璃反射高光
            canvas.save();
            canvas.clipPath(path);
            canvas.drawRect(0, 0, width, Math.min(height, Math.round(80 * density)), glassPaint);
            canvas.restore();
            // 4. 边缘折射光（玻璃片边缘：上下横边亮、左右竖边几乎不可见）
            // 20 层平滑衰减描边由宽到窄叠加，最外圈峰值 42%、过渡连续无台阶
            if (refractionEnabled) {
                canvas.save();
                canvas.clipPath(path);
                for (Paint p : refractionLayerPaints) {
                    if (p != null && p.getShader() != null) {
                        canvas.drawPath(path, p);
                    }
                }
                canvas.restore();
            }
            // 5. 绘制边框
            if (strokePaint.getStrokeWidth() > 0
                    && strokePaint.getColor() != Color.TRANSPARENT) {
                canvas.drawPath(path, strokePaint);
            }
        }

        // ---- 设置亮度因子（转换为黑色遮罩最大透明度） ----
        void setBrightnessFactor(float factor) {
            float clamped = Math.max(0.1f, Math.min(1.0f, factor));
            mMaxOverlayAlpha = Math.round((1.0f - clamped) * 255);
        }

        // ---- 更新背景透明度和黑色遮罩透明度（替代原来修改颜色值方案，性能更高） ----
        void updateFillColorAndAlpha(int alpha, int targetAlpha) {
            // 设置填充底色透明度
            fillPaint.setAlpha(alpha);
            // 计算黑色遮罩透明度：alpha=255 时遮罩最浓，alpha=targetAlpha 时遮罩消失
            int darkAlpha = mMaxOverlayAlpha;
            if (targetAlpha >= 255 || alpha <= targetAlpha) {
                darkAlpha = 0;
            } else if (alpha >= 255) {
                darkAlpha = mMaxOverlayAlpha;
            } else {
                darkAlpha = mMaxOverlayAlpha * (alpha - targetAlpha) / (255 - targetAlpha);
            }
            darkOverlayPaint.setAlpha(darkAlpha);
            invalidateSelf();
        }

//        @Override
//        public void setAlpha(int alpha) {
//            // 只修改边框和玻璃高光的透明度，填充色由 updateFillColorAndAlpha 单独控制
//            strokePaint.setAlpha(alpha);
//            glassPaint.setAlpha(alpha);
//            // 不修改 fillPaint
//        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);    // strokePaint 的透明度保持 setStroke 时设置的值，不在此修改
            glassPaint.setAlpha(alpha);
            for (int i = 0; i < refractionLayerPaints.length; i++) {
                refractionLayerPaints[i].setAlpha(
                        Math.round(alpha * refractionBaseAlpha[i] / 255f));
            }
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            glassPaint.setColorFilter(colorFilter);
            for (Paint p : refractionLayerPaints) {
                p.setColorFilter(colorFilter);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ColorOS 风格效果：手势观察 / 灵动拉伸回弹 / 手指光晕
    // ═══════════════════════════════════════════════════════════

    /**
     * 根布局：先走默认分发（不影响子 View 的点击/滚动），再旁路观察事件。
     * 窗口内手势的真正 target 是 contentContainer / cell，
     * 普通 rootLayout 监听收不到内部事件，必须在 dispatch 层观察。
     */
    private class TouchObserverFrameLayout extends FrameLayout {
        TouchObserverFrameLayout(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            boolean handled = super.dispatchTouchEvent(ev);
            if (gestureController != null) {
                gestureController.onRootDispatch(ev);
            }
            return handled;
        }
    }

        /**
         * 窗口手势控制器（仅快捷方式窗口）：
         * 1. 手指按住拖动 → 窗口沿手指方向轻微移动（不完全跟手）并沿拖动方向拉伸/压扁；
         *    位移为纯位置公式（只取决于手指位置，与移速无关，快慢移速位移一致）；
         * 2. 松手 → 阻尼弹簧回弹（约 2 次超调震荡）到原始位置与正常比例，
         *    不注入松手速度（v0=0），避免快速移速导致松手瞬间窜动；
         * 3. 拖动/回弹期间逐帧用 UPDATE_BLUR_WINDOW_SIZE 同步模糊窗口的 x/y/w/h（方案 A：完全同步）；
         * 4. 手指光晕跟随（点击/按住/移动）。
         */
        private class WindowGestureController {
            // 弹簧参数：ζ=0.35（约 2 次可见超调）、ω=15rad/s（周期约 0.42s，约 1s 收敛）
            private static final float SPRING_ZETA = 0.35f;
            private static final float SPRING_OMEGA = 18f;
            private static final float SPRING_DURATION_MS = 1500f;
            private static final float DRAG_DAMPING = 0.08f;        // 位移跟手系数（轻微跟随）
            private static final float MAX_DRAG_DP = 6f;           // 最大位移
            private static final float STRETCH_DISTANCE_DP = 560f;  // 拉满所需拖动距离
            private static final float MAX_STRETCH = 0.05f;         // 最大拉伸 6%

            volatile boolean dragMoved; // 已发生拖动（抑制 cell 点击）

            private boolean tracking;    // 按下位置在窗口内
            private boolean dragging;    // 已越过 touch slop，进入拖动
            private boolean springing;   // 松手回弹中
            private float downRawX, downRawY;
            private float baseLeft, baseTop;        // DOWN 时窗口屏幕坐标（含当时 translation）
            private float baseTransX, baseTransY;   // DOWN 时的 translation（回弹目标）
            private float offsetX, offsetY;         // 当前拖动位移
            private float sx = 1f, sy = 1f;         // 当前拉伸比例
            // 模糊框逐帧同步（节流：字段有 ≥1px 变化才发）
            private boolean blurFrameSent;
            private int lastBlurX, lastBlurY, lastBlurW, lastBlurH;
            // 弹簧快照
            private float springQOffsetX, springQOffsetY, springQDevX, springQDevY;
            private long springStartNanos;
            private final int[] tmpLoc = new int[2];

        void onRootDispatch(MotionEvent ev) {
            if (contentContainer == null || isFinishing()) return;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    onDown(ev);
                    break;
                case MotionEvent.ACTION_MOVE:
                    onMove(ev);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    onUp(ev);
                    break;
                default:
                    break;
            }
        }

        private boolean canInteract() {
            return isExpanded && !isCollapsing && !isExpandAnimating
                    && !isKeyboardAnimating && !isFinishing();
        }

        private void onDown(MotionEvent ev) {
            int[] loc = tmpLoc;
            contentContainer.getLocationOnScreen(loc);
            float w = contentContainer.getWidth();
            float h = contentContainer.getHeight();
            if (w <= 0 || h <= 0) {
                tracking = false;
                return;
            }

            // 回弹中重新按下：立即收敛到静止状态，再以静止状态为新手势基准
            if (springing) {
                springing = false;
                offsetX = 0f;
                offsetY = 0f;
                sx = 1f;
                sy = 1f;
                contentContainer.setTranslationX(baseTransX);
                contentContainer.setTranslationY(baseTransY);
                contentContainer.setScaleX(1f);
                contentContainer.setScaleY(1f);
                contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                sendBlurFrame(true); // 校正模糊层到静止框
                contentContainer.getLocationOnScreen(loc);
            }

            // 仅窗口内部按下才跟踪（外部按下交给原有“点击外部关闭”逻辑）
            if (ev.getRawX() < loc[0] || ev.getRawX() > loc[0] + w
                    || ev.getRawY() < loc[1] || ev.getRawY() > loc[1] + h) {
                tracking = false;
                dragMoved = false;
                updateGlow(ev.getRawX(), ev.getRawY(), false);
                return;
            }

            tracking = true;
            dragMoved = false;
            dragging = false;
            downRawX = ev.getRawX();
            downRawY = ev.getRawY();
            baseLeft = loc[0];
            baseTop = loc[1];
            baseTransX = contentContainer.getTranslationX();
            baseTransY = contentContainer.getTranslationY();
            offsetX = 0f;
            offsetY = 0f;
            sx = 1f;
            sy = 1f;
            blurFrameSent = false;
            // 保证拉伸绕中心（展开动画已设过，防外部改动）
            contentContainer.setPivotX(w / 2f);
            contentContainer.setPivotY(h / 2f);
            updateGlow(ev.getRawX(), ev.getRawY(), true);
        }

        private void onMove(MotionEvent ev) {
            if (!tracking) return;
            updateGlow(ev.getRawX(), ev.getRawY(), true);
            if (!canInteract()) {
                abortGesture();
                return;
            }
            if (!shortcutDragStretch) return;

            if (!dragging) {
                int slop = ViewConfiguration.get(OverlayWindowActivity.this).getScaledTouchSlop();
                float dx0 = ev.getRawX() - downRawX;
                float dy0 = ev.getRawY() - downRawY;
                if (dx0 * dx0 + dy0 * dy0 < (float) slop * slop) return;
                dragging = true;
                dragMoved = true;
                contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            float dx = ev.getRawX() - downRawX;
            float dy = ev.getRawY() - downRawY;

            // 位移：纯位置公式（与移速无关），tanh 软钳位到 ≤16dp
            float maxPx = dpToPxF(MAX_DRAG_DP);
            offsetX = (float) (maxPx * Math.tanh(dx * DRAG_DAMPING / maxPx));
            offsetY = (float) (maxPx * Math.tanh(dy * DRAG_DAMPING / maxPx));

            // 拉伸：沿拖动方向拉伸、垂直方向压扁
            float len = (float) Math.hypot(dx, dy);
            float s = Math.min(1f, len / dpToPxF(STRETCH_DISTANCE_DP)) * MAX_STRETCH;
            float ux = len > 0.001f ? Math.abs(dx) / len : 0f;
            float uy = len > 0.001f ? Math.abs(dy) / len : 0f;
            sx = 1f + s * (ux - 0.4f * uy);
            sy = 1f + s * (uy - 0.4f * ux);

            // 限制窗口不超出屏幕（越界会让 WMS 钳住模糊窗口，表现为模糊层反向移动）
            clampOffsets();

            applyTransformAndSyncBlur();
        }

        private void onUp(MotionEvent ev) {
            updateGlow(ev.getRawX(), ev.getRawY(), false);
            if (!tracking) return;
            tracking = false;
            if (dragging) {
                dragging = false;
                startSpring();
            }
        }

        /** 手势被更高优先级状态打断（收起/键盘等）：只清状态不改 transform */
        private void abortGesture() {
            tracking = false;
            dragging = false;
            updateGlow(0, 0, false);
        }

        /** 取消全部手势（收起动画开始时调用），transform 控制权交回收起动画 */
        void cancelAll() {
            tracking = false;
            dragging = false;
            springing = false;
            dragMoved = false;
            if (fingerGlowView != null) fingerGlowView.release();
        }

        private void startSpring() {
            // 只从松手位置回弹，不注入松手速度（v0=0）：
            // 避免快速移动手指后松手瞬间弹簧窜出较大位移，保证快/慢移速下回弹表现一致
            springQOffsetX = offsetX;
            springQOffsetY = offsetY;
            springQDevX = sx - 1f;
            springQDevY = sy - 1f;
            springStartNanos = System.nanoTime();
            springing = true;
            Choreographer.getInstance().postFrameCallback(springCallback);
        }

        /**
         * 欠阻尼弹簧解析解（v0=0）：q(t) = e^{-ζωt} · q0 · (cos(ωd·t) + (ζω/ωd)·sin(ωd·t))
         */
        private float springValue(float q0, float tSec) {
            float env = (float) Math.exp(-SPRING_ZETA * SPRING_OMEGA * tSec);
            float wd = SPRING_OMEGA * (float) Math.sqrt(1f - SPRING_ZETA * SPRING_ZETA);
            return env * q0 * ((float) Math.cos(wd * tSec)
                    + (SPRING_ZETA * SPRING_OMEGA / wd) * (float) Math.sin(wd * tSec));
        }

        private final Choreographer.FrameCallback springCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!springing) return;
                if (isCollapsing || isFinishing() || !canInteract()) {
                    // 收起动画接管 transform，此处直接退出
                    springing = false;
                    return;
                }
                float tMs = (frameTimeNanos - springStartNanos) / 1_000_000f;
                if (tMs >= SPRING_DURATION_MS) {
                    endSpring();
                    return;
                }
                float t = tMs / 1000f;
                offsetX = springValue(springQOffsetX, t);
                offsetY = springValue(springQOffsetY, t);
                sx = 1f + springValue(springQDevX, t);
                sy = 1f + springValue(springQDevY, t);

                // 回弹超调同样不能越出屏幕
                clampOffsets();

                // 收敛判定：按包络（振幅）判定，避免瞬时值在过零点误判收敛、掐掉超调
                float env = (float) Math.exp(-SPRING_ZETA * SPRING_OMEGA * t);
                boolean offsetSettled = env * Math.max(Math.abs(springQOffsetX), Math.abs(springQOffsetY)) < 0.3f;
                boolean devSettled = env * Math.max(Math.abs(springQDevX), Math.abs(springQDevY)) < 0.0008f;
                if (offsetSettled && devSettled) {
                    endSpring();
                    return;
                }
                applyTransformAndSyncBlur();
                Choreographer.getInstance().postFrameCallback(this);
            }
        };

        private void endSpring() {
            springing = false;
            if (isCollapsing || isFinishing()) return;
            offsetX = 0f;
            offsetY = 0f;
            sx = 1f;
            sy = 1f;
            contentContainer.setTranslationX(baseTransX);
            contentContainer.setTranslationY(baseTransY);
            contentContainer.setScaleX(1f);
            contentContainer.setScaleY(1f);
            contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
            sendBlurFrame(true); // 最后一帧恢复到与布局精确一致的基础框
        }

        private void applyTransformAndSyncBlur() {
            contentContainer.setTranslationX(baseTransX + offsetX);
            contentContainer.setTranslationY(baseTransY + offsetY);
            contentContainer.setScaleX(sx);
            contentContainer.setScaleY(sy);
            sendBlurFrame(false);
        }

        /**
         * 限制窗口基准矩形始终在屏幕内。
         * 越界时模糊窗口的 params.x/y 会被 WMS 钳到屏幕边缘，
         * 内容层继续移动而模糊层不动，表现为"模糊层反向移动"。
         */
        private void clampOffsets() {
            float w = contentContainer.getWidth();
            float h = contentContainer.getHeight();
            if (w <= 0 || h <= 0) return;
            float minOffX = -baseLeft;
            float maxOffX = screenWidth - baseLeft - w;
            float minOffY = -baseTop;
            float maxOffY = screenHeight - baseTop - h;
            offsetX = Math.max(minOffX, Math.min(maxOffX, offsetX));
            offsetY = Math.max(minOffY, Math.min(maxOffY, offsetY));
        }

        /**
         * 方案 A：逐帧同步模糊窗口完整 x/y/w/h。
         * 复用现有 UPDATE_BLUR_WINDOW_SIZE 协议（接收端已支持）。
         * 若真机实测掉帧，降级顺序：尺寸量化到 2px → 隔帧发送 → 退回包络方案。
         */
        private void sendBlurFrame(boolean force) {
            if (contentContainer == null) return;
            float w = contentContainer.getWidth();
            float h = contentContainer.getHeight();
            if (w <= 0 || h <= 0) return;
            // 缩放绕中心：中心 = 基准左上 + 半尺寸 + 当前位移
            float centerX = baseLeft + w / 2f + offsetX;
            float centerY = baseTop + h / 2f + offsetY;
            int bx = Math.round(centerX - w * sx / 2f);
            int by = Math.round(centerY - h * sy / 2f);
            int bw = Math.round(w * sx);
            int bh = Math.round(h * sy);

            // 与屏幕求交集：请求框越界同样会触发 WMS 钳制（拉伸瞬间可能略微越界），
            // 钳到屏幕边界后与被屏幕裁切的内容严格对齐
            int sx0 = Math.max(0, bx);
            int sy0 = Math.max(0, by);
            int sx1 = Math.min(screenWidth, bx + bw);
            int sy1 = Math.min(screenHeight, by + bh);
            if (sx1 - sx0 <= 0 || sy1 - sy0 <= 0) return;
            bx = sx0;
            by = sy0;
            bw = sx1 - sx0;
            bh = sy1 - sy0;

            if (!force && blurFrameSent
                    && bx == lastBlurX && by == lastBlurY && bw == lastBlurW && bh == lastBlurH) {
                return;
            }
            if (force && !blurFrameSent) return;
            lastBlurX = bx;
            lastBlurY = by;
            lastBlurW = bw;
            lastBlurH = bh;
            blurFrameSent = true;
            try {
                Intent intent = new Intent("UPDATE_BLUR_WINDOW_SIZE");
                intent.setPackage(getPackageName());
                intent.putExtra("blur_x", bx);
                intent.putExtra("blur_y", by);
                intent.putExtra("blur_width", bw);
                intent.putExtra("blur_height", bh);
                intent.putExtra("blur_corner_radius", dpToPx(cornerRadius));
                sendBroadcast(intent);
            } catch (Exception e) {
                Log.e(TAG, "发送模糊窗口逐帧同步失败", e);
            }
        }

        // ─── 手指光晕 ───

        private void updateGlow(float rawX, float rawY, boolean pressed) {
            if (fingerGlowView == null) return;
            if (!pressed) {
                fingerGlowView.release();
                return;
            }
            if (!canInteract()) {
                fingerGlowView.release();
                return;
            }
            int[] loc = tmpLoc;
            contentContainer.getLocationOnScreen(loc);
            float scx = Math.max(0.01f, contentContainer.getScaleX());
            float scy = Math.max(0.01f, contentContainer.getScaleY());
            fingerGlowView.setPointer((rawX - loc[0]) / scx, (rawY - loc[1]) / scy);
            fingerGlowView.press();
        }
    }

    /**
     * 手指光晕：跟随手指的径向白色高光，叠在 contentContainer 最上层。
     * 永不消费触摸事件（onTouchEvent 恒 false），事件自动穿透给下层内容。
     */
    private class FingerGlowView extends View {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float pointerX, pointerY;
        private float glowRadius;
        private boolean pressed;

        FingerGlowView(Context context) {
            super(context);
            glowPaint.setStyle(Paint.Style.FILL);
            setAlpha(0f);
            // 光晕裁剪到窗口圆角轮廓内，避免手指移到四角时光晕溢出圆角显示为直角
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(),
                            dpToPx(cornerRadius));
                }
            });
            setClipToOutline(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            glowRadius = Math.round(Math.max(dpToPx(100),
                    Math.min(dpToPx(140), Math.min(w, h) * 0.6f)));
            // 以 (0,0) 为圆心创建 shader，绘制时平移到指针位置，避免每次移动都分配对象
            glowPaint.setShader(new RadialGradient(0, 0, glowRadius,
                    Color.argb(70, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP));
        }

        void setPointer(float x, float y) {
            pointerX = x;
            pointerY = y;
            invalidate();
        }

        void press() {
            if (pressed) return;
            pressed = true;
            animate().cancel();
            animate().alpha(1f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        void release() {
            if (!pressed && getAlpha() == 0f) return;
            pressed = false;
            animate().cancel();
            animate().alpha(0f).setDuration(350)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (getAlpha() <= 0.001f || glowPaint.getShader() == null) return;
            canvas.save();
            canvas.translate(pointerX, pointerY);
            canvas.drawCircle(0, 0, glowRadius, glowPaint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false; // 永不消费，事件穿透给下层 cell / ScrollView
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

    private void removeTransparentBackground(WebView webView){
        String js = "(function(){" +
                "var s=document.getElementById('transparent-bg-style');" +
                "if(s){s.parentNode.removeChild(s);}" +
                "})()";

        try {
            // 同步执行，确保 CSS 立即生效
            webView.evaluateJavascript(js, null);
            Log.d(TAG, isDarkMode() ? "Dark mode CSS filter injected" : "Dark mode CSS filter removed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to " + (isDarkMode() ? "inject" : "remove") + " dark mode CSS filter", e);
        }
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
                "}";

        
        String js = "(function(){" +
                // 注入 CSS
                "var style=document.getElementById('transparent-bg-style');" +
                "if(!style){style = document.createElement('style');style.id = 'transparent-bg-style';}" +
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
        Log.d(TAG, "injectDarkModeCSS: isDarkMode=" + isDarkMode() + " webpageBgTransparent=" + webpageBgTransparent);

        String js;
        if (isDarkMode()) {
            String css;
            if (webpageBgTransparent) {
                // 透明模式：只应用滤镜，不改变背景色（保持透明）
                css = "" +
                        "html { -webkit-filter: invert(85%) hue-rotate(180deg); filter: invert(85%) hue-rotate(180deg); }" +
                        "img, video, canvas, svg, [style*=\"background-image\"] { -webkit-filter: invert(85%) hue-rotate(180deg); filter: invert(85%) hue-rotate(180deg); }" +
                        ":root { color-scheme: dark !important; }";
            } else {
                // 不透明模式：设置深色背景，同时应用滤镜
                css = "" +
                        "html { background-color: #111 !important; -webkit-filter: invert(85%) hue-rotate(180deg); filter: invert(85%) hue-rotate(180deg); }" +
                        "img, video, canvas, svg, [style*=\"background-image\"] { -webkit-filter: invert(85%) hue-rotate(180deg); filter: invert(85%) hue-rotate(180deg); }" +
                        ":root { color-scheme: dark !important; }";
            }
            js = "(function() {" +
                    "  var s = document.getElementById('__dark_mode_filter');" +
                    "  if (!s) { s = document.createElement('style'); s.id = '__dark_mode_filter'; }" +
                    "  s.textContent = '" + css.replace("'", "\\'") + "';" +
                    "  var h = document.head; if (h) { h.appendChild(s); } else { document.documentElement.appendChild(s); }" +
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
        String js;
        if (TextUtils.isEmpty(customCss)) {
            js = "(function(){" +
                    "var s=document.getElementById('custom-css-style');" +
                    "if(s){s.parentNode.removeChild(s);}" +
                    "})()";
        }else{
            // 完整的 JavaScript 字符串转义
            String escapedCss = customCss
                    .replace("\\", "\\\\")  // 先转义反斜杠
                    .replace("'", "\\'")     // 再转义单引号
                    .replace("\n", "\\n")    // 转义换行符
                    .replace("\r", "\\r");   // 转义回车符
            
            js = "(function(){" +
                    "var style=document.getElementById('custom-css-style');" +
                    "if(!style){style = document.createElement('style');style.id = 'custom-css-style';}" +
                    "style.textContent = '" + escapedCss + "';" +
                    "if (document.head) {" +
                    "  document.head.appendChild(style);" +
                    "} else {" +
                    "  document.documentElement.appendChild(style);" +
                    "}" +
                    // 调试：检查目标元素是否存在
                    "var target = document.querySelector('.container-slHYQz');" +
                    "console.log('Custom CSS injected, length: " + customCss.length() + ", element found: ' + (target !== null));" +
                    "if(target) { console.log('Element display: ' + window.getComputedStyle(target).display); }" +
                    "return 'Custom CSS injection complete';" +
                    "})()";
        }
        

        
        webView.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "Custom CSS injection result: " + value);
            }
        });
        Log.d(TAG, "Injected custom CSS, content: " + customCss.substring(0, Math.min(customCss.length(), 100)));
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
    
    // ═══════════════════════════════════════════════════════════
    // content:// URI → file:// 临时文件转换
    // ═══════════════════════════════════════════════════════════
    
    /**
     * 将 content:// URI 复制到临时文件，转为 file:// URI
     * 避免 FileChooserActivity 结束后 content URI 权限失效导致上传失败
     */
    private Uri[] copyContentUrisToTempFiles(Uri[] uris) {
        if (uris == null || uris.length == 0) {
            Log.d(TAG, "copyContentUrisToTempFiles: null or empty input");
            return uris;
        }
        Log.d(TAG, "copyContentUrisToTempFiles: copying " + uris.length + " URIs");
        
        Uri[] result = new Uri[uris.length];
        for (int i = 0; i < uris.length; i++) {
            Uri uri = uris[i];
            if (uri == null) {
                result[i] = null;
                continue;
            }
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                Log.d(TAG, "copyContentUrisToTempFiles[" + i + "]: already file://, skip");
                result[i] = uri;
                continue;
            }
            try {
                String fileName = "upload_" + System.currentTimeMillis() + "_" + i;
                String extension = getFileExtension(uri);
                if (extension != null) fileName += extension;
    
                File cacheDir = new File(getCacheDir(), "webview_uploads");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File tempFile = new File(cacheDir, fileName);
    
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    in = getContentResolver().openInputStream(uri);
                    if (in == null) {
                        Log.w(TAG, "copyContentUrisToTempFiles[" + i + "]: openInputStream returned null for " + uri);
                        result[i] = uri;
                        continue;
                    }
                    out = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[65536];
                    int total = 0, len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                        total += len;
                    }
                    out.flush();
                    Log.d(TAG, "copyContentUrisToTempFiles[" + i + "]: SUCCESS "
                            + uri + " -> " + tempFile.getAbsolutePath()
                            + " (" + total + " bytes, file=" + tempFile.length() + ")");
                } finally {
                    if (in != null) try { in.close(); } catch (Exception ignored) {}
                    if (out != null) try { out.close(); } catch (Exception ignored) {}
                }
    
                result[i] = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempFile);
            } catch (Exception e) {
                Log.e(TAG, "copyContentUrisToTempFiles[" + i + "]: FAILED for " + uri, e);
                result[i] = uri;
            }
        }
        return result;
    }
    
    /**
     * 从 content URI 推断文件扩展名
     */
    private String getFileExtension(Uri uri) {
        try {
            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null) {
                if (mimeType.equals("image/jpeg")) return ".jpg";
                if (mimeType.equals("image/png")) return ".png";
                if (mimeType.equals("image/gif")) return ".gif";
                if (mimeType.equals("image/webp")) return ".webp";
                if (mimeType.equals("video/mp4")) return ".mp4";
                if (mimeType.equals("audio/mpeg")) return ".mp3";
                // 从 MIME 类型提取常见后缀
                if (mimeType.startsWith("image/")) return "." + mimeType.substring(6);
                if (mimeType.startsWith("video/")) return "." + mimeType.substring(6);
                if (mimeType.startsWith("audio/")) return "." + mimeType.substring(6);
            }
            // 从 URI 路径最后一段提取扩展名
            String path = uri.getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0) return path.substring(dot);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get file extension for " + uri, e);
        }
        return null;
    }
    
    /**
     * 使用 FileChooserActivity 代理打开文件选择器
     * 使用 Activity 实例启动，FileChooserActivity 将在同一 Task 中运行，
     * Activity 会进入 onPause/onStop 但不会被销毁（noHistory 已移除），
     * 因此文件上传完成后能自然返回当前窗口。
     */
    private void openFileChooser(ValueCallback<Uri[]> filePathCallback,
                                   WebChromeClient.FileChooserParams fileChooserParams) {
        Log.d(TAG, "openFileChooser: callback=" + filePathCallback
                + ", params=" + fileChooserParams
                + ", isFinishing=" + isFinishing()
                + ", mIsStartingFileChooser=" + mIsStartingFileChooser);
        
        if (fileChooserParams != null) {
            String[] acceptTypes = fileChooserParams.getAcceptTypes();
            Log.d(TAG, "openFileChooser: acceptTypes=" + java.util.Arrays.toString(acceptTypes)
                    + ", mode=" + fileChooserParams.getMode());
        }
        
        mIsStartingFileChooser = true;
        
        if (FileChooserActivity.uploadCallback != null) {
            Log.d(TAG, "openFileChooser: cancelling previous pending callback");
            try {
                FileChooserActivity.uploadCallback.onReceiveValue(null);
            } catch (Exception ignored) {}
        }
        
        cleanupOldTempFiles();
        
        final ValueCallback<Uri[]> originalCallback = filePathCallback;
        FileChooserActivity.uploadCallback = new ValueCallback<Uri[]>() {
            @Override
            public void onReceiveValue(Uri[] value) {
                Log.d(TAG, "FCA callback onReceiveValue: value=" + (value != null ? value.length : "null"));
                if (value != null) {
                    for (Uri u : value) {
                        Log.d(TAG, "FCA callback: uri=" + u + ", scheme=" + u.getScheme());
                        try {
                            Log.d(TAG, "FCA callback: type=" + getContentResolver().getType(u));
                        } catch (Exception e) {
                            Log.w(TAG, "FCA callback: cannot get type for " + u);
                        }
                    }
                }
                final Uri[] fileUris = copyContentUrisToTempFiles(value);
                if (fileUris != value) {
                    Log.d(TAG, "FCA callback: copied to temp files:");
                    for (Uri u : fileUris) {
                        Log.d(TAG, "FCA callback: temp=" + u);
                    }
                }
                sPendingUploadCallback = originalCallback;
                sPendingFileUris = fileUris;
                Log.d(TAG, "FCA callback: stored pending callback, sPendingFileUris=" + (sPendingFileUris != null ? sPendingFileUris.length : 0));
            }
        };
        
        try {
            Intent proxyIntent = new Intent(this, FileChooserActivity.class);
            proxyIntent.putExtra("accept_types", new String[]{"*/*"});
            proxyIntent.putExtra("allow_multiple", false);
            startActivity(proxyIntent);
            Log.d(TAG, "openFileChooser: FileChooserActivity started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start file chooser proxy", e);
            if (FileChooserActivity.uploadCallback != null) {
                FileChooserActivity.uploadCallback.onReceiveValue(null);
                FileChooserActivity.uploadCallback = null;
            }
            mIsStartingFileChooser = false;
        }
    }
    
    /**
     * 清理 webview_uploads 目录中过期的临时文件
     */
    private void cleanupOldTempFiles() {
        try {
            File cacheDir = new File(getCacheDir(), "webview_uploads");
            if (!cacheDir.exists()) {
                Log.d(TAG, "cleanupOldTempFiles: dir not exists, skip");
                return;
            }
            File[] files = cacheDir.listFiles();
            if (files == null) return;
            int deleted = 0;
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (f.isFile() && (now - f.lastModified()) > 5 * 60 * 1000) {
                    if (f.delete()) deleted++;
                }
            }
            Log.d(TAG, "cleanupOldTempFiles: deleted " + deleted + " old temp files");
        } catch (Exception e) {
            Log.w(TAG, "Failed to cleanup old temp files", e);
        }
    }
}
