package com.jtfa.barenhancement;

import android.app.ActivityManager;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_RUNTIME_PERMISSIONS = 1002;

    // 用户对悬浮窗服务开关的意图（持久化，覆盖安装/进程被杀后据此自动恢复）
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    
    private Switch switchService;
    private Switch switchBootStart;
    private Switch switchExcludeRecents;
    private TextView textOverlayStatus;
    private TriggerActionsController triggerActionsController;
    private SharedPreferences prefs;

    // 代码里 setChecked() 会同步触发监听器，刷新状态时用它屏蔽
    private boolean suppressServiceSwitchCallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化 SharedPreferences
        prefs = getSharedPreferences("ai_window_settings", MODE_PRIVATE);
        
        setContentView(R.layout.activity_main);

        switchService = findViewById(R.id.switch_service);
        switchBootStart = findViewById(R.id.switch_boot_start);
        switchExcludeRecents = findViewById(R.id.switch_exclude_recents);
        textOverlayStatus = findViewById(R.id.text_overlay_status);
        Button btnOpenSettings = findViewById(R.id.btn_open_settings);
        Button btnEditShortcuts = findViewById(R.id.btn_edit_shortcuts);

        // 快捷方式编辑入口（原设置页按钮，随触发卡搬到首页）
        btnEditShortcuts.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ShortcutEditActivity.class)));

        // 交互与样式设置卡片（原设置页触发行为，搬到首页后由控制器负责读写）
        triggerActionsController = new TriggerActionsController(this, prefs);

        // 悬浮窗服务开关
        updateServiceSwitchState();
        switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressServiceSwitchCallback) return;
            // 先记录用户意图：覆盖安装/进程被系统杀掉后据此自动恢复
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, isChecked).apply();
            if (isChecked) {
                // 开启服务
                if (hasOverlayPermission()) {
                    requestRuntimePermissionsThenStart();
                } else {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                    checkOverlayPermission();
                    prefs.edit().putBoolean(KEY_SERVICE_ENABLED, false).apply();
                    setServiceSwitchSilently(false); // 恢复开关状态（不回调）
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
        // 冷启动时把任务属性与偏好对齐（从桌面进入的 Intent 已带标志，这里兜底）
        applyExcludeFromRecents(excludeFromRecentsSetting);
        switchExcludeRecents.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("exclude_from_recents", isChecked).apply();
            // 直接修改当前任务属性，立即生效，不需要重启应用
            applyExcludeFromRecents(isChecked);
            Toast.makeText(this,
                    isChecked ? "已开启：应用已从最近任务中隐藏" : "已关闭：应用将在最近任务中显示",
                    Toast.LENGTH_SHORT).show();
            Log.d("MainActivity", "最近任务隐藏开关: " + isChecked);
        });
        
        // 打开设置页面按钮点击事件
        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    /**
     * 运行时修改本应用任务是否出现在最近任务中。
     *
     * 启动 Intent 的 FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS 只在创建任务时生效，
     * 这里直接改任务属性，切换开关立即生效，不需要重启应用。
     * 系统最近任务列表在下次打开时刷新；个别 OEM 桌面可能缓存卡片。
     */
    private void applyExcludeFromRecents(boolean exclude) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) {
                Log.w("MainActivity", "ActivityManager 不可用，跳过最近任务设置");
                return;
            }
            int count = 0;
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                task.setExcludeFromRecents(exclude);
                count++;
            }
            Log.d("MainActivity", "setExcludeFromRecents(" + exclude + ") 已应用，任务数=" + count);
        } catch (Exception e) {
            Log.e("MainActivity", "设置最近任务隐藏失败", e);
        }
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
        // Android 13+：申请通知权限（前台服务通知/诊断通知需要；被拒绝也会继续启动服务）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_RUNTIME_PERMISSIONS);
            return;
        }
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
     * 更新服务开关状态（纯读取，不触发启动/停止）
     */
    private void updateServiceSwitchState() {
        boolean isRunning = isServiceRunning(FloatWindowService.class);
        // 迁移：老版本没有持久化标志，服务在跑就视为用户开启过
        if (isRunning && !prefs.contains(KEY_SERVICE_ENABLED)) {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, true).apply();
        }
        setServiceSwitchSilently(isRunning);
        Log.d("MainActivity", "悬浮窗服务状态: " + (isRunning ? "运行中" : "未运行"));
    }

    /**
     * 静默设置开关（不触发 OnCheckedChangeListener）
     */
    private void setServiceSwitchSilently(boolean checked) {
        suppressServiceSwitchCallback = true;
        switchService.setChecked(checked);
        suppressServiceSwitchCallback = false;
    }

    /**
     * 按用户意图对账服务状态：意图为开、权限还在、服务没跑 → 自动拉起。
     * 覆盖安装（进程被杀）或服务被系统回收后，重新打开应用即可恢复。
     */
    private void refreshServiceState() {
        boolean enabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false);
        boolean isRunning = isServiceRunning(FloatWindowService.class);

        if (isRunning && !prefs.contains(KEY_SERVICE_ENABLED)) {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, true).apply();
            enabled = true;
        }
        if (!isRunning && enabled && hasOverlayPermission()) {
            Log.d("MainActivity", "按已保存的开关意图自动恢复悬浮窗服务");
            isRunning = startFloatWindowService();
        }
        setServiceSwitchSilently(isRunning);
        Log.d("MainActivity", "悬浮窗服务状态: " + (isRunning ? "运行中" : "未运行")
                + "，用户意图: " + (enabled ? "开" : "关"));
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
     *
     * @return 启动命令是否发送成功
     */
    private boolean startFloatWindowService() {
        Log.d("MainActivity", "准备启动 FloatWindowService");
        Intent serviceIntent = new Intent(this, FloatWindowService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Log.e("MainActivity", "启动悬浮窗服务失败", e);
            Toast.makeText(this, "悬浮窗服务启动失败，请检查自启动/后台运行权限", Toast.LENGTH_SHORT).show();
            return false;
        }
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
        updateOverlayStatus();
        // 每次回到页面时检查权限状态和服务状态
        if (hasOverlayPermission()) {
            Toast.makeText(this, "当前已拥有悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
        // 更新服务开关状态：若用户上次开着且服务没跑（覆盖安装/被系统回收），自动恢复
        refreshServiceState();
    }

    /** 刷新首页「悬浮窗权限」状态行 */
    private void updateOverlayStatus() {
        boolean granted = hasOverlayPermission();
        textOverlayStatus.setText(granted ? "✓ 已授予" : "✗ 未授予");
        textOverlayStatus.setTextColor(granted ? 0xFF4CAF50 : 0xFFF44336);
    }
}
