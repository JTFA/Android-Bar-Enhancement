package com.jtfa.barenhancement;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 首页「交互与样式设置」卡片的控制器。
 *
 * 原先这段逻辑在 SettingsActivity 里，卡片搬到首页后由本类独立负责：
 * 读取 / 保存触发行为、应用选择、启动配置、振动、窗口打开，
 * 并把改动即时写入 SharedPreferences 与通知 FloatWindowService。
 */
public class TriggerActionsController {

    private static final String KEY_CLICK_APP_PACKAGE = "click_app_package";
    private static final String KEY_CLICK_APP_NAME = "click_app_name";
    private static final String KEY_CLICK_APP_CLASS = "click_app_class";
    private static final String KEY_CLICK_ACTION = "click_action";
    private static final String KEY_DOUBLE_CLICK_ACTION = "double_click_action";
    private static final String KEY_LONG_PRESS_ACTION = "long_press_action";
    private static final String KEY_DOUBLE_CLICK_APP_PACKAGE = "double_click_app_package";
    private static final String KEY_DOUBLE_CLICK_APP_NAME = "double_click_app_name";
    private static final String KEY_DOUBLE_CLICK_APP_CLASS = "double_click_app_class";
    private static final String KEY_LONG_PRESS_APP_PACKAGE = "long_press_app_package";
    private static final String KEY_LONG_PRESS_APP_NAME = "long_press_app_name";
    private static final String KEY_LONG_PRESS_APP_CLASS = "long_press_app_class";
    private static final String KEY_CLICK_OPEN_IN_WINDOW = "click_open_in_window";
    private static final String KEY_DOUBLE_CLICK_OPEN_IN_WINDOW = "double_click_open_in_window";
    private static final String KEY_LONG_PRESS_OPEN_IN_WINDOW = "long_press_open_in_window";
    private static final String KEY_CLICK_VIBRATE = "click_vibrate_enabled";
    private static final String KEY_DOUBLE_CLICK_VIBRATE = "double_click_vibrate_enabled";
    private static final String KEY_LONG_PRESS_VIBRATE = "long_press_vibrate_enabled";
    private static final String KEY_APP_SELECTOR_SHOWN = "app_selector_first_time_shown";

    private static final String ACTION_NONE = "none";
    private static final String ACTION_APP = "app";
    private static final String ACTION_SHORTCUT = "shortcut";
    private static final String ACTION_AI = "ai";

    private final Activity activity;
    private final SharedPreferences preferences;

    private Spinner spinnerClickAction, spinnerDoubleClickAction, spinnerLongPressAction;
    private LinearLayout layoutClickApp, layoutDoubleClickApp, layoutLongPressApp;
    private TextView textClickSelectedApp, textDoubleClickSelectedApp, textLongPressSelectedApp;

    private String clickAction = "";
    private String doubleClickAction = "";
    private String longPressAction = "";
    private String clickAppPackage = "";
    private String clickAppName = "";
    private String clickAppClass = "";
    private String doubleClickAppPackage = "";
    private String doubleClickAppName = "";
    private String doubleClickAppClass = "";
    private String longPressAppPackage = "";
    private String longPressAppName = "";
    private String longPressAppClass = "";

    private LinearLayout layoutClickOpenInWindow, layoutDoubleClickOpenInWindow, layoutLongPressOpenInWindow;
    private Switch switchClickOpenInWindow, switchDoubleClickOpenInWindow, switchLongPressOpenInWindow;
    private LinearLayout layoutClickVibrate, layoutDoubleClickVibrate, layoutLongPressVibrate;
    private Switch switchClickVibrate, switchDoubleClickVibrate, switchLongPressVibrate;
    private LinearLayout layoutClickLaunch, layoutDoubleClickLaunch, layoutLongPressLaunch;
    private TextView textClickLaunchSummary, textDoubleClickLaunchSummary, textLongPressLaunchSummary;

    private boolean isAppPickerSelecting = false;
    private String appPickerTarget = "";
    private AlertDialog currentAppPickerDialog = null;
    private ListView currentAppPickerListView = null;
    private AppPickerAdapter currentAppPickerAdapter = null;

    /** 初始化期间抑制监听器回写 */
    private boolean loading = true;

    public TriggerActionsController(Activity activity, SharedPreferences preferences) {
        this.activity = activity;
        this.preferences = preferences;
        bindViews();
        setupSpinners();
        setupListeners();
        load();
        loading = false;
    }

    private void bindViews() {
        spinnerClickAction = activity.findViewById(R.id.spinner_click_action);
        layoutClickApp = activity.findViewById(R.id.layout_click_app);
        textClickSelectedApp = activity.findViewById(R.id.text_click_selected_app);

        spinnerDoubleClickAction = activity.findViewById(R.id.spinner_double_click_action);
        layoutDoubleClickApp = activity.findViewById(R.id.layout_double_click_app);
        textDoubleClickSelectedApp = activity.findViewById(R.id.text_double_click_selected_app);

        spinnerLongPressAction = activity.findViewById(R.id.spinner_long_press_action);
        layoutLongPressApp = activity.findViewById(R.id.layout_long_press_app);
        textLongPressSelectedApp = activity.findViewById(R.id.text_long_press_selected_app);

        layoutClickOpenInWindow = activity.findViewById(R.id.layout_click_open_in_window);
        switchClickOpenInWindow = activity.findViewById(R.id.switch_click_open_in_window);
        layoutDoubleClickOpenInWindow = activity.findViewById(R.id.layout_double_click_open_in_window);
        switchDoubleClickOpenInWindow = activity.findViewById(R.id.switch_double_click_open_in_window);
        layoutLongPressOpenInWindow = activity.findViewById(R.id.layout_long_press_open_in_window);
        switchLongPressOpenInWindow = activity.findViewById(R.id.switch_long_press_open_in_window);

        layoutClickVibrate = activity.findViewById(R.id.layout_click_vibrate);
        switchClickVibrate = activity.findViewById(R.id.switch_click_vibrate);
        layoutDoubleClickVibrate = activity.findViewById(R.id.layout_double_click_vibrate);
        switchDoubleClickVibrate = activity.findViewById(R.id.switch_double_click_vibrate);
        layoutLongPressVibrate = activity.findViewById(R.id.layout_long_press_vibrate);
        switchLongPressVibrate = activity.findViewById(R.id.switch_long_press_vibrate);

        layoutClickLaunch = activity.findViewById(R.id.layout_click_launch);
        textClickLaunchSummary = activity.findViewById(R.id.text_click_launch_summary);
        layoutDoubleClickLaunch = activity.findViewById(R.id.layout_double_click_launch);
        textDoubleClickLaunchSummary = activity.findViewById(R.id.text_double_click_launch_summary);
        layoutLongPressLaunch = activity.findViewById(R.id.layout_long_press_launch);
        textLongPressLaunchSummary = activity.findViewById(R.id.text_long_press_launch_summary);
    }

    private void setupSpinners() {
        String[] labels = AppConfig.AI_WINDOW_ENABLED
                ? new String[]{
                    activity.getString(R.string.action_none),
                    activity.getString(R.string.action_open_app),
                    activity.getString(R.string.action_open_shortcut),
                    activity.getString(R.string.action_open_ai)
                }
                : new String[]{
                    activity.getString(R.string.action_none),
                    activity.getString(R.string.action_open_app),
                    activity.getString(R.string.action_open_shortcut)
                };
        spinnerClickAction.setAdapter(newActionAdapter(labels));
        spinnerDoubleClickAction.setAdapter(newActionAdapter(labels));
        spinnerLongPressAction.setAdapter(newActionAdapter(labels));
    }

    private ArrayAdapter<String> newActionAdapter(String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupListeners() {
        final String[] actionKeys = {ACTION_NONE, ACTION_APP, ACTION_SHORTCUT, ACTION_AI};

        AdapterView.OnItemSelectedListener actionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (loading) return;
                int spinnerId = parent.getId();
                String selectedAction = actionKeys[position];
                if (spinnerId == R.id.spinner_click_action) {
                    clickAction = selectedAction;
                } else if (spinnerId == R.id.spinner_double_click_action) {
                    doubleClickAction = selectedAction;
                } else if (spinnerId == R.id.spinner_long_press_action) {
                    longPressAction = selectedAction;
                }
                updateAppSelectorVisibility();
                saveAndApply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        spinnerClickAction.setOnItemSelectedListener(actionListener);
        spinnerDoubleClickAction.setOnItemSelectedListener(actionListener);
        spinnerLongPressAction.setOnItemSelectedListener(actionListener);

        switchClickVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!loading) saveAndApply();
        });
        switchDoubleClickVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!loading) saveAndApply();
        });
        switchLongPressVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!loading) saveAndApply();
        });
        switchClickOpenInWindow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!loading) saveAndApply();
        });
        switchDoubleClickOpenInWindow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!loading) saveAndApply();
        });
        switchLongPressOpenInWindow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!loading) saveAndApply();
        });

        layoutClickApp.setOnClickListener(v -> startAppPicker("click"));
        layoutDoubleClickApp.setOnClickListener(v -> startAppPicker("double_click"));
        layoutLongPressApp.setOnClickListener(v -> startAppPicker("long_press"));

        layoutClickLaunch.setOnClickListener(v -> openLaunchConfig("click"));
        layoutDoubleClickLaunch.setOnClickListener(v -> openLaunchConfig("double_click"));
        layoutLongPressLaunch.setOnClickListener(v -> openLaunchConfig("long_press"));
    }

    private void load() {
        clickAction = preferences.getString(KEY_CLICK_ACTION, "");
        if (TextUtils.isEmpty(clickAction)) {
            String savedPkg = preferences.getString(KEY_CLICK_APP_PACKAGE, "");
            clickAction = TextUtils.isEmpty(savedPkg) ? ACTION_NONE : ACTION_APP;
        }
        clickAction = resolveEnabledAction(clickAction);
        spinnerClickAction.setSelection(getActionIndex(clickAction));

        doubleClickAction = resolveEnabledAction(
                preferences.getString(KEY_DOUBLE_CLICK_ACTION, ACTION_SHORTCUT));
        spinnerDoubleClickAction.setSelection(getActionIndex(doubleClickAction));

        longPressAction = preferences.getString(KEY_LONG_PRESS_ACTION,
                AppConfig.AI_WINDOW_ENABLED ? ACTION_AI : ACTION_NONE);
        longPressAction = resolveEnabledAction(longPressAction);
        spinnerLongPressAction.setSelection(getActionIndex(longPressAction));

        clickAppPackage = preferences.getString(KEY_CLICK_APP_PACKAGE, "");
        clickAppName = preferences.getString(KEY_CLICK_APP_NAME, "");
        clickAppClass = preferences.getString(KEY_CLICK_APP_CLASS, "");
        textClickSelectedApp.setText(TextUtils.isEmpty(clickAppName)
                ? activity.getString(R.string.not_selected) : clickAppName);

        doubleClickAppPackage = preferences.getString(KEY_DOUBLE_CLICK_APP_PACKAGE, "");
        doubleClickAppName = preferences.getString(KEY_DOUBLE_CLICK_APP_NAME, "");
        doubleClickAppClass = preferences.getString(KEY_DOUBLE_CLICK_APP_CLASS, "");
        textDoubleClickSelectedApp.setText(TextUtils.isEmpty(doubleClickAppName)
                ? activity.getString(R.string.not_selected) : doubleClickAppName);

        longPressAppPackage = preferences.getString(KEY_LONG_PRESS_APP_PACKAGE, "");
        longPressAppName = preferences.getString(KEY_LONG_PRESS_APP_NAME, "");
        longPressAppClass = preferences.getString(KEY_LONG_PRESS_APP_CLASS, "");
        textLongPressSelectedApp.setText(TextUtils.isEmpty(longPressAppName)
                ? activity.getString(R.string.not_selected) : longPressAppName);

        switchClickOpenInWindow.setChecked(preferences.getBoolean(KEY_CLICK_OPEN_IN_WINDOW, false));
        switchDoubleClickOpenInWindow.setChecked(preferences.getBoolean(KEY_DOUBLE_CLICK_OPEN_IN_WINDOW, false));
        switchLongPressOpenInWindow.setChecked(preferences.getBoolean(KEY_LONG_PRESS_OPEN_IN_WINDOW, false));

        switchClickVibrate.setChecked(preferences.getBoolean(KEY_CLICK_VIBRATE, false));
        switchDoubleClickVibrate.setChecked(preferences.getBoolean(KEY_DOUBLE_CLICK_VIBRATE, false));
        switchLongPressVibrate.setChecked(preferences.getBoolean(KEY_LONG_PRESS_VIBRATE, false));

        updateLaunchConfigSummaries();
        updateAppSelectorVisibility();
    }

    private String resolveEnabledAction(String action) {
        if (!AppConfig.AI_WINDOW_ENABLED && ACTION_AI.equals(action)) {
            return ACTION_NONE;
        }
        return action;
    }

    private int getActionIndex(String action) {
        if (ACTION_NONE.equals(action)) return 0;
        else if (ACTION_APP.equals(action)) return 1;
        else if (ACTION_SHORTCUT.equals(action)) return 2;
        else if (ACTION_AI.equals(action)) return AppConfig.AI_WINDOW_ENABLED ? 3 : 0;
        return 0;
    }

    /** 保存到 SharedPreferences 并即时通知服务（首页没有保存按钮） */
    private void saveAndApply() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_CLICK_ACTION, clickAction);
        editor.putString(KEY_DOUBLE_CLICK_ACTION, doubleClickAction);
        editor.putString(KEY_LONG_PRESS_ACTION, longPressAction);
        editor.putString(KEY_CLICK_APP_PACKAGE, clickAppPackage);
        editor.putString(KEY_CLICK_APP_NAME, clickAppName);
        editor.putString(KEY_CLICK_APP_CLASS, clickAppClass);
        editor.putString(KEY_DOUBLE_CLICK_APP_PACKAGE, doubleClickAppPackage);
        editor.putString(KEY_DOUBLE_CLICK_APP_NAME, doubleClickAppName);
        editor.putString(KEY_DOUBLE_CLICK_APP_CLASS, doubleClickAppClass);
        editor.putString(KEY_LONG_PRESS_APP_PACKAGE, longPressAppPackage);
        editor.putString(KEY_LONG_PRESS_APP_NAME, longPressAppName);
        editor.putString(KEY_LONG_PRESS_APP_CLASS, longPressAppClass);
        editor.putBoolean(KEY_CLICK_OPEN_IN_WINDOW, switchClickOpenInWindow.isChecked());
        editor.putBoolean(KEY_DOUBLE_CLICK_OPEN_IN_WINDOW, switchDoubleClickOpenInWindow.isChecked());
        editor.putBoolean(KEY_LONG_PRESS_OPEN_IN_WINDOW, switchLongPressOpenInWindow.isChecked());
        editor.putBoolean(KEY_CLICK_VIBRATE, switchClickVibrate.isChecked());
        editor.putBoolean(KEY_DOUBLE_CLICK_VIBRATE, switchDoubleClickVibrate.isChecked());
        editor.putBoolean(KEY_LONG_PRESS_VIBRATE, switchLongPressVibrate.isChecked());
        editor.apply();

        Intent intent = new Intent(activity, FloatWindowService.class);
        intent.setAction("UPDATE_TRIGGER_ACTIONS");
        intent.putExtra("click_action", clickAction);
        intent.putExtra("double_click_action", doubleClickAction);
        intent.putExtra("long_press_action", longPressAction);
        intent.putExtra("click_app_package", clickAppPackage);
        intent.putExtra("click_app_name", clickAppName);
        intent.putExtra("click_app_class", clickAppClass);
        intent.putExtra("double_click_app_package", doubleClickAppPackage);
        intent.putExtra("double_click_app_name", doubleClickAppName);
        intent.putExtra("double_click_app_class", doubleClickAppClass);
        intent.putExtra("long_press_app_package", longPressAppPackage);
        intent.putExtra("long_press_app_name", longPressAppName);
        intent.putExtra("long_press_app_class", longPressAppClass);
        intent.putExtra("click_open_in_window", switchClickOpenInWindow.isChecked());
        intent.putExtra("double_click_open_in_window", switchDoubleClickOpenInWindow.isChecked());
        intent.putExtra("long_press_open_in_window", switchLongPressOpenInWindow.isChecked());
        intent.putExtra("click_vibrate", switchClickVibrate.isChecked());
        intent.putExtra("double_click_vibrate", switchDoubleClickVibrate.isChecked());
        intent.putExtra("long_press_vibrate", switchLongPressVibrate.isChecked());
        // 服务未运行时不主动拉起，避免用户还没开开关就出现悬浮条；
        // 设置已写入 SharedPreferences，服务启动时会自行读取。
        if (FloatWindowService.isRunning()) {
            activity.startService(intent);
        }
    }

    private void updateAppSelectorVisibility() {
        layoutClickApp.setVisibility(ACTION_APP.equals(clickAction) ? View.VISIBLE : View.GONE);
        layoutDoubleClickApp.setVisibility(ACTION_APP.equals(doubleClickAction) ? View.VISIBLE : View.GONE);
        layoutLongPressApp.setVisibility(ACTION_APP.equals(longPressAction) ? View.VISIBLE : View.GONE);
        layoutClickLaunch.setVisibility(ACTION_APP.equals(clickAction) ? View.VISIBLE : View.GONE);
        layoutDoubleClickLaunch.setVisibility(ACTION_APP.equals(doubleClickAction) ? View.VISIBLE : View.GONE);
        layoutLongPressLaunch.setVisibility(ACTION_APP.equals(longPressAction) ? View.VISIBLE : View.GONE);
        layoutClickOpenInWindow.setVisibility(ACTION_APP.equals(clickAction) ? View.VISIBLE : View.GONE);
        layoutDoubleClickOpenInWindow.setVisibility(ACTION_APP.equals(doubleClickAction) ? View.VISIBLE : View.GONE);
        layoutLongPressOpenInWindow.setVisibility(ACTION_APP.equals(longPressAction) ? View.VISIBLE : View.GONE);
        layoutClickVibrate.setVisibility(ACTION_NONE.equals(clickAction) ? View.GONE : View.VISIBLE);
        layoutDoubleClickVibrate.setVisibility(ACTION_NONE.equals(doubleClickAction) ? View.GONE : View.VISIBLE);
        layoutLongPressVibrate.setVisibility(ACTION_NONE.equals(longPressAction) ? View.GONE : View.VISIBLE);
    }

    // ===== 应用选择 =====

    private void startAppPicker(String gesture) {
        appPickerTarget = gesture;
        boolean firstTimeShown = preferences.getBoolean(KEY_APP_SELECTOR_SHOWN, false);
        if (!firstTimeShown) {
            showPermissionExplanation();
        } else {
            showAppPicker();
        }
    }

    private void showPermissionExplanation() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.app_permission_title)
                .setMessage(R.string.app_permission_message)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        preferences.edit().putBoolean(KEY_APP_SELECTOR_SHOWN, true).apply();
                        showAppPicker();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAppPicker() {
        if (isAppPickerSelecting) {
            return;
        }
        isAppPickerSelecting = true;

        final AlertDialog loadingDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.select_app)
                .setMessage("正在加载应用列表...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LoadedApps loaded = loadApps();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingDialog.dismiss();
                            showAppPickerDialog(loaded.names, loaded.packages, loaded.classes, loaded.icons);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("TriggerActionsController", "加载应用列表失败", e);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingDialog.dismiss();
                            Toast.makeText(activity, "加载应用列表失败", Toast.LENGTH_SHORT).show();
                            isAppPickerSelecting = false;
                        }
                    });
                }
            }
        }).start();
    }

    private static class LoadedApps {
        String[] names;
        String[] packages;
        String[] classes;
        Drawable[] icons;
    }

    private LoadedApps loadApps() {
        PackageManager pm = activity.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        boolean includeSystemApps = preferences.getBoolean("include_system_apps", false);
        List<ResolveInfo> filteredApps = new ArrayList<>();
        for (ResolveInfo app : apps) {
            if (includeSystemApps || !isSystemApp(pm, app)) {
                filteredApps.add(app);
            }
        }

        final Collator collator = Collator.getInstance(Locale.CHINESE);
        Collections.sort(filteredApps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return collator.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString());
            }
        });

        LoadedApps loaded = new LoadedApps();
        loaded.names = new String[filteredApps.size()];
        loaded.packages = new String[filteredApps.size()];
        loaded.classes = new String[filteredApps.size()];
        loaded.icons = new Drawable[filteredApps.size()];
        for (int i = 0; i < filteredApps.size(); i++) {
            loaded.names[i] = filteredApps.get(i).loadLabel(pm).toString();
            loaded.packages[i] = filteredApps.get(i).activityInfo.packageName;
            loaded.classes[i] = filteredApps.get(i).activityInfo.name;
            loaded.icons[i] = filteredApps.get(i).loadIcon(pm);
        }
        return loaded;
    }

    private boolean isSystemApp(PackageManager pm, ResolveInfo resolveInfo) {
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private class AppPickerAdapter extends android.widget.BaseAdapter {
        private String[] appNames;
        private String[] appPackages;
        private String[] appClasses;
        private Drawable[] appIcons;

        AppPickerAdapter(String[] names, String[] packages, String[] classes, Drawable[] icons) {
            this.appNames = names;
            this.appPackages = packages;
            this.appClasses = classes;
            this.appIcons = icons;
        }

        @Override
        public int getCount() {
            return appNames.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                itemLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

                ImageView iconView = new ImageView(activity);
                iconView.setId(View.generateViewId());
                iconView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)));
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                itemLayout.addView(iconView);

                TextView nameView = new TextView(activity);
                nameView.setId(View.generateViewId());
                nameView.setTextSize(14);
                nameView.setTextColor(0xFF333333);
                nameView.setPadding(dpToPx(12), 0, 0, 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                nameView.setLayoutParams(lp);
                itemLayout.addView(nameView);

                convertView = itemLayout;
            }

            LinearLayout itemLayout = (LinearLayout) convertView;
            ImageView iconView = (ImageView) itemLayout.getChildAt(0);
            TextView nameView = (TextView) itemLayout.getChildAt(1);

            iconView.setImageDrawable(appIcons[position]);
            nameView.setText(appNames[position]);

            return convertView;
        }

        void updateData(String[] names, String[] packages, String[] classes, Drawable[] icons) {
            this.appNames = names;
            this.appPackages = packages;
            this.appClasses = classes;
            this.appIcons = icons;
            notifyDataSetChanged();
        }
    }

    private void showAppPickerDialog(final String[] appNames, final String[] appPackages,
                                     final String[] appClasses, final Drawable[] appIcons) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

        Switch switchSystemApps = new Switch(activity);
        switchSystemApps.setText("显示系统应用");
        switchSystemApps.setChecked(preferences.getBoolean("include_system_apps", false));
        switchSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("include_system_apps", isChecked).apply();
            reloadAppListInDialog();
        });
        layout.addView(switchSystemApps);

        ListView listView = new ListView(activity);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        currentAppPickerListView = listView;
        currentAppPickerAdapter = new AppPickerAdapter(appNames, appPackages, appClasses, appIcons);
        listView.setAdapter(currentAppPickerAdapter);

        layout.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.select_app)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .create();
        currentAppPickerDialog = dialog;

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isAppPickerSelecting) {
                    return;
                }
                String pkg = currentAppPickerAdapter.appPackages[position];
                String name = currentAppPickerAdapter.appNames[position];
                String cls = currentAppPickerAdapter.appClasses[position];
                setGestureApp(appPickerTarget, pkg, name, cls);
                saveGestureAppSetting(appPickerTarget);
                dialog.dismiss();
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                isAppPickerSelecting = false;
                currentAppPickerDialog = null;
                currentAppPickerListView = null;
                currentAppPickerAdapter = null;
            }
        });

        dialog.show();
    }

    private void reloadAppListInDialog() {
        if (currentAppPickerDialog == null || currentAppPickerAdapter == null) {
            return;
        }
        currentAppPickerDialog.setTitle("正在刷新...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LoadedApps loaded = loadApps();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (currentAppPickerAdapter == null || currentAppPickerDialog == null) {
                                return;
                            }
                            currentAppPickerAdapter.updateData(loaded.names, loaded.packages, loaded.classes, loaded.icons);
                            currentAppPickerDialog.setTitle(R.string.select_app);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("TriggerActionsController", "刷新应用列表失败", e);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (currentAppPickerDialog == null) {
                                return;
                            }
                            currentAppPickerDialog.setTitle(R.string.select_app);
                            Toast.makeText(activity, "刷新失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void saveGestureAppSetting(String gesture) {
        String pkg = getGestureAppPackage(gesture);
        String name = getGestureAppName(gesture);
        String cls = getGestureAppClass(gesture);

        SharedPreferences.Editor editor = preferences.edit();
        if ("click".equals(gesture)) {
            editor.putString(KEY_CLICK_APP_PACKAGE, pkg);
            editor.putString(KEY_CLICK_APP_NAME, name);
            editor.putString(KEY_CLICK_APP_CLASS, cls);
        } else if ("double_click".equals(gesture)) {
            editor.putString(KEY_DOUBLE_CLICK_APP_PACKAGE, pkg);
            editor.putString(KEY_DOUBLE_CLICK_APP_NAME, name);
            editor.putString(KEY_DOUBLE_CLICK_APP_CLASS, cls);
        } else if ("long_press".equals(gesture)) {
            editor.putString(KEY_LONG_PRESS_APP_PACKAGE, pkg);
            editor.putString(KEY_LONG_PRESS_APP_NAME, name);
            editor.putString(KEY_LONG_PRESS_APP_CLASS, cls);
        }
        editor.commit();

        saveAndApply();
    }

    private String getGestureAppPackage(String gesture) {
        if ("click".equals(gesture)) return clickAppPackage;
        else if ("double_click".equals(gesture)) return doubleClickAppPackage;
        else if ("long_press".equals(gesture)) return longPressAppPackage;
        return "";
    }

    private String getGestureAppName(String gesture) {
        if ("click".equals(gesture)) return clickAppName;
        else if ("double_click".equals(gesture)) return doubleClickAppName;
        else if ("long_press".equals(gesture)) return longPressAppName;
        return "";
    }

    private String getGestureAppClass(String gesture) {
        if ("click".equals(gesture)) return clickAppClass;
        else if ("double_click".equals(gesture)) return doubleClickAppClass;
        else if ("long_press".equals(gesture)) return longPressAppClass;
        return "";
    }

    private void setGestureApp(String gesture, String pkg, String name, String cls) {
        String oldPkg = getGestureAppPackage(gesture);
        String oldCls = getGestureAppClass(gesture);

        if ("click".equals(gesture)) {
            clickAppPackage = pkg;
            clickAppName = name;
            clickAppClass = cls;
            textClickSelectedApp.setText(name);
        } else if ("double_click".equals(gesture)) {
            doubleClickAppPackage = pkg;
            doubleClickAppName = name;
            doubleClickAppClass = cls;
            textDoubleClickSelectedApp.setText(name);
        } else if ("long_press".equals(gesture)) {
            longPressAppPackage = pkg;
            longPressAppName = name;
            longPressAppClass = cls;
            textLongPressSelectedApp.setText(name);
        }

        if (!TextUtils.equals(oldPkg, pkg) || !TextUtils.equals(oldCls, cls)) {
            ShortcutLaunchConfig.resetGesture(preferences.edit(), gesture).apply();
        }
        updateLaunchConfigSummary(gesture);
    }

    // ===== 启动配置 =====

    private void openLaunchConfig(String gesture) {
        GestureLaunchConfigDialog.show(activity, gesture,
                getGestureAppPackage(gesture),
                getGestureAppClass(gesture),
                getGestureAppName(gesture),
                preferences,
                new Runnable() {
                    @Override
                    public void run() {
                        updateLaunchConfigSummary(gesture);
                    }
                });
    }

    private void updateLaunchConfigSummaries() {
        updateLaunchConfigSummary("click");
        updateLaunchConfigSummary("double_click");
        updateLaunchConfigSummary("long_press");
    }

    private void updateLaunchConfigSummary(String gesture) {
        TextView target;
        if ("click".equals(gesture)) target = textClickLaunchSummary;
        else if ("double_click".equals(gesture)) target = textDoubleClickLaunchSummary;
        else target = textLongPressLaunchSummary;
        if (target == null) return;

        ShortcutLaunchConfig config = ShortcutLaunchConfig.loadGesture(preferences, gesture,
                getGestureAppPackage(gesture), getGestureAppClass(gesture),
                getGestureOpenInWindowPref(gesture));

        String summary = GestureLaunchConfigDialog.summarize(activity, config);
        if (!preferences.contains(ShortcutLaunchConfig.gestureKey(gesture))) {
            summary = activity.getString(R.string.launch_config_default) + " · " + summary;
        }
        target.setText(summary);
    }

    private boolean getGestureOpenInWindowPref(String gesture) {
        if ("click".equals(gesture)) {
            return preferences.getBoolean(KEY_CLICK_OPEN_IN_WINDOW, false);
        }
        if ("double_click".equals(gesture)) {
            return preferences.getBoolean(KEY_DOUBLE_CLICK_OPEN_IN_WINDOW, false);
        }
        if ("long_press".equals(gesture)) {
            return preferences.getBoolean(KEY_LONG_PRESS_OPEN_IN_WINDOW, false);
        }
        return false;
    }

    private int dpToPx(int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
