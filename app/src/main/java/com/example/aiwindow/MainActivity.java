package com.example.aiwindow;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_RUNTIME_PERMISSIONS = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnCheckPermission = findViewById(R.id.btn_check_permission);
        Button btnStartService = findViewById(R.id.btn_start_service);
        Button btnOpenSettings = findViewById(R.id.btn_open_settings);
        Button btnTestSimple = findViewById(R.id.btn_test_simple);

        // 检查权限按钮点击事件
        btnCheckPermission.setOnClickListener(v -> checkOverlayPermission());

        // 启动服务按钮点击事件
        btnStartService.setOnClickListener(v -> {
            if (hasOverlayPermission()) {
                requestRuntimePermissionsThenStart();
            } else {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                checkOverlayPermission();
            }
        });
        
        // 打开设置页面按钮点击事件
        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // 测试简化版服务
        btnTestSimple.setOnClickListener(v -> {
            if (hasOverlayPermission()) {
                startSimpleService();
            } else {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                checkOverlayPermission();
            }
        });
    }

    /**
     * 检查是否有悬浮窗权限
     */
    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    /**
     * 检查并申请悬浮窗权限
     */
    private void checkOverlayPermission() {
        if (hasOverlayPermission()) {
            Toast.makeText(this, "已拥有悬浮窗权限", Toast.LENGTH_SHORT).show();
        } else {
            // 引导用户跳转到系统设置页开启权限
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
        }
    }

    /**
     * 请求麦克风和相机运行时权限，然后启动服务
     */
    private void requestRuntimePermissionsThenStart() {
        String[] permissions = {
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
        };
        boolean needRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_RUNTIME_PERMISSIONS);
        } else {
            startFloatWindowService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_RUNTIME_PERMISSIONS) {
            // Regardless of result, start the service (permissions can be granted later)
            startFloatWindowService();
        }
    }

    /**
     * 启动悬浮窗前台服务
     */
    private void startFloatWindowService() {
        Log.d("MainActivity", "准备启动 FloatWindowService");
        Intent serviceIntent = new Intent(this, FloatWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show();
        finish(); // 关闭Activity，让悬浮窗在后台运行
    }
    
    /**
     * 启动简化测试服务
     */
    private void startSimpleService() {
        Log.d("MainActivity", "准备启动 SimpleFloatService（红色测试条）");
        Intent serviceIntent = new Intent(this, SimpleFloatService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "简化测试服务已启动，请查看底部红色条", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "权限授予成功！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到页面时检查权限状态
        if (hasOverlayPermission()) {
            Toast.makeText(this, "当前已拥有悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
    }
}
