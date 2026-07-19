package com.example.aiwindow;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 快捷方式启动配置
 * 控制每个快捷方式的启动方式（URL Scheme / Intent Extra / 显式组件）
 * 支持持久化到 SharedPreferences
 */
public class ShortcutLaunchConfig {
    public static final String MODE_URL = "url_scheme";
    public static final String MODE_INTENT = "intent_extra";
    public static final String MODE_EXPLICIT = "explicit";

    public String launchMode = MODE_URL;
    public String uri = "";
    public String targetPackage = "";
    public String targetClass = "";
    public boolean openInWindow = false; // 是否使用系统小窗打开
    
    // 支持多个 Extra
    public java.util.List<ExtraItem> extras = new java.util.ArrayList<>();
    
    // 兼容旧版本的单字段（已废弃）
    @Deprecated
    public String extraKey = "";
    @Deprecated
    public String extraValue = "";
    @Deprecated
    public String extraType = "boolean"; // "boolean" or "string"
    
    /**
     * Extra 项
     */
    public static class ExtraItem {
        public String key;
        public String value;
        public String type; // "boolean", "string", "int", "long", "float"
        
        public ExtraItem() {}
        
        public ExtraItem(String key, String value, String type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }
    }

    public ShortcutLaunchConfig() {}

    public ShortcutLaunchConfig(String launchMode, String uri, String targetPackage,
                                String targetClass, String extraKey, String extraValue, String extraType) {
        this.launchMode = launchMode;
        this.uri = uri;
        this.targetPackage = targetPackage;
        this.targetClass = targetClass;
        this.extraKey = extraKey;
        this.extraValue = extraValue;
        this.extraType = extraType;
    }

    /** 获取指定快捷方式的默认配置 */
    public static ShortcutLaunchConfig getDefault(String shortcutId) {
        ShortcutLaunchConfig c = new ShortcutLaunchConfig();
        switch (shortcutId) {
            case "alipay_scan":
                c.launchMode = MODE_URL;
                c.uri = "alipayqr://platformapi/startapp?saId=10000007";
                c.targetPackage = "com.eg.android.AlipayGphone";
                c.targetClass = "com.alipay.mobile.scan.aidl.ScanMainActivity";
                break;
            case "alipay_pay":
                c.launchMode = MODE_URL;
                c.uri = "alipayqr://platformapi/startapp?saId=20000056";
                c.targetPackage = "com.eg.android.AlipayGphone";
                c.targetClass = "com.alipay.mobile.payee.ui.PayeeActivity";
                break;
            case "wechat_scan":
                c.launchMode = MODE_INTENT;
                c.targetPackage = "com.tencent.mm";
                c.targetClass = "com.tencent.mm.ui.LauncherUI";
                c.extras.add(new ExtraItem("LauncherUI.From.Scaner.Shortcut", "true", "boolean"));
                break;
            case "wechat_pay":
                c.launchMode = MODE_INTENT;
                c.targetPackage = "com.tencent.mm";
                c.targetClass = "com.tencent.mm.ui.LauncherUI";
                c.extras.add(new ExtraItem("LauncherUI.Shortcut.LaunchType", "launch_type_offline_wallet", "string"));
                break;
        }
        return c;
    }

    public static ShortcutLaunchConfig fromJson(JSONObject json) {
        ShortcutLaunchConfig c = new ShortcutLaunchConfig();
        c.launchMode = json.optString("launchMode", MODE_URL);
        c.uri = json.optString("uri", "");
        c.targetPackage = json.optString("targetPackage", "");
        c.targetClass = json.optString("targetClass", "");
        c.openInWindow = json.optBoolean("openInWindow", false);
        
        // 尝试读取 extras 列表
        if (json.has("extras")) {
            try {
                org.json.JSONArray extrasArray = json.getJSONArray("extras");
                for (int i = 0; i < extrasArray.length(); i++) {
                    JSONObject extraObj = extrasArray.getJSONObject(i);
                    ExtraItem item = new ExtraItem(
                        extraObj.optString("key", ""),
                        extraObj.optString("value", ""),
                        extraObj.optString("type", "string")
                    );
                    c.extras.add(item);
                }
            } catch (Exception e) {
                // 解析失败，忽略
            }
        }
        
        // 兼容旧版本：如果没有 extras，从旧字段迁移
        if (c.extras.isEmpty()) {
            String oldKey = json.optString("extraKey", "");
            if (!TextUtils.isEmpty(oldKey)) {
                c.extras.add(new ExtraItem(
                    oldKey,
                    json.optString("extraValue", ""),
                    json.optString("extraType", "boolean")
                ));
            }
        }
        
        return c;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("launchMode", launchMode);
            json.put("uri", uri);
            json.put("targetPackage", targetPackage);
            json.put("targetClass", targetClass);
            json.put("openInWindow", openInWindow);
            
            // 保存 extras 列表
            org.json.JSONArray extrasArray = new org.json.JSONArray();
            for (ExtraItem item : extras) {
                JSONObject extraObj = new JSONObject();
                extraObj.put("key", item.key);
                extraObj.put("value", item.value);
                extraObj.put("type", item.type);
                extrasArray.put(extraObj);
            }
            json.put("extras", extrasArray);
            
            return json;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** 从 SharedPreferences 加载 */
    public static ShortcutLaunchConfig load(SharedPreferences prefs, String shortcutId) {
        String jsonStr = prefs.getString("shortcut_launch_" + shortcutId, "");
        if (TextUtils.isEmpty(jsonStr)) {
            return getDefault(shortcutId);
        }
        try {
            return fromJson(new JSONObject(jsonStr));
        } catch (Exception e) {
            return getDefault(shortcutId);
        }
    }

    /** 保存到 SharedPreferences，返回 Editor 以便链式调用 */
    public static android.content.SharedPreferences.Editor save(android.content.SharedPreferences.Editor editor, String shortcutId, ShortcutLaunchConfig config) {
        editor.putString("shortcut_launch_" + shortcutId, config.toJson().toString());
        return editor;
    }

    /** 获取模式的中文显示名 */
    public static String getModeDisplayName(String mode) {
        switch (mode) {
            case MODE_URL: return "URL Scheme";
            case MODE_INTENT: return "Intent Extra";
            case MODE_EXPLICIT: return "显式组件";
            default: return mode;
        }
    }

    /** 获取快捷方式的显示名 */
    public static String getShortcutDisplayName(String shortcutId) {
        switch (shortcutId) {
            case "alipay_scan": return "支付宝扫一扫";
            case "alipay_pay": return "支付宝付款码";
            case "wechat_scan": return "微信扫一扫";
            case "wechat_pay": return "微信付款码";
            default: return shortcutId;
        }
    }

    /** 获取配置的简短描述 */
    public String getSummary() {
        switch (launchMode) {
            case MODE_URL:
                return "URL: " + uri;
            case MODE_INTENT:
                String extraSummary;
                if (extras.isEmpty()) {
                    extraSummary = " 无Extra";
                } else if (extras.size() == 1) {
                    ExtraItem item = extras.get(0);
                    extraSummary = " Extra: " + item.key + "=" + item.value;
                } else {
                    extraSummary = " Extras: " + extras.size() + "个";
                }
                return targetClass.substring(targetClass.lastIndexOf('.') + 1) + extraSummary;
            case MODE_EXPLICIT:
                return targetClass.substring(targetClass.lastIndexOf('.') + 1);
            default:
                return launchMode;
        }
    }

    /**
     * 根据配置创建启动 Intent
     * @return 如果配置可用则返回 Intent，否则返回 null
     */
    public android.content.Intent buildLaunchIntent() {
        switch (launchMode) {
            case MODE_URL:
                if (!TextUtils.isEmpty(uri)) {
                    return new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(uri))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                break;
            case MODE_INTENT:
                if (!TextUtils.isEmpty(targetPackage) && !TextUtils.isEmpty(targetClass)) {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setClassName(targetPackage, targetClass);
                    
                    // 添加所有 extras
                    for (ExtraItem item : extras) {
                        if (!TextUtils.isEmpty(item.key)) {
                            switch (item.type) {
                                case "boolean":
                                    intent.putExtra(item.key, Boolean.parseBoolean(item.value));
                                    break;
                                case "int":
                                    try {
                                        intent.putExtra(item.key, Integer.parseInt(item.value));
                                    } catch (NumberFormatException e) {
                                        // 忽略无效值
                                    }
                                    break;
                                case "long":
                                    try {
                                        intent.putExtra(item.key, Long.parseLong(item.value));
                                    } catch (NumberFormatException e) {
                                        // 忽略无效值
                                    }
                                    break;
                                case "float":
                                    try {
                                        intent.putExtra(item.key, Float.parseFloat(item.value));
                                    } catch (NumberFormatException e) {
                                        // 忽略无效值
                                    }
                                    break;
                                case "string":
                                default:
                                    intent.putExtra(item.key, item.value);
                                    break;
                            }
                        }
                    }
                    
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    return intent;
                }
                break;
            case MODE_EXPLICIT:
                if (!TextUtils.isEmpty(targetPackage) && !TextUtils.isEmpty(targetClass)) {
                    android.content.Intent intent = new android.content.Intent();
                    intent.setClassName(targetPackage, targetClass);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    return intent;
                }
                break;
        }
        return null;
    }
}
