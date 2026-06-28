package com.example.aiwindow;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 快捷方式数据模型
 * 表示一个快捷方式项，包含名称、图标（支持 emoji 和自定义图片）、包名、类名
 */
public class ShortcutItem {
    private String id;
    private String name;
    private String icon; // emoji 或 Unicode 字符（iconType="emoji" 时使用）
    private String iconType; // "emoji" 或 "image"
    private String iconPath; // 图片文件路径（iconType="image" 时使用，为空则显示纯色占位）
    private boolean enabled; // 是否启用
    private String packageName;
    private String className;

    public ShortcutItem() {
        this.id = "";
        this.name = "";
        this.icon = "⚡";
        this.iconType = "emoji";
        this.iconPath = "";
        this.enabled = true;
        this.packageName = "";
        this.className = "";
    }

    public ShortcutItem(String id, String name, String icon, String packageName, String className) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.iconType = "emoji";
        this.iconPath = "";
        this.packageName = packageName;
        this.className = className;
    }

    public ShortcutItem(String id, String name, String icon, String iconType, String iconPath,
                        String packageName, String className) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.iconType = iconType;
        this.iconPath = iconPath;
        this.enabled = true;
        this.packageName = packageName;
        this.className = className;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getIconType() { return iconType; }
    public void setIconType(String iconType) { this.iconType = iconType; }
    public String getIconPath() { return iconPath; }
    public void setIconPath(String iconPath) { this.iconPath = iconPath; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    /**
     * 序列化为 JSON
     */
    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("icon", icon);
            json.put("iconType", iconType);
            json.put("iconPath", iconPath);
            json.put("enabled", enabled);
            json.put("packageName", packageName);
            json.put("className", className);
            return json;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * 从 JSON 反序列化
     */
    public static ShortcutItem fromJson(JSONObject json) {
        ShortcutItem item = new ShortcutItem();
        item.setId(json.optString("id", ""));
        item.setName(json.optString("name", ""));
        item.setIcon(json.optString("icon", "⚡"));
        item.setIconType(json.optString("iconType", "emoji"));
        item.setIconPath(json.optString("iconPath", ""));
        item.setEnabled(json.optBoolean("enabled", true));
        item.setPackageName(json.optString("packageName", ""));
        item.setClassName(json.optString("className", ""));
        return item;
    }

    /**
     * 生成默认快捷方式列表
     * 使用纯色占位图作为图标（image 类型），用户可在编辑界面自行替换为图片
     */
    public static java.util.ArrayList<ShortcutItem> getDefaultShortcuts() {
        java.util.ArrayList<ShortcutItem> list = new java.util.ArrayList<>();
        list.add(new ShortcutItem("alipay_scan", "支付宝扫一扫", "", "image", "",
                "com.eg.android.AlipayGphone",
                "com.alipay.mobile.scan.aidl.ScanMainActivity"));
        list.add(new ShortcutItem("alipay_pay", "支付宝付款码", "", "image", "",
                "com.eg.android.AlipayGphone",
                "com.alipay.mobile.payee.ui.PayeeActivity"));
        list.add(new ShortcutItem("wechat_scan", "微信扫一扫", "", "image", "",
                "com.tencent.mm",
                "com.tencent.mm.plugin.scanner.ui.BaseScanUI"));
        list.add(new ShortcutItem("wechat_pay", "微信付款码", "", "image", "",
                "com.tencent.mm",
                "com.tencent.mm.ui.LauncherUI"));
        return list;
    }
}
