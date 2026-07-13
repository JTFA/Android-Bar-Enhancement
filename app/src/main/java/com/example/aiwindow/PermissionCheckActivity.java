package com.example.aiwindow;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限管理页面：列出应用所需的全部权限及获取状态，支持按需授予。
 */
public class PermissionCheckActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY = 2001;
    private static final int REQUEST_CODE_RUNTIME = 2002;

    private LinearLayout container;

    /** 权限条目数据 */
    private static class PermItem {
        final String name;        // 显示名称
        final String desc;        // 用途描述
        final String androidPerm; // Android 权限常量（悬浮窗用 null 标识）
        final int minSdk;         // 最低 API 级别（低于则不显示）
        final int maxSdk;         // 最高 API 级别（高于则不显示，0 表示不限）

        PermItem(String name, String desc, String androidPerm, int minSdk, int maxSdk) {
            this.name = name;
            this.desc = desc;
            this.androidPerm = androidPerm;
            this.minSdk = minSdk;
            this.maxSdk = maxSdk;
        }
    }

    private final PermItem[] permItems = new PermItem[]{
        new PermItem("悬浮窗权限", "在其它应用上方显示悬浮窗", null, 0, 0),
        new PermItem("麦克风权限", "语音输入、语音搜索等功能", Manifest.permission.RECORD_AUDIO, 0, 0),
        new PermItem("相机权限", "拍照上传、扫码等功能", Manifest.permission.CAMERA, 0, 0),
        new PermItem("读取存储权限", "选择本地文件、图片上传", Manifest.permission.READ_EXTERNAL_STORAGE, 0, 32),
        new PermItem("写入存储权限", "下载文件保存到本地", Manifest.permission.WRITE_EXTERNAL_STORAGE, 0, 28),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_check);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        container = findViewById(R.id.permission_list_container);
        buildPermissionList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到页面时刷新状态（从系统设置返回后自动更新）
        container.removeAllViews();
        buildPermissionList();
    }

    /** 动态构建权限列表 */
    private void buildPermissionList() {
        for (PermItem item : permItems) {
            // 检查 SDK 版本过滤
            if (Build.VERSION.SDK_INT < item.minSdk) continue;
            if (item.maxSdk > 0 && Build.VERSION.SDK_INT > item.maxSdk) continue;

            boolean granted = checkGranted(item);
            addPermissionRow(container, item, granted);
        }
    }

    /** 检查某条权限是否已授予 */
    private boolean checkGranted(PermItem item) {
        if (item.androidPerm == null) {
            // 悬浮窗权限
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        } else {
            return ContextCompat.checkSelfPermission(this, item.androidPerm) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /** 向容器中添加一条权限行 */
    private void addPermissionRow(LinearLayout parent, PermItem item, boolean granted) {
        // 卡片容器
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(resolveThemeColor(R.attr.bgCard));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(resolveThemeColor(R.attr.bgCard));
        cardBg.setCornerRadius(dp2px(10));
        card.setBackground(cardBg);
        int padH = dp2px(16), padV = dp2px(14);
        card.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp2px(10);
        card.setLayoutParams(cardLp);

        // 第一行：名称 + 状态
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText(item.name);
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvName.setTextColor(resolveThemeColor(R.attr.textPrimary));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row1.addView(tvName, nameLp);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(granted ? "✓ 已授予" : "✗ 未授予");
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvStatus.setTextColor(granted ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        row1.addView(tvStatus);

        card.addView(row1);

        // 第二行：描述
        TextView tvDesc = new TextView(this);
        tvDesc.setText(item.desc);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvDesc.setTextColor(resolveThemeColor(R.attr.textSecondary));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp2px(4);
        card.addView(tvDesc, descLp);

        // 第三行：获取按钮（仅未授予时显示）
        if (!granted) {
            Button btnGrant = new Button(this);
            btnGrant.setText("获取");
            btnGrant.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            btnGrant.setTextColor(Color.WHITE);
            btnGrant.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.teal_700));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnLp.topMargin = dp2px(10);
            btnLp.gravity = Gravity.END;
            card.addView(btnGrant, btnLp);

            btnGrant.setOnClickListener(v -> requestPermission(item));
        }

        parent.addView(card);
    }

    /** 发起权限申请 */
    private void requestPermission(PermItem item) {
        if (item.androidPerm == null) {
            // 悬浮窗权限：跳转系统设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            }
        } else {
            // 运行时权限：弹出系统授权对话框
            ActivityCompat.requestPermissions(this, new String[]{item.androidPerm}, REQUEST_CODE_RUNTIME);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_RUNTIME) {
            // 刷新列表
            container.removeAllViews();
            buildPermissionList();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY) {
            // 从系统设置返回后 onResume 会自动刷新
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int resolveThemeColor(int attrResId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private int dp2px(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
