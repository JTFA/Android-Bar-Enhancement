package com.example.aiwindow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
    
    // 背景遮罩设置
    private static final String KEY_OVERLAY_COLOR = "overlay_color";
    private static final String KEY_OVERLAY_ALPHA = "overlay_alpha";
    private static final String KEY_OVERLAY_ANIM_DURATION = "overlay_anim_duration";
    
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
    private EditText editWindowWidth, editWindowHeight;
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
    
    // 单击提示条打开第三方应用
    private LinearLayout layoutSelectApp;
    private TextView textViewSelectedApp;
    private String clickAppPackage = "";
    private String clickAppName = "";
    
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
    
    // 羽化宽度控件
    private SeekBar seekPressedBarFeather;
    private TextView textPressedBarFeather;
    
    // 按下时位置Y控件
    private SeekBar seekPressedBarPositionY;
    private TextView textPressedBarPositionY;
    
    // 按下时位置Y控件
    
    // 背景遮罩控件
    private SeekBar seekOverlayAlpha;
    private TextView textOverlayAlpha;
    private EditText editOverlayColor;
    private SeekBar seekOverlayAnimDuration;
    private TextView textOverlayAnimDuration;
    
    // 保存按钮
    private Button btnSave;
    
    // SharedPreferences
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化 SharedPreferences
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        initViews();
        loadSettings(); // 加载已保存的设置
        setupListeners();
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
        editWindowWidth = findViewById(R.id.edit_window_width);
        editWindowHeight = findViewById(R.id.edit_window_height);
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
        
//        seekShadowWidth = findViewById(R.id.seek_shadow_width);
//        seekShadowAlpha = findViewById(R.id.seek_shadow_alpha);
//        editShadowColor = findViewById(R.id.edit_shadow_color);
//        textShadowWidth = findViewById(R.id.text_shadow_width);
//        textShadowAlpha = findViewById(R.id.text_shadow_alpha);
        
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
        
        // 单击提示条打开第三方应用
        layoutSelectApp = findViewById(R.id.layout_select_app);
        textViewSelectedApp = findViewById(R.id.text_selected_app);
        
        // 输入法避让控件
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
        
        // 羽化宽度控件
        seekPressedBarFeather = findViewById(R.id.seek_pressed_bar_feather);
        textPressedBarFeather = findViewById(R.id.text_pressed_bar_feather);
        
        // 按下时位置Y控件
        seekPressedBarPositionY = findViewById(R.id.seek_pressed_bar_position_y);
        textPressedBarPositionY = findViewById(R.id.text_pressed_bar_position_y);
        
        // 按下时位置Y控件
        
        // 背景遮罩控件
        seekOverlayAlpha = findViewById(R.id.seek_overlay_alpha);
        textOverlayAlpha = findViewById(R.id.text_overlay_alpha);
        editOverlayColor = findViewById(R.id.edit_overlay_color);
        seekOverlayAnimDuration = findViewById(R.id.seek_overlay_anim_duration);
        textOverlayAnimDuration = findViewById(R.id.text_overlay_anim_duration);
        
        // 保存按钮
        btnSave = findViewById(R.id.btn_save_apply);
    }
    
    /**
     * 从 SharedPreferences 加载设置
     */
    private void loadSettings() {
        // 加载 AI URL
        String aiUrl = preferences.getString(KEY_AI_URL, "https://www.doubao.com");
        editAiUrl.setText(aiUrl);
        
        // 加载提示条宽度
        int barWidth = preferences.getInt(KEY_BAR_WIDTH, 355);
        seekBarWidth.setProgress(barWidth);
        textWidth.setText("宽度: " + barWidth + "dp");
        
        // 加载提示条高度
        int barHeight = preferences.getInt(KEY_BAR_HEIGHT, 9);
        seekBarHeight.setProgress(barHeight);
        textHeight.setText("高度: " + barHeight + "dp");
        
        // 加载提示条圆角
        int barCorner = preferences.getInt(KEY_BAR_CORNER, 30);
        seekBarCorner.setProgress(barCorner);
        textCorner.setText("圆角: " + barCorner + "dp");
        
        // 加载 AI 窗口宽度
        int windowWidth = preferences.getInt(KEY_WINDOW_WIDTH, 340);
        editWindowWidth.setText(String.valueOf(windowWidth));
        
        // 加载 AI 窗口高度
        int windowHeight = preferences.getInt(KEY_WINDOW_HEIGHT, 450);
        editWindowHeight.setText(String.valueOf(windowHeight));
        
        int barPosY = preferences.getInt(KEY_BAR_POSITION_Y, 391);
        seekBarPositionY.setProgress(barPosY);
        String posYText = barPosY >= 0 ? "位置 Y: " + barPosY + "dp (向上)" : "位置 Y: " + barPosY + "dp (向下)";
        textPositionY.setText(posYText);
        
        // 加载长按时间
        int longPressDuration = preferences.getInt(KEY_LONG_PRESS_DURATION, 500);
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
        
        // 加载阴影设置
//        int shadowWidth = preferences.getInt(KEY_SHADOW_WIDTH, 0);
//        seekShadowWidth.setProgress(shadowWidth);
//        textShadowWidth.setText("阴影宽度: " + shadowWidth + "dp");
        
//        String shadowColor = preferences.getString(KEY_SHADOW_COLOR, "#000000");
//        editShadowColor.setText(shadowColor);
        
//        int shadowAlpha = preferences.getInt(KEY_SHADOW_ALPHA, 0); // 关键修复：默认值从 30 改为 60
//        seekShadowAlpha.setProgress(shadowAlpha);
//        textShadowAlpha.setText("阴影透明度: " + String.format("%.2f", shadowAlpha / 100.0f));
        
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
        boolean closeOnOutsideClick = preferences.getBoolean(KEY_CLOSE_ON_OUTSIDE_CLICK, true);
        switchCloseOnOutsideClick.setChecked(closeOnOutsideClick);
        
        // 加载侧边返回手势设置
        boolean backPrioritizeWebview = preferences.getBoolean(KEY_BACK_PRIORITIZE_WEBVIEW, false);
        switchBackPrioritizeWebview.setChecked(backPrioritizeWebview);
        
        // 加载单击提示条打开的应用设置
        clickAppPackage = preferences.getString(KEY_CLICK_APP_PACKAGE, "");
        clickAppName = preferences.getString(KEY_CLICK_APP_NAME, "");
        if (!TextUtils.isEmpty(clickAppName)) {
            textViewSelectedApp.setText(clickAppName);
        } else {
            textViewSelectedApp.setText(R.string.not_selected);
        }
        
        // 加载输入法避让设置
        int keyboardOffset = preferences.getInt(KEY_KEYBOARD_OFFSET, 397);
        seekKeyboardOffset.setProgress(keyboardOffset);
        textKeyboardOffset.setText("窗口上移距离: " + keyboardOffset + "px");
        
        int keyboardAnimDuration = preferences.getInt(KEY_KEYBOARD_ANIM_DURATION, 250);
        seekKeyboardAnimDuration.setProgress(keyboardAnimDuration);
        textKeyboardAnimDuration.setText("平移动画时长: " + keyboardAnimDuration + "ms");
        
        // 加载动画速度
        int animDuration = preferences.getInt(KEY_ANIM_DURATION, 350);
        seekAnimDuration.setProgress(animDuration);
        textAnimDuration.setText("动画速度: " + animDuration + "ms");
        
        // 加载按下状态样式
        boolean pressedEnabled = preferences.getBoolean(KEY_PRESSED_BAR_ENABLED, true);
        switchPressedBarEnabled.setChecked(pressedEnabled);
        
        int pressedWidth = preferences.getInt(KEY_PRESSED_BAR_WIDTH, 420);
        seekPressedBarWidth.setProgress(pressedWidth);
        textPressedBarWidth.setText("按下时宽度: " + pressedWidth + "dp");
        
        int pressedHeight = preferences.getInt(KEY_PRESSED_BAR_HEIGHT, 20);
        seekPressedBarHeight.setProgress(pressedHeight);
        textPressedBarHeight.setText("按下时高度: " + pressedHeight + "dp");
        
        int pressedCorner = preferences.getInt(KEY_PRESSED_BAR_CORNER, 45);
        seekPressedBarCorner.setProgress(pressedCorner);
        textPressedBarCorner.setText("按下时圆角: " + pressedCorner + "dp");
        
        int pressedAlpha = preferences.getInt(KEY_PRESSED_BAR_ALPHA, 100);
        seekPressedBarAlpha.setProgress(pressedAlpha);
        textPressedBarAlpha.setText("按下时透明度: " + String.format("%.2f", pressedAlpha / 100.0f));
        
        String pressedColor = preferences.getString(KEY_PRESSED_BAR_COLOR, "#0099dd");
        editPressedBarColor.setText(pressedColor);
        
        // 加载羽化宽度
        int pressedFeather = preferences.getInt(KEY_PRESSED_BAR_FEATHER, 10);
        seekPressedBarFeather.setProgress(pressedFeather);
        textPressedBarFeather.setText("边缘羽化宽度: " + pressedFeather + "dp");
        
        // 加载按下时位置Y
        int pressedPosY = preferences.getInt(KEY_PRESSED_BAR_POSITION_Y, 17);
        seekPressedBarPositionY.setProgress(pressedPosY);
        String pressedPosYText = pressedPosY >= 0 ? "按下时位置Y: " + pressedPosY + "dp (向上)" : "按下时位置Y: " + pressedPosY + "dp (向下)";
        textPressedBarPositionY.setText(pressedPosYText);
        
        // 加载按下时位置Y
        // 加载背景遮罩设置
        int overlayAlpha = preferences.getInt(KEY_OVERLAY_ALPHA, 50);
        seekOverlayAlpha.setProgress(overlayAlpha);
        textOverlayAlpha.setText("遮罩透明度: " + overlayAlpha + "%");
        
        String overlayColor = preferences.getString(KEY_OVERLAY_COLOR, "#000000");
        editOverlayColor.setText(overlayColor);
        
        int overlayAnimDuration = preferences.getInt(KEY_OVERLAY_ANIM_DURATION, 100);
        seekOverlayAnimDuration.setProgress(overlayAnimDuration);
        textOverlayAnimDuration.setText("遮罩渐变时长: " + overlayAnimDuration + "ms");
    }
    
    /**
     * 保存设置到 SharedPreferences
     */
    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        
        // 保存 AI URL
        String aiUrl = editAiUrl.getText().toString().trim();
        if (TextUtils.isEmpty(aiUrl)) {
            aiUrl = "https://www.doubao.com"; // 默认值
        }
        editor.putString(KEY_AI_URL, aiUrl);
        
        // 保存提示条样式
        editor.putInt(KEY_BAR_WIDTH, seekBarWidth.getProgress());
        editor.putInt(KEY_BAR_HEIGHT, seekBarHeight.getProgress());
        editor.putInt(KEY_BAR_CORNER, seekBarCorner.getProgress());
        editor.putInt(KEY_BAR_POSITION_Y, seekBarPositionY.getProgress());
        editor.putInt(KEY_LONG_PRESS_DURATION, seekLongPressDuration.getProgress());
        
        // 保存 AI 窗口大小
        String windowWidthStr = editWindowWidth.getText().toString().trim();
        String windowHeightStr = editWindowHeight.getText().toString().trim();
        
        int windowWidth = 350; // 默认值
        int windowHeight = 600; // 默认值
        
        try {
            if (!TextUtils.isEmpty(windowWidthStr)) {
                windowWidth = Integer.parseInt(windowWidthStr);
                // 限制范围
                windowWidth = Math.max(200, Math.min(600, windowWidth));
            }
            if (!TextUtils.isEmpty(windowHeightStr)) {
                windowHeight = Integer.parseInt(windowHeightStr);
                // 限制范围
                windowHeight = Math.max(300, Math.min(900, windowHeight));
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            return;
        }
        
        editor.putInt(KEY_WINDOW_WIDTH, windowWidth);
        editor.putInt(KEY_WINDOW_HEIGHT, windowHeight);
        editor.putInt(KEY_WINDOW_POSITION_Y, seekWindowPositionY.getProgress());
        
        // 保存边框和阴影设置
        editor.putInt(KEY_BORDER_WIDTH, seekBorderWidth.getProgress());
        editor.putInt(KEY_CORNER_RADIUS, seekCornerRadius.getProgress()); // 保存圆角半径
        String borderColor = editBorderColor.getText().toString().trim();
        if (TextUtils.isEmpty(borderColor) || !borderColor.startsWith("#")) {
            borderColor = "#4CAF50";
        }
        editor.putString(KEY_BORDER_COLOR, borderColor);
        editor.putInt(KEY_BORDER_ALPHA, seekBorderAlpha.getProgress());
        
//        editor.putInt(KEY_SHADOW_WIDTH, seekShadowWidth.getProgress());
//        String shadowColor = editShadowColor.getText().toString().trim();
//        if (TextUtils.isEmpty(shadowColor) || !shadowColor.startsWith("#")) {
//            shadowColor = "#000000";
//        }
//        editor.putString(KEY_SHADOW_COLOR, shadowColor);
//        editor.putInt(KEY_SHADOW_ALPHA, seekShadowAlpha.getProgress());
        
        // 保存关闭按钮显示设置
        editor.putBoolean(KEY_SHOW_CLOSE_BUTTON, switchShowCloseButton.isChecked());
        
        // 保存显示刷新按钮设置
        editor.putBoolean(KEY_SHOW_REFRESH_BUTTON, switchShowRefreshButton.isChecked());
        
        // 保存显示后退按钮设置
        editor.putBoolean(KEY_SHOW_BACK_BUTTON, switchShowBackButton.isChecked());
        
        // 保存点击窗口外关闭窗口设置
        editor.putBoolean(KEY_CLOSE_ON_OUTSIDE_CLICK, switchCloseOnOutsideClick.isChecked());
        
        // 保存侧边返回手势设置
        editor.putBoolean(KEY_BACK_PRIORITIZE_WEBVIEW, switchBackPrioritizeWebview.isChecked());
        
        // 保存单击提示条打开的应用设置
        editor.putString(KEY_CLICK_APP_PACKAGE, clickAppPackage);
        editor.putString(KEY_CLICK_APP_NAME, clickAppName);
        
        // 保存输入法避让设置
        editor.putInt(KEY_KEYBOARD_OFFSET, seekKeyboardOffset.getProgress());
        editor.putInt(KEY_KEYBOARD_ANIM_DURATION, seekKeyboardAnimDuration.getProgress());
        
        // 保存动画速度
        editor.putInt(KEY_ANIM_DURATION, seekAnimDuration.getProgress());
        
        // 保存按下状态样式
        editor.putBoolean(KEY_PRESSED_BAR_ENABLED, switchPressedBarEnabled.isChecked());
        editor.putInt(KEY_PRESSED_BAR_WIDTH, seekPressedBarWidth.getProgress());
        editor.putInt(KEY_PRESSED_BAR_HEIGHT, seekPressedBarHeight.getProgress());
        editor.putInt(KEY_PRESSED_BAR_CORNER, seekPressedBarCorner.getProgress());
        editor.putInt(KEY_PRESSED_BAR_ALPHA, seekPressedBarAlpha.getProgress());
        String pressedColor = editPressedBarColor.getText().toString().trim();
        if (TextUtils.isEmpty(pressedColor) || !pressedColor.startsWith("#")) {
            pressedColor = "#333333";
        }
        editor.putString(KEY_PRESSED_BAR_COLOR, pressedColor);
        
        // 保存羽化宽度
        editor.putInt(KEY_PRESSED_BAR_FEATHER, seekPressedBarFeather.getProgress());
        
        // 保存按下时位置Y
        editor.putInt(KEY_PRESSED_BAR_POSITION_Y, seekPressedBarPositionY.getProgress());
        
        // 保存按下时位置Y
        // 保存背景遮罩设置
        editor.putInt(KEY_OVERLAY_ALPHA, seekOverlayAlpha.getProgress());
        String overlayColor = editOverlayColor.getText().toString().trim();
        if (TextUtils.isEmpty(overlayColor) || !overlayColor.startsWith("#")) {
            overlayColor = "#000000";
        }
        editor.putString(KEY_OVERLAY_COLOR, overlayColor);
        editor.putInt(KEY_OVERLAY_ANIM_DURATION, seekOverlayAnimDuration.getProgress());
        
        // 提交保存
        editor.apply();
        
        // 通知服务更新
        applySettingsToService(aiUrl, windowWidth, windowHeight);
        
        Toast.makeText(this, "设置已保存并应用", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 将设置应用到正在运行的服务
     */
    private void applySettingsToService(String aiUrl, int windowWidth, int windowHeight) {
        // 更新提示条样式和位置
        Intent styleIntent = new Intent(this, FloatWindowService.class);
        styleIntent.setAction("UPDATE_STYLE");
        styleIntent.putExtra("width", seekBarWidth.getProgress());
        styleIntent.putExtra("height", seekBarHeight.getProgress());
        styleIntent.putExtra("corner", seekBarCorner.getProgress());
        styleIntent.putExtra("position_y", seekBarPositionY.getProgress());
        styleIntent.putExtra("pressed_bar_enabled", switchPressedBarEnabled.isChecked());
        styleIntent.putExtra("pressed_bar_color", getPressedBarColorSafe());
        styleIntent.putExtra("pressed_bar_alpha", seekPressedBarAlpha.getProgress());
        styleIntent.putExtra("pressed_bar_width", seekPressedBarWidth.getProgress());
        styleIntent.putExtra("pressed_bar_height", seekPressedBarHeight.getProgress());
        styleIntent.putExtra("pressed_bar_corner", seekPressedBarCorner.getProgress());
        styleIntent.putExtra("pressed_bar_feather_width", seekPressedBarFeather.getProgress());
        styleIntent.putExtra("pressed_bar_position_y", seekPressedBarPositionY.getProgress());
        startService(styleIntent);
        
        // 关键修复：获取边框和阴影设置
        String borderColor = editBorderColor.getText().toString().trim();
        if (TextUtils.isEmpty(borderColor) || !borderColor.startsWith("#")) {
            borderColor = "#4CAF50";
        }
        
//        String shadowColor = editShadowColor.getText().toString().trim();
//        if (TextUtils.isEmpty(shadowColor) || !shadowColor.startsWith("#")) {
//            shadowColor = "#000000";
//        }
        
        // 更新 AI 窗口大小、位置、边框和阴影
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
        sizeIntent.putExtra("overlay_color", editOverlayColor.getText().toString().trim());
        sizeIntent.putExtra("overlay_alpha", seekOverlayAlpha.getProgress());
        sizeIntent.putExtra("click_app_package", clickAppPackage);
        sizeIntent.putExtra("click_app_name", clickAppName);
        startService(sizeIntent);
        
        // 更新输入法避让设置
        Intent kbIntent = new Intent(this, FloatWindowService.class);
        kbIntent.setAction("UPDATE_KEYBOARD_SETTINGS");
        kbIntent.putExtra("keyboard_offset", seekKeyboardOffset.getProgress());
        kbIntent.putExtra("keyboard_anim_duration", seekKeyboardAnimDuration.getProgress());
        startService(kbIntent);
        
        // 更新动画速度（通过 UPDATE_STYLE 传递）
        Intent animIntent = new Intent(this, FloatWindowService.class);
        animIntent.setAction("UPDATE_STYLE");
        animIntent.putExtra("anim_duration", seekAnimDuration.getProgress());
        animIntent.putExtra("overlay_anim_duration", seekOverlayAnimDuration.getProgress());
        // 保持当前其他样式不变，传入现有值
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
        startService(animIntent);
        
        // 更新 AI URL（如果服务正在运行且窗口已展开）
        Intent urlIntent = new Intent(this, FloatWindowService.class);
        urlIntent.setAction("UPDATE_AI_URL");
        urlIntent.putExtra("ai_url", aiUrl);
        startService(urlIntent);
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
                applyCurrentSettings(); // 实时应用
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
                applyCurrentSettings(); // 实时应用
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
                applyCurrentSettings(); // 实时应用
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
                applyCurrentSettings(); // 实时应用
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
                applyCurrentSettings(); // 实时应用
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        

        // AI 窗口位置 Y - 实时更新
        seekWindowPositionY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textWindowPositionY.setText("窗口位置 Y: " + progress + " (居中)");
                applyCurrentSettings(); // 实时应用
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
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 窗口圆角半径 - 实时更新
        seekCornerRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textCornerRadius.setText("窗口圆角: " + progress + "dp");
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 边框透明度 - 实时更新
        seekBorderAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textBorderAlpha.setText("边框透明度: " + String.format("%.2f", progress / 100.0f));
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 阴影宽度 - 实时更新
//        seekShadowWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                textShadowWidth.setText("阴影宽度: " + progress + "dp");
//                applyCurrentSettings();
//            }
//            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
//            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
        
        // 阴影透明度 - 实时更新
//        seekShadowAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                textShadowAlpha.setText("阴影透明度: " + String.format("%.2f", progress / 100.0f));
//                applyCurrentSettings();
//            }
//            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
//            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
        
        // 输入法上移距离 - 实时更新
        seekKeyboardOffset.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textKeyboardOffset.setText("窗口上移距离: " + progress + "px");
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 输入法动画时长 - 实时更新
        seekKeyboardAnimDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textKeyboardAnimDuration.setText("平移动画时长: " + progress + "ms");
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 展开/收起动画速度 - 实时更新
        seekAnimDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textAnimDuration.setText("动画速度: " + progress + "ms");
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时启用开关 - 实时更新
        switchPressedBarEnabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                applyCurrentSettings();
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
                applyCurrentSettings();
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
                applyCurrentSettings();
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
                applyCurrentSettings();
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
                applyCurrentSettings();
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
                applyCurrentSettings();
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
                applyCurrentSettings();
                triggerPressedPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 按下时颜色编辑框 TextWatcher（实时预览）
        editPressedBarColor.addTextChangedListener(new android.text.TextWatcher() {
            private Handler textHandler = new Handler(Looper.getMainLooper());
            private Runnable textTimeout;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (textTimeout != null) {
                    textHandler.removeCallbacks(textTimeout);
                }
                textTimeout = new Runnable() {
                    @Override
                    public void run() {
                        applyCurrentSettings();
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
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 背景遮罩渐变时长 - 实时更新
        seekOverlayAnimDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOverlayAnimDuration.setText("遮罩渐变时长: " + progress + "ms");
                applyCurrentSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 保存并应用按钮
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        
        // 选择要打开的应用
        layoutSelectApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 首次打开时弹出权限说明
                boolean firstTimeShown = preferences.getBoolean(KEY_APP_SELECTOR_SHOWN, false);
                if (!firstTimeShown) {
                    showPermissionExplanation();
                } else {
                    showAppPicker();
                }
            }
        });
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
        startService(styleIntent);
        
        // 更新窗口位置和大小
        String windowWidthStr = editWindowWidth.getText().toString().trim();
        String windowHeightStr = editWindowHeight.getText().toString().trim();
        
        int windowWidth = 350;
        int windowHeight = 600;
        
        try {
            if (!TextUtils.isEmpty(windowWidthStr)) {
                windowWidth = Integer.parseInt(windowWidthStr);
                windowWidth = Math.max(200, Math.min(600, windowWidth));
            }
            if (!TextUtils.isEmpty(windowHeightStr)) {
                windowHeight = Integer.parseInt(windowHeightStr);
                windowHeight = Math.max(300, Math.min(900, windowHeight));
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入
        }
        
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
        sizeIntent.putExtra("overlay_color", editOverlayColor.getText().toString().trim());
        sizeIntent.putExtra("overlay_alpha", seekOverlayAlpha.getProgress());
        sizeIntent.putExtra("click_app_package", clickAppPackage);
        sizeIntent.putExtra("click_app_name", clickAppName);
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
        startService(animIntent);
    }
    
    /**
     * 安全获取按下状态颜色值
     */
    private String getPressedBarColorSafe() {
        String color = editPressedBarColor.getText().toString().trim();
        if (TextUtils.isEmpty(color) || !color.startsWith("#")) {
            return "#333333";
        }
        return color;
    }
    
    /**
     * 触发按下样式预览（自动显示按下样式覆盖层，1秒无操作后自动隐藏）
     */
    private void triggerPressedPreview() {
        Intent intent = new Intent(this, FloatWindowService.class);
        intent.setAction("UPDATE_STYLE");
        intent.putExtra("show_pressed_preview", true);
        startService(intent);
    }
    
    /**
     * 显示应用读取权限说明（首次打开时调用）
     */
    private void showPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_permission_title)
                .setMessage(R.string.app_permission_message)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 标记已显示过权限说明
                        preferences.edit().putBoolean(KEY_APP_SELECTOR_SHOWN, true).apply();
                        // 打开应用选择器
                        showAppPicker();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    /**
     * 显示已安装应用列表供用户选择
     */
    private void showAppPicker() {
        // 查询所有可启动的应用
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        
        // 按应用名排序（使用中文排序器，中文按拼音首字母排列）
        final Collator collator = Collator.getInstance(java.util.Locale.CHINESE);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                String labelA = a.loadLabel(pm).toString();
                String labelB = b.loadLabel(pm).toString();
                return collator.compare(labelA, labelB);
            }
        });
        
        // 准备列表数据
        final String[] appNames = new String[apps.size()];
        final String[] appPackages = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            appNames[i] = apps.get(i).loadLabel(pm).toString();
            appPackages[i] = apps.get(i).activityInfo.packageName;
        }
        
        // 找到当前选中项
        int checkedItem = -1;
        if (!TextUtils.isEmpty(clickAppPackage)) {
            for (int i = 0; i < appPackages.length; i++) {
                if (appPackages[i].equals(clickAppPackage)) {
                    checkedItem = i;
                    break;
                }
            }
        }
        
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_app)
                .setSingleChoiceItems(appNames, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clickAppPackage = appPackages[which];
                        clickAppName = appNames[which];
                        textViewSelectedApp.setText(clickAppName);
                        // 保存并通知服务
                        saveClickAppSetting();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    /**
     * 保存单击打开应用设置并通知服务
     */
    private void saveClickAppSetting() {
        // 保存到 SharedPreferences
        preferences.edit()
                .putString(KEY_CLICK_APP_PACKAGE, clickAppPackage)
                .putString(KEY_CLICK_APP_NAME, clickAppName)
                .apply();
        
        // 通知服务更新
        Intent intent = new Intent(this, FloatWindowService.class);
        intent.setAction("UPDATE_CLICK_APP");
        intent.putExtra("click_app_package", clickAppPackage);
        intent.putExtra("click_app_name", clickAppName);
        startService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        startService(intent);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
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
        startService(intent);
    }

    /**
     * dp 转 px
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
