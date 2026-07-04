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

    // ─── 视图 ───
    private FrameLayout rootLayout;
    private FrameLayout contentContainer;
    private View blurBackgroundView; // 独立模糊背景层（仅显示模糊桌面，不含子内容）
    private WebView webView;
    private ImageView backButtonRef;
    private boolean isExpanded = false;
    private boolean isCollapsing = false;
    private BroadcastReceiver restoreBarReceiver;

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
    }

    @Override
    protected void onDestroy() {
        // 通知下层模糊窗口关闭
        BlurWindowActivity.finishIfAlive();
        if (restoreBarReceiver != null) {
            try { unregisterReceiver(restoreBarReceiver); } catch (Exception ignored) {}
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
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
        } else {
            shortcutIconSize = intent.getIntExtra(EXTRA_SHORTCUT_ICON_SIZE, 100);
            shortcutStyle = intent.getIntExtra(EXTRA_SHORTCUT_STYLE, 0);
            shortcutBlurRadius = intent.getIntExtra(EXTRA_SHORTCUT_BLUR_RADIUS, 20);
            shortcutOverlayAlpha = intent.getIntExtra(EXTRA_SHORTCUT_OVERLAY_ALPHA, 180);
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
    }

    private int getAcrylicBgColor() {
        // 半透明底色：浅色/深色跟随系统，80% 不透明度显示模糊背景
        if (isDarkMode()) {
            return Color.parseColor("#CC2B2B2B");
        } else {
            return Color.parseColor("#CCFFFFFF");
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
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Activity 中的 WebView 可以正常接收输入法输入，无需 IME 代理
        WebSettings settings = webView.getSettings();
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

        // WebChromeClient（权限）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                try { request.grant(request.getResources()); } catch (Exception ignored) {}
            }
        });

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateBackButtonState();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                updateBackButtonState();
            }
        });

        // Activity 的 WebView 可以使用原生 ActionMode，无需 JS 选中
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

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
        // WebView 背景透明
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        container.addView(webView);
        webView.loadUrl(aiUrl);
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
                if (webView != null) webView.reload();
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

    // ═══════════════════════════════════════════════════════════
    // 展开动画（Choreographer 驱动）
    // ═══════════════════════════════════════════════════════════

    private void performExpandAnimation() {
        // 计算初始变换
        initScaleX = (float) startWidth / targetWidth;
        initScaleY = (float) startHeight / targetHeight;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) contentContainer.getLayoutParams();
        initTranslationX = startX - 2 * lp.leftMargin;
        initTranslationY = startY - 2 * lp.topMargin;

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
                        webView.setAlpha(easeOutCubic(alphaProgress));
                    }
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
                        webView.setAlpha(1f);
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

                contentContainer.setScaleX(curScaleX);
                contentContainer.setScaleY(curScaleY);
                contentContainer.setTranslationX(curTransX);
                contentContainer.setTranslationY(curTransY);

                // blurBackgroundView 同步收起动画
                if (blurBackgroundView != null) {
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
                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (blurBackgroundView != null) {
                        blurBackgroundView.setScaleX(1f);
                        blurBackgroundView.setScaleY(1f);
                        blurBackgroundView.setTranslationX(0f);
                        blurBackgroundView.setTranslationY(0f);
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
}
