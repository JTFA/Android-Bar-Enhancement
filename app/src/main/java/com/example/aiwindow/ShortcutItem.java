package com.example.aiwindow;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 快捷方式数据模型
 * 表示一个快捷方式项，包含名称、图标、包名、类名
 */
public class ShortcutItem {
    private String id;
    private String name;
    private String icon; // emoji 或 Unicode 字符作为图标
    private String packageName;
    private String className;

    public ShortcutItem() {
        this.id = "";
        this.name = "";
        this.icon = "⚡";
        this.packageName = "";
        this.className = "";
    }

    public ShortcutItem(String id, String name, String icon, String packageName, String className) {
        this.id = id;
        this.name = name;
        this.icon = icon;
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
        item.setPackageName(json.optString("packageName", ""));
        item.setClassName(json.optString("className", ""));
        return item;
    }

    /**
     * 生成默认快捷方式列表
     */
    public static java.util.ArrayList<ShortcutItem> getDefaultShortcuts() {
        java.util.ArrayList<ShortcutItem> list = new java.util.ArrayList<>();
        list.add(new ShortcutItem("alipay_scan", "支付宝扫一扫", "🔍",
                "com.eg.android.AlipayGphone",
                "com.alipay.mobile.scan.aidl.ScanMainActivity"));
        list.add(new ShortcutItem("alipay_pay", "支付宝付款码", "💳",
                "com.eg.android.AlipayGphone",
                "com.alipay.mobile.payee.ui.PayeeActivity"));
        list.add(new ShortcutItem("wechat_scan", "微信扫一扫", "🔍",
                "com.tencent.mm",
                "com.tencent.mm.plugin.scanner.ui.BaseScanUI"));
        list.add(new ShortcutItem("wechat_pay", "微信付款码", "💳",
                "com.tencent.mm",
                "com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI"));
        return list;
    }
}
