package com.jtfa.barenhancement;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;

/**
 * 横屏设置项容器：左侧「与竖屏同步」勾选框，右侧原设置内容。
 * 勾选同步或高级锁定时，内容置灰并拦截触摸；勾选框本身仍可操作。
 */
public class SyncRowLayout extends LinearLayout {

    public interface OnSyncChangedListener {
        void onSyncChanged(SyncRowLayout row, boolean checked);
    }

    private CheckBox checkBox;
    private LinearLayout content;
    private boolean initialized;
    private boolean locked;
    private boolean syncUiVisible;
    private boolean internalChange;
    private OnSyncChangedListener listener;

    public SyncRowLayout(Context context) {
        super(context);
        init(context);
    }

    public SyncRowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SyncRowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBaselineAligned(false);

        checkBox = new CheckBox(context);
        checkBox.setText("与竖屏同步");
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        checkBox.setTextColor(resolveThemeColor(context, R.attr.textSecondary));
        checkBox.setButtonTintList(ColorStateList.valueOf(
                resolveThemeColor(context, R.attr.colorPrimaryBrand)));
        checkBox.setMinWidth(0);
        checkBox.setMinimumWidth(0);
        checkBox.setPadding(0, 0, dp(context, 8), 0);
        checkBox.setVisibility(GONE);
        super.addView(checkBox, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        super.addView(content, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        initialized = true;
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChange || locked) return;
            updateContentState();
            if (listener != null) {
                listener.onSyncChanged(this, isChecked);
            }
        });
    }

    private static int resolveThemeColor(Context context, int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (initialized && content != null && child != checkBox && child != content) {
            content.addView(child, index, params);
        } else {
            super.addView(child, index, params);
        }
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (initialized && content != null && child != checkBox && child != content) {
            content.addView(child, params);
        } else {
            super.addView(child, params);
        }
    }

    @Override
    public void addView(View child, int index) {
        if (initialized && content != null && child != checkBox && child != content) {
            content.addView(child, index);
        } else {
            super.addView(child, index);
        }
    }

    @Override
    public void addView(View child) {
        if (initialized && content != null && child != checkBox && child != content) {
            content.addView(child);
        } else {
            super.addView(child);
        }
    }

    public void setSyncUiVisible(boolean visible) {
        syncUiVisible = visible;
        checkBox.setVisibility(visible ? VISIBLE : GONE);
        updateContentState();
    }

    public boolean isSyncUiVisible() {
        return syncUiVisible;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        internalChange = true;
        checkBox.setChecked(locked || checkBox.isChecked());
        internalChange = false;
        checkBox.setEnabled(!locked);
        updateContentState();
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isSynced() {
        return checkBox.isChecked();
    }

    public void setSynced(boolean synced) {
        if (checkBox.isChecked() != synced) {
            internalChange = true;
            checkBox.setChecked(synced);
            internalChange = false;
        }
        updateContentState();
    }

    public void setOnSyncChangedListener(OnSyncChangedListener listener) {
        this.listener = listener;
    }

    private void updateContentState() {
        boolean blocked = locked || (syncUiVisible && checkBox.isChecked());
        content.setAlpha(blocked ? 0.4f : 1f);
        // 高级项固定锁定时勾选框也淡一点，表示不可操作
        checkBox.setAlpha(locked ? 0.6f : 1f);
        if (blocked) {
            // 同步/锁定时内容不可编辑，顺手收起可能残留的输入焦点与键盘
            View focused = content.findFocus();
            if (focused != null) {
                focused.clearFocus();
            }
        }
        invalidate();
    }

    private boolean isBlocked() {
        return locked || (syncUiVisible && checkBox.isChecked());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isBlocked()) {
            return super.onInterceptTouchEvent(ev);
        }
        if (!locked && syncUiVisible) {
            float x = ev.getX();
            float y = ev.getY();
            if (x >= checkBox.getLeft() && x <= checkBox.getRight()
                    && y >= checkBox.getTop() && y <= checkBox.getBottom()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isBlocked()) {
            return true;
        }
        return super.onTouchEvent(event);
    }
}
