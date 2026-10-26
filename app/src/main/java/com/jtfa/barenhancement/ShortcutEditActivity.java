package com.jtfa.barenhancement;

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
import android.app.Dialog;
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
    private TextView dialogEmojiPreview;
    private EditText dialogIconPathInput;

    // 长按拖动排序相关
    private int draggingFromIndex = -1;

    // 图片选择启动器
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建根布局
        LinearLayout rootContainer = new LinearLayout(this);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setBackgroundColor(resolveThemeColor(R.attr.dividerColor));

        // 固定标题栏
        LinearLayout titleBarContainer = new LinearLayout(this);
        titleBarContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        titleBarContainer.setOrientation(LinearLayout.VERTICAL);
        titleBarContainer.setBackgroundColor(resolveThemeColor(R.attr.bgPage));
        titleBarContainer.setPadding(dpToPx(0), dpToPx(30), dpToPx(0), dpToPx(0));
        titleBarContainer.setElevation(dpToPx(2));

        TextView titleView = new TextView(this);
        titleView.setText("管理快捷方式");
        titleView.setTextSize(28);
        titleView.setTextColor(resolveThemeColor(R.attr.textPrimary));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(12));
        titleBarContainer.addView(titleView);

        // 标题分割线
        View titleDivider = new View(this);
        titleDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        titleDivider.setBackgroundColor(resolveThemeColor(R.attr.dividerColor));
        titleBarContainer.addView(titleDivider);

        rootContainer.addView(titleBarContainer);

        // 内容滚动区域
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // 说明
        TextView descView = new TextView(this);
        descView.setText("添加、编辑或删除快捷方式。双击提示条弹出快捷方式窗口。\n图标支持 emoji 和自定义图片。");
        descView.setTextSize(14);
        descView.setTextColor(resolveThemeColor(R.attr.textSecondary));
        descView.setPadding(0, 0, 0, dpToPx(20));
        contentLayout.addView(descView);

        // 列表卡片容器
        LinearLayout listCard = new LinearLayout(this);
        listCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setBackground(createCardBackground());
        listCard.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        contentLayout.addView(listCard);

        // 列表容器
        listContainer = new LinearLayout(this);
        listContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listCard.addView(listContainer);

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
                dpToPx(52));
        btnParams.setMargins(0, dpToPx(16), 0, 0);
        addButton.setLayoutParams(btnParams);
        addButton.setText("+ 添加快捷方式");
        stylePrimaryButton(addButton);
        addButton.setOnClickListener(v -> showEditDialog(null, -1));
        contentLayout.addView(addButton);

        scrollView.addView(contentLayout);
        rootContainer.addView(scrollView);
        setContentView(rootContainer);

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
            emptyView.setTextColor(resolveThemeColor(R.attr.textHint));
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
        card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        card.setBackground(createCardBackground());
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams marginParams = (LinearLayout.LayoutParams) card.getLayoutParams();
        marginParams.setMargins(0, 0, 0, dpToPx(8));
        card.setLayoutParams(marginParams);

        // 启用开关
        Switch enableSwitch = new Switch(this);
        enableSwitch.setChecked(item.isEnabled());
        enableSwitch.setPadding(0, 0, dpToPx(12), 0);
        enableSwitch.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            enableSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(resolveThemeColor(R.attr.colorPrimaryBrand)));
            enableSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(resolveThemeColor(R.attr.trackColor)));
        }
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setEnabled(isChecked);
            saveShortcuts();
        });
        card.addView(enableSwitch);

        // 图标（预览）
        if ("image".equals(item.getIconType())) {
            ImageView iconView = new ImageView(this);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            iconLp.setMargins(0, 0, dpToPx(12), 0);
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
            iconView.setPadding(0, 0, dpToPx(12), 0);
            card.addView(iconView);
        }

        // 名称和详情
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        infoLayout.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(item.getName());
        nameView.setTextSize(15);
        nameView.setTextColor(resolveThemeColor(R.attr.textPrimary));
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoLayout.addView(nameView);

        String typeHint = "image".equals(item.getIconType()) ? " [图片]" : " [Emoji]";
        TextView detailView = new TextView(this);
        detailView.setText(item.getPackageName() + "\n" + item.getClassName() + typeHint);
        detailView.setTextSize(12);
        detailView.setTextColor(resolveThemeColor(R.attr.textHint));
        detailView.setMaxLines(1);
        detailView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailView.setPadding(0, dpToPx(2), 0, 0);
        infoLayout.addView(detailView);

        card.addView(infoLayout);

        // 编辑按钮
        TextView editBtn = new TextView(this);
        editBtn.setText("编辑");
        editBtn.setTextSize(13);
        editBtn.setTextColor(resolveThemeColor(R.attr.colorPrimaryBrand));
        editBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
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
            resetBtn.setTextSize(13);
            resetBtn.setTextColor(0xFF4CAF50);
            resetBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
            resetBtn.setClickable(true);
            resetBtn.setOnClickListener(v -> resetShortcutToDefault(index));
            card.addView(resetBtn);
        } else {
            // 用户自建项：只显示"删除"按钮
            TextView delBtn = new TextView(this);
            delBtn.setText("删除");
            delBtn.setTextSize(13);
            delBtn.setTextColor(0xFFF44336);
            delBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
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
        formLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // ===== 应用包名 =====
        TextView pkgLabel = new TextView(this);
        pkgLabel.setText("应用包名");
        pkgLabel.setTextSize(14);
        pkgLabel.setTextColor(resolveThemeColor(R.attr.textPrimary));
        pkgLabel.setPadding(0, 0, 0, dpToPx(6));
        formLayout.addView(pkgLabel);

        // 名称输入框（先创建，供选择应用回填名称使用）
        EditText nameInput = new EditText(this);
        nameInput.setHint("输入快捷方式名称");
        nameInput.setText(existing != null ? existing.getName() : "");
        nameInput.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        nameInput.setBackground(createInputBackground());
        nameInput.setTextSize(14);
        nameInput.setGravity(android.view.Gravity.CENTER_VERTICAL);
        nameInput.setSingleLine(true);

        // Activity 类名输入框（先创建，供选择应用回填类名使用）
        EditText clsInput = new EditText(this);
        clsInput.setHint("输入 Activity 完整类名");
        clsInput.setText(existing != null ? existing.getClassName() : "");
        clsInput.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        clsInput.setBackground(createInputBackground());
        clsInput.setTextSize(14);
        clsInput.setSingleLine(true);

        // 包名输入行（包含输入框和选择按钮）
        LinearLayout pkgRow = new LinearLayout(this);
        pkgRow.setOrientation(LinearLayout.HORIZONTAL);
        pkgRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        EditText pkgInput = new EditText(this);
        pkgInput.setId(View.generateViewId());
        pkgInput.setHint("输入应用包名");
        pkgInput.setText(existing != null ? existing.getPackageName() : "");
        pkgInput.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        pkgInput.setBackground(createInputBackground());
        pkgInput.setTextSize(14);
        pkgInput.setSingleLine(true);
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pkgInput.setLayoutParams(pkgLp);
        pkgRow.addView(pkgInput);

        Button selectAppBtn = new Button(this);
        selectAppBtn.setText("选择应用");
        selectAppBtn.setTextSize(13);
        selectAppBtn.setTextColor(0xFFFFFFFF);
        selectAppBtn.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.colorPrimaryBrand), dpToPx(10)));
        selectAppBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        selectAppBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(dpToPx(8), 0, 0, 0);
        selectAppBtn.setLayoutParams(btnLp);
        pkgRow.addView(selectAppBtn);
        formLayout.addView(pkgRow);

        // 间距
        TextView spacer1 = new TextView(this);
        spacer1.setText("");
        spacer1.setPadding(0, dpToPx(16), 0, 0);
        formLayout.addView(spacer1);

        // ===== 名称和图标 =====
        TextView nameLabel = new TextView(this);
        nameLabel.setText("名称和图标");
        nameLabel.setTextSize(14);
        nameLabel.setTextColor(resolveThemeColor(R.attr.textPrimary));
        nameLabel.setPadding(0, 0, 0, dpToPx(6));
        formLayout.addView(nameLabel);

        LinearLayout nameIconRow = new LinearLayout(this);
        nameIconRow.setOrientation(LinearLayout.HORIZONTAL);
        nameIconRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, dpToPx(44), 1));
        nameIconRow.addView(nameInput);

        final boolean existingIsImage = existing != null && "image".equals(existing.getIconType());

        // 图标预览（图片；旧数据的 emoji 只读显示）
        FrameLayout previewBox = new FrameLayout(this);
        int previewSize = dpToPx(44);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(previewSize, previewSize);
        previewLp.setMargins(dpToPx(8), 0, 0, 0);
        previewBox.setLayoutParams(previewLp);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(dpToPx(8));
        previewBg.setColor(resolveThemeColor(R.attr.bgPage));
        previewBox.setBackground(previewBg);
        previewBox.setClipToOutline(true);

        dialogImagePreview = new ImageView(this);
        dialogImagePreview.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialogImagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        dialogImagePreview.setVisibility(View.GONE);
        previewBox.addView(dialogImagePreview);

        dialogEmojiPreview = new TextView(this);
        dialogEmojiPreview.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialogEmojiPreview.setGravity(android.view.Gravity.CENTER);
        dialogEmojiPreview.setTextSize(22);
        dialogEmojiPreview.setTextColor(resolveThemeColor(R.attr.textPrimary));
        dialogEmojiPreview.setVisibility(View.GONE);
        previewBox.addView(dialogEmojiPreview);
        nameIconRow.addView(previewBox);

        // 图片路径（隐藏输入框，仅用于存储路径）
        dialogIconPathInput = new EditText(this);
        dialogIconPathInput.setVisibility(View.GONE);
        String existingPath = (existingIsImage && existing != null && existing.getIconPath() != null)
                ? existing.getIconPath() : "";
        dialogIconPathInput.setText(existingPath);

        // 选择图片（两行文字，压缩宽度）
        Button selectImageBtn = new Button(this);
        selectImageBtn.setText("选择\n图片");
        selectImageBtn.setTextSize(11);
        selectImageBtn.setTextColor(0xFFFFFFFF);
        selectImageBtn.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.colorPrimaryBrand), dpToPx(9)));
        selectImageBtn.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        selectImageBtn.setStateListAnimator(null);
        selectImageBtn.setSingleLine(false);
        selectImageBtn.setMaxLines(2);
        selectImageBtn.setGravity(android.view.Gravity.CENTER);
        selectImageBtn.setIncludeFontPadding(false);
        selectImageBtn.setMinWidth(0);
        selectImageBtn.setMinimumWidth(0);
        selectImageBtn.setMinHeight(0);
        selectImageBtn.setMinimumHeight(0);
        LinearLayout.LayoutParams selectImageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectImageLp.setMargins(dpToPx(6), 0, 0, 0);
        selectImageBtn.setLayoutParams(selectImageLp);
        nameIconRow.addView(selectImageBtn);

        // 恢复默认图标（两行文字）
        Button resetBtn = new Button(this);
        resetBtn.setText("恢复默认\n图标");
        resetBtn.setTextSize(11);
        resetBtn.setTextColor(0xFFFFFFFF);
        resetBtn.setBackground(createRoundedButtonBg(0xFF4CAF50, dpToPx(9)));
        resetBtn.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        resetBtn.setStateListAnimator(null);
        resetBtn.setSingleLine(false);
        resetBtn.setMaxLines(2);
        resetBtn.setGravity(android.view.Gravity.CENTER);
        resetBtn.setIncludeFontPadding(false);
        resetBtn.setMinWidth(0);
        resetBtn.setMinimumWidth(0);
        resetBtn.setMinHeight(0);
        resetBtn.setMinimumHeight(0);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.setMargins(dpToPx(6), 0, 0, 0);
        resetBtn.setLayoutParams(resetLp);
        nameIconRow.addView(resetBtn);

        formLayout.addView(nameIconRow);

        // 初始化预览
        if (!TextUtils.isEmpty(existingPath)) {
            updateDialogIconPreview(existingPath, "");
        } else if (existing != null && !TextUtils.isEmpty(existing.getIcon())) {
            updateDialogIconPreview("", existing.getIcon());
        } else {
            updateDialogIconPreview("", "⚡");
        }

        final String shortcutId = existing != null ? existing.getId() : "sc_" + System.currentTimeMillis();

        selectImageBtn.setOnClickListener(v -> {
            editingShortcutId = shortcutId;
            imagePickerLauncher.launch("image/*");
        });

        resetBtn.setOnClickListener(v -> {
            if (existing != null && isDefaultShortcutId(existing.getId())) {
                restoreBundledIcon(existing.getId());
            } else {
                restoreAppIcon(pkgInput);
            }
        });

        selectAppBtn.setOnClickListener(v ->
                showAppPickerForShortcut(pkgInput, nameInput, dialogIconPathInput, dialogImagePreview, clsInput));

        // 间距
        TextView spacer2 = new TextView(this);
        spacer2.setText("");
        spacer2.setPadding(0, dpToPx(16), 0, 0);
        formLayout.addView(spacer2);

        // ===== Activity 类名 =====
        TextView clsLabel = new TextView(this);
        clsLabel.setText("Activity 类名");
        clsLabel.setTextSize(14);
        clsLabel.setTextColor(resolveThemeColor(R.attr.textPrimary));
        clsLabel.setPadding(0, 0, 0, dpToPx(6));
        formLayout.addView(clsLabel);

        LinearLayout clsRow = new LinearLayout(this);
        clsRow.setOrientation(LinearLayout.HORIZONTAL);
        clsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        clsInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        clsRow.addView(clsInput);

        Button pickActivityBtn = new Button(this);
        pickActivityBtn.setText("选择 Activity");
        pickActivityBtn.setTextSize(13);
        pickActivityBtn.setTextColor(0xFFFFFFFF);
        pickActivityBtn.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.colorPrimaryBrand), dpToPx(10)));
        pickActivityBtn.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        pickActivityBtn.setStateListAnimator(null);
        pickActivityBtn.setSingleLine(true);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickLp.setMargins(dpToPx(8), 0, 0, 0);
        pickActivityBtn.setLayoutParams(pickLp);
        pickActivityBtn.setOnClickListener(v ->
                GestureLaunchConfigDialog.showActivityPicker(
                        this, pkgInput.getText().toString().trim(), clsInput));
        clsRow.addView(pickActivityBtn);
        formLayout.addView(clsRow);

        // ========== 分隔 + 启动方式配置 ==========
        final ShortcutLaunchConfig launchConfig = ShortcutLaunchConfig.load(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE), shortcutId);

        TextView divider = new TextView(this);
        divider.setText("");
        divider.setPadding(0, dpToPx(20), 0, 0);
        formLayout.addView(divider);

        View line = new View(this);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        line.setBackgroundColor(resolveThemeColor(R.attr.dividerColor));
        line.setPadding(0, 0, 0, 0);
        formLayout.addView(line);

        TextView configTitle = new TextView(this);
        configTitle.setText("启动方式");
        configTitle.setTextSize(16);
        configTitle.setTextColor(resolveThemeColor(R.attr.textPrimary));
        configTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        configTitle.setPadding(0, dpToPx(16), 0, dpToPx(12));
        formLayout.addView(configTitle);

        // 窗口打开开关
        LinearLayout windowRow = new LinearLayout(this);
        windowRow.setOrientation(LinearLayout.HORIZONTAL);
        windowRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        windowRow.setPadding(0, 0, 0, dpToPx(12));

        TextView windowLabel = new TextView(this);
        windowLabel.setText("窗口打开");
        windowLabel.setTextSize(14);
        windowLabel.setTextColor(resolveThemeColor(R.attr.textPrimary));
        LinearLayout.LayoutParams windowLabelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        windowLabel.setLayoutParams(windowLabelLp);
        windowRow.addView(windowLabel);

        final Switch windowSwitch = new Switch(this);
        windowSwitch.setChecked(launchConfig.openInWindow);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            windowSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(resolveThemeColor(R.attr.colorPrimaryBrand)));
            windowSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(resolveThemeColor(R.attr.trackColor)));
        }
        windowRow.addView(windowSwitch);
        formLayout.addView(windowRow);

        TextView windowHint = new TextView(this);
        windowHint.setText("开启后使用系统小窗（自由窗口）打开应用");
        windowHint.setTextSize(12);
        windowHint.setTextColor(resolveThemeColor(R.attr.textHint));
        windowHint.setPadding(0, 0, 0, dpToPx(12));
        formLayout.addView(windowHint);

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
        editUri.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        editUri.setBackground(createInputBackground());
        editUri.setTextSize(14);
        LinearLayout.LayoutParams uriLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        uriLp.setMargins(0, 0, 0, dpToPx(12));
        formLayout.addView(editUri, uriLp);

        // Extras 容器
        final LinearLayout extrasContainer = new LinearLayout(this);
        extrasContainer.setOrientation(LinearLayout.VERTICAL);
        formLayout.addView(extrasContainer);
        
        // 添加 Extra 按钮
        Button addExtraBtn = new Button(this);
        addExtraBtn.setText("+ 添加 Extra");
        addExtraBtn.setTextSize(13);
        addExtraBtn.setTextColor(0xFFFFFFFF);
        addExtraBtn.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.colorPrimaryBrand), dpToPx(10)));
        addExtraBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        addExtraBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams addBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addBtnLp.setMargins(0, dpToPx(12), 0, dpToPx(12));
        addExtraBtn.setLayoutParams(addBtnLp);
        
        // 用于存储所有 Extra 行的引用
        final java.util.List<LinearLayout> extraRows = new java.util.ArrayList<>();
        final java.util.List<EditText> extraKeyInputs = new java.util.ArrayList<>();
        final java.util.List<EditText> extraValueInputs = new java.util.ArrayList<>();
        final java.util.List<Spinner> extraTypeSpinners = new java.util.ArrayList<>();
        
        // 创建 Extra 行的方法
        final Runnable[] createExtraRow = new Runnable[1];
        createExtraRow[0] = new Runnable() {
            @Override
            public void run() {
                LinearLayout row = new LinearLayout(ShortcutEditActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, 0, 0, dpToPx(6));
                
                // Key 输入框
                EditText keyInput = new EditText(ShortcutEditActivity.this);
                keyInput.setHint("Key");
                keyInput.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                keyInput.setBackground(createInputBackground());
                keyInput.setTextSize(13);
                LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                keyLp.setMargins(0, 0, dpToPx(6), 0);
                keyInput.setLayoutParams(keyLp);
                row.addView(keyInput);
                
                // Value 输入框
                EditText valueInput = new EditText(ShortcutEditActivity.this);
                valueInput.setHint("Value");
                valueInput.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
                valueInput.setBackground(createInputBackground());
                valueInput.setTextSize(13);
                LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                valLp.setMargins(0, 0, dpToPx(6), 0);
                valueInput.setLayoutParams(valLp);
                row.addView(valueInput);
                
                // 类型选择器
                Spinner typeSpinner = new Spinner(ShortcutEditActivity.this);
                String[] types = {"string", "boolean", "int", "long", "float"};
                ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(ShortcutEditActivity.this,
                        android.R.layout.simple_spinner_item, types);
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                typeSpinner.setAdapter(typeAdapter);
                LinearLayout.LayoutParams typeLp = new LinearLayout.LayoutParams(dpToPx(90), ViewGroup.LayoutParams.WRAP_CONTENT);
                typeLp.setMargins(0, 0, dpToPx(6), 0);
                typeSpinner.setLayoutParams(typeLp);
                row.addView(typeSpinner);
                
                // 删除按钮
                Button deleteBtn = new Button(ShortcutEditActivity.this);
                deleteBtn.setText("×");
                deleteBtn.setTextSize(16);
                deleteBtn.setTextColor(0xFFFFFFFF);
                deleteBtn.setBackground(createRoundedButtonBg(0xFFF44336, dpToPx(8)));
                deleteBtn.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
                deleteBtn.setStateListAnimator(null);
                deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(36), ViewGroup.LayoutParams.WRAP_CONTENT));
                
                final int index = extraRows.size();
                deleteBtn.setOnClickListener(v -> {
                    extrasContainer.removeView(row);
                    extraRows.remove(row);
                    extraKeyInputs.remove(keyInput);
                    extraValueInputs.remove(valueInput);
                    extraTypeSpinners.remove(typeSpinner);
                });
                
                row.addView(deleteBtn);
                
                extrasContainer.addView(row);
                extraRows.add(row);
                extraKeyInputs.add(keyInput);
                extraValueInputs.add(valueInput);
                extraTypeSpinners.add(typeSpinner);
            }
        };
        
        addExtraBtn.setOnClickListener(v -> {
            createExtraRow[0].run();
        });
        
        formLayout.addView(addExtraBtn);
        
        // 加载已有的 extras
        if (launchConfig.extras != null && !launchConfig.extras.isEmpty()) {
            for (ShortcutLaunchConfig.ExtraItem item : launchConfig.extras) {
                createExtraRow[0].run();
                int lastIdx = extraKeyInputs.size() - 1;
                extraKeyInputs.get(lastIdx).setText(item.key);
                extraValueInputs.get(lastIdx).setText(item.value);
                String[] types = {"string", "boolean", "int", "long", "float"};
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equals(item.type)) {
                        extraTypeSpinners.get(lastIdx).setSelection(i);
                        break;
                    }
                }
            }
        } else if (!TextUtils.isEmpty(launchConfig.extraKey)) {
            // 兼容旧版本
            createExtraRow[0].run();
            int lastIdx = extraKeyInputs.size() - 1;
            extraKeyInputs.get(lastIdx).setText(launchConfig.extraKey);
            extraValueInputs.get(lastIdx).setText(launchConfig.extraValue);
            extraTypeSpinners.get(lastIdx).setSelection("boolean".equals(launchConfig.extraType) ? 1 : 0);
        }

        // 模式切换时更新可见性
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String mode = modes[pos];
                boolean isUrl = ShortcutLaunchConfig.MODE_URL.equals(mode);
                boolean isIntent = ShortcutLaunchConfig.MODE_INTENT.equals(mode);
                editUri.setVisibility(isUrl ? View.VISIBLE : View.GONE);
                extrasContainer.setVisibility(isIntent ? View.VISIBLE : View.GONE);
                addExtraBtn.setVisibility(isIntent ? View.VISIBLE : View.GONE);
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

        // 创建自定义对话框（现代卡片风格）
        boolean isNew = existing == null;

        // 外层容器：白色圆角卡片
        LinearLayout dialogContainer = new LinearLayout(this);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setBackground(createCardBackground());
        dialogContainer.setClipToOutline(true);

        // 对话框标题栏（标题 + 取消/保存按钮）
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dpToPx(20), dpToPx(14), dpToPx(16), dpToPx(14));

        TextView dialogTitle = new TextView(this);
        dialogTitle.setText(isNew ? "添加快捷方式" : "编辑快捷方式");
        dialogTitle.setTextSize(18);
        dialogTitle.setTextColor(resolveThemeColor(R.attr.textPrimary));
        dialogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        dialogTitle.setLayoutParams(titleLp);
        titleBar.addView(dialogTitle);

        // 取消按钮
        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(13);
        cancelBtn.setTextColor(resolveThemeColor(R.attr.textSecondary));
        cancelBtn.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.bgPage), dpToPx(10)));
        cancelBtn.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        cancelBtn.setStateListAnimator(null);
        titleBar.addView(cancelBtn);

        // 保存按钮
        Button saveBtn = new Button(this);
        saveBtn.setText("保存");
        saveBtn.setTextSize(13);
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.colorPrimaryBrand), dpToPx(10)));
        saveBtn.setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8));
        saveBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams saveBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveBtnLp.setMargins(dpToPx(8), 0, 0, 0);
        saveBtn.setLayoutParams(saveBtnLp);
        titleBar.addView(saveBtn);

        dialogContainer.addView(titleBar);

        // 标题分隔线
        View titleDivider = new View(this);
        titleDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        titleDivider.setBackgroundColor(resolveThemeColor(R.attr.dividerColor));
        dialogContainer.addView(titleDivider);

        // 内容区域
        dialogContainer.addView(dialogScrollView);

        // 创建自定义 Dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogContainer);
        dialog.setCancelable(true);

        // 设置对话框窗口样式
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // 保存按钮逻辑
        saveBtn.setOnClickListener(v -> {
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

            // 判断图标类型（不再支持输入 emoji，只有图片或保留原图标）
            String iconPath = dialogIconPathInput.getText().toString().trim();
            String iconType;
            String icon;

            if (!TextUtils.isEmpty(iconPath)) {
                iconType = "image";
                icon = "";
            } else if (existing != null) {
                String oldPath = existing.getIconPath() != null ? existing.getIconPath() : "";
                if (!TextUtils.isEmpty(oldPath)) {
                    iconType = "image";
                    icon = "";
                    iconPath = oldPath;
                } else if (!TextUtils.isEmpty(existing.getIcon())) {
                    iconType = "emoji";
                    icon = existing.getIcon();
                } else {
                    iconType = "emoji";
                    icon = "⚡";
                }
            } else {
                iconType = "emoji";
                icon = "⚡";
                iconPath = "";
            }

            ShortcutItem targetItem;
            if (isNew) {
                targetItem = new ShortcutItem(shortcutId, name, icon, iconType, iconPath, pkg, cls);
                shortcutList.add(targetItem);
                Toast.makeText(this, "快捷方式已添加", Toast.LENGTH_SHORT).show();
            } else {
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
            int modePos = spinnerMode.getSelectedItemPosition();
            launchConfig.launchMode = modes[modePos];
            launchConfig.uri = editUri.getText().toString().trim();
            launchConfig.targetPackage = pkg;
            // MODE_INTENT 时 targetClass 由启动配置自行管理（如 LauncherUI + Extra），
            // 不应被 ShortcutItem 的 className（如 BaseScanUI）覆盖
            if (!ShortcutLaunchConfig.MODE_INTENT.equals(launchConfig.launchMode)) {
                launchConfig.targetClass = cls;
            }

            // 收集所有 extras
            launchConfig.extras.clear();
            for (int i = 0; i < extraKeyInputs.size(); i++) {
                String key = extraKeyInputs.get(i).getText().toString().trim();
                String value = extraValueInputs.get(i).getText().toString().trim();
                if (!TextUtils.isEmpty(key)) {
                    String[] types = {"string", "boolean", "int", "long", "float"};
                    String type = types[extraTypeSpinners.get(i).getSelectedItemPosition()];
                    launchConfig.extras.add(new ShortcutLaunchConfig.ExtraItem(key, value, type));
                }
            }

            launchConfig.openInWindow = windowSwitch.isChecked();
            ShortcutLaunchConfig.save(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit(), shortcutId, launchConfig).apply();

            refreshList();
            dialog.dismiss();
        });

        // 取消按钮逻辑
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
                updateDialogIconPreview(savedPath, "");

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
     * 更新对话框里的图标预览：有图片显示图片，否则显示 emoji（旧数据只读兜底）
     */
    private void updateDialogIconPreview(String path, String emoji) {
        if (dialogImagePreview == null || dialogEmojiPreview == null) return;
        Bitmap bmp = loadIconBitmap(path);
        if (bmp != null) {
            dialogImagePreview.setImageBitmap(bmp);
            dialogImagePreview.setVisibility(View.VISIBLE);
            dialogEmojiPreview.setVisibility(View.GONE);
        } else {
            dialogImagePreview.setImageDrawable(null);
            dialogImagePreview.setVisibility(View.GONE);
            dialogEmojiPreview.setText(TextUtils.isEmpty(emoji) ? "⚡" : emoji);
            dialogEmojiPreview.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 是否为内置的四个默认快捷方式
     */
    private boolean isDefaultShortcutId(String id) {
        if (id == null) return false;
        return "alipay_scan".equals(id) || "alipay_pay".equals(id)
                || "wechat_scan".equals(id) || "wechat_pay".equals(id);
    }

    /**
     * 恢复内置默认图标（仅四个默认快捷方式）
     */
    private void restoreBundledIcon(String id) {
        int resId = getResources().getIdentifier(id, "drawable", getPackageName());
        if (resId == 0) {
            Toast.makeText(this, "未找到默认图标资源", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File iconsDir = new File(getFilesDir(), ICONS_DIR);
            if (!iconsDir.exists()) iconsDir.mkdirs();
            File destFile = new File(iconsDir, id + ".png");
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
            updateDialogIconPreview(savedPath, "");
            Toast.makeText(this, "已恢复默认图标", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("ShortcutEdit", "恢复默认图标失败", e);
            Toast.makeText(this, "恢复默认图标失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 恢复为所选应用本身的图标（非默认快捷方式）
     */
    private void restoreAppIcon(EditText pkgInput) {
        String pkg = pkgInput.getText().toString().trim();
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, "请先选择应用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(pkg);
            saveAppIconToStorage(pkg, icon);
            String savedPath = getSavedIconPath(pkg);
            if (TextUtils.isEmpty(savedPath)) {
                Toast.makeText(this, "获取应用图标失败", Toast.LENGTH_SHORT).show();
                return;
            }
            dialogIconPathInput.setText(savedPath);
            updateDialogIconPreview(savedPath, "");
            Toast.makeText(this, "已恢复默认图标", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("ShortcutEdit", "获取应用图标失败", e);
            Toast.makeText(this, "获取应用图标失败", Toast.LENGTH_SHORT).show();
        }
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
        layout.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        
        // 系统应用开关
        Switch switchSystemApps = new Switch(this);
        switchSystemApps.setText("显示系统应用");
        switchSystemApps.setTextSize(14);
        switchSystemApps.setChecked(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean("include_system_apps", false));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            switchSystemApps.setThumbTintList(android.content.res.ColorStateList.valueOf(resolveThemeColor(R.attr.colorPrimaryBrand)));
            switchSystemApps.setTrackTintList(android.content.res.ColorStateList.valueOf(resolveThemeColor(R.attr.trackColor)));
        }
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
        listView.setDivider(null);
        
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
                    itemLayout.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
                    
                    // 图标
                    ImageView iconView = new ImageView(ShortcutEditActivity.this);
                    iconView.setId(View.generateViewId());
                    iconView.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)));
                    iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    itemLayout.addView(iconView);
                    
                    // 名称
                    TextView nameView = new TextView(ShortcutEditActivity.this);
                    nameView.setId(View.generateViewId());
                    nameView.setTextSize(15);
                    nameView.setTextColor(resolveThemeColor(R.attr.textPrimary));
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
                    updateDialogIconPreview(savedIconPath, "");
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
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                if (width <= 0 || height <= 0) {
                    width = height = dpToPx(96);
                }
                bitmap = android.graphics.Bitmap.createBitmap(
                        width,
                        height,
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

    private int resolveThemeColor(int attrResId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * 创建白色圆角卡片背景
     */
    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(resolveThemeColor(R.attr.bgCard));
        drawable.setCornerRadius(dpToPx(16));
        return drawable;
    }

    /**
     * 创建现代风格按钮样式（蓝色主按钮）
     */
    private void stylePrimaryButton(Button button) {
        button.setTextSize(15);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.colorPrimaryBrand), dpToPx(12)));
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
    }

    /**
     * 创建现代风格次要按钮样式（浅蓝背景）
     */
    private void styleSecondaryButton(Button button) {
        button.setTextSize(15);
        button.setTextColor(resolveThemeColor(R.attr.colorPrimaryBrand));
        button.setBackground(createRoundedButtonBg(resolveThemeColor(R.attr.btnSecondaryBg), dpToPx(12)));
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);
    }

    /**
     * 创建圆角按钮背景
     */
    private GradientDrawable createRoundedButtonBg(int color, int cornerRadius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(cornerRadius);
        return drawable;
    }

    /**
     * 创建现代风格输入框背景
     */
    private GradientDrawable createInputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(resolveThemeColor(R.attr.inputBg));
        drawable.setStroke(dpToPx(1), resolveThemeColor(R.attr.inputStroke));
        drawable.setCornerRadius(dpToPx(12));
        return drawable;
    }
}
