package com.jtfa.barenhancement;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置页面 - AI 悬浮窗配置
 * 包含提示条样式、AI 窗口大小、网页 URL 等设置
 */
public class SettingsActivity extends AppCompatActivity {

    // SharedPreferences 相关
    private static final String PREFS_NAME = "ai_window_settings";
    private static final String KEY_AI_URL = "ai_url";
    private static final String KEY_BAR_WIDTH = "bar_width";
    private static final String KEY_BAR_HEIGHT = "bar_height";
    private static final String KEY_BAR_CORNER = "bar_corner";
    private static final String KEY_BAR_ALPHA = "bar_alpha";
    private static final String KEY_BAR_POSITION_Y = "bar_position_y";
    private static final String KEY_LONG_PRESS_DURATION = "long_press_duration";
    private static final String KEY_WINDOW_WIDTH = "window_width";
    private static final String KEY_WINDOW_HEIGHT = "window_height";
    private static final String KEY_WINDOW_POSITION_Y = "window_position_y";
    
    // 边框和阴影设置
    private static final String KEY_BORDER_WIDTH = "border_width";
    private static final String KEY_CORNER_RADIUS = "corner_radius"; // 新增：窗口圆角半径
    private static final String KEY_BORDER_COLOR = "border_color";
    private static final String KEY_BORDER_ALPHA = "border_alpha";
    private static final String KEY_SHADOW_WIDTH = "shadow_width";
    private static final String KEY_SHADOW_COLOR = "shadow_color";
    private static final String KEY_SHADOW_ALPHA = "shadow_alpha";
    private static final String KEY_SHOW_CLOSE_BUTTON = "show_close_button"; // 新增：关闭按钮显示设置
    private static final String KEY_SHOW_REFRESH_BUTTON = "show_refresh_button"; // 显示刷新按钮
    private static final String KEY_SHOW_BACK_BUTTON = "show_back_button"; // 显示后退按钮
    private static final String KEY_CLOSE_ON_OUTSIDE_CLICK = "close_on_outside_click"; // 点击窗口外关闭窗口
    private static final String KEY_BACK_PRIORITIZE_WEBVIEW = "back_prioritize_webview"; // 返回优先网页上一级
    
    // 单击提示条打开第三方应用
    private static final String KEY_CLICK_APP_PACKAGE = "click_app_package";
    private static final String KEY_CLICK_APP_NAME = "click_app_name";
    private static final String KEY_CLICK_APP_CLASS = "click_app_class";
    
    // 触发行为配置
    private static final String KEY_CLICK_ACTION = "click_action";
    private static final String KEY_DOUBLE_CLICK_ACTION = "double_click_action";
    private static final String KEY_LONG_PRESS_ACTION = "long_press_action";
    
    // 双击打开应用
    private static final String KEY_DOUBLE_CLICK_APP_PACKAGE = "double_click_app_package";
    private static final String KEY_DOUBLE_CLICK_APP_NAME = "double_click_app_name";
    private static final String KEY_DOUBLE_CLICK_APP_CLASS = "double_click_app_class";
    
    // 长按打开应用
    private static final String KEY_LONG_PRESS_APP_PACKAGE = "long_press_app_package";
    private static final String KEY_LONG_PRESS_APP_NAME = "long_press_app_name";
    private static final String KEY_LONG_PRESS_APP_CLASS = "long_press_app_class";
    
    // 窗口打开开关
    private static final String KEY_CLICK_OPEN_IN_WINDOW = "click_open_in_window";
    private static final String KEY_DOUBLE_CLICK_OPEN_IN_WINDOW = "double_click_open_in_window";
    private static final String KEY_LONG_PRESS_OPEN_IN_WINDOW = "long_press_open_in_window";
    
    // 触发时振动开关
    private static final String KEY_CLICK_VIBRATE = "click_vibrate_enabled";
    private static final String KEY_DOUBLE_CLICK_VIBRATE = "double_click_vibrate_enabled";
    private static final String KEY_LONG_PRESS_VIBRATE = "long_press_vibrate_enabled";
    
    // 行为类型常量
    private static final String ACTION_NONE = "none";
    private static final String ACTION_APP = "app";
    private static final String ACTION_SHORTCUT = "shortcut";
    private static final String ACTION_AI = "ai";
    private static final String KEY_APP_SELECTOR_SHOWN = "app_selector_first_time_shown";
    
    // 输入法避让设置
    private static final String KEY_KEYBOARD_OFFSET = "keyboard_offset";
    private static final String KEY_KEYBOARD_ANIM_DURATION = "keyboard_anim_duration";
    
    // 动画速度设置
    private static final String KEY_ANIM_DURATION = "anim_duration";
    
    // 按下状态样式设置
    private static final String KEY_PRESSED_BAR_ENABLED = "pressed_bar_enabled";
    private static final String KEY_PRESSED_BAR_COLOR = "pressed_bar_color";
    private static final String KEY_PRESSED_BAR_ALPHA = "pressed_bar_alpha";
    private static final String KEY_PRESSED_BAR_WIDTH = "pressed_bar_width";
    private static final String KEY_PRESSED_BAR_HEIGHT = "pressed_bar_height";
    private static final String KEY_PRESSED_BAR_CORNER = "pressed_bar_corner";
    private static final String KEY_PRESSED_BAR_FEATHER = "pressed_bar_feather_width";
    private static final String KEY_PRESSED_BAR_POSITION_Y = "pressed_bar_position_y";
    private static final String KEY_PRESSED_BAR_GLOW_RADIUS = "pressed_bar_glow_radius";
    private static final String KEY_PRESSED_BAR_COLOR_MODE = "pressed_bar_color_mode";
    
    // 背景遮罩设置
    private static final String KEY_OVERLAY_COLOR = "overlay_color";
    private static final String KEY_OVERLAY_ALPHA = "overlay_alpha";
    private static final String KEY_OVERLAY_ANIM_DURATION = "overlay_anim_duration";
    
    // 快捷方式窗口设置
    private static final String KEY_SHORTCUT_WINDOW_WIDTH = "shortcut_window_width";
    private static final String KEY_SHORTCUT_WINDOW_HEIGHT = "shortcut_window_height";
    private static final String KEY_SHORTCUT_WINDOW_POSITION_Y = "shortcut_window_position_y";
    private static final String KEY_SHORTCUT_CORNER_RADIUS = "shortcut_corner_radius";
    private static final String KEY_SHORTCUT_ICON_SIZE = "shortcut_icon_size";
    private static final String KEY_SHORTCUT_ICON_CORNER = "shortcut_icon_corner";
    private static final String KEY_SHORTCUT_ICON_COLUMNS = "shortcut_icon_columns";
    private static final String KEY_SHORTCUT_EDGE_BAND = "shortcut_edge_band_dp";
    private static final String KEY_SHORTCUT_EDGE_OPACITY = "shortcut_edge_opacity";
    // 手指光晕参数
    private static final String KEY_FINGER_GLOW_BRIGHTNESS = "finger_glow_brightness";
    private static final String KEY_FINGER_GLOW_SIZE = "finger_glow_size";

    private static final String KEY_SHORTCUT_STYLE = "shortcut_style";
    private static final String KEY_BLUR_SYNC_MODE = "blur_sync_mode";
    private static final String KEY_SHORTCUT_BLUR_RADIUS = "shortcut_blur_radius";
    private static final String KEY_SHORTCUT_OVERLAY_ALPHA = "shortcut_overlay_alpha";
    
    // AI 窗口模糊效果设置
    private static final String KEY_AI_STYLE = "ai_style";
    private static final String KEY_AI_BLUR_RADIUS = "ai_blur_radius";
    private static final String KEY_AI_OVERLAY_ALPHA = "ai_overlay_alpha";
    private static final String KEY_CUSTOM_CSS = "custom_css"; // 自定义 CSS
    private static final String KEY_WEBPAGE_BG_TRANSPARENT = "webpage_bg_transparent"; // 网页背景透明
    
    // 顶部小横条透明度
    private static final String KEY_TOP_BAR_TRANSPARENCY = "top_bar_transparency";
    
    // AI URL 输入框
    private EditText editAiUrl;
    
    // 提示条样式控件
    private SeekBar seekBarWidth, seekBarHeight, seekBarCorner;
    private TextView textWidth, textHeight, textCorner;
    private SeekBar seekBarPositionY;
    private TextView textPositionY;
    private SeekBar seekLongPressDuration;
    private TextView textLongPressDuration;
    
    // AI 窗口大小控件
    private SeekBar seekWindowWidth, seekWindowHeight;
    private TextView textWindowWidth, textWindowHeight;
    private SeekBar seekWindowPositionY;
    private TextView textWindowPositionY;
    
    // 边框和阴影控件
    private SeekBar seekBorderWidth, seekBorderAlpha;
    private EditText editBorderColor;
    private TextView textBorderWidth, textBorderAlpha;
    private SeekBar seekCornerRadius; // 新增：圆角半径 SeekBar
    private TextView textCornerRadius; // 新增：圆角半径 TextView
//    private SeekBar seekShadowWidth, seekShadowAlpha;
//    private EditText editShadowColor;
//    private TextView textShadowWidth, textShadowAlpha;
    private Switch switchShowCloseButton; // 新增：关闭按钮显示开关
    private Switch switchShowRefreshButton; // 显示刷新按钮开关
    private Switch switchShowBackButton; // 显示后退按钮开关
    private Switch switchCloseOnOutsideClick; // 点击窗口外关闭窗口开关
    private Switch switchBackPrioritizeWebview; // 侧边返回手势行为

    private Switch switchUseExpandDirectly;
    private Switch switchTryFullscreenWindowing;
    private LinearLayout layoutCustomWindowingMode;
    private EditText editCustomWindowingMode;
    
    // 提示条触发行为已搬到首页，由 TriggerActionsController 负责

    // 输入法避让控件
    private SeekBar seekKeyboardOffset;
    private TextView textKeyboardOffset;
    private SeekBar seekKeyboardAnimDuration;
    private TextView textKeyboardAnimDuration;
    
    // 动画速度控件
    private SeekBar seekAnimDuration;
    private TextView textAnimDuration;
    
    // 按下状态样式控件
    private Switch switchPressedBarEnabled;
    private SeekBar seekPressedBarWidth, seekPressedBarHeight, seekPressedBarCorner, seekPressedBarAlpha;
    private TextView textPressedBarWidth, textPressedBarHeight, textPressedBarCorner, textPressedBarAlpha;
    private EditText editPressedBarColor;
    private TextView textPressedBarColorLabel;
    
    // 羽化宽度控件
    private SeekBar seekPressedBarFeather;
    private TextView textPressedBarFeather;
    
    // 按下时位置Y控件
    private SeekBar seekPressedBarPositionY;
    private TextView textPressedBarPositionY;

    // 光晕半径/颜色模式控件
    private SeekBar seekPressedBarGlowRadius;
    private TextView textPressedBarGlowRadius;
    private RadioGroup radioPressedColorMode;
    
    // 按下时位置Y控件
    
    // 背景遮罩控件
    private SeekBar seekOverlayAlpha;
    private TextView textOverlayAlpha;
    private EditText editOverlayColor;
    private SeekBar seekOverlayAnimDuration;
    private TextView textOverlayAnimDuration;
    
    // 快捷方式窗口控件
    private SeekBar seekShortcutWindowWidth, seekShortcutWindowHeight, seekShortcutWindowPositionY;
    private TextView textShortcutWindowWidth, textShortcutWindowHeight, textShortcutWindowPositionY;
    private SeekBar seekShortcutCornerRadius;
    private TextView textShortcutCornerRadius;
    private SeekBar seekShortcutIconColumns;
    private TextView textShortcutIconColumns;
    private SeekBar seekShortcutIconSize;
    private TextView textShortcutIconSize;
    private SeekBar seekShortcutIconCorner;
    private TextView textShortcutIconCorner;

    private Spinner spinnerShortcutStyle;
    private Spinner spinnerBlurSyncMode;
    private LinearLayout layoutBlurSyncMode;
    private SeekBar seekShortcutBlurRadius, seekShortcutOverlayAlpha;
    private TextView textShortcutBlurRadius, textShortcutOverlayAlpha;
    private View textShortcutStyleLabel;
    private View containerShortcutStyle;
    private LinearLayout rowShortcutEdgeRefraction;
    private LinearLayout rowShortcutFingerGlow;
    private LinearLayout rowShortcutDragStretch;
    private int shortcutStyleBeforeFloatMode = 0;
    private boolean floatingModeRestricted = false;
    
    // AI 窗口模糊效果控件
    private Spinner spinnerAiStyle;
    private SeekBar seekAiBlurRadius, seekAiOverlayAlpha;
    private TextView textAiBlurRadius, textAiOverlayAlpha;
    private EditText editCustomCss; // 自定义 CSS
    private Switch switchWebpageBgTransparent; // 网页背景透明

    // ColorOS 风格效果开关（快捷方式窗口）
    private Switch switchShortcutEdgeRefraction;   // 边缘折射光
    private Switch switchShortcutFingerGlow;       // 手指光晕
    private Switch switchShortcutDragStretch;      // 拖动灵动拉伸
    // 边缘光参数（仅边缘折射光开启时显示）
    private LinearLayout containerShortcutEdgeSettings;
    private SeekBar seekShortcutEdgeBand, seekShortcutEdgeOpacity;
    private TextView textShortcutEdgeBand, textShortcutEdgeOpacity;
    // 手指光晕参数
    private LinearLayout containerFingerGlowSettings;
    private SeekBar seekFingerGlowBrightness, seekFingerGlowSize;
    private TextView textFingerGlowBrightness, textFingerGlowSize;
    
    // 顶部小横条透明度控件
    private SeekBar seekTopBarTransparency;
    private TextView textTopBarTransparency;
    
    // 保存按钮
    private Button btnSave;

    // 未保存修改与返回确认
    private boolean hasUnsavedChanges = false;
    private boolean uiInitialized = false;
    private boolean suppressCallbacks = false;
    private boolean discardingChanges = false;
    private boolean activityResumed = false;
    private AlertDialog unsavedDialog;
    private final Handler pendingApplyHandler = new Handler(Looper.getMainLooper());
    private final Runnable pendingApplyRunnable = new Runnable() {
        @Override
        public void run() {
            applyCurrentSettings();
        }
    };
    private final Runnable markUiInitializedRunnable = new Runnable() {
        @Override
        public void run() {
            uiInitialized = true;
            // Spinner 首次布局时可能回调一次 onItemSelected，这里清掉误判
            hasUnsavedChanges = false;
        }
    };

    // 快捷窗口轮廓实时预览层（触摸穿透，不影响操作）
    private WindowOutlineOverlay windowOutlineOverlay;

    // 触发区预览（黑色指示）重试：服务可能刚启动，静态直连会落空，350ms 后补一次
    private final Handler previewRetryHandler = new Handler(Looper.getMainLooper());
    private final Runnable previewRetryRunnable = new Runnable() {
        @Override
        public void run() {
            FloatWindowService.setTriggerAreaPreview(true);
        }
    };

    // SharedPreferences
    private SharedPreferences preferences;

    // 横竖屏布局与逐项同步
    private View settingsRoot;
    private LinearLayout settingsBody;
    private LinearLayout groupTabsContainer;
    private View groupTabsDivider;
    private LinearLayout headerTitleRow;
    private TextView btnOrientationPortrait;
    private TextView btnOrientationLandscape;
    private TextView textLandscapeSyncHint;
    private TextView textAdvancedSyncNote;
    private final Map<String, SyncRowLayout> syncRows = new LinkedHashMap<>();
    private final Map<String, SyncSpec> syncSpecs = new LinkedHashMap<>();
    private final List<SyncRowLayout> advancedSyncRows = new ArrayList<>();
    private int previousRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean previousLandscape = false;
    private Boolean exitTargetLandscape = null;
    private boolean orientationForced = false;
    private boolean restoringOrientation = false;
    private boolean changingOrientation = false;
    private boolean lastLandscape = false;
    private int orientationRequestSeq = 0;
    private int statusBarInsetTop = 0;
    private int navigationBarInsetLeft = 0;
    private int navigationBarInsetRight = 0;
    private int navigationBarInsetBottom = 0;
    private boolean orientationLayoutApplied = false;
    private long suppressCallbacksUntil = 0;

    // 分组锚点（提示条 / 快捷窗口 / 高级）
    private ScrollView settingsScroll;
    private TextView tabTip, tabShortcut, tabAdvanced;
    private View groupTip, groupShortcut, groupAdvanced;
    private int activeGroupIndex = 0;
    private long suppressScrollUntil = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化 SharedPreferences
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        previousRequestedOrientation = getRequestedOrientation();
        previousLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        initViews();
        setupSyncSpecs();
        setupSyncRows();
        applyFeatureFlagVisibility(); // 按总配置隐藏 AI 窗口相关设置
        loadSettings(); // 加载已保存的设置
        setupListeners();
        setupOrientationToggle();
        setupGroupTabs(); // 顶部三个分组锚点：固定显示 + 滑动高亮

        // 监听器就绪后按当前方向刷新一次控件与标签
        withSuppressedCallbacks(this::loadCurrentOrientationIntoControls);
        applyOrientationLayout();

        // 边到边（targetSdk 35+）时顶部标题与内容需避开系统栏；
        // 横屏状态栏较矮、侧边可能有导航栏，不能沿用竖屏固定值。
        View insetHost = settingsRoot != null ? settingsRoot : headerTitleRow;
        if (insetHost != null) {
            insetHost.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets status = insets.getInsets(WindowInsets.Type.statusBars());
                android.graphics.Insets nav = insets.getInsets(WindowInsets.Type.navigationBars());
                statusBarInsetTop = status.top;
                navigationBarInsetLeft = nav.left;
                navigationBarInsetRight = nav.right;
                navigationBarInsetBottom = nav.bottom;
                updateHeaderPadding();
                updateNavigationInsets();
                return insets;
            });
        }

        // 快捷窗口轮廓实时预览层：叠加在内容顶层，触摸穿透不阻挡设置操作
        windowOutlineOverlay = new WindowOutlineOverlay(this);
        ((FrameLayout) findViewById(android.R.id.content)).addView(windowOutlineOverlay,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Spinner 首次布局可能回调一次，延迟标记界面已完成初始化
        pendingApplyHandler.postDelayed(markUiInitializedRunnable, 300);

        // 返回时若有未保存修改，询问保存/取消
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (unsavedDialog != null && unsavedDialog.isShowing()) return;
                if (hasUnsavedChanges) {
                    showUnsavedDialog();
                } else {
                    finish();
                }
            }
        });
    }

    private void showUnsavedDialog() {
        if (isFinishing() || isDestroyed()) return;
        unsavedDialog = new AlertDialog.Builder(this)
                .setMessage("是否保存设置？")
                .setPositiveButton("保存", (dialog, which) -> {
                    dialog.dismiss();
                    saveSettings();
                    finish();
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.dismiss();
                    discardChangesAndFinish();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * 取消本次修改：恢复到 SharedPreferences 中最后一次保存的状态。
     * 未点过保存时，prefs 仍是进入页面时的值，因此等价于回到进入页面时。
     */
    private void discardChangesAndFinish() {
        discardingChanges = true;
        pendingApplyHandler.removeCallbacks(pendingApplyRunnable);
        hasUnsavedChanges = false;

        // 静默从 prefs 重新加载控件，避免监听器把当前修改再次下发
        withSuppressedCallbacks(() -> {
            loadSettings();
            loadCurrentOrientationIntoControls();
        });

        // 把最后一次保存的状态重新下发给服务
        applyCurrentSettings();
        finish();
    }

    /** 任意设置发生变化：标记未保存并立即下发到服务（不写 prefs）。 */
    private void onSettingChanged() {
        if (!uiInitialized || suppressCallbacks || discardingChanges) {
            return;
        }
        captureCurrentValues(OrientationSettings.isLandscape(this));
        hasUnsavedChanges = true;
        applyCurrentSettings();
    }

    /** 文本输入使用防抖，避免每敲一个字符都下发一次。 */
    private void onTextSettingChanged() {
        if (!uiInitialized || suppressCallbacks || discardingChanges) {
            return;
        }
        captureCurrentValues(OrientationSettings.isLandscape(this));
        hasUnsavedChanges = true;
        pendingApplyHandler.removeCallbacks(pendingApplyRunnable);
        pendingApplyHandler.postDelayed(pendingApplyRunnable, 350);
    }

    private void flushPendingApply() {
        pendingApplyHandler.removeCallbacks(pendingApplyRunnable);
        if (!discardingChanges) {
            applyCurrentSettings();
        }
    }

    /** 读取四个滑块当前值，刷新并显示轮廓预览层 */
    private void refreshWindowOutlinePreview() {
        if (windowOutlineOverlay == null) return;
        windowOutlineOverlay.update(
                seekShortcutWindowWidth.getProgress(),
                seekShortcutWindowHeight.getProgress(),
                seekShortcutWindowPositionY.getProgress(),
                seekShortcutCornerRadius.getProgress());
        windowOutlineOverlay.show();
    }

    /** 松手后延迟渐隐轮廓预览层 */
    private void scheduleHideWindowOutlinePreview() {
        if (windowOutlineOverlay != null) windowOutlineOverlay.scheduleHide();
    }

    /**
     * 自定义 windowingMode 输入框：仅配置开启且「尝试消除授权弹窗」开关打开时显示。
     */
    private void updateCustomWindowingModeVisibility() {
        if (AppConfig.CUSTOM_WINDOWING_MODE_ENABLED && switchTryFullscreenWindowing.isChecked()) {
            layoutCustomWindowingMode.setVisibility(View.VISIBLE);
        } else {
            layoutCustomWindowingMode.setVisibility(View.GONE);
        }
    }

    /** 解析输入框：留空/非法 → 默认 6 */
    private int parseCustomWindowingMode() {
        String s = editCustomWindowingMode.getText().toString().trim();
        if (s.isEmpty()) return 6;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 6;
        }
    }

    /**
     * 按总配置 AppConfig.AI_WINDOW_ENABLED 隐藏 AI 窗口相关设置。
     * 功能代码未删除：把配置项改为 true 后重新编译即可恢复。
     */
    private void applyFeatureFlagVisibility() {
        // 模糊层同步模式固定「均衡」档时隐藏整节（与 AI 总开关无关）
        if (AppConfig.BLUR_SYNC_FORCE_BALANCED) {
            layoutBlurSyncMode.setVisibility(View.GONE);
        }

        // 自定义 windowingMode 输入框：配置关闭时始终隐藏
        if (!AppConfig.CUSTOM_WINDOWING_MODE_ENABLED) {
            layoutCustomWindowingMode.setVisibility(View.GONE);
        } else {
            updateCustomWindowingModeVisibility();
        }

        if (AppConfig.AI_WINDOW_ENABLED) {
            return;
        }

        // 隐藏「窗口设置」「AI 窗口模糊效果（含输入法避让）」两张卡片
        findViewById(R.id.card_window_settings).setVisibility(View.GONE);
        findViewById(R.id.card_ai_blur_card).setVisibility(View.GONE);
    }
    
    /**
     * 初始化视图控件
     */
    private void initViews() {
        // AI URL 输入框
        editAiUrl = findViewById(R.id.edit_ai_url);
        
        // 提示条样式控件
        seekBarWidth = findViewById(R.id.seek_bar_width);
        seekBarHeight = findViewById(R.id.seek_bar_height);
        seekBarCorner = findViewById(R.id.seek_bar_corner);
        seekBarPositionY = findViewById(R.id.seek_bar_position_y);
        
        textWidth = findViewById(R.id.text_bar_width);
        textHeight = findViewById(R.id.text_bar_height);
        textCorner = findViewById(R.id.text_bar_corner);
        textPositionY = findViewById(R.id.text_bar_position_y);
        
        // 长按时间控件
        seekLongPressDuration = findViewById(R.id.seek_long_press_duration);
        textLongPressDuration = findViewById(R.id.text_long_press_duration);
        
        // AI 窗口大小控件
        seekWindowWidth = findViewById(R.id.seek_window_width);
        seekWindowHeight = findViewById(R.id.seek_window_height);
        textWindowWidth = findViewById(R.id.text_window_width);
        textWindowHeight = findViewById(R.id.text_window_height);
        seekWindowPositionY = findViewById(R.id.seek_window_position_y);
        textWindowPositionY = findViewById(R.id.text_window_position_y);
        
        // 边框和阴影控件
        seekBorderWidth = findViewById(R.id.seek_border_width);
        seekBorderAlpha = findViewById(R.id.seek_border_alpha);
        editBorderColor = findViewById(R.id.edit_border_color);
        textBorderWidth = findViewById(R.id.text_border_width);
        textBorderAlpha = findViewById(R.id.text_border_alpha);
        
        // 圆角半径控件
        seekCornerRadius = findViewById(R.id.seek_corner_radius);
        textCornerRadius = findViewById(R.id.text_corner_radius);
        

        
        // 关闭按钮显示开关
        switchShowCloseButton = findViewById(R.id.switch_show_close_button);
        
        // 显示刷新按钮开关
        switchShowRefreshButton = findViewById(R.id.switch_show_refresh_button);
        
        // 显示后退按钮开关
        switchShowBackButton = findViewById(R.id.switch_show_back_button);
        
        // 点击窗口外关闭窗口开关
        switchCloseOnOutsideClick = findViewById(R.id.switch_close_on_outside_click);
        
        // 侧边返回手势行为开关
        switchBackPrioritizeWebview = findViewById(R.id.switch_back_prioritize_webview);
        
        // 提示条触发行为已搬到首页（TriggerActionsController）

        seekKeyboardOffset = findViewById(R.id.seek_keyboard_offset);
        textKeyboardOffset = findViewById(R.id.text_keyboard_offset);
        seekKeyboardAnimDuration = findViewById(R.id.seek_keyboard_anim_duration);
        textKeyboardAnimDuration = findViewById(R.id.text_keyboard_anim_duration);
        
        // 动画速度控件
        seekAnimDuration = findViewById(R.id.seek_anim_duration);
        textAnimDuration = findViewById(R.id.text_anim_duration);
        
        // 按下状态样式控件
        switchPressedBarEnabled = findViewById(R.id.switch_pressed_bar_enabled);
        seekPressedBarWidth = findViewById(R.id.seek_pressed_bar_width);
        seekPressedBarHeight = findViewById(R.id.seek_pressed_bar_height);
        seekPressedBarCorner = findViewById(R.id.seek_pressed_bar_corner);
        seekPressedBarAlpha = findViewById(R.id.seek_pressed_bar_alpha);
        textPressedBarWidth = findViewById(R.id.text_pressed_bar_width);
        textPressedBarHeight = findViewById(R.id.text_pressed_bar_height);
        textPressedBarCorner = findViewById(R.id.text_pressed_bar_corner);
        textPressedBarAlpha = findViewById(R.id.text_pressed_bar_alpha);
        editPressedBarColor = findViewById(R.id.edit_pressed_bar_color);
        textPressedBarColorLabel = findViewById(R.id.text_pressed_bar_color_label);
        
        // 羽化宽度控件
        seekPressedBarFeather = findViewById(R.id.seek_pressed_bar_feather);
        textPressedBarFeather = findViewById(R.id.text_pressed_bar_feather);
        
        // 按下时位置Y控件
        seekPressedBarPositionY = findViewById(R.id.seek_pressed_bar_position_y);
        textPressedBarPositionY = findViewById(R.id.text_pressed_bar_position_y);

        // 光晕半径/颜色模式控件
        seekPressedBarGlowRadius = findViewById(R.id.seek_pressed_bar_glow_radius);
        textPressedBarGlowRadius = findViewById(R.id.text_pressed_bar_glow_radius);
        radioPressedColorMode = findViewById(R.id.radio_pressed_color_mode);
        
        // 按下时位置Y控件
        
        // 背景遮罩控件
        seekOverlayAlpha = findViewById(R.id.seek_overlay_alpha);
        textOverlayAlpha = findViewById(R.id.text_overlay_alpha);
        editOverlayColor = findViewById(R.id.edit_overlay_color);
        seekOverlayAnimDuration = findViewById(R.id.seek_overlay_anim_duration);
        textOverlayAnimDuration = findViewById(R.id.text_overlay_anim_duration);
        
        // 快捷方式窗口控件
        seekShortcutWindowWidth = findViewById(R.id.seek_shortcut_window_width);
        seekShortcutWindowHeight = findViewById(R.id.seek_shortcut_window_height);
        seekShortcutWindowPositionY = findViewById(R.id.seek_shortcut_window_position_y);
        textShortcutWindowWidth = findViewById(R.id.text_shortcut_window_width);
        textShortcutWindowHeight = findViewById(R.id.text_shortcut_window_height);
        textShortcutWindowPositionY = findViewById(R.id.text_shortcut_window_position_y);
        seekShortcutCornerRadius = findViewById(R.id.seek_shortcut_corner_radius);
        textShortcutCornerRadius = findViewById(R.id.text_shortcut_corner_radius);
        seekShortcutIconColumns = findViewById(R.id.seek_shortcut_icon_columns);
        textShortcutIconColumns = findViewById(R.id.text_shortcut_icon_columns);
        seekShortcutIconSize = findViewById(R.id.seek_shortcut_icon_size);
        textShortcutIconSize = findViewById(R.id.text_shortcut_icon_size);
        seekShortcutIconCorner = findViewById(R.id.seek_shortcut_icon_corner);
        textShortcutIconCorner = findViewById(R.id.text_shortcut_icon_corner);


        spinnerShortcutStyle = findViewById(R.id.spinner_shortcut_style);
        textShortcutStyleLabel = findViewById(R.id.text_shortcut_style_label);
        containerShortcutStyle = findViewById(R.id.container_shortcut_style);
        rowShortcutEdgeRefraction = findViewById(R.id.row_shortcut_edge_refraction);
        rowShortcutFingerGlow = findViewById(R.id.row_shortcut_finger_glow);
        rowShortcutDragStretch = findViewById(R.id.row_shortcut_drag_stretch);
        spinnerBlurSyncMode = findViewById(R.id.spinner_blur_sync_mode);
        layoutBlurSyncMode = findViewById(R.id.layout_blur_sync_mode);
        seekShortcutBlurRadius = findViewById(R.id.seek_shortcut_blur_radius);
        seekShortcutOverlayAlpha = findViewById(R.id.seek_shortcut_overlay_alpha);
        textShortcutBlurRadius = findViewById(R.id.text_shortcut_blur_radius);
        textShortcutOverlayAlpha = findViewById(R.id.text_shortcut_overlay_alpha);

        // ColorOS 风格效果开关
        switchShortcutEdgeRefraction = findViewById(R.id.switch_shortcut_edge_refraction);
        switchShortcutFingerGlow = findViewById(R.id.switch_shortcut_finger_glow);
        switchShortcutDragStretch = findViewById(R.id.switch_shortcut_drag_stretch);

        // 边缘光参数控件
        containerShortcutEdgeSettings = findViewById(R.id.container_shortcut_edge_settings);
        seekShortcutEdgeBand = findViewById(R.id.seek_shortcut_edge_band);
        textShortcutEdgeBand = findViewById(R.id.text_shortcut_edge_band);
        seekShortcutEdgeOpacity = findViewById(R.id.seek_shortcut_edge_opacity);
        textShortcutEdgeOpacity = findViewById(R.id.text_shortcut_edge_opacity);
        // 手指光晕参数
        containerFingerGlowSettings = findViewById(R.id.container_finger_glow_settings);
        seekFingerGlowBrightness = findViewById(R.id.seek_finger_glow_brightness);
        textFingerGlowBrightness = findViewById(R.id.text_finger_glow_brightness);
        seekFingerGlowSize = findViewById(R.id.seek_finger_glow_size);
        textFingerGlowSize = findViewById(R.id.text_finger_glow_size);

        // AI 窗口模糊效果控件
        spinnerAiStyle = findViewById(R.id.spinner_ai_style);
        seekAiBlurRadius = findViewById(R.id.seek_ai_blur_radius);
        seekAiOverlayAlpha = findViewById(R.id.seek_ai_overlay_alpha);
        textAiBlurRadius = findViewById(R.id.text_ai_blur_radius);
        textAiOverlayAlpha = findViewById(R.id.text_ai_overlay_alpha);
        editCustomCss = findViewById(R.id.edit_custom_css);
        switchWebpageBgTransparent = findViewById(R.id.switch_webpage_bg_transparent);
        switchUseExpandDirectly = findViewById(R.id.switch_use_expand_directly);
        switchTryFullscreenWindowing = findViewById(R.id.switch_try_fullscreen_windowing);
        layoutCustomWindowingMode = findViewById(R.id.layout_custom_windowing_mode);
        editCustomWindowingMode = findViewById(R.id.edit_custom_windowing_mode);
        
        // 顶部小横条透明度控件
        seekTopBarTransparency = findViewById(R.id.seek_top_bar_transparency);
        textTopBarTransparency = findViewById(R.id.text_top_bar_transparency);

        // 横竖屏布局与逐项同步
        settingsRoot = findViewById(R.id.settings_root);
        settingsScroll = findViewById(R.id.settings_scroll);
        settingsBody = findViewById(R.id.settings_body);
        groupTabsContainer = findViewById(R.id.group_tabs_container);
        groupTabsDivider = findViewById(R.id.group_tabs_divider);
        headerTitleRow = findViewById(R.id.header_title_row);
        btnOrientationPortrait = findViewById(R.id.btn_orientation_portrait);
        btnOrientationLandscape = findViewById(R.id.btn_orientation_landscape);
        textLandscapeSyncHint = findViewById(R.id.text_landscape_sync_hint);
        textAdvancedSyncNote = findViewById(R.id.text_advanced_sync_note);

        // 保存按钮
        btnSave = findViewById(R.id.btn_save_apply);
    }

    // ==================== 横竖屏逐项同步 ====================

    private interface ValueReader {
        Object read();
    }

    private interface ValueWriter {
        void write(Object value);
    }

    private static class SyncSpec {
        static final int TYPE_INT = 0;
        static final int TYPE_STRING = 1;
        static final int TYPE_BOOLEAN = 2;

        final String key;
        final int type;
        final Object defaultValue;
        final ValueReader reader;
        final ValueWriter writer;
        Object portrait;
        Object landscape;
        boolean synced = true;
        SyncRowLayout row;

        SyncSpec(String key, int type, Object defaultValue, ValueReader reader, ValueWriter writer) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.reader = reader;
            this.writer = writer;
        }

        Object current(boolean landscapeMode) {
            if (landscapeMode && !synced) {
                return landscape != null ? landscape : defaultValue;
            }
            return portrait != null ? portrait : defaultValue;
        }

        void capture(boolean landscapeMode) {
            if (landscapeMode) {
                if (!synced) landscape = reader.read();
            } else {
                portrait = reader.read();
            }
        }
    }

    private void setupSyncSpecs() {
        syncSpecs.clear();

        // 提示条：触发区 5 项 + 按下样式 10 项
        addSeekSpec(KEY_BAR_WIDTH, 355, seekBarWidth);
        addSeekSpec(KEY_BAR_HEIGHT, 9, seekBarHeight);
        addSeekSpec(KEY_BAR_POSITION_Y, 391, seekBarPositionY);
        addSeekSpec(KEY_BAR_CORNER, 30, seekBarCorner);
        addSeekSpec(KEY_LONG_PRESS_DURATION, 500, seekLongPressDuration);
        addSwitchSpec(KEY_PRESSED_BAR_ENABLED, true, switchPressedBarEnabled);
        addSeekSpec(KEY_PRESSED_BAR_WIDTH, 420, seekPressedBarWidth);
        addSeekSpec(KEY_PRESSED_BAR_HEIGHT, 20, seekPressedBarHeight);
        addSeekSpec(KEY_PRESSED_BAR_POSITION_Y, 0, seekPressedBarPositionY);
        addSeekSpec(KEY_PRESSED_BAR_CORNER, 45, seekPressedBarCorner);
        addSeekSpec(KEY_PRESSED_BAR_ALPHA, 100, seekPressedBarAlpha);
        addPressedColorModeSpec();
        addTextSpec(KEY_PRESSED_BAR_COLOR, "#0099dd", editPressedBarColor);
        addSeekSpec(KEY_PRESSED_BAR_FEATHER, 10, seekPressedBarFeather);
        addSeekSpec(KEY_PRESSED_BAR_GLOW_RADIUS, 25, seekPressedBarGlowRadius);

        // 快捷窗口：窗口 14 项 + 图标 3 项 + 遮罩 4 项 + 动画 1 项
        addSeekSpec(KEY_SHORTCUT_WINDOW_WIDTH, 350, seekShortcutWindowWidth);
        addSeekSpec(KEY_SHORTCUT_WINDOW_HEIGHT, 350, seekShortcutWindowHeight);
        addSeekSpec(KEY_SHORTCUT_WINDOW_POSITION_Y, 231, seekShortcutWindowPositionY);
        addSeekSpec(KEY_SHORTCUT_CORNER_RADIUS, 24, seekShortcutCornerRadius);
        addShortcutStyleSpec();
        addSeekSpec(KEY_SHORTCUT_BLUR_RADIUS, 20, seekShortcutBlurRadius);
        addSeekSpec(KEY_SHORTCUT_OVERLAY_ALPHA, 180, seekShortcutOverlayAlpha);
        addSwitchSpec("shortcut_edge_refraction", true, switchShortcutEdgeRefraction);
        addSeekSpec(KEY_SHORTCUT_EDGE_BAND, 4, seekShortcutEdgeBand);
        addSeekSpec(KEY_SHORTCUT_EDGE_OPACITY, 48, seekShortcutEdgeOpacity);
        addSwitchSpec("shortcut_finger_glow", true, switchShortcutFingerGlow);
        addSeekSpec(KEY_FINGER_GLOW_BRIGHTNESS, 27, seekFingerGlowBrightness);
        addSeekSpec(KEY_FINGER_GLOW_SIZE, 100, seekFingerGlowSize);
        addSwitchSpec("shortcut_drag_stretch", true, switchShortcutDragStretch);
        addSeekSpec(KEY_SHORTCUT_ICON_COLUMNS, AppConfig.DEFAULT_SHORTCUT_ICON_COLUMNS,
                seekShortcutIconColumns);
        addSeekSpec(KEY_SHORTCUT_ICON_SIZE, 100, seekShortcutIconSize);
        addSeekSpec(KEY_SHORTCUT_ICON_CORNER, 16, seekShortcutIconCorner);
        addSeekSpec(KEY_OVERLAY_ALPHA, 50, seekOverlayAlpha);
        addTextSpec(KEY_OVERLAY_COLOR, "#000000", editOverlayColor);
        addSeekSpec(KEY_OVERLAY_ANIM_DURATION, 100, seekOverlayAnimDuration);
        addSwitchSpec(KEY_CLOSE_ON_OUTSIDE_CLICK, true, switchCloseOnOutsideClick);
        addSeekSpec(KEY_ANIM_DURATION, 350, seekAnimDuration);
    }

    private void addSeekSpec(String key, int def, SeekBar seekBar) {
        syncSpecs.put(key, new SyncSpec(key, SyncSpec.TYPE_INT, def,
                seekBar::getProgress,
                value -> seekBar.setProgress((Integer) value)));
    }

    private void addSwitchSpec(String key, boolean def, Switch switchView) {
        syncSpecs.put(key, new SyncSpec(key, SyncSpec.TYPE_BOOLEAN, def,
                switchView::isChecked,
                value -> switchView.setChecked((Boolean) value)));
    }

    private void addTextSpec(String key, String def, EditText editText) {
        syncSpecs.put(key, new SyncSpec(key, SyncSpec.TYPE_STRING, def,
                () -> editText.getText().toString().trim(),
                value -> editText.setText(value == null ? "" : value.toString())));
    }

    private void addPressedColorModeSpec() {
        syncSpecs.put(KEY_PRESSED_BAR_COLOR_MODE,
                new SyncSpec(KEY_PRESSED_BAR_COLOR_MODE, SyncSpec.TYPE_STRING, "rainbow",
                        this::getPressedColorMode,
                        value -> radioPressedColorMode.check("color".equals(value)
                                ? R.id.radio_pressed_color_custom
                                : R.id.radio_pressed_color_rainbow)));
    }

    private void addShortcutStyleSpec() {
        syncSpecs.put(KEY_SHORTCUT_STYLE,
                new SyncSpec(KEY_SHORTCUT_STYLE, SyncSpec.TYPE_INT, 0,
                        () -> switchUseExpandDirectly.isChecked()
                                ? shortcutStyleBeforeFloatMode
                                : spinnerShortcutStyle.getSelectedItemPosition(),
                        value -> {
                            int style = (Integer) value;
                            shortcutStyleBeforeFloatMode = style;
                            if (!switchUseExpandDirectly.isChecked()) {
                                spinnerShortcutStyle.setSelection(style);
                            }
                        }));
    }

    private void setupSyncRows() {
        syncRows.clear();
        advancedSyncRows.clear();
        View content = settingsScroll.getChildAt(0);
        if (content != null) {
            collectSyncRows(content);
        }
        for (String key : syncSpecs.keySet()) {
            if (!syncRows.containsKey(key)) {
                android.util.Log.w("SettingsActivity", "未找到同步行: " + key);
            }
        }
    }

    private void collectSyncRows(View view) {
        if (view instanceof SyncRowLayout) {
            SyncRowLayout row = (SyncRowLayout) view;
            Object tagObject = row.getTag();
            String tag = tagObject == null ? "" : tagObject.toString();
            if (tag.startsWith("adv:")) {
                advancedSyncRows.add(row);
                row.setSyncUiVisible(false);
                row.setLocked(false);
                return;
            }
            SyncSpec spec = syncSpecs.get(tag);
            if (spec != null) {
                spec.row = row;
                syncRows.put(tag, row);
                row.setOnSyncChangedListener((r, checked) -> onSyncRowChanged(spec, checked));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSyncRows(group.getChildAt(i));
            }
        }
    }

    private void onSyncRowChanged(SyncSpec spec, boolean checked) {
        if (suppressCallbacks || discardingChanges) {
            return;
        }
        boolean landscape = OrientationSettings.isLandscape(this);
        captureCurrentValues(landscape);
        spec.synced = checked;
        // 取消同步时横屏初始值沿用当前竖屏值；勾选同步时横屏直接读竖屏值
        spec.landscape = spec.portrait;
        withSuppressedCallbacks(() -> spec.writer.write(spec.current(landscape)));
        applySyncRowStates();
        hasUnsavedChanges = true;
        applyCurrentSettings();
    }

    private void loadSyncSpecsFromPrefs() {
        for (SyncSpec spec : syncSpecs.values()) {
            spec.synced = OrientationSettings.isSynced(preferences, spec.key);
            spec.portrait = readPrefValue(spec, false);
            spec.landscape = spec.synced ? spec.portrait : readPrefValue(spec, true);
        }
    }

    private Object readPrefValue(SyncSpec spec, boolean landscape) {
        boolean useLandscapeValue = landscape && !spec.synced;
        String key = useLandscapeValue ? spec.key + OrientationSettings.LAND_SUFFIX : spec.key;
        if (spec.type == SyncSpec.TYPE_INT) {
            int value = preferences.getInt(key, (Integer) spec.defaultValue);
            if (KEY_SHORTCUT_ICON_COLUMNS.equals(spec.key)) {
                value = Math.max(AppConfig.SHORTCUT_ICON_COLUMNS_MIN,
                        Math.min(AppConfig.SHORTCUT_ICON_COLUMNS_MAX, value));
            } else if (KEY_SHORTCUT_ICON_SIZE.equals(spec.key)) {
                value = Math.max(50, Math.min(100, value));
            }
            return value;
        } else if (spec.type == SyncSpec.TYPE_BOOLEAN) {
            return preferences.getBoolean(key, (Boolean) spec.defaultValue);
        } else {
            String value = preferences.getString(key, (String) spec.defaultValue);
            return value != null ? value : spec.defaultValue;
        }
    }

    private void loadCurrentOrientationIntoControls() {
        boolean landscape = OrientationSettings.isLandscape(this);
        applySyncRowStates();
        for (SyncSpec spec : syncSpecs.values()) {
            Object value = spec.current(landscape);
            if (value != null) {
                spec.writer.write(value);
            }
        }
        applySyncRowStates();
        updatePressedColorInputVisibility();
        updateShortcutStyleVisibility(spinnerShortcutStyle.getSelectedItemPosition());
        updateEdgeSettingsVisibility(switchShortcutEdgeRefraction.isChecked());
        updateFingerGlowSettingsVisibility(switchShortcutFingerGlow.isChecked());
    }

    private void captureCurrentValues(boolean landscape) {
        for (SyncSpec spec : syncSpecs.values()) {
            spec.capture(landscape);
        }
    }

    private void applySyncRowStates() {
        boolean landscape = OrientationSettings.isLandscape(this);
        for (SyncSpec spec : syncSpecs.values()) {
            if (spec.row != null) {
                spec.row.setSyncUiVisible(landscape);
                spec.row.setSynced(spec.synced);
            }
        }
        for (SyncRowLayout row : advancedSyncRows) {
            row.setSyncUiVisible(landscape);
            row.setLocked(landscape);
        }
        if (textLandscapeSyncHint != null) {
            textLandscapeSyncHint.setVisibility(landscape ? View.VISIBLE : View.GONE);
        }
        if (textAdvancedSyncNote != null) {
            textAdvancedSyncNote.setVisibility(landscape ? View.VISIBLE : View.GONE);
        }
    }

    private void withSuppressedCallbacks(Runnable action) {
        boolean previous = suppressCallbacks;
        suppressCallbacks = true;
        suppressCallbacksUntil = System.currentTimeMillis() + 450;
        try {
            action.run();
        } finally {
            suppressCallbacks = previous;
        }
    }

    private void setSyncRowVisibility(String key, boolean visible) {
        SyncRowLayout row = syncRows.get(key);
        if (row != null) {
            row.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void setupOrientationToggle() {
        if (btnOrientationPortrait != null) {
            btnOrientationPortrait.setOnClickListener(v -> requestOrientation(false));
        }
        if (btnOrientationLandscape != null) {
            btnOrientationLandscape.setOnClickListener(v -> requestOrientation(true));
        }
    }

    private void requestOrientation(boolean landscape) {
        if (changingOrientation) return;
        if (OrientationSettings.isLandscape(this) == landscape) return;
        captureCurrentValues(OrientationSettings.isLandscape(this));
        orientationForced = true;
        changingOrientation = true;
        final int seq = ++orientationRequestSeq;
        setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // 系统若未真正触发旋转，避免方向按钮一直处于锁定状态
        pendingApplyHandler.postDelayed(() -> {
            if (seq == orientationRequestSeq && changingOrientation) {
                changingOrientation = false;
            }
        }, 1500);
    }

    private void applyOrientationLayout() {
        if (settingsBody == null || groupTabsContainer == null
                || groupTabsDivider == null || settingsScroll == null) {
            return;
        }
        boolean landscape = OrientationSettings.isLandscape(this);

        settingsBody.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout.LayoutParams tabsLp =
                (LinearLayout.LayoutParams) groupTabsContainer.getLayoutParams();
        if (landscape) {
            tabsLp.width = dpToPx(150);
            tabsLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            tabsLp.weight = 0;
            groupTabsContainer.setOrientation(LinearLayout.VERTICAL);
        } else {
            tabsLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            tabsLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            tabsLp.weight = 0;
            groupTabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        }
        groupTabsContainer.setLayoutParams(tabsLp);
        updateGroupTabsPadding();

        LinearLayout.LayoutParams dividerLp =
                (LinearLayout.LayoutParams) groupTabsDivider.getLayoutParams();
        if (landscape) {
            dividerLp.width = dpToPx(1);
            dividerLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            dividerLp.weight = 0;
        } else {
            dividerLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            dividerLp.height = dpToPx(1);
            dividerLp.weight = 0;
        }
        groupTabsDivider.setLayoutParams(dividerLp);

        LinearLayout.LayoutParams scrollLp =
                (LinearLayout.LayoutParams) settingsScroll.getLayoutParams();
        if (landscape) {
            scrollLp.width = 0;
            scrollLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            scrollLp.weight = 1;
        } else {
            scrollLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            scrollLp.height = 0;
            scrollLp.weight = 1;
        }
        settingsScroll.setLayoutParams(scrollLp);

        TextView[] tabs = {tabTip, tabShortcut, tabAdvanced};
        for (int i = 0; i < tabs.length; i++) {
            TextView tab = tabs[i];
            if (tab == null) continue;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tab.getLayoutParams();
            if (landscape) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = dpToPx(44);
                lp.weight = 0;
                lp.setMargins(0, 0, 0, dpToPx(8));
            } else {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = dpToPx(32);
                lp.weight = 0;
                lp.setMargins(0, 0, i < tabs.length - 1 ? dpToPx(8) : 0, 0);
            }
            tab.setLayoutParams(lp);
            tab.setGravity(Gravity.CENTER);
        }

        updateHeaderPadding();
        updateOrientationToggle(landscape);
        applySyncRowStates();
        lastLandscape = landscape;

        if (orientationLayoutApplied) {
            settingsScroll.post(() -> scrollToGroup(activeGroupIndex));
        }
        orientationLayoutApplied = true;
    }

    private void updateHeaderPadding() {
        if (headerTitleRow == null) return;
        boolean landscape = OrientationSettings.isLandscape(this);
        int baseTop = landscape ? dpToPx(12) : dpToPx(46);
        // 有状态栏 inset 时至少留出 inset + 12dp，避免标题被状态栏遮住
        int top = Math.max(baseTop, statusBarInsetTop + dpToPx(12));
        headerTitleRow.setPadding(dpToPx(16), top, dpToPx(16), dpToPx(12));
    }

    private void updateGroupTabsPadding() {
        if (groupTabsContainer == null) return;
        boolean landscape = OrientationSettings.isLandscape(this);
        int leftInset = navigationBarInsetLeft;
        if (landscape) {
            groupTabsContainer.setPadding(dpToPx(8) + leftInset, dpToPx(12),
                    dpToPx(8), dpToPx(8));
        } else {
            groupTabsContainer.setPadding(dpToPx(16) + leftInset, 0,
                    dpToPx(16), dpToPx(10));
        }
    }

    private void updateNavigationInsets() {
        if (settingsScroll != null) {
            settingsScroll.setPadding(navigationBarInsetLeft, 0,
                    navigationBarInsetRight, navigationBarInsetBottom);
            settingsScroll.setClipToPadding(false);
        }
        updateGroupTabsPadding();
    }

    private void updateOrientationToggle(boolean landscape) {
        if (btnOrientationPortrait == null || btnOrientationLandscape == null) return;
        btnOrientationPortrait.setBackgroundResource(landscape
                ? R.drawable.tab_chip_unselected : R.drawable.tab_chip_selected);
        btnOrientationPortrait.setTextColor(landscape
                ? resolveThemeColor(R.attr.textSecondary) : 0xFFFFFFFF);
        btnOrientationLandscape.setBackgroundResource(landscape
                ? R.drawable.tab_chip_selected : R.drawable.tab_chip_unselected);
        btnOrientationLandscape.setTextColor(landscape
                ? 0xFFFFFFFF : resolveThemeColor(R.attr.textSecondary));
    }

    private int prefInt(String key, int defValue) {
        return OrientationSettings.getInt(this, preferences, key, defValue);
    }

    private String prefString(String key, String defValue) {
        return OrientationSettings.getString(this, preferences, key, defValue);
    }

    private boolean prefBoolean(String key, boolean defValue) {
        return OrientationSettings.getBoolean(this, preferences, key, defValue);
    }

    /**
     * 从 SharedPreferences 加载设置
     */
    private void loadSettings() {
        // 加载 AI URL
        String aiUrl = preferences.getString(KEY_AI_URL, "https://www.doubao.com");
        editAiUrl.setText(aiUrl);
        
        // 加载提示条宽度
        int barWidth = prefInt(KEY_BAR_WIDTH, 355);
        seekBarWidth.setProgress(barWidth);
        textWidth.setText("宽度: " + barWidth + "dp");
        
        // 加载提示条高度
        int barHeight = prefInt(KEY_BAR_HEIGHT, 9);
        seekBarHeight.setProgress(barHeight);
        textHeight.setText("高度: " + barHeight + "dp");
        
        // 加载提示条圆角
        int barCorner = prefInt(KEY_BAR_CORNER, 30);
        seekBarCorner.setProgress(barCorner);
        textCorner.setText("圆角: " + barCorner + "dp");
        
        // 加载 AI 窗口宽度
        int windowWidth = preferences.getInt(KEY_WINDOW_WIDTH, 340);
        windowWidth = Math.max(200, Math.min(600, windowWidth));
        seekWindowWidth.setProgress(windowWidth);
        textWindowWidth.setText("宽度: " + windowWidth + "dp");
        
        // 加载 AI 窗口高度
        int windowHeight = preferences.getInt(KEY_WINDOW_HEIGHT, 450);
        windowHeight = Math.max(300, Math.min(900, windowHeight));
        seekWindowHeight.setProgress(windowHeight);
        textWindowHeight.setText("高度: " + windowHeight + "dp");
        
        int barPosY = prefInt(KEY_BAR_POSITION_Y, 391);
        seekBarPositionY.setProgress(barPosY);
        String posYText = barPosY >= 0 ? "位置 Y: " + barPosY + "dp (向上)" : "位置 Y: " + barPosY + "dp (向下)";
        textPositionY.setText(posYText);
        
        // 加载长按时间
        int longPressDuration = prefInt(KEY_LONG_PRESS_DURATION, 500);
        seekLongPressDuration.setProgress(longPressDuration);
        textLongPressDuration.setText("长按触发时间: " + longPressDuration + "ms");
        
        int windowPosY = preferences.getInt(KEY_WINDOW_POSITION_Y, 231);
        seekWindowPositionY.setProgress(windowPosY);
        textWindowPositionY.setText("窗口位置 Y: " + windowPosY + " (居中)");
        
        // 加载边框设置
        int borderWidth = preferences.getInt(KEY_BORDER_WIDTH, 1);
        seekBorderWidth.setProgress(borderWidth);
        textBorderWidth.setText("边框宽度: " + borderWidth + "dp");
        
        // 加载窗口圆角半径
        int cornerRadius = preferences.getInt(KEY_CORNER_RADIUS, 30);
        seekCornerRadius.setProgress(cornerRadius);
        textCornerRadius.setText("窗口圆角: " + cornerRadius + "dp");
        
        String borderColor = preferences.getString(KEY_BORDER_COLOR, "#4CAF50");
        editBorderColor.setText(borderColor);
        
        int borderAlpha = preferences.getInt(KEY_BORDER_ALPHA, 10);
        seekBorderAlpha.setProgress(borderAlpha);
        textBorderAlpha.setText("边框透明度: " + String.format("%.2f", borderAlpha / 100.0f));

        
        // 加载关闭按钮显示设置
        boolean showCloseButton = preferences.getBoolean(KEY_SHOW_CLOSE_BUTTON, true);
        switchShowCloseButton.setChecked(showCloseButton);
        
        // 加载显示刷新按钮设置
        boolean showRefreshButton = preferences.getBoolean(KEY_SHOW_REFRESH_BUTTON, true);
        switchShowRefreshButton.setChecked(showRefreshButton);
        
        // 加载显示后退按钮设置
        boolean showBackButton = preferences.getBoolean(KEY_SHOW_BACK_BUTTON, true);
        switchShowBackButton.setChecked(showBackButton);
        
        // 加载点击窗口外关闭窗口设置
        boolean closeOnOutsideClick = prefBoolean(KEY_CLOSE_ON_OUTSIDE_CLICK, true);
        switchCloseOnOutsideClick.setChecked(closeOnOutsideClick);
        
        // 加载侧边返回手势设置
        boolean backPrioritizeWebview = preferences.getBoolean(KEY_BACK_PRIORITIZE_WEBVIEW, false);
        switchBackPrioritizeWebview.setChecked(backPrioritizeWebview);
        
        // 提示条触发行为已搬到首页，由 TriggerActionsController 负责
        
        // 加载输入法避让设置
        int keyboardOffset = preferences.getInt(KEY_KEYBOARD_OFFSET, 397);
        seekKeyboardOffset.setProgress(keyboardOffset);
        textKeyboardOffset.setText("窗口上移距离: " + keyboardOffset + "px");
        
        int keyboardAnimDuration = preferences.getInt(KEY_KEYBOARD_ANIM_DURATION, 250);
        seekKeyboardAnimDuration.setProgress(keyboardAnimDuration);
        textKeyboardAnimDuration.setText("平移动画时长: " + keyboardAnimDuration + "ms");
        
        // 加载动画速度
        int animDuration = prefInt(KEY_ANIM_DURATION, 350);
        seekAnimDuration.setProgress(animDuration);
        textAnimDuration.setText("动画速度: " + animDuration + "ms");
        
        // 加载按下状态样式
        boolean pressedEnabled = prefBoolean(KEY_PRESSED_BAR_ENABLED, true);
        switchPressedBarEnabled.setChecked(pressedEnabled);
        
        int pressedWidth = prefInt(KEY_PRESSED_BAR_WIDTH, 420);
        seekPressedBarWidth.setProgress(pressedWidth);
        textPressedBarWidth.setText("按下时宽度: " + pressedWidth + "dp");
        
        int pressedHeight = prefInt(KEY_PRESSED_BAR_HEIGHT, 20);
        seekPressedBarHeight.setProgress(pressedHeight);
        textPressedBarHeight.setText("按下时高度: " + pressedHeight + "dp");
        
        int pressedCorner = prefInt(KEY_PRESSED_BAR_CORNER, 45);
        seekPressedBarCorner.setProgress(pressedCorner);
        textPressedBarCorner.setText("按下时圆角: " + pressedCorner + "dp");
        
        int pressedAlpha = prefInt(KEY_PRESSED_BAR_ALPHA, 100);
        seekPressedBarAlpha.setProgress(pressedAlpha);
        textPressedBarAlpha.setText("按下时透明度: " + String.format("%.2f", pressedAlpha / 100.0f));
        
        String pressedColor = prefString(KEY_PRESSED_BAR_COLOR, "#0099dd");
        editPressedBarColor.setText(pressedColor);
        
        // 加载羽化宽度
        int pressedFeather = prefInt(KEY_PRESSED_BAR_FEATHER, 10);
        seekPressedBarFeather.setProgress(pressedFeather);
        textPressedBarFeather.setText("边缘羽化宽度: " + pressedFeather + "dp");
        
        // 加载按下时位置Y
        int pressedPosY = prefInt(KEY_PRESSED_BAR_POSITION_Y, 0);
        seekPressedBarPositionY.setProgress(pressedPosY);
        String pressedPosYText = pressedPosY >= 0 ? "按下时位置Y: " + pressedPosY + "dp (向上)" : "按下时位置Y: " + pressedPosY + "dp (向下)";
        textPressedBarPositionY.setText(pressedPosYText);

        // 加载光晕半径
        int pressedGlow = prefInt(KEY_PRESSED_BAR_GLOW_RADIUS, 25);
        seekPressedBarGlowRadius.setProgress(pressedGlow);
        textPressedBarGlowRadius.setText(pressedGlow == 0 ? "光晕半径: 关闭" : "光晕半径: " + pressedGlow + "dp");

        // 加载颜色模式
        String colorMode = prefString(KEY_PRESSED_BAR_COLOR_MODE, "rainbow");
        radioPressedColorMode.check("color".equals(colorMode) ? R.id.radio_pressed_color_custom : R.id.radio_pressed_color_rainbow);
        updatePressedColorInputVisibility();
        
        // 加载按下时位置Y
        // 加载背景遮罩设置
        int overlayAlpha = prefInt(KEY_OVERLAY_ALPHA, 50);
        seekOverlayAlpha.setProgress(overlayAlpha);
        textOverlayAlpha.setText("遮罩透明度: " + overlayAlpha + "%");
        
        String overlayColor = prefString(KEY_OVERLAY_COLOR, "#000000");
        editOverlayColor.setText(overlayColor);
        
        int overlayAnimDuration = prefInt(KEY_OVERLAY_ANIM_DURATION, 100);
        seekOverlayAnimDuration.setProgress(overlayAnimDuration);
        textOverlayAnimDuration.setText("遮罩渐变时长: " + overlayAnimDuration + "ms");
        
        // 加载快捷方式窗口设置
        int shortcutWinWidth = prefInt(KEY_SHORTCUT_WINDOW_WIDTH, 350);
        seekShortcutWindowWidth.setProgress(shortcutWinWidth);
        textShortcutWindowWidth.setText("快捷窗口宽度: " + shortcutWinWidth + "dp");
        
        int shortcutWinHeight = prefInt(KEY_SHORTCUT_WINDOW_HEIGHT, 350);
        seekShortcutWindowHeight.setProgress(shortcutWinHeight);
        textShortcutWindowHeight.setText("快捷窗口最大高度: " + shortcutWinHeight + "dp");
        
        int shortcutWinPosY = prefInt(KEY_SHORTCUT_WINDOW_POSITION_Y, 231);
        seekShortcutWindowPositionY.setProgress(shortcutWinPosY);
        textShortcutWindowPositionY.setText("快捷窗口位置 Y: " + shortcutWinPosY + " (居中)");
        
        int shortcutCRadius = prefInt(KEY_SHORTCUT_CORNER_RADIUS, 24);
        seekShortcutCornerRadius.setProgress(shortcutCRadius);
        textShortcutCornerRadius.setText("快捷窗口圆角: " + shortcutCRadius + "dp");

        int shortcutIconColumns = prefInt(
                KEY_SHORTCUT_ICON_COLUMNS, AppConfig.DEFAULT_SHORTCUT_ICON_COLUMNS);
        shortcutIconColumns = Math.max(AppConfig.SHORTCUT_ICON_COLUMNS_MIN,
                Math.min(AppConfig.SHORTCUT_ICON_COLUMNS_MAX, shortcutIconColumns));
        seekShortcutIconColumns.setProgress(shortcutIconColumns);
        textShortcutIconColumns.setText("图标列数: " + shortcutIconColumns);

        int shortcutIconSize = prefInt(KEY_SHORTCUT_ICON_SIZE, 100);
        shortcutIconSize = Math.max(50, Math.min(100, shortcutIconSize));
        seekShortcutIconSize.setProgress(shortcutIconSize);
        textShortcutIconSize.setText("图标大小: " + shortcutIconSize + "%");
        int shortcutIconCorner = prefInt(KEY_SHORTCUT_ICON_CORNER, 16);
        seekShortcutIconCorner.setProgress(shortcutIconCorner);
        textShortcutIconCorner.setText("图标圆角: " + shortcutIconCorner + "%");

        int style = prefInt(KEY_SHORTCUT_STYLE, 0);
        shortcutStyleBeforeFloatMode = style;
        spinnerShortcutStyle.setSelection(style);
        spinnerBlurSyncMode.setSelection(preferences.getInt(KEY_BLUR_SYNC_MODE, 0));
        int blurRadius = prefInt(KEY_SHORTCUT_BLUR_RADIUS, 20);
        seekShortcutBlurRadius.setProgress(blurRadius);
        textShortcutBlurRadius.setText("模糊半径: " + blurRadius + "dp");
        int overlayAlpha1 = prefInt(KEY_SHORTCUT_OVERLAY_ALPHA, 180);
        seekShortcutOverlayAlpha.setProgress(overlayAlpha1);
        textShortcutOverlayAlpha.setText("叠加色透明度: " + (overlayAlpha1 * 100 / 255) + "%");
        updateShortcutStyleVisibility(style);

        // 加载 ColorOS 风格效果开关（默认开启）
        switchShortcutEdgeRefraction.setChecked(prefBoolean("shortcut_edge_refraction", true));
        switchShortcutFingerGlow.setChecked(prefBoolean("shortcut_finger_glow", true));
        switchShortcutDragStretch.setChecked(prefBoolean("shortcut_drag_stretch", true));

        // 加载边缘光参数（默认 4dp / 48%）
        int edgeBand = prefInt(KEY_SHORTCUT_EDGE_BAND, 4);
        seekShortcutEdgeBand.setProgress(edgeBand);
        textShortcutEdgeBand.setText("边缘光宽度: " + edgeBand + "dp");
        int edgeOpacity = prefInt(KEY_SHORTCUT_EDGE_OPACITY, 48);
        seekShortcutEdgeOpacity.setProgress(edgeOpacity);
        textShortcutEdgeOpacity.setText("边缘光透明度: " + edgeOpacity + "%");
        updateEdgeSettingsVisibility(switchShortcutEdgeRefraction.isChecked());
        // 加载手指光晕参数
        int glowBrightness = prefInt(KEY_FINGER_GLOW_BRIGHTNESS, 27);
        seekFingerGlowBrightness.setProgress(glowBrightness);
        textFingerGlowBrightness.setText("光晕亮度: " + glowBrightness + "%");
        int glowSize = prefInt(KEY_FINGER_GLOW_SIZE, 100);
        seekFingerGlowSize.setProgress(glowSize);
        textFingerGlowSize.setText("光晕范围: " + glowSize + "%");
        updateFingerGlowSettingsVisibility(switchShortcutFingerGlow.isChecked());
        
        // 加载 AI 窗口模糊效果设置
        int aiStyle = preferences.getInt(KEY_AI_STYLE, 0);
        spinnerAiStyle.setSelection(aiStyle);
        updateAiStyleVisibility(aiStyle);
        
        int aiBlurRadius = preferences.getInt(KEY_AI_BLUR_RADIUS, 25);
        seekAiBlurRadius.setProgress(aiBlurRadius);
        textAiBlurRadius.setText("AI 窗口模糊半径: " + aiBlurRadius + "dp");
        
        int aiOverlayAlpha = preferences.getInt(KEY_AI_OVERLAY_ALPHA, 204);
        // 当样式为不透明时，强制显示 100%
        if (aiStyle == 0) {
            aiOverlayAlpha = 255;
            seekAiOverlayAlpha.setProgress(255);
        } else {
            seekAiOverlayAlpha.setProgress(aiOverlayAlpha);
        }
        textAiOverlayAlpha.setText("网页透明度: " + (aiOverlayAlpha * 100 / 255) + "%");
        
        // 加载自定义 CSS
        String customCss = preferences.getString(KEY_CUSTOM_CSS, "");
        editCustomCss.setText(customCss);
        
        // 加载网页背景透明设置
        boolean webpageBgTransparent = preferences.getBoolean(KEY_WEBPAGE_BG_TRANSPARENT, false);
        switchWebpageBgTransparent.setChecked(webpageBgTransparent);

        boolean useExpand = preferences.getBoolean("use_expand_directly", false);
        switchUseExpandDirectly.setChecked(useExpand);
        updateFloatingWindowModeRestrictions(useExpand);
        switchTryFullscreenWindowing.setChecked(
                preferences.getBoolean("try_fullscreen_windowing", false));
        // 自定义 windowingMode（留空 = 默认 6）
        editCustomWindowingMode.setText(preferences.getString("custom_windowing_mode", ""));
        // 开关状态加载后刷新输入框可见性（applyFeatureFlagVisibility 执行时开关尚未加载）
        updateCustomWindowingModeVisibility();
        
        // 加载顶部小横条透明度
        int topBarTransparency = preferences.getInt(KEY_TOP_BAR_TRANSPARENCY, 40);
        seekTopBarTransparency.setProgress(topBarTransparency);
        textTopBarTransparency.setText("小横条透明度: " + topBarTransparency + "%");

        // 初始化双方向内存模型与勾选框状态
        loadSyncSpecsFromPrefs();
        applySyncRowStates();
    }
    
    /**
     * 保存设置到 SharedPreferences。
     * 这是本次页面会话中唯一真正落盘的入口；其它修改只下发服务。
     */
    private void saveSettings() {
        persistAllSettings();
        pendingApplyHandler.removeCallbacks(pendingApplyRunnable);
        applyCurrentSettings();
        hasUnsavedChanges = false;
        Toast.makeText(this, "设置已保存并应用", Toast.LENGTH_SHORT).show();
    }

    private void persistAllSettings() {
        // 先把当前方向的最新控件值收进内存模型
        captureCurrentValues(OrientationSettings.isLandscape(this));
        SharedPreferences.Editor editor = preferences.edit();

        // 非方向同步项：始终保存竖屏值（横屏下这些控件被锁定为竖屏值）
        editor.putString(KEY_AI_URL, getAiUrlSafe());

        // 保存 AI 窗口大小
        editor.putInt(KEY_WINDOW_WIDTH, seekWindowWidth.getProgress());
        editor.putInt(KEY_WINDOW_HEIGHT, seekWindowHeight.getProgress());
        editor.putInt(KEY_WINDOW_POSITION_Y, seekWindowPositionY.getProgress());

        // 保存边框和阴影设置
        editor.putInt(KEY_BORDER_WIDTH, seekBorderWidth.getProgress());
        editor.putInt(KEY_CORNER_RADIUS, seekCornerRadius.getProgress());
        editor.putString(KEY_BORDER_COLOR, getBorderColorSafe());
        editor.putInt(KEY_BORDER_ALPHA, seekBorderAlpha.getProgress());

        // 保存关闭按钮显示设置
        editor.putBoolean(KEY_SHOW_CLOSE_BUTTON, switchShowCloseButton.isChecked());

        // 保存显示刷新按钮设置
        editor.putBoolean(KEY_SHOW_REFRESH_BUTTON, switchShowRefreshButton.isChecked());

        // 保存显示后退按钮设置
        editor.putBoolean(KEY_SHOW_BACK_BUTTON, switchShowBackButton.isChecked());

        // 保存侧边返回手势设置
        editor.putBoolean(KEY_BACK_PRIORITIZE_WEBVIEW, switchBackPrioritizeWebview.isChecked());

        // 保存输入法避让设置
        editor.putInt(KEY_KEYBOARD_OFFSET, seekKeyboardOffset.getProgress());
        editor.putInt(KEY_KEYBOARD_ANIM_DURATION, seekKeyboardAnimDuration.getProgress());

        // 保存高级 / AI 设置（这些项不参与横屏独立配置）
        editor.putInt(KEY_AI_STYLE, spinnerAiStyle.getSelectedItemPosition());

        int aiOverlayAlphaToSave = seekAiOverlayAlpha.getProgress();
        // 当样式为不透明时，强制保存 100%
        if (spinnerAiStyle.getSelectedItemPosition() == 0) {
            aiOverlayAlphaToSave = 255;
        }

        editor.putInt(KEY_AI_BLUR_RADIUS, seekAiBlurRadius.getProgress());
        editor.putInt(KEY_AI_OVERLAY_ALPHA, aiOverlayAlphaToSave);

        // 保存自定义 CSS
        editor.putString(KEY_CUSTOM_CSS, editCustomCss.getText().toString().trim());

        // 保存网页背景透明设置
        editor.putBoolean(KEY_WEBPAGE_BG_TRANSPARENT, switchWebpageBgTransparent.isChecked());

        // 保存顶部小横条透明度
        editor.putInt(KEY_TOP_BAR_TRANSPARENCY, seekTopBarTransparency.getProgress());

        editor.putInt(KEY_BLUR_SYNC_MODE, spinnerBlurSyncMode.getSelectedItemPosition());
        editor.putBoolean("use_expand_directly", switchUseExpandDirectly.isChecked());
        editor.putBoolean("try_fullscreen_windowing", switchTryFullscreenWindowing.isChecked());
        editor.putString("custom_windowing_mode", editCustomWindowingMode.getText().toString().trim());

        // 37 个方向同步项：竖屏值 + 同步标记 +（取消同步时）横屏独立值
        for (SyncSpec spec : syncSpecs.values()) {
            writeSyncSpec(editor, spec);
        }

        editor.apply();
    }

    private void writeSyncSpec(SharedPreferences.Editor editor, SyncSpec spec) {
        Object portraitValue = normalizeSyncValue(spec, spec.portrait);
        Object landscapeValue = normalizeSyncValue(spec, spec.landscape);
        if (spec.type == SyncSpec.TYPE_INT) {
            editor.putInt(spec.key, (Integer) portraitValue);
            if (spec.synced) {
                OrientationSettings.markSynced(editor, spec.key);
            } else {
                editor.putInt(spec.key + OrientationSettings.LAND_SUFFIX,
                        (Integer) landscapeValue);
                editor.putBoolean(spec.key + OrientationSettings.SYNC_SUFFIX, false);
            }
        } else if (spec.type == SyncSpec.TYPE_BOOLEAN) {
            editor.putBoolean(spec.key, (Boolean) portraitValue);
            if (spec.synced) {
                OrientationSettings.markSynced(editor, spec.key);
            } else {
                editor.putBoolean(spec.key + OrientationSettings.LAND_SUFFIX,
                        (Boolean) landscapeValue);
                editor.putBoolean(spec.key + OrientationSettings.SYNC_SUFFIX, false);
            }
        } else {
            editor.putString(spec.key, (String) portraitValue);
            if (spec.synced) {
                OrientationSettings.markSynced(editor, spec.key);
            } else {
                editor.putString(spec.key + OrientationSettings.LAND_SUFFIX,
                        (String) landscapeValue);
                editor.putBoolean(spec.key + OrientationSettings.SYNC_SUFFIX, false);
            }
        }
    }

    private Object normalizeSyncValue(SyncSpec spec, Object value) {
        if (value == null) return spec.defaultValue;
        if (spec.type == SyncSpec.TYPE_INT) {
            int intValue = (Integer) value;
            if (KEY_SHORTCUT_ICON_COLUMNS.equals(spec.key)) {
                intValue = clamp(intValue, AppConfig.SHORTCUT_ICON_COLUMNS_MIN,
                        AppConfig.SHORTCUT_ICON_COLUMNS_MAX);
            } else if (KEY_SHORTCUT_ICON_SIZE.equals(spec.key)) {
                intValue = clamp(intValue, 50, 100);
            }
            return intValue;
        }
        if (spec.type == SyncSpec.TYPE_STRING) {
            String stringValue = value.toString().trim();
            if (KEY_PRESSED_BAR_COLOR.equals(spec.key)) {
                return isValidColor(stringValue) ? stringValue : "#333333";
            }
            if (KEY_OVERLAY_COLOR.equals(spec.key)) {
                return isValidColor(stringValue) ? stringValue : "#000000";
            }
            if (KEY_PRESSED_BAR_COLOR_MODE.equals(spec.key)) {
                return "color".equals(stringValue) ? "color" : "rainbow";
            }
            return stringValue;
        }
        return value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isValidColor(String value) {
        return !TextUtils.isEmpty(value) && value.startsWith("#");
    }

    /**
     * 设置事件监听器
     */
    private void setupListeners() {
        // 提示条宽度调节 - 实时更新
        seekBarWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textWidth.setText("宽度: " + progress + "dp");
                onSettingChanged(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 提示条高度调节 - 实时更新
        seekBarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textHeight.setText("高度: " + progress + "dp");
                onSettingChanged(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 提示条圆角调节 - 实时更新
        seekBarCorner.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textCorner.setText("圆角: " + progress + "dp");
                onSettingChanged(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 提示条位置 Y - 实时更新
        seekBarPositionY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String posYText = progress >= 0 ? "位置 Y: " + progress + "dp (向上)" : "位置 Y: " + progress + "dp (向下)";
                textPositionY.setText(posYText);
                onSettingChanged(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 长按触发时间 - 实时更新
        seekLongPressDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textLongPressDuration.setText("长按触发时间: " + progress + "ms");
                onSettingChanged(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        

        // AI 窗口宽度 - 实时更新
        seekWindowWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textWindowWidth.setText("宽度: " + progress + "dp");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // AI 窗口高度 - 实时更新
        seekWindowHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textWindowHeight.setText("高度: " + progress + "dp");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // AI 窗口位置 Y - 实时更新
        seekWindowPositionY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textWindowPositionY.setText("窗口位置 Y: " + progress + " (居中)");
                onSettingChanged(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 边框宽度 - 实时更新
        seekBorderWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textBorderWidth.setText("边框宽度: " + progress + "dp");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 窗口圆角半径 - 实时更新
        seekCornerRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textCornerRadius.setText("窗口圆角: " + progress + "dp");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 边框透明度 - 实时更新
        seekBorderAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textBorderAlpha.setText("边框透明度: " + String.format("%.2f", progress / 100.0f));
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        
        // 输入法上移距离 - 实时更新
        seekKeyboardOffset.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textKeyboardOffset.setText("窗口上移距离: " + progress + "px");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 输入法动画时长 - 实时更新
        seekKeyboardAnimDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textKeyboardAnimDuration.setText("平移动画时长: " + progress + "ms");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 展开/收起动画速度 - 实时更新
        seekAnimDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textAnimDuration.setText("动画速度: " + progress + "ms");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时启用开关 - 实时更新
        switchPressedBarEnabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                onSettingChanged();
                if (isChecked) {
                    triggerPressedPreview();
                }
            }
        });
        
        // 按下时宽度 - 实时更新
        seekPressedBarWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPressedBarWidth.setText("按下时宽度: " + progress + "dp");
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时高度 - 实时更新
        seekPressedBarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPressedBarHeight.setText("按下时高度: " + progress + "dp");
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时圆角 - 实时更新
        seekPressedBarCorner.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPressedBarCorner.setText("按下时圆角: " + progress + "dp");
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时透明度 - 实时更新
        seekPressedBarAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = progress / 100.0f;
                textPressedBarAlpha.setText("按下时透明度: " + String.format("%.2f", alpha));
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 边缘羽化宽度 - 实时更新
        seekPressedBarFeather.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPressedBarFeather.setText("边缘羽化宽度: " + progress + "dp");
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时位置Y - 实时更新
        seekPressedBarPositionY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                String posYText = progress >= 0 ? "按下时位置Y: " + progress + "dp (向上)" : "按下时位置Y: " + progress + "dp (向下)";
                textPressedBarPositionY.setText(posYText);
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 光晕半径 - 实时更新
        seekPressedBarGlowRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPressedBarGlowRadius.setText(progress == 0 ? "光晕半径: 关闭" : "光晕半径: " + progress + "dp");
                onSettingChanged();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 颜色模式 - 切换即时预览
        radioPressedColorMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                updatePressedColorInputVisibility();
                onSettingChanged();
                triggerPressedPreview();
            }
        });

        // 按下时颜色编辑框 TextWatcher（实时预览）
        editPressedBarColor.addTextChangedListener(new android.text.TextWatcher() {
            private Handler textHandler = new Handler(Looper.getMainLooper());
            private Runnable textTimeout;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 输入颜色后立刻离开时也要弹保存确认，不能等 300ms 防抖回调
                if (uiInitialized && !suppressCallbacks && !discardingChanges) {
                    hasUnsavedChanges = true;
                }
                if (textTimeout != null) {
                    textHandler.removeCallbacks(textTimeout);
                }
                textTimeout = new Runnable() {
                    @Override
                    public void run() {
                        // 方向切换/放弃修改时正在回填控件，延迟回调不应触发预览
                        if (System.currentTimeMillis() < suppressCallbacksUntil) return;
                        onTextSettingChanged();
                        triggerPressedPreview();
                    }
                };
                textHandler.postDelayed(textTimeout, 300);
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        // 背景遮罩透明度 - 实时更新
        seekOverlayAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOverlayAlpha.setText("遮罩透明度: " + progress + "%");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 背景遮罩渐变时长 - 实时更新
        seekOverlayAnimDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOverlayAnimDuration.setText("遮罩渐变时长: " + progress + "ms");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 快捷方式窗口宽度 - 实时更新
        seekShortcutWindowWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutWindowWidth.setText("快捷窗口宽度: " + progress + "dp");
                onSettingChanged();
                if (fromUser) refreshWindowOutlinePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { refreshWindowOutlinePreview(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { scheduleHideWindowOutlinePreview(); }
        });
        
        // 快捷方式窗口高度 - 实时更新
        seekShortcutWindowHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutWindowHeight.setText("快捷窗口最大高度: " + progress + "dp");
                onSettingChanged();
                if (fromUser) refreshWindowOutlinePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { refreshWindowOutlinePreview(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { scheduleHideWindowOutlinePreview(); }
        });
        
        // 快捷方式窗口位置 Y - 实时更新
        seekShortcutWindowPositionY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutWindowPositionY.setText("快捷窗口位置 Y: " + progress + " (居中)");
                onSettingChanged();
                if (fromUser) refreshWindowOutlinePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { refreshWindowOutlinePreview(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { scheduleHideWindowOutlinePreview(); }
        });
        
        // 快捷窗口圆角 - 实时更新
        seekShortcutCornerRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutCornerRadius.setText("快捷窗口圆角: " + progress + "dp");
                onSettingChanged();
                if (fromUser) refreshWindowOutlinePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { refreshWindowOutlinePreview(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { scheduleHideWindowOutlinePreview(); }
        });
        
        // 快捷方式图标列数 - 实时更新
        seekShortcutIconColumns.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutIconColumns.setText("图标列数: " + progress);
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 快捷方式图标大小 - 实时更新
        seekShortcutIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutIconSize.setText("图标大小: " + progress + "%");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 快捷方式图标圆角 - 实时更新
        seekShortcutIconCorner.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutIconCorner.setText("图标圆角: " + progress + "%");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 边缘光 / 手指光晕参数
        seekShortcutEdgeBand.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutEdgeBand.setText("边缘光宽度: " + progress + "dp");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekShortcutEdgeOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutEdgeOpacity.setText("边缘光透明度: " + progress + "%");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekFingerGlowBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textFingerGlowBrightness.setText("光晕亮度: " + progress + "%");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekFingerGlowSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textFingerGlowSize.setText("光晕范围: " + progress + "%");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        // 边缘折射光开关控制边缘光参数显隐
        switchShortcutEdgeRefraction.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateEdgeSettingsVisibility(isChecked);
            onSettingChanged();
        });
        // 手指光晕开关控制光晕参数显隐
        switchShortcutFingerGlow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateFingerGlowSettingsVisibility(isChecked);
            onSettingChanged();
        });
        switchShortcutDragStretch.setOnCheckedChangeListener((buttonView, isChecked) ->
                onSettingChanged());

        // 保存并应用按钮
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        
        // 尝试消除授权弹窗开关 - 实时下发（手势路径）
        switchTryFullscreenWindowing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateCustomWindowingModeVisibility(); // 开关开启时显示自定义 windowingMode 输入框
            onSettingChanged();
        });

        // 悬浮窗模式：开启后快捷窗口固定不透明，透明相关设置置灰
        switchUseExpandDirectly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateFloatingWindowModeRestrictions(isChecked);
            onSettingChanged();
        });
        
        spinnerShortcutStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateShortcutStyleVisibility(position);
                if (!switchUseExpandDirectly.isChecked()) {
                    shortcutStyleBeforeFloatMode = position;
                }
                onSettingChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerBlurSyncMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSettingChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        seekShortcutBlurRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutBlurRadius.setText("模糊半径: " + progress + "dp");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekShortcutOverlayAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textShortcutOverlayAlpha.setText("叠加色透明度: " + (progress * 100 / 255) + "%");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // AI 窗口模糊效果监听器
        seekAiBlurRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textAiBlurRadius.setText("AI 窗口模糊半径: " + progress + "dp");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekAiOverlayAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textAiOverlayAlpha.setText("网页透明度: " + (progress * 100 / 255) + "%");
                if (fromUser) onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        spinnerAiStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateAiStyleVisibility(position);
                // 当样式为不透明时，强制设置透明度为 100%
                if (position == 0) {
                    seekAiOverlayAlpha.setProgress(255);
                    textAiOverlayAlpha.setText("网页透明度: 100%");
                }
                onSettingChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // 网页背景透明开关监听器
        switchWebpageBgTransparent.setOnCheckedChangeListener((buttonView, isChecked) ->
                onSettingChanged());

        // 顶部小横条透明度 - 实时更新
        seekTopBarTransparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textTopBarTransparency.setText("小横条透明度: " + progress + "%");
                onSettingChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 其余开关 / 文本设置：即时下发服务，但不写 prefs
        switchShowCloseButton.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());
        switchShowRefreshButton.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());
        switchShowBackButton.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());
        switchCloseOnOutsideClick.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());
        switchBackPrioritizeWebview.setOnCheckedChangeListener((buttonView, isChecked) -> onSettingChanged());

        addDebouncedTextWatcher(editAiUrl);
        addDebouncedTextWatcher(editBorderColor);
        addDebouncedTextWatcher(editOverlayColor);
        addDebouncedTextWatcher(editCustomCss);
        addDebouncedTextWatcher(editCustomWindowingMode);
    }

    /** 文本输入防抖：停止输入 350ms 后再下发，避免逐字刷新。 */
    private void addDebouncedTextWatcher(EditText editText) {
        if (editText == null) return;
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onTextSettingChanged();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void updateShortcutStyleVisibility(int style) {
        boolean show = (style != 0);
        textShortcutBlurRadius.setVisibility(show ? View.VISIBLE : View.GONE);
        seekShortcutBlurRadius.setVisibility(show ? View.VISIBLE : View.GONE);
        textShortcutOverlayAlpha.setVisibility(show ? View.VISIBLE : View.GONE);
        seekShortcutOverlayAlpha.setVisibility(show ? View.VISIBLE : View.GONE);
        // 整行一起隐藏，避免横屏只剩一个没有内容的同步勾选框
        setSyncRowVisibility(KEY_SHORTCUT_BLUR_RADIUS, show);
        setSyncRowVisibility(KEY_SHORTCUT_OVERLAY_ALPHA, show);
    }

    /**
     * 悬浮窗模式限制：快捷窗口固定不透明，背景样式与透明/动效相关设置置灰。
     * 只改变界面展示和当前有效样式，不覆盖用户保存的背景样式，关闭悬浮窗模式后恢复。
     */
    private void updateFloatingWindowModeRestrictions(boolean enabled) {
        if (enabled) {
            if (!floatingModeRestricted) {
                int current = spinnerShortcutStyle.getSelectedItemPosition();
                if (current < 0) {
                    current = shortcutStyleBeforeFloatMode;
                }
                shortcutStyleBeforeFloatMode = current;
                floatingModeRestricted = true;
            }
            if (spinnerShortcutStyle.getSelectedItemPosition() != 0) {
                spinnerShortcutStyle.setSelection(0);
            }
            updateShortcutStyleVisibility(0);
            setShortcutStyleControlsEnabled(false);
        } else {
            if (floatingModeRestricted) {
                spinnerShortcutStyle.setSelection(shortcutStyleBeforeFloatMode);
                updateShortcutStyleVisibility(shortcutStyleBeforeFloatMode);
                floatingModeRestricted = false;
            }
            setShortcutStyleControlsEnabled(true);
        }
    }

    /**
     * 统一置灰/恢复快捷窗口的透明与动效设置。
     * 置灰时禁用实际控件，并在行/参数容器上挂点击拦截，点击时提示不支持。
     */
    private void setShortcutStyleControlsEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.5f;
        if (textShortcutStyleLabel != null) {
            textShortcutStyleLabel.setAlpha(alpha);
            setUnsupportedClickInterceptor(textShortcutStyleLabel, !enabled);
        }
        if (containerShortcutStyle != null) {
            containerShortcutStyle.setAlpha(alpha);
            setUnsupportedClickInterceptor(containerShortcutStyle, !enabled);
        }
        setControlEnabled(spinnerShortcutStyle, enabled);

        if (rowShortcutEdgeRefraction != null) {
            rowShortcutEdgeRefraction.setAlpha(alpha);
            setUnsupportedClickInterceptor(rowShortcutEdgeRefraction, !enabled);
        }
        setControlEnabled(switchShortcutEdgeRefraction, enabled);
        if (containerShortcutEdgeSettings != null) {
            containerShortcutEdgeSettings.setAlpha(alpha);
            setUnsupportedClickInterceptor(containerShortcutEdgeSettings, !enabled);
        }
        setControlEnabled(seekShortcutEdgeBand, enabled);
        setControlEnabled(seekShortcutEdgeOpacity, enabled);

        if (rowShortcutFingerGlow != null) {
            rowShortcutFingerGlow.setAlpha(alpha);
            setUnsupportedClickInterceptor(rowShortcutFingerGlow, !enabled);
        }
        setControlEnabled(switchShortcutFingerGlow, enabled);
        if (containerFingerGlowSettings != null) {
            containerFingerGlowSettings.setAlpha(alpha);
            setUnsupportedClickInterceptor(containerFingerGlowSettings, !enabled);
        }
        setControlEnabled(seekFingerGlowBrightness, enabled);
        setControlEnabled(seekFingerGlowSize, enabled);

        if (rowShortcutDragStretch != null) {
            rowShortcutDragStretch.setAlpha(alpha);
            setUnsupportedClickInterceptor(rowShortcutDragStretch, !enabled);
        }
        setControlEnabled(switchShortcutDragStretch, enabled);
    }

    /**
     * 禁用时同时关闭 clickable，避免禁用控件吞掉触摸导致外层提示不触发。
     */
    private void setControlEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setClickable(enabled);
    }

    private void setUnsupportedClickInterceptor(View view, boolean disabled) {
        if (disabled) {
            view.setOnClickListener(v -> showFloatingWindowUnsupportedToast());
        } else {
            view.setOnClickListener(null);
            view.setClickable(false);
        }
    }

    private void showFloatingWindowUnsupportedToast() {
        Toast.makeText(this, "悬浮窗模式暂不支持该功能", Toast.LENGTH_SHORT).show();
    }

    /**
     * 边缘光参数仅在边缘折射光开启时显示
     */
    private void updateEdgeSettingsVisibility(boolean enabled) {
        if (containerShortcutEdgeSettings != null) {
            containerShortcutEdgeSettings.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 手指光晕参数仅在手指光晕开启时显示
     */
    private void updateFingerGlowSettingsVisibility(boolean enabled) {
        if (containerFingerGlowSettings != null) {
            containerFingerGlowSettings.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }
    
    private void updateAiStyleVisibility(int style) {
        boolean show = (style != 0);
        textAiBlurRadius.setVisibility(show ? View.VISIBLE : View.GONE);
        seekAiBlurRadius.setVisibility(show ? View.VISIBLE : View.GONE);
        textAiOverlayAlpha.setVisibility(show ? View.VISIBLE : View.GONE);
        seekAiOverlayAlpha.setVisibility(show ? View.VISIBLE : View.GONE);
        // 网页背景透明开关仅在亚克力(1)或液态玻璃(2)样式时显示
        boolean showWebpageBgTransparent = (style == 1 || style == 2);
        switchWebpageBgTransparent.setVisibility(showWebpageBgTransparent ? View.VISIBLE : View.GONE);
    }
    
    /**
     * 实时应用当前设置（不保存到 SharedPreferences）
     */
    private void applyCurrentSettings() {
        // 立即发送更新到服务
        Intent styleIntent = new Intent(this, FloatWindowService.class);
        styleIntent.setAction("UPDATE_STYLE");
        styleIntent.putExtra("width", seekBarWidth.getProgress());
        styleIntent.putExtra("height", seekBarHeight.getProgress());
        styleIntent.putExtra("corner", seekBarCorner.getProgress());
        styleIntent.putExtra("position_y", seekBarPositionY.getProgress());
        styleIntent.putExtra("long_press_duration", seekLongPressDuration.getProgress());
        styleIntent.putExtra("pressed_bar_enabled", switchPressedBarEnabled.isChecked());
        styleIntent.putExtra("pressed_bar_color", getPressedBarColorSafe());
        styleIntent.putExtra("pressed_bar_alpha", seekPressedBarAlpha.getProgress());
        styleIntent.putExtra("pressed_bar_width", seekPressedBarWidth.getProgress());
        styleIntent.putExtra("pressed_bar_height", seekPressedBarHeight.getProgress());
        styleIntent.putExtra("pressed_bar_corner", seekPressedBarCorner.getProgress());
        styleIntent.putExtra("pressed_bar_feather_width", seekPressedBarFeather.getProgress());
        styleIntent.putExtra("pressed_bar_position_y", seekPressedBarPositionY.getProgress());
        styleIntent.putExtra("pressed_bar_glow_radius", seekPressedBarGlowRadius.getProgress());
        styleIntent.putExtra("pressed_bar_color_mode", getPressedColorMode());
        startService(styleIntent);
        
        // 更新窗口位置和大小
        int windowWidth = seekWindowWidth.getProgress();
        int windowHeight = seekWindowHeight.getProgress();
        
        // 获取边框和阴影设置
        String borderColor = editBorderColor.getText().toString().trim();
        if (TextUtils.isEmpty(borderColor) || !borderColor.startsWith("#")) {
            borderColor = "#4CAF50";
        }
        
//        String shadowColor = editShadowColor.getText().toString().trim();
//        if (TextUtils.isEmpty(shadowColor) || !shadowColor.startsWith("#")) {
//            shadowColor = "#000000";
//        }
        
        Intent sizeIntent = new Intent(this, FloatWindowService.class);
        sizeIntent.setAction("UPDATE_WINDOW_SIZE");
        sizeIntent.putExtra("window_width", dpToPx(windowWidth));
        sizeIntent.putExtra("window_height", dpToPx(windowHeight));
        sizeIntent.putExtra("window_position_y", seekWindowPositionY.getProgress());
        sizeIntent.putExtra("border_width", seekBorderWidth.getProgress());
        sizeIntent.putExtra("corner_radius", seekCornerRadius.getProgress()); // 新增：圆角半径
        sizeIntent.putExtra("border_color", borderColor);
        sizeIntent.putExtra("border_alpha", seekBorderAlpha.getProgress() / 100.0f);
//        sizeIntent.putExtra("shadow_width", seekShadowWidth.getProgress());
//        sizeIntent.putExtra("shadow_color", shadowColor);
//        sizeIntent.putExtra("shadow_alpha", seekShadowAlpha.getProgress() / 100.0f);
        sizeIntent.putExtra("show_close_button", switchShowCloseButton.isChecked()); // 新增：关闭按钮显示
        sizeIntent.putExtra("show_refresh_button", switchShowRefreshButton.isChecked()); // 新增：显示刷新按钮
        sizeIntent.putExtra("show_back_button", switchShowBackButton.isChecked()); // 新增：显示后退按钮
        sizeIntent.putExtra("close_on_outside_click", switchCloseOnOutsideClick.isChecked()); // 点击窗口外关闭窗口
        sizeIntent.putExtra("back_prioritize_webview", switchBackPrioritizeWebview.isChecked()); // 侧边返回手势行为
        sizeIntent.putExtra("overlay_color", getOverlayColorSafe());
        sizeIntent.putExtra("overlay_alpha", seekOverlayAlpha.getProgress());
        sizeIntent.putExtra("overlay_anim_duration", seekOverlayAnimDuration.getProgress());
        sizeIntent.putExtra("use_expand_directly", switchUseExpandDirectly.isChecked());
        startService(sizeIntent);
        
        // 更新输入法避让设置
        Intent kbIntent = new Intent(this, FloatWindowService.class);
        kbIntent.setAction("UPDATE_KEYBOARD_SETTINGS");
        kbIntent.putExtra("keyboard_offset", seekKeyboardOffset.getProgress());
        kbIntent.putExtra("keyboard_anim_duration", seekKeyboardAnimDuration.getProgress());
        startService(kbIntent);
        
        // 更新动画速度
        Intent animIntent = new Intent(this, FloatWindowService.class);
        animIntent.setAction("UPDATE_STYLE");
        animIntent.putExtra("anim_duration", seekAnimDuration.getProgress());
        animIntent.putExtra("overlay_anim_duration", seekOverlayAnimDuration.getProgress());
        animIntent.putExtra("width", seekBarWidth.getProgress());
        animIntent.putExtra("height", seekBarHeight.getProgress());
        animIntent.putExtra("corner", seekBarCorner.getProgress());
        animIntent.putExtra("position_y", seekBarPositionY.getProgress());
        animIntent.putExtra("long_press_duration", seekLongPressDuration.getProgress());
        // 按下状态样式
        animIntent.putExtra("pressed_bar_enabled", switchPressedBarEnabled.isChecked());
        animIntent.putExtra("pressed_bar_color", getPressedBarColorSafe());
        animIntent.putExtra("pressed_bar_alpha", seekPressedBarAlpha.getProgress());
        animIntent.putExtra("pressed_bar_width", seekPressedBarWidth.getProgress());
        animIntent.putExtra("pressed_bar_height", seekPressedBarHeight.getProgress());
        animIntent.putExtra("pressed_bar_corner", seekPressedBarCorner.getProgress());
        animIntent.putExtra("pressed_bar_feather_width", seekPressedBarFeather.getProgress());
        animIntent.putExtra("pressed_bar_position_y", seekPressedBarPositionY.getProgress());
        animIntent.putExtra("pressed_bar_glow_radius", seekPressedBarGlowRadius.getProgress());
        animIntent.putExtra("pressed_bar_color_mode", getPressedColorMode());
        startService(animIntent);
        
        // 更新快捷方式窗口设置
        Intent shortcutIntent = new Intent(this, FloatWindowService.class);
        shortcutIntent.setAction("UPDATE_SHORTCUT_SETTINGS");
        shortcutIntent.putExtra("shortcut_window_width", seekShortcutWindowWidth.getProgress());
        shortcutIntent.putExtra("shortcut_window_height", seekShortcutWindowHeight.getProgress());
        shortcutIntent.putExtra("shortcut_window_position_y", seekShortcutWindowPositionY.getProgress());
        shortcutIntent.putExtra("shortcut_corner_radius", seekShortcutCornerRadius.getProgress());
        shortcutIntent.putExtra("shortcut_icon_columns", seekShortcutIconColumns.getProgress());
        shortcutIntent.putExtra("shortcut_icon_size", seekShortcutIconSize.getProgress());
        shortcutIntent.putExtra("shortcut_icon_corner", seekShortcutIconCorner.getProgress());
        shortcutIntent.putExtra("try_fullscreen_windowing", switchTryFullscreenWindowing.isChecked());
        shortcutIntent.putExtra("custom_windowing_mode", parseCustomWindowingMode());
        shortcutIntent.putExtra("shortcut_style",
                switchUseExpandDirectly.isChecked() ? 0 : spinnerShortcutStyle.getSelectedItemPosition());
        shortcutIntent.putExtra("shortcut_blur_radius", seekShortcutBlurRadius.getProgress());
        shortcutIntent.putExtra("shortcut_overlay_alpha", seekShortcutOverlayAlpha.getProgress());
        shortcutIntent.putExtra("blur_sync_mode", spinnerBlurSyncMode.getSelectedItemPosition());
        startService(shortcutIntent);

        // 快捷窗口 ColorOS 风格效果
        Intent shortcutEffectIntent = new Intent(this, FloatWindowService.class);
        shortcutEffectIntent.setAction("UPDATE_SHORTCUT_EFFECT_SETTINGS");
        shortcutEffectIntent.putExtra("shortcut_edge_refraction", switchShortcutEdgeRefraction.isChecked());
        shortcutEffectIntent.putExtra("shortcut_finger_glow", switchShortcutFingerGlow.isChecked());
        shortcutEffectIntent.putExtra("shortcut_drag_stretch", switchShortcutDragStretch.isChecked());
        shortcutEffectIntent.putExtra("shortcut_edge_band_dp", seekShortcutEdgeBand.getProgress());
        shortcutEffectIntent.putExtra("shortcut_edge_opacity", seekShortcutEdgeOpacity.getProgress());
        shortcutEffectIntent.putExtra("finger_glow_brightness", seekFingerGlowBrightness.getProgress());
        shortcutEffectIntent.putExtra("finger_glow_size", seekFingerGlowSize.getProgress());
        startService(shortcutEffectIntent);

        // AI 页面设置（网页背景透明、自定义 CSS）
        Intent aiPageIntent = new Intent(this, FloatWindowService.class);
        aiPageIntent.setAction("UPDATE_AI_PAGE_SETTINGS");
        aiPageIntent.putExtra("webpage_bg_transparent", switchWebpageBgTransparent.isChecked());
        aiPageIntent.putExtra("custom_css", editCustomCss.getText().toString().trim());
        startService(aiPageIntent);

        // 更新 AI URL
        Intent aiUrlIntent = new Intent(this, FloatWindowService.class);
        aiUrlIntent.setAction("UPDATE_AI_URL");
        aiUrlIntent.putExtra("ai_url", getAiUrlSafe());
        startService(aiUrlIntent);

        // 更新 AI 窗口模糊效果设置
        Intent aiBlurIntent = new Intent(this, FloatWindowService.class);
        aiBlurIntent.setAction("UPDATE_AI_BLUR_SETTINGS");
        aiBlurIntent.putExtra("ai_style", spinnerAiStyle.getSelectedItemPosition());
        int aiOverlayAlphaToSend = seekAiOverlayAlpha.getProgress();
        if (spinnerAiStyle.getSelectedItemPosition() == 0) {
            aiOverlayAlphaToSend = 255;
        }
        aiBlurIntent.putExtra("ai_blur_radius", seekAiBlurRadius.getProgress());
        aiBlurIntent.putExtra("ai_overlay_alpha", aiOverlayAlphaToSend);
        startService(aiBlurIntent);

        // 更新顶部小横条透明度
        Intent barTransparencyIntent = new Intent(this, FloatWindowService.class);
        barTransparencyIntent.setAction("UPDATE_TOP_BAR_TRANSPARENCY");
        barTransparencyIntent.putExtra("top_bar_transparency", seekTopBarTransparency.getProgress());
        startService(barTransparencyIntent);
    }
    
    /**
     * 安全获取按下状态颜色值
     */
    private String getPressedColorMode() {
        return radioPressedColorMode.getCheckedRadioButtonId() == R.id.radio_pressed_color_custom
                ? "color" : "rainbow";
    }

    /** 仅「自定义颜色」模式显示颜色输入框 */
    private void updatePressedColorInputVisibility() {
        boolean custom = radioPressedColorMode.getCheckedRadioButtonId() == R.id.radio_pressed_color_custom;
        int visibility = custom ? View.VISIBLE : View.GONE;
        if (textPressedBarColorLabel != null) {
            textPressedBarColorLabel.setVisibility(visibility);
        }
        if (editPressedBarColor != null) {
            editPressedBarColor.setVisibility(visibility);
        }
        // 非自定义颜色时整行隐藏，横屏也不会留下空的同步勾选框
        setSyncRowVisibility(KEY_PRESSED_BAR_COLOR, custom);
    }

    private String getPressedBarColorSafe() {
        String color = editPressedBarColor.getText().toString().trim();
        if (TextUtils.isEmpty(color) || !color.startsWith("#")) {
            return "#333333";
        }
        return color;
    }

    private String getAiUrlSafe() {
        String url = editAiUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            return "https://www.doubao.com";
        }
        return url;
    }

    private String getBorderColorSafe() {
        String color = editBorderColor.getText().toString().trim();
        if (TextUtils.isEmpty(color) || !color.startsWith("#")) {
            return "#4CAF50";
        }
        return color;
    }

    private String getOverlayColorSafe() {
        String color = editOverlayColor.getText().toString().trim();
        if (TextUtils.isEmpty(color) || !color.startsWith("#")) {
            return "#000000";
        }
        return color;
    }
    
    /**
     * 触发按下样式预览（自动显示按下样式覆盖层，1秒无操作后自动隐藏）
     */
    private void triggerPressedPreview() {
        // 回填控件/放弃修改时不要触发预览
        if (suppressCallbacks || discardingChanges) return;
        Intent intent = new Intent(this, FloatWindowService.class);
        intent.setAction("UPDATE_STYLE");
        intent.putExtra("show_pressed_preview", true);
        startService(intent);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean newLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (restoringOrientation) {
            // 系统方向已恢复：用恢复后的真实方向重读落盘值，
            // 不再依赖服务尚未更新的 Configuration
            FloatWindowService.reloadSettingsForOrientation(newLandscape);
            return;
        }
        if (isFinishing()) return;

        // 只处理真正的横竖屏切换；键盘/深色模式等配置变化不重载控件
        if (newLandscape == lastLandscape) {
            return;
        }
        // 用户物理旋转时，退出目标应跟随新的系统方向；
        // 由方向按钮触发的旋转则保留进入设置页时的原始方向。
        if (!changingOrientation) {
            previousLandscape = newLandscape;
        }
        // 先按旧方向把控件值收进模型，再切换布局
        captureCurrentValues(lastLandscape);
        lastLandscape = newLandscape;
        changingOrientation = false;
        orientationRequestSeq++;

        applyOrientationLayout();
        withSuppressedCallbacks(this::loadCurrentOrientationIntoControls);
        applySyncRowStates();
        if (activityResumed) {
            applyCurrentSettings();
        }
        // 若轮廓预览正显示，按新方向刷新几何
        if (windowOutlineOverlay != null && windowOutlineOverlay.getAlpha() > 0f) {
            refreshWindowOutlinePreview();
        }
    }

    @Override
    public void finish() {
        if (orientationForced) {
            // 退出目标是强制旋转前的系统方向
            exitTargetLandscape = previousLandscape;
            orientationForced = false;
            restoringOrientation = true;
            // 先放行服务的方向回调，再请求恢复系统方向，
            // 避免服务 onConfigurationChanged 因设置页仍标记为前台而被跳过
            FloatWindowService.sSettingsActivityOpen = false;
            setRequestedOrientation(previousRequestedOrientation);
        } else if (exitTargetLandscape == null) {
            exitTargetLandscape = OrientationSettings.isLandscape(this);
        }
        super.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        FloatWindowService.sSettingsActivityOpen = true;
        // 回到设置页：重新把当前（可能是未保存的）设置下发给服务
        if (!discardingChanges) {
            flushPendingApply();
        }

        // 进入设置页面时，将底部提示条透明度设为50%
        Intent intent = new Intent(this, FloatWindowService.class);
        intent.setAction("UPDATE_STYLE");
        intent.putExtra("width", seekBarWidth.getProgress());
        intent.putExtra("height", seekBarHeight.getProgress());
        intent.putExtra("corner", seekBarCorner.getProgress());
        intent.putExtra("alpha", 0.5f);
        intent.putExtra("position_y", seekBarPositionY.getProgress());
        intent.putExtra("long_press_duration", seekLongPressDuration.getProgress());
        intent.putExtra("pressed_bar_enabled", switchPressedBarEnabled.isChecked());
        intent.putExtra("pressed_bar_color", getPressedBarColorSafe());
        intent.putExtra("pressed_bar_alpha", seekPressedBarAlpha.getProgress());
        intent.putExtra("pressed_bar_width", seekPressedBarWidth.getProgress());
        intent.putExtra("pressed_bar_height", seekPressedBarHeight.getProgress());
        intent.putExtra("pressed_bar_corner", seekPressedBarCorner.getProgress());
        intent.putExtra("pressed_bar_feather_width", seekPressedBarFeather.getProgress());
        intent.putExtra("pressed_bar_position_y", seekPressedBarPositionY.getProgress());
        intent.putExtra("pressed_bar_glow_radius", seekPressedBarGlowRadius.getProgress());
        intent.putExtra("pressed_bar_color_mode", getPressedColorMode());
        try {
            startService(intent);
        } catch (Exception e) {
            // 后台启动限制等异常不应让设置页崩溃；预览由静态直连兜底
            e.printStackTrace();
        }
        // 关键修复：不只依赖 intent 改透明度，直接让服务把提示条恢复到可见状态
        FloatWindowService.setTriggerAreaPreview(true);
        previewRetryHandler.removeCallbacks(previewRetryRunnable);
        previewRetryHandler.postDelayed(previewRetryRunnable, 350);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        // 离开设置页时立即隐藏快捷窗口轮廓预览，避免残留
        if (windowOutlineOverlay != null) windowOutlineOverlay.hideNow();

        // 退出时不再下发当前方向（可能是横屏）的即时值：
        // 在途 UPDATE_* 会晚于重载到达并覆盖落盘值。真正退出统一走下面的重载。
        if (!discardingChanges && !isFinishing()) {
            // 先执行待下发的文本修改，再隐藏提示条
            flushPendingApply();
            // 离开设置页面时，将底部提示条透明度设为0
            Intent intent = new Intent(this, FloatWindowService.class);
            intent.setAction("UPDATE_STYLE");
            intent.putExtra("width", seekBarWidth.getProgress());
            intent.putExtra("height", seekBarHeight.getProgress());
            intent.putExtra("corner", seekBarCorner.getProgress());
            intent.putExtra("alpha", 0.0f);
            intent.putExtra("position_y", seekBarPositionY.getProgress());
            intent.putExtra("long_press_duration", seekLongPressDuration.getProgress());
            intent.putExtra("pressed_bar_enabled", switchPressedBarEnabled.isChecked());
            intent.putExtra("pressed_bar_color", getPressedBarColorSafe());
            intent.putExtra("pressed_bar_alpha", seekPressedBarAlpha.getProgress());
            intent.putExtra("pressed_bar_width", seekPressedBarWidth.getProgress());
            intent.putExtra("pressed_bar_height", seekPressedBarHeight.getProgress());
            intent.putExtra("pressed_bar_corner", seekPressedBarCorner.getProgress());
            intent.putExtra("pressed_bar_feather_width", seekPressedBarFeather.getProgress());
            intent.putExtra("pressed_bar_position_y", seekPressedBarPositionY.getProgress());
            intent.putExtra("pressed_bar_glow_radius", seekPressedBarGlowRadius.getProgress());
            intent.putExtra("pressed_bar_color_mode", getPressedColorMode());
            try {
                startService(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 离开设置页：取消重试并立即熄灭触发区预览
        previewRetryHandler.removeCallbacks(previewRetryRunnable);
        FloatWindowService.setTriggerAreaPreview(false);
        FloatWindowService.sSettingsActivityOpen = false;
        // 真正退出时（保存/放弃/直接返回）让服务按最终方向重新读取落盘值，
        // 避免退出瞬间方向恢复与服务配置变化时序错位。
        if (isFinishing()) {
            boolean targetLandscape = exitTargetLandscape != null
                    ? exitTargetLandscape
                    : OrientationSettings.isLandscape(this);
            FloatWindowService.reloadSettingsForOrientation(targetLandscape);
        }
    }

    @Override
    protected void onDestroy() {
        pendingApplyHandler.removeCallbacks(pendingApplyRunnable);
        pendingApplyHandler.removeCallbacks(markUiInitializedRunnable);
        if (previewRetryHandler != null) {
            previewRetryHandler.removeCallbacks(previewRetryRunnable);
        }
        if (unsavedDialog != null && unsavedDialog.isShowing()) {
            unsavedDialog.dismiss();
        }
        super.onDestroy();
    }

    /**
     * 绑定顶部三个分组锚点：点击滚动到分组，滑动时切换高亮。
     * 锚点栏在标题栏内，不随内容滚动，始终可见。
     */
    private void setupGroupTabs() {
        settingsScroll = findViewById(R.id.settings_scroll);
        tabTip = findViewById(R.id.tab_tip);
        tabShortcut = findViewById(R.id.tab_shortcut);
        tabAdvanced = findViewById(R.id.tab_advanced);
        groupTip = findViewById(R.id.group_tip);
        groupShortcut = findViewById(R.id.group_shortcut);
        groupAdvanced = findViewById(R.id.group_advanced);

        tabTip.setOnClickListener(v -> scrollToGroup(0));
        tabShortcut.setOnClickListener(v -> scrollToGroup(1));
        tabAdvanced.setOnClickListener(v -> scrollToGroup(2));

        settingsScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                updateActiveGroupByScroll());
        settingsScroll.post(this::updateActiveGroupByScroll);
    }

    private View groupAt(int index) {
        if (index == 0) return groupTip;
        if (index == 1) return groupShortcut;
        return groupAdvanced;
    }

    private void scrollToGroup(int index) {
        View target = groupAt(index);
        if (target == null) return;
        suppressScrollUntil = System.currentTimeMillis() + 600;
        setActiveGroup(index);
        settingsScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dpToPx(8)));
    }

    /** 以「分组标题滚到顶部」为界，取最后一个已经到顶的分组 */
    private void updateActiveGroupByScroll() {
        if (System.currentTimeMillis() < suppressScrollUntil) return;
        int scrollY = settingsScroll.getScrollY();
        int threshold = dpToPx(12);
        int active = 0;
        for (int i = 0; i < 3; i++) {
            View group = groupAt(i);
            if (group != null && group.getTop() - scrollY <= threshold) {
                active = i;
            }
        }
        View content = settingsScroll.getChildAt(0);
        if (content != null && scrollY + settingsScroll.getHeight() >= content.getBottom() - dpToPx(4)) {
            active = 2;
        }
        setActiveGroup(active);
    }

    private void setActiveGroup(int index) {
        activeGroupIndex = index;
        TextView[] tabs = {tabTip, tabShortcut, tabAdvanced};
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null) continue;
            boolean selected = i == index;
            tabs[i].setBackgroundResource(selected
                    ? R.drawable.tab_chip_selected
                    : R.drawable.tab_chip_unselected);
            tabs[i].setTextColor(selected ? 0xFFFFFFFF : resolveThemeColor(R.attr.textSecondary));
        }
    }

    private int resolveThemeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /**
     * dp 转 px
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
