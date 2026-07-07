package com.example.aiwindow;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.Intent;
import android.view.DragEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 快捷方式编辑 Activity
 * 用户可添加、编辑、删除快捷方式（名称、图标、包名、类名）
 * 图标支持 emoji 和自定义图片两种类型
 */
public class ShortcutEditActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ai_window_settings";
    private static final String KEY_SHORTCUT_LIST = "shortcut_list";
    private static final String ICONS_DIR = "shortcut_icons";

    private LinearLayout listContainer;
    private ArrayList<ShortcutItem> shortcutList;

    // 当前正在编辑的快捷方式 ID（用于图片选择回调）
    private String editingShortcutId = null;
    // 对话框内的控件引用（更新用）
    private ImageView dialogImagePreview;
    private EditText dialogIconPathInput;
    private EditText dialogIconInput; // emoji 输入框
    private TextView dialogTypeIndicator; // 图标类型指示
    private boolean dialogIsImageMode = false;

    // 长按拖动排序相关
    private int draggingFromIndex = -1;

    // 图片选择启动器
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

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
        descView.setText("添加、编辑或删除快捷方式。双击提示条弹出快捷方式窗口。\n图标支持 emoji 和自定义图片。");
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

        // 设置长按拖动排序监听（拖放重排）
        listContainer.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENDED: {
                    for (int i = 0; i < listContainer.getChildCount(); i++) {
                        listContainer.getChildAt(i).setAlpha(1f);
                    }
                    draggingFromIndex = -1;
                    return true;
                }
                case DragEvent.ACTION_DROP: {
                    int fromIdx = draggingFromIndex;
                    if (fromIdx < 0 || fromIdx >= shortcutList.size()) return false;
                    int toIdx = 0;
                    float dropY = event.getY();
                    for (int i = 0; i < listContainer.getChildCount(); i++) {
                        View child = listContainer.getChildAt(i);
                        if (dropY < child.getTop() + child.getHeight() / 2f) {
                            toIdx = i;
                            break;
                        }
                        toIdx = i + 1;
                    }
                    if (toIdx < 0) toIdx = 0;
                    if (toIdx > shortcutList.size()) toIdx = shortcutList.size();
                    if (fromIdx == toIdx) return true;
                    int adjustedTo = (toIdx > fromIdx) ? toIdx - 1 : toIdx;
                    ShortcutItem movedItem = shortcutList.remove(fromIdx);
                    shortcutList.add(adjustedTo, movedItem);
                    saveShortcuts();
                    refreshList();
                    return true;
                }
            }
            return false;
        });

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
            copyDefaultIconsToStorage(shortcutList);
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
            copyDefaultIconsToStorage(shortcutList);
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
     * 首次运行时，将 res/drawable/ 中的默认图标复制到内部存储
     */
    private void copyDefaultIconsToStorage(java.util.ArrayList<ShortcutItem> list) {
        File iconsDir = new File(getFilesDir(), ICONS_DIR);
        if (!iconsDir.exists()) {
            iconsDir.mkdirs();
        }
        for (ShortcutItem item : list) {
            if (!"image".equals(item.getIconType())) continue;
            if (!TextUtils.isEmpty(item.getIconPath())) continue;

            int resId = getResources().getIdentifier(item.getId(), "drawable", getPackageName());
            if (resId == 0) continue;

            try {
                File destFile = new File(iconsDir, item.getId() + ".png");
                if (destFile.exists()) {
                    item.setIconPath(destFile.getAbsolutePath());
                    continue;
                }
                InputStream in = getResources().openRawResource(resId);
                FileOutputStream out = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.close();
                in.close();
                item.setIconPath(destFile.getAbsolutePath());
            } catch (Exception e) {
                android.util.Log.e("ShortcutEdit", "复制默认图标失败: " + item.getId(), e);
            }
        }
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
     * 创建快捷方式卡片视图（含启用开关、长按拖动排序）
     */
    private View createShortcutCard(final ShortcutItem item, final int index) {
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        card.setBackgroundColor(0xFFF5F5F5);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams marginParams = (LinearLayout.LayoutParams) card.getLayoutParams();
        marginParams.setMargins(0, 0, 0, dpToPx(6));
        card.setLayoutParams(marginParams);

        // 启用开关
        Switch enableSwitch = new Switch(this);
        enableSwitch.setChecked(item.isEnabled());
        enableSwitch.setPadding(0, 0, dpToPx(8), 0);
        enableSwitch.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setEnabled(isChecked);
            saveShortcuts();
        });
        card.addView(enableSwitch);

        // 图标（预览）
        if ("image".equals(item.getIconType())) {
            ImageView iconView = new ImageView(this);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
            iconLp.setMargins(0, 0, dpToPx(10), 0);
            iconView.setLayoutParams(iconLp);
            iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable roundBg = new GradientDrawable();
            roundBg.setShape(GradientDrawable.RECTANGLE);
            roundBg.setCornerRadius(dpToPx(8));
            Bitmap bmp = loadIconBitmap(item.getIconPath());
            if (bmp != null) {
                iconView.setImageBitmap(bmp);
                iconView.setBackground(roundBg);
                iconView.setClipToOutline(true);
            } else {
                roundBg.setColor(getPlaceholderColor(item.getId()));
                iconView.setBackground(roundBg);
            }
            card.addView(iconView);
        } else {
            TextView iconView = new TextView(this);
            iconView.setText(item.getIcon() != null ? item.getIcon() : "⚡");
            iconView.setTextSize(24);
            iconView.setPadding(0, 0, dpToPx(10), 0);
            card.addView(iconView);
        }

        // 名称和详情
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        infoLayout.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(item.getName());
        nameView.setTextSize(14);
        nameView.setTextColor(0xFF333333);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoLayout.addView(nameView);

        String typeHint = "image".equals(item.getIconType()) ? " [图片]" : " [Emoji]";
        TextView detailView = new TextView(this);
        detailView.setText(item.getPackageName() + "\n" + item.getClassName() + typeHint);
        detailView.setTextSize(11);
        detailView.setTextColor(0xFF999999);
        detailView.setMaxLines(1);
        detailView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailView.setPadding(0, dpToPx(2), 0, 0);
        infoLayout.addView(detailView);

        card.addView(infoLayout);

        // 编辑按钮
        TextView editBtn = new TextView(this);
        editBtn.setText("编辑");
        editBtn.setTextSize(12);
        editBtn.setTextColor(0xFF2196F3);
        editBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        editBtn.setClickable(true);
        editBtn.setOnClickListener(v -> showEditDialog(shortcutList.get(index), index));
        card.addView(editBtn);

        // 判断是否为4个默认快捷方式
        final String[] defaultIds = {"alipay_scan", "alipay_pay", "wechat_scan", "wechat_pay"};
        boolean isDefaultShortcut = false;
        for (String did : defaultIds) {
            if (did.equals(item.getId())) { isDefaultShortcut = true; break; }
        }
        if (isDefaultShortcut) {
            // 默认项：显示"默认"按钮（恢复全部默认值），不显示"删除"按钮
            TextView resetBtn = new TextView(this);
            resetBtn.setText("默认");
            resetBtn.setTextSize(12);
            resetBtn.setTextColor(0xFF4CAF50);
            resetBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            resetBtn.setClickable(true);
            resetBtn.setOnClickListener(v -> resetShortcutToDefault(index));
            card.addView(resetBtn);
        } else {
            // 用户自建项：只显示"删除"按钮
            TextView delBtn = new TextView(this);
            delBtn.setText("删除");
            delBtn.setTextSize(12);
            delBtn.setTextColor(0xFFF44336);
            delBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            delBtn.setClickable(true);
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
        }

        // 长按拖动排序
        final int currentIndex = index;
        card.setOnLongClickListener(v -> {
            draggingFromIndex = currentIndex;
            ClipData data = ClipData.newPlainText("shortcut_index", String.valueOf(currentIndex));
            v.startDrag(data, new View.DragShadowBuilder(v), v, 0);
            v.setAlpha(0.5f);
            return true;
        });

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

        // ===== 图标区域 =====
        TextView iconLabel = new TextView(this);
        iconLabel.setText("图标");
        iconLabel.setTextSize(13);
        iconLabel.setTextColor(0xFF666666);
        iconLabel.setPadding(0, 0, 0, dpToPx(4));
        formLayout.addView(iconLabel);

        final boolean existingIsImage = existing != null && "image".equals(existing.getIconType());

        // 显示当前图标类型的提示
        dialogTypeIndicator = new TextView(this);
        dialogTypeIndicator.setText(existingIsImage ? "当前：图片图标" : "当前：Emoji 图标");
        dialogTypeIndicator.setTextSize(12);
        dialogTypeIndicator.setTextColor(0xFF999999);
        dialogTypeIndicator.setPadding(0, 0, 0, dpToPx(6));
        formLayout.addView(dialogTypeIndicator);

        // Emoji 输入
        dialogIconInput = new EditText(this);
        dialogIconInput.setHint("输入 Emoji 图标（如 🔍），留空则使用图片");
        dialogIconInput.setText(!existingIsImage && existing != null ? existing.getIcon() : "");
        dialogIconInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        dialogIconInput.setBackgroundResource(android.R.drawable.edit_text);
        formLayout.addView(dialogIconInput);

        // 选择图片按钮
        LinearLayout imageRow = new LinearLayout(this);
        imageRow.setOrientation(LinearLayout.HORIZONTAL);
        imageRow.setPadding(0, dpToPx(6), 0, 0);

        Button selectImageBtn = new Button(this);
        selectImageBtn.setText("选择图片");
        selectImageBtn.setTextSize(14);
        selectImageBtn.setTextColor(0xFFFFFFFF);
        selectImageBtn.setBackgroundColor(0xFF2196F3);
        selectImageBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

        // 图片预览
        dialogImagePreview = new ImageView(this);
        int previewSize = dpToPx(48);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(previewSize, previewSize);
        previewLp.setMargins(dpToPx(12), 0, 0, 0);
        dialogImagePreview.setLayoutParams(previewLp);
        dialogImagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(dpToPx(8));
        previewBg.setColor(0xFFEEEEEE);
        dialogImagePreview.setBackground(previewBg);
        dialogImagePreview.setVisibility(View.GONE);

        // 图片路径（隐藏的输入框，用于存储路径）
        dialogIconPathInput = new EditText(this);
        dialogIconPathInput.setVisibility(View.GONE);
        String existingPath = (existingIsImage && existing != null && existing.getIconPath() != null)
                ? existing.getIconPath() : "";
        dialogIconPathInput.setText(existingPath);

        // 如果有已有图片路径，加载预览
        if (!TextUtils.isEmpty(existingPath)) {
            Bitmap bmp = loadIconBitmap(existingPath);
            if (bmp != null) {
                dialogImagePreview.setImageBitmap(bmp);
                dialogImagePreview.setVisibility(View.VISIBLE);
            }
        }

        final String shortcutId = existing != null ? existing.getId() : "sc_" + System.currentTimeMillis();
        selectImageBtn.setOnClickListener(v -> {
            editingShortcutId = shortcutId;
            imagePickerLauncher.launch("image/*");
        });

        imageRow.addView(selectImageBtn);
        imageRow.addView(dialogImagePreview);
        formLayout.addView(imageRow);

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

        // 类名（提前声明，以便在包名选择按钮中使用）
        TextView clsLabel = new TextView(this);
        clsLabel.setText("Activity 类名");
        clsLabel.setTextSize(13);
        clsLabel.setTextColor(0xFF666666);
        clsLabel.setPadding(0, 0, 0, dpToPx(4));

        EditText clsInput = new EditText(this);
        clsInput.setHint("输入 Activity 完整类名");
        clsInput.setText(existing != null ? existing.getClassName() : "");
        clsInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        clsInput.setBackgroundResource(android.R.drawable.edit_text);

        // 包名输入行（包含输入框和选择按钮）
        LinearLayout pkgRow = new LinearLayout(this);
        pkgRow.setOrientation(LinearLayout.HORIZONTAL);
        pkgRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        EditText pkgInput = new EditText(this);
        pkgInput.setId(View.generateViewId());
        pkgInput.setHint("输入应用包名");
        pkgInput.setText(existing != null ? existing.getPackageName() : "");
        pkgInput.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        pkgInput.setBackgroundResource(android.R.drawable.edit_text);
        pkgInput.setTextSize(14);
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pkgInput.setLayoutParams(pkgLp);
        pkgRow.addView(pkgInput);

        Button selectAppBtn = new Button(this);
        selectAppBtn.setText("选择应用");
        selectAppBtn.setTextSize(12);
        selectAppBtn.setTextColor(0xFFFFFFFF);
        selectAppBtn.setBackgroundColor(0xFF2196F3);
        selectAppBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(dpToPx(8), 0, 0, 0);
        selectAppBtn.setLayoutParams(btnLp);
        
        // 点击选择应用按钮
        selectAppBtn.setOnClickListener(v -> {
            showAppPickerForShortcut(pkgInput, nameInput, dialogIconPathInput, dialogImagePreview, clsInput);
        });
        
        pkgRow.addView(selectAppBtn);
        formLayout.addView(pkgRow);

        // 间距
        TextView spacer3 = new TextView(this);
        spacer3.setText("");
        spacer3.setPadding(0, dpToPx(8), 0, 0);
        formLayout.addView(spacer3);

        // 添加类名标签和输入框
        formLayout.addView(clsLabel);
        formLayout.addView(clsInput);

        // ========== 分隔 + 启动方式配置 ==========
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

        // "恢复默认"按钮（仅4个默认快捷方式）
        if (existing != null) {
            boolean isDefaultSc = false;
            String[] defIds = {"alipay_scan", "alipay_pay", "wechat_scan", "wechat_pay"};
            for (String did : defIds) {
                if (did.equals(existing.getId())) { isDefaultSc = true; break; }
            }
            if (isDefaultSc) {
                Button resetBtn = new Button(this);
                resetBtn.setText("恢复默认图标");
                resetBtn.setTextSize(14);
                resetBtn.setTextColor(0xFFFFFFFF);
                resetBtn.setBackgroundColor(0xFF4CAF50);
                resetBtn.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                resetLp.setMargins(0, dpToPx(12), 0, 0);
                resetBtn.setLayoutParams(resetLp);
                resetBtn.setOnClickListener(v -> {
                    int resId = getResources().getIdentifier(existing.getId(), "drawable", getPackageName());
                    if (resId == 0) {
                        Toast.makeText(this, "未找到默认图标资源", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        File iconsDir = new File(getFilesDir(), ICONS_DIR);
                        if (!iconsDir.exists()) iconsDir.mkdirs();
                        File destFile = new File(iconsDir, existing.getId() + ".png");
                        InputStream in = getResources().openRawResource(resId);
                        FileOutputStream out = new FileOutputStream(destFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.close();
                        in.close();

                        String savedPath = destFile.getAbsolutePath();
                        dialogIconPathInput.setText(savedPath);
                        dialogIconInput.setText("");
                        dialogTypeIndicator.setText("当前：图片图标");
                        Bitmap bmp = loadIconBitmap(savedPath);
                        if (bmp != null) {
                            dialogImagePreview.setImageBitmap(bmp);
                            dialogImagePreview.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(this, "已恢复默认图标", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.util.Log.e("ShortcutEdit", "恢复默认图标失败", e);
                    }
                });
                formLayout.addView(resetBtn);
            }
        }

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

        // 创建可滑动的对话框内容
        ScrollView dialogScrollView = new ScrollView(this);
        dialogScrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialogScrollView.addView(formLayout);

        // 创建对话框
        boolean isNew = existing == null;
        new AlertDialog.Builder(this)
                .setTitle(isNew ? "添加快捷方式" : "编辑快捷方式")
                .setView(dialogScrollView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String pkg = pkgInput.getText().toString().trim();
                    String cls = clsInput.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(pkg)) {
                        Toast.makeText(this, "请输入包名", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 判断图标类型：如果用户输入了 emoji 则优先使用 emoji
                    String emojiText = dialogIconInput.getText().toString().trim();
                    String iconPath = dialogIconPathInput.getText().toString().trim();
                    String iconType;
                    String icon;

                    if (!TextUtils.isEmpty(emojiText)) {
                        // 用户输入了 emoji → 使用 emoji 类型
                        iconType = "emoji";
                        icon = emojiText;
                        iconPath = "";
                    } else if (!TextUtils.isEmpty(iconPath)) {
                        // 用户选择了图片 → 使用图片类型
                        iconType = "image";
                        icon = "";
                    } else {
                        // 都没有 → 默认使用 emoji
                        iconType = "emoji";
                        icon = "⚡";
                        iconPath = "";
                    }

                    ShortcutItem targetItem;
                    if (isNew) {
                        // 添加
                        targetItem = new ShortcutItem(shortcutId, name, icon, iconType, iconPath, pkg, cls);
                        shortcutList.add(targetItem);
                        Toast.makeText(this, "快捷方式已添加", Toast.LENGTH_SHORT).show();
                    } else {
                        // 更新
                        targetItem = existing;
                        existing.setName(name);
                        existing.setIcon(icon);
                        existing.setIconType(iconType);
                        existing.setIconPath(iconPath);
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

    /**
     * 图片选择回调
     */
    private void onImagePicked(Uri uri) {
        if (uri == null || editingShortcutId == null) return;

        try {
            // 创建图标存储目录
            File iconsDir = new File(getFilesDir(), ICONS_DIR);
            if (!iconsDir.exists()) {
                iconsDir.mkdirs();
            }

            // 复制图片到内部存储
            String fileName = editingShortcutId + ".png";
            File destFile = new File(iconsDir, fileName);

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                FileOutputStream outputStream = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

                // 更新对话框中的预览和路径
                final String savedPath = destFile.getAbsolutePath();
                dialogIconPathInput.setText(savedPath);

                Bitmap bmp = loadIconBitmap(savedPath);
                if (bmp != null) {
                    dialogImagePreview.setImageBitmap(bmp);
                    dialogImagePreview.setVisibility(View.VISIBLE);
                }

                // 清除 emoji 输入提示
                dialogIconInput.setText("");
                dialogTypeIndicator.setText("当前：图片图标");

                Toast.makeText(this, "图标已选择", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("ShortcutEdit", "保存图标失败", e);
            Toast.makeText(this, "选择图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 从文件路径加载图片 Bitmap
     */
    private Bitmap loadIconBitmap(String path) {
        if (TextUtils.isEmpty(path)) return null;
        try {
            File file = new File(path);
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            android.util.Log.e("ShortcutEdit", "加载图标失败: " + path, e);
        }
        return null;
    }

    /**
     * 根据 ID 生成占位颜色
     */
    private int getPlaceholderColor(String id) {
        if (id == null) return 0xFF1677FF;
        switch (id) {
            case "alipay_scan": return 0xFF1677FF; // 蓝色
            case "alipay_pay": return 0xFF1677FF;
            case "wechat_scan": return 0xFF07C160; // 绿色
            case "wechat_pay": return 0xFF07C160;
            default: {
                int hash = id.hashCode();
                int r = (hash & 0xFF) % 200 + 55;
                int g = ((hash >> 8) & 0xFF) % 200 + 55;
                int b = ((hash >> 16) & 0xFF) % 200 + 55;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * 将默认快捷方式的图标重置为项目自带的默认图片
     */
    private void resetShortcutIcon(int index) {
        if (index < 0 || index >= shortcutList.size()) return;
        ShortcutItem item = shortcutList.get(index);
        if (item == null) return;

        int resId = getResources().getIdentifier(item.getId(), "drawable", getPackageName());
        if (resId == 0) {
            Toast.makeText(this, "未找到默认图标资源", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File iconsDir = new File(getFilesDir(), ICONS_DIR);
            if (!iconsDir.exists()) iconsDir.mkdirs();

            File destFile = new File(iconsDir, item.getId() + ".png");
            InputStream in = getResources().openRawResource(resId);
            FileOutputStream out = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            in.close();

            item.setIconType("image");
            item.setIconPath(destFile.getAbsolutePath());
            item.setIcon("");
            saveShortcuts();
            refreshList();
            Toast.makeText(this, "已恢复默认图标", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("ShortcutEdit", "恢复默认图标失败", e);
            Toast.makeText(this, "恢复默认图标失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 将指定索引的快捷方式恢复为全部默认值（名称、图标、包名、类名、启动配置）
     */
    private void resetShortcutToDefault(int index) {
        if (index < 0 || index >= shortcutList.size()) return;
        ShortcutItem item = shortcutList.get(index);
        if (item == null) return;

        java.util.ArrayList<ShortcutItem> defaults = ShortcutItem.getDefaultShortcuts();
        ShortcutItem defaultItem = null;
        for (ShortcutItem d : defaults) {
            if (d.getId().equals(item.getId())) {
                defaultItem = d;
                break;
            }
        }
        if (defaultItem == null) {
            Toast.makeText(this, "未找到默认配置", Toast.LENGTH_SHORT).show();
            return;
        }

        // 恢复所有字段
        item.setName(defaultItem.getName());
        item.setIcon(defaultItem.getIcon());
        item.setIconType(defaultItem.getIconType());
        item.setPackageName(defaultItem.getPackageName());
        item.setClassName(defaultItem.getClassName());
        item.setEnabled(true);

        // 复制默认图标
        int resId = getResources().getIdentifier(item.getId(), "drawable", getPackageName());
        if (resId != 0) {
            try {
                File iconsDir = new File(getFilesDir(), ICONS_DIR);
                if (!iconsDir.exists()) iconsDir.mkdirs();
                File destFile = new File(iconsDir, item.getId() + ".png");
                InputStream in = getResources().openRawResource(resId);
                FileOutputStream out = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.close();
                in.close();
                item.setIconPath(destFile.getAbsolutePath());
            } catch (Exception e) {
                android.util.Log.e("ShortcutEdit", "恢复默认图标失败", e);
                item.setIconPath("");
            }
        } else {
            item.setIconPath("");
        }

        // 删除已保存的启动配置，使 load() 返回最新 getDefault()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove("shortcut_launch_" + item.getId())
                .apply();

        saveShortcuts();
        refreshList();
        Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show();
    }

    /**
     * 为快捷方式编辑显示应用选择器
     */
    private void showAppPickerForShortcut(final EditText pkgInput, final EditText nameInput,
                                          final EditText iconPathInput, final ImageView imagePreview,
                                          final EditText clsInput) {
        // 创建加载对话框
        final AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setMessage("正在加载应用列表...")
                .setCancelable(false)
                .create();
        loadingDialog.show();
        
        // 在后台线程中查询应用列表
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PackageManager pm = getPackageManager();
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
                    
                    // 过滤系统应用（默认不读取）
                    boolean includeSystemApps = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .getBoolean("include_system_apps", false);
                    List<ResolveInfo> filteredApps = new ArrayList<>();
                    for (ResolveInfo app : apps) {
                        if (includeSystemApps || !isSystemApp(pm, app)) {
                            filteredApps.add(app);
                        }
                    }
                    
                    // 按应用名排序
                    final Collator collator = Collator.getInstance(java.util.Locale.CHINESE);
                    Collections.sort(filteredApps, new Comparator<ResolveInfo>() {
                        @Override
                        public int compare(ResolveInfo a, ResolveInfo b) {
                            String labelA = a.loadLabel(pm).toString();
                            String labelB = b.loadLabel(pm).toString();
                            return collator.compare(labelA, labelB);
                        }
                    });
                    
                    // 准备列表数据
                    final String[] appNames = new String[filteredApps.size()];
                    final String[] appPackages = new String[filteredApps.size()];
                    final String[] appClasses = new String[filteredApps.size()];
                    final android.graphics.drawable.Drawable[] appIcons = new android.graphics.drawable.Drawable[filteredApps.size()];
                    for (int i = 0; i < filteredApps.size(); i++) {
                        appNames[i] = filteredApps.get(i).loadLabel(pm).toString();
                        appPackages[i] = filteredApps.get(i).activityInfo.packageName;
                        appClasses[i] = filteredApps.get(i).activityInfo.name;
                        appIcons[i] = filteredApps.get(i).loadIcon(pm);
                    }
                    
                    // 在主线程中显示对话框
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingDialog.dismiss();
                            showAppPickerDialogForShortcut(appNames, appPackages, appClasses, appIcons,
                                    pkgInput, nameInput, iconPathInput, imagePreview, clsInput);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("ShortcutEdit", "加载应用列表失败", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingDialog.dismiss();
                            Toast.makeText(ShortcutEditActivity.this, "加载应用列表失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
    
    /**
     * 判断是否为系统应用
     */
    private boolean isSystemApp(PackageManager pm, ResolveInfo resolveInfo) {
        try {
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0);
            return (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 显示应用选择对话框（用于快捷方式编辑）
     */
    private void showAppPickerDialogForShortcut(final String[] appNames, final String[] appPackages,
                                                final String[] appClasses, final android.graphics.drawable.Drawable[] appIcons,
                                                final EditText pkgInput, final EditText nameInput,
                                                final EditText iconPathInput, final ImageView imagePreview,
                                                final EditText clsInput) {
        // 创建自定义视图
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        
        // 系统应用开关
        Switch switchSystemApps = new Switch(this);
        switchSystemApps.setText("显示系统应用");
        switchSystemApps.setChecked(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean("include_system_apps", false));
        switchSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean("include_system_apps", isChecked).apply();
            // 重新加载应用列表（关闭当前对话框并重新打开）
            showAppPickerForShortcut(pkgInput, nameInput, iconPathInput, imagePreview, clsInput);
        });
        layout.addView(switchSystemApps);
        
        // 创建 ListView
        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 自定义适配器（带图标）
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
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
                    LinearLayout itemLayout = new LinearLayout(ShortcutEditActivity.this);
                    itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                    itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    itemLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                    
                    // 图标
                    ImageView iconView = new ImageView(ShortcutEditActivity.this);
                    iconView.setId(View.generateViewId());
                    iconView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)));
                    iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    itemLayout.addView(iconView);
                    
                    // 名称
                    TextView nameView = new TextView(ShortcutEditActivity.this);
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
        };
        
        listView.setAdapter(adapter);
        layout.addView(listView);
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setView(layout)
                .setNegativeButton("取消", null);
        
        AlertDialog dialog = builder.create();
        
        // 设置列表项点击事件
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String pkg = appPackages[position];
                String name = appNames[position];
                String cls = appClasses[position];
                android.graphics.drawable.Drawable icon = appIcons[position];
                
                // 自动填充包名
                pkgInput.setText(pkg);
                
                // 自动填充名称（如果用户没有修改过）
                if (TextUtils.isEmpty(nameInput.getText().toString().trim())) {
                    nameInput.setText(name);
                }
                
                // 自动填充类名
                clsInput.setText(cls);
                
                // 保存图标到内部存储
                saveAppIconToStorage(pkg, icon);
                
                // 更新图标预览
                String savedIconPath = getSavedIconPath(pkg);
                if (!TextUtils.isEmpty(savedIconPath)) {
                    iconPathInput.setText(savedIconPath);
                    Bitmap bmp = loadIconBitmap(savedIconPath);
                    if (bmp != null) {
                        imagePreview.setImageBitmap(bmp);
                        imagePreview.setVisibility(View.VISIBLE);
                    }
                }
                
                Toast.makeText(ShortcutEditActivity.this, "已选择: " + name, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }
    
    /**
     * 将应用图标保存到内部存储
     */
    private void saveAppIconToStorage(String packageName, android.graphics.drawable.Drawable drawable) {
        try {
            File iconsDir = new File(getFilesDir(), ICONS_DIR);
            if (!iconsDir.exists()) {
                iconsDir.mkdirs();
            }
            
            File destFile = new File(iconsDir, packageName + ".png");
            
            // 将 Drawable 转换为 Bitmap
            android.graphics.Bitmap bitmap;
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            } else {
                bitmap = android.graphics.Bitmap.createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            
            // 保存为 PNG
            FileOutputStream out = new FileOutputStream(destFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            
            android.util.Log.d("ShortcutEdit", "图标已保存: " + destFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("ShortcutEdit", "保存图标失败", e);
        }
    }
    
    /**
     * 获取已保存的图标路径
     */
    private String getSavedIconPath(String packageName) {
        File iconsDir = new File(getFilesDir(), ICONS_DIR);
        File iconFile = new File(iconsDir, packageName + ".png");
        if (iconFile.exists()) {
            return iconFile.getAbsolutePath();
        }
        return null;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
