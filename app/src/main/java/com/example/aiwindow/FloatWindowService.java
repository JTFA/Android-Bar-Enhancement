package com.example.aiwindow;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Choreographer;
import android.view.animation.PathInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.animation.ValueAnimator;

import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

import android.app.DownloadManager;
import android.webkit.URLUtil;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 悬浮窗前台服务
 * 用于在后台稳定运行悬浮窗
 */
public class FloatWindowService extends Service {

    private static final String CHANNEL_ID = "float_window_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams layoutParams;

    public static final float SMOOTH_FACTOR = 0.65f;

    // 底部提示条相关
    private FrameLayout barContainer;
    private View bottomBar;
    private WindowManager.LayoutParams barLayoutParams;
    // 独立光晕窗口：承载按下覆盖层与光晕层（FLAG_NOT_TOUCHABLE、恒定尺寸、永不缩放）
    private FrameLayout glowContainer;
    private WindowManager.LayoutParams glowLayoutParams;

    // 默认样式参数
    private int currentWidth = 375;
    private int currentHeight = 9; // dp
    private int currentCornerRadius = 30; // dp
    private float currentAlpha = 0.0f;
    private int barPositionX = 0; // x 偏移
    private int barPositionY = 389; // y 偏移（距底部，越靠近屏幕底部值越大）
    private int longPressDuration = 500; // 长按触发时间（毫秒）
    private int windowRadius = 30;
    // 按下状态样式（手指按住提示条但尚未触发长按时）
    private boolean pressedBarEnabled = true; // 是否启用按下样式变化
    private String pressedBarColor = "#0099ff"; // 按下时颜色
    private int pressedBarAlpha = 100; // 按下时透明度 (0-100)
    private int pressedBarWidth = 400; // 按下时宽度 (dp)
    private int pressedBarHeight = 16; // 按下时高度 (dp)
    private int pressedBarCorner = 30; // 按下时圆角 (dp)
    private int pressedBarFeatherWidth = 10; // 按下时边缘羽化宽度 (dp)
    private int pressedBarPositionY = 0; //按下时位置Y (dp，正值向上)
    private View pressedOverlay; // 按下状态覆盖层（透明度渐变显示按下样式）
    private View glowOverlay; // 光晕层（小横条作为光源向四周散射光）
    private GlowView glowOuter; // 外层光晕：大范围柔和远光
    private GlowView glowInner; // 内层光晕：贴着横条的亮芯
    private int pressedBarGlowRadius = 25; // 光晕半径 (dp，0=关闭光晕)
    private String pressedBarColorMode = "rainbow"; // 颜色模式: rainbow=彩虹流光, color=自定义颜色
    private ValueAnimator rainbowAnimator; // 彩虹流光动画（按住期间循环）
    private float rainbowFlow = 0f; // 流光相位（核心层与光晕层共享，保证同步）

    // AI 对话窗口相关
    private FrameLayout aiWindowContainer;
    private WebView aiWebView;
    private WindowManager.LayoutParams aiWindowParams;
    private boolean isAiWindowExpanded = false;
    // OverlayWindowActivity 前台可见标记（同进程静态直连，由 Activity onStart/onStop 维护）
    private static volatile boolean sOverlayWindowOpen = false;

    public static void setOverlayWindowOpen(boolean open) {
        sOverlayWindowOpen = open;
    }
    private String currentAiUrl = "https://www.doubao.com"; // 默认 URL，可通过设置修改
    private String loadedAiUrl = null; // 实际已加载的 URL（用于判断是否需要重新加载）

    // AI 窗口默认尺寸（单位：dp，在 onCreate 中转换为 px）
    private static final int AI_WINDOW_WIDTH_DP = 333;
    private static final int AI_WINDOW_HEIGHT_DP = 450;

    private int aiWindowWidth;   // 像素值，在 onCreate 中初始化
    private int aiWindowHeight;  // 像素值，在 onCreate 中初始化
    private int windowPositionX = 0; // 窗口 X 偏移
    private int windowPositionY = 225; // 窗口 Y 偏移

    // ===== 快捷方式窗口相关 =====
    private boolean isShortcutWindowExpanded = false;
    private View shortcutWindowView;

    // 快捷方式窗口默认设置
    private int shortcutWindowWidth = 350;  // dp
    private int shortcutWindowHeight = 350; // dp
    private int shortcutWindowPositionY = 231; // 居中位置
    private int shortcutCornerRadius = 24; // dp
    private String shortcutBgColor = "#FFFFFF";
    private int shortcutIconSize = 100; // 图标大小百分比 50-150，默认 100%
    private int shortcutIconCorner = 16; // 图标圆角（图标边长百分比 0-50，连续曲率 superellipse）
    private boolean tryFullscreenWindowing = false; // 尝试消除授权弹窗（windowingMode 自定义）
    private int customWindowingMode = 6; // 自定义 windowingMode，不输入时默认 6

    // 双击检测相关
    private static final int DOUBLE_TAP_TIMEOUT = 300; // ms
    private long lastTapTime = 0;
    private Handler tapHandler = new Handler(Looper.getMainLooper());
    private Runnable singleTapRunnable;

    // 边框和阴影设置
    private int borderWidth = 1; // dp
    private int cornerRadius = 30; // dp - 窗口圆角半径
    private String borderColor = "#000000";
    private float borderAlpha = 0.1f;
    private int shadowWidth = 0; // dp
    private String shadowColor = "#000000";
    private float shadowAlpha = 0.0f; // 关键修复：增加默认透明度从 0.3 到 0.6，使阴影更明显

    // 背景遮罩设置
    private View overlayView;
    private String overlayColor = "#000000";
    private int overlayAlpha = 50; // 遮罩透明度 0-100

    private WindowManager.LayoutParams overlayLayoutParams;    // 关闭按钮显示设置
    private GradientDrawable overlayBackground; // 遮罩背景 Drawable
    private boolean isOverlayAnimating = false; // 遮罩动画进行中标志
    private boolean showCloseButton = true; // 默认显示关闭按钮
    private boolean showRefreshButton = true; // 默认显示刷新按钮
    private boolean showBackButton = true; // 默认显示后退按钮
    private int topBarTransparency = 40; // 顶部小横条背景透明度 0-100
    private boolean isTopBarExpanded = false;

    // 返回上一级按钮引用（用于在页面加载后更新状态）
    private ImageView backButtonRef;

    // 单击提示条打开第三方应用
    private String clickAppPackage = "";
    private String clickAppName = "";
    private String clickAppClass = "";

    // ===== 触发行为配置 =====
    private static final String ACTION_NONE = "none";
    private static final String ACTION_APP = "app";
    private static final String ACTION_SHORTCUT = "shortcut";
    private static final String ACTION_AI = "ai";

    private String clickAction = ""; // 单击行为（空字符串表示由加载的设置决定）
    private String doubleClickAction = ACTION_SHORTCUT; // 双击行为（默认快捷方式窗口）
    private String longPressAction = ACTION_AI; // 长按行为（默认AI窗口）

    // 双击打开第三方应用
    private String doubleClickAppPackage = "";
    private String doubleClickAppName = "";
    private String doubleClickAppClass = "";

    // 长按打开第三方应用
    private String longPressAppPackage = "";
    private String longPressAppName = "";
    private String longPressAppClass = "";

    // 窗口打开开关（使用系统小窗打开应用）
    private boolean clickOpenInWindow = false;
    private boolean doubleClickOpenInWindow = false;
    private boolean longPressOpenInWindow = false;
    // 触发时振动开关
    private boolean clickVibrate = false;
    private boolean doubleClickVibrate = false;
    private boolean longPressVibrate = false;

    // 点击窗口外关闭窗口设置
    private boolean closeOnOutsideClick = true; // 默认开启

    // 侧边返回手势优先返回网页上一级（true）还是始终关闭窗口（false）
    private boolean backPrioritizeWebview = true;

    // IME 代理 EditText（解决 TYPE_APPLICATION_OVERLAY 中 WebView 无法建立 InputConnection 的问题）
    private EditText proxyIme;
    private GestureDetector gestureDetector;
    private TextWatcher proxyTextWatcher;
    private boolean isUpdatingProxy = false;
    private boolean isLongPressing = false;
    private Runnable pendingTextForward;
    private View contextMenuPopupView;
    private volatile String pendingPasteText; // 待粘贴的剪贴板文本（通过 @JavascriptInterface 传递，避免嵌入 JS 代码导致超长限制）
    private FrameLayout windowRootLayout;
    private Runnable blurRefocusRunnable; // 输入框失焦后延迟重聚焦（用于区分发送按钮短暂失焦与真正失焦）

    // 输入法避让相关
    private boolean isKeyboardVisible = false;
    private int baseLeftMargin = 0;     // 无键盘时的 leftMargin
    private int baseTopMargin = 0;      // 无键盘时的 topMargin
    private int baseRightMargin = 0;    // 无键盘时的 rightMargin
    private int baseBottomMargin = 0;   // 无键盘时的 bottomMargin
    private int keyboardOffset = 785;   // 输入法弹出时窗口上移距离（px）
    private int keyboardAnimDuration = 250; // 平移动画时长（ms）
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private boolean isKeyboardAnimating = false;
    private Runnable keyboardHideDetector = null;

    private int shortcutStyle = 0; // 0=不透明, 1=亚克力, 2=液态玻璃
    private int shortcutBlurRadius = 20; // dp
    private int shortcutOverlayAlpha = 180; // 0-255
    private View shortcutContentContainer;
    private ContinuousRoundRectDrawable shortcutBackgroundDrawable;
    
    // AI 窗口模糊效果设置
    private int aiStyle = 0; // 0=不透明, 1=亚克力, 2=液态玻璃
    private int aiBlurRadius = 25; // dp
    private int aiOverlayAlpha = 204; // 0-255 (背景透明度)

    private boolean useExpandDirectly = false;
    private boolean webpageBgTransparent = false;
    private String customCss = "";

    // IME 处理中标志（类级别），可在 cleanupProxyIme 中重置，防止死锁
    private volatile boolean isImeProcessing = false;

    // 按下样式预览自动隐藏（设置页调节按下样式时使用）
    private Handler pressedPreviewHandler = new Handler(Looper.getMainLooper());

    private Runnable pressedPreviewTimeout = new Runnable() {
        @Override
        public void run() {
            restoreNormalBarStyles();
            Log.d(TAG, "按下样式预览超时，已自动隐藏");
        }
    };

    private BroadcastReceiver restoreBarReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.aiwindow.RESTORE_BAR".equals(intent.getAction())) {
                // 恢复底部提示条透明度
                if (bottomBar != null) {
                    bottomBar.animate().alpha(1f).setDuration(150).start();
                }
            }
        }
    };

    // 动画相关
    private int animationDuration = 385; // 毫秒（展开/收起动画时长，可从设置调节）
    private int overlayAnimDuration = 100; // 毫秒（背景遮罩透明度渐变动画时长）
    private static final long FRAME_INTERVAL = 8; // 120fps (1000/120 ≈ 8ms)
    private static final String TAG = "FloatWindowService";
    private boolean blurWindowFadeInSent = false; // 标记是否已发送模糊窗口渐变显示广播

    // Android 官方贝塞尔曲线插值器（API 21+ 内置，零依赖）
    // Material Design FastOutSlowIn 标准缓动，先快后慢，末尾平滑自然
    private final PathInterpolator animInterpolator = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);

    // 展开动画插值器：在 FastOutSlowIn 基础上末尾有微小超调（~2.5%），自然连贯不抽搐
    // 第二控制点 y=1.035 使贝塞尔曲线在末尾轻微越过 1.0 后回落，模拟物理弹簧阻尼
//    private final PathInterpolator expandInterpolator = new PathInterpolator(0.4f, 0.2f, 0.2f, 1.0f);
    private final OvershootInterpolator expandInterpolator = new OvershootInterpolator(0.5f);

    // 展开动画末段放缓：[0, TA] 沿用 expandInterpolator（前期速度与原实现逐帧一致），
    // TA 之后由欠阻尼弹簧在位置/速度连续(C1)条件下接管，超调一次后落点取
    // "回到目标值的第二次过零"（e=0、残余速度极小，无需 snap）。
    // 总时长 = animationDuration × expandSpringTotal；调节动画速度设置时整个时间轴
    // （含弹簧段）按 animationDuration 等比缩放，即为纯速度缩放。
    private static final float EXPAND_SPRING_TA = 0.54f;     // 弹簧接管点（原曲线时间轴）
    private static final float EXPAND_SPRING_ZETA = 0.52f;   // 阻尼比
    private static final float EXPAND_SPRING_OMEGA = 5.2f;   // 归一化角频率（单位 = 1/animationDuration）
    private boolean expandSpringReady = false;
    private float expandSpringA, expandSpringB, expandSpringWd, expandSpringTotal;

    /**
     * 检测系统是否处于深色模式
     */
    private boolean isDarkMode() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 获取窗口背景色（跟随系统深色模式）
     */
    private int getWindowBgColor() {
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

    /**
     * 为 WebView 应用深色模式设置（API 29+）
     * 每次展开窗口时都调用，对新创建和复用的 WebView 均生效
     */
    private void applyWebViewDarkMode(WebView webView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WebSettings settings = webView.getSettings();
            if (isDarkMode()) {
                settings.setForceDark(WebSettings.FORCE_DARK_ON);
                // API 33+: 开启算法暗化（更智能，保留图片，平滑调整）而非简单反色
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    settings.setAlgorithmicDarkeningAllowed(true);
                }
                // 设置深色背景，在 CSS filter 生效前消除白闪
                webView.setBackgroundColor(Color.parseColor("#2B2B2B"));
            } else {
                settings.setForceDark(WebSettings.FORCE_DARK_OFF);
                // 浅色模式恢复白色背景
                webView.setBackgroundColor(Color.WHITE);
            }
        }
        // 注入 CSS filter 作为深色模式后备方案（FORCE_DARK_ON 在某些设备不生效时保障）
        injectDarkModeCSS(webView);
    }

    /**
     * 注入 CSS filter 实现深色模式（后备方案）
     * 使用 filter: invert(0.85) hue-rotate(180deg) 反转页面颜色，
     * 与硬编码颜色不同，它保留页面原有设计关系，图片双重反转保持正常显示。
     * 当 FORCE_DARK_ON 的算法暗化不生效时（部分 OEM 设备），此方案作为可靠后备。
     */
    private void injectDarkModeCSS(WebView view) {
        String js;
        if (isDarkMode()) {
            // 注入 CSS filter 实现深色模式
            String css = "html { -webkit-filter: invert(0.85) hue-rotate(180deg); filter: invert(0.85) hue-rotate(180deg); background-color: #111 !important; }" +
                    "img, video, canvas, svg, [style*=\"background-image\"] { -webkit-filter: invert(0.85) hue-rotate(180deg); filter: invert(0.85) hue-rotate(180deg); }" +
                    ":root { color-scheme: dark !important; }";
            
            js = "(function(){" +
                    "var s=document.getElementById('__dark_mode_filter');" +
                    "if(!s){" +
                    "s=document.createElement('style');" +
                    "s.id='__dark_mode_filter';" +
                    "s.textContent='" + css.replace("'", "\\'") + "';" +
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
            view.evaluateJavascript(js, null);
            Log.d(TAG, isDarkMode() ? "Dark mode CSS filter injected" : "Dark mode CSS filter removed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to " + (isDarkMode() ? "inject" : "remove") + " dark mode CSS filter", e);
        }
    }
        
    /**
     * 应用网页设置：网页背景透明、自定义 CSS
     * 在 AI 窗口展开后调用，确保 WebView 已创建并加载了 URL
     */
    private void applyWebpageSettings() {
        if (aiWebView == null) return;
            
        // 1. 根据“网页背景透明”设置决定是否注入透明背景 CSS
        if (webpageBgTransparent && aiStyle != 0) {
            injectTransparentBackground(aiWebView);
        } else {
            removeTransparentBackground(aiWebView);
        }
            
        // 2. 注入深色模式 CSS
        injectDarkModeCSS(aiWebView);
            
        // 3. 注入自定义 CSS
        if (!TextUtils.isEmpty(customCss)) {
            injectCustomCss(aiWebView);
        }
            
        // 4. 根据设置调整 WebView 背景透明度
        if (webpageBgTransparent) {
            aiWebView.setBackgroundColor(Color.TRANSPARENT);
            Log.d(TAG, "网页背景透明已启用，WebView 背景设为透明");
        } else if (aiStyle != 0 && aiOverlayAlpha < 255) {
            int alpha = (int)(aiOverlayAlpha / 255.0f * 255);
            aiWebView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
            Log.d(TAG, "网页背景透明未启用，WebView 背景透明度=" + alpha);
        } else {
            aiWebView.setBackgroundColor(Color.TRANSPARENT);
            Log.d(TAG, "WebView 背景设为透明（不透明样式）");
        }
            
        Log.d(TAG, "网页设置已应用: webpageBgTransparent=" + webpageBgTransparent 
                + ", customCss length=" + (customCss != null ? customCss.length() : 0)
                + ", aiStyle=" + aiStyle + ", aiOverlayAlpha=" + aiOverlayAlpha);
    }
        
    /**
     * 注入透明背景 CSS，使网页背景透明
     */
    private void injectTransparentBackground(WebView view) {
        String js = "(function(){" +
                "var s=document.getElementById('__transparent_bg');" +
                "if(!s){" +
                "s=document.createElement('style');" +
                "s.id='__transparent_bg';" +
                "s.textContent='html,body{background:transparent!important;background-color:transparent!important}';" +
                "var h=document.head;if(h){h.appendChild(s);}else{document.documentElement.appendChild(s);}" +
                "}" +
                "})()";
            
        try {
            view.evaluateJavascript(js, null);
            view.setBackgroundColor(Color.TRANSPARENT);
            Log.d(TAG, "透明背景 CSS 已注入");
        } catch (Exception e) {
            Log.e(TAG, "注入透明背景 CSS 失败", e);
        }
    }
        
    /**
     * 移除透明背景 CSS
     */
    private void removeTransparentBackground(WebView view) {
        String js = "(function(){" +
                "var s=document.getElementById('__transparent_bg');" +
                "if(s){s.parentNode.removeChild(s);}" +
                "})()";
            
        try {
            view.evaluateJavascript(js, null);
            Log.d(TAG, "透明背景 CSS 已移除");
        } catch (Exception e) {
            Log.e(TAG, "移除透明背景 CSS 失败", e);
        }
    }
        
    /**
     * 注入自定义 CSS
     */
    private void injectCustomCss(WebView view) {
        if (TextUtils.isEmpty(customCss)) return;
            
        // 完整的 JavaScript 字符串转义
        String escapedCss = customCss
                .replace("\\", "\\\\")  // 先转义反斜杠
                .replace("'", "\\'")     // 再转义单引号
                .replace("\n", "\\n")    // 转义换行符
                .replace("\r", "\\r");   // 转义回车符
            
        String js = "(function(){" +
                "var s=document.getElementById('__custom_css');" +
                "if(!s){" +
                "s=document.createElement('style');" +
                "s.id='__custom_css';" +
                "s.textContent='" + escapedCss + "';" +
                "var h=document.head;if(h){h.appendChild(s);}else{document.documentElement.appendChild(s);}" +
                "}" +
                "console.log('Custom CSS injected, length: " + customCss.length() + "');" +
                "})()";
            
        try {
            view.evaluateJavascript(js, null);
            Log.d(TAG, "自定义 CSS 已注入，长度: " + customCss.length());
        } catch (Exception e) {
            Log.e(TAG, "注入自定义 CSS 失败", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DiagLog.installUncaughtHandler(this);
        DiagLog.log(this, "service onCreate");
        Log.d(TAG, "========================================");
        Log.d(TAG, "FloatWindowService onCreate 被调用");
        Log.d(TAG, "Android 版本: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "权限状态: " + hasOverlayPermission());

        // 关键修复：从 SharedPreferences 加载保存的设置
        loadSettings();
        Log.d(TAG, "AI 窗口尺寸 - 宽: " + aiWindowWidth + "px, 高: " + aiWindowHeight + "px");

        try {
            createNotificationChannel();
            Log.d(TAG, "通知渠道创建成功");

            Notification notification = createNotification();
            Log.d(TAG, "通知对象创建成功");

            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "前台服务启动成功，通知已显示");

            initBottomBar();
            Log.d(TAG, "底部提示条初始化完成");

            // 注册广播接收器，用于接收 Activity 发送的恢复提示条通知
            IntentFilter filter = new IntentFilter("com.example.aiwindow.RESTORE_BAR");
            // Android 13+ (API 33) 必须指定 RECEIVER_EXPORTED 或 RECEIVER_NOT_EXPORTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(restoreBarReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(restoreBarReceiver, filter, 0); // 使用 0 作为默认标志
            }

        } catch (Exception e) {
            Log.e(TAG, "❌❌❌ 服务启动失败", e);
            e.printStackTrace();
            stopSelf(); // 启动失败则停止服务
        }

        Log.d(TAG, "========================================");
    }

    /**
     * 检查悬浮窗权限
     */
    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            DiagLog.log(this, "onStartCommand action=" + intent.getAction());
        }
        // 处理样式更新请求
        if (intent != null && "UPDATE_STYLE".equals(intent.getAction())) {
            int width = intent.getIntExtra("width", currentWidth);
            int height = intent.getIntExtra("height", currentHeight);
            int corner = intent.getIntExtra("corner", currentCornerRadius);
            float alpha = intent.getFloatExtra("alpha", currentAlpha);
            int posX = intent.getIntExtra("position_x", barPositionX);
            int posY = intent.getIntExtra("position_y", barPositionY);
            int duration = intent.getIntExtra("long_press_duration", longPressDuration);
            int animDuration = intent.getIntExtra("anim_duration", animationDuration);

            // 按下状态样式（可选参数，不存在时保持当前值）
            if (intent.hasExtra("pressed_bar_enabled")) {
                pressedBarEnabled = intent.getBooleanExtra("pressed_bar_enabled", pressedBarEnabled);
            }
            if (intent.hasExtra("pressed_bar_color")) {
                pressedBarColor = intent.getStringExtra("pressed_bar_color");
            }
            if (intent.hasExtra("pressed_bar_alpha")) {
                pressedBarAlpha = intent.getIntExtra("pressed_bar_alpha", pressedBarAlpha);
            }
            if (intent.hasExtra("pressed_bar_width")) {
                pressedBarWidth = intent.getIntExtra("pressed_bar_width", pressedBarWidth);
            }
            if (intent.hasExtra("pressed_bar_height")) {
                pressedBarHeight = intent.getIntExtra("pressed_bar_height", pressedBarHeight);
            }
            if (intent.hasExtra("pressed_bar_corner")) {
                pressedBarCorner = intent.getIntExtra("pressed_bar_corner", pressedBarCorner);
            }
            if (intent.hasExtra("pressed_bar_feather_width")) {
                pressedBarFeatherWidth = intent.getIntExtra("pressed_bar_feather_width", pressedBarFeatherWidth);
            }
            if (intent.hasExtra("pressed_bar_position_y")) {
                pressedBarPositionY = intent.getIntExtra("pressed_bar_position_y", pressedBarPositionY);
            }
            if (intent.hasExtra("pressed_bar_glow_radius")) {
                pressedBarGlowRadius = intent.getIntExtra("pressed_bar_glow_radius", pressedBarGlowRadius);
            }
            DiagLog.log(this, "UPDATE_STYLE w=" + width + " h=" + height + " posY=" + posY
                    + " pressed=" + pressedBarWidth + "x" + pressedBarHeight + "dp glowR=" + pressedBarGlowRadius);
            if (intent.hasExtra("pressed_bar_color_mode")) {
                String colorMode = intent.getStringExtra("pressed_bar_color_mode");
                if (colorMode != null && !colorMode.isEmpty()) {
                    pressedBarColorMode = colorMode;
                }
            }

            updateBarStyles(width, height, corner, alpha);
            updateBarPosition(posX, posY);
            updateLongPressDuration(duration);
            if (animDuration != animationDuration) {
                animationDuration = animDuration;
                Log.d(TAG, "动画速度已更新: " + animationDuration + "ms");
            }
            // 遮罩渐变时长
            if (intent.hasExtra("overlay_anim_duration")) {
                int overlayAnim = intent.getIntExtra("overlay_anim_duration", overlayAnimDuration);
                if (overlayAnim != overlayAnimDuration) {
                    overlayAnimDuration = overlayAnim;
                    Log.d(TAG, "遮罩渐变时长已更新: " + overlayAnimDuration + "ms");
                }
            }

            // 按下样式预览（设置页调节按下样式时使用）
            if (intent.getBooleanExtra("show_pressed_preview", false)) {
                pressedPreviewHandler.removeCallbacks(pressedPreviewTimeout);
                applyPressedBarStyles();
                pressedPreviewHandler.postDelayed(pressedPreviewTimeout, 1000);
                Log.d(TAG, "按下样式预览已触发，1秒无操作后自动隐藏");
            }
        }
        // 处理 AI 窗口大小更新请求
        else if (intent != null && "UPDATE_WINDOW_SIZE".equals(intent.getAction())) {
            int windowWidth = intent.getIntExtra("window_width", aiWindowWidth);
            int windowHeight = intent.getIntExtra("window_height", aiWindowHeight);
            int winPosX = intent.getIntExtra("window_position_x", windowPositionX);
            int winPosY = intent.getIntExtra("window_position_y", windowPositionY);

            // 获取边框、圆角和阴影设置
            int bWidth = intent.getIntExtra("border_width", borderWidth);
            int cRadius = intent.getIntExtra("corner_radius", cornerRadius); // 新增：圆角半径
            String bColor = intent.getStringExtra("border_color");
            float bAlpha = intent.getFloatExtra("border_alpha", borderAlpha);
            int sWidth = intent.getIntExtra("shadow_width", shadowWidth);
            String sColor = intent.getStringExtra("shadow_color");
            float sAlpha = intent.getFloatExtra("shadow_alpha", shadowAlpha);
            boolean showCloseBtn = intent.getBooleanExtra("show_close_button", true); // 新增：关闭按钮显示
            boolean showRefreshBtn = intent.getBooleanExtra("show_refresh_button", true); // 显示刷新按钮
            boolean showBackBtn = intent.getBooleanExtra("show_back_button", true); // 显示后退按钮
            boolean closeOnOutside = intent.getBooleanExtra("close_on_outside_click", true); // 点击窗口外关闭窗口
            boolean backPrioritize = intent.getBooleanExtra("back_prioritize_webview", true); // 侧边返回手势行为
            windowRadius = cRadius;

            // 获取背景遮罩设置
            String overlayCol = intent.getStringExtra("overlay_color");
            int overlayAlp = intent.getIntExtra("overlay_alpha", overlayAlpha);
            if (overlayCol != null) {
                overlayColor = overlayCol;
            }
            overlayAlpha = overlayAlp;

            // 遮罩渐变时长
            if (intent.hasExtra("overlay_anim_duration")) {
                int overlayAnim = intent.getIntExtra("overlay_anim_duration", overlayAnimDuration);
                if (overlayAnim != overlayAnimDuration) {
                    overlayAnimDuration = overlayAnim;
                    Log.d(TAG, "遮罩渐变时长已更新: " + overlayAnimDuration + "ms");
                }
            }

            if (intent.hasExtra("use_expand_directly")) {
                useExpandDirectly = intent.getBooleanExtra("use_expand_directly", false);
                Log.d(TAG, "useExpandDirectly 已更新: " + useExpandDirectly);
            }

            // 更新遮罩窗口外观（如果已创建）
            updateOverlayAppearance();

            updateWindowSize(windowWidth, windowHeight);
            updateWindowPosition(winPosX, winPosY);
            updateWindowBorder(bWidth, cRadius, bColor, bAlpha, sWidth, sColor, sAlpha);
            updateShowCloseButton(showCloseBtn);
            updateShowRefreshButton(showRefreshBtn);
            updateShowBackButton(showBackBtn);
            closeOnOutsideClick = closeOnOutside;
            Log.d(TAG, "点击窗口外关闭窗口设置已更新: " + closeOnOutsideClick);
            backPrioritizeWebview = backPrioritize;
            Log.d(TAG, "侧边返回手势设置已更新: " + backPrioritizeWebview);

            // 单击提示条打开的应用设置
            if (intent.hasExtra("click_app_package")) {
                clickAppPackage = intent.getStringExtra("click_app_package");
                clickAppName = intent.getStringExtra("click_app_name");
                clickAppClass = intent.getStringExtra("click_app_class");
                Log.d(TAG, "单击打开应用已更新: " + clickAppName + " (" + clickAppPackage + ")");
            }

            // 触发行为配置
            if (intent.hasExtra("click_action")) {
                clickAction = intent.getStringExtra("click_action");
            }
            if (intent.hasExtra("double_click_action")) {
                doubleClickAction = intent.getStringExtra("double_click_action");
            }
            if (intent.hasExtra("long_press_action")) {
                longPressAction = intent.getStringExtra("long_press_action");
            }
            if (intent.hasExtra("double_click_app_package")) {
                doubleClickAppPackage = intent.getStringExtra("double_click_app_package");
                doubleClickAppName = intent.getStringExtra("double_click_app_name");
                doubleClickAppClass = intent.getStringExtra("double_click_app_class");
            }
            if (intent.hasExtra("long_press_app_package")) {
                longPressAppPackage = intent.getStringExtra("long_press_app_package");
                longPressAppName = intent.getStringExtra("long_press_app_name");
                longPressAppClass = intent.getStringExtra("long_press_app_class");
            }
            if (intent.hasExtra("click_open_in_window")) {
                clickOpenInWindow = intent.getBooleanExtra("click_open_in_window", false);
                doubleClickOpenInWindow = intent.getBooleanExtra("double_click_open_in_window", false);
                longPressOpenInWindow = intent.getBooleanExtra("long_press_open_in_window", false);
            }
            if (intent.hasExtra("click_vibrate")) {
                clickVibrate = intent.getBooleanExtra("click_vibrate", clickVibrate);
                doubleClickVibrate = intent.getBooleanExtra("double_click_vibrate", doubleClickVibrate);
                longPressVibrate = intent.getBooleanExtra("long_press_vibrate", longPressVibrate);
            }
        }
        // 处理输入法避让设置更新
        else if (intent != null && "UPDATE_KEYBOARD_SETTINGS".equals(intent.getAction())) {
            keyboardOffset = intent.getIntExtra("keyboard_offset", keyboardOffset);
            keyboardAnimDuration = intent.getIntExtra("keyboard_anim_duration", keyboardAnimDuration);
            // 保存到 prefs（下次启动时生效）
            getSharedPreferences("ai_window_settings", MODE_PRIVATE).edit()
                    .putInt("keyboard_offset", keyboardOffset)
                    .putInt("keyboard_anim_duration", keyboardAnimDuration)
                    .apply();
            Log.d(TAG, "输入法设置已更新: offset=" + keyboardOffset + ", animDuration=" + keyboardAnimDuration);
        }
        // 处理快捷方式窗口设置更新
        else if (intent != null && "UPDATE_SHORTCUT_SETTINGS".equals(intent.getAction())) {
            shortcutWindowWidth = intent.getIntExtra("shortcut_window_width", shortcutWindowWidth);
            shortcutWindowHeight = intent.getIntExtra("shortcut_window_height", shortcutWindowHeight);
            shortcutWindowPositionY = intent.getIntExtra("shortcut_window_position_y", shortcutWindowPositionY);
            shortcutCornerRadius = intent.getIntExtra("shortcut_corner_radius", shortcutCornerRadius);
            shortcutIconSize = intent.getIntExtra("shortcut_icon_size", shortcutIconSize);
            shortcutIconCorner = intent.getIntExtra("shortcut_icon_corner", shortcutIconCorner);
            tryFullscreenWindowing = intent.getBooleanExtra("try_fullscreen_windowing", tryFullscreenWindowing);
            customWindowingMode = intent.getIntExtra("custom_windowing_mode", customWindowingMode);
            shortcutStyle = intent.getIntExtra("shortcut_style", shortcutStyle);
            shortcutBlurRadius = intent.getIntExtra("shortcut_blur_radius", shortcutBlurRadius);
            shortcutOverlayAlpha = intent.getIntExtra("shortcut_overlay_alpha", shortcutOverlayAlpha);

            // 如果窗口已展开，需要重建
            if (isShortcutWindowExpanded) {
                updateShortcutWindowStyle();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        collapseShortcutWindowInternal();
                    }
                });
            }
        }
        // 快捷窗口格子点击请求：由服务上下文启动应用（app 前台时立即启动，
        // 避免退后台后被 OEM 静默拦截）
        else if (intent != null && "LAUNCH_APP".equals(intent.getAction())) {
            Intent launchIntent = intent.getParcelableExtra("launch_intent");
            boolean openInWindow = intent.getBooleanExtra("open_in_window", false);
            String appName = intent.getStringExtra("app_name");
            if (launchIntent == null) {
                Log.e(TAG, "LAUNCH_APP 缺少 launch_intent");
            } else {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startWithWindowing(launchIntent, openInWindow);
                    Log.d(TAG, "格子点击已启动: " + appName);
                } catch (Exception e) {
                    Log.e(TAG, "格子点击打开应用失败: " + appName, e);
                    Toast.makeText(this, "打开 " + appName + " 失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
        // 处理 AI URL 更新请求
        else if (intent != null && "UPDATE_AI_URL".equals(intent.getAction())) {
            String aiUrl = intent.getStringExtra("ai_url");
            updateAiUrl(aiUrl);
        }
        // 处理 AI 窗口模糊效果设置更新
        else if (intent != null && "UPDATE_AI_BLUR_SETTINGS".equals(intent.getAction())) {
            aiStyle = intent.getIntExtra("ai_style", aiStyle);
            aiBlurRadius = intent.getIntExtra("ai_blur_radius", aiBlurRadius);
            aiOverlayAlpha = intent.getIntExtra("ai_overlay_alpha", aiOverlayAlpha);
            
            // 当样式为不透明时，强制网页透明度为 100%
            if (aiStyle == 0) {
                aiOverlayAlpha = 255;
            }
            
            Log.d(TAG, "AI 窗口模糊效果已更新: style=" + aiStyle + ", blurRadius=" + aiBlurRadius + ", overlayAlpha=" + aiOverlayAlpha);
        }
        // 处理顶部小横条透明度更新
        else if (intent != null && "UPDATE_TOP_BAR_TRANSPARENCY".equals(intent.getAction())) {
            topBarTransparency = intent.getIntExtra("top_bar_transparency", topBarTransparency);
            Log.d(TAG, "顶部小横条透明度已更新: " + topBarTransparency);
            // 如果topBarView已存在，立即更新背景
            if (topBarView != null && topBarView.getBackground() instanceof GradientDrawable) {
                int bgAlpha255 = (int)(Math.min(100, Math.max(0, topBarTransparency)) * 255.0f / 100.0f);
                GradientDrawable bg = (GradientDrawable) topBarView.getBackground();
                bg.setColor(Color.argb(bgAlpha255, 0x88, 0x88, 0x88));
            }
        }
        // 处理单击打开应用设置更新
        else if (intent != null && "UPDATE_CLICK_APP".equals(intent.getAction())) {
            if (intent.hasExtra("click_app_package")) {
                clickAppPackage = intent.getStringExtra("click_app_package");
                clickAppName = intent.getStringExtra("click_app_name");
                clickAppClass = intent.getStringExtra("click_app_class");
                Log.d(TAG, "单击打开应用设置已更新: " + clickAppName + " (" + clickAppPackage + ")");
            }
        }
        // 处理触发行为配置更新
        else if (intent != null && "UPDATE_TRIGGER_ACTIONS".equals(intent.getAction())) {
            if (intent.hasExtra("click_action")) {
                clickAction = intent.getStringExtra("click_action");
                doubleClickAction = intent.getStringExtra("double_click_action");
                longPressAction = intent.getStringExtra("long_press_action");

                clickAppPackage = intent.getStringExtra("click_app_package");
                clickAppName = intent.getStringExtra("click_app_name");
                clickAppClass = intent.getStringExtra("click_app_class");

                doubleClickAppPackage = intent.getStringExtra("double_click_app_package");
                doubleClickAppName = intent.getStringExtra("double_click_app_name");
                doubleClickAppClass = intent.getStringExtra("double_click_app_class");

                longPressAppPackage = intent.getStringExtra("long_press_app_package");
                longPressAppName = intent.getStringExtra("long_press_app_name");
                longPressAppClass = intent.getStringExtra("long_press_app_class");

                clickOpenInWindow = intent.getBooleanExtra("click_open_in_window", clickOpenInWindow);
                doubleClickOpenInWindow = intent.getBooleanExtra("double_click_open_in_window", doubleClickOpenInWindow);
                longPressOpenInWindow = intent.getBooleanExtra("long_press_open_in_window", longPressOpenInWindow);

                clickVibrate = intent.getBooleanExtra("click_vibrate", clickVibrate);
                doubleClickVibrate = intent.getBooleanExtra("double_click_vibrate", doubleClickVibrate);
                longPressVibrate = intent.getBooleanExtra("long_press_vibrate", longPressVibrate);

                Log.d(TAG, "触发行为配置已更新: click=" + clickAction + " double=" + doubleClickAction + " long=" + longPressAction);
            }
        }
        return START_STICKY; // 服务被杀死后自动重启
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "悬浮窗服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持悬浮窗在后台运行");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建前台通知
     */
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮窗正在运行")
                .setContentText("点击可关闭悬浮窗")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统图标
                .setOngoing(true) // 设置为持续通知，不可滑动删除
                .build();
    }

    /**
     * 初始化底部提示条
     */
    private void initBottomBar() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 创建容器
        barContainer = new FrameLayout(this);
        barContainer.setClipChildren(false); // 允许子视图超出容器边界（覆盖层尺寸较大时不被截断）

        // 设置提示条布局参数
        barLayoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            barLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            barLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 关键配置：不拦截外部点击，但能响应自身触摸事件
        barLayoutParams.format = PixelFormat.RGBA_8888;
        barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
//                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; // 允许布局超出屏幕范围


        barLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
        // 横条窗口尺寸恒等于设置的触发区（手势期间绝不缩放）；按压视觉由独立光晕窗口承载
        barLayoutParams.width = computeBarWindowWidth();
        barLayoutParams.height = computeBarWindowHeight();

        // CENTER_VERTICAL|CENTER_HORIZONTAL 下：x=barPositionX(居中偏移), y=dpToPx(barPositionY)(居中偏移)
        barLayoutParams.x = barPositionX;
        barLayoutParams.y = dpToPx(barPositionY);

        Log.d(TAG, "提示条参数 - 宽度: " + currentWidth + ", 高度: " + dpToPx(currentHeight) + "px");
        Log.d(TAG, "提示条参数 - 圆角: " + currentCornerRadius + "dp, 透明度: " + currentAlpha);
        Log.d(TAG, "提示条位置 - X: " + barPositionX + ", Y: " + barPositionY + "dp (正值向上)");

        // 创建提示条视图（仅横条在横条窗口内）
        bottomBar = createBottomBarView();
        barContainer.addView(bottomBar);

        // 按下覆盖层与光晕放入独立光晕窗口（不可触摸、恒定尺寸）
        createGlowWindow();
        glowOverlay = createGlowOverlay();
        glowContainer.addView(glowOverlay);
        pressedOverlay = createPressedOverlay();
        glowContainer.addView(pressedOverlay);


        // 添加到底部窗口（在遮罩之上）
        try {
            windowManager.addView(barContainer, barLayoutParams);
            Log.d(TAG, "底部提示条已成功添加到窗口");
        } catch (Exception e) {
            Log.e(TAG, "添加底部提示条失败", e);
            e.printStackTrace();
        }

        // 光晕窗口不在服务启动时挂载：仅按下瞬间挂载、松手淡出后移除
        //（该机型对 FLAG_NOT_TOUCHABLE 处理异常，常驻光晕窗口会拦截其覆盖范围内的系统弹窗点击）
        DiagLog.log(this, "initBottomBar bar=" + barLayoutParams.width + "x" + barLayoutParams.height
                + " @(" + barLayoutParams.x + "," + barLayoutParams.y + ")"
                + " fl=0x" + Integer.toHexString(barLayoutParams.flags)
                + " glow=" + glowLayoutParams.width + "x" + glowLayoutParams.height
                + " fl=0x" + Integer.toHexString(glowLayoutParams.flags)
                + " cfg w=" + currentWidth + " h=" + currentHeight + "dp posY=" + barPositionY
                + " pressed=" + pressedBarWidth + "x" + pressedBarHeight + "dp glowR=" + pressedBarGlowRadius);
        barContainer.post(new Runnable() {
            @Override
            public void run() {
                DiagLog.log(FloatWindowService.this, "layout barContainer=" + barContainer.getWidth() + "x" + barContainer.getHeight()
                        + " bottomBar=" + (bottomBar != null ? bottomBar.getWidth() + "x" + bottomBar.getHeight() : "null")
                        + " children=" + barContainer.getChildCount());
            }
        });
    }

    /**
     * 创建独立光晕窗口的容器与参数（initBottomBar 中在横条窗口之后 addView，保证 Z 序更高）。
     * FLAG_NOT_TOUCHABLE：触摸完全穿透 →即使窗口大于横条也不产生死区；
     * 尺寸恒定：按压/松手只做 alpha 动画，手势期间绝不 resize
     * （动态缩窗会在手势中触发系统取消事件与 surface 过渡错位）
     */
    private void createGlowWindow() {
        glowContainer = new FrameLayout(this);
        glowContainer.setClipChildren(false);

        glowLayoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            glowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            glowLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        glowLayoutParams.format = PixelFormat.RGBA_8888;
        glowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        glowLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
        glowLayoutParams.width = computeGlowWindowWidth();
        glowLayoutParams.height = computeGlowWindowHeight();
        glowLayoutParams.x = barLayoutParams.x;
        glowLayoutParams.y = barLayoutParams.y;
    }

    /**
     * 创建底部提示条视图
     */
    private View createBottomBarView() {
        FeatheredFrameLayout bar = new FeatheredFrameLayout(this);

        // 设置尺寸（bottomBar 对齐容器底部，使覆盖层在容器顶部时有正确层次）
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                currentWidth,
                dpToPx(currentHeight)
        );
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
        bar.setLayoutParams(params);

        // 创建圆角背景
        GradientDrawable background = createRoundedBackground(
                currentCornerRadius,
                currentAlpha
        );
        bar.setBackground(background);

        // 新增：开启硬件图层缓存，减少 GPU 合成开销
        bar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // 设置内边距
        int padding = dpToPx(16);
        bar.setPadding(padding, 0, padding, 0);

        // 关键功能：实现自定义长按时间的触摸监听器
        final Handler longPressHandler = new Handler(Looper.getMainLooper());

        // 标记长按是否已触发，用于阻止 ACTION_UP 传播到 OnClickListener
        final boolean[] longPressTriggered = {false};

        final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                // 防御：窗口打开期间不触发长按流程（含提示条淡出与展开）
                if (isAnyWindowOpen()) {
                    return;
                }
                // 标记长按已触发，用于 ACTION_UP 中阻止点击事件
                longPressTriggered[0] = true;

                // 长按触发前先淡出按下覆盖层（不触发窗口恢复）
                restoreNormalBarStyles(false);

                // 淡出提示条后执行展开（Activity模式，直接启动OverlayWindowActivity）
                // 不再需要startWindowHostActivity，由Activity自身处理返回键
                if (bottomBar != null) {
                    bottomBar.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            // 恢复窗口位置和大小为正常值（展开动画需从正常尺寸开始）
                            applyStyleChanges();
                            onBarLongPressed();
                        }
                    }).start();
                } else {
                    onBarLongPressed();
                }
            }
        };

        // 记录按下时的坐标，用于判断是否有大幅移动
        final float[] downX = {0};
        final float[] downY = {0};
        final int MOVE_THRESHOLD = dpToPx(10); // 移动阈值：10dp

        // 双击检测用处理器
        tapHandler = new Handler(Looper.getMainLooper());
        singleTapRunnable = new Runnable() {
            @Override
            public void run() {
                // 超时后执行单击行为，未被双击覆盖
                if (!isShortcutWindowExpanded && !isAiWindowExpanded) {
                    performGestureAction("click");
                    Log.d(TAG, "单击触发");
                }
            }
        };

        bar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 窗口已打开（前台可见）：完全屏蔽提示条的单击/双击/长按
                //（无按下样式、无长按计时、无单双击判定，事件静默吞掉）
                if (isAnyWindowOpen()) {
                    return true;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 重置长按标记
                        longPressTriggered[0] = false;
                        // 手指按下时，记录坐标、应用按下样式并启动延迟任务
                        downX[0] = event.getX();
                        downY[0] = event.getY();
                        DiagLog.log(FloatWindowService.this, "bar_touch DOWN (" + (int) event.getX() + "," + (int) event.getY() + ")");
                        applyPressedBarStyles();
                        longPressHandler.postDelayed(longPressRunnable, longPressDuration);
                        Log.d(TAG, "手指按下，开始计时: " + longPressDuration + "ms");
                        break;
                    case MotionEvent.ACTION_UP:
                        // 手指抬起时，移除长按任务并恢复样式
                        longPressHandler.removeCallbacks(longPressRunnable);
                        restoreNormalBarStyles();
                        Log.d(TAG, "手指抬起，取消长按");
                        // 如果长按已触发，消费此事件
                        if (longPressTriggered[0]) {
                            longPressTriggered[0] = false;
                            tapHandler.removeCallbacks(singleTapRunnable);
                            return true;
                        }
                        // 处理双击/单击
                        // 取消之前的延迟单击
                        tapHandler.removeCallbacks(singleTapRunnable);
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                            // 两次快速点击 → 双击
                            lastTapTime = 0;
                            onBarDoubleClicked();
                            Log.d(TAG, "双击触发");
                        } else {
                            // 第一次点击，记录时间并等待双击超时
                            lastTapTime = now;
                            tapHandler.postDelayed(singleTapRunnable, DOUBLE_TAP_TIMEOUT);
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        // 事件取消时，移除长按任务并恢复样式
                        longPressHandler.removeCallbacks(longPressRunnable);
                        restoreNormalBarStyles();
                        tapHandler.removeCallbacks(singleTapRunnable);
                        Log.d(TAG, "事件取消，移除长按任务");
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // 检查是否移动超过阈值
                        float deltaX = Math.abs(event.getX() - downX[0]);
                        float deltaY = Math.abs(event.getY() - downY[0]);
                        if (deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            restoreNormalBarStyles();
                            Log.d(TAG, "移动超过阈值 (" + deltaX + ", " + deltaY + ")，取消长按");
                        }
                        break;
                }
                return true; // 消费事件，防止事件穿透
            }
        });

        return bar;
    }

    /**
     * 创建圆角背景
     */
    private GradientDrawable createRoundedBackground(int cornerRadiusDp, float alpha) {
        return createRoundedBackground(cornerRadiusDp, alpha, "#000000");
    }

    /**
     * 创建圆角背景（支持自定义颜色）
     */
    private GradientDrawable createRoundedBackground(int cornerRadiusDp, float alpha, String colorHex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);

        // 关键修复：分别设置四个角的圆角半径，确保所有角都有圆角
        float radius = dpToPx(cornerRadiusDp);
        drawable.setCornerRadii(new float[]{
                radius, radius,  // 左上
                radius, radius,  // 右上
                radius, radius,  // 右下
                radius, radius   // 左下
        });

        int color = parseColorWithAlpha(colorHex, alpha);
        drawable.setColor(color);

        return drawable;
    }

    /**
     * 更新提示条样式
     *
     * @param width        宽度（像素），使用 MATCH_PARENT 或具体像素值
     * @param height       高度（dp）
     * @param cornerRadius 圆角半径（dp）
     * @param alpha        透明度 0.0-1.0
     */
    public void updateBarStyles(int width, int height, int cornerRadius, float alpha) {
        currentWidth = width;
        currentHeight = height;
        currentCornerRadius = cornerRadius;
        currentAlpha = alpha;

        Log.d(TAG, "更新样式 - 宽: " + width + ", 高: " + height + "dp, 圆角: " + cornerRadius + "dp, 透明度: " + alpha);

        // 关键修复：立即在主线程执行，不使用 Handler.post 延迟
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyStyleChanges();
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    applyStyleChanges();
                }
            });
        }
    }

    /** 横条窗口尺寸恒等于设置的触发区（手势期间绝不缩放，避免输入取消与画面过渡问题） */
    private int computeBarWindowWidth() {
        if (currentWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }
        return currentWidth;
    }

    private int computeBarWindowHeight() {
        return dpToPx(currentHeight);
    }

    /** 光晕窗口尺寸恒定：容纳核心层+位置偏移+光晕，不可触摸故不产生死区 */
    private int computeGlowWindowWidth() {
        if (currentWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }
        return Math.max(currentWidth, pressedBarWidth + 2 * dpToPx(pressedBarGlowRadius));
    }

    private int computeGlowWindowHeight() {
        return Math.max(dpToPx(currentHeight), dpToPx(pressedBarHeight)
                + 2 * Math.abs(dpToPx(pressedBarPositionY))
                + 2 * dpToPx(pressedBarGlowRadius));
    }

    /** 光晕窗口与横条窗口对齐（同 gravity/x/y），尺寸按当前设置重算 */
    private void syncGlowWindow() {
        if (glowContainer == null || glowLayoutParams == null || windowManager == null || barLayoutParams == null) return;
        glowLayoutParams.width = computeGlowWindowWidth();
        glowLayoutParams.height = computeGlowWindowHeight();
        glowLayoutParams.x = barLayoutParams.x;
        glowLayoutParams.y = barLayoutParams.y;
        if (glowContainer.getWindowToken() == null) {
            return; // 未挂载（静止态）：仅保留参数，挂载时机=按下
        }
        try {
            windowManager.updateViewLayout(glowContainer, glowLayoutParams);
        } catch (Exception e) {
            Log.e(TAG, "同步光晕窗口失败", e);
        }
    }

    /**
     * 应用样式更改到视图
     */
    private void applyStyleChanges() {
        if (bottomBar != null && barLayoutParams != null && windowManager != null) {
            try {
                // 横条窗口尺寸恒等于设置的触发区（手势期间绝不缩放）；按压视觉由独立光晕窗口承载
                barLayoutParams.width = computeBarWindowWidth();
                barLayoutParams.height = computeBarWindowHeight();
                barLayoutParams.x = barPositionX;
                barLayoutParams.y = dpToPx(barPositionY); // 正值越大提示条越靠上

                // 关键修复：同时更新 bottomBar 内部的 LayoutParams，确保宽度/高度实时生效
                FrameLayout.LayoutParams bottomLp = (FrameLayout.LayoutParams) bottomBar.getLayoutParams();
                bottomLp.width = currentWidth;
                bottomLp.height = dpToPx(currentHeight);
                bottomLp.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
                bottomBar.setLayoutParams(bottomLp);
                GradientDrawable background = createRoundedBackground(
                        currentCornerRadius,
                        currentAlpha
                );
                bottomBar.setBackground(background);

                // 强制刷新布局
                bottomBar.requestLayout();

                // 应用更新
                windowManager.updateViewLayout(barContainer, barLayoutParams);
                syncGlowWindow();
                Log.d(TAG, "样式已实时更新");
            } catch (Exception e) {
                Log.e(TAG, "更新样式失败", e);
            }
        } else {
            Log.w(TAG, "无法更新样式：视图未初始化");
        }
    }

    /**
     * 创建按下状态覆盖层
     * 用于按下/释放时透明度渐变显示按下样式
     */
    private View createPressedOverlay() {
        FeatheredFrameLayout overlay = new FeatheredFrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                pressedBarWidth,
                dpToPx(pressedBarHeight)
        );
        params.gravity = Gravity.CENTER_HORIZONTAL;
        overlay.setLayoutParams(params);

        // 设置按下样式背景
        GradientDrawable bg = createRoundedBackground(pressedBarCorner, pressedBarAlpha / 100.0f, pressedBarColor);
        overlay.setBackground(bg);

        // 启用边缘羽化
        int pressedColor = parseColorWithAlpha(pressedBarColor, pressedBarAlpha / 100.0f);
        overlay.setFeatherEnabled(true, pressedColor, pressedBarCorner, pressedBarFeatherWidth);

        overlay.setAlpha(0f);
        return overlay;
    }

    /**
     * 创建光晕层（小横条作为光源，光经高斯模糊后向四周散射）
     * 尺寸 = 按下样式尺寸 + 2×光晕半径（窗口尺寸公式同步扩边，保证不被 Surface 裁切）
     */
    private View createGlowOverlay() {
        FrameLayout container = new FrameLayout(this);
        int glowPx = dpToPx(pressedBarGlowRadius);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                pressedBarWidth + glowPx * 2,
                dpToPx(pressedBarHeight) + glowPx * 2);
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
        container.setLayoutParams(params);

        // 双层光晕：外层柔和远光 + 内层亮芯（RenderEffect 单视图只能一个，故分两层实现层次感）
        glowOuter = new GlowView(this);
        container.addView(glowOuter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        glowInner = new GlowView(this);
        glowInner.setAlpha(0.55f);
        container.addView(glowInner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        updateGlowBackground(container);
        applyGlowEffect(container);
        container.setAlpha(0f);
        return container;
    }

    /**
     * 按颜色模式更新双层光晕的内容（自定义颜色=同色圆角矩形，彩虹=流光渐变）
     */
    private void updateGlowBackground(View glow) {
        updateGlowContent(glowOuter);
        updateGlowContent(glowInner);
    }

    private void updateGlowContent(GlowView target) {
        if (target == null) return;
        if ("rainbow".equals(pressedBarColorMode)) {
            target.setContent(new RainbowDrawable(dpToPx(pressedBarCorner)));
        } else {
            target.setContent(createRoundedBackground(pressedBarCorner, pressedBarAlpha / 100.0f, pressedBarColor));
        }
    }

    /**
     * 应用/更新双层光晕模糊特效（光晕半径0=关闭光晕）
     * 内容内缩 R 绘制，四周留 R 宽透明带：
     * 外层 sigma=R/2（尾迹在透明带内收敛到 ~2%，无硬边）；内层 sigma=R/6（亮芯）
     */
    private void applyGlowEffect(View glow) {
        int glowPx = dpToPx(pressedBarGlowRadius);
        if (glowPx <= 0) {
            glow.setVisibility(View.INVISIBLE);
            if (glowOuter != null) glowOuter.setRenderEffect(null);
            if (glowInner != null) glowInner.setRenderEffect(null);
            return;
        }
        glow.setVisibility(View.VISIBLE);
        if (glowOuter != null) {
            float sigmaOut = Math.max(glowPx / 2f, 1f);
            glowOuter.setRenderEffect(RenderEffect.createBlurEffect(sigmaOut, sigmaOut, Shader.TileMode.CLAMP));
        }
        if (glowInner != null) {
            float sigmaIn = Math.max(glowPx / 6f, 1f);
            glowInner.setRenderEffect(RenderEffect.createBlurEffect(sigmaIn, sigmaIn, Shader.TileMode.CLAMP));
        }
    }

    /**
     * 光晕子视图：内容（横条形状）内缩光晕半径居中绘制，外围留透明带，
     * 经 RenderEffect 高斯模糊后，光从横条形状向外散射（而非整块实心矩形）
     */
    private class GlowView extends View {
        private Drawable content;

        GlowView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void setContent(Drawable drawable) {
            this.content = drawable;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (content == null) return;
            int inset = dpToPx(pressedBarGlowRadius);
            int w = getWidth();
            int h = getHeight();
            if (w <= inset * 2 || h <= inset * 2) return;
            content.setBounds(inset, inset, w - inset, h - inset);
            content.draw(canvas);
        }
    }

    /**
     * 彩虹流光模式：按住期间启动无限流光动画（松开由 restoreNormalBarStyles 取消）
     */
    private void startRainbowFlowIfNeeded() {
        if (!"rainbow".equals(pressedBarColorMode)) {
            if (rainbowAnimator != null) rainbowAnimator.cancel();
            return;
        }
        if (rainbowAnimator != null && rainbowAnimator.isRunning()) return;
        rainbowAnimator = ValueAnimator.ofFloat(0f, 1f);
        rainbowAnimator.setDuration(2500);
        rainbowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rainbowAnimator.setInterpolator(new LinearInterpolator());
        rainbowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                rainbowFlow = (float) animation.getAnimatedValue();
                if (pressedOverlay != null) pressedOverlay.invalidate();
                if (glowOverlay != null) glowOverlay.invalidate();
            }
        });
        rainbowAnimator.start();
    }

    /**
     * 彩虹流光圆角矩形 Drawable：沿宽度方向的彩虹渐变，
     * 通过平移 shader 相位实现无缝循环流动（rainbowFlow 为核心层/光晕层共享字段）
     * 透明度烘焙进绘制，与自定义颜色模式的 createRoundedBackground 语义一致
     */
    private class RainbowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Matrix flowMatrix = new Matrix();
        private LinearGradient cachedShader;
        private float cachedWidth = -1f;
        private float cachedHeight = -1f;
        private float cornerPx;

        RainbowDrawable(float cornerRadiusPx) {
            this.cornerPx = cornerRadiusPx;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            rect.set(b);
            float r = Math.min(cornerPx, Math.min(rect.width(), rect.height()) / 2f);
            if (cachedShader == null || cachedWidth != rect.width() || cachedHeight != rect.height()) {
                int[] colors = {
                        0xFFFF1B6B, 0xFFFF8A00, 0xFFFFD500, 0xFF34D399,
                        0xFF22D3EE, 0xFF6366F1, 0xFFA855F7, 0xFFFF1B6B
                };
                float[] positions = {0f, 0.15f, 0.30f, 0.45f, 0.60f, 0.75f, 0.90f, 1f};
                cachedShader = new LinearGradient(0f, 0f, Math.max(rect.width(), 1f), 0f,
                        colors, positions, Shader.TileMode.REPEAT);
                cachedWidth = rect.width();
                cachedHeight = rect.height();
            }
            // 流光：平移一个渐变周期实现无缝循环
            flowMatrix.setTranslate(-rainbowFlow * rect.width(), 0f);
            cachedShader.setLocalMatrix(flowMatrix);
            paint.setShader(cachedShader);
            paint.setAlpha(Math.round(pressedBarAlpha / 100.0f * 255));
            canvas.drawRoundRect(rect, r, r, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * 应用按下状态样式（手指按住提示条时的视觉效果）
     */
    /** 光晕窗口拆卸任务（按下样式淡出完成后执行） */
    private final Runnable glowWindowDetachRunnable = new Runnable() {
        @Override
        public void run() {
            detachGlowWindow();
        }
    };

    /**
     * 按下瞬间挂载光晕窗口（服务启动/静止态不挂载）。
     * 该机型对 FLAG_NOT_TOUCHABLE 处理异常：常驻光晕窗口会拦截其覆盖范围内、
     * 横条之外的系统弹窗点击；改为仅按下期间存在后，静止态死区消失。
     */
    private void ensureGlowWindowAttached() {
        if (glowContainer == null || glowLayoutParams == null || windowManager == null) return;
        pressedPreviewHandler.removeCallbacks(glowWindowDetachRunnable);
        if (glowContainer.getWindowToken() != null) return;
        try {
            windowManager.addView(glowContainer, glowLayoutParams);
            DiagLog.log(this, "glow attached (press)");
        } catch (Exception e) {
            Log.e(TAG, "挂载光晕窗口失败", e);
        }
    }

    /** 松手淡出后拆卸光晕窗口，恢复静止态无死区 */
    private void detachGlowWindow() {
        if (glowContainer == null || windowManager == null) return;
        if (glowContainer.getWindowToken() == null) return;
        try {
            windowManager.removeView(glowContainer);
            DiagLog.log(this, "glow detached (rest)");
        } catch (Exception e) {
            Log.e(TAG, "拆卸光晕窗口失败", e);
        }
    }

    private void applyPressedBarStyles() {
        if (bottomBar == null || pressedOverlay == null || barLayoutParams == null || windowManager == null)
            return;
        if (!pressedBarEnabled) return;
        if (isAiWindowExpanded) return;

        try {
            // 按下瞬间挂载光晕窗口（静止态不挂载，避免死区）
            ensureGlowWindowAttached();

            // 取消正在进行的动画
            pressedOverlay.animate().cancel();

            // === 关键修复：不改变 barContainer 的正常位置 ===
            // 覆盖层使用按下样式设置项（pressedBarWidth/Height/Corner），
            // 仅通过透明度变换显示按下效果，避免容器位置变化导致的位移和缩放
            // 容器已有 setClipChildren(false)，覆盖层可超出容器布局边界渲染

            // == 第一步：更新覆盖层样式 ==
            // 覆盖层/光晕位于独立光晕窗口（恒定尺寸），按下只做透明度动画，不触碰任何窗口尺寸

            // 日志：打印光晕窗口与覆盖层尺寸用于调试裁剪问题
            int containerW = (glowLayoutParams != null) ? glowLayoutParams.width : computeGlowWindowWidth();
            int containerH = (glowLayoutParams != null) ? glowLayoutParams.height : computeGlowWindowHeight();
            Log.d(TAG, "[PRESSED_DEBUG] 光晕窗口尺寸: w=" + containerW + " h=" + containerH
                    + " pressedOverlay尺寸: w=" + pressedBarWidth + " h=" + dpToPx(pressedBarHeight));
            if (containerW != ViewGroup.LayoutParams.MATCH_PARENT && pressedBarWidth > containerW) {
                Log.w(TAG, "[PRESSED_DEBUG] 覆盖层宽度(" + pressedBarWidth + ") > 容器宽度(" + containerW + ")，可能被裁剪！");
            }
            if (dpToPx(pressedBarHeight) > containerH) {
                Log.w(TAG, "[PRESSED_DEBUG] 覆盖层高度(" + dpToPx(pressedBarHeight) + ") > 容器高度(" + containerH + ")，可能被裁剪！");
            }

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pressedOverlay.getLayoutParams();
            params.width = pressedBarWidth;
            params.height = dpToPx(pressedBarHeight);
            // pressedBarPositionY 决定覆盖层定位方向：
            // 正值（向上）→ 从容器底部定位，负 translationY 向上偏移 → 覆盖层移入容器顶部的额外空间
            // 负值（向下）→ 从容器顶部定位，正 translationY 向下偏移 → 覆盖层移入容器底部的额外空间
//            if (pressedBarPositionY >= 0) {
//                 向上偏移：从底部定位，用 translationY 向上推
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
//            } else {
//                // 向下偏移：从顶部定位，用 translationY 向下推
//                params.gravity = Gravity.CENTER_HORIZONTAL; // 默认 TOP | CENTER_HORIZONTAL
//            }
            // 移除 bottomMargin = -getNavigationBarHeight()：该绝对定位补偿加在同容器同重力的
            // 相对布局中只会使覆盖层中心下移导航栏/2（历史上默认值17即为 nav/2 的手工补偿，
            // 换导航栏高度不同的设备会产生偏差）。移除后覆盖层与提示条天然重合，0=重合点与设备无关
            params.bottomMargin = 0;
            pressedOverlay.setLayoutParams(params);
            // pressedBarPositionY：正值向上偏移覆盖层（正值越大覆盖层越靠上）
            pressedOverlay.setTranslationY(-dpToPx(pressedBarPositionY));

            // 更新覆盖层背景为按下样式（颜色、透明度、圆角）；彩虹模式使用流光渐变
            if ("rainbow".equals(pressedBarColorMode)) {
                pressedOverlay.setBackground(new RainbowDrawable(dpToPx(pressedBarCorner)));
            } else {
                GradientDrawable bg = createRoundedBackground(pressedBarCorner, pressedBarAlpha / 100.0f, pressedBarColor);
                pressedOverlay.setBackground(bg);
            }

            // 启用边缘羽化
            if (pressedOverlay instanceof FeatheredFrameLayout) {
                int pressedColor = parseColorWithAlpha(pressedBarColor, pressedBarAlpha / 100.0f);
                ((FeatheredFrameLayout) pressedOverlay).setFeatherEnabled(true, pressedColor, pressedBarCorner, pressedBarFeatherWidth);
            }

            // === 光晕层同步（光源效果：与核心层同位、同偏移、同步淡入） ===
            if (glowOverlay != null) {
                int glowPx = dpToPx(pressedBarGlowRadius);
                FrameLayout.LayoutParams glowParams = (FrameLayout.LayoutParams) glowOverlay.getLayoutParams();
                glowParams.width = pressedBarWidth + glowPx * 2;
                glowParams.height = dpToPx(pressedBarHeight) + glowPx * 2;
                glowParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
                glowOverlay.setLayoutParams(glowParams);
                updateGlowBackground(glowOverlay);
                applyGlowEffect(glowOverlay);
                glowOverlay.setTranslationY(pressedOverlay.getTranslationY());
                glowOverlay.animate().cancel();
                glowOverlay.setAlpha(pressedBarAlpha / 100.0f * 0.6f);
                glowOverlay.animate().alpha(pressedBarAlpha / 100.0f).setDuration(80).start();
            }
            startRainbowFlowIfNeeded();

            // == 第二步：纯透明度动画显示覆盖层 ==
            pressedOverlay.setAlpha(pressedBarAlpha / 100.0f * 0.6f);
            pressedOverlay.animate().alpha(pressedBarAlpha / 100.0f).setDuration(80).start();

            Log.d(TAG, "按下样式已应用（纯透明度变换，无位移和缩放）");
        } catch (Exception e) {
            Log.e(TAG, "应用按下样式失败", e);
        }
    }

    /**
     * 恢复原始样式（手指抬起时）
     *
     * @param restoreWindow 是否在动画完成后恢复窗口位置和大小
     */
    private void restoreNormalBarStyles(boolean restoreWindow) {
        if (pressedOverlay == null) return;
        if (isAiWindowExpanded) return;

        // 取消正在进行的动画
        pressedOverlay.animate().cancel();

        // 光晕层与流光动画同步收尾（与核心层相同的淡出时长）
        if (glowOverlay != null) {
            glowOverlay.animate().cancel();
            glowOverlay.animate().alpha(0f).setDuration(restoreWindow ? 150 : 100).start();
        }
        if (rainbowAnimator != null) {
            rainbowAnimator.cancel();
        }

        if (restoreWindow) {
            // 透明度渐出隐藏按下样式，完成后恢复正常的 bottomBar 样式
            pressedOverlay.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "restoreNormalBarStyles endAction fired, isAiExpanded=" + isAiWindowExpanded
                            + " isShortcutExpanded=" + isShortcutWindowExpanded);
                    if (!isAiWindowExpanded && !isShortcutWindowExpanded) {
                        applyStyleChanges();
                    }
                }
            }).start();
        } else {
            // 仅淡出按下样式（用于长按触发展开前）
            pressedOverlay.animate().alpha(0f).setDuration(100).start();
        }

        // 淡出完成后拆卸光晕窗口：静止态不挂光晕窗口（防系统弹窗死区回归）
        pressedPreviewHandler.removeCallbacks(glowWindowDetachRunnable);
        pressedPreviewHandler.postDelayed(glowWindowDetachRunnable, restoreWindow ? 200 : 150);

        Log.d(TAG, "已恢复原始样式（透明度渐变）");
    }

    /**
     * 恢复原始样式（手指抬起时）
     * 默认恢复窗口位置和大小
     */
    private void restoreNormalBarStyles() {
        restoreNormalBarStyles(true);
    }

    /**
     * 更新提示条位置
     *
     * @param posX X 偏移
     * @param posY Y 偏移（距底部，dp）
     */
    public void updateBarPosition(int posX, int posY) {
        barPositionX = posX;
        barPositionY = posY;

        Log.d(TAG, "更新位置 - X: " + posX + ", Y: " + posY + "dp");

        // 关键修复：立即在主线程执行
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyPositionChanges();
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    applyPositionChanges();
                }
            });
        }
    }

    /**
     * 应用位置更改到视图
     */
    private void applyPositionChanges() {
        if (barLayoutParams != null && windowManager != null) {
            try {
                barLayoutParams.x = barPositionX;
                barLayoutParams.y = dpToPx(barPositionY); // 正值越大提示条越靠上

                windowManager.updateViewLayout(barContainer, barLayoutParams);
                syncGlowWindow();
                Log.d(TAG, "位置已实时更新 - X: " + barPositionX + ", Y: " + barPositionY + "dp");
            } catch (Exception e) {
                Log.e(TAG, "更新位置失败", e);
            }
        }
    }

    /**
     * 更新长按触发时间
     *
     * @param duration 长按时间（毫秒）
     */
    public void updateLongPressDuration(int duration) {
        longPressDuration = duration;
        Log.d(TAG, "长按触发时间已更新: " + duration + "ms");
    }

    /**
     * 更新窗口边框和阴影设置
     */
    public void updateWindowBorder(int bWidth, int cRadius, String bColor, float bAlpha,
                                   int sWidth, String sColor, float sAlpha) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "更新边框、圆角和阴影设置:");
        Log.d(TAG, "  边框 - 宽度: " + bWidth + "dp, 颜色: " + bColor + ", 透明度: " + bAlpha);
        Log.d(TAG, "  圆角 - 半径: " + cRadius + "dp");
        Log.d(TAG, "  阴影 - 宽度: " + sWidth + "dp, 颜色: " + sColor + ", 透明度: " + sAlpha);

        borderWidth = bWidth;
        cornerRadius = cRadius; // 新增：更新圆角半径
        borderColor = bColor != null ? bColor : "#4CAF50";
        borderAlpha = bAlpha;
        shadowWidth = sWidth;
        shadowColor = sColor != null ? sColor : "#000000";
        shadowAlpha = sAlpha;

        Log.d(TAG, "更新后的成员变量:");
        Log.d(TAG, "  cornerRadius = " + cornerRadius);
        Log.d(TAG, "  borderColor = " + borderColor);
        Log.d(TAG, "  shadowColor = " + shadowColor);
        Log.d(TAG, "========================================");

        // 如果窗口已经展开，需要重新创建视图以应用新样式
        if (isAiWindowExpanded && aiWebView != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    recreateAiWindowView();
                }
            });
        }
    }

    /**
     * 更新关闭按钮显示设置
     */
    public void updateShowCloseButton(boolean show) {
        showCloseButton = show;
        Log.d(TAG, "关闭按钮显示设置已更新: " + showCloseButton);

        // 如果窗口已经展开，需要重新创建视图以应用新设置
        if (isAiWindowExpanded && aiWebView != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    recreateAiWindowView();
                }
            });
        }
    }

    /**
     * 更新刷新按钮显示设置
     */
    public void updateShowRefreshButton(boolean show) {
        showRefreshButton = show;
        Log.d(TAG, "刷新按钮显示设置已更新: " + showRefreshButton);

        // 如果窗口已经展开，需要重新创建视图以应用新设置
        if (isAiWindowExpanded && aiWebView != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    recreateAiWindowView();
                }
            });
        }
    }

    /**
     * 更新后退按钮显示设置
     */
    public void updateShowBackButton(boolean show) {
        showBackButton = show;
        Log.d(TAG, "后退按钮显示设置已更新: " + showBackButton);

        // 如果窗口已经展开，需要重新创建视图以应用新设置
        if (isAiWindowExpanded && aiWebView != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    recreateAiWindowView();
                }
            });
        }
    }

    /**
     * 重新创建 AI 窗口视图以应用新的边框和阴影样式
     */
    private void recreateAiWindowView() {
        if (barContainer == null || windowManager == null) {
            return;
        }

        try {
            // 保留 WebView
            WebView oldWebView = aiWebView;

            // 移除旧视图
            barContainer.removeAllViews();

            // 创建新视图
            View aiWindowView = createAiWindowView();
            barContainer.addView(aiWindowView);

            // 恢复 WebView 引用
            aiWebView = oldWebView;

            Log.d(TAG, "AI 窗口视图已重新创建，应用新的边框和阴影样式");
        } catch (Exception e) {
            Log.e(TAG, "重新创建 AI 窗口视图失败", e);
        }
    }

    /**
     * 长按回调：根据配置执行行为
     */
    private void onBarLongPressed() {
        Log.d(TAG, "提示条被长按");

        // 执行长按对应的行为
        performGestureAction("long_press");
    }

    /**
     * 双击提示条回调：根据配置执行行为
     */
    private void onBarDoubleClicked() {
        Log.d(TAG, "提示条被双击");

        if (isAiWindowExpanded || isShortcutWindowExpanded) {
            return;
        }
        performGestureAction("double_click");
    }

    /**
     * 是否有本应用窗口已打开（OverlayWindowActivity 前台可见 或 直接展开模式）
     */
    private boolean isAnyWindowOpen() {
        return sOverlayWindowOpen || isAiWindowExpanded || isShortcutWindowExpanded;
    }

    /**
     * 根据手势类型执行配置的行为
     */
    private void performGestureAction(String gesture) {
        // 防御：窗口打开期间不执行任何手势行为（正常路径已在触摸入口静默拦截）
        if (isAnyWindowOpen()) {
            Log.d(TAG, gesture + " 行为：窗口已打开，已屏蔽");
            return;
        }
        String action = getGestureAction(gesture);
        String pkg = getGestureAppPackage(gesture);
        String cls = getGestureAppClass(gesture);
        String name = getGestureAppName(gesture);

        // AI 窗口功能隐藏期间：防御性拦截 ai 行为（旧存储值/外部下发均不拉起 AI 窗口）
        if (!AppConfig.AI_WINDOW_ENABLED && ACTION_AI.equals(action)) {
            Log.d(TAG, gesture + " 行为：AI窗口功能已隐藏，忽略");
            return;
        }

        // 触发振动：行为非「无」即振（与具体动作类型解耦，后续新增动作无需适配）
        if (!ACTION_NONE.equals(action) && !TextUtils.isEmpty(action) && getGestureVibrate(gesture)) {
            triggerShortVibrate();
        }

        if (ACTION_NONE.equals(action)) {
            Log.d(TAG, gesture + " 行为：无");
            return;
        } else if (ACTION_APP.equals(action)) {
            if (!TextUtils.isEmpty(pkg) && !TextUtils.isEmpty(cls)) {
                boolean openInWindow = getGestureOpenInWindow(gesture);
                launchGestureApp(gesture, pkg, name, cls, openInWindow);
                Log.d(TAG, gesture + " 行为：打开应用 " + name + (openInWindow ? " (小窗)" : ""));
            } else {
                Log.d(TAG, gesture + " 行为：未配置应用");
            }
        } else if (ACTION_SHORTCUT.equals(action)) {
            Log.d(TAG, gesture + " 行为：打开快捷方式窗口（Activity模式）");
            if (!isShortcutWindowExpanded && !isAiWindowExpanded) {
                if (useExpandDirectly) {
                    expandToShortcutWindow();
                } else {
                    launchShortcutWindow();
                }
            }
        } else if (ACTION_AI.equals(action)) {
            Log.d(TAG, gesture + " 行为：打开AI窗口（Activity模式）");
            if (!isShortcutWindowExpanded && !isAiWindowExpanded) {
                if (useExpandDirectly) {
                    expandToAiWindow();
                } else {
                    launchAiWindow();
                }
            }
        }
    }

    /**
     * 获取指定手势的配置行为
     */
    private String getGestureAction(String gesture) {
        if ("click".equals(gesture)) return clickAction;
        else if ("double_click".equals(gesture)) return doubleClickAction;
        else if ("long_press".equals(gesture)) return longPressAction;
        return ACTION_NONE;
    }

    /**
     * 获取指定手势的应用包名
     */
    private String getGestureAppPackage(String gesture) {
        if ("click".equals(gesture)) return clickAppPackage;
        else if ("double_click".equals(gesture)) return doubleClickAppPackage;
        else if ("long_press".equals(gesture)) return longPressAppPackage;
        return "";
    }

    /**
     * 获取指定手势的应用名称
     */
    private String getGestureAppName(String gesture) {
        if ("click".equals(gesture)) return clickAppName;
        else if ("double_click".equals(gesture)) return doubleClickAppName;
        else if ("long_press".equals(gesture)) return longPressAppName;
        return "";
    }

    /**
     * 获取指定手势的应用类名
     */
    private String getGestureAppClass(String gesture) {
        if ("click".equals(gesture)) return clickAppClass;
        else if ("double_click".equals(gesture)) return doubleClickAppClass;
        else if ("long_press".equals(gesture)) return longPressAppClass;
        return "";
    }

    /**
     * 获取指定手势的窗口打开设置
     */
    private boolean getGestureOpenInWindow(String gesture) {
        if ("click".equals(gesture)) return clickOpenInWindow;
        else if ("double_click".equals(gesture)) return doubleClickOpenInWindow;
        else if ("long_press".equals(gesture)) return longPressOpenInWindow;
        return false;
    }

    /**
     * 获取指定手势的「触发时振动」开关
     */
    private boolean getGestureVibrate(String gesture) {
        if ("click".equals(gesture)) return clickVibrate;
        else if ("double_click".equals(gesture)) return doubleClickVibrate;
        else if ("long_press".equals(gesture)) return longPressVibrate;
        return false;
    }

    /**
     * 触发瞬间短促振动一下
     */
    private void triggerShortVibrate() {
        try {
            android.os.VibratorManager vm = (android.os.VibratorManager)
                    getSystemService(VIBRATOR_MANAGER_SERVICE);
            if (vm != null) {
                vm.getDefaultVibrator().vibrate(
                        android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception e) {
            Log.w(TAG, "vibrate failed: " + e);
        }
    }

    /**
     * 按手势的启动配置打开应用
     * 支持 URL Scheme / Intent Extra / 显式组件（可指定 Activity），失败时回退到应用启动页
     */
    private void launchGestureApp(String gesture, String appPackage, String appName, String appClass, boolean openInWindow) {
        android.content.SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        ShortcutLaunchConfig config = ShortcutLaunchConfig.loadGesture(prefs, gesture, appPackage, appClass, openInWindow);
        Intent configIntent = config.buildLaunchIntent();

        if (configIntent != null) {
            try {
                startWithWindowing(configIntent, openInWindow);
                Log.d(TAG, "已按启动配置打开 " + appName + " [" + ShortcutLaunchConfig.getModeDisplayName(config.launchMode) + "]");
                return;
            } catch (Exception e) {
                // 目标 Activity 未导出 / URI 无处理应用等情况
                Log.w(TAG, "启动配置打开失败，回退到应用启动页: " + appName, e);
            }
        }

        // 回退链：应用启动页
        try {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(appPackage);
            if (fallback == null) {
                fallback = new Intent(Intent.ACTION_MAIN);
                fallback.addCategory(Intent.CATEGORY_LAUNCHER);
                fallback.setPackage(appPackage);
            }
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startWithWindowing(fallback, openInWindow);
            Log.d(TAG, "已回退打开应用启动页: " + appName);
        } catch (Exception e) {
            Log.e(TAG, "打开应用失败: " + appName, e);
            Toast.makeText(this, "打开 " + appName + " 失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 窗口模式启动应用：小窗=windowingMode 100；「尝试消除授权弹窗」开=自定义
     * windowingMode（默认 6，隐藏 API 可能被 ROM 拒绝，失败回退普通 startActivity）；
     * 否则普通 startActivity（默认路径）
     */
    private void startWithWindowing(Intent intent, boolean openInWindow) {
        if (openInWindow) {
            Bundle bundle = new Bundle();
            bundle.putInt("android.activity.windowingMode", 100);
            startActivity(intent, bundle);
            return;
        }
        if (tryFullscreenWindowing) {
            Bundle bundle = new Bundle();
            bundle.putInt("android.activity.windowingMode", customWindowingMode);
            try {
                startActivity(intent, bundle);
                return;
            } catch (Exception e) {
                Log.w(TAG, "windowingMode=" + customWindowingMode + " 打开失败，回退默认方式: " + e);
                Toast.makeText(this, "该windowingMode参数无法使用，已使用默认方式打开",
                        Toast.LENGTH_SHORT).show();
            }
        }
        startActivity(intent);
    }

    /**
     * 打开指定应用
     */
    private void launchApp(String appPackage, String appName, String appClass, boolean openInWindow) {
        if (TextUtils.isEmpty(appPackage) || TextUtils.isEmpty(appClass)) {
            Log.d(TAG, "未选择要打开的应用");
            return;
        }

        try {
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setClassName(appPackage, appClass);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startWithWindowing(launchIntent, openInWindow);
            Log.d(TAG, "已打开应用: " + appName + (openInWindow ? " (小窗)" : ""));
        } catch (Exception e) {
            Log.e(TAG, "打开应用失败: " + appName, e);
        }
    }

    /**
     * 单击提示条：打开已选择的第三方应用（兼容旧版，保留引用）
     */
    private void launchClickApp() {
        launchApp(clickAppPackage, clickAppName, clickAppClass, clickOpenInWindow);
    }

    /**
     * 创建背景遮罩窗口（仅在窗口展开时使用）
     */
    private void createOverlayWindow() {
        if (overlayView != null) return;
        try {
            overlayView = new View(this);
            GradientDrawable overlayBg = new GradientDrawable();
            overlayBg.setShape(GradientDrawable.RECTANGLE);
            int overlayColorInt = Color.parseColor(overlayColor);
            overlayBg.setColor(overlayColorInt);
            overlayBg.setAlpha(255);
            overlayView.setBackground(overlayBg);
            overlayView.setAlpha(0f);

            DisplayMetrics realMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(realMetrics);

            overlayLayoutParams = new WindowManager.LayoutParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                overlayLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                overlayLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            overlayLayoutParams.format = PixelFormat.RGBA_8888;
            overlayLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            overlayLayoutParams.gravity = Gravity.TOP | Gravity.START;
            overlayLayoutParams.width = realMetrics.widthPixels;
            overlayLayoutParams.height = realMetrics.heightPixels;
            overlayLayoutParams.x = 0;
            overlayLayoutParams.y = 0;

            Log.d(TAG, "背景遮罩窗口已创建（等待添加到窗口管理器）");
        } catch (Exception e) {
            Log.e(TAG, "创建背景遮罩窗口失败", e);
            overlayView = null;
        }
    }

    /**
     * 移除背景遮罩窗口
     */
    private void removeOverlayWindow() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                Log.d(TAG, "背景遮罩窗口已移除");
            } catch (Exception e) {
                Log.e(TAG, "移除背景遮罩窗口失败", e);
            }
            overlayView = null;
            overlayLayoutParams = null;
            overlayBackground = null;
        }
    }

    /**
     * 展开/收起中途失败后的兜底恢复：
     * 把 bar 窗口恢复为触发区尺寸、移除全屏遮罩、清除触摸拦截并重建提示条。
     * 若不恢复，残留的全屏可触摸窗口/常驻遮罩会导致整屏无法点击（历史"系统弹窗卡死"同型问题）。
     */
    private void restoreBarWindowAfterFailure(String from) {
        Log.e(TAG, "restoreBarWindowAfterFailure: " + from);
        try {
            isAiWindowExpanded = false;
            isShortcutWindowExpanded = false;
            isOverlayAnimating = false;

            if (barContainer != null && windowManager != null && barLayoutParams != null) {
                barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                barLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
                barLayoutParams.width = computeBarWindowWidth();
                barLayoutParams.height = computeBarWindowHeight();
                barLayoutParams.x = barPositionX;
                barLayoutParams.y = dpToPx(barPositionY);

                boolean attached = barContainer.getWindowToken() != null;
                if (attached) {
                    windowManager.updateViewLayout(barContainer, barLayoutParams);
                } else {
                    windowManager.addView(barContainer, barLayoutParams);
                }

                barContainer.removeAllViews();
                barContainer.setBackground(null);
                barContainer.setClipToOutline(false);
                barContainer.setOnTouchListener(null);
                barContainer.setAlpha(1f);
                bottomBar = createBottomBarView();
                barContainer.addView(bottomBar);
            }

            // 光晕窗口不参与失败恢复（静止态不挂载，按下时由 applyPressedBarStyles 挂载）
            syncGlowWindow();

            removeOverlayWindow();
            closeBlurWindow();
            finishWindowHostActivity();
            Log.e(TAG, "restoreBarWindowAfterFailure: 已恢复为触发区尺寸");
        } catch (Exception e) {
            Log.e(TAG, "restoreBarWindowAfterFailure 异常: " + from, e);
        }
    }

    /**
     * 展开看门狗：展开发起 5 秒后若仍未进入展开状态但 bar 窗口停留在全屏，
     * 说明展开流程中途夭折（异常/回调未执行），强制恢复，保证整屏不可能永久冻结。
     */
    private void scheduleExpandWatchdog(final String tag) {
        try {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (isAiWindowExpanded || isShortcutWindowExpanded) return;
                        if (barLayoutParams == null || barContainer == null || windowManager == null) return;
                        if (barContainer.getWindowToken() == null) return;
                        DisplayMetrics realMetrics = new DisplayMetrics();
                        windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
                        boolean fullscreen = barLayoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT
                                || barLayoutParams.width >= realMetrics.widthPixels;
                        if (fullscreen) {
                            restoreBarWindowAfterFailure("watchdog(" + tag + "): 展开5秒后仍未完成");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "expand watchdog error", e);
                    }
                }
            }, 5000L);
        } catch (Exception e) {
            Log.e(TAG, "scheduleExpandWatchdog 失败", e);
        }
    }

    /**
     * 展开为 AI 对话窗口
     */
    private void expandToAiWindow() {
        if (isAiWindowExpanded) {
            return;
        }

        if (overlayView == null) {
            createOverlayWindow();
        }

        // 确保 overlayView 在 barContainer 下方
        if (overlayView != null && barContainer != null) {
            try {
                // 光晕窗口不参与换序（仅按下期间挂载）
                if (barContainer.getWindowToken() != null) {
                    windowManager.removeView(barContainer);
                }
                // 如果 overlayView 尚未添加，则添加
                if (overlayView.getWindowToken() == null) {
                    windowManager.addView(overlayView, overlayLayoutParams);
                }
                // 重新添加 barContainer（使用当前的 barLayoutParams）
                windowManager.addView(barContainer, barLayoutParams);
                // 光晕窗口不参与换序（仅按下期间挂载）
                Log.d(TAG, "窗口层级已调整：barContainer 置于 overlayView 之上");
            } catch (Exception e) {
                Log.e(TAG, "调整窗口层级失败", e);
            }
        }

        // Save start position for animation
        final int startWidth = barLayoutParams.width;
        final int startHeight = barLayoutParams.height;
        // 直接从 barPositionX/Y 成员变量获取，避免 barLayoutParams 被中间操作修改
        // CENTER_VERTICAL|CENTER_HORIZONTAL 下：x=barPositionX(居中偏移), y=dpToPx(barPositionY)(居中偏移)
        final int startX = barPositionX;
        final int startY = dpToPx(barPositionY);

        if (barContainer != null && windowManager != null) {
            try {
                // 关键修复：使用 getRealMetrics 获取包含导航栏的全屏尺寸
                // 确保展开/收起动画期间窗口 Surface 缓冲区覆盖导航栏区域，不被裁剪
                DisplayMetrics realMetrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
                final int screenWidth = realMetrics.widthPixels;
                final int screenHeight = realMetrics.heightPixels;

                // ===== 保持 CENTER_VERTICAL|CENTER_HORIZONTAL 重力，无需切换 =====
                // gravity stays CENTER_VERTICAL | CENTER_HORIZONTAL, 无需切换
                barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                barLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
                // 计算足够容纳 contentContainer 的窗口尺寸
                // contentContainer 使用 Gravity.CENTER + margins，其中心位置为：
                //   centerX = parentWidth/2 + leftMargin - rightMargin
                //          = parentWidth/2 + 2*windowPositionX
                //   centerY = parentHeight/2 + topMargin - bottomMargin
                //          = parentHeight/2 + 2*windowPositionY
                // 因此 Surface 至少需要: 宽 >= 4*|windowPositionX| + aiWindowWidth, 高 >= 4*|windowPositionY| + aiWindowHeight
                // 以防止 SurfaceFlinger 裁剪超出 Surface 缓冲区的内容
                int requiredWidth = Math.max(screenWidth, 4 * Math.abs(windowPositionX) + aiWindowWidth);
                int requiredHeight = Math.max(screenHeight, 4 * Math.abs(windowPositionY) + aiWindowHeight);

                // barContainer 立即设为足够大的尺寸，不再逐帧调整窗口 LayoutParams（改用 View 变换动画）
                barLayoutParams.x = 0;
                barLayoutParams.y = 0;
                barLayoutParams.width = requiredWidth;
                barLayoutParams.height = requiredHeight;
                windowManager.updateViewLayout(barContainer, barLayoutParams);
                Log.d(TAG, "[EXPAND_DEBUG] barContainer 全屏: w=" + requiredWidth + " h=" + requiredHeight);
                scheduleExpandWatchdog("ai");

                // ===== 关键修复：嵌套 OnGlobalLayoutListener 确保两层布局都完成 =====
                // 第一层：等待 barContainer 完成全屏布局
                // 第二层：等待 contentContainer 完成布局后再设置 translation 和启动动画
                // 避免：1) barContainer 仍处于旧尺寸时就添加视图
                //      2) contentContainer 的自然位置未确定时就计算视觉位置
                barContainer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // 校验：必须等 barContainer 达到全屏尺寸，防止被 collapse 残留的 layout 事件提前触发
                        if (barContainer.getWidth() < screenWidth || barContainer.getHeight() < screenHeight) {
                            Log.d(TAG, "[EXPAND_DEBUG] barContainer 尚未达到全屏尺寸(w=" + barContainer.getWidth() + " h=" + barContainer.getHeight() + ")，等待...");
                            return;
                        }
                        barContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        Log.d(TAG, "[EXPAND_DEBUG] barContainer 全屏布局完成，添加视图");

                        try {
                            // ===== 替换视图内容 =====
                            barContainer.removeAllViews();
                            View aiWindowView = createAiWindowView();
                            barContainer.addView(aiWindowView);

                            // 获取 contentContainer 并设为目标尺寸
                            View contentContainer = ((FrameLayout) ((FrameLayout) aiWindowView).getChildAt(0));
                            FrameLayout.LayoutParams contentLp = (FrameLayout.LayoutParams) contentContainer.getLayoutParams();
                            final int contentTargetWidth = contentLp.width;
                            final int contentTargetHeight = contentLp.height;
                            contentContainer.setAlpha(0f);

                            int effectiveStartWidth = (startWidth == ViewGroup.LayoutParams.MATCH_PARENT || startWidth <= 0) ? screenWidth : startWidth;
                            final int contentStartWidth = effectiveStartWidth;
                            // content 立即设为目标尺寸，通过初始 scale 呈现提示条大小的视觉效果
                            contentLp.width = contentTargetWidth;
                            contentLp.height = contentTargetHeight;
                            contentContainer.setLayoutParams(contentLp);

                            // ===== 同时设置 scale, pivot 和 translation，避免先 setScale 后 setTranslation 的帧间隙 =====
                            // 注意：setTranslationX/Y 在布局前设置是安全的，Android 会在布局完成后自动叠加到最终位置
                            final float initScaleX = (float) contentStartWidth / contentTargetWidth;
                            final float initScaleY = (float) dpToPx(currentHeight) / contentTargetHeight;
                            final float initTranslationX = startX - 2 * contentLp.leftMargin;
                            final float initTranslationY = startY - 2 * contentLp.topMargin;
                            Log.d(TAG, "[EXPAND_DEBUG] initTrans: (" + initTranslationX + "," + initTranslationY + ")"
                                    + " start=(" + startX + "," + startY + ")"
                                    + " margin=(" + contentLp.leftMargin + "," + contentLp.topMargin + ")"
                                    + " contentSize=" + contentTargetWidth + "x" + contentTargetHeight
                                    + " initScale=" + initScaleX + "x" + initScaleY);

                            contentContainer.setAlpha(0f);
                            Log.d(TAG, "[EXPAND_TRACE] scale/translation 已设置 at " + System.currentTimeMillis()
                                    + " scale=" + initScaleX + "x" + initScaleY
                                    + " trans=(" + initTranslationX + "," + initTranslationY + ")");
                            animationSetTime = System.currentTimeMillis();

                            // ===== 设置初始圆角 =====
//                            ((GradientDrawable) contentContainer.getBackground()).setCornerRadius(dpToPx(currentCornerRadius));
//                            if (contentContainer.getForeground() instanceof GradientDrawable) {
//                                ((GradientDrawable) contentContainer.getForeground()).setCornerRadius(dpToPx(currentCornerRadius));
//                            }
//                            contentContainer.invalidateOutline();

                            // 记录基础 margins
                            baseLeftMargin = contentLp.leftMargin;
                            baseTopMargin = contentLp.topMargin;
                            baseRightMargin = contentLp.rightMargin;
                            baseBottomMargin = contentLp.bottomMargin;

                            isAiWindowExpanded = true;
                            
                            // ===== 新增：启动下层模糊窗口（如果样式为亚克力或液态玻璃）=====
//                            if (aiStyle != 0) {
//                                startBlurWindow(aiWindowWidth, aiWindowHeight, windowPositionX, windowPositionY, cornerRadius, aiBlurRadius);
//                            }
                            
//                            startWindowHostActivity();
                            isKeyboardVisible = false;
                            if (aiWebView != null) {
                                aiWebView.setAlpha(0f);
                                
                                // ===== 新增：应用网页背景透明和自定义 CSS =====
                                applyWebpageSettings();
                            }



                            // ===== View.post()：等待 contentContainer 布局完全完成后设置属性并启动动画 =====
                            // 使用 View.post() 而非 OnGlobalLayoutListener，兼容性更强，避免不同 Android 版本布局回调时序差异
                            final View cc = contentContainer;

                            cc.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, "[EXPAND_DEBUG] cc.post 执行，设置属性并启动动画"
                                            + " contentTop=" + cc.getTop() + " contentLeft=" + cc.getLeft()
                                            + " getX=" + cc.getX() + " getY=" + cc.getY()
                                            + " scale=" + cc.getScaleX() + "x" + cc.getScaleY()
                                            + " trans=(" + cc.getTranslationX() + "," + cc.getTranslationY() + ")");

                                    // layout 完成后设置全部属性（pivot/scale/translation/layer），确保首帧位置正确
                                    cc.setAlpha(0f);
                                    cc.setPivotX(contentTargetWidth / 2f);
                                    cc.setPivotY(contentTargetHeight / 2f);
                                    cc.setScaleX(initScaleX);
                                    cc.setScaleY(initScaleY);
                                    cc.setTranslationX(initTranslationX);
                                    cc.setTranslationY(initTranslationY);
                                    cc.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                    
                                    // 【关键修复】AI窗口需要根据实际位置更新模糊窗口
                                    // 等待布局完成后获取真实坐标并发送广播
//                                    if (aiStyle != 0) {
//                                        cc.post(() -> {
//                                            try {
//                                                // 计算 contentContainer 在屏幕上的绝对坐标
//                                                int blurX = cc.getLeft();
//                                                int blurY = cc.getTop() - getNavigationBarHeight()/2;
//                                                int blurWidth = cc.getWidth();
//                                                int blurHeight = cc.getHeight();
//
//                                                Intent updateIntent = new Intent("UPDATE_BLUR_WINDOW_SIZE");
//                                                updateIntent.setPackage(getPackageName());
//                                                updateIntent.putExtra("blur_x", blurX);
//                                                updateIntent.putExtra("blur_y", blurY);
//                                                updateIntent.putExtra("blur_width", blurWidth);
//                                                updateIntent.putExtra("blur_height", blurHeight);
//                                                updateIntent.putExtra("blur_corner_radius", dpToPx(cornerRadius));
//                                                sendBroadcast(updateIntent);
//                                                Log.d(TAG, "已发送AI窗口模糊更新: x=" + blurX + " y=" + blurY
//                                                        + " w=" + blurWidth + " h=" + blurHeight + " 圆角=" + cornerRadius + "dp");
//                                            } catch (Exception e) {
//                                                Log.e(TAG, "发送模糊窗口更新失败", e);
//                                            }
//                                        });
//                                    }



                                    // 背景遮罩先渐入完成，再执行窗口展开动画
                                    if (overlayView != null) {

                                        final View ov = overlayView;
                                        final float targetAlpha = overlayAlpha / 100.0f;
                                        final long startTime = System.currentTimeMillis();
                                        ov.setAlpha(0f);
                                        isOverlayAnimating = true;
                                        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                                            @Override
                                            public void doFrame(long frameTimeNanos) {
                                                // 不在这里设置 cc.setAlpha(1f)——遮罩期间 content 保持不可见，
                                                // 避免在初始小尺寸+translation 状态下暴露"白条"
                                                // 等遮罩完成后的 performSmoothExpandAnimation 再设为可见
                                                // 以"AI窗口已展开"为准：共享标志被失败路径置 false 会导致循环静默死亡
                                                if (!isAiWindowExpanded) return;
                                                long elapsed = System.currentTimeMillis() - startTime;
                                                float progress = Math.min((float) elapsed / overlayAnimDuration, 1.0f);
                                                float eased = easeOutCubic(progress);
                                                ov.setAlpha(targetAlpha * eased);
                                                if (progress < 1.0f) {
                                                    Choreographer.getInstance().postFrameCallback(this);
                                                } else {
                                                    isOverlayAnimating = false;
                                                    performSmoothExpandAnimation(cc);
                                                }
                                            }
                                        });
                                    } else {
                                        // 遮罩创建失败时也要继续展开，否则停在全屏不可见状态造成整屏冻结
                                        performSmoothExpandAnimation(cc);
                                    }
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "[EXPAND_DEBUG] 布局回调内展开失败", e);
                            isOverlayAnimating = false;
                            e.printStackTrace();
                            restoreBarWindowAfterFailure("expandToAiWindow 布局回调内失败");
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "expand failed", e);
                isOverlayAnimating = false;
                e.printStackTrace();
                restoreBarWindowAfterFailure("expandToAiWindow 外层失败");
            }
        }
    }

    /**
     * 启动下层模糊窗口 BlurWindowActivity
     * 尺寸/位置和 OverlayWindowActivity 的内容区域对齐
     */
    private void startBlurWindow(int targetWidth, int targetHeight, int targetPosX, int targetPosY, float cornerRadiusDp) {
        startBlurWindow(targetWidth, targetHeight, targetPosX, targetPosY, cornerRadiusDp, 25);
    }
    
    private void startBlurWindow(int targetWidth, int targetHeight, int targetPosX, int targetPosY, float cornerRadiusDp, int blurRadius) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d(TAG, "API < 31，跳过模糊窗口");
            return;
        }
        blurWindowFadeInSent = false; // 重置渐变显示标志，准备新一次的动画
        try {
            DisplayMetrics dm = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;

            // 计算内容区域在屏幕上的绝对坐标
            // OverlayWindowActivity 使用 Gravity.CENTER + margin 定位，实际偏移 = 2 * margin
            // x = centerX + leftMargin - rightMargin = centerX + targetPosX - (-targetPosX) = centerX + 2*targetPosX
            // y = centerY + topMargin - bottomMargin = centerY + targetPosY - (-targetPosY) = centerY + 2*targetPosY
            int centerX = (screenWidth - targetWidth) / 2;
            int centerY = (screenHeight - targetHeight) / 2;
            int blurX = centerX + 2 * targetPosX;
            int blurY = centerY + 2 * targetPosY;

            Intent blurIntent = new Intent(this, BlurWindowActivity.class);
            blurIntent.putExtra(BlurWindowActivity.EXTRA_BLUR_X, blurX);
            blurIntent.putExtra(BlurWindowActivity.EXTRA_BLUR_Y, blurY);
            blurIntent.putExtra(BlurWindowActivity.EXTRA_BLUR_WIDTH, targetWidth);
            blurIntent.putExtra(BlurWindowActivity.EXTRA_BLUR_HEIGHT, targetHeight);
            blurIntent.putExtra(BlurWindowActivity.EXTRA_BLUR_RADIUS, blurRadius);
            blurIntent.putExtra(BlurWindowActivity.EXTRA_BLUR_CORNER_RADIUS, dpToPx((int) cornerRadiusDp));
            // NO_ANIMATION：禁止模糊窗口的启动转场，避免转场期间覆写窗口 alpha
            // 在展开开始瞬间闪现全尺寸模糊
            blurIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            // 启动握手：通知 OverlayWindowActivity 等模糊窗口就绪后再开始展开动画
            BlurWindowActivity.sPendingLaunch = true;
            startActivity(blurIntent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            Log.d(TAG, "已启动模糊窗口: 位置(" + blurX + "," + blurY + ") 尺寸" + targetWidth + "x" + targetHeight 
                    + " 圆角" + dpToPx((int)cornerRadiusDp) + "px 模糊半径=" + blurRadius);
        } catch (Exception e) {
            BlurWindowActivity.sPendingLaunch = false; // 启动失败时清除握手标志，避免展开白等
            Log.e(TAG, "启动模糊窗口失败", e);
        }
    }

    private void launchAiWindow() {
        // 根据背景样式决定是否启动模糊窗口
        if (aiStyle != 0) {
            startBlurWindow(aiWindowWidth, aiWindowHeight, windowPositionX, windowPositionY, cornerRadius, aiBlurRadius);
        }

        Intent intent = new Intent(this, OverlayWindowActivity.class);
        intent.putExtra(OverlayWindowActivity.EXTRA_WINDOW_TYPE, OverlayWindowActivity.WINDOW_TYPE_AI);
        intent.putExtra(OverlayWindowActivity.EXTRA_START_X, barPositionX);
        intent.putExtra(OverlayWindowActivity.EXTRA_START_Y, dpToPx(barPositionY));
        intent.putExtra(OverlayWindowActivity.EXTRA_START_WIDTH, currentWidth);
        intent.putExtra(OverlayWindowActivity.EXTRA_START_HEIGHT, dpToPx(currentHeight));
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_WIDTH, aiWindowWidth);
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_HEIGHT, aiWindowHeight);
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_POS_X, windowPositionX);
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_POS_Y, windowPositionY);
        intent.putExtra(OverlayWindowActivity.EXTRA_ANIM_DURATION, animationDuration);
        intent.putExtra(OverlayWindowActivity.EXTRA_CORNER_RADIUS, cornerRadius);
        intent.putExtra(OverlayWindowActivity.EXTRA_BORDER_WIDTH, borderWidth);
        intent.putExtra(OverlayWindowActivity.EXTRA_BORDER_COLOR, borderColor);
        intent.putExtra(OverlayWindowActivity.EXTRA_BORDER_ALPHA, borderAlpha);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHOW_CLOSE_BUTTON, showCloseButton);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHOW_REFRESH_BUTTON, showRefreshButton);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHOW_BACK_BUTTON, showBackButton);
        intent.putExtra(OverlayWindowActivity.EXTRA_CLOSE_ON_OUTSIDE, closeOnOutsideClick);
        intent.putExtra(OverlayWindowActivity.EXTRA_BACK_PRIORITIZE_WEBVIEW, backPrioritizeWebview);
        intent.putExtra(OverlayWindowActivity.EXTRA_AI_URL, currentAiUrl);
        intent.putExtra(OverlayWindowActivity.EXTRA_AI_STYLE, aiStyle);
        intent.putExtra(OverlayWindowActivity.EXTRA_AI_BLUR_RADIUS, aiBlurRadius);
        intent.putExtra(OverlayWindowActivity.EXTRA_AI_OVERLAY_ALPHA, aiOverlayAlpha);
        intent.putExtra(OverlayWindowActivity.EXTRA_TOP_BAR_TRANSPARENCY, topBarTransparency);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        // 隐藏底部提示条（透明度设为0）
        if (bottomBar != null) bottomBar.setAlpha(0f);
    }

    private void launchShortcutWindow() {
        // 根据背景样式决定是否启动模糊窗口
        int sTargetWidth = dpToPx(shortcutWindowWidth);
        int sTargetHeight = dpToPx(shortcutWindowHeight);  // 最大高度
        
        // 样式=0（不透明）时不启动模糊窗口
        if (shortcutStyle != 0) {
            startBlurWindow(sTargetWidth, sTargetHeight, 0, shortcutWindowPositionY, shortcutCornerRadius, shortcutBlurRadius);
        }

        Intent intent = new Intent(this, OverlayWindowActivity.class);
        intent.putExtra(OverlayWindowActivity.EXTRA_WINDOW_TYPE, OverlayWindowActivity.WINDOW_TYPE_SHORTCUT);
        intent.putExtra(OverlayWindowActivity.EXTRA_START_X, barPositionX);
        intent.putExtra(OverlayWindowActivity.EXTRA_START_Y, dpToPx(barPositionY));
        intent.putExtra(OverlayWindowActivity.EXTRA_START_WIDTH, currentWidth);
        intent.putExtra(OverlayWindowActivity.EXTRA_START_HEIGHT, dpToPx(currentHeight));
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_WIDTH, sTargetWidth);
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_HEIGHT, sTargetHeight);
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_POS_X, 0);
        intent.putExtra(OverlayWindowActivity.EXTRA_TARGET_POS_Y, shortcutWindowPositionY);
        intent.putExtra(OverlayWindowActivity.EXTRA_ANIM_DURATION, animationDuration);
        intent.putExtra(OverlayWindowActivity.EXTRA_CORNER_RADIUS, shortcutCornerRadius);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHOW_CLOSE_BUTTON, showCloseButton);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHOW_REFRESH_BUTTON, showRefreshButton);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHOW_BACK_BUTTON, showBackButton);
        intent.putExtra(OverlayWindowActivity.EXTRA_CLOSE_ON_OUTSIDE, closeOnOutsideClick);
        intent.putExtra(OverlayWindowActivity.EXTRA_BACK_PRIORITIZE_WEBVIEW, backPrioritizeWebview);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHORTCUT_ICON_SIZE, shortcutIconSize);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHORTCUT_ICON_CORNER, shortcutIconCorner);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHORTCUT_STYLE, shortcutStyle);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHORTCUT_BLUR_RADIUS, shortcutBlurRadius);
        intent.putExtra(OverlayWindowActivity.EXTRA_SHORTCUT_OVERLAY_ALPHA, shortcutOverlayAlpha);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        if (bottomBar != null) bottomBar.setAlpha(0f);
    }



    /**
     * Animate contentContainer from start size to target size
     */
    private void performContentExpandAnimation(final View content, final int startWidth, final int startHeight,
                                               final int targetWidth, final int targetHeight) {
        final long startTime = System.currentTimeMillis();
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / animationDuration, 1.0f);
                float easedProgress = easeOutCubic(progress);

                int currentWidth = (int) (startWidth + (targetWidth - startWidth) * easedProgress);
                int currentHeight = (int) (startHeight + (targetHeight - startHeight) * easedProgress);

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
                lp.width = Math.max(currentWidth, 1);
                lp.height = Math.max(currentHeight, 1);
                content.setLayoutParams(lp);

                if (progress < 1.0f) {
                    handler.postDelayed(this, FRAME_INTERVAL);
                }
            }
        });
    }

    /**
     * 创建 AI 对话窗口
     */
    private void createAiWindow() {
        aiWindowContainer = new FrameLayout(this);

        // 设置 AI 窗口布局参数
        aiWindowParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            aiWindowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            aiWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        aiWindowParams.format = PixelFormat.RGBA_8888;
        aiWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        aiWindowParams.gravity = Gravity.CENTER;
        aiWindowParams.width = aiWindowWidth;
        aiWindowParams.height = aiWindowHeight;

        // 创建 AI 窗口视图
        View aiWindowView = createAiWindowView();
        aiWindowContainer.addView(aiWindowView);

        // 添加到窗口（初始尺寸为0，用于动画）
        try {
            aiWindowParams.width = 0;
            aiWindowParams.height = 0;
            windowManager.addView(aiWindowContainer, aiWindowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建 AI 窗口视图（包含 WebView 和关闭按钮）
     */
    private View createAiWindowView() {
        Log.d(TAG, "创建 AI 窗口 - 边框: " + borderWidth + "dp, 颜色: " + borderColor + ", 透明度: " + borderAlpha);
        Log.d(TAG, "创建 AI 窗口 - 圆角: " + cornerRadius + "dp");
        Log.d(TAG, "创建 AI 窗口 - 阴影: " + shadowWidth + "dp, 颜色: " + shadowColor + ", 透明度: " + shadowAlpha);

        // rootLayout: fullscreen transparent overlay for outside touch detection
        windowRootLayout = new FrameLayout(this);
        windowRootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        windowRootLayout.setBackgroundColor(Color.TRANSPARENT);
        // 关键修复：允许子视图（contentContainer）超出 windowRootLayout 的边界
        // 当窗口位置调低时（windowPositionY 较大），contentContainer 底部可能超出 windowRootLayout
        // 默认 clipChildren=true 会裁剪超出部分，导致导航栏区域被裁切
        windowRootLayout.setClipChildren(false);

        // 拦截返回键（键盘显示时 overlay 有焦点，需要自行处理返回键）
        windowRootLayout.setFocusable(true);
        windowRootLayout.setFocusableInTouchMode(true);
        windowRootLayout.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    Log.d(TAG, "windowRootLayout BACK key intercepted, isKeyboardVisible=" + isKeyboardVisible);
                    if (isKeyboardVisible) {
                        // 键盘可见 → 隐藏键盘并恢复窗口位置
                        stopKeyboardHideDetector();
                        cleanupProxyIme();
                        try {
                            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (imm != null && barContainer != null) {
                                imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to hide keyboard on back key", e);
                        }
                        isKeyboardVisible = false;
                        animateForKeyboard(false, 0);
                        restoreNotFocusableFlag();
                        // 安全兜底：确保窗口位置已恢复
                        resetKeyboardOffset();
                    } else {
                        // 键盘不可见 → 关闭窗口
                        collapseToBottomBar();
                    }
                    return true; // consume
                }
                return false;
            }
        });

        // 点击外部区域处理见下面的 OnTouchListener（在 addView 完成后设置）
        // 注意：不要在此处设置 setOnClickListener，OnTouchListener 在 DOWN 阶段消费事件更可靠

        // System back (gesture + nav bar) handled by WindowHostActivity proxy

        // contentContainer: specific size, centered in fullscreen rootLayout with position offset
        FrameLayout contentContainer = new FrameLayout(this);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                aiWindowWidth,
                aiWindowHeight
        );
        containerParams.gravity = Gravity.CENTER;
        // Apply window position offset as margins (offset from center)
        containerParams.leftMargin = windowPositionX;
        containerParams.topMargin = windowPositionY;
        containerParams.rightMargin = -windowPositionX;
        containerParams.bottomMargin = -windowPositionY;
        contentContainer.setLayoutParams(containerParams);

        // Block click propagation to rootLayout (outside click handler)
        contentContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // consume click, don't propagate to rootLayout
            }
        });

        // 使用连续曲率圆角背景
        ContinuousRoundRectDrawable background = new ContinuousRoundRectDrawable(
                getWindowBgColor(),
                dpToPx(cornerRadius)
        );
        // 显式设置圆角（某些情况下构造参数可能未触发 buildPath）
        background.setCornerRadius(dpToPx(cornerRadius));
        contentContainer.setBackground(background);
// 注意：连续曲率 Path 无法通过 setClipToOutline 裁剪，所以去掉这行
// contentContainer.setClipToOutline(true); // 删除或注释掉


        if (borderWidth > 0) {
            try {
                int borderColorWithAlpha = parseColorWithAlpha(borderColor, borderAlpha);
                // 将边框参数直接设置到背景 Drawable 中
                background.setStroke(dpToPx(borderWidth), borderColorWithAlpha);
                Log.d(TAG, "Continuous border set: " + borderColor + " -> 0x" + Integer.toHexString(borderColorWithAlpha));
            } catch (Exception e) {
                Log.e(TAG, "set border failed", e);
            }
        }

        // 关键修复：为边框留出空间
        // setStroke 绘制的边框在形状外侧，setClipToOutline 裁剪的是填充区域
        // 需要添加等于边框宽度的 padding，否则 WebView 会覆盖边框
        // No padding needed - foreground border draws on top of children

        // 判断是否复用已有的 WebView（保留上次关闭时的网页状态）
        boolean isNewWebView = false;
        if (aiWebView == null) {
            aiWebView = createWebView();
            isNewWebView = true;
        } else {
            // 从旧的父视图中移除（如果还在父视图中）
            if (aiWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) aiWebView.getParent()).removeView(aiWebView);
            }
            Log.d(TAG, "Reusing existing WebView, preserving page state");
        }
        FrameLayout.LayoutParams webViewParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        aiWebView.setLayoutParams(webViewParams);

        GradientDrawable webViewBackground = new GradientDrawable();
        webViewBackground.setShape(GradientDrawable.RECTANGLE);
        webViewBackground.setCornerRadius(dpToPx(cornerRadius));
        webViewBackground.setColor(Color.TRANSPARENT);
        aiWebView.setBackground(webViewBackground);

        // ✅ 启用 WebView 的硬件加速和图层类型，确保圆角渲染正确
        aiWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // 为 WebView 设置圆角裁剪，使其与 contentContainer 的圆角一致
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 延迟到布局完成后设置 Outline，确保宽高已确定
            aiWebView.post(new Runnable() {
                @Override
                public void run() {
                    int w = aiWebView.getWidth();
                    int h = aiWebView.getHeight();
                    if (w > 0 && h > 0) {
                        final Path path = buildContinuousRoundRectPath(w, h, dpToPx(cornerRadius));
                        aiWebView.setOutlineProvider(new ViewOutlineProvider() {
                            @Override
                            public void getOutline(View view, Outline outline) {
                                outline.setConvexPath(path);
                            }
                        });
                        aiWebView.setClipToOutline(true);
                        aiWebView.invalidate();
                    }
                }
            });
        }
        contentContainer.addView(aiWebView);

        // 复用 WebView 时恢复渲染状态（收起时 onPause 暂停了内部渲染管线）
        if (!isNewWebView && aiWebView != null) {
            // 先置为可见，确保硬件渲染表面正确初始化
            // 后续在 expandToAiWindow 中会再设为 0 用于动画，此时 contentContainer
            // 处于极小 scale 状态，用户看不到这个短暂的 alpha=1
            aiWebView.setAlpha(1f);
            aiWebView.onResume();
            aiWebView.invalidate();
            Log.d(TAG, "Reused WebView: onResume + invalidate called");
        }

        // 为新创建和复用的 WebView 均应用深色模式设置
        applyWebViewDarkMode(aiWebView);

        // 仅新创建的 WebView 需要加载 URL；已有 WebView 保留上次的网页状态
        if (isNewWebView) {
            Log.d(TAG, "New WebView, loading URL: " + currentAiUrl);
            aiWebView.loadUrl(currentAiUrl);
            loadedAiUrl = currentAiUrl;
        } else {
            // 用 loadedAiUrl 判断 URL 是否被用户主动修改（而非页面内部导航）
            // 避免 getUrl() 返回重定向/子页面 URL 导致误刷新
            if (loadedAiUrl == null || !loadedAiUrl.equals(currentAiUrl)) {
                Log.d(TAG, "URL changed from " + loadedAiUrl + " to " + currentAiUrl + ", reloading");
                aiWebView.loadUrl(currentAiUrl);
                loadedAiUrl = currentAiUrl;
            }
        }

        // 创建隐藏 IME 代理 EditText（接收输入法输入，通过 JS 转发给 WebView）
        proxyIme = new EditText(this);
        proxyIme.setVisibility(View.INVISIBLE);
        proxyIme.setAlpha(0f);
        FrameLayout.LayoutParams proxyParams = new FrameLayout.LayoutParams(1, dpToPx(48));
        proxyParams.gravity = Gravity.BOTTOM;
        proxyIme.setLayoutParams(proxyParams);
        proxyIme.setFocusable(true);
        proxyIme.setFocusableInTouchMode(true);
        proxyIme.setBackground(null);
        contentContainer.addView(proxyIme);

        // ===== 创建顶部小横条按钮组（居中显示的小灰条，点击展开为圆角矩形） =====
        addTopBar(contentContainer);

        Log.d(TAG, "✅ contentContainer 已设置 clipToOutline，WebView 有相同圆角背景");

        // 组装层级：rootLayout -> contentContainer
        windowRootLayout.addView(contentContainer);

        // 外部触摸事件处理：使用 OnTouchListener 在 DOWN 阶段就主动消费外部触摸事件
        // 与 OnClickListener 不同，OnTouchListener 在 dispatchTouchEvent 内部执行，
        // 返回 true 可确保触摸序列被当前窗口完全捕获，不穿透到下层窗口
        // 解决 TYPE_APPLICATION_OVERLAY 下首次触摸被系统旁路的问题
        windowRootLayout.setOnTouchListener(new View.OnTouchListener() {
            private boolean mOutsideDown = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        // 判断触摸点是否在 contentContainer 之外
                        // contentContainer 是 rootLayout 的第一个子视图（index 0）
                        View content = windowRootLayout.getChildAt(0);
                        if (content != null) {
                            Rect rect = new Rect();
                            content.getHitRect(rect);
                            mOutsideDown = !rect.contains((int) event.getX(), (int) event.getY());
                        } else {
                            mOutsideDown = true;
                        }
                        // 外部触摸立即消费 DOWN，避免事件穿透到窗口管理器后被旁路
                        return mOutsideDown;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (mOutsideDown) {
                            mOutsideDown = false;
                            Log.d(TAG, "Outside touch UP, isKeyboardVisible=" + isKeyboardVisible);
                            if (isKeyboardVisible) {
                                stopKeyboardHideDetector();
                                cleanupProxyIme();
                                try {
                                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                    if (imm != null && barContainer != null) {
                                        imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to hide keyboard on outside touch", e);
                                }
                                isKeyboardVisible = false;
                                animateForKeyboard(false, 0);
                                restoreNotFocusableFlag();
                                resetKeyboardOffset();
                            } else {
                                if (closeOnOutsideClick) {
                                    collapseToBottomBar();
                                } else {
                                    Log.d(TAG, "Outside touch ignored: closeOnOutsideClick=false");
                                }
                            }
                            return true;
                        }
                        return false;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        mOutsideDown = false;
                        return true;
                    default:
                        // MOVE 等中间事件延续 DOWN 阶段的消费状态
                        return mOutsideDown;
                }
            }
        });

        return windowRootLayout;
    }

    /**
     * 创建渐变阴影 Drawable
     *
     * @return 渐变阴影背景
     */
    private GradientDrawable createShadowDrawable() {
        // 关键修复：使用径向渐变实现从内到外的阴影效果
        int[] colors;

        try {
            int shadowColorWithAlpha = parseColorWithAlpha(shadowColor, shadowAlpha);
            int transparentColor = Color.argb(0, 0, 0, 0);

            // 定义渐变色：从阴影颜色到透明
            colors = new int[]{
                    shadowColorWithAlpha,  // 中心（靠近边框）
                    transparentColor       // 边缘（完全透明）
            };

            Log.d(TAG, "✅ 阴影渐变颜色 - 起始: 0x" + Integer.toHexString(shadowColorWithAlpha) +
                    ", 结束: 0x" + Integer.toHexString(transparentColor));
        } catch (Exception e) {
            Log.e(TAG, "❌ 阴影颜色解析失败", e);
            // 默认黑色半透明阴影
            colors = new int[]{Color.argb(128, 0, 0, 0), Color.argb(0, 0, 0, 0)};
        }

        // 创建径向渐变 Drawable
        GradientDrawable shadowDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colors
        );
        shadowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);

        // 关键修复：设置渐变半径为阴影视图的一半（从中心到边缘）
        // 阴影视图比内容大 shadowWidth*2，所以半径应该是 shadowWidth
        shadowDrawable.setGradientRadius(dpToPx(shadowWidth));
        shadowDrawable.setGradientCenter(0.5f, 0.5f);

        // 设置圆角，与内容容器匹配
        shadowDrawable.setCornerRadius(dpToPx(cornerRadius + shadowWidth));

        Log.d(TAG, "✅ 阴影 Drawable 已创建 - 半径: " + shadowWidth + "dp, 圆角: " + (cornerRadius + shadowWidth) + "dp");

        return shadowDrawable;
    }

    /**
     * 解析颜色字符串并应用透明度
     *
     * @param colorHex 颜色字符串（如 #RRGGBB）
     * @param alpha    透明度 0.0-1.0
     * @return 带透明度的颜色值
     */
    private int parseColorWithAlpha(String colorHex, float alpha) {
        try {
            int color = Color.parseColor(colorHex);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            int alphaInt = (int) (alpha * 255);
            return Color.argb(alphaInt, red, green, blue);
        } catch (Exception e) {
            Log.e(TAG, "颜色解析失败: " + colorHex + ", 使用默认颜色");
            return Color.parseColor("#4CAF50");
        }
    }

    /**
     * 创建 WebView
     */
    private WebView createWebView() {
        WebView webView = new WebView(getApplicationContext()) {
            @Override
            protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                // 当代理 IME 激活时，阻止焦点丢失通知传到 Chromium
                // 否则 Chromium 会 blur 当前聚焦的输入框，导致光标消失
                // proxyIme.requestFocus() 会触发 WebView 的 onFocusChanged(false),
                // Chromium 收到后自动 blur 了 activeElement -> 光标消失
                if (!focused && proxyIme != null && proxyIme.getVisibility() == View.VISIBLE) {
                    Log.d(TAG, "WebView onFocusChanged(false) suppressed: proxy IME active, keeping DOM focus");
                    return; // 不调用 super，Chromium 不知道焦点变化，不会 blur 输入框
                }
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
            }

            @Override
            public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
                // 当代理 IME 激活时，阻止 WebView 抢夺焦点
                // WebView 的 setFocusableInTouchMode(true) 会导致触摸时自动 requestFocus，
                // 从而抢走 proxyIme 的焦点->IME 连接断开->键盘缩回。
                // onLongPress 中事后重聚焦不够可靠，因为有约 100ms 的时间窗口。
                if (proxyIme != null && proxyIme.getVisibility() == View.VISIBLE) {
                    Log.d(TAG, "WebView requestFocus blocked: proxy IME active");
                    return false;
                }
                return super.requestFocus(direction, previouslyFocusedRect);
            }

            @Override
            public ActionMode startActionMode(ActionMode.Callback callback) {
                // TYPE_APPLICATION_OVERLAY 窗口没有 Activity，默认 startActionMode 会失败
                // 尝试 TYPE_FLOATING（API 23+），失败则返回 null 由 JS 接管选中
                Log.d(TAG, "WebView.startActionMode(callback) called");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        ActionMode mode = startActionMode(callback, ActionMode.TYPE_FLOATING);
                        Log.d(TAG, "startActionMode TYPE_FLOATING result: " + mode);
                        if (mode != null) return mode;
                    } catch (Exception e) {
                        Log.e(TAG, "startActionMode TYPE_FLOATING failed", e);
                    }
                }
                return super.startActionMode(callback);
            }

            @Override
            public ActionMode startActionMode(ActionMode.Callback callback, int type) {
                Log.d(TAG, "WebView.startActionMode type=" + type + " called");
                try {
                    ActionMode mode = super.startActionMode(callback, type);
                    Log.d(TAG, "startActionMode type=" + type + " result: " + mode);
                    return mode;
                } catch (Exception e) {
                    Log.e(TAG, "startActionMode type=" + type + " failed", e);
                    return null;
                }
            }
        };
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        // 阻断 WebView 原生长按处理，完全由自定义 GestureDetector + JS 接管文本选择
        webView.setOnLongClickListener(v -> true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // WebView 深色模式由 applyWebViewDarkMode() 在展开窗口时动态设置
        // 不在此处硬编码，以确保每次展开都根据当前系统深色模式实时切换

        // Enable media and file access for microphone and file upload
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        // JavaScript 接口：检测输入框聚焦/失焦，通过隐藏的代理 EditText 接收 IME 输入
        // 在 TYPE_APPLICATION_OVERLAY 中 WebView 无法建立 InputConnection，
        // 因此使用代理 EditText 接收输入法文字，再通过 JS 转发到 WebView
        webView.addJavascriptInterface(new Object() {

            @JavascriptInterface
            public void onInputFocus() {
                Log.d(TAG, "JS: input focused");
                // 如果有延迟重聚焦 pending，取消它（点击发送按钮后立即 re-focus 的场景）
                if (blurRefocusRunnable != null) {
                    new Handler(Looper.getMainLooper()).removeCallbacks(blurRefocusRunnable);
                    blurRefocusRunnable = null;
                    Log.d(TAG, "onInputFocus: cancelled pending blur refocus");
                }
                if (FloatWindowService.this.isImeProcessing) {
                    Log.d(TAG, "onInputFocus: already processing, ignoring");
                    return;
                }
                // 如果代理 IME 已激活且键盘可见，只需更新元素引用，跳过重新设置
                // 防止点击发送按钮等操作导致的键盘闪烁问题
                if (isKeyboardVisible && proxyIme != null && proxyIme.getVisibility() == View.VISIBLE) {
                    Log.d(TAG, "onInputFocus: IME already active, updating _imeTarget only");
                    aiWebView.evaluateJavascript(
                            "(function(){var el=document.activeElement;if(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable))window._imeTarget=el;})()", null);
                    return;
                }
                FloatWindowService.this.isImeProcessing = true;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAiWindowExpanded) {
                            FloatWindowService.this.isImeProcessing = false;
                            return;
                        }
                        Log.d(TAG, "onInputFocus: switching to proxy IME");
                        // 1. 在 WebView 中保存当前聚焦元素的引用
                        //    之后 proxyIme.requestFocus() 会使 WebView 失焦，activeElement 会变 null
                        aiWebView.evaluateJavascript(
                                "(function(){" +
                                        "  var el = document.activeElement;" +
                                        "  if(el && (el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable)){" +
                                        "    window._imeTarget = el;" +
                                        "    window._imeValue = el.value || el.textContent || '';" +
                                        "  }" +
                                        "})()", null);
                        // 2. 移除 FLAG_NOT_FOCUSABLE，让窗口可接收 IME 输入
                        removeNotFocusableFlag();
                        // 3. 延迟等待 flag 生效
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // 4. 从 WebView 获取输入框现有文本，用于初始化代理 EditText
                                //    使 delta 对比基于实际页面文本，而非从空字符串开始
                                if (aiWebView != null) {
                                    aiWebView.evaluateJavascript(
                                            "(function(){return window._imeValue||'';})()",
                                            new ValueCallback<String>() {
                                                @Override
                                                public void onReceiveValue(String value) {
                                                    String initialText = "";
                                                    if (value != null && !"null".equals(value) && value.length() >= 2) {
                                                        try {
                                                            initialText = new org.json.JSONTokener(value).nextValue().toString();
                                                        } catch (Exception e) {
                                                            Log.e(TAG, "Failed to parse _imeValue JSON", e);
                                                        }
                                                    }
                                                    setupProxyIme(initialText);
                                                }
                                            });
                                } else {
                                    setupProxyIme("");
                                }
                            }
                        }, 80);
                    }
                });
            }

            @JavascriptInterface
            public void onInputBlur() {
                // 输入框失焦后，延迟 300ms 检测是否真正失焦（非短暂 blur-re-focus）
                // 如果 300ms 内没有重新聚焦（例如点击发送按钮后页面重聚焦输入框），
                // 则清理代理 IME 并缩回键盘。
                // 点击发送按钮时，页面通常 blur → re-focus 输入框，
                // re-focus 会触发 onInputFocus 并取消本延迟任务，键盘保持。
                // 点击空白区域/按返回时，blur 后没有 re-focus，延迟到期后缩回键盘。
                Log.d(TAG, "JS: input blurred, scheduling delayed cleanup");
                if (blurRefocusRunnable != null) {
                    new Handler(Looper.getMainLooper()).removeCallbacks(blurRefocusRunnable);
                }
                blurRefocusRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "JS: blur timeout -> dismissing keyboard");
                        stopKeyboardHideDetector();
                        if (proxyIme != null) {
                            cleanupProxyIme();
                        }
                        try {
                            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (imm != null && barContainer != null) {
                                imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to hide keyboard on blur timeout", e);
                        }
                        isKeyboardVisible = false;
                        animateForKeyboard(false, 0);
                        restoreNotFocusableFlag();
                        resetKeyboardOffset();
                        blurRefocusRunnable = null;
                    }
                };
                new Handler(Looper.getMainLooper()).postDelayed(blurRefocusRunnable, 300);
            }

            @JavascriptInterface
            public void onSelectionChange(int start, int end) {
                Log.d(TAG, "[CURSOR] selection changed to (" + start + "," + end + ")");
                final int selStart = start;
                final int selEnd = end;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (proxyIme != null && proxyIme.getText() != null) {
                            int len = proxyIme.getText().length();
                            int s = Math.max(0, Math.min(selStart, len));
                            int e = Math.max(0, Math.min(selEnd, len));
                            proxyIme.setSelection(s, e);
                            Log.d(TAG, "[CURSOR] proxyIme setSelection(" + s + "," + e + ")");
                        }
                    }
                });
            }

            @JavascriptInterface
            public String getPendingPasteText() {
                String t = pendingPasteText;
                Log.d(TAG, "[PASTE_DEBUG] getPendingPasteText called, text length=" + (t != null ? t.length() : -1) + " firstChar=" + (t != null && t.length() > 0 ? (int) t.charAt(0) : -1));
                pendingPasteText = null;
                return t != null ? t : "";
            }

            @JavascriptInterface
            public void syncProxyIme(String newValue) {
                if (newValue == null) return;
                final String value = newValue;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        isUpdatingProxy = true;
                        proxyIme.setText(value);
                        if (value.length() > 0) {
                            proxyIme.setSelection(value.length());
                        }
                        isUpdatingProxy = false;
                        Log.d(TAG, "[SYNC] proxyIme synced, value=" + value.substring(0, Math.min(value.length(), 50)) + " new length=" + value.length());
                    }
                });
            }
        }, "AndroidBridge");

        // 注入 JavaScript 监听输入框聚焦/失焦事件
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading: " + url);
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "Page started: " + url);
                // 尽早注入深色模式 CSS filter，避免页面加载过程中白闪
                injectDarkModeCSS(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded: " + url);
                
                // 页面加载完成后按顺序注入所有 CSS（确保 DOM 已准备好）
                // 1. 根据"网页背景透明"设置决定是否注入透明背景 CSS
                if (webpageBgTransparent && aiStyle != 0) {
                    injectTransparentBackground(view);
                } else {
                    removeTransparentBackground(view);
                }
                
                // 2. 注入深色模式 CSS（文字反色，图片不反色）- 必须最后注入确保不被覆盖
                injectDarkModeCSS(view);
                
                // 3. 注入自定义 CSS
                if (!TextUtils.isEmpty(customCss)) {
                    injectCustomCss(view);
                }
                
                // 注入 JavaScript 检测输入框聚焦
                injectInputFocusScript(view);
                
                // 等待 CSS 生效后设置 WebView 背景
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
                
                // 页面加载完成后更新返回按钮状态
                updateBackButtonState(backButtonRef);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                // 浏览历史更新时确保返回按钮状态正确（canGoBack 此时已可用）
                updateBackButtonState(backButtonRef);
            }
        });

        // Set WebChromeClient for file upload and microphone permission
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                Log.d(TAG, "WebView permission requested: " + java.util.Arrays.toString(request.getResources()));
                // Grant permissions directly (must be synchronous - PermissionRequest expires after callback)
                try {
                    request.grant(request.getResources());
                    Log.d(TAG, "WebView permissions granted");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to grant WebView permissions", e);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             final ValueCallback<Uri[]> filePathCallback,
                                             final FileChooserParams fileChooserParams) {
                Log.d(TAG, "File chooser requested");

                // Cancel any pending callback from previous attempt
                if (FileChooserActivity.uploadCallback != null) {
                    try {
                        FileChooserActivity.uploadCallback.onReceiveValue(null);
                    } catch (Exception ignored) {
                    }
                }

                // Hide floating window so file chooser dialog is not blocked
                hideFloatingWindow();

                // Wrap callback to restore floating window after file selection
                FileChooserActivity.uploadCallback = new ValueCallback<Uri[]>() {
                    @Override
                    public void onReceiveValue(Uri[] value) {
                        Log.d(TAG, "Wrapped callback invoked, files=" + (value != null ? value.length : 0));
                        // 将 content:// URI 复制到临时文件，转为 file:// URI
                        // 避免 FileChooserActivity 结束后 content URI 权限失效导致上传失败
                        Uri[] fileUris = copyContentUrisToTempFiles(value);
                        try {
                            filePathCallback.onReceiveValue(fileUris);
                            Log.d(TAG, "WebView callback invoked successfully");
                        } catch (Exception e) {
                            Log.e(TAG, "Error invoking WebView callback", e);
                        }
                        // Restore floating window
                        showFloatingWindow();
                    }
                };

                try {
                    Intent proxyIntent = new Intent(FloatWindowService.this, FileChooserActivity.class);
                    proxyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    // Pass accept types and mode to FileChooserActivity
                    String[] acceptTypes = fileChooserParams.getAcceptTypes();
                    proxyIntent.putExtra("accept_types", acceptTypes);
                    proxyIntent.putExtra("allow_multiple", fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

                    Log.d(TAG, "Accept types: " + java.util.Arrays.toString(acceptTypes));

                    startActivity(proxyIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch file chooser", e);
                    FileChooserActivity.uploadCallback = null;
                    showFloatingWindow();
                    return false;
                }
                return true;
            }
        });

        // 设置下载监听器，支持网页内的下载按钮触发文件下载
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Log.d(TAG, "========== DOWNLOAD REQUESTED ==========");
                Log.d(TAG, "  url: " + url.substring(0, Math.min(url.length(), 120)));
                Log.d(TAG, "  userAgent: " + userAgent);
                Log.d(TAG, "  contentDisposition: " + contentDisposition);
                Log.d(TAG, "  mimetype: " + mimetype);
                Log.d(TAG, "  contentLength: " + contentLength);
                try {
                    // data: URI 需要单独处理（DownloadManager 不支持）
                    if (url != null && url.startsWith("data:")) {
                        handleDataUriDownload(url, mimetype);
                        Log.d(TAG, "========== DOWNLOAD END (data URI) ==========");
                        return;
                    }

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                    // 转发 User-Agent（部分服务器校验）
                    if (userAgent != null && !userAgent.isEmpty()) {
                        request.addRequestHeader("User-Agent", userAgent);
                        Log.d(TAG, "  User-Agent forwarded");
                    }

                    // 转发 WebView 的 Cookies（登录态），否则服务器可能拒绝
                    try {
                        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                        String cookies = cookieManager.getCookie(url);
                        if (cookies != null && !cookies.isEmpty()) {
                            request.addRequestHeader("Cookie", cookies);
                            Log.d(TAG, "  Cookies attached (" + cookies.length() + " chars)");
                        } else {
                            Log.d(TAG, "  No cookies for this URL");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "  Failed to attach cookies", e);
                    }

                    if (mimetype != null && !mimetype.isEmpty()) {
                        request.setMimeType(mimetype);
                    }
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    Log.d(TAG, "  guessedFileName: " + fileName);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.setAllowedOverMetered(true);
                    request.setAllowedOverRoaming(true);

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    Log.d(TAG, "  DownloadManager service: " + (dm != null ? "OK" : "NULL"));

                    if (dm != null) {
                        long downloadId = dm.enqueue(request);
                        Log.d(TAG, "  download enqueued, ID: " + downloadId);
                        showToastTop("下载已开始: " + fileName, Toast.LENGTH_SHORT);
                    } else {
                        Log.e(TAG, "  DownloadManager service is NULL!");
                    }
                    Log.d(TAG, "========== DOWNLOAD END ==========");
                } catch (Exception e) {
                    Log.e(TAG, "========== DOWNLOAD FAILED ==========", e);
                    showToastTop("下载失败: " + e.getMessage(), Toast.LENGTH_LONG);
                }
            }
        });

        // 在 WebView 上添加触摸监听器，当代理 IME 激活时阻止 WebView 抢走焦点
        // 点击发送按钮等操作时，WebView 内部可能 requestFocus 导致 proxyIme 失去焦点、IME 断开
        // 使用 GestureDetector 区分短按（点击）和长按：
        // - 短按：在 onSingleTapUp 中重新聚焦 proxyIme，保持 IME 连接
        // - 长按：不聚焦 proxyIme，让 WebView 原生处理上下文菜单（复制/粘贴）
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                isLongPressing = false; // 每次新的触摸事件重置长按标记
                return true; // 必须返回 true，GestureDetector 才能继续处理后续事件
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // 短按（点击）：重新聚焦代理 EditText 以保持 IME 连接
                // 注意：必须排除长按后的 ACTION_UP，否则会覆盖 WebView 的文本选择
                if (!isLongPressing && proxyIme != null && proxyIme.getVisibility() == View.VISIBLE) {
                    proxyIme.requestFocus();
                }
                // 非长按点击，清除选中状态（模拟正常浏览器点击取消选中的行为）
                if (!isLongPressing && aiWebView != null) {
                    aiWebView.evaluateJavascript(
                            "(function(){try{window.getSelection().removeAllRanges();}catch(e){}})()", null);
                }
                isLongPressing = false;
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // 长按：设置标记，阻止 onSingleTapUp 聚焦 proxyIme
                // 通过 JavaScript 检测选中文本，显示自定义上下文菜单（复制/全选/粘贴）
                // 注意：必须在此重新聚焦 proxyIme，因为 WebView 的 setFocusableInTouchMode(true) 会
                // 在触摸时抢走 proxyIme 的焦点，导致 IME 连接断开、键盘缩回。
                // 短按在 onSingleTapUp 中重聚焦，但长按时 GestureDetector 不触发 onSingleTapUp。
                if (proxyIme != null && proxyIme.getVisibility() == View.VISIBLE) {
                    proxyIme.requestFocus();
                }
                isLongPressing = true;
                final float touchX = e.getX();
                final float touchY = e.getY();
                Log.d(TAG, "Long press on WebView at (" + touchX + "," + touchY + ")");

                if (aiWebView != null) {
                    aiWebView.evaluateJavascript(
                            "(function(x, y){" +
                                    "  var R = {h:false, t:'', i:false, img:''};" +
                                    "  var dpr = window.devicePixelRatio || 1, cx = x / dpr, cy = y / dpr;" +
                                    "  var sel = window.getSelection();" +
                                    "  try{" +
                                    "    var hit = document.elementFromPoint(cx, cy);" +
                                    "    if(hit && (hit.tagName==='INPUT'||hit.tagName==='TEXTAREA')){" +
                                    "      R.i = true;" +
                                    "      var s = hit.selectionStart, e = hit.selectionEnd;" +
                                    "      if(s !== e){ R.h = true; R.t = hit.value.substring(s, e); }" +
                                    "      else{" +
                                    "        var off = 0;" +
                                    "        var rect = hit.getBoundingClientRect();" +
                                    "        var cs = window.getComputedStyle(hit);" +
                                    "        var leftPad = (parseFloat(cs.paddingLeft)||0) + (parseFloat(cs.borderLeftWidth)||0);" +
                                    "        var textX = cx - rect.left - leftPad + (hit.scrollLeft||0);" +
                                    "        if(textX > 0){" +
                                    "          var cv = document.createElement('canvas');" +
                                    "          var ctx = cv.getContext('2d');" +
                                    "          ctx.font = cs.font || (cs.fontSize + ' ' + cs.fontFamily);" +
                                    "          var v = hit.value, lo = 0, hi = v.length;" +
                                    "          while(lo < hi){" +
                                    "            var mid = (lo + hi) >>> 1;" +
                                    "            if(ctx.measureText(v.substring(0,mid)).width >= textX) hi = mid;" +
                                    "            else lo = mid + 1;" +
                                    "          }" +
                                    "          off = lo;" +
                                    "        }" +
                                    "        hit.selectionStart = hit.selectionEnd = off;" +
                                    "        window._dragAnchor = off;" +
                                    "      }" +
                                    "      return JSON.stringify(R);" +
                                    "    }" +
                                    "  }catch(e){}" +
                                    "  try{if(sel) sel.removeAllRanges();}catch(e){}" +
                                    "  try{" +
                                    "    var elem = document.elementFromPoint(cx, cy);" +
                                    "    if(elem && elem.nodeType === 1){" +
                                    "      !function wn(n, cb){" +
                                    "        if(n.nodeType === 3){ if(n.textContent.trim()) cb(n); return; }" +
                                    "        if(n.nodeType === 1 && n.childNodes){" +
                                    "          for(var i=0;i<n.childNodes.length;i++) wn(n.childNodes[i], cb);" +
                                    "        }" +
                                    "      }(elem, function(tn){" +
                                    "        if(R.h) return;" +
                                    "        var rc = document.createRange(); rc.selectNodeContents(tn);" +
                                    "        var rect = rc.getBoundingClientRect();" +
                                    "        if(cy >= rect.top-3 && cy <= rect.bottom+3 && cx >= rect.left-3 && cx <= rect.right+3){" +
                                    "          var txt = tn.textContent;" +
                                    "          for(var j=0; j<txt.length; j++){" +
                                    "            rc.setStart(tn, j); rc.setEnd(tn, j+1);" +
                                    "            var cr = rc.getBoundingClientRect();" +
                                    "            if(cr.left <= cx && cx <= cr.right){" +
                                    "              var st = j, en = j;" +
                                    "              var _mL = (txt.charCodeAt(j)<128?50:0);" +
                                    "              while(st > 0 && txt.charCodeAt(st-1) > 32 && j-st<_mL) st--;" +
                                    "              while(en < txt.length && txt.charCodeAt(en) > 32 && en-j<_mL) en++;" +
                                    "              if(_mL===0 && en===j && j+1<=txt.length) en=j+1;" +
                                    "              if(st < en){" +
                                    "                var wr = document.createRange();" +
                                    "                wr.setStart(tn, st); wr.setEnd(tn, en);" +
                                    "                sel.addRange(wr);" +
                                    "                R.h = true; R.t = txt.substring(st, en);" +
                                    "              } return;" +
                                    "            }" +
                                    "          }" +
                                    "          var idx = Math.round((cx - rect.left) / rect.width * (txt.length-1));" +
                                    "          idx = Math.max(0, Math.min(idx, txt.length-1));" +
                                    "          var st = idx, en = idx;" +
                                    "          var _mL2 = (txt.charCodeAt(idx)<128?50:0);" +
                                    "          while(st > 0 && txt.charCodeAt(st-1) > 32 && idx-st<_mL2) st--;" +
                                    "          while(en < txt.length && txt.charCodeAt(en) > 32 && en-idx<_mL2) en++;" +
                                    "          if(_mL2===0 && en===idx && idx+1<=txt.length) en=idx+1;" +
                                    "          if(st < en){" +
                                    "            var wr = document.createRange();" +
                                    "            wr.setStart(tn, st); wr.setEnd(tn, en);" +
                                    "            sel.addRange(wr);" +
                                    "            R.h = true; R.t = txt.substring(st, en);" +
                                    "          }" +
                                    "        }" +
                                    "      });" +
                                    "    }" +
                                    "  }catch(e){}" +
                                    "  if(!R.h && document.caretRangeFromPoint){" +
                                    "    try{" +
                                    "      var range = document.caretRangeFromPoint(cx, cy);" +
                                    "      if(range){" +
                                    "        var node = range.startContainer, off = range.startOffset;" +
                                    "        if(node.nodeType === 3){" +
                                    "          var txt = node.textContent;" +
                                    "          var st = off, en = off;" +
                                    "          var _mL3 = (txt.charCodeAt(off)<128?50:0);" +
                                    "          while(st > 0 && txt.charCodeAt(st-1) > 32 && off-st<_mL3) st--;" +
                                    "          while(en < txt.length && txt.charCodeAt(en) > 32 && en-off<_mL3) en++;" +
                                    "          if(_mL3===0 && en===off && off+1<=txt.length) en=off+1;" +
                                    "          if(st < en){" +
                                    "            var wr = document.createRange();" +
                                    "            wr.setStart(node, st); wr.setEnd(node, en);" +
                                    "            sel.addRange(wr);" +
                                    "            R.h = true; R.t = txt.substring(st, en);" +
                                    "          }" +
                                    "        }" +
                                    "      }" +
                                    "    }catch(e){}" +
                                    "  }" +
                                    "  if(sel && sel.rangeCount > 0){" +
                                    "    var _r = sel.getRangeAt(0);" +
                                    "    window._selAnchorNode = _r.startContainer;" +
                                    "    window._selAnchorOffset = _r.startOffset;" +
                                    "  }" +
                                    "  if(!R.h && !R.i){" +
                                    "    try{" +
                                    "      var hit = document.elementFromPoint(cx, cy);" +
                                    "      if(hit && hit.tagName === 'IMG'){" +
                                    "        R.img = hit.currentSrc || hit.src || '';" +
                                    "      }" +
                                    "    }catch(e){}" +
                                    "  }" +
                                    "  return JSON.stringify(R);" +
                                    "})(" + touchX + "," + touchY + ")",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    Log.d(TAG, "JS result raw: " + value);
                                    boolean hasSel = false;
                                    String selText = "";
                                    boolean isInput = false;
                                    String imageUrl = "";
                                    if (value != null && !"null".equals(value) && value.length() > 2) {
                                        try {
                                            // evaluateJavascript 对返回值做了 JSON 编码。
                                            // JS 中 JSON.stringify(R) 返回字符串，导致双重编码。
                                            // 先用 JSONTokener 解码外层 JSON 字符串得到内层 JSON，再解析。
                                            String jsonStr = new org.json.JSONTokener(value).nextValue().toString();
                                            org.json.JSONObject json = new org.json.JSONObject(jsonStr);
                                            hasSel = json.optBoolean("h", false);
                                            selText = json.optString("t", "");
                                            isInput = json.optBoolean("i", false);
                                            imageUrl = json.optString("img", "");
                                        } catch (Exception ex) {
                                            Log.e(TAG, "Parse selection failed", ex);
                                        }
                                    }
                                    Log.d(TAG, "JS result parsed -> hasSel=" + hasSel + " selText='" + selText + "' isInput=" + isInput + " imageUrl=" + imageUrl);
                                    if (imageUrl.length() > 0) {
                                        Log.d(TAG, "Image detected at long-press: " + imageUrl);
                                        showContextMenu(touchX, touchY, "", false, false, imageUrl);
                                        isLongPressing = false;
                                    } else if (isInput && !hasSel) {
                                        Log.d(TAG, "Input field, no selection, waiting for drag or up");
                                    } else if (isInput && hasSel) {
                                        Log.d(TAG, "Input field, word auto-selected, waiting for drag end");
                                    } else if (!hasSel) {
                                        isLongPressing = false;
                                        Log.d(TAG, "No text at long-press position, cancelling");
                                    } else {
                                        Log.d(TAG, "Word selected '" + selText + "', waiting for drag end or touch up");
                                    }
                                }
                            });
                }
            }
        });

        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 收起展开的小横条
                if (event.getAction() == MotionEvent.ACTION_DOWN && isTopBarExpanded) {
                    collapseTopBar();
                }

                gestureDetector.onTouchEvent(event);

                if (isLongPressing) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_MOVE) {
                        final float mx = event.getX();
                        final float my = event.getY();
                        Log.d(TAG, "Drag extend to (" + mx + "," + my + ")");
                        if (aiWebView != null) {
                            aiWebView.evaluateJavascript(
                                    "(function(x,y){" +
                                            "  var dpr=window.devicePixelRatio||1,cx=x/dpr,cy=y/dpr;" +
                                            "  try{" +
                                            "    var hit=document.elementFromPoint(cx,cy);" +
                                            "    if(hit&&(hit.tagName==='INPUT'||hit.tagName==='TEXTAREA')){" +
                                            "      var off=0;" +
                                            "      var rect=hit.getBoundingClientRect();" +
                                            "      var cs=window.getComputedStyle(hit);" +
                                            "      var leftPad=(parseFloat(cs.paddingLeft)||0)+(parseFloat(cs.borderLeftWidth)||0);" +
                                            "      var textX=cx-rect.left-leftPad+(hit.scrollLeft||0);" +
                                            "      if(textX>0){" +
                                            "        var cv=document.createElement('canvas');" +
                                            "        var ctx=cv.getContext('2d');" +
                                            "        ctx.font=cs.font||(cs.fontSize+' '+cs.fontFamily);" +
                                            "        var v=hit.value,lo=0,hi=v.length;" +
                                            "        while(lo<hi){" +
                                            "          var mid=(lo+hi)>>>1;" +
                                            "          if(ctx.measureText(v.substring(0,mid)).width>=textX) hi=mid;" +
                                            "          else lo=mid+1;" +
                                            "        }" +
                                            "        off=lo;" +
                                            "      }" +
                                            "      if(window._dragAnchor===undefined) window._dragAnchor=hit.selectionStart;" +
                                            "      var anchor=window._dragAnchor;" +
                                            "      hit.selectionStart=Math.min(anchor,off);" +
                                            "      hit.selectionEnd=Math.max(anchor,off);" +
                                            "    }else{" +
                                            "      var sel=window.getSelection();" +
                                            "      if(sel&&sel.rangeCount>0){var r=document.caretRangeFromPoint(cx,cy);if(r)sel.extend(r.startContainer,r.startOffset);}" +
                                            "    }" +
                                            "  }catch(e){}" +
                                            "})(" + mx + "," + my + ")", null);
                        }
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        handleLongPressDragEnd(event.getX(), event.getY());
                    }
                }

                return false;
            }
        });

        return webView;
    }

    /**
     * 长按拖拽结束后（ACTION_UP/CANCEL），读取最终选中文本并显示上下文菜单
     */
    private void handleLongPressDragEnd(final float touchX, final float touchY) {
        if (aiWebView == null) {
            isLongPressing = false;
            return;
        }
        Log.d(TAG, "LP drag end, reading final selection at (" + touchX + "," + touchY + ")");
        aiWebView.evaluateJavascript(
                "(function(x,y){" +
                        "  var R = {h:false, t:'', i:false, img:''};" +
                        "  var dpr = window.devicePixelRatio || 1, cx = x / dpr, cy = y / dpr;" +
                        "  var sel = window.getSelection();" +
                        "  try{" +
                        "    var hit = document.elementFromPoint(cx, cy);" +
                        "    if(hit && (hit.tagName==='INPUT'||hit.tagName==='TEXTAREA')){" +
                        "      R.i = true;" +
                        "      var s = hit.selectionStart, e = hit.selectionEnd;" +
                        "      if(s !== e){ R.h = true; R.t = hit.value.substring(s, e); }" +
                        "      window._dragAnchor = undefined;" +
                        "      return JSON.stringify(R);" +
                        "    }" +
                        "  }catch(e){}" +
                        "  if(sel && sel.rangeCount > 0){" +
                        "    var txt = sel.toString().trim();" +
                        "    if(txt.length > 0){ R.h = true; R.t = txt; }" +
                        "  }" +
                        "  if(!R.h && !R.i){" +
                        "    try{" +
                        "      var hit = document.elementFromPoint(cx, cy);" +
                        "      if(hit && hit.tagName === 'IMG'){" +
                        "        R.img = hit.currentSrc || hit.src || '';" +
                        "      }" +
                        "    }catch(e){}" +
                        "  }" +
                        "  return JSON.stringify(R);" +
                        "})(" + touchX + "," + touchY + ")",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.d(TAG, "Final sel raw: " + value);
                        boolean hasSel = false;
                        String selText = "";
                        boolean isInput = false;
                        String imageUrl = "";
                        if (value != null && !"null".equals(value) && value.length() > 2) {
                            try {
                                String jsonStr = new org.json.JSONTokener(value).nextValue().toString();
                                org.json.JSONObject json = new org.json.JSONObject(jsonStr);
                                hasSel = json.optBoolean("h", false);
                                selText = json.optString("t", "");
                                isInput = json.optBoolean("i", false);
                                imageUrl = json.optString("img", "");
                            } catch (Exception ex) {
                                Log.e(TAG, "Parse final sel failed", ex);
                            }
                        }
                        Log.d(TAG, "Final sel -> hasSel=" + hasSel + " text='" + selText + "' isInput=" + isInput + " imageUrl=" + imageUrl);
                        showContextMenu(touchX, touchY, selText, hasSel, isInput, imageUrl);
                        isLongPressing = false;
                    }
                });
    }

    /**
     * Temporarily hide floating window so dialogs are not blocked
     */
    private void hideFloatingWindow() {
        if (barContainer != null && windowManager != null) {
            try {
                barContainer.setVisibility(View.GONE);
                windowManager.updateViewLayout(barContainer, barLayoutParams);
                Log.d(TAG, "Floating window hidden for file chooser");
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide floating window", e);
            }
        }
    }

    /**
     * Restore floating window visibility after dialog closes
     */
    private void showFloatingWindow() {
        if (barContainer != null && windowManager != null) {
            try {
                barContainer.setVisibility(View.VISIBLE);
                windowManager.updateViewLayout(barContainer, barLayoutParams);
                Log.d(TAG, "Floating window restored");
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore floating window", e);
            }
        }
    }

    /**
     * 将 content:// URI 复制到应用缓存目录的临时文件，返回 file:// URI 数组
     * 解决 FileChooserActivity 结束后 content URI 权限失效导致 WebView 无法读取文件的问题
     */
    private Uri[] copyContentUrisToTempFiles(Uri[] uris) {
        if (uris == null || uris.length == 0) return uris;

        Uri[] result = new Uri[uris.length];
        for (int i = 0; i < uris.length; i++) {
            Uri uri = uris[i];
            if (uri == null) {
                result[i] = null;
                continue;
            }
            // 只有 content:// 或 android.resource:// 等非 file 协议的需要复制
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                result[i] = uri;
                continue;
            }
            try {
                // 生成临时文件：根据原始文件名或 content type 生成后缀
                String fileName = "upload_" + System.currentTimeMillis() + "_" + i;
                String extension = getFileExtension(uri);
                if (extension != null) fileName += extension;

                File cacheDir = new File(getCacheDir(), "webview_uploads");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File tempFile = new File(cacheDir, fileName);

                // 复制内容
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    in = getContentResolver().openInputStream(uri);
                    if (in == null) {
                        Log.w(TAG, "Cannot open input stream for " + uri);
                        result[i] = uri;
                        continue;
                    }
                    out = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[65536];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    out.flush();
                    Log.d(TAG, "Copied " + uri + " -> " + tempFile.getAbsolutePath() + " (" + tempFile.length() + " bytes)");
                } finally {
                    if (in != null) try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                    if (out != null) try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                }

                result[i] = Uri.fromFile(tempFile);
            } catch (Exception e) {
                Log.e(TAG, "Failed to copy " + uri + " to temp file, using original", e);
                result[i] = uri; // fallback to original URI
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
     * 将网页图片下载并保存到本地相册
     *
     * @param imageUrl 图片的绝对 URL
     */
    private void saveImageToGallery(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        showToastTop("开始下载图片...", Toast.LENGTH_SHORT);

        new Thread(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                byte[] imageData;
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    imageData = out.toByteArray();
                }

                String mimeType = conn.getContentType();
                if (mimeType == null) mimeType = "image/jpeg";
                String extension = ".jpg";
                if (mimeType.contains("png")) extension = ".png";
                else if (mimeType.contains("gif")) extension = ".gif";
                else if (mimeType.contains("webp")) extension = ".webp";
                else if (mimeType.contains("bmp")) extension = ".bmp";
                else if (mimeType.contains("svg")) extension = ".svg";

                String fileName = "IMG_" + System.currentTimeMillis() + extension;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+：使用 MediaStore 保存到相册（无需存储权限）
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WebImages");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                            out.write(imageData);
                        }
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);

                        showToastTop("图片已保存到相册", Toast.LENGTH_SHORT);
                        Log.d(TAG, "Image saved to MediaStore: " + fileName);
                    }
                } else {
                    // Android 9 及以下：保存到外部存储，通知媒体扫描器
                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File appDir = new File(picturesDir, "WebImages");
                    if (!appDir.exists()) appDir.mkdirs();
                    File imageFile = new File(appDir, fileName);
                    try (FileOutputStream out = new FileOutputStream(imageFile)) {
                        out.write(imageData);
                    }
                    // 通知媒体扫描器
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(Uri.fromFile(imageFile));
                    sendBroadcast(intent);

                    showToastTop("图片已保存到相册", Toast.LENGTH_SHORT);
                    Log.d(TAG, "Image saved to: " + imageFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save image: " + imageUrl, e);
                showToastTop("保存图片失败: " + e.getMessage(), Toast.LENGTH_LONG);
            }
        }).start();
    }

    private void showToastTop(final String msg, final int duration) {
        new Handler(Looper.getMainLooper()).post(() -> {
            // 窗口未展开时回退到系统 Toast
            if (windowRootLayout == null || windowRootLayout.getParent() == null) {
                Toast toast = Toast.makeText(FloatWindowService.this, msg, duration);
                toast.setGravity(Gravity.TOP, 0, dpToPx(80));
                toast.show();
                return;
            }

            // 在悬浮窗内部创建自定义 Toast（避免系统 Toast 位置受限）
            TextView toastView = new TextView(FloatWindowService.this);
            toastView.setText(msg);
            toastView.setTextColor(Color.WHITE);
            toastView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            toastView.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
            toastView.setGravity(Gravity.CENTER);
            toastView.setEllipsize(TextUtils.TruncateAt.END);
            toastView.setMaxLines(2);

            // 半透明黑色圆角背景
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dpToPx(8));
            bg.setColor(Color.parseColor("#DD333333"));
            toastView.setBackground(bg);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.topMargin = dpToPx(60);
            toastView.setLayoutParams(params);

            // 淡入动画
            toastView.setAlpha(0f);
            windowRootLayout.addView(toastView);
            toastView.animate().alpha(1f).setDuration(200).start();

            // 定时自动移除（带淡出动画）
            int showMs = (duration == Toast.LENGTH_LONG) ? 3500 : 2000;
            toastView.postDelayed(() -> {
                if (toastView.getParent() != null) {
                    toastView.animate().alpha(0f).setDuration(300)
                            .withEndAction(() -> {
                                if (toastView.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) toastView.getParent()).removeView(toastView);
                                }
                            }).start();
                }
            }, showMs);
        });
    }

    /**
     * 处理 data: URI 的数据下载（DownloadManager 不支持此类 URI）
     * 解析 base64 数据后保存到 Downloads 目录
     */
    private void handleDataUriDownload(String dataUri, String mimetype) {
        Log.d(TAG, "handleDataUriDownload called");
        try {
            // data: URI 格式: data:[<mediatype>][;base64],<data>
            if (!dataUri.startsWith("data:")) return;

            // 去掉 data: 前缀
            String rest = dataUri.substring(5);

            // 查找逗号分隔符
            int commaIndex = rest.indexOf(',');
            if (commaIndex < 0) {
                Log.e(TAG, "Invalid data URI: no comma found");
                return;
            }

            String header = rest.substring(0, commaIndex);
            String data = rest.substring(commaIndex + 1);

            // 判断是否是 base64 编码
            boolean isBase64 = header.contains(";base64");

            // 从 header 中提取 MIME 类型（如果有）
            String mime = mimetype;
            if ((mime == null || mime.isEmpty()) && header.contains(";")) {
                mime = header.substring(0, header.indexOf(';'));
            } else if (mime == null || mime.isEmpty()) {
                mime = header;
            }
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";

            Log.d(TAG, "  data URI mime: " + mime + ", isBase64: " + isBase64 + ", data length: " + data.length());

            byte[] fileData;
            if (isBase64) {
                fileData = Base64.decode(data, Base64.DEFAULT);
            } else {
                // URL 解码（百分号编码）
                String decoded = android.net.Uri.decode(data);
                fileData = decoded.getBytes("UTF-8");
            }

            Log.d(TAG, "  decoded data size: " + fileData.length + " bytes");

            // 从 MIME 类型确定文件后缀
            String extension = ".bin";
            if (mime.contains("jpeg") || mime.contains("jpg")) extension = ".jpg";
            else if (mime.contains("png")) extension = ".png";
            else if (mime.contains("gif")) extension = ".gif";
            else if (mime.contains("webp")) extension = ".webp";
            else if (mime.contains("bmp")) extension = ".bmp";
            else if (mime.contains("svg")) extension = ".svg";
            else if (mime.contains("pdf")) extension = ".pdf";
            else if (mime.contains("zip")) extension = ".zip";
            else if (mime.contains("mp4")) extension = ".mp4";
            else if (mime.contains("mp3")) extension = ".mp3";

            String fileName = "download_" + System.currentTimeMillis() + extension;
            Log.d(TAG, "  saving to Downloads/" + fileName);

            // 保存到 Downloads 目录
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：使用 MediaStore Downloads
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        out.write(fileData);
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                }
            } else {
                // Android 9 及以下：保存到外部 Downloads 目录
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadDir, fileName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(fileData);
                }
                // 通知媒体扫描器
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                sendBroadcast(intent);
            }

            Log.d(TAG, "Data URI download saved: " + fileName + " (" + fileData.length + " bytes)");
            showToastTop("下载完成: " + fileName, Toast.LENGTH_SHORT);

        } catch (Exception e) {
            Log.e(TAG, "Failed to handle data URI download", e);
            showToastTop("下载失败: " + e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 顶部小横条（AI窗口顶部居中灰色小条，点击展开为按钮组）
    // ═══════════════════════════════════════════════════════════

    private FrameLayout topBarView;
    private LinearLayout topBarButtonRow;
    private int topBarExpandedW, topBarExpandedH;
    private int topBarCollapsedW, topBarCollapsedH;

    private void addTopBar(FrameLayout container) {
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

        // ── 主容器 ──
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

        if (hasBack) {
            ImageView backBtn = createTopBarButton(android.R.drawable.ic_menu_revert);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            if (hasRefresh || hasClose) lp.rightMargin = gap;
            backBtn.setLayoutParams(lp);
            backBtn.setOnClickListener(v -> {
                if (aiWebView != null && aiWebView.canGoBack()) {
                    aiWebView.goBack();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> updateBackButtonState(backBtn), 100);
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
                if (aiWebView != null) aiWebView.reload();
            });
            topBarButtonRow.addView(refreshBtn);
        }

        if (hasClose) {
            ImageView closeBtn = createTopBarButton(android.R.drawable.ic_menu_close_clear_cancel);
            closeBtn.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
            closeBtn.setOnClickListener(v -> collapseToBottomBar());
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
        if (aiWebView != null) {
            FrameLayout.LayoutParams wvLp = (FrameLayout.LayoutParams) aiWebView.getLayoutParams();
            wvLp.topMargin = dpToPx(8);
            aiWebView.setLayoutParams(wvLp);
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

        updateBackButtonState(backButtonRef);
    }

    private void collapseTopBar() {
        if (!isTopBarExpanded) return;
        isTopBarExpanded = false;

        float sx = (float) topBarCollapsedW / topBarExpandedW;
        float sy = (float) topBarCollapsedH / topBarExpandedH;

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

    private void updateBackButtonState(ImageView backButton) {
        if (backButton == null) return;
        boolean canGoBack = aiWebView != null && aiWebView.canGoBack();
        backButton.setEnabled(canGoBack);
        backButton.setAlpha(canGoBack ? 1.0f : 0.3f);
    }

    /**
     * 展开动画：通过 contentContainer 的 scaleX/scaleY/translationY 变换实现
     * barContainer 保持全屏，避免 updateViewLayout 逐帧重排导致的掉帧
     */
    private long animationSetTime = 0; // 记录 scale/translation 设置的时间点，用于日志

    /** 计算弹簧接管状态：与 expandInterpolator 在 TA 处位置/速度连续，并解析出第二次过零落点。 */
    private void initExpandSpring() {
        if (expandSpringReady) return;
        final float eps = 0.001f;
        float fta = expandInterpolator.getInterpolation(EXPAND_SPRING_TA);
        float ea = 1f - fta;                                              // 接管点误差
        float fPrime = (expandInterpolator.getInterpolation(EXPAND_SPRING_TA + eps)
                - expandInterpolator.getInterpolation(EXPAND_SPRING_TA - eps)) / (2f * eps);
        float va = -fPrime;                                                // 误差变化率 = -f'(TA)
        float wd = EXPAND_SPRING_OMEGA * (float) Math.sqrt(1f - EXPAND_SPRING_ZETA * EXPAND_SPRING_ZETA);
        expandSpringA = ea;
        expandSpringB = (va + EXPAND_SPRING_ZETA * EXPAND_SPRING_OMEGA * ea) / wd;
        expandSpringWd = wd;
        // A·cosθ+B·sinθ = R·cos(θ-ψ)，ψ=atan2(B,A)：首个递减零点 = ψ+π/2（过零），
        // 再经半个周期回零 = ψ+3π/2，即动画落点（值精确为 1、速度极小）
        float psi = (float) Math.atan2(expandSpringB, expandSpringA);
        expandSpringTotal = EXPAND_SPRING_TA + (psi + 1.5f * (float) Math.PI) / wd;
        expandSpringReady = true;
    }

    /** 分段展开缓动：[0,TA] 原超调曲线（前期不变）；(TA, total) 弹簧回落；≥total 精确 1。 */
    private float expandEase(float progress) {
        initExpandSpring();
        if (progress <= EXPAND_SPRING_TA) {
            return expandInterpolator.getInterpolation(progress);
        }
        if (progress >= expandSpringTotal) {
            return 1f;
        }
        float u = progress - EXPAND_SPRING_TA;
        float env = (float) Math.exp(-EXPAND_SPRING_ZETA * EXPAND_SPRING_OMEGA * u);
        float e = env * (expandSpringA * (float) Math.cos(expandSpringWd * u)
                + expandSpringB * (float) Math.sin(expandSpringWd * u));
        return 1f - e;
    }

    private void performSmoothExpandAnimation(
            final View contentContainer) {
        initExpandSpring();
        if (barContainer == null || barLayoutParams == null) {
            return;
        }
        // setAlpha(1f) 移入 doFrame 第一帧中，与 scale/translation 同时生效，
        // 避免在动画首帧前暴露初始小尺寸+translation 状态造成闪一下

        final float startScaleX = contentContainer.getScaleX();
        final float startScaleY = contentContainer.getScaleY();
        final float startTranslationX = contentContainer.getTranslationX();
        final float startTranslationY = contentContainer.getTranslationY();


        // 立即记录开始时间，避免首帧被跳过
        final long adjustedStartTime = System.nanoTime();
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            private int frameCount = 0;
            private long prevFrameTime = 0;

            @Override
            public void doFrame(long frameTimeNanos) {
                if (prevFrameTime == 0) {
                    prevFrameTime = frameTimeNanos;
                }

                frameCount++;
                long intervalNs = frameTimeNanos - prevFrameTime;
                prevFrameTime = frameTimeNanos;

                // 纯时间进度
                float timeProgress = (float) (frameTimeNanos - adjustedStartTime) / 1000000f / animationDuration;
                float progress = Math.min(timeProgress, expandSpringTotal);
                // 前段沿用原超调曲线，末段弹簧回落放缓（见 initExpandSpring/expandEase）
                float easedProgress = expandEase(progress);

                // 插值 scaleX/scaleY/translationX/translationY
                float curScaleX = startScaleX + (1f - startScaleX) * easedProgress;
                float curScaleY = startScaleY + (1f - startScaleY) * easedProgress;
                float curTranslationX = startTranslationX * (1f - easedProgress);
                float curTranslationY = startTranslationY * (1f - easedProgress);

                // contentContainer 透明度：基于 scaleY 绝对值的渐入策略
                // 使用绝对值而非 progress% 使行为与动画速度和窗口类型无关
                // 当 scaleY < 0.20 时透明，0.20~0.40 渐入，>= 0.40 全可见
//                final float scaleYThreshold = 0.14f;
//                final float scaleYFadeEnd = 0.15f;
//                if (curScaleY < scaleYThreshold) {
//                    contentContainer.setAlpha(0f);
//                } else if (curScaleY < scaleYFadeEnd) {
//                    float fadeIn = (curScaleY - scaleYThreshold) / (scaleYFadeEnd - scaleYThreshold);
//                    contentContainer.setAlpha(easeOutCubic(fadeIn));
//                } else {
//                    contentContainer.setAlpha(1f);
//                }

                // WebView 透明度：前一半为 0，后一半从 0 渐变为 1
                if (aiWebView != null) {
                    if (progress < 0.5f) {
                        aiWebView.setAlpha(0f);
                    } else {
                        float alphaProgress = (progress - 0.5f) * 2f;
                        if (alphaProgress > 1f) alphaProgress = 1f;   // 弹簧末段 progress>1，防止算出 >1 的 alpha
                        aiWebView.setAlpha(easeOutCubic(alphaProgress));
                    }
                }


                // 应用变换（只改 View 属性，不触发 updateViewLayout）
                // 首个 vsync 帧时与 scale/translation 一起置为可见，消除初始状态的闪现
                contentContainer.setAlpha(1f);
                contentContainer.setScaleX(curScaleX);
                contentContainer.setScaleY(curScaleY);
                contentContainer.setTranslationX(curTranslationX);
                contentContainer.setTranslationY(curTranslationY);

                Log.d(TAG, "[EXPAND_TRACE] 第" + frameCount + "帧: tp=" + timeProgress + " p=" + progress + " ez=" + easedProgress
                        + " scale=" + curScaleX + "x" + curScaleY
                        + " trans=(" + curTranslationX + "," + curTranslationY + ")");

                // 当动画进度达到 60% 时，通知模糊窗口开始渐变显示
//                if (progress >= 0.4f && !blurWindowFadeInSent) {
//                    blurWindowFadeInSent = true;
//                    sendBlurWindowFadeInBroadcast(progress);
//                    Log.d(TAG, "AI窗口展开动画进度达到60%，已发送模糊窗口渐变显示广播");
//                }

                if (progress < expandSpringTotal) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    // 动画完成，确保最终值精确、恢复普通图层
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);
                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);

                    // 修复：强制 WebView 刷新渲染，解决偶现空白问题
                    // 展开动画期间 alpha 从 0→1 过渡，部分 Android 版本中硬件层可能不刷新初始帧
                    if (aiWebView != null) {
                        aiWebView.setAlpha(1f);
                        aiWebView.invalidate();
                    }
                }
            }
        });
    }

    /**
     * 获取快捷方式列表
     */
    private java.util.ArrayList<ShortcutItem> loadShortcutList() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
            String jsonStr = prefs.getString("shortcut_list", "");
            if (android.text.TextUtils.isEmpty(jsonStr)) {
                java.util.ArrayList<ShortcutItem> defaults = ShortcutItem.getDefaultShortcuts();
                copyDefaultIconsToStorage(defaults);
                return defaults;
            }
            java.util.ArrayList<ShortcutItem> list = new java.util.ArrayList<>();
            try {
                org.json.JSONArray jsonArray = new org.json.JSONArray(jsonStr);
                for (int i = 0; i < jsonArray.length(); i++) {
                    list.add(ShortcutItem.fromJson(jsonArray.getJSONObject(i)));
                }
                // 过滤掉禁用的快捷方式
                java.util.ArrayList<ShortcutItem> enabledList = new java.util.ArrayList<>();
                for (ShortcutItem si : list) {
                    if (si.isEnabled()) {
                        enabledList.add(si);
                    }
                }
                return enabledList;
            } catch (Exception e) {
                Log.e(TAG, "解析快捷方式列表失败", e);
                java.util.ArrayList<ShortcutItem> defaults = ShortcutItem.getDefaultShortcuts();
                copyDefaultIconsToStorage(defaults);
                return defaults;
            }
        } catch (Exception e) {
            Log.e(TAG, "加载快捷方式列表失败", e);
            java.util.ArrayList<ShortcutItem> defaults = ShortcutItem.getDefaultShortcuts();
            copyDefaultIconsToStorage(defaults);
            return defaults;
        }
    }

    /**
     * 首次运行时，将 res/drawable/ 中的默认图标复制到内部存储
     */
    private void copyDefaultIconsToStorage(java.util.ArrayList<ShortcutItem> list) {
        java.io.File iconsDir = new java.io.File(getFilesDir(), "shortcut_icons");
        if (!iconsDir.exists()) {
            iconsDir.mkdirs();
        }
        for (ShortcutItem item : list) {
            if (!"image".equals(item.getIconType())) continue;
            if (!android.text.TextUtils.isEmpty(item.getIconPath())) continue;

            int resId = getResources().getIdentifier(item.getId(), "drawable", getPackageName());
            if (resId == 0) continue;

            try {
                java.io.File destFile = new java.io.File(iconsDir, item.getId() + ".png");
                if (destFile.exists()) {
                    item.setIconPath(destFile.getAbsolutePath());
                    continue;
                }
                java.io.InputStream in = getResources().openRawResource(resId);
                java.io.FileOutputStream out = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.close();
                in.close();
                item.setIconPath(destFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "复制默认图标失败: " + item.getId(), e);
            }
        }
    }

    /**
     * 双击提示条：展开快捷方式窗口
     */
    private void expandToShortcutWindow() {
        if (isAiWindowExpanded || isShortcutWindowExpanded) {
            return;
        }

        if (overlayView == null) {
            createOverlayWindow();
        }

        // 确保 overlayView 在 barContainer 下方
        if (overlayView != null && barContainer != null) {
            try {
                // 光晕窗口不参与换序（仅按下期间挂载）
                if (barContainer.getWindowToken() != null) {
                    windowManager.removeView(barContainer);
                }
                // 如果 overlayView 尚未添加，则添加
                if (overlayView.getWindowToken() == null) {
                    windowManager.addView(overlayView, overlayLayoutParams);
                }
                // 重新添加 barContainer（使用当前的 barLayoutParams）
                windowManager.addView(barContainer, barLayoutParams);
                // 光晕窗口不参与换序（仅按下期间挂载）
                Log.d(TAG, "窗口层级已调整：barContainer 置于 overlayView 之上");
            } catch (Exception e) {
                Log.e(TAG, "调整窗口层级失败", e);
            }
        }

        Log.d(TAG, "展开快捷方式窗口: isAiExpanded=" + isAiWindowExpanded
                + " isShortcutExpanded=" + isShortcutWindowExpanded);

        if (barContainer == null || windowManager == null) return;

        try {
            DisplayMetrics realMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
            final int screenWidth = realMetrics.widthPixels;
            final int screenHeight = realMetrics.heightPixels;

            // 保存展开前的位置用于动画
            final int startX = barPositionX;
            final int startY = dpToPx(barPositionY);

            // 设置全屏表面
            barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            barLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;

            int requiredWidth = Math.max(screenWidth, 4 * Math.abs(0) + dpToPx(shortcutWindowWidth));
            // 底边锚定公式（topMargin = posY + (锚定K - 实际高h)/4）下父容器需容纳
            // 顶部越界项 2h-K，故表面高度 = 4*|posY| + max(K, 2*maxH-K)
            // （K=默认高度350dp；maxH=350 时与旧值 4*|posY|+maxH 完全相同，仅增不减）
            int anchorHeightPx = dpToPx(AppConfig.DEFAULT_SHORTCUT_WINDOW_HEIGHT_DP);
            int requiredHeight = Math.max(screenHeight,
                    4 * Math.abs(shortcutWindowPositionY)
                            + Math.max(anchorHeightPx, 2 * dpToPx(shortcutWindowHeight) - anchorHeightPx));

            barLayoutParams.x = 0;
            barLayoutParams.y = 0;
            barLayoutParams.width = requiredWidth;
            barLayoutParams.height = requiredHeight;


            windowManager.updateViewLayout(barContainer, barLayoutParams);
            Log.d(TAG, "[SHORTCUT_EXPAND_DEBUG] barContainer 全屏: w=" + requiredWidth + " h=" + requiredHeight);
            scheduleExpandWatchdog("shortcut");

            // ===== 关键修复：等待 barContainer 布局完成后再设置变换和启动动画 =====
            barContainer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    barContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    Log.d(TAG, "[SHORTCUT_EXPAND_DEBUG] barContainer 布局完成，开始设置变换");

                    try {
                        // 替换视图
                        barContainer.removeAllViews();
                        View shortcutView = createShortcutWindowView();
                        barContainer.addView(shortcutView);

                        // 获取内容容器（createShortcutWindowView 已设置实际高度）
                        FrameLayout rootLayout = (FrameLayout) shortcutView;
                        View contentContainer = rootLayout.getChildAt(0);
                        FrameLayout.LayoutParams contentLp = (FrameLayout.LayoutParams) contentContainer.getLayoutParams();
                        final int contentTargetWidth = contentLp.width;
                        final int contentTargetHeight = contentLp.height;

                        // 初始设为目标尺寸，用 scale 缩放
                        contentLp.width = contentTargetWidth;
                        contentLp.height = contentTargetHeight;
                        contentContainer.setLayoutParams(contentLp);

                        int effectiveStartWidth = (currentWidth == ViewGroup.LayoutParams.MATCH_PARENT || currentWidth <= 0) ? screenWidth : currentWidth;
                        final float initScaleX = (float) effectiveStartWidth / contentTargetWidth;
                        final float initScaleY = (float) dpToPx(currentHeight) / contentTargetHeight;
                        final float initTranslationX = startX - 2 * contentLp.leftMargin;
                        final float initTranslationY = startY - 2 * contentLp.topMargin;
                        Log.d(TAG, "[SHORTCUT_EXPAND_DEBUG] initTrans: (" + initTranslationX + "," + initTranslationY + ")"
                                + " start=(" + startX + "," + startY + ")"
                                + " margin=(" + contentLp.leftMargin + "," + contentLp.topMargin + ")"
                                + " contentSize=" + contentTargetWidth + "x" + contentTargetHeight
                                + " initScale=" + initScaleX + "x" + initScaleY);


                        contentContainer.setAlpha(0f);
                        Log.d(TAG, "[SHORTCUT_EXPAND_TRACE] scale/translation 已设置 at " + System.currentTimeMillis()
                                + " scale=" + initScaleX + "x" + initScaleY
                                + " trans=(" + initTranslationX + "," + initTranslationY + ")");
                        animationSetTime = System.currentTimeMillis();

                        // 设置初始圆角
//                        ((GradientDrawable) contentContainer.getBackground()).setCornerRadius(dpToPx(currentCornerRadius));
//                        if (contentContainer.getForeground() instanceof GradientDrawable) {
//                            ((GradientDrawable) contentContainer.getForeground()).setCornerRadius(dpToPx(currentCornerRadius));
//                        }
//                        contentContainer.invalidateOutline();

                        isShortcutWindowExpanded = true;

                        // ===== 新增：启动下层模糊窗口（如果样式为亚克力或液态玻璃）=====
//                        if (shortcutStyle != 0) {
//                            int sTargetWidth = dpToPx(shortcutWindowWidth);
//                            int sTargetHeight = dpToPx(shortcutWindowHeight);
//                            startBlurWindow(sTargetWidth, sTargetHeight, 0, shortcutWindowPositionY, shortcutCornerRadius, shortcutBlurRadius);
//                        }

                        // 提前启动代理 Activity，展开过程中即可响应返回手势
//                        startWindowHostActivity();

                        // ===== View.post()：等待 contentContainer 布局完全完成后设置属性并启动动画 =====
                        // 使用 View.post() 而非 OnGlobalLayoutListener，兼容性更强
                        final View cc = contentContainer;


                        cc.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "[SHORTCUT_EXPAND_DEBUG] cc.post 执行，设置属性并启动动画"
                                        + " contentWidth=" + cc.getWidth() + " contentHeight=" + cc.getHeight()
                                        + " getX=" + cc.getX() + " getY=" + cc.getY()
                                        + " scale=" + cc.getScaleX() + "x" + cc.getScaleY()
                                        + " trans=(" + cc.getTranslationX() + "," + cc.getTranslationY() + ")");

                                // layout 完成后设置全部属性（pivot/scale/translation/layer），确保首帧位置正确
                                cc.setPivotX(contentTargetWidth / 2f);
                                cc.setPivotY(contentTargetHeight / 2f);
                                cc.setScaleX(initScaleX);
                                cc.setScaleY(initScaleY);
                                cc.setTranslationX(initTranslationX);
                                cc.setTranslationY(initTranslationY);
                                cc.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                                updateShortcutWindowStyle();
                                
                                // 【关键修复】快捷窗口需要根据实际位置更新模糊窗口
                                // 等待布局完成后获取真实坐标并发送广播
//                                if (shortcutStyle != 0) {
//                                    cc.post(() -> {
//                                        try {
//                                            // 计算 contentContainer 在屏幕上的绝对坐标
//
//                                            int blurX = cc.getLeft();
//                                            int blurY = cc.getTop() - getNavigationBarHeight()/2;
//                                            int blurWidth = cc.getWidth();
//                                            int blurHeight = cc.getHeight();
//
//                                            Intent updateIntent = new Intent("UPDATE_BLUR_WINDOW_SIZE");
//                                            updateIntent.setPackage(getPackageName());
//                                            updateIntent.putExtra("blur_x", blurX);
//                                            updateIntent.putExtra("blur_y", blurY);
//                                            updateIntent.putExtra("blur_width", blurWidth);
//                                            updateIntent.putExtra("blur_height", blurHeight);
//                                            updateIntent.putExtra("blur_corner_radius", dpToPx(shortcutCornerRadius));
//                                            sendBroadcast(updateIntent);
//                                            Log.d(TAG, "已发送快捷窗口模糊更新: x=" + blurX + " y=" + blurY
//                                                    + " w=" + blurWidth + " h=" + blurHeight + " 圆角=" + shortcutCornerRadius + "dp");
//                                        } catch (Exception e) {
//                                            Log.e(TAG, "发送模糊窗口更新失败", e);
//                                        }
//                                    });
//                                }


                                // 背景遮罩先渐入完成，再执行窗口展开动画
                                if (overlayView != null) {
                                    final View ov = overlayView;
                                    final float targetAlpha = overlayAlpha / 100.0f;
                                    final long startTime = System.currentTimeMillis();
                                    ov.setAlpha(0f);
                                    isOverlayAnimating = true;
                                    Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                                        @Override
                                        public void doFrame(long frameTimeNanos) {
                                            // 以"快捷窗口已展开"为准：isOverlayAnimating 是共享标志，
                                            // 被失败路径置 false 会导致本循环静默死亡、展开中途停摆（整屏冻结）
                                            if (!isShortcutWindowExpanded) return;
                                            long elapsed = System.currentTimeMillis() - startTime;
                                            float progress = Math.min((float) elapsed / overlayAnimDuration, 1.0f);
                                            float eased = easeOutCubic(progress);
                                            ov.setAlpha(targetAlpha * eased);
                                            if (progress < 1.0f) {
                                                Choreographer.getInstance().postFrameCallback(this);
                                            } else {
                                                isOverlayAnimating = false;
                                                performShortcutExpandAnimation(cc);
                                            }
                                        }
                                    });
                                } else {
                                    performShortcutExpandAnimation(cc);
                                }
                            }
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "[SHORTCUT_EXPAND_DEBUG] 布局回调内展开失败", e);
                        isOverlayAnimating = false;
                        isShortcutWindowExpanded = false;
                        // 关键：展开已把 bar 窗口改为全屏并可能已加入遮罩，失败必须恢复，
                        // 否则残留全屏可触摸窗口导致整屏无法点击
                        restoreBarWindowAfterFailure("expandToShortcutWindow 布局回调内失败");
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "展开快捷方式窗口失败", e);
            isShortcutWindowExpanded = false;
            restoreBarWindowAfterFailure("expandToShortcutWindow 外层失败");
        }
    }

    /**
     * 快捷方式窗口展开动画
     */
    private void performShortcutExpandAnimation(final View contentContainer) {
        initExpandSpring();
        final float startScaleX = contentContainer.getScaleX();
        final float startScaleY = contentContainer.getScaleY();
        final float startTranslationX = contentContainer.getTranslationX();
        final float startTranslationY = contentContainer.getTranslationY();

        final long adjustedStartTime = System.nanoTime();

        Log.d(TAG, "[SHORTCUT_EXPAND_DEBUG] 展开动画开始 - scale: " + startScaleX + "x" + startScaleY
                + " -> 1x1"
                + " trans=(" + startTranslationX + "," + startTranslationY + ") -> 0"
                + " delaySinceSet=" + (System.currentTimeMillis() - animationSetTime) + "ms");

        // setAlpha(1f) 移入 doFrame 第一帧中，与 scale/translation 同时生效，
        // 避免在动画首帧前暴露初始小尺寸+translation 状态造成闪一下

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            private int frameCount = 0;

            @Override
            public void doFrame(long frameTimeNanos) {
                frameCount++;
                // 纯时间进度
                float timeProgress = (float) (frameTimeNanos - adjustedStartTime) / 1000000f / animationDuration;
                float progress = Math.min(timeProgress, expandSpringTotal);
                // 前段沿用原超调曲线，末段弹簧回落放缓（见 initExpandSpring/expandEase）
                float easedProgress = expandEase(progress);

                float curScaleX = startScaleX + (1f - startScaleX) * easedProgress;
                float curScaleY = startScaleY + (1f - startScaleY) * easedProgress;
                float curTranslationX = startTranslationX * (1f - easedProgress);
                float curTranslationY = startTranslationY * (1f - easedProgress);

                // contentContainer 透明度：基于 scaleY 绝对值的渐入策略
                // 使用绝对值而非 progress% 使行为与动画速度和窗口类型无关
                // 当 scaleY < 0.20 时透明，0.20~0.40 渐入，>= 0.40 全可见
//                final float scaleYThreshold = 0.12f;
//                final float scaleYFadeEnd = 0.13f;
//                if (curScaleY < scaleYThreshold) {
//                    contentContainer.setAlpha(0f);
//                } else if (curScaleY < scaleYFadeEnd) {
//                    float fadeIn = (curScaleY - scaleYThreshold) / (scaleYFadeEnd - scaleYThreshold);
//                    contentContainer.setAlpha(easeOutCubic(fadeIn));
//                } else {
//                    contentContainer.setAlpha(1f);
//                }


                contentContainer.setAlpha(1f);
                contentContainer.setScaleX(curScaleX);
                contentContainer.setScaleY(curScaleY);
                contentContainer.setTranslationX(curTranslationX);
                contentContainer.setTranslationY(curTranslationY);

                Log.d(TAG, "[SHORTCUT_EXPAND_TRACE] 第" + frameCount + "帧: tp=" + timeProgress + " p=" + progress + " ez=" + easedProgress
                        + " scale=" + curScaleX + "x" + curScaleY
                        + " trans=(" + curTranslationX + "," + curTranslationY + ")");

                // 当动画进度达到 60% 时，通知模糊窗口开始渐变显示
//                if (progress >= 0.6f && !blurWindowFadeInSent) {
//                    blurWindowFadeInSent = true;
//                    sendBlurWindowFadeInBroadcast(progress);
//                    Log.d(TAG, "快捷窗口展开动画进度达到60%，已发送模糊窗口渐变显示广播");
//                }

                if (progress < expandSpringTotal) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);
                    // 请求重新布局以确保子视图（GridLayout 单元格）正确测量
                    contentContainer.requestLayout();
                }
            }
        });
    }

    /**
     * 创建快捷方式窗口视图
     * 高度优先以图标行数所占高度为准，超过最大高度时限制高度并启用滑动
     */
    private View createShortcutWindowView() {
        // rootLayout: 全屏透明，用于捕获外部点击
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.TRANSPARENT);
        rootLayout.setClipChildren(false);

        // 点击外部关闭
        rootLayout.setOnTouchListener(new View.OnTouchListener() {
            private boolean mOutsideDown = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        View content = rootLayout.getChildAt(0);
                        if (content != null) {
                            android.graphics.Rect rect = new android.graphics.Rect();
                            content.getHitRect(rect);
                            mOutsideDown = !rect.contains((int) event.getX(), (int) event.getY());
                        } else {
                            mOutsideDown = true;
                        }
                        return mOutsideDown;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (mOutsideDown) {
                            mOutsideDown = false;
                            collapseToBottomBar();
                            return true;
                        }
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

        // ===== 1. 创建 GridLayout 并添加快捷方式 =====
        android.widget.GridLayout gridLayout = new android.widget.GridLayout(this);
        gridLayout.setColumnCount(4);
        gridLayout.setRowCount(android.widget.GridLayout.UNDEFINED);
        gridLayout.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        java.util.ArrayList<ShortcutItem> shortcuts = loadShortcutList();
        for (final ShortcutItem item : shortcuts) {
            View cell = createShortcutCell(item);
            gridLayout.addView(cell);
        }

        // 测量 GridLayout 的自然高度，用于确定窗口实际高度
        int gridWidthMeasure = dpToPx(shortcutWindowWidth);
        gridLayout.measure(
                View.MeasureSpec.makeMeasureSpec(gridWidthMeasure, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int contentHeight = gridLayout.getMeasuredHeight();
        int maxHeight = dpToPx(shortcutWindowHeight);
        int actualHeight = Math.min(contentHeight, maxHeight);

        // 计算调整后的Y位置，使窗口下边缘不随内容行数及「最大高度」设置变化
        // （只由 positionY + 锚定常数 350dp 决定，见 AppConfig.DEFAULT_SHORTCUT_WINDOW_HEIGHT_DP）
        int adjustedTopMargin = shortcutWindowPositionY
                + (dpToPx(AppConfig.DEFAULT_SHORTCUT_WINDOW_HEIGHT_DP) - actualHeight) / 4;

        // ===== 2. 最外层内容容器 =====
        FrameLayout contentContainer = new FrameLayout(this);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                dpToPx(shortcutWindowWidth),
                actualHeight);
        containerParams.gravity = Gravity.CENTER;
        containerParams.leftMargin = 0;
        containerParams.topMargin = adjustedTopMargin;
        containerParams.rightMargin = 0;
        containerParams.bottomMargin = -adjustedTopMargin;
        contentContainer.setLayoutParams(containerParams);
        contentContainer.setClipChildren(false);

        // ===== 3. 背景层（半透明 + 模糊） =====
        FrameLayout bgContainer = new FrameLayout(this);
        FrameLayout.LayoutParams bgParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        bgContainer.setLayoutParams(bgParams);

        float cornerPx = dpToPx(shortcutCornerRadius);
        // 背景颜色（半透明或不透明）
        int bgColor = computeShortcutBgColor();
//        if (shortcutStyle == 2) {
//            // 液态玻璃：使用渐变 + 光晕
//            GradientDrawable glassDrawable = new GradientDrawable(
//                    GradientDrawable.Orientation.TL_BR,
//                    new int[] { bgColor, adjustBrightness(bgColor, 0.8f), bgColor }
//            );
//            glassDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
//            glassDrawable.setCornerRadius(dpToPx(shortcutCornerRadius));
//            // 添加高光线条（通过设置 stroke 或叠加子 View）
//            bgContainer.setBackground(glassDrawable);
//        } else if (shortcutStyle == 1) {
//            // 亚克力：保留 RenderEffect 模糊，但背景色改为带噪声纹理（可选）
//            bgContainer.setBackgroundColor(bgColor);
//            applyShortcutBlurEffect(bgContainer, true, shortcutBlurRadius);
//        } else {
//            bgContainer.setBackgroundColor(bgColor);
//        }
        if (shortcutStyle == 0) {
            // 不透明或半透明纯色（无效果）
            bgContainer.setBackgroundColor(bgColor);
        } else if (shortcutStyle == 1) {
            // 亚克力风格：半透明底色 + 噪点 + 高光
            LayerDrawable layers = new LayerDrawable(new Drawable[] {
                    createBaseGradient(bgColor, cornerPx),
                    createNoiseDrawable(),
                    createHighlightDrawable(cornerPx)
            });
            bgContainer.setBackground(layers);
        } else if (shortcutStyle == 2) {
            // 液态玻璃风格：更通透的底色 + 强高光 + 轻微阴影
            int glassColor = adjustBrightness(bgColor, 1.2f); // 提亮
            LayerDrawable layers = new LayerDrawable(new Drawable[] {
                    createBaseGradient(glassColor, cornerPx),
                    createHighlightDrawable(cornerPx)
            });
            bgContainer.setBackground(layers);
        }

        // 圆角裁剪（与 contentContainer 一致）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bgContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(shortcutCornerRadius));
                }
            });
            bgContainer.setClipToOutline(true);
        }

        // 应用模糊效果（仅对背景层，不影响上层内容清晰度）
        applyShortcutBlurEffect(bgContainer, shortcutStyle != 0, shortcutBlurRadius);

        // 先添加背景层
        contentContainer.addView(bgContainer);

        // ===== 4. 内容层（GridLayout 包裹 ScrollView） =====
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        gridLayout.setLayoutParams(gridParams);

        View contentView;
        if (contentHeight > maxHeight) {
            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setClipToOutline(true);
            scrollView.addView(gridLayout);
            contentView = scrollView;
        } else {
            contentView = gridLayout;
        }
        // 内容层透明，不遮挡背景
        contentView.setBackgroundColor(Color.TRANSPARENT);

        // 添加内容层（在背景之上）
        contentContainer.addView(contentView);

        // 保存引用（用于后续样式更新）
        shortcutContentContainer = contentContainer;

        // 将 contentContainer 添加到 rootLayout
        rootLayout.addView(contentContainer);
        return rootLayout;
    }

    /**
     * 创建渐变底色 Drawable（模拟玻璃底色）
     * @param baseColor 基础颜色（带透明度）
     * @param cornerPx 圆角半径（px）
     * @return 渐变 Drawable
     */
    private GradientDrawable createBaseGradient(int baseColor, float cornerPx) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, // 从左上到右下
                new int[] {
                        baseColor,
                        adjustBrightness(baseColor, 0.85f),
                        baseColor
                }
        );
        gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        gd.setCornerRadius(cornerPx);
        return gd;
    }



    /**
     * 创建顶部高光条 Drawable（模拟玻璃反光）
     */
    private Drawable createHighlightDrawable(float cornerPx) {
        GradientDrawable highlight = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(80, 255, 255, 255), // 顶部白色半透明
                        Color.argb(0, 255, 255, 255)   // 底部完全透明
                }
        );
        highlight.setCornerRadius(cornerPx);
        highlight.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return highlight;
    }

    /**
     * 创建噪点纹理 Drawable（可选，增加质感）
     */
    private Drawable createNoiseDrawable() {
        Bitmap noiseBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(noiseBitmap);
        Paint paint = new Paint();
        Random random = new Random();
        for (int i = 0; i < 200; i++) {
            int x = random.nextInt(64);
            int y = random.nextInt(64);
            int alpha = random.nextInt(30) + 10;
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawRect(x, y, x + 1, y + 1, paint);
        }
        BitmapDrawable noiseDrawable = new BitmapDrawable(getResources(), noiseBitmap);
        noiseDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return noiseDrawable;
    }

    private int adjustBrightness(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1.0f, hsv[2] * factor);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private void updateShortcutWindowStyle() {
        if (shortcutContentContainer == null) return;
        View bgLayer = ((ViewGroup) shortcutContentContainer).getChildAt(0);
        if (bgLayer != null) {
            int bgColor = computeShortcutBgColor();
            if (shortcutStyle == 2) {
                // 重新生成液态玻璃 Drawable
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] { bgColor, adjustBrightness(bgColor, 0.8f), bgColor }
                );
                gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                gd.setCornerRadius(dpToPx(shortcutCornerRadius));
                bgLayer.setBackground(gd);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bgLayer.setRenderEffect(null); // 清除模糊
                }
            } else if (shortcutStyle == 1) {
                bgLayer.setBackgroundColor(bgColor);
                applyShortcutBlurEffect(bgLayer, true, shortcutBlurRadius);
            } else {
                bgLayer.setBackgroundColor(bgColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bgLayer.setRenderEffect(null);
                }
            }
            bgLayer.invalidate();
        }
    }

    private void applyShortcutBlurEffect(View view, boolean enable, int radiusDp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enable && radiusDp > 0) {
                float radiusPx = dpToPx(radiusDp);
                RenderEffect effect = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP);
                view.setRenderEffect(effect);
            } else {
                view.setRenderEffect(null);
            }
        }
    }



    /**
     * 创建单个快捷方式按钮
     * 支持 Emoji 和图片两种图标类型，图片显示为圆角矩形
     */
    private View createShortcutCell(final ShortcutItem item) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        // 计算均匀分布的单元格宽度：窗口宽度减去 grid padding 后 / 4
        int gridPadding = dpToPx(12) * 2;
        int cellWidth = (dpToPx(shortcutWindowWidth) - gridPadding) / 4;

        android.widget.GridLayout.LayoutParams cellParams = new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED));
        cellParams.width = cellWidth;
        cellParams.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
        cell.setLayoutParams(cellParams);

        cell.setClickable(true);
        cell.setFocusable(true);

        // 图标大小缩放因子
        final float iconScale = Math.max(0.5f, Math.min(1.5f, shortcutIconSize / 100.0f));

        // 判断图标类型
        if ("image".equals(item.getIconType())) {
            // ===== 图片图标（圆角矩形） =====
            FrameLayout iconContainer = new FrameLayout(this);
            int iconSize = Math.round((cellWidth - dpToPx(16)) * iconScale);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER;
            iconContainer.setLayoutParams(iconParams);

            // 圆角矩形背景
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.RECTANGLE);
            iconBg.setCornerRadius(dpToPx(12));

            // 尝试加载自定义图片
            android.graphics.Bitmap bitmap = loadShortcutIcon(item);
            if (bitmap != null) {
                // 有自定义图片 → 显示图片
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
        // 名称
        TextView nameView = new TextView(this);
        nameView.setText(item.getName());
        nameView.setTextSize(Math.max(10, Math.round(11 * iconScale)));
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        // 根据系统深色模式设置文字颜色
        if (isDarkMode()) {
            nameView.setTextColor(0xFFCCCCCC); // 深色模式使用浅灰色
        } else {
            nameView.setTextColor(0xFF222222); // 浅色模式使用深灰色
        }
        nameView.setGravity(Gravity.CENTER);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setMaxLines(1);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = dpToPx(4);
        nameView.setLayoutParams(nameParams);
        cell.addView(nameView);

        cell.setOnClickListener(v -> launchShortcutApp(item));

        // 按压图标缩小、松手回弹动画（连贯缩放）
        cell.setOnTouchListener(new View.OnTouchListener() {
            private boolean mIsPressed = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mIsPressed = true;
                        v.animate().cancel();
                        v.animate()
                                .scaleX(0.90f)
                                .scaleY(0.90f)
                                .setDuration(120)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start();
                        return false; // 让事件继续传播，OnClickListener 可触发
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (mIsPressed) {
                            mIsPressed = false;
                            v.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(250)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f))
                                    .start();
                        }
                        return false;
                }
                return false;
            }
        });

        return cell;
    }

    /**
     * 加载快捷方式的自定义图片图标
     *
     * @return Bitmap，若无图片文件则返回 null
     */
    private android.graphics.Bitmap loadShortcutIcon(ShortcutItem item) {
        if (item == null || item.getIconPath() == null || item.getIconPath().isEmpty()) {
            return null;
        }
        try {
            java.io.File file = new java.io.File(item.getIconPath());
            if (file.exists()) {
                return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "加载快捷方式图标失败: " + item.getName(), e);
        }
        return null;
    }

    /**
     * 根据快捷方式 ID 生成一致的占位颜色
     */
    private int getShortcutPlaceholderColor(String id) {
        if (id == null) return 0xFF1677FF;
        switch (id) {
            case "alipay_scan":
                return 0xFF1677FF; // 蓝色
            case "alipay_pay":
                return 0xFF1677FF;
            case "wechat_scan":
                return 0xFF07C160; // 绿色
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

    /**
     * 通过快捷方式打开应用
     */
    /**
     * 通过快捷方式打开应用
     * 对于非导出的内部 Activity（如支付宝/微信的扫一扫、付款码），使用 URL Scheme 启动
     */
    private void launchShortcutApp(ShortcutItem item) {
        if (item == null || android.text.TextUtils.isEmpty(item.getPackageName())) {
            Log.d(TAG, "快捷方式包名为空");
            return;
        }

        android.content.SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        ShortcutLaunchConfig config = ShortcutLaunchConfig.load(prefs, item.getId());
        try {
            Intent launchIntent = createShortcutLaunchIntent(item);
            // 与手势路径共用 startWithWindowing：小窗=mode100 / 开关开=mode1 / 默认普通启动
            startWithWindowing(launchIntent, config.openInWindow);
            Log.d(TAG, "已打开快捷方式: " + item.getName());

            // 打开应用后收起快捷方式窗口
            collapseToBottomBar();
        } catch (android.content.ActivityNotFoundException e) {
            // URL Scheme 无法处理时，降级为打开应用主界面
            Log.w(TAG, "URL Scheme 打开失败，尝试打开应用主界面: " + item.getName(), e);
            try {
                Intent fallbackIntent = new Intent(Intent.ACTION_MAIN);
                fallbackIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                fallbackIntent.setPackage(item.getPackageName());
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startWithWindowing(fallbackIntent, config.openInWindow);
                Log.d(TAG, "已打开应用主界面: " + item.getName());
                collapseToBottomBar();
            } catch (Exception e2) {
                Log.e(TAG, "打开快捷方式失败: " + item.getName(), e2);
                Toast.makeText(this, "打开 " + item.getName() + " 失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "打开快捷方式失败: " + item.getName(), e);
            Toast.makeText(this, "打开 " + item.getName() + " 失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 根据快捷方式创建适当的 Intent
     * 部分内部 Activity 非导出（android:exported=false），需使用 URL Scheme 替代显式组件名
     */
    private Intent createShortcutLaunchIntent(ShortcutItem item) {
        // 从配置加载启动方式
        android.content.SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        ShortcutLaunchConfig config = ShortcutLaunchConfig.load(prefs, item.getId());
        Intent configIntent = config.buildLaunchIntent();
        if (configIntent != null) {
            return configIntent;
        }

        // 如果类名为空，使用包名启动（类似单击提示条的方式）
        if (android.text.TextUtils.isEmpty(item.getClassName())) {
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

    /**
     * 快捷方式窗口内部收起（带连贯缩回动画）
     */
    private void collapseShortcutWindowInternal() {
        if (!isShortcutWindowExpanded) return;

        Log.d(TAG, "收起快捷方式窗口: " + android.util.Log.getStackTraceString(new Throwable()));

        // ===== 关键修复：收起动画开始时立即通知模糊窗口执行渐隐动画 =====
//        sendBlurWindowFadeOutBroadcast();
        Log.d(TAG, "已发送快捷窗口模糊渐隐广播");

        // 动画期间在视图层拦截所有触摸
        barContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        // 查找 contentContainer（rootLayout 下的第一个子view，即 createShortcutWindowView 中添加的 contentContainer）
        View contentContainer = null;
        if (barContainer.getChildCount() > 0) {
            View rootView = barContainer.getChildAt(0);
            if (rootView instanceof FrameLayout && ((FrameLayout) rootView).getChildCount() > 0) {
                contentContainer = ((FrameLayout) rootView).getChildAt(0);
            }
        }

        final DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        final int screenW = dm.widthPixels;

        if (contentContainer != null) {
            final View content = contentContainer;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
            final int contentStartWidth = lp.width;
            final int contentStartHeight = lp.height;

            // content 缩小的目标尺寸：匹配提示条区域
            int effectiveTargetWidth = (currentWidth == ViewGroup.LayoutParams.MATCH_PARENT || currentWidth <= 0) ? screenW : currentWidth;
            final int contentTargetW = Math.max(effectiveTargetWidth, 1);
            final int contentTargetH = Math.max(dpToPx(currentHeight), 1);

            // 收起动画目标变换（逆向展开动画的变换）
            final float endScaleX = (float) contentTargetW / contentStartWidth;
            final float endScaleY = (float) contentTargetH / contentStartHeight;
            final float endTranslationX = barPositionX - 2 * lp.leftMargin;
            final float endTranslationY = dpToPx(barPositionY) - 2 * lp.topMargin;

            // 圆角插值：从快捷窗口实际圆角过渡到提示条圆角
            // 从 contentContainer 的 GradientDrawable 读取实际圆角值（展开时设置的 currentCornerRadius），
            // 避免使用 shortcutCornerRadius 配置值导致收起瞬间圆角跳变
//            final int startCornerPx = Math.round(((GradientDrawable) content.getBackground()).getCornerRadius());
//            final int targetCornerPx = dpToPx(currentCornerRadius);

//            Log.d(TAG, "收起快捷窗口动画 - scale: 1x1 -> " + endScaleX + "x" + endScaleY
//                    + " trans: 0,0 -> (" + endTranslationX + "," + endTranslationY + ")"
//                    + " corner: " + startCornerPx + "px -> " + targetCornerPx + "px");

            performSmoothCollapseAnimation(
                    content,
                    endScaleX, endScaleY,
                    endTranslationX, endTranslationY,
//                    startCornerPx, targetCornerPx,
                    new Runnable() {
                        @Override
                        public void run() {
                            finishCollapseShortcutWindow();
                        }
                    }
            );
        } else {
            finishCollapseShortcutWindow();
        }
    }

    /**
     * 快捷方式窗口收起完成：清理并恢复底部提示条
     */
    private void finishCollapseShortcutWindow() {
        Log.d(TAG, "finishCollapseShortcutWindow");
        try {
            barContainer.removeAllViews();

            // 恢复为底部提示条
            barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
//                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            barLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
            barLayoutParams.width = computeBarWindowWidth();
            barLayoutParams.height = computeBarWindowHeight();
            barLayoutParams.x = barPositionX;
            barLayoutParams.y = dpToPx(barPositionY);
            windowManager.updateViewLayout(barContainer, barLayoutParams);
            syncGlowWindow();

            barContainer.setBackground(null);
            barContainer.setClipToOutline(false);

            // 重新创建底部提示条（覆盖层/光晕常驻独立光晕窗口，无需重建）
            bottomBar = createBottomBarView();
            barContainer.addView(bottomBar);
            barContainer.setAlpha(1f);

            isShortcutWindowExpanded = false;

            // 隐藏背景遮罩
            if (overlayView != null) {
                final View ov = overlayView;
                final float startAlpha = overlayAlpha / 100.0f;
                final long startTime = System.currentTimeMillis();
                ov.setAlpha(startAlpha);
                Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                    @Override
                    public void doFrame(long frameTimeNanos) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        float progress = Math.min((float) elapsed / overlayAnimDuration, 1.0f);
                        ov.setAlpha(startAlpha * (1 - easeInCubic(progress)));
                        if (progress < 1.0f) {
                            Choreographer.getInstance().postFrameCallback(this);
                        } else {
                            ov.setAlpha(0f);
                        }
                    }
                });
            }

            barContainer.setOnTouchListener(null);

            // 移除背景遮罩（释放窗口资源，避免干扰其他应用弹窗）
            removeOverlayWindow();

            // 关闭代理 Activity
            finishWindowHostActivity();
            
            // 注意：blurWindow 已在收起动画开始时通过 sendBlurWindowFadeOutBroadcast 关闭
            // 此处不再重复调用 closeBlurWindow()
            
            Log.d(TAG, "快捷方式窗口已收起");
        } catch (Exception e) {
            Log.e(TAG, "finishCollapseShortcutWindow 失败", e);
            isShortcutWindowExpanded = false;
            finishWindowHostActivity();
            // 收起中途异常时尺寸可能尚未恢复、触摸拦截可能仍在，必须兜底恢复
            restoreBarWindowAfterFailure("finishCollapseShortcutWindow 失败");
        }
    }

    /**
     * 执行展开动画（旧版本，保留以防需要）
     */
    private void performExpandAnimation() {
        if (aiWindowContainer == null || aiWindowParams == null) {
            return;
        }

        // 从中心点开始放大
        final int targetWidth = aiWindowWidth;
        final int targetHeight = aiWindowHeight;
        final long startTime = System.currentTimeMillis();

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / animationDuration, 1.0f);

                // 使用缓动函数让动画更自然
                float easedProgress = easeOutCubic(progress);

                int currentWidth = (int) (targetWidth * easedProgress);
                int currentHeight = (int) (targetHeight * easedProgress);

                try {
                    aiWindowParams.width = currentWidth;
                    aiWindowParams.height = currentHeight;
                    windowManager.updateViewLayout(aiWindowContainer, aiWindowParams);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (progress < 1.0f) {
                    handler.postDelayed(this, FRAME_INTERVAL); // 8ms = 120fps
                }
            }
        });
    }


    /**
     * 收起为底部提示条
     */
    private void collapseToBottomBar() {
        if (!isAiWindowExpanded && !isShortcutWindowExpanded) {
            return;
        }

        // 如果快捷方式窗口展开，直接收起（简化处理）
        if (isShortcutWindowExpanded) {
            collapseShortcutWindowInternal();
            return;
        }

        Log.d(TAG, "[COLLAPSE_DEBUG] collapseToBottomBar 开始 at time=" + System.currentTimeMillis());

        // 移除输入法检测
        removeKeyboardListener();

        // 隐藏输入法并恢复 FLAG_NOT_FOCUSABLE（如果键盘正在显示）
        if (isKeyboardVisible) {
            stopKeyboardHideDetector();
            cleanupProxyIme();
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && barContainer != null) {
                    imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to hide keyboard on collapse", e);
            }
            isKeyboardVisible = false;
        }
        // 确保 FLAG_NOT_FOCUSABLE 被恢复（键盘显示时可能被移除）
        restoreNotFocusableFlag();

        // 动画期间在视图层拦截所有触摸，防止触摸到达 WebView 视图层级（GestureDetector 等）干扰动画帧
        // 不修改窗口 flags（FLAG_NOT_TOUCHABLE 在某些 Android 版本会导致窗口半透明）
        // 直接在 barContainer 的 OnTouchListener 中消费所有事件，避免事件传播到子视图
        barContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        Log.d(TAG, "[COLLAPSE_DEBUG] 收起动画开始，视图层拦截触摸");
        
        // ===== 关键修复：收起动画开始时立即通知模糊窗口执行渐隐动画 =====
//        sendBlurWindowFadeOutBroadcast();
//        Log.d(TAG, "[COLLAPSE_DEBUG] 已发送模糊窗口渐隐广播");

        // 直接计算 CENTER 重力下的目标位置，避免从动画结束值反算（需在 try 外声明，catch 中也用到）
        final int targetBtmX = barPositionX;
        final int targetBtmY = dpToPx(barPositionY);

        try {
            // 获取当前 bar 位置/大小（全屏状态，CENTER 坐标系）
            final int startX = barLayoutParams.x;
            final int startY = barLayoutParams.y;
            final int startWidth = barLayoutParams.width;
            final int startHeight = barLayoutParams.height;

            Log.d(TAG, "[COLLAPSE_DEBUG] collapseToBottomBar: start=(" + startX + "," + startY + ") " + startWidth + "x" + startHeight
                    + " gravity=" + barLayoutParams.gravity);

            // 获取屏幕尺寸（使用 getRealMetrics 确保包含导航栏区域，与 expand 保持一致）
            DisplayMetrics dm = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(dm);
            final int screenW = dm.widthPixels;
            final int screenH = dm.heightPixels;

            // 提示条的最终尺寸和位置（CENTER 坐标系，无需转换）
            final int targetWidth = currentWidth;
            final int targetHeight = dpToPx(currentHeight);
            // CENTER_VERTICAL|CENTER_HORIZONTAL 下：x=barPositionX(居中偏移), y=dpToPx(barPositionY)(居中偏移)
            final int targetX = barPositionX;
            final int targetY = dpToPx(barPositionY);

            // 查找 contentContainer
            View contentContainer = null;
            if (barContainer.getChildCount() > 0) {
                View rootView = barContainer.getChildAt(0);
                if (rootView instanceof FrameLayout && ((FrameLayout) rootView).getChildCount() > 0) {
                    contentContainer = ((FrameLayout) rootView).getChildAt(0);
                }
            }

            if (contentContainer != null) {
                final View content = contentContainer;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
                final int contentStartWidth = lp.width;
                final int contentStartHeight = lp.height;
                // content 缩小的目标尺寸：匹配 bar 区域
                int effectiveTargetWidth = (currentWidth == ViewGroup.LayoutParams.MATCH_PARENT || currentWidth <= 0) ? screenW : currentWidth;
                final int contentTargetW = Math.max(effectiveTargetWidth, 1);
                final int contentTargetH = Math.max(targetHeight, 1);

                // ===== 收起动画目标变换 =====
                final float endScaleX = (float) contentTargetW / contentStartWidth;
                final float endScaleY = (float) contentTargetH / contentStartHeight;
                // targetY = dpToPx(barPositionY) 是 bar 在 CENTER 坐标下的 Y，contentLp.topMargin = windowPositionY
                final float endTranslationX = targetX - 2 * lp.leftMargin;
                final float endTranslationY = targetY - 2 * lp.topMargin;

                // ===== 圆角插值参数（从窗口实际圆角过渡到提示条圆角） =====
                // 从 contentContainer 的 GradientDrawable 读取实际圆角值，
                // 避免使用配置值导致收起瞬间圆角跳变
//                final int startCornerPx = Math.round(((GradientDrawable) content.getBackground()).getCornerRadius());
//                final int targetCornerPx = dpToPx(currentCornerRadius);
//
//                Log.d(TAG, "[COLLAPSE_DEBUG] collapseToBottomBar: endScale=" + endScaleX + "x" + endScaleY
//                        + " endTrans=(" + endTranslationX + "," + endTranslationY + ")"
//                        + " contentStart=" + contentStartWidth + "x" + contentStartHeight);

                performSmoothCollapseAnimation(
                        content,
                        endScaleX, endScaleY,
                        endTranslationX, endTranslationY,
//                        startCornerPx, targetCornerPx,
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "[COLLAPSE_DEBUG] 收起动画完成回调 at time=" + System.currentTimeMillis());
                                finishCollapseToBottomBar(targetBtmX, targetBtmY);
                            }
                        }
                );
            } else {
                finishCollapseToBottomBar(targetBtmX, targetBtmY);
            }
        } catch (Exception e) {
            Log.e(TAG, "collapse failed", e);
            finishCollapseToBottomBar(targetBtmX, targetBtmY);
        }
    }

    /**
     * Finish collapse - switch window back to bottom bar mode
     */
    private void finishCollapseToBottomBar(int targetBtmX, int targetBtmY) {
        Log.d(TAG, "[COLLAPSE_DEBUG] finishCollapseToBottomBar 开始 at time=" + System.currentTimeMillis());
        Log.d(TAG, "[COLLAPSE_DEBUG] WindowHostActivity.instance=" + (WindowHostActivity.instance != null)
                + " isFinishing=" + (WindowHostActivity.instance != null && WindowHostActivity.instance.isFinishing()));

        if (barContainer == null || windowManager == null) {
            isAiWindowExpanded = false;
            isShortcutWindowExpanded = false;
            return;
        }

        try {
            // ===== 关键修复：收起前暂停 WebView 渲染，避免内部渲染管线挂起 =====
            if (aiWebView != null) {
                aiWebView.onPause();
                Log.d(TAG, "WebView onPause called on collapse");
            }

            // ===== 关键修复：先移除旧视图，再立即缩小窗口，防止 surface 残留白色区域 =====
            barContainer.removeAllViews();

            // 立即将窗口设为底部提示条大小并切换重力，即使后续 addView 前的 stale frame 也只有小区域
            barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
//                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            barLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
            // 横条窗口尺寸恒等于设置的触发区（手势期间绝不缩放）；按压视觉由独立光晕窗口承载
            barLayoutParams.width = computeBarWindowWidth();
            barLayoutParams.height = computeBarWindowHeight();
            // CENTER_VERTICAL|CENTER_HORIZONTAL 下：直接使用 barPositionX/Y
            barLayoutParams.x = targetBtmX;
            barLayoutParams.y = targetBtmY;
            windowManager.updateViewLayout(barContainer, barLayoutParams);
            syncGlowWindow();

            // 清除圆角背景和裁剪
            barContainer.setBackground(null);
            barContainer.setClipToOutline(false);

            // 现在创建并添加底部提示条视图（覆盖层/光晕常驻独立光晕窗口，无需重建）
            bottomBar = createBottomBarView();
            barContainer.addView(bottomBar);

            // 恢复提示条透明度（长按淡出展开时可能被置为0）
            barContainer.setAlpha(1f);

            isAiWindowExpanded = false;
            isShortcutWindowExpanded = false;
            isKeyboardVisible = false;
            isKeyboardAnimating = false;
            // 隐藏背景遮罩：渐隐动画（在窗口收起完成后执行，不影响主动画）
            if (overlayView != null) {
                final View ov = overlayView;
                final float startAlpha = overlayAlpha / 100.0f;
                final long startTime = System.currentTimeMillis();
                ov.setAlpha(startAlpha);
                isOverlayAnimating = true;
                Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                    @Override
                    public void doFrame(long frameTimeNanos) {
                        if (!isOverlayAnimating) return;
                        long elapsed = System.currentTimeMillis() - startTime;
                        float progress = Math.min((float) elapsed / overlayAnimDuration, 1.0f);
                        float eased = easeInCubic(progress);
                        ov.setAlpha(startAlpha * (1 - eased));
                        if (progress < 1.0f) {
                            Choreographer.getInstance().postFrameCallback(this);
                        } else {
                            ov.setAlpha(0f);
                        }
                    }
                });
            }

            // 恢复正常触摸处理（收起动画开始时在 barContainer 设置了拦截触摸的 OnTouchListener）
            barContainer.setOnTouchListener(null);

            // 移除背景遮罩（释放窗口资源，避免干扰其他应用弹窗）
            removeOverlayWindow();

            // Finish proxy Activity
            finishWindowHostActivity();
            
            // 注意：blurWindow 已在收起动画开始时通过 sendBlurWindowFadeOutBroadcast 关闭
            // 此处不再重复调用 closeBlurWindow()
            
            Log.d(TAG, "Collapse to bottom bar complete");
        } catch (Exception e) {
            Log.e(TAG, "finishCollapse failed", e);
            isAiWindowExpanded = false;
            finishWindowHostActivity();
            // 收起中途异常时尺寸可能尚未恢复、触摸拦截可能仍在，必须兜底恢复
            restoreBarWindowAfterFailure("finishCollapseToBottomBar 失败");
        }
    }

    /**
     * Start transparent proxy Activity to receive system back events.
     * System back (both nav bar button and gesture swipe) is only delivered to Activities,
     * not to TYPE_APPLICATION_OVERLAY windows, so this Activity acts as a bridge.
     */
    private void startWindowHostActivity() {
        Log.d(TAG, "[COLLAPSE_DEBUG] startWindowHostActivity at time=" + System.currentTimeMillis()
                + " isAiWindowExpanded=" + isAiWindowExpanded);

        WindowHostActivity.onBackCallback = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "[COLLAPSE_DEBUG] onBackCallback 执行 at time=" + System.currentTimeMillis()
                        + " isKeyboardVisible=" + isKeyboardVisible
                        + " canGoBack=" + (aiWebView != null && aiWebView.canGoBack())
                        + " isAiWindowExpanded=" + isAiWindowExpanded);

                if (isKeyboardVisible) {
                    // 键盘可见时：先隐藏键盘并恢复窗口位置，不关闭窗口
                    Log.d(TAG, "Back pressed: hiding keyboard and restoring window position");
                    stopKeyboardHideDetector();
                    cleanupProxyIme();
                    try {
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null && barContainer != null) {
                            imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to hide keyboard", e);
                    }
                    isKeyboardVisible = false;
                    animateForKeyboard(false, 0);
                    // 恢复 FLAG_NOT_FOCUSABLE（返回手势穿透到 WindowHostActivity）
                    restoreNotFocusableFlag();
                    // 安全兜底：确保窗口位置已恢复
                    resetKeyboardOffset();
                    // 延迟兜底：finishActivity 可能导致布局重排，多次确保位置正确
                    scheduleKeyboardPositionFallback();
                    // 不finish WindowHostActivity，用户可再次按返回关闭窗口
                } else if (backPrioritizeWebview && isAiWindowExpanded && aiWebView != null && aiWebView.canGoBack()) {
                    // 开启"优先返回网页上一级"时，先尝试网页内返回
                    Log.d(TAG, "Back pressed: WebView can go back, backPrioritizeWebview=true");
                    aiWebView.goBack();
                    // 重新启动代理 Activity 以拦截下一次返回手势
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            startWindowHostActivity();
                        }
                    });
                } else {
                    Log.d(TAG, "Back pressed: collapsing window (backPrioritizeWebview=" + backPrioritizeWebview + ")");
                    collapseToBottomBar();
                }
            }
        };
        try {
            Intent intent = new Intent(this, WindowHostActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            Log.d(TAG, "WindowHostActivity started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WindowHostActivity", e);
            WindowHostActivity.onBackCallback = null;
        }
    }

    /**
     * Finish the proxy Activity (called when we collapse the window ourselves).
     */
    private void finishWindowHostActivity() {
        WindowHostActivity.onBackCallback = null;
        WindowHostActivity activity = WindowHostActivity.instance;
        Log.d(TAG, "[COLLAPSE_DEBUG] finishWindowHostActivity at time=" + System.currentTimeMillis()
                + " activity=" + (activity != null)
                + " isFinishing=" + (activity != null && activity.isFinishing()));
        if (activity != null) {
            try {
                activity.finishWithoutTransition();
                Log.d(TAG, "WindowHostActivity finished");
            } catch (Exception e) {
                Log.e(TAG, "Failed to finish WindowHostActivity", e);
            }
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
     * 发送模糊窗口渐隐消失广播
     */
    private void sendBlurWindowFadeOutBroadcast() {
        try {
            Intent intent = new Intent("BLUR_WINDOW_FADE_OUT");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
            Log.d(TAG, "已发送模糊窗口渐隐消失广播");
        } catch (Exception e) {
            Log.e(TAG, "发送模糊窗口渐隐消失广播失败", e);
        }
    }
    
    /**
     * 关闭模糊窗口 BlurWindowActivity
     */
    private void closeBlurWindow() {
        try {
            // 发送渐变消失广播，让 BlurWindowActivity 执行渐隐动画后自动关闭
            Intent fadeOutIntent = new Intent("BLUR_WINDOW_FADE_OUT");
            String packageName = getPackageName();
            fadeOutIntent.setPackage(packageName);
            sendBroadcast(fadeOutIntent);
            Log.d(TAG, "已发送模糊窗口关闭广播: package=" + packageName);
        } catch (Exception e) {
            Log.e(TAG, "关闭模糊窗口失败", e);
        }
    }

    // ==================== FLAG_NOT_FOCUSABLE 动态管理 ====================

    /**
     * 移除 FLAG_NOT_FOCUSABLE，让 overlay 窗口可接收焦点（IME 需要）
     */
    private void removeNotFocusableFlag() {
        if (barLayoutParams == null || windowManager == null || barContainer == null) return;
        barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        barLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
        Log.d(TAG, "removeNotFocusableFlag: setting flags=0x" + Integer.toHexString(barLayoutParams.flags));
        try {
            windowManager.updateViewLayout(barContainer, barLayoutParams);
            Log.d(TAG, "FLAG_NOT_FOCUSABLE removed, flags now=0x" + Integer.toHexString(barLayoutParams.flags));
        } catch (Exception e) {
            Log.e(TAG, "removeNotFocusableFlag failed", e);
        }
    }

    /**
     * 恢复 FLAG_NOT_FOCUSABLE，让触摸事件穿透到 WindowHostActivity（返回手势需要）
     */
    private void restoreNotFocusableFlag() {
        if (barLayoutParams == null || windowManager == null || barContainer == null) return;
        barLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        barLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
        try {
            windowManager.updateViewLayout(barContainer, barLayoutParams);
            Log.d(TAG, "FLAG_NOT_FOCUSABLE restored");
        } catch (Exception e) {
            Log.e(TAG, "restoreNotFocusableFlag failed", e);
        }
        // 安全兜底：确保窗口位置恢复（键盘避让动画可能被干扰）
        resetKeyboardOffset();
    }

    /**
     * 注入 JavaScript 监听输入框聚焦/失焦事件
     */
    private void injectInputFocusScript(WebView webView) {
        String js = "javascript:(function() {"
                + "  function notifyFocus(e) {"
                + "    var tag = e.target.tagName;"
                + "    if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable) {"
                + "      AndroidBridge.onInputFocus();"
                + "    }"
                + "  }"
                + "  function notifyBlur(e) {"
                + "    var tag = e.target.tagName;"
                + "    if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable) {"
                + "      AndroidBridge.onInputBlur();"
                + "    }"
                + "  }"
                + "  function notifyClick(e) {"
                + "    var tag = e.target.tagName;"
                + "    if (tag === 'INPUT' || tag === 'TEXTAREA' || e.target.isContentEditable) {"
                + "      AndroidBridge.onInputFocus();"
                + "      var selStart = (tag==='INPUT'||tag==='TEXTAREA') ? e.target.selectionStart : -1;"
                + "      var selEnd = (tag==='INPUT'||tag==='TEXTAREA') ? e.target.selectionEnd : -1;"
                + "      if(selStart >= 0) AndroidBridge.onSelectionChange(selStart, selEnd);"
                + "    }"
                + "  }"
                + "  var lastSelStart = -1, lastSelEnd = -1;"
                + "  document.addEventListener('selectionchange', function(e) {"
                + "    var el = document.activeElement;"
                + "    if(el && (el.tagName==='INPUT'||el.tagName==='TEXTAREA')){"
                + "      var ss = el.selectionStart, se = el.selectionEnd;"
                + "      if(ss !== lastSelStart || se !== lastSelEnd){"
                + "        lastSelStart = ss;"
                + "        lastSelEnd = se;"
                + "        AndroidBridge.onSelectionChange(ss, se);"
                + "      }"
                + "    }"
                + "  });"
                + "  document.addEventListener('focus', notifyFocus, true);"
                + "  document.addEventListener('blur', notifyBlur, true);"
                + "  document.addEventListener('click', notifyClick, true);"
                + "})();";
        webView.loadUrl(js);
        Log.d(TAG, "Input focus JS injected");
    }

    // ==================== 输入法避让功能 ====================

    /**
     * 显示键盘，带重试机制
     * WebView 的 InputConnection 可能需要额外时间建立，
     * 因此先尝试 showSoftInput，失败后用 toggleSoftInput 重试
     */
    private void showKeyboardWithRetry() {
        try {
            final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) return;

            // 第一次尝试 showSoftInput
            boolean result = imm.showSoftInput(aiWebView, InputMethodManager.SHOW_FORCED);
            Log.d(TAG, "showSoftInput attempt 1: result=" + result
                    + ", hasFocus=" + (aiWebView != null ? aiWebView.hasFocus() : "null")
                    + ", hasWindowFocus=" + (aiWebView != null ? aiWebView.hasWindowFocus() : "null"));

            if (!result) {
                // showSoftInput 失败，延迟 200ms 重试
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        boolean result2 = imm.showSoftInput(aiWebView, InputMethodManager.SHOW_FORCED);
                        Log.d(TAG, "showSoftInput attempt 2: result=" + result2);
                        if (!result2) {
                            // 两次 showSoftInput 都失败，使用 toggleSoftInput 强制弹出
                            imm.toggleSoftInput(
                                    InputMethodManager.SHOW_FORCED,
                                    InputMethodManager.HIDE_IMPLICIT_ONLY);
                            Log.d(TAG, "toggleSoftInput(SHOW_FORCED) called as last resort");
                        }
                    }
                }, 200);
            }
        } catch (Exception e) {
            Log.e(TAG, "showKeyboardWithRetry failed", e);
            // 回退到 toggleSoftInput
            try {
                InputMethodManager imm2 = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm2 != null) {
                    imm2.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    Log.d(TAG, "toggleSoftInput(SHOW_FORCED) called after exception");
                }
            } catch (Exception e2) {
                Log.e(TAG, "toggleSoftInput fallback also failed", e2);
            }
        }
    }

    /**
     * 设置 IME 代理 EditText
     * 使用从 WebView 读取的现有文本来初始化代理，确保增量对比正确
     */
    private void setupProxyIme(String initialText) {
        Log.d(TAG, "[IME_DEBUG] setupProxyIme initialText=" + (initialText != null ? initialText.substring(0, Math.min(initialText.length(), 50)) + " len=" + initialText.length() : "null"));
        isUpdatingProxy = true;
        if (proxyTextWatcher != null) {
            proxyIme.removeTextChangedListener(proxyTextWatcher);
            proxyTextWatcher = null;
        }
        proxyIme.setText(initialText);
        if (initialText != null && initialText.length() > 0) {
            proxyIme.setSelection(initialText.length());
        }
        proxyIme.setVisibility(View.VISIBLE);
        proxyIme.requestFocus();
        isUpdatingProxy = false;
        // 5. 设置 TextWatcher 捕获输入法输入
        proxyTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingProxy) return;
                final String text = s.toString();
                final int cursorPos = proxyIme.getSelectionStart();
                Log.d(TAG, "[IME_DEBUG] afterTextChanged text=" + text.substring(0, Math.min(text.length(), 50)) + " len=" + text.length() + " cursor=" + cursorPos);
                // 去抖：取消之前的 pending，延迟发送新的
                if (pendingTextForward != null) {
                    new Handler(Looper.getMainLooper()).removeCallbacks(pendingTextForward);
                }
                pendingTextForward = new Runnable() {
                    @Override
                    public void run() {
                        forwardTextToWebView(text, cursorPos);
                    }
                };
                new Handler(Looper.getMainLooper()).postDelayed(pendingTextForward, 80);
            }
        };
        proxyIme.addTextChangedListener(proxyTextWatcher);
        // 6. 在代理 EditText 上弹出输入法
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                // showSoftInput 需要目标 view 有焦点（proxyIme 已有）
                boolean result = imm.showSoftInput(proxyIme, InputMethodManager.SHOW_FORCED);
                Log.d(TAG, "showSoftInput(proxyIme) result=" + result);
                if (!result) {
                    // 回退：toggleSoftInput 不要求焦点
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    Log.d(TAG, "toggleSoftInput fallback for proxyIme");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "showSoftInput(proxyIme) failed", e);
        }
        if (!isKeyboardVisible) {
            isKeyboardVisible = true;
            animateForKeyboard(true, 0);
        }
        startKeyboardHideDetector();
        FloatWindowService.this.isImeProcessing = false;
        // 7. 确保 WebView 输入框保持 DOM 焦点（光标显示）
        //    onFocusChanged 拦截阻止了 Chromium 自动 blur，
        //    这里仅做保险确认
        if (aiWebView != null) {
            aiWebView.evaluateJavascript(
                    "(function(){" +
                            "  var el = window._imeTarget;" +
                            "  if(el && document.activeElement !== el){" +
                            "    el.focus();" +
                            "  }" +
                            "})()", null);
        }
    }

    /**
     * 将代理 EditText 的文本转发到 WebView 的输入框
     * 使用 window._imeTarget（在聚焦代理之前保存的元素引用）
     * 通过原生 value setter 确保 React/Vue 等框架能检测到变化
     */
    private void forwardTextToWebView(String text, int cursorPos) {
        if (aiWebView == null) return;
        Log.d(TAG, "[IME_DEBUG] forwardTextToWebView text=" + text.substring(0, Math.min(text.length(), 50)) + " len=" + text.length() + " cursor=" + cursorPos);
        // 全量替换：使用原生 value setter 确保 React/Vue 等框架能检测到变化
        // 由于代理 EditText 初始化时已包含 WebView 现有文本，首次输入不会覆盖
        final String escaped = escapeJsString(text);
        aiWebView.evaluateJavascript(
                "(function(){" +
                        "  var el = window._imeTarget;" +
                        "  if(!el || !el.parentNode) return;" +
                        "  try{" +
                        "    var tag = el.tagName;" +
                        "    if(tag==='INPUT'||tag==='TEXTAREA'){" +
                        "      var proto = (tag==='TEXTAREA' ? HTMLTextAreaElement : HTMLInputElement).prototype;" +
                        "      var nativeSetter = Object.getOwnPropertyDescriptor(proto,'value');" +
                        "      if(nativeSetter && nativeSetter.set){" +
                        "        nativeSetter.set.call(el,'" + escaped + "');" +
                        "      } else {" +
                        "        el.value='" + escaped + "';" +
                        "      }" +
                        "      el.selectionStart = el.selectionEnd = " + cursorPos + ";" +
                        "      el.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "      el.dispatchEvent(new Event('change',{bubbles:true}));" +
                        "    } else if(el.isContentEditable){" +
                        "      el.textContent='" + escaped + "';" +
                        "    }" +
                        "  } catch(e){}" +
                        "})()", null);
    }

    /**
     * 转义文本中的特殊字符，使其可安全嵌入 JS 单引号字符串
     */
    private String escapeJsString(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\u2028':
                    sb.append("\\u2028");
                    break;
                case '\u2029':
                    sb.append("\\u2029");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // ==================== 自定义上下文菜单（长按复制/粘贴） ====================

    /**
     * 显示自定义长按上下文菜单（复制/全选/粘贴）
     */
    private void showContextMenu(float touchX, float touchY, String selectedText, boolean hasSelection, boolean isInput, String imageUrl) {
        Log.d(TAG, "showContextMenu called: hasSelection=" + hasSelection + " selectedText='" + selectedText + "' isInput=" + isInput + " imageUrl=" + imageUrl + " touch=(" + touchX + "," + touchY + ")");
        dismissContextMenu();
        if (aiWebView == null || barContainer == null || windowManager == null) return;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        layout.setElevation(dpToPx(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(8));
        bg.setColor(Color.parseColor("#FAFAFA"));
        bg.setStroke(1, Color.parseColor("#DDDDDD"));
        layout.setBackground(bg);

        // 复制按钮（仅在选中文本时显示）
        if (hasSelection) {
            TextView copyBtn = new TextView(this);
            copyBtn.setText("复制");
            copyBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            copyBtn.setTextSize(14);
            copyBtn.setTextColor(Color.parseColor("#333333"));
            copyBtn.setGravity(Gravity.CENTER);
            copyBtn.setOnClickListener(v -> {
                copyToClipboard(selectedText);
                dismissContextMenu();
            });
            layout.addView(copyBtn);

            // 剪切按钮（仅在输入框内的选中文本显示）
            if (isInput) {
                TextView cutBtn = new TextView(this);
                cutBtn.setText("剪切");
                cutBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
                cutBtn.setTextSize(14);
                cutBtn.setTextColor(Color.parseColor("#333333"));
                cutBtn.setGravity(Gravity.CENTER);
                cutBtn.setOnClickListener(v -> {
                    cutFromInput(selectedText);
                    dismissContextMenu();
                });
                layout.addView(cutBtn);
            }
        }

        // 全选按钮
        TextView selectAllBtn = new TextView(this);
        selectAllBtn.setText("全选");
        selectAllBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        selectAllBtn.setTextSize(14);
        selectAllBtn.setTextColor(Color.parseColor("#333333"));
        selectAllBtn.setGravity(Gravity.CENTER);
        selectAllBtn.setOnClickListener(v -> {
            selectAllWebView();
            dismissContextMenu();
        });
        layout.addView(selectAllBtn);

        // 粘贴按钮（仅在输入框显示）
        if (isInput) {
            TextView pasteBtn = new TextView(this);
            pasteBtn.setText("粘贴");
            pasteBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            pasteBtn.setTextSize(14);
            pasteBtn.setTextColor(Color.parseColor("#333333"));
            pasteBtn.setGravity(Gravity.CENTER);
            pasteBtn.setOnClickListener(v -> {
                pasteFromClipboard();
                dismissContextMenu();
            });
            layout.addView(pasteBtn);
        }

        // 保存图片按钮（当长按检测到图片时显示）
        if (imageUrl != null && imageUrl.length() > 0) {
            TextView saveImgBtn = new TextView(this);
            saveImgBtn.setText("保存图片");
            saveImgBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            saveImgBtn.setTextSize(14);
            saveImgBtn.setTextColor(Color.parseColor("#333333"));
            saveImgBtn.setGravity(Gravity.CENTER);
            saveImgBtn.setOnClickListener(v -> {
                saveImageToGallery(imageUrl);
                dismissContextMenu();
            });
            layout.addView(saveImgBtn);
        }

        // 测量尺寸
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        layout.measure(widthSpec, heightSpec);
        int popupWidth = Math.max(layout.getMeasuredWidth(), 1);
        int popupHeight = Math.max(layout.getMeasuredHeight(), 1);

        // 将 WebView 内的触摸坐标转换为屏幕坐标
        int[] webViewLoc = new int[2];
        aiWebView.getLocationOnScreen(webViewLoc);
        int popupX = webViewLoc[0] + (int) touchX - popupWidth / 2;
        int popupY = webViewLoc[1] + (int) touchY - popupHeight - dpToPx(10);

        // 边界约束：确保菜单不超出屏幕
        DisplayMetrics dm = getResources().getDisplayMetrics();
        popupX = Math.max(dpToPx(4), Math.min(popupX, dm.widthPixels - popupWidth - dpToPx(4)));
        popupY = Math.max(dpToPx(4), Math.min(popupY, dm.heightPixels - popupHeight - dpToPx(4)));

        // 使用 WindowManager 创建独立覆盖层（TYPE_APPLICATION_OVERLAY 中 PopupWindow 不可靠）
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                popupWidth,
                popupHeight,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = popupX;
        params.y = popupY;
        params.token = barContainer.getWindowToken();

        // 监听外部点击以关闭菜单
        layout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                dismissContextMenu();
                return true;
            }
            return false;
        });

        try {
            windowManager.addView(layout, params);
            contextMenuPopupView = layout;
            Log.d(TAG, "Context menu shown at (" + popupX + "," + popupY + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show context menu", e);
        }
    }

    /**
     * 关闭上下文菜单
     */
    private void dismissContextMenu() {
        if (contextMenuPopupView != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(contextMenuPopupView);
                Log.d(TAG, "Context menu dismissed");
            } catch (Exception e) {
                Log.w(TAG, "Failed to dismiss context menu", e);
            }
            contextMenuPopupView = null;
        }
    }

    /**
     * 复制文本到系统剪贴板
     */
    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("label", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Copied to clipboard: " + text);
        } catch (Exception e) {
            Log.e(TAG, "Copy failed", e);
        }
    }

    /**
     * 在 WebView 中全选文本
     */
    private void selectAllWebView() {
        if (aiWebView == null) return;
        aiWebView.evaluateJavascript(
                "(function(){" +
                        "  var el = document.activeElement;" +
                        "  if(el && (el.tagName==='INPUT'||el.tagName==='TEXTAREA')){" +
                        "    el.select();" +
                        "  }else{" +
                        "    var sel = window.getSelection();" +
                        "    if(sel){" +
                        "      var range = document.createRange();" +
                        "      range.selectNodeContents(document.body);" +
                        "      sel.removeAllRanges();" +
                        "      sel.addRange(range);" +
                        "    }" +
                        "  }" +
                        "})()", null);
        Toast.makeText(this, "已全选", Toast.LENGTH_SHORT).show();
    }

    /**
     * 从剪贴板粘贴到 WebView 输入框
     */
    private void pasteFromClipboard() {
        if (aiWebView == null) return;
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (!clipboard.hasPrimaryClip()) return;
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            CharSequence pasteText = item.getText();
            if (pasteText == null || pasteText.length() == 0) return;

            final String text = pasteText.toString();
            // 将文本存储到字段，通过 @JavascriptInterface 传递给 JS
            // 避免将文本直接嵌入 JS 代码字符串，防止 evaluateJavascript 因超长 JS 代码失败
            Log.d(TAG, "[PASTE_DEBUG] clipboard text length=" + text.length());
            pendingPasteText = text;

            aiWebView.evaluateJavascript(
                    "(function(){" +
                            "  try{" +
                            "    var el = document.activeElement;" +
                            "    if(!el || (el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA')){" +
                            "      el = window._imeTarget;" +
                            "    }" +
                            "    if(!el || (el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA'&&!el.isContentEditable)){" +
                            "      console.log('[PASTE_JS] no target element'); return;" +
                            "    }" +
                            "    var text = AndroidBridge.getPendingPasteText();" +
                            "    console.log('[PASTE_JS] text len=' + (text?text.length:-1));" +
                            "    if(!text || text.length === 0){ console.log('[PASTE_JS] empty text'); return; }" +
                            "    if(el.tagName==='INPUT'||el.tagName==='TEXTAREA'){" +
                            "      var s = el.selectionStart, e = el.selectionEnd;" +
                            "      var newVal = el.value.substring(0,s) + text + el.value.substring(e);" +
                            "      var tag = el.tagName;" +
                            "      var proto = (tag==='TEXTAREA' ? HTMLTextAreaElement : HTMLInputElement).prototype;" +
                            "      var nativeSetter = Object.getOwnPropertyDescriptor(proto,'value');" +
                            "      if(nativeSetter && nativeSetter.set){" +
                            "        nativeSetter.set.call(el, newVal);" +
                            "      } else {" +
                            "        el.value = newVal;" +
                            "      }" +
                            "      el.selectionStart = el.selectionEnd = s + text.length;" +
                            "      el.dispatchEvent(new Event('input', {bubbles:true}));" +
                            "      el.dispatchEvent(new Event('change', {bubbles:true}));" +
                            "      console.log('[PASTE_JS] paste to input ok');" +
                            "    }else if(el.isContentEditable){" +
                            "      var sel = window.getSelection();" +
                            "      if(sel && sel.rangeCount > 0){" +
                            "        var range = sel.getRangeAt(0);" +
                            "        range.deleteContents();" +
                            "        range.insertNode(document.createTextNode(text));" +
                            "        range.collapse(false);" +
                            "        sel.removeAllRanges();" +
                            "        sel.addRange(range);" +
                            "      }" +
                            "      el.dispatchEvent(new Event('input', {bubbles:true}));" +
                            "      el.dispatchEvent(new Event('change', {bubbles:true}));" +
                            "      console.log('[PASTE_JS] paste to contenteditable ok');" +
                            "    }" +
                            "    AndroidBridge.syncProxyIme(el.value||el.textContent||'');" +
                            "  }catch(e){ console.log('[PASTE_JS] error: ' + e.message); }" +
                            "})()", null);
            Log.d(TAG, "Pasted text into WebView via bridge");
        } catch (Exception e) {
            Log.e(TAG, "Paste failed", e);
        }
    }

    /**
     * 剪切输入框中的选中文本（复制到剪贴板后清除选中部分）
     */
    private void cutFromInput(String text) {
        if (text == null || text.isEmpty() || aiWebView == null) return;
        // 先复制到剪贴板
        copyToClipboard(text);
        // 再用 JS 清除选中部分
        String escaped = escapeJsString(text);
        aiWebView.evaluateJavascript(
                "(function(){" +
                        "  var el = document.activeElement;" +
                        "  if(!el || (el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA')) return;" +
                        "  var s = el.selectionStart, e = el.selectionEnd;" +
                        "  if(s === e) return;" +
                        "  el.value = el.value.substring(0,s) + el.value.substring(e);" +
                        "  el.selectionStart = el.selectionEnd = s;" +
                        "  el.dispatchEvent(new Event('input', {bubbles:true}));" +
                        "  el.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "  AndroidBridge.syncProxyIme(el.value);" +
                        "})()", null);
        Log.d(TAG, "Cut text from input: " + text);
    }

    /**
     * 移除 TextWatcher、清除文本、隐藏代理
     */
    private void cleanupProxyIme() {
        // 重置 IME 处理中标志，防止键盘隐藏后再次点击输入框时被 stale flag 阻塞
        isImeProcessing = false;
        dismissContextMenu();
        if (proxyIme == null) return;
        if (pendingTextForward != null) {
            new Handler(Looper.getMainLooper()).removeCallbacks(pendingTextForward);
            pendingTextForward = null;
        }
        if (proxyTextWatcher != null) {
            proxyIme.removeTextChangedListener(proxyTextWatcher);
        }
        isUpdatingProxy = true;
        proxyIme.setText("");
        isUpdatingProxy = false;
        proxyIme.setVisibility(View.INVISIBLE);
        // 清除代理 EditText 的焦点状态，防止残留焦点干扰下次触摸事件
        // 如果不 clearFocus，第一次点击窗口外透明区域时焦点切换会消耗触摸事件
        proxyIme.clearFocus();
        // 清除 WebView 中保存的元素引用，并 blur 当前聚焦的输入框
        // 如果不 blur，用户再次点击同一输入框时不会触发 focus 事件，IME 无法弹出
        if (aiWebView != null) {
            aiWebView.evaluateJavascript(
                    "(function(){" +
                            "  var el = window._imeTarget;" +
                            "  if(el && document.activeElement === el){" +
                            "    el.blur();" +
                            "  }" +
                            "  window._imeTarget = null;" +
                            "})()", null);
        }
    }

    /**
     * 设置输入法可见性检测监听器
     * 使用 OnGlobalLayoutListener 检测屏幕可见区域变化来推断输入法状态
     */
    private void setupKeyboardListener(final View rootView) {
        if (rootView == null) return;

        keyboardLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int lastKeyboardHeight = 0;

            @Override
            public void onGlobalLayout() {
                android.graphics.Rect r = new android.graphics.Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keyboardHeight = screenHeight - r.bottom;

                // 去抖：变化量小于 20px 忽略
                if (Math.abs(keyboardHeight - lastKeyboardHeight) < 20) return;
                lastKeyboardHeight = keyboardHeight;

                boolean shown = keyboardHeight > screenHeight * 0.12f;

                if (shown && !isKeyboardVisible) {
                    isKeyboardVisible = true;
                    animateForKeyboard(true, keyboardHeight);
                } else if (!shown && isKeyboardVisible) {
                    Log.d(TAG, "OnGlobalLayout: keyboard hidden detected");
                    cleanupProxyIme();
                    try {
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null && barContainer != null) {
                            imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to hide keyboard on global layout", e);
                    }
                    isKeyboardVisible = false;
                    animateForKeyboard(false, 0);
                    restoreNotFocusableFlag();
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
        if (keyboardLayoutListener != null && barContainer != null && barContainer.getChildCount() > 0) {
            try {
                View rootView = barContainer.getChildAt(0);
                rootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
                Log.d(TAG, "Keyboard listener removed");
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove keyboard listener", e);
            }
        }
        keyboardLayoutListener = null;
    }

    /**
     * 输入法出现/消失时的平移动画
     *
     * @param show           true=输入法出现（窗口上移），false=输入法消失（窗口恢复）
     * @param detectedHeight 检测到的输入法高度（像素）
     */
    private void animateForKeyboard(final boolean show, final int detectedHeight) {
        if (barContainer == null || barContainer.getChildCount() == 0) return;

        final View rootView = barContainer.getChildAt(0);
        if (!(rootView instanceof FrameLayout) || ((FrameLayout) rootView).getChildCount() < 1)
            return;

        final View content = ((FrameLayout) rootView).getChildAt(0);

        final float startTranslationY = content.getTranslationY();
        int offset = keyboardOffset;
        if (detectedHeight > offset) {
            offset = detectedHeight;
        }
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
                    content.setTranslationY(currentTranslationY);

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
     * 在 restoreNotFocusableFlag 之后调用，确保键盘避让动画即使被干扰也能恢复
     */
    private void resetKeyboardOffset() {
        // 正在执行键盘动画时不要打断，让 animateForKeyboard 平滑完成
        if (isKeyboardAnimating) {
            return;
        }
        if (barContainer == null || barContainer.getChildCount() == 0) return;
        try {
            View rootView = barContainer.getChildAt(0);
            if (rootView instanceof FrameLayout && ((FrameLayout) rootView).getChildCount() > 0) {
                View content = ((FrameLayout) rootView).getChildAt(0);
                if (content.getTranslationY() != 0f) {
                    content.setTranslationY(0f);
                    Log.d(TAG, "resetKeyboardOffset: translationY -> 0");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "resetKeyboardOffset failed", e);
        }
        isKeyboardAnimating = false;
    }

    /**
     * 启动输入法隐藏检测器
     * 使用反射调用 getInputMethodWindowVisibleHeight() (API 25+) 直接检测 IME 窗口高度
     * 回退到 isActive() 检测输入法连接状态
     * 当用户通过输入法自身的返回键或系统返回手势关闭输入法时触发恢复窗口位置
     */
    private void startKeyboardHideDetector() {
        if (keyboardHideDetector != null) {
            return;
        }
        keyboardHideDetector = new Runnable() {
            @Override
            public void run() {
                if (!isAiWindowExpanded || !isKeyboardVisible || proxyIme == null) {
                    keyboardHideDetector = null;
                    return;
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm == null) {
                    new Handler(Looper.getMainLooper()).postDelayed(this, 200);
                    return;
                }

                boolean imeHidden = isImeHidden(imm);

                if (imeHidden) {
                    Log.d(TAG, "Keyboard hide detector: IME window hidden");
                    cleanupProxyIme();
                    try {
                        if (barContainer != null) {
                            imm.hideSoftInputFromWindow(barContainer.getWindowToken(), 0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to hide keyboard on detector", e);
                    }
                    isKeyboardVisible = false;
                    animateForKeyboard(false, 0);
                    restoreNotFocusableFlag();
                    // 延迟兜底：确保窗口位置最终被恢复
                    scheduleKeyboardPositionFallback();
                    keyboardHideDetector = null;
                    return;
                }
                new Handler(Looper.getMainLooper()).postDelayed(this, 200);
            }
        };
        new Handler(Looper.getMainLooper()).postDelayed(keyboardHideDetector, 200);
    }

    /**
     * 检测输入法窗口是否隐藏
     * API 25+: 反射调用 getInputMethodWindowVisibleHeight() 获取 IME 窗口可见高度
     * API 24 及以下: 通过 isActive() 判断连接是否断开
     */
    private boolean isImeHidden(InputMethodManager imm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                java.lang.reflect.Method method = InputMethodManager.class.getMethod("getInputMethodWindowVisibleHeight");
                int height = (int) method.invoke(imm);
                if (height > 0) {
                    Log.d(TAG, "IME height check: " + height + "px, still visible");
                    return false;
                }
                return true; // height == 0, IME fully hidden
            } catch (Exception e) {
                Log.e(TAG, "IME height check failed, falling back to isActive", e);
            }
        }
        return !imm.isActive(proxyIme);
    }

    /**
     * 停止输入法隐藏检测器
     */
    private void stopKeyboardHideDetector() {
        keyboardHideDetector = null; // Runnable 下次执行时自动退出
    }

    /**
     * 延迟兜底：在键盘隐藏后多次确保窗口位置恢复
     * 解决 finishActivity、布局重排等异步操作可能导致的复位干扰
     */
    private void scheduleKeyboardPositionFallback() {
        final Handler handler = new Handler(Looper.getMainLooper());
        // 多次尝试，覆盖不同时点的布局变化
        for (int delay : new int[]{50, 150, 400}) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isAiWindowExpanded) return;
                    resetKeyboardOffset();
                }
            }, delay);
        }
    }

    // ==================== 动画辅助方法 ====================

    /**
     * Animate contentContainer shrinking from current size to 0, then run onComplete
     */
    private void performContentCollapseAnimation(final View content, final Runnable onComplete) {
        final int startWidth = content.getLayoutParams().width;
        final int startHeight = content.getLayoutParams().height;
        final long startTime = System.currentTimeMillis();

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / animationDuration, 1.0f);
                float easedProgress = easeInCubic(progress);

                int currentW = (int) (startWidth * (1 - easedProgress));
                int currentH = (int) (startHeight * (1 - easedProgress));

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
                lp.width = Math.max(currentW, 1);
                lp.height = Math.max(currentH, 1);
                content.setLayoutParams(lp);

                if (progress < 1.0f) {
                    handler.postDelayed(this, FRAME_INTERVAL);
                } else {
                    if (onComplete != null) onComplete.run();
                }
            }
        });
    }

    /**
     * 收起动画：通过 contentContainer 的 scaleX/scaleY/translationY 变换实现
     * barContainer 保持全屏，避免 updateViewLayout 逐帧重排导致的掉帧
     */
    private void performSmoothCollapseAnimation(
            final View contentContainer,
            final float endScaleX, final float endScaleY,
            final float endTranslationX, final float endTranslationY,
            final Runnable onComplete) {
        if (barContainer == null || barLayoutParams == null) {
            if (onComplete != null) onComplete.run();
            return;
        }


        // 动画期间启用硬件层
        contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

//        final GradientDrawable contentBg = (GradientDrawable) contentContainer.getBackground();
//        final android.graphics.drawable.Drawable foreground = contentContainer.getForeground();

        // 立即记录开始时间，避免首帧被跳过
        final long adjustedStartTime = System.nanoTime();

        // 完成状态：无论正常完成还是中途异常，onComplete 只执行一次，
        // 避免异常导致回调丢失、全屏窗口与触摸拦截残留造成整屏冻结
        final boolean[] completed = {false};

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            private int frameCount = 0;
            private long prevFrameTime = 0;

            private void finishNow() {
                if (completed[0]) return;
                completed[0] = true;
                try {
                    contentContainer.setScaleX(1f);
                    contentContainer.setScaleY(1f);
                    contentContainer.setTranslationX(0f);
                    contentContainer.setTranslationY(0f);
                    contentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (onComplete != null) {
                    try {
                        onComplete.run();
                    } catch (Exception e) {
                        Log.e(TAG, "collapse onComplete 执行失败", e);
                    }
                }
            }

            @Override
            public void doFrame(long frameTimeNanos) {
                if (completed[0]) return;
                try {
                    if (prevFrameTime == 0) {
                        prevFrameTime = frameTimeNanos;
                    }

                    frameCount++;
                    long intervalNs = frameTimeNanos - prevFrameTime;
                    prevFrameTime = frameTimeNanos;
                    // 纯时间进度
                    float timeProgress = (float) (frameTimeNanos - adjustedStartTime) / 1000000f / animationDuration;
                    float progress = Math.min(timeProgress, 1.0f);
                    float easedProgress = animInterpolator.getInterpolation(progress);

                    // WebView 透明度：前一半从 1 渐变为 0，后一半保持 0
                    if (aiWebView != null) {
                        if (progress < 0.5f) {
                            float alphaProgress = progress * 2f;
                            aiWebView.setAlpha(1f - easeOutCubic(alphaProgress));
                        } else {
                            aiWebView.setAlpha(0f);
                        }
                    }

                    // 插值 scaleX/scaleY/translationX/translationY
                    float curScaleX = 1f + (endScaleX - 1f) * easedProgress;
                    float curScaleY = 1f + (endScaleY - 1f) * easedProgress;
                    float curTranslationX = endTranslationX * easedProgress;
                    float curTranslationY = endTranslationY * easedProgress;

                    // 应用变换（只改 View 属性，不触发 updateViewLayout）
                    contentContainer.setScaleX(curScaleX);
                    contentContainer.setScaleY(curScaleY);
                    contentContainer.setTranslationX(curTranslationX);
                    contentContainer.setTranslationY(curTranslationY);

                    if (progress < 1.0f) {
                        Choreographer.getInstance().postFrameCallback(this);
                    } else {
                        // 动画完成，恢复普通图层、重置变换
                        finishNow();
                    }
                } catch (Exception e) {
                    // 关键：异常时强制完成收起，绝不允许动画静默中断导致整屏冻结
                    Log.e(TAG, "collapse animation frame failed, 强制完成收起", e);
                    finishNow();
                }
            }
        });
    }

    /**
     * 执行收起动画（旧版本，保留以防需要）
     */
    private void performCollapseAnimation() {
        if (aiWindowContainer == null || aiWindowParams == null) {
            return;
        }

        final int startWidth = aiWindowParams.width;
        final int startHeight = aiWindowParams.height;
        final long startTime = System.currentTimeMillis();

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / animationDuration, 1.0f);

                // 使用缓入函数
                float easedProgress = easeInCubic(progress);

                int currentWidth = (int) (startWidth * (1 - easedProgress));
                int currentHeight = (int) (startHeight * (1 - easedProgress));

                try {
                    aiWindowParams.width = Math.max(currentWidth, 0);
                    aiWindowParams.height = Math.max(currentHeight, 0);
                    windowManager.updateViewLayout(aiWindowContainer, aiWindowParams);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (progress < 1.0f) {
                    handler.postDelayed(this, FRAME_INTERVAL); // 8ms = 120fps
                } else {
                    // 动画完成后，移除 AI 窗口，重新显示底部提示条
                    removeAiWindow();
                    showBottomBar();
                    isAiWindowExpanded = false;
                }
            }
        });
    }

    /**
     * 移除 AI 窗口
     */
    private void removeAiWindow() {
        if (aiWindowContainer != null && windowManager != null) {
            try {
                windowManager.removeView(aiWindowContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 销毁 WebView
        destroyWebView();
    }

    /**
     * 重新显示底部提示条
     */
    private void showBottomBar() {
        if (barContainer == null) {
            initBottomBar();
        } else {
            try {
                windowManager.addView(barContainer, barLayoutParams);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 销毁 WebView（防止内存泄漏）
     */
    private void destroyWebView() {
        if (aiWebView != null) {
            try {
                aiWebView.stopLoading();
                aiWebView.clearHistory();
                aiWebView.clearCache(true);
                aiWebView.loadUrl("about:blank");
                aiWebView.removeAllViews();
                aiWebView.destroy();
                aiWebView = null;
                Log.d(TAG, "WebView 已销毁");
            } catch (Exception e) {
                Log.e(TAG, "销毁 WebView 失败", e);
            }
        }
    }

    /**
     * 更新 AI 窗口大小
     *
     * @param width  宽度（像素）
     * @param height 高度（像素）
     */
    public void updateWindowSize(int width, int height) {
        aiWindowWidth = width;
        aiWindowHeight = height;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (isAiWindowExpanded && aiWindowContainer != null && aiWindowParams != null) {
                    try {
                        aiWindowParams.width = aiWindowWidth;
                        aiWindowParams.height = aiWindowHeight;
                        windowManager.updateViewLayout(aiWindowContainer, aiWindowParams);
                        Log.d(TAG, "AI 窗口大小已更新: " + width + "x" + height);
                    } catch (Exception e) {
                        Log.e(TAG, "更新窗口大小失败", e);
                    }
                }
            }
        });
    }

    /**
     * 更新 AI 窗口位置
     *
     * @param posX X 偏移
     * @param posY Y 偏移
     */
    public void updateWindowPosition(int posX, int posY) {
        windowPositionX = posX;
        windowPositionY = posY;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (isAiWindowExpanded && barContainer != null && windowManager != null) {
                    // Update contentContainer margins to reflect position offset
                    try {
                        View rootView = barContainer.getChildAt(0);
                        if (rootView instanceof FrameLayout && ((FrameLayout) rootView).getChildCount() > 0) {
                            View content = ((FrameLayout) rootView).getChildAt(0);
                            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
                            lp.leftMargin = windowPositionX;
                            lp.topMargin = windowPositionY;
                            lp.rightMargin = -windowPositionX;
                            lp.bottomMargin = -windowPositionY;
                            content.setLayoutParams(lp);

                            // 关键修复：确保 Surface 缓冲区足够大以容纳新位置的内容
                            // 当 windowPositionY 增大时，content 在 CENTER 坐标下的中心点下移
                            // 底部可能超出 barContainer 当前的 Surface 缓冲区边界，被 SurfaceFlinger 裁切
                            DisplayMetrics realMetrics = new DisplayMetrics();
                            windowManager.getDefaultDisplay().getRealMetrics(realMetrics);
                            int contentWidth = lp.width > 0 ? lp.width : aiWindowWidth;
                            int contentHeight = lp.height > 0 ? lp.height : aiWindowHeight;
                            int requiredWidth = Math.max(realMetrics.widthPixels,
                                    4 * Math.abs(posX) + contentWidth);
                            int requiredHeight = Math.max(realMetrics.heightPixels,
                                    4 * Math.abs(posY) + contentHeight);
                            if (requiredWidth > barLayoutParams.width
                                    || requiredHeight > barLayoutParams.height) {
                                barLayoutParams.width = requiredWidth;
                                barLayoutParams.height = requiredHeight;
                                barLayoutParams.x = 0;
                                barLayoutParams.y = 0;
                                // 确保 FLAG_LAYOUT_NO_LIMITS 存在（防止意外丢失）
                                barLayoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                                windowManager.updateViewLayout(barContainer, barLayoutParams);
                                Log.d(TAG, "barContainer surface 已扩大: w=" + requiredWidth
                                        + " h=" + requiredHeight);
                            }

                            Log.d(TAG, "AI 窗口位置已更新(通过margins): (" + posX + ", " + posY + ")");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "更新窗口位置失败", e);
                    }
                }
            }
        });
    }

    /**
     * 更新遮罩窗口外观
     */
    private void updateOverlayAppearance() {
        if (overlayView != null) {
            try {
                int overlayColorInt = Color.parseColor(overlayColor);
                overlayView.setBackgroundColor(overlayColorInt);
                // 如果窗口已展开，更新到当前透明度
                if (isAiWindowExpanded) {
                    overlayView.setAlpha(overlayAlpha / 100.0f);
                }
                Log.d(TAG, "背景遮罩外观已更新 - 颜色: " + overlayColor + ", 透明度: " + overlayAlpha + "%");
            } catch (Exception e) {
                Log.e(TAG, "更新遮罩外观失败", e);
            }
        }
    }

    /**
     * 缓出立方函数
     */
    private float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    /**
     * 缓入立方函数
     */
    private float easeInCubic(float t) {
        return (float) Math.pow(t, 3);
    }

    /**
     * 更新 AI URL
     *
     * @param url 新的 AI 网页 URL
     */
    public void updateAiUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }

        // 关键修复：保存 URL 到成员变量，下次打开时使用
        currentAiUrl = url;
        Log.d(TAG, "AI URL 已保存到变量: " + currentAiUrl);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (isAiWindowExpanded && aiWebView != null) {
                    try {
                        // 如果窗口已经展开，立即加载新 URL
                        aiWebView.loadUrl(url);
                        loadedAiUrl = url;
                        Log.d(TAG, "WebView 立即加载新 URL: " + url);
                    } catch (Exception e) {
                        Log.e(TAG, "更新 AI URL 失败", e);
                    }
                } else {
                    Log.d(TAG, "窗口未展开，URL 已保存，下次打开时生效");
                }
            }
        });
    }

    /**
     * 从 SharedPreferences 加载保存的设置
     */
    private void loadSettings() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);

            // 加载提示条样式
            currentWidth = prefs.getInt("bar_width", currentWidth);
            currentHeight = prefs.getInt("bar_height", currentHeight);
            currentCornerRadius = prefs.getInt("bar_corner", currentCornerRadius);
            // 关键修复：SettingsActivity 以 int(0~100) 保存，不能直接用 getFloat 读取
            currentAlpha = prefs.getInt("bar_alpha", 0) / 100.0f;
            barPositionX = prefs.getInt("bar_position_x", barPositionX);
            barPositionY = prefs.getInt("bar_position_y", barPositionY);
            longPressDuration = prefs.getInt("long_press_duration", longPressDuration);

            // 加载 AI 窗口设置
            int windowWidth = prefs.getInt("window_width", 350);
            int windowHeight = prefs.getInt("window_height", 600);
            windowPositionX = prefs.getInt("window_position_x", windowPositionX);
            windowPositionY = prefs.getInt("window_position_y", windowPositionY);

            // 转换窗口尺寸为像素
            aiWindowWidth = dpToPx(windowWidth);
            aiWindowHeight = dpToPx(windowHeight);

            // 加载 AI URL（用户通过设置修改的 URL）
            String savedUrl = prefs.getString("ai_url", null);
            if (savedUrl != null) {
                currentAiUrl = savedUrl;
            }

            // 加载边框和阴影设置
            borderWidth = prefs.getInt("border_width", borderWidth);
            borderColor = prefs.getString("border_color", borderColor);
            // 关键修复：SettingsActivity 以 int(0~100) 保存
            borderAlpha = prefs.getInt("border_alpha", (int) (borderAlpha * 100)) / 100.0f;
            shadowWidth = prefs.getInt("shadow_width", shadowWidth);
            shadowColor = prefs.getString("shadow_color", shadowColor);
            // 关键修复：SettingsActivity 以 int(0~100) 保存
            shadowAlpha = prefs.getInt("shadow_alpha", (int) (shadowAlpha * 100)) / 100.0f;

            // 加载窗口圆角半径
            cornerRadius = prefs.getInt("corner_radius", cornerRadius);

            // 加载关闭按钮显示设置
            showCloseButton = prefs.getBoolean("show_close_button", showCloseButton);

            // 加载显示刷新按钮设置
            showRefreshButton = prefs.getBoolean("show_refresh_button", showRefreshButton);

            // 加载显示后退按钮设置
            showBackButton = prefs.getBoolean("show_back_button", showBackButton);

            // 加载顶部小横条透明度
            topBarTransparency = prefs.getInt("top_bar_transparency", topBarTransparency);

            // 加载输入法避让设置
            keyboardOffset = prefs.getInt("keyboard_offset", keyboardOffset);
            keyboardAnimDuration = prefs.getInt("keyboard_anim_duration", keyboardAnimDuration);

            // 加载点击窗口外关闭窗口设置
            closeOnOutsideClick = prefs.getBoolean("close_on_outside_click", true);

            // 加载侧边返回手势设置
            backPrioritizeWebview = prefs.getBoolean("back_prioritize_webview", true);

            // 加载单击提示条打开的应用设置
            clickAppPackage = prefs.getString("click_app_package", "");
            clickAppName = prefs.getString("click_app_name", "");
            clickAppClass = prefs.getString("click_app_class", "");

            // ===== 加载触发行为配置 =====
            clickAction = prefs.getString("click_action", "");
            // 向后兼容：如果 clickAction 未设置
            if (TextUtils.isEmpty(clickAction)) {
                clickAction = TextUtils.isEmpty(clickAppPackage) ? ACTION_NONE : ACTION_APP;
            }
            doubleClickAction = prefs.getString("double_click_action", ACTION_SHORTCUT);
            longPressAction = prefs.getString("long_press_action",
                    AppConfig.AI_WINDOW_ENABLED ? ACTION_AI : ACTION_NONE);

            // AI 窗口功能隐藏期间：ai 行为按「无」处理（不覆写存储值，恢复配置后原样生效）
            if (!AppConfig.AI_WINDOW_ENABLED) {
                if (ACTION_AI.equals(clickAction)) clickAction = ACTION_NONE;
                if (ACTION_AI.equals(doubleClickAction)) doubleClickAction = ACTION_NONE;
                if (ACTION_AI.equals(longPressAction)) longPressAction = ACTION_NONE;
            }

            // 加载双击打开的应用
            doubleClickAppPackage = prefs.getString("double_click_app_package", "");
            doubleClickAppName = prefs.getString("double_click_app_name", "");
            doubleClickAppClass = prefs.getString("double_click_app_class", "");

            // 加载长按打开的应用
            longPressAppPackage = prefs.getString("long_press_app_package", "");
            longPressAppName = prefs.getString("long_press_app_name", "");
            longPressAppClass = prefs.getString("long_press_app_class", "");

            // 加载窗口打开开关
            clickOpenInWindow = prefs.getBoolean("click_open_in_window", false);
            doubleClickOpenInWindow = prefs.getBoolean("double_click_open_in_window", false);
            longPressOpenInWindow = prefs.getBoolean("long_press_open_in_window", false);

            // 加载触发时振动开关
            clickVibrate = prefs.getBoolean("click_vibrate_enabled", false);
            doubleClickVibrate = prefs.getBoolean("double_click_vibrate_enabled", false);
            longPressVibrate = prefs.getBoolean("long_press_vibrate_enabled", false);

            // 加载动画速度
            animationDuration = prefs.getInt("anim_duration", animationDuration);
            overlayAnimDuration = prefs.getInt("overlay_anim_duration", overlayAnimDuration);

            // 加载按下状态样式
            pressedBarEnabled = prefs.getBoolean("pressed_bar_enabled", pressedBarEnabled);
            pressedBarColor = prefs.getString("pressed_bar_color", pressedBarColor);
            pressedBarAlpha = prefs.getInt("pressed_bar_alpha", pressedBarAlpha);
            pressedBarWidth = prefs.getInt("pressed_bar_width", pressedBarWidth);
            pressedBarHeight = prefs.getInt("pressed_bar_height", pressedBarHeight);
            pressedBarCorner = prefs.getInt("pressed_bar_corner", pressedBarCorner);
            pressedBarFeatherWidth = prefs.getInt("pressed_bar_feather_width", pressedBarFeatherWidth);
            pressedBarPositionY = prefs.getInt("pressed_bar_position_y", pressedBarPositionY);
            pressedBarGlowRadius = prefs.getInt("pressed_bar_glow_radius", pressedBarGlowRadius);
            pressedBarColorMode = prefs.getString("pressed_bar_color_mode", pressedBarColorMode);

            // 加载背景遮罩设置
            overlayColor = prefs.getString("overlay_color", overlayColor);
            overlayAlpha = prefs.getInt("overlay_alpha", overlayAlpha);

            // 加载遮罩渐变时长
            overlayAnimDuration = prefs.getInt("overlay_anim_duration", overlayAnimDuration);

            // 加载快捷方式窗口设置
            shortcutWindowWidth = prefs.getInt("shortcut_window_width", shortcutWindowWidth);
            shortcutWindowHeight = prefs.getInt("shortcut_window_height", shortcutWindowHeight);
            shortcutWindowPositionY = prefs.getInt("shortcut_window_position_y", shortcutWindowPositionY);
            shortcutCornerRadius = prefs.getInt("shortcut_corner_radius", shortcutCornerRadius);
            shortcutIconSize = prefs.getInt("shortcut_icon_size", shortcutIconSize);
            shortcutIconCorner = prefs.getInt("shortcut_icon_corner", shortcutIconCorner);
        tryFullscreenWindowing = prefs.getBoolean("try_fullscreen_windowing", tryFullscreenWindowing);
            // 自定义 windowingMode：留空/非法 → 默认 6
            String customWm = prefs.getString("custom_windowing_mode", "");
            if (!TextUtils.isEmpty(customWm)) {
                try {
                    customWindowingMode = Integer.parseInt(customWm.trim());
                } catch (NumberFormatException ignored) {
                    customWindowingMode = 6;
                }
            }
            shortcutStyle = prefs.getInt("shortcut_style", 0);
            shortcutBlurRadius = prefs.getInt("shortcut_blur_radius", 20);
            shortcutOverlayAlpha = prefs.getInt("shortcut_overlay_alpha", 180);
            
            // 加载 AI 窗口模糊效果设置
            aiStyle = prefs.getInt("ai_style", 0);
            aiBlurRadius = prefs.getInt("ai_blur_radius", 25);
            aiOverlayAlpha = prefs.getInt("ai_overlay_alpha", 204);

            useExpandDirectly = prefs.getBoolean("use_expand_directly", false);
            // 加载网页背景透明和自定义 CSS（用于 AI 窗口）
            webpageBgTransparent = prefs.getBoolean("webpage_bg_transparent", false);
            customCss = prefs.getString("custom_css", "");

            Log.d(TAG, "设置已加载 - 提示条: 宽=" + currentWidth + ", 高=" + currentHeight +
                    ", 圆角=" + currentCornerRadius + ", 透明度=" + currentAlpha +
                    ", 位置X=" + barPositionX + ", 位置Y=" + barPositionY);
            Log.d(TAG, "设置已加载 - AI窗口: 宽=" + windowWidth + "dp, 高=" + windowHeight +
                    "dp, 位置X=" + windowPositionX + ", 位置Y=" + windowPositionY);
            Log.d(TAG, "设置已加载 - 按下样式: 颜色=" + pressedBarColor + ", 透明度=" + pressedBarAlpha +
                    ", 宽=" + pressedBarWidth + "dp, 高=" + pressedBarHeight + "dp, 圆角=" + pressedBarCorner + "dp, 羽化宽度=" + pressedBarFeatherWidth + "dp, Y=" + pressedBarPositionY + "dp, 光晕=" + pressedBarGlowRadius + "dp, 颜色模式=" + pressedBarColorMode);
        } catch (Exception e) {
            Log.e(TAG, "加载设置失败", e);
        }
    }

    private int computeShortcutBgColor() {
        if (shortcutStyle == 0) {
            return getWindowBgColor();
        } else {
            int baseColor = getWindowBgColor();
            int alpha = Math.min(255, Math.max(0, shortcutOverlayAlpha));
            return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        }
    }

    /**
     * dp转px
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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

    @Override
    public void onDestroy() {
        DiagLog.log(this, "service onDestroy");
        if (rainbowAnimator != null) {
            rainbowAnimator.cancel();
            rainbowAnimator = null;
        }
        if (restoreBarReceiver != null) {
            try {
                unregisterReceiver(restoreBarReceiver);
                restoreBarReceiver = null;
                Log.d(TAG, "已取消注册 RESTORE_BAR 广播接收器");
            } catch (Exception e) {
                Log.e(TAG, "取消注册广播接收器失败", e);
            }
        }
        super.onDestroy();

        // 关闭自定义上下文菜单
        dismissContextMenu();

        // 销毁 WebView
        destroyWebView();

        // 移除底部提示条
        if (barContainer != null && windowManager != null) {
            try {
                windowManager.removeView(barContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 移除光晕窗口
        if (glowContainer != null && windowManager != null) {
            try {
                windowManager.removeView(glowContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 移除 AI 窗口
        if (aiWindowContainer != null && windowManager != null) {
            try {
                windowManager.removeView(aiWindowContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        // 移除背景遮罩窗口
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "移除背景遮罩窗口失败", e);
            }
            overlayView = null;
            overlayLayoutParams = null;
        }



        Log.d(TAG, "服务已销毁，所有资源已清理");
    }

    // ========== 自定义 View：Canvas + PorterDuff 边缘羽化 ==========

    /**
     * 自定义 FrameLayout，重写 onDraw 使用 PorterDuff.Mode.DST_OUT 蒙版
     * 实现四周向内渐变透明（真正的边缘羽化淡出效果）
     * <p>
     * 原理：
     * 1. saveLayer 创建带 Alpha 透明图层
     * 2. 先由父类 FrameLayout 绘制背景（圆角实色）
     * 3. 用 DST_OUT 绘制径向渐变蒙版，擦除边缘像素实现淡出
     * 4. 合并图层得到自然羽化边缘
     */
    private class FeatheredFrameLayout extends FrameLayout {
        private Paint maskPaint;
        private Shader topMask, bottomMask, leftMask, rightMask;
        private boolean featherEnabled = false;
        private boolean needsMaskUpdate = false;
        private int featherColor;
        private int featherCornerDp;
        private int featherWidthDp = 15; // 各边统一羽化宽度 (dp)，可通过设置调节

        public FeatheredFrameLayout(Context context) {
            super(context);
            maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        /**
         * 启用/禁用边缘羽化效果
         *
         * @param enabled        是否启用
         * @param color          背景色
         * @param cornerRadiusDp 圆角半径 (dp)
         * @param featherWidthDp 羽化宽度 (dp)
         */
        public void setFeatherEnabled(boolean enabled, int color, int cornerRadiusDp, int featherWidthDp) {
            this.featherEnabled = enabled;
            this.featherColor = color;
            this.featherCornerDp = cornerRadiusDp;
            this.featherWidthDp = featherWidthDp;
            needsMaskUpdate = true;
            invalidate();
        }

        /**
         * 更新 4 个线性渐变蒙版（上/下/左/右各一条边）
         * 每条边从边缘向中心由不透明过渡到透明，再用 DST_OUT 擦除边缘像素
         * 这样四条边有完全一致的羽化宽度，不会出现径向渐变的矩形失真
         */
        private void updateMasks() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int fw = dpToPx(featherWidthDp);
            int fh = dpToPx(featherWidthDp);
            // 羽化宽度不能超过视图尺寸的一半
            if (fw > w / 2) fw = w / 2;
            if (fh > h / 2) fh = h / 2;

            // 每条边从边缘(alpha=255,不透明)向中心(alpha=0,透明)渐变
            // DST_OUT: 不透明处完全擦除内容，透明处完全保留
            // 实现从边缘向内逐渐淡出的效果

            // 上边：顶部(不透明) -> 向下 fh 像素(透明)
            topMask = new LinearGradient(0f, 0f, 0f, (float) fh,
                    0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
            // 下边：底部(不透明) -> 向上 fh 像素(透明)
            bottomMask = new LinearGradient(0f, (float) h, 0f, (float) (h - fh),
                    0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
            // 左边：左边缘(不透明) -> 向右 fw 像素(透明)
            leftMask = new LinearGradient(0f, 0f, (float) fw, 0f,
                    0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
            // 右边：右边缘(不透明) -> 向左 fw 像素(透明)
            rightMask = new LinearGradient((float) w, 0f, (float) (w - fw), 0f,
                    0xFF000000, 0x00000000, Shader.TileMode.CLAMP);

            needsMaskUpdate = false;
        }

        @Override
        public void draw(Canvas canvas) {
            if (!featherEnabled) {
                super.draw(canvas);
                return;
            }

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                super.draw(canvas);
                return;
            }

            // 更新蒙版（首次绘制或尺寸变化后）
            if (needsMaskUpdate || topMask == null) {
                updateMasks();
            }

            // 1. saveLayer 创建离屏缓冲区
            int saved = canvas.saveLayer(0, 0, w, h, null);

            // 2. 绘制所有内容（背景、onDraw、子 View）
            super.draw(canvas);

            // 3. 依次用 DST_OUT 蒙版擦除四条边的边缘像素
            //    使用同一个 maskPaint，只需在每次 drawRect 前切换 shader
            int fw = dpToPx(featherWidthDp);
            int fh = dpToPx(featherWidthDp);

            // 上边羽化
            maskPaint.setShader(topMask);
            canvas.drawRect(0, 0, w, fh, maskPaint);
            // 下边羽化
            maskPaint.setShader(bottomMask);
            canvas.drawRect(0, h - fh, w, h, maskPaint);
            // 左边羽化
            maskPaint.setShader(leftMask);
            canvas.drawRect(0, 0, fw, h, maskPaint);
            // 右边羽化
            maskPaint.setShader(rightMask);
            canvas.drawRect(w - fw, 0, w, h, maskPaint);

            // 4. 合并图层
            canvas.restoreToCount(saved);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            needsMaskUpdate = true;
        }
    }

    private Path buildContinuousRoundRectPath(int width, int height, float cornerRadiusPx) {
        Path path = new Path();
        if (width <= 0 || height <= 0) return path;
        float r = Math.min(cornerRadiusPx, Math.min(width, height) / 2f);
        if (r <= 0) {
            path.addRect(0, 0, width, height, Path.Direction.CW);
            return path;
        }
        float c = r * ContinuousRoundRectDrawable.SMOOTH_FACTOR;
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
        return path;
    }
    /**
     * 连续曲率圆角 Drawable（使用三次贝塞尔曲线，形状接近普通圆角矩形，拐角平滑）
     * 圆角半径由外部设置控制，平滑度固定为0.65（接近 iOS 风格）
     */
    private static class ContinuousRoundRectDrawable extends Drawable {
        private Paint fillPaint;
        private Paint strokePaint;
        private Path path;
        private float cornerRadius;
        private int width, height;
        private float strokeWidth;

        // 控制平滑度的因子，0.5~0.8，0.65 为推荐值（iOS 风格）
        private static final float SMOOTH_FACTOR = 0.65f;

        public ContinuousRoundRectDrawable(int fillColor, float cornerRadiusPx) {
            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(fillColor);
            fillPaint.setStyle(Paint.Style.FILL);

            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.TRANSPARENT);
            strokePaint.setStrokeWidth(0);

            this.cornerRadius = cornerRadiusPx;
            path = new Path();
        }

        /**
         * 设置边框（描边）
         */
        public void setStroke(int widthPx, int color) {
            this.strokeWidth = widthPx;
            strokePaint.setStrokeWidth(widthPx);
            strokePaint.setColor(color);
            invalidateSelf();
        }

        /**
         * 更新填充颜色（用于深色模式切换）
         */
        public void setFillColor(int color) {
            fillPaint.setColor(color);
            invalidateSelf();
        }

        /**
         * 更新圆角半径
         */
        public void setCornerRadius(float radiusPx) {
            this.cornerRadius = radiusPx;
            buildPath();
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

            float c = r * SMOOTH_FACTOR; // 控制点偏移量
            float w = width;
            float h = height;

            // 从左上角的上边起点 (r, 0) 开始
            path.moveTo(r, 0);
            // 上边直线到右上角起点 (w - r, 0)
            path.lineTo(w - r, 0);
            // 右上角：从 (w-r, 0) 到 (w, r)
            path.cubicTo(w - r + c, 0, w, r - c, w, r);
            // 右边直线到右下角起点 (w, h - r)
            path.lineTo(w, h - r);
            // 右下角：从 (w, h-r) 到 (w-r, h)
            path.cubicTo(w, h - r + c, w - r + c, h, w - r, h);
            // 下边直线到左下角起点 (r, h)
            path.lineTo(r, h);
            // 左下角：从 (r, h) 到 (0, h-r)
            path.cubicTo(r - c, h, 0, h - r + c, 0, h - r);
            // 左边直线到左上角起点 (0, r)
            path.lineTo(0, r);
            // 左上角：从 (0, r) 到 (r, 0)
            path.cubicTo(0, r - c, r - c, 0, r, 0);
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            if (fillPaint.getColor() != Color.TRANSPARENT || fillPaint.getAlpha() > 0) {
                canvas.drawPath(path, fillPaint);
            }
            if (strokePaint.getStrokeWidth() > 0 && strokePaint.getColor() != Color.TRANSPARENT) {
                canvas.drawPath(path, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

