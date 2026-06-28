package com.example.aiwindow;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 快捷方式编辑 Activity
 * 用户可添加、编辑、删除快捷方式（名称、图标、包名、类名）
 */
public class ShortcutEditActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ai_window_settings";
    private static final String KEY_SHORTCUT_LIST = "shortcut_list";

    private LinearLayout listContainer;
    private ArrayList<ShortcutItem> shortcutList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建根布局
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.setOrientation(LinearLayout.VERTICAL);

        // 标题
        TextView titleView = new TextView(this);
        titleView.setText("管理快捷方式");
        titleView.setTextSize(22);
        titleView.setTextColor(0xFF333333);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dpToPx(8));
        rootLayout.addView(titleView);

        // 说明
        TextView descView = new TextView(this);
        descView.setText("添加、编辑或删除快捷方式。双击提示条弹出快捷方式窗口。");
        descView.setTextSize(13);
        descView.setTextColor(0xFF999999);
        descView.setPadding(0, 0, 0, dpToPx(20));
        rootLayout.addView(descView);

        // 列表容器
        listContainer = new LinearLayout(this);
        listContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        listContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(listContainer);

        // 添加按钮
        Button addButton = new Button(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(48));
        btnParams.setMargins(0, dpToPx(16), 0, 0);
        addButton.setLayoutParams(btnParams);
        addButton.setText("+ 添加快捷方式");
        addButton.setTextSize(16);
        addButton.setTextColor(0xFFFFFFFF);
        addButton.setBackgroundColor(0xFF4CAF50);
        addButton.setPadding(0, 0, 0, 0);
        addButton.setOnClickListener(v -> showEditDialog(null, -1));
        rootLayout.addView(addButton);

        scrollView.addView(rootLayout);
        setContentView(scrollView);

        // 加载数据
        loadShortcuts();
        refreshList();
    }

    /**
     * 加载快捷方式列表
     */
    private void loadShortcuts() {
        shortcutList = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_SHORTCUT_LIST, "");
        if (TextUtils.isEmpty(jsonStr)) {
            shortcutList = ShortcutItem.getDefaultShortcuts();
            saveShortcuts();
            return;
        }
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                shortcutList.add(ShortcutItem.fromJson(jsonArray.getJSONObject(i)));
            }
        } catch (Exception e) {
            shortcutList = ShortcutItem.getDefaultShortcuts();
        }
    }

    /**
     * 保存快捷方式列表
     */
    private void saveShortcuts() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();
        for (ShortcutItem item : shortcutList) {
            jsonArray.put(item.toJson());
        }
        prefs.edit().putString(KEY_SHORTCUT_LIST, jsonArray.toString()).apply();
    }

    /**
     * 刷新列表显示
     */
    private void refreshList() {
        listContainer.removeAllViews();

        if (shortcutList.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("暂无快捷方式，请点击下方按钮添加");
            emptyView.setTextSize(15);
            emptyView.setTextColor(0xFF999999);
            emptyView.setPadding(0, dpToPx(30), 0, dpToPx(30));
            emptyView.setGravity(android.view.Gravity.CENTER);
            listContainer.addView(emptyView);
            return;
        }

        for (int i = 0; i < shortcutList.size(); i++) {
            final int index = i;
            ShortcutItem item = shortcutList.get(i);
            listContainer.addView(createShortcutCard(item, index));
        }
    }

    /**
     * 创建快捷方式卡片视图
     */
    private View createShortcutCard(ShortcutItem item, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        card.setBackgroundColor(0xFFF5F5F5);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams marginParams = (LinearLayout.LayoutParams) card.getLayoutParams();
        marginParams.setMargins(0, 0, 0, dpToPx(8));
        card.setLayoutParams(marginParams);

        // 图标
        TextView iconView = new TextView(this);
        iconView.setTextSize(28);
        iconView.setText(item.getIcon() != null ? item.getIcon() : "⚡");
        iconView.setPadding(0, 0, dpToPx(12), 0);
        card.addView(iconView);

        // 名称和详情
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        infoLayout.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(item.getName());
        nameView.setTextSize(15);
        nameView.setTextColor(0xFF333333);
        infoLayout.addView(nameView);

        TextView detailView = new TextView(this);
        detailView.setText(item.getPackageName() + "\n" + item.getClassName());
        detailView.setTextSize(11);
        detailView.setTextColor(0xFF999999);
        detailView.setPadding(0, dpToPx(2), 0, 0);
        infoLayout.addView(detailView);

        card.addView(infoLayout);

        // 编辑按钮
        Button editBtn = new Button(this);
        editBtn.setText("编辑");
        editBtn.setTextSize(12);
        editBtn.setTextColor(0xFF2196F3);
        editBtn.setBackgroundColor(0x00000000);
        editBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        editBtn.setOnClickListener(v -> showEditDialog(shortcutList.get(index), index));
        card.addView(editBtn);

        // 删除按钮
        Button delBtn = new Button(this);
        delBtn.setText("删除");
        delBtn.setTextSize(12);
        delBtn.setTextColor(0xFFF44336);
        delBtn.setBackgroundColor(0x00000000);
        delBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        delBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定删除快捷方式 \"" + shortcutList.get(index).getName() + "\"？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        shortcutList.remove(index);
                        saveShortcuts();
                        refreshList();
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        card.addView(delBtn);

        return card;
    }

    /**
     * 显示编辑对话框
     */
    private void showEditDialog(ShortcutItem existing, int editIndex) {
        LinearLayout formLayout = new LinearLayout(this);
        formLayout.setOrientation(LinearLayout.VERTICAL);
        formLayout.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

        // 名称
        TextView nameLabel = new TextView(this);
        nameLabel.setText("名称");
        nameLabel.setTextSize(13);
        nameLabel.setTextColor(0xFF666666);
        nameLabel.setPadding(0, 0, 0, dpToPx(4));
        formLayout.addView(nameLabel);

        EditText nameInput = new EditText(this);
        nameInput.setHint("输入快捷方式名称");
        nameInput.setText(existing != null ? existing.getName() : "");
        nameInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        nameInput.setBackgroundResource(android.R.drawable.edit_text);
        formLayout.addView(nameInput);

        // 间距
        TextView spacer1 = new TextView(this);
        spacer1.setText("");
        spacer1.setPadding(0, dpToPx(8), 0, 0);
        formLayout.addView(spacer1);

        // 图标
        TextView iconLabel = new TextView(this);
        iconLabel.setText("图标 (Emoji)");
        iconLabel.setTextSize(13);
        iconLabel.setTextColor(0xFF666666);
        iconLabel.setPadding(0, 0, 0, dpToPx(4));
        formLayout.addView(iconLabel);

        EditText iconInput = new EditText(this);
        iconInput.setHint("输入 Emoji 图标（如 🔍）");
        iconInput.setText(existing != null ? existing.getIcon() : "⚡");
        iconInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        iconInput.setBackgroundResource(android.R.drawable.edit_text);
        formLayout.addView(iconInput);

        // 间距
        TextView spacer2 = new TextView(this);
        spacer2.setText("");
        spacer2.setPadding(0, dpToPx(8), 0, 0);
        formLayout.addView(spacer2);

        // 包名
        TextView pkgLabel = new TextView(this);
        pkgLabel.setText("应用包名");
        pkgLabel.setTextSize(13);
        pkgLabel.setTextColor(0xFF666666);
        pkgLabel.setPadding(0, 0, 0, dpToPx(4));
        formLayout.addView(pkgLabel);

        EditText pkgInput = new EditText(this);
        pkgInput.setHint("输入应用包名");
        pkgInput.setText(existing != null ? existing.getPackageName() : "");
        pkgInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        pkgInput.setBackgroundResource(android.R.drawable.edit_text);
        formLayout.addView(pkgInput);

        // 间距
        TextView spacer3 = new TextView(this);
        spacer3.setText("");
        spacer3.setPadding(0, dpToPx(8), 0, 0);
        formLayout.addView(spacer3);

        // 类名
        TextView clsLabel = new TextView(this);
        clsLabel.setText("Activity 类名");
        clsLabel.setTextSize(13);
        clsLabel.setTextColor(0xFF666666);
        clsLabel.setPadding(0, 0, 0, dpToPx(4));
        formLayout.addView(clsLabel);

        EditText clsInput = new EditText(this);
        clsInput.setHint("输入 Activity 完整类名");
        clsInput.setText(existing != null ? existing.getClassName() : "");
        clsInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        clsInput.setBackgroundResource(android.R.drawable.edit_text);
        formLayout.addView(clsInput);

        // ========== 分隔 + 启动方式配置 ==========
        final String shortcutId = existing != null ? existing.getId() : "sc_" + System.currentTimeMillis();
        final ShortcutLaunchConfig launchConfig = ShortcutLaunchConfig.load(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE), shortcutId);

        TextView divider = new TextView(this);
        divider.setText("");
        divider.setPadding(0, dpToPx(16), 0, 0);
        formLayout.addView(divider);

        View line = new View(this);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        line.setBackgroundColor(0xFFDDDDDD);
        line.setPadding(0, 0, 0, 0);
        formLayout.addView(line);

        TextView configTitle = new TextView(this);
        configTitle.setText("启动方式");
        configTitle.setTextSize(15);
        configTitle.setTextColor(0xFF333333);
        configTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        configTitle.setPadding(0, dpToPx(12), 0, dpToPx(8));
        formLayout.addView(configTitle);

        // 启动模式选择
        final String[] modes = {ShortcutLaunchConfig.MODE_URL, ShortcutLaunchConfig.MODE_INTENT, ShortcutLaunchConfig.MODE_EXPLICIT};
        final String[] modeLabels = {ShortcutLaunchConfig.getModeDisplayName(ShortcutLaunchConfig.MODE_URL),
                                       ShortcutLaunchConfig.getModeDisplayName(ShortcutLaunchConfig.MODE_INTENT),
                                       ShortcutLaunchConfig.getModeDisplayName(ShortcutLaunchConfig.MODE_EXPLICIT)};

        final Spinner spinnerMode = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modeLabels);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(launchConfig.launchMode)) {
                spinnerMode.setSelection(i);
                break;
            }
        }
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.setMargins(0, 0, 0, dpToPx(8));
        formLayout.addView(spinnerMode, spinnerLp);

        // URI 输入框
        final EditText editUri = new EditText(this);
        editUri.setHint("URI（如 alipayqr://...）");
        editUri.setText(launchConfig.uri);
        editUri.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        editUri.setBackgroundResource(android.R.drawable.edit_text);
        editUri.setTextSize(14);
        LinearLayout.LayoutParams uriLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        uriLp.setMargins(0, 0, 0, dpToPx(8));
        formLayout.addView(editUri, uriLp);

        // Extra Key 输入框
        final EditText editExtraKey = new EditText(this);
        editExtraKey.setHint("Extra Key（如 LauncherUI.From.Scaner.Shortcut）");
        editExtraKey.setText(launchConfig.extraKey);
        editExtraKey.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        editExtraKey.setBackgroundResource(android.R.drawable.edit_text);
        editExtraKey.setTextSize(14);
        LinearLayout.LayoutParams ekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ekLp.setMargins(0, 0, 0, dpToPx(4));
        formLayout.addView(editExtraKey, ekLp);

        // Extra Value + 类型
        LinearLayout extraRow = new LinearLayout(this);
        extraRow.setOrientation(LinearLayout.HORIZONTAL);
        extraRow.setPadding(0, 0, 0, dpToPx(8));

        final EditText editExtraValue = new EditText(this);
        editExtraValue.setHint("Extra Value");
        editExtraValue.setText(launchConfig.extraValue);
        editExtraValue.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        editExtraValue.setBackgroundResource(android.R.drawable.edit_text);
        editExtraValue.setTextSize(14);
        LinearLayout.LayoutParams evLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        evLp.setMargins(0, 0, dpToPx(8), 0);
        extraRow.addView(editExtraValue, evLp);

        // 值类型：boolean / string
        final Spinner spinnerType = new Spinner(this);
        String[] typeLabels = {"boolean", "string"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, typeLabels);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);
        spinnerType.setSelection("boolean".equals(launchConfig.extraType) ? 0 : 1);
        LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(
                dpToPx(100), ViewGroup.LayoutParams.WRAP_CONTENT);
        extraRow.addView(spinnerType, typeLp);

        formLayout.addView(extraRow);

        // 模式切换时更新可见性
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String mode = modes[pos];
                boolean isUrl = ShortcutLaunchConfig.MODE_URL.equals(mode);
                boolean isIntent = ShortcutLaunchConfig.MODE_INTENT.equals(mode);
                editUri.setVisibility(isUrl ? View.VISIBLE : View.GONE);
                editExtraKey.setVisibility(isIntent ? View.VISIBLE : View.GONE);
                extraRow.setVisibility(isIntent ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        // 触发初始状态
        spinnerMode.setSelection(spinnerMode.getSelectedItemPosition());

        // 创建对话框
        boolean isNew = existing == null;
        new AlertDialog.Builder(this)
                .setTitle(isNew ? "添加快捷方式" : "编辑快捷方式")
                .setView(formLayout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String icon = iconInput.getText().toString().trim();
                    String pkg = pkgInput.getText().toString().trim();
                    String cls = clsInput.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(cls)) {
                        Toast.makeText(this, "请输入包名和类名", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(icon)) {
                        icon = "⚡";
                    }

                    ShortcutItem targetItem;
                    if (isNew) {
                        // 添加
                        targetItem = new ShortcutItem(shortcutId, name, icon, pkg, cls);
                        shortcutList.add(targetItem);
                        Toast.makeText(this, "快捷方式已添加", Toast.LENGTH_SHORT).show();
                    } else {
                        // 更新
                        targetItem = existing;
                        existing.setName(name);
                        existing.setIcon(icon);
                        existing.setPackageName(pkg);
                        existing.setClassName(cls);
                        Toast.makeText(this, "快捷方式已更新", Toast.LENGTH_SHORT).show();
                    }

                    saveShortcuts();
                    // 保存启动配置
                    int modePos = spinnerMode.getSelectedItemPosition();
                    launchConfig.launchMode = modes[modePos];
                    launchConfig.uri = editUri.getText().toString().trim();
                    launchConfig.targetPackage = pkg;
                    launchConfig.targetClass = cls;
                    launchConfig.extraKey = editExtraKey.getText().toString().trim();
                    launchConfig.extraValue = editExtraValue.getText().toString().trim();
                    launchConfig.extraType = spinnerType.getSelectedItemPosition() == 0 ? "boolean" : "string";
                    ShortcutLaunchConfig.save(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit(), shortcutId, launchConfig).apply();

                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
