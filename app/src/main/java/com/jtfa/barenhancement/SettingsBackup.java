package com.jtfa.barenhancement;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 设置备份：导出 / 导入
 *
 * 备份为单个 JSON 文件（图标以 base64 内嵌），不含包名等与安装实例绑定的信息，
 * 因此修改包名后仍可导入。
 *
 * 导入时会校验：
 *  - 未知设置项（当前版本不认识的 key）：跳过
 *  - 类型不匹配：跳过
 *  - 数值超出当前版本取值范围：跳过
 *  - 快捷方式列表 / 启动配置 JSON 解析失败：跳过
 *  - 自定义图标路径按文件名重指向当前安装的 filesDir（换包名后旧绝对路径失效）
 * 任一项被跳过时，调用方提示「导入的设置与当前版本不一致，已跳过不匹配项」。
 */
public final class SettingsBackup {

    public static final String FORMAT = "xiaohengtiao-settings";
    public static final int FORMAT_VERSION = 1;

    private static final String TAG = "SettingsBackup";
    private static final String PREFS_NAME = "ai_window_settings";
    private static final String ICONS_DIR = "shortcut_icons";
    private static final String PREFIX_SHORTCUT_LAUNCH = "shortcut_launch_";
    private static final String PREFIX_GESTURE_LAUNCH = "gesture_launch_";
    private static final int MAX_STRING_LEN = 20000;
    private static final int MAX_ICON_BYTES = 5 * 1024 * 1024;

    private static final int T_INT = 0;
    private static final int T_BOOL = 1;
    private static final int T_STRING = 2;

    /** 当前版本认识的设置项及其类型 */
    private static final Map<String, Integer> KEY_TYPES = new HashMap<>();
    /** 数值型设置项的取值范围（闭区间），缺省表示不限制 */
    private static final Map<String, int[]> INT_RANGES = new HashMap<>();
    /** 颜色类设置项，要求 #RRGGBB 或 #AARRGGBB */
    private static final String[] COLOR_KEYS = {
            "border_color", "overlay_color", "pressed_bar_color", "shadow_color"
    };

    static {
        // ── 提示条 ──
        putInt("bar_width", 100, 1200);
        putInt("bar_height", 0, 150);
        putInt("bar_corner", 0, 60);
        putInt("bar_alpha", 0, 100);
        putInt("bar_position_x", -2000, 2000);
        putInt("bar_position_y", -2000, 2000);
        putInt("long_press_duration", 50, 2000);
        putInt("anim_duration", 50, 800);

        // ── 按下状态样式 ──
        putBool("pressed_bar_enabled");
        putString("pressed_bar_color");
        putInt("pressed_bar_alpha", 0, 100);
        putInt("pressed_bar_width", 100, 1200);
        putInt("pressed_bar_height", 0, 150);
        putInt("pressed_bar_corner", 0, 60);
        putInt("pressed_bar_feather_width", 0, 50);
        putInt("pressed_bar_position_y", -10, 10);
        putInt("pressed_bar_glow_radius", 0, 60);
        putString("pressed_bar_color_mode");

        // ── AI 窗口（功能总开关关闭时仍保留数据）──
        putString("ai_url");
        putInt("ai_style", 0, 2);
        putInt("ai_blur_radius", 0, 300);
        putInt("ai_overlay_alpha", 0, 255);
        putInt("window_width", 200, 600);
        putInt("window_height", 300, 900);
        putInt("window_position_x", -2000, 2000);
        putInt("window_position_y", -500, 500);
        putInt("border_width", 0, 20);
        putInt("border_alpha", 0, 100);
        putString("border_color");
        putInt("corner_radius", 0, 50);
        putInt("shadow_width", 0, 20);
        putString("shadow_color");
        putInt("shadow_alpha", 0, 100);
        putBool("show_close_button");
        putBool("show_refresh_button");
        putBool("show_back_button");
        putBool("close_on_outside_click");
        putBool("back_prioritize_webview");
        putString("custom_css");
        putBool("webpage_bg_transparent");
        putInt("top_bar_transparency", 0, 100);
        putInt("keyboard_offset", 0, 1000);
        putInt("keyboard_anim_duration", 50, 800);

        // ── 手势动作 ──
        putString("click_action");
        putString("click_app_package");
        putString("click_app_name");
        putString("click_app_class");
        putString("double_click_action");
        putString("double_click_app_package");
        putString("double_click_app_name");
        putString("double_click_app_class");
        putString("long_press_action");
        putString("long_press_app_package");
        putString("long_press_app_name");
        putString("long_press_app_class");
        putBool("click_open_in_window");
        putBool("double_click_open_in_window");
        putBool("long_press_open_in_window");
        putBool("click_vibrate_enabled");
        putBool("double_click_vibrate_enabled");
        putBool("long_press_vibrate_enabled");

        // ── 遮罩 ──
        putInt("overlay_alpha", 0, 100);
        putString("overlay_color");
        putInt("overlay_anim_duration", 180, 500);

        // ── 快捷窗口 ──
        putInt("shortcut_window_width", 200, 600);
        putInt("shortcut_window_height", 200, 600);
        putInt("shortcut_window_position_y", -500, 500);
        putInt("shortcut_corner_radius", 0, 50);
        putInt("shortcut_style", 0, 2);
        putInt("blur_sync_mode", 0, 2);
        putInt("shortcut_blur_radius", 0, 300);
        putInt("shortcut_overlay_alpha", 0, 255);
        putInt("shortcut_icon_size", 50, 150);
        putInt("shortcut_icon_corner", 0, 50);
        putInt("shortcut_edge_band_dp", 1, 12);
        putInt("shortcut_edge_opacity", 5, 100);
        putBool("shortcut_edge_refraction");
        putBool("shortcut_finger_glow");
        putBool("shortcut_drag_stretch");
        putInt("finger_glow_brightness", 5, 100);
        putInt("finger_glow_size", 50, 150);
        putString("shortcut_list");

        // ── 其它 ──
        putBool("boot_start_enabled");
        putBool("exclude_from_recents");
        putBool("include_system_apps");
        putBool("use_expand_directly");
        putBool("try_fullscreen_windowing");
        putString("custom_windowing_mode");
    }

    /**
     * 纯本机 UI 状态（如首次授权说明是否已展示），不随备份迁移：
     * 换包名后新安装仍应重新走授权说明，否则应用列表可能为空。
     */
    private static final java.util.Set<String> IGNORED_KEYS = new java.util.HashSet<>(
            java.util.Collections.singletonList("app_selector_first_time_shown"));

    private static void putInt(String key, int min, int max) {
        KEY_TYPES.put(key, T_INT);
        INT_RANGES.put(key, new int[]{min, max});
    }

    private static void putBool(String key) {
        KEY_TYPES.put(key, T_BOOL);
    }

    private static void putString(String key) {
        KEY_TYPES.put(key, T_STRING);
    }

    private SettingsBackup() {
    }

    // ═══════════════════════════════════════════════════════════
    // 导出
    // ═══════════════════════════════════════════════════════════

    public static String suggestedFileName() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        return "xiaohengtiao_settings_" + ts + ".json";
    }

    /** 生成完整备份 JSON（在后台线程调用） */
    public static JSONObject buildBackup(Context context) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        root.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()));
        root.put("appVersion", versionName(context));

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONObject prefsJson = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (IGNORED_KEYS.contains(entry.getKey())) continue;
            JSONObject wrapper = wrapValue(entry.getValue());
            if (wrapper != null) {
                prefsJson.put(entry.getKey(), wrapper);
            }
        }
        root.put("prefs", prefsJson);

        JSONObject iconsJson = new JSONObject();
        File iconsDir = new File(context.getFilesDir(), ICONS_DIR);
        File[] files = iconsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile() || f.length() <= 0 || f.length() > MAX_ICON_BYTES) continue;
                byte[] bytes = readAllBytes(f);
                if (bytes != null) {
                    iconsJson.put(f.getName(), Base64.encodeToString(bytes, Base64.NO_WRAP));
                }
            }
        }
        root.put("icons", iconsJson);
        return root;
    }

    private static JSONObject wrapValue(Object value) throws JSONException {
        JSONObject o = new JSONObject();
        if (value instanceof Integer) {
            o.put("t", "int");
            o.put("v", value);
        } else if (value instanceof Long) {
            o.put("t", "long");
            o.put("v", value);
        } else if (value instanceof Float) {
            o.put("t", "float");
            o.put("v", ((Float) value).doubleValue());
        } else if (value instanceof Boolean) {
            o.put("t", "bool");
            o.put("v", value);
        } else if (value instanceof String) {
            o.put("t", "string");
            o.put("v", value);
        } else {
            return null;
        }
        return o;
    }

    private static String versionName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 导入
    // ═══════════════════════════════════════════════════════════

    public static boolean looksLikeBackup(JSONObject root) {
        return root != null && FORMAT.equals(root.optString("format", ""));
    }

    /** 导入结果 */
    public static class ImportResult {
        public int applied;   // 已应用设置项数
        public int skipped;   // 因不匹配被跳过的项数（含未知项/类型不符/越界/解析失败）
        public int icons;     // 已写入的图标文件数
    }

    /** 应用备份（在后台线程调用） */
    public static ImportResult importBackup(Context context, JSONObject root) {
        ImportResult result = new ImportResult();
        JSONObject prefsJson = root.optJSONObject("prefs");
        if (prefsJson == null) return result;

        File iconsDir = new File(context.getFilesDir(), ICONS_DIR);
        if (!iconsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            iconsDir.mkdirs();
        }

        // 1. 先写图标，保证快捷方式列表重指向的路径已存在
        JSONObject iconsJson = root.optJSONObject("icons");
        if (iconsJson != null) {
            java.util.Iterator<String> it = iconsJson.keys();
            while (it.hasNext()) {
                String name = it.next();
                String safeName = new File(name).getName();
                if (safeName.isEmpty()) continue;
                try {
                    byte[] bytes = Base64.decode(iconsJson.optString(name, ""), Base64.DEFAULT);
                    if (bytes.length == 0) continue;
                    File dest = new File(iconsDir, safeName);
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        fos.write(bytes);
                    }
                    result.icons++;
                } catch (Exception e) {
                    Log.w(TAG, "图标写入失败: " + name, e);
                }
            }
        }

        // 2. 逐项校验并写入设置
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        java.util.Iterator<String> it = prefsJson.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (IGNORED_KEYS.contains(key)) continue;
            JSONObject item = prefsJson.optJSONObject(key);
            if (item == null || !isKnownKey(key)) {
                result.skipped++;
                continue;
            }
            if (applyOne(context, editor, key, item, iconsDir)) {
                result.applied++;
            } else {
                result.skipped++;
            }
        }
        editor.commit();
        return result;
    }

    private static boolean isKnownKey(String key) {
        return KEY_TYPES.containsKey(key)
                || key.startsWith(PREFIX_SHORTCUT_LAUNCH)
                || key.startsWith(PREFIX_GESTURE_LAUNCH);
    }

    private static boolean applyOne(Context context, SharedPreferences.Editor editor,
                                    String key, JSONObject item, File iconsDir) {
        String type = item.optString("t", "");
        Object raw = item.opt("v");

        // 快捷方式/手势启动配置：JSON 字符串
        if (key.startsWith(PREFIX_SHORTCUT_LAUNCH) || key.startsWith(PREFIX_GESTURE_LAUNCH)) {
            if (!"string".equals(type) || !(raw instanceof String)) return false;
            String json = (String) raw;
            if (json.length() > MAX_STRING_LEN) return false;
            try {
                new JSONObject(json);
            } catch (JSONException e) {
                return false;
            }
            editor.putString(key, json);
            return true;
        }

        Integer expected = KEY_TYPES.get(key);
        if (expected == null) return false;

        if (expected == T_INT) {
            if (!"int".equals(type) || !(raw instanceof Number)) return false;
            double d = ((Number) raw).doubleValue();
            if (d != Math.rint(d)) return false;
            int value = (int) Math.round(d);
            int[] range = INT_RANGES.get(key);
            if (range != null && (value < range[0] || value > range[1])) return false;
            editor.putInt(key, value);
            return true;
        }

        if (expected == T_BOOL) {
            if (!"bool".equals(type) || !(raw instanceof Boolean)) return false;
            editor.putBoolean(key, (Boolean) raw);
            return true;
        }

        // 字符串型
        if (!"string".equals(type) || !(raw instanceof String)) return false;
        String value = (String) raw;
        if (value.length() > MAX_STRING_LEN) return false;

        if (isColorKey(key) && !value.isEmpty() && !value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            return false;
        }
        if ("custom_windowing_mode".equals(key)
                && !value.trim().isEmpty()
                && !value.trim().matches("-?\\d+")) {
            return false;
        }
        if ("shortcut_list".equals(key)) {
            try {
                value = rewriteShortcutIconPaths(value, iconsDir);
            } catch (JSONException e) {
                return false;
            }
        }
        editor.putString(key, value);
        return true;
    }

    private static boolean isColorKey(String key) {
        for (String colorKey : COLOR_KEYS) {
            if (colorKey.equals(key)) return true;
        }
        return false;
    }

    /**
     * 快捷方式列表里的 iconPath 是绝对路径（含包名），换包名后必然失效。
     * 这里按文件名重新指向当前安装的 filesDir/shortcut_icons。
     */
    private static String rewriteShortcutIconPaths(String json, File iconsDir) throws JSONException {
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String path = item.optString("iconPath", "");
            if (path.isEmpty()) continue;
            String name = new File(path).getName();
            File dest = new File(iconsDir, name);
            item.put("iconPath", dest.exists() ? dest.getAbsolutePath() : "");
        }
        return array.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════

    private static byte[] readAllBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int n = fis.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /** 写出备份 JSON 到目标 Uri（在后台线程调用） */
    public static void writeBackupTo(Context context, android.net.Uri uri, JSONObject backup)
            throws Exception {
        byte[] data = backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        OutputStream os = context.getContentResolver().openOutputStream(uri, "wt");
        if (os == null) throw new java.io.IOException("无法打开输出流");
        try (OutputStream out = os) {
            out.write(data);
            out.flush();
        }
    }

    /** 读取备份 JSON（在后台线程调用），超过 20MB 视为非法 */
    public static JSONObject readBackupFrom(Context context, android.net.Uri uri) throws Exception {
        java.io.InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) throw new java.io.IOException("无法打开输入流");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = is) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 20 * 1024 * 1024) throw new java.io.IOException("备份文件过大");
                bos.write(buf, 0, n);
            }
        }
        String text = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        return new JSONObject(text);
    }

    /** 读取备份中的导出信息，用于导入确认框 */
    public static String describe(JSONObject root) {
        String time = root.optString("exportedAt", "");
        String version = root.optString("appVersion", "");
        StringBuilder sb = new StringBuilder();
        if (!time.isEmpty()) sb.append("备份时间：").append(time);
        if (!version.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("备份版本：").append(version);
        }
        return sb.toString();
    }
}
