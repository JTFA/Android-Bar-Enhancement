package com.example.aiwindow;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        // Eliminate enter transition animation
        overridePendingTransition(0, 0);
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
            finish();
            overridePendingTransition(0, 0);
        }
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
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // singleTask 复用：重置 handled 以接收下一次返回事件
        handled = false;
        Log.d(TAG, "onNewIntent - handled reset for next back");
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
        instance = null;
        super.onDestroy();
        if (!handled) {
            Log.d(TAG, "Destroyed without back press, cleaning callback");
            onBackCallback = null;
        }
    }
}
