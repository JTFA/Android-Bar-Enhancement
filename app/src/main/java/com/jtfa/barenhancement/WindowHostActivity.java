package com.jtfa.barenhancement;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Transparent proxy Activity that sits behind the floating window overlay.
 * Its sole purpose is to receive system "back" events (both navigation bar button
 * and gesture navigation swipe) and forward them to the Service to collapse the window.
 *
 * Android only delivers back events to the topmost Activity in the back stack.
 * TYPE_APPLICATION_OVERLAY windows never receive these events, so this Activity
 * acts as a bridge.
 */
public class WindowHostActivity extends AppCompatActivity {

    private static final String TAG = "WindowHostActivity";

    /** Static callback: when non-null, back event will invoke it and finish. */
    public static Runnable onBackCallback;

    /** Static instance reference so Service can call finish() directly */
    public static WindowHostActivity instance;

    private boolean handled = false;

    /**
     * 零时长进/出转场：API 34+ 用 overrideActivityTransition（overridePendingTransition 已废弃，
     * 且部分机型接收方调用不生效，需配合启动方 ActivityOptions）；以下版本回退 overridePendingTransition。
     */
    private void suppressTransition(int overrideType) {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(overrideType, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        // Eliminate enter transition animation
        suppressTransition(Activity.OVERRIDE_TRANSITION_OPEN);
        suppressTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
        Log.d(TAG, "onCreate - task=" + getTaskId());

        // Make system bars transparent
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility()
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        // Fullscreen transparent layout - ensures Activity has visible window area
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        v.setBackgroundColor(Color.TRANSPARENT);
        setContentView(v);

        // 小窗检测（vivo 小窗不走标准多窗口回调，isInMultiWindowMode 恒为 false）：
        // 本 Activity 恒全屏，窗口 bounds 变小即被拖入小窗/分屏 → 立即收回
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (isShrunkenWindow() && !isFinishing()) {
                            Log.d(TAG, "shrunken window detected (freeform?), finishing host");
                            suppressTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
                            finish();
                        }
                    }
                });

        // Register back callback via OnBackPressedDispatcher (handles both button and gesture)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "handleOnBackPressed called (dispatcher)");
                handleBack();
            }
        });

        // If recreated by system (e.g. config change), just finish
        if (savedInstanceState != null) {
            Log.d(TAG, "Recreated by system, finishing");
            suppressTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
            finish();
        }
    }

    /** vivo 小窗不走标准多窗口回调（isInMultiWindowMode=false），用窗口尺寸识别：本窗口恒全屏，变小即小窗/分屏 */
    private boolean isShrunkenWindow() {
        android.graphics.Rect win = getWindowManager().getCurrentWindowMetrics().getBounds();
        android.graphics.Rect max = getWindowManager().getMaximumWindowMetrics().getBounds();
        return win.width() < max.width() * 0.92f || win.height() < max.height() * 0.92f;
    }

    private void handleBack() {
        if (handled) return;
        handled = true;

        Log.d(TAG, "Back event received, invoking callback");

        Runnable callback = onBackCallback;
        onBackCallback = null;

        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                Log.e(TAG, "Error in back callback", e);
            }
            // 有回调时让回调自己决定何时 finish（collapse 通过 finishWindowHostActivity 在动画完成后 finish）
            // 过早 finish 会导致 Activity 窗口在收起动画过程中被移除，可能闪现白色背景
            Log.d(TAG, "Back callback executed, skipping immediate finish (let callback manage lifecycle)");
        } else {
            // 没有回调时直接 finish
            Log.d(TAG, "No callback registered, finishing directly");
            finishActivity();
        }
    }

    private void finishActivity() {
        suppressTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
        finishAndRemoveTask();
    }

    /** 无过渡关闭：供 FloatWindowService 收起流程直接调用（API 34+ overrideActivityTransition CLOSE，以下回退 overridePendingTransition） */
    public void finishWithoutTransition() {
        suppressTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
        finishAndRemoveTask();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // singleTask 复用：重置 handled 以接收下一次返回事件
        handled = false;
        Log.d(TAG, "onNewIntent - handled reset for next back");
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, android.content.res.Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        // 小窗/分屏兜底：manifest 已声明 resizeableActivity=false，个别 ROM 手势仍强拆时立即收回
        if (isInMultiWindowMode && !isFinishing()) {
            Log.d(TAG, "entered multi-window mode, finishing host");
            suppressTransition(Activity.OVERRIDE_TRANSITION_CLOSE);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - hasWindowFocus=" + hasWindowFocus()
                + ", isFinishing=" + isFinishing()
                + ", callback=" + (onBackCallback != null));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop - isFinishing=" + isFinishing());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
    }

    @Override
    protected void onDestroy() {
        // 只清理 instance 当它仍指向本实例时，防止后续新 Activity 实例被误清除
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
        // 同样只在无后继实例时才清理回调，避免清除新 Activity 设置的 onBackCallback
        if (!handled && instance == null) {
            Log.d(TAG, "Destroyed without back press, cleaning callback");
            onBackCallback = null;
        }
    }
}
