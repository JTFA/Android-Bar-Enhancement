package com.example.aiwindow;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
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
 * 提示条手势「启动配置」弹窗
 * 与快捷方式编辑页的「启动方式」一致：URL Scheme / Intent Extra / 显式组件（可指定 Activity）
 * 数据复用 {@link ShortcutLaunchConfig}，存储 key 为 gesture_launch_<gesture>
 */
public class GestureLaunchConfigDialog {

    private GestureLaunchConfigDialog() {}

    /**
     * 弹出启动配置对话框
     *
     * @param activity    宿主页面（用于主题取色/弹窗）
     * @param gesture     手势标识：click / double_click / long_press
     * @param appPackage  该手势已选应用的包名（只读，更改需回到设置页选择应用）
     * @param appClass    该手势已选应用的类名（默认值）
     * @param appName     应用显示名（仅用于标题）
     * @param prefs       ai_window_settings
     * @param onSaved     保存成功后的回调（用于刷新摘要）
     */
    public static void show(final Activity activity, final String gesture,
                            final String appPackage, final String appClass,
                            final String appName, final SharedPreferences prefs,
                            final Runnable onSaved) {
        if (activity == null || activity.isFinishing()) return;
        if (TextUtils.isEmpty(appPackage)) {
            Toast.makeText(activity, "请先选择要打开的应用", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean openInWindow = prefs.getBoolean(openInWindowKey(gesture), false);
        final ShortcutLaunchConfig config = ShortcutLaunchConfig.loadGesture(
                prefs, gesture, appPackage, appClass, openInWindow);

        final LinearLayout formLayout = new LinearLayout(activity);
        formLayout.setOrientation(LinearLayout.VERTICAL);
        formLayout.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 16));

        // ===== 目标应用（只读） =====
        formLayout.addView(label(activity, "目标应用"));
        TextView pkgView = new TextView(activity);
        pkgView.setText(appPackage);
        pkgView.setTextSize(14);
        pkgView.setTextColor(resolveThemeColor(activity, R.attr.textSecondary));
        pkgView.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        pkgView.setBackground(createInputBackground(activity));
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pkgLp.bottomMargin = dp(activity, 6);
        formLayout.addView(pkgView, pkgLp);

        TextView changeHint = new TextView(activity);
        changeHint.setText("更换应用请在设置页的「选择要打开的应用」中重新选择（会重置本配置）");
        changeHint.setTextSize(12);
        changeHint.setTextColor(resolveThemeColor(activity, R.attr.textHint));
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.bottomMargin = dp(activity, 16);
        formLayout.addView(changeHint, hintLp);

        // ===== 分隔线 =====
        View line = new View(activity);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        line.setBackgroundColor(resolveThemeColor(activity, R.attr.dividerColor));
        formLayout.addView(line);

        // ===== 启动方式 =====
        formLayout.addView(label(activity, "启动方式"));

        final String[] modes = {ShortcutLaunchConfig.MODE_URL,
                ShortcutLaunchConfig.MODE_INTENT, ShortcutLaunchConfig.MODE_EXPLICIT};
        final String[] modeLabels = {
                ShortcutLaunchConfig.getModeDisplayName(ShortcutLaunchConfig.MODE_URL),
                ShortcutLaunchConfig.getModeDisplayName(ShortcutLaunchConfig.MODE_INTENT),
                ShortcutLaunchConfig.getModeDisplayName(ShortcutLaunchConfig.MODE_EXPLICIT)};

        final Spinner spinnerMode = new Spinner(activity);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, modeLabels);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(config.launchMode)) {
                spinnerMode.setSelection(i);
                break;
            }
        }
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.bottomMargin = dp(activity, 12);
        formLayout.addView(spinnerMode, spinnerLp);

        // ===== URI 输入框（URL Scheme） =====
        final LinearLayout uriSection = new LinearLayout(activity);
        uriSection.setOrientation(LinearLayout.VERTICAL);
        uriSection.addView(label(activity, "URI"));
        final EditText editUri = new EditText(activity);
        editUri.setHint("URI（如 alipayqr://platformapi/startapp?saId=10000007）");
        editUri.setText(config.uri);
        editUri.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        editUri.setBackground(createInputBackground(activity));
        editUri.setTextSize(14);
        LinearLayout.LayoutParams uriLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uriLp.bottomMargin = dp(activity, 12);
        uriSection.addView(editUri, uriLp);
        formLayout.addView(uriSection);

        // ===== Activity 选择 + 类名输入 =====
        final LinearLayout classSection = new LinearLayout(activity);
        classSection.setOrientation(LinearLayout.VERTICAL);
        formLayout.addView(classSection);

        LinearLayout classLabelRow = new LinearLayout(activity);
        classLabelRow.setOrientation(LinearLayout.HORIZONTAL);
        classLabelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView classLabel = label(activity, "Activity 类名");
        classLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        classLabelRow.addView(classLabel);

        Button pickActivityBtn = new Button(activity);
        pickActivityBtn.setText("选择 Activity");
        pickActivityBtn.setTextSize(12);
        pickActivityBtn.setTextColor(0xFFFFFFFF);
        pickActivityBtn.setBackground(createRoundedButtonBg(
                resolveThemeColor(activity, R.attr.colorPrimaryBrand), dp(activity, 10)));
        pickActivityBtn.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6));
        pickActivityBtn.setStateListAnimator(null);
        classLabelRow.addView(pickActivityBtn);
        classSection.addView(classLabelRow);

        final EditText editClass = new EditText(activity);
        editClass.setHint("目标 Activity（留空 = 应用启动页）");
        editClass.setText(config.targetClass);
        editClass.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        editClass.setBackground(createInputBackground(activity));
        editClass.setTextSize(14);
        LinearLayout.LayoutParams clsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clsLp.bottomMargin = dp(activity, 12);
        classSection.addView(editClass, clsLp);

        pickActivityBtn.setOnClickListener(v ->
                showActivityPicker(activity, appPackage, editClass));

        // ===== Extras（Intent Extra） =====
        final LinearLayout extrasSection = new LinearLayout(activity);
        extrasSection.setOrientation(LinearLayout.VERTICAL);
        formLayout.addView(extrasSection);

        extrasSection.addView(label(activity, "Extras"));

        final LinearLayout extrasContainer = new LinearLayout(activity);
        extrasContainer.setOrientation(LinearLayout.VERTICAL);
        extrasSection.addView(extrasContainer);

        final List<EditText> extraKeyInputs = new ArrayList<>();
        final List<EditText> extraValueInputs = new ArrayList<>();
        final List<Spinner> extraTypeSpinners = new ArrayList<>();

        final Runnable[] createExtraRow = new Runnable[1];
        createExtraRow[0] = new Runnable() {
            @Override
            public void run() {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 0, 0, dp(activity, 6));

                EditText keyInput = new EditText(activity);
                keyInput.setHint("Key");
                keyInput.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                keyInput.setBackground(createInputBackground(activity));
                keyInput.setTextSize(13);
                LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                keyLp.rightMargin = dp(activity, 6);
                keyInput.setLayoutParams(keyLp);
                row.addView(keyInput);

                EditText valueInput = new EditText(activity);
                valueInput.setHint("Value");
                valueInput.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                valueInput.setBackground(createInputBackground(activity));
                valueInput.setTextSize(13);
                LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                valLp.rightMargin = dp(activity, 6);
                valueInput.setLayoutParams(valLp);
                row.addView(valueInput);

                Spinner typeSpinner = new Spinner(activity);
                String[] types = extraTypes();
                ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(activity,
                        android.R.layout.simple_spinner_item, types);
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                typeSpinner.setAdapter(typeAdapter);
                LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(
                        dp(activity, 88), ViewGroup.LayoutParams.WRAP_CONTENT);
                typeLp.rightMargin = dp(activity, 6);
                typeSpinner.setLayoutParams(typeLp);
                row.addView(typeSpinner);

                Button deleteBtn = new Button(activity);
                deleteBtn.setText("×");
                deleteBtn.setTextSize(16);
                deleteBtn.setTextColor(0xFFFFFFFF);
                deleteBtn.setBackground(createRoundedButtonBg(0xFFF44336, dp(activity, 8)));
                deleteBtn.setPadding(dp(activity, 10), dp(activity, 6), dp(activity, 10), dp(activity, 6));
                deleteBtn.setStateListAnimator(null);
                deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(
                        dp(activity, 36), ViewGroup.LayoutParams.WRAP_CONTENT));
                deleteBtn.setOnClickListener(v -> {
                    extrasContainer.removeView(row);
                    extraKeyInputs.remove(keyInput);
                    extraValueInputs.remove(valueInput);
                    extraTypeSpinners.remove(typeSpinner);
                });
                row.addView(deleteBtn);

                extrasContainer.addView(row);
                extraKeyInputs.add(keyInput);
                extraValueInputs.add(valueInput);
                extraTypeSpinners.add(typeSpinner);
            }
        };

        Button addExtraBtn = new Button(activity);
        addExtraBtn.setText("+ 添加 Extra");
        addExtraBtn.setTextSize(13);
        addExtraBtn.setTextColor(0xFFFFFFFF);
        addExtraBtn.setBackground(createRoundedButtonBg(
                resolveThemeColor(activity, R.attr.colorPrimaryBrand), dp(activity, 10)));
        addExtraBtn.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));
        addExtraBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams addBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addBtnLp.topMargin = dp(activity, 8);
        addBtnLp.bottomMargin = dp(activity, 8);
        addExtraBtn.setLayoutParams(addBtnLp);
        addExtraBtn.setOnClickListener(v -> createExtraRow[0].run());
        extrasSection.addView(addExtraBtn);

        // 加载已有 extras
        if (config.extras != null && !config.extras.isEmpty()) {
            for (ShortcutLaunchConfig.ExtraItem item : config.extras) {
                createExtraRow[0].run();
                int last = extraKeyInputs.size() - 1;
                extraKeyInputs.get(last).setText(item.key);
                extraValueInputs.get(last).setText(item.value);
                String[] types = extraTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equals(item.type)) {
                        extraTypeSpinners.get(last).setSelection(i);
                        break;
                    }
                }
            }
        }

        // ===== 模式切换可见性 =====
        final Runnable[] applyModeVisibility = new Runnable[1];
        applyModeVisibility[0] = new Runnable() {
            @Override
            public void run() {
                String mode = modes[spinnerMode.getSelectedItemPosition()];
                boolean isUrl = ShortcutLaunchConfig.MODE_URL.equals(mode);
                boolean isIntent = ShortcutLaunchConfig.MODE_INTENT.equals(mode);
                boolean isComponent = isIntent || ShortcutLaunchConfig.MODE_EXPLICIT.equals(mode);
                uriSection.setVisibility(isUrl ? View.VISIBLE : View.GONE);
                classSection.setVisibility(isComponent ? View.VISIBLE : View.GONE);
                extrasSection.setVisibility(isIntent ? View.VISIBLE : View.GONE);
            }
        };
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                applyModeVisibility[0].run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        // 立即应用初始可见性（setSelection 同值时不会触发回调）
        applyModeVisibility[0].run();

        // ===== 对话框外壳 =====
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(formLayout);

        LinearLayout dialogContainer = new LinearLayout(activity);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setBackground(createCardBackground(activity));
        dialogContainer.setClipToOutline(true);

        LinearLayout titleBar = new LinearLayout(activity);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 16), dp(activity, 14));

        TextView titleView = new TextView(activity);
        titleView.setText(TextUtils.isEmpty(appName) ? "启动配置" : "启动配置 · " + appName);
        titleView.setTextSize(17);
        titleView.setTextColor(resolveThemeColor(activity, R.attr.textPrimary));
        titleView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleView.setLayoutParams(titleLp);
        titleBar.addView(titleView);

        Button cancelBtn = new Button(activity);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(13);
        cancelBtn.setTextColor(resolveThemeColor(activity, R.attr.textSecondary));
        cancelBtn.setBackground(createRoundedButtonBg(
                resolveThemeColor(activity, R.attr.bgPage), dp(activity, 10)));
        cancelBtn.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
        cancelBtn.setStateListAnimator(null);
        titleBar.addView(cancelBtn);

        Button saveBtn = new Button(activity);
        saveBtn.setText("保存");
        saveBtn.setTextSize(13);
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setBackground(createRoundedButtonBg(
                resolveThemeColor(activity, R.attr.colorPrimaryBrand), dp(activity, 10)));
        saveBtn.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 8));
        saveBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = dp(activity, 8);
        saveBtn.setLayoutParams(saveLp);
        titleBar.addView(saveBtn);

        dialogContainer.addView(titleBar);

        View titleDivider = new View(activity);
        titleDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        titleDivider.setBackgroundColor(resolveThemeColor(activity, R.attr.dividerColor));
        dialogContainer.addView(titleDivider);

        dialogContainer.addView(scrollView);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogContainer);
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String mode = modes[spinnerMode.getSelectedItemPosition()];
            String uri = editUri.getText().toString().trim();
            String cls = editClass.getText().toString().trim();

            if (ShortcutLaunchConfig.MODE_URL.equals(mode) && TextUtils.isEmpty(uri)) {
                Toast.makeText(activity, "请输入 URI", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ShortcutLaunchConfig.MODE_INTENT.equals(mode) && TextUtils.isEmpty(cls)) {
                Toast.makeText(activity, "Intent Extra 模式需要指定 Activity 类名", Toast.LENGTH_SHORT).show();
                return;
            }

            config.launchMode = mode;
            config.uri = uri;
            config.targetPackage = appPackage;
            config.targetClass = cls;
            // 窗口打开开关的唯一数据源仍是设置页的开关（key 与 SettingsActivity 一致）
            config.openInWindow = prefs.getBoolean(openInWindowKey(gesture), false);

            config.extras.clear();
            for (int i = 0; i < extraKeyInputs.size(); i++) {
                String key = extraKeyInputs.get(i).getText().toString().trim();
                String value = extraValueInputs.get(i).getText().toString().trim();
                if (!TextUtils.isEmpty(key)) {
                    String type = extraTypes()[extraTypeSpinners.get(i).getSelectedItemPosition()];
                    config.extras.add(new ShortcutLaunchConfig.ExtraItem(key, value, type));
                }
            }

            ShortcutLaunchConfig.saveGesture(prefs.edit(), gesture, config).apply();
            Toast.makeText(activity, "启动配置已保存", Toast.LENGTH_SHORT).show();
            if (onSaved != null) onSaved.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 选择目标应用的 Activity（列出该包下已导出的 Activity）
     * 第一项为「应用启动页（默认）」，选择后清空类名
     */
    private static void showActivityPicker(final Activity activity, final String pkg,
                                           final EditText classInput) {
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(activity, "请先选择应用", Toast.LENGTH_SHORT).show();
            return;
        }

        final AlertDialog loading = new AlertDialog.Builder(activity)
                .setTitle("选择 Activity")
                .setMessage("正在加载...")
                .setCancelable(false)
                .create();
        loading.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.content.pm.PackageManager pm = activity.getPackageManager();
                    PackageInfo pi = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES);
                    final List<ActivityInfo> list = new ArrayList<>();
                    if (pi.activities != null) {
                        for (ActivityInfo ai : pi.activities) {
                            if (ai.exported) list.add(ai);
                        }
                    }
                    final Collator collator = Collator.getInstance(Locale.CHINESE);
                    Collections.sort(list, new Comparator<ActivityInfo>() {
                        @Override
                        public int compare(ActivityInfo a, ActivityInfo b) {
                            return collator.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString());
                        }
                    });

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try { loading.dismiss(); } catch (Exception ignored) {}
                            if (list.isEmpty()) {
                                Toast.makeText(activity, "未找到可启动的 Activity，请手动输入类名",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            showActivityListDialog(activity, pkg, list, classInput);
                        }
                    });
                } catch (final Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try { loading.dismiss(); } catch (Exception ignored) {}
                            Toast.makeText(activity, "加载 Activity 列表失败，请手动输入类名",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static void showActivityListDialog(final Activity activity, final String pkg,
                                               final List<ActivityInfo> list,
                                               final EditText classInput) {
        final android.content.pm.PackageManager pm = activity.getPackageManager();

        // 第 0 项：应用启动页（默认），类名为空
        final List<String> classNames = new ArrayList<>();
        classNames.add("");
        for (ActivityInfo ai : list) classNames.add(ai.name);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));

        ListView listView = new ListView(activity);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        listView.setDivider(null);
        layout.addView(listView);

        listView.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return classNames.size(); }
            @Override public Object getItem(int position) { return classNames.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));

                ImageView iconView = new ImageView(activity);
                iconView.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(iconView);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                textLp.leftMargin = dp(activity, 12);
                TextView nameView = new TextView(activity);
                nameView.setTextSize(15);
                nameView.setTextColor(resolveThemeColor(activity, R.attr.textPrimary));
                TextView clsView = new TextView(activity);
                clsView.setTextSize(11);
                clsView.setTextColor(resolveThemeColor(activity, R.attr.textHint));
                textCol.addView(nameView);
                textCol.addView(clsView);
                row.addView(textCol, textLp);

                String clsName = classNames.get(position);
                if (TextUtils.isEmpty(clsName)) {
                    iconView.setImageDrawable(pm.getDefaultActivityIcon());
                    nameView.setText("应用启动页（默认）");
                    clsView.setText("留空 = 由系统选择启动 Activity");
                } else {
                    ActivityInfo matched = null;
                    for (ActivityInfo info : list) {
                        if (info.name.equals(clsName)) { matched = info; break; }
                    }
                    if (matched != null) {
                        iconView.setImageDrawable(matched.loadIcon(pm));
                        nameView.setText(matched.loadLabel(pm));
                        clsView.setText(matched.name);
                    } else {
                        iconView.setImageDrawable(pm.getDefaultActivityIcon());
                        nameView.setText(clsName);
                        clsView.setText(clsName);
                    }
                }
                return row;
            }
        });

        final AlertDialog listDialog = new AlertDialog.Builder(activity)
                .setTitle("选择 Activity · " + pkg)
                .setView(layout)
                .setNegativeButton("取消", null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String clsName = classNames.get(position);
            classInput.setText(clsName);
            classInput.setSelection(classInput.getText().length());
            listDialog.dismiss();
        });

        listDialog.show();
    }

    /**
     * 生成手势启动配置的摘要文案（用于设置页「启动配置」行右侧）
     */
    public static String summarize(android.content.Context context, ShortcutLaunchConfig config) {
        if (config == null) return "";
        switch (config.launchMode) {
            case ShortcutLaunchConfig.MODE_URL:
                if (TextUtils.isEmpty(config.uri)) return "未设置 URL";
                return "URL: " + config.uri;
            case ShortcutLaunchConfig.MODE_INTENT: {
                String cls = simpleName(config.targetClass);
                String head = TextUtils.isEmpty(cls) ? "未设置 Activity" : cls;
                if (config.extras == null || config.extras.isEmpty()) return head + " · 无 Extra";
                if (config.extras.size() == 1) {
                    ShortcutLaunchConfig.ExtraItem item = config.extras.get(0);
                    return head + " · " + item.key + "=" + item.value;
                }
                return head + " · " + config.extras.size() + " 个 Extra";
            }
            case ShortcutLaunchConfig.MODE_EXPLICIT:
            default:
                String cls = simpleName(config.targetClass);
                return TextUtils.isEmpty(cls) ? "应用启动页" : cls;
        }
    }

    private static String simpleName(String cls) {
        if (TextUtils.isEmpty(cls)) return "";
        return cls.substring(cls.lastIndexOf('.') + 1);
    }

    private static String[] extraTypes() {
        return new String[]{"string", "boolean", "int", "long", "float"};
    }

    /** 与 SettingsActivity 的 KEY_*_OPEN_IN_WINDOW 保持一致 */
    private static String openInWindowKey(String gesture) {
        if ("click".equals(gesture)) return "click_open_in_window";
        if ("double_click".equals(gesture)) return "double_click_open_in_window";
        if ("long_press".equals(gesture)) return "long_press_open_in_window";
        return "click_open_in_window";
    }

    private static TextView label(Activity activity, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(resolveThemeColor(activity, R.attr.textPrimary));
        view.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        return view;
    }

    private static int resolveThemeColor(Activity activity, int attrResId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private static int dp(Activity activity, int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static GradientDrawable createCardBackground(Activity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(resolveThemeColor(activity, R.attr.bgCard));
        drawable.setCornerRadius(dp(activity, 16));
        return drawable;
    }

    private static GradientDrawable createRoundedButtonBg(int color, int cornerRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(cornerRadius);
        return drawable;
    }

    private static GradientDrawable createInputBackground(Activity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(resolveThemeColor(activity, R.attr.inputBg));
        drawable.setStroke(dp(activity, 1), resolveThemeColor(activity, R.attr.inputStroke));
        drawable.setCornerRadius(dp(activity, 12));
        return drawable;
    }
}
