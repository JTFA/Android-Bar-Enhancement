package com.example.aiwindow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_RUNTIME_PERMISSIONS = 1002;
    
    private Switch switchService;
    private Switch switchBootStart;
    private Switch switchExcludeRecents;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化 SharedPreferences
        prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        
        setContentView(R.layout.activity_main);

        Button btnCheckPermission = findViewById(R.id.btn_check_permission);
        switchService = findViewById(R.id.switch_service);
        switchBootStart = findViewById(R.id.switch_boot_start);
        switchExcludeRecents = findViewById(R.id.switch_exclude_recents);
        Button btnOpenSettings = findViewById(R.id.btn_open_settings);
        Button btnTestSimple = findViewById(R.id.btn_test_simple);

        // 检查权限按钮点击事件：跳转到权限管理页面
        btnCheckPermission.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PermissionCheckActivity.class));
        });

        // 悬浮窗服务开关
        updateServiceSwitchState();
        switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 开启服务
                if (hasOverlayPermission()) {
                    requestRuntimePermissionsThenStart();
                } else {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                    checkOverlayPermission();
                    switchService.setChecked(false); // 恢复开关状态
                }
            } else {
                // 关闭服务
                stopFloatWindowService();
            }
        });
        
        // 开机自启开关
        boolean bootStartEnabled = prefs.getBoolean("boot_start_enabled", false);
        switchBootStart.setChecked(bootStartEnabled);
        switchBootStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("boot_start_enabled", isChecked).apply();
            Toast.makeText(this, isChecked ? "已开启开机自启" : "已关闭开机自启", Toast.LENGTH_SHORT).show();
        });
        
        // 在最近任务中隐藏开关
        boolean excludeFromRecentsSetting = prefs.getBoolean("exclude_from_recents", false);
        switchExcludeRecents.setChecked(excludeFromRecentsSetting);
        switchExcludeRecents.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("exclude_from_recents", isChecked).apply();
            
            if (isChecked) {
                // 显示对话框，询问是否立即重启
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("启用隐藏功能")
                    .setMessage("需要在最近任务中隐藏MainActivity。\n\n是否立即重启应用以应用此设置？")
                    .setPositiveButton("立即重启", (dialog, which) -> {
                        Intent intent = new Intent(this, SplashActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("稍后重启", (dialog, which) -> {
                        Toast.makeText(this, "已保存设置，下次启动应用时生效", Toast.LENGTH_SHORT).show();
                    })
                    .show();
                Log.d("MainActivity", "已在最近任务中隐藏开关打开");
            } else {
                Toast.makeText(this, "已关闭：应用将在最近任务中显示", Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "已在最近任务中隐藏开关关闭");
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
        // AI 窗口功能隐藏期间：不再申请麦克风/相机权限，直接启动服务
        if (!AppConfig.AI_WINDOW_ENABLED) {
            startFloatWindowService();
            return;
        }
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
     * 更新服务开关状态
     */
    private void updateServiceSwitchState() {
        boolean isRunning = isServiceRunning(FloatWindowService.class);
        switchService.setChecked(isRunning);
        Log.d("MainActivity", "悬浮窗服务状态: " + (isRunning ? "运行中" : "未运行"));
    }
    
    /**
     * 检查服务是否正在运行
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 停止悬浮窗前台服务
     */
    private void stopFloatWindowService() {
        Log.d("MainActivity", "准备停止 FloatWindowService");
        Intent serviceIntent = new Intent(this, FloatWindowService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "悬浮窗服务已停止", Toast.LENGTH_SHORT).show();
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
//        finish(); // 关闭Activity，让悬浮窗在后台运行
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
        // 每次回到页面时检查权限状态和服务状态
        if (hasOverlayPermission()) {
            Toast.makeText(this, "当前已拥有悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
        // 更新服务开关状态
        updateServiceSwitchState();
    }
}
