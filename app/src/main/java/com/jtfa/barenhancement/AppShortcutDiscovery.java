package com.jtfa.barenhancement;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 应用快捷方式发现。
 *
 * Android 只允许默认桌面/助手读取其他应用的完整快捷方式列表（LauncherApps.getShortcuts）。
 * 普通应用能做的只有：
 * 1. 解析应用安装包中通过 android.app.shortcuts 声明的「静态快捷方式」；
 * 2. 找到实现了 ACTION_CREATE_SHORTCUT 的应用，打开它自己的快捷方式选择页。
 *
 * 运行时通过 ShortcutManager 生成的动态快捷方式（微信/支付宝等长按菜单常见）
 * 没有公开 API 可读，这里不会出现在静态列表中。
 */
public final class AppShortcutDiscovery {

    private static final String TAG = "ShortcutDiscovery";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String META_SHORTCUTS = "android.app.shortcuts";

    private AppShortcutDiscovery() {
    }

    /** 静态快捷方式 */
    public static class StaticShortcut {
        public String appLabel;
        public String packageName;
        public String shortcutId;
        public String label;
        public Drawable icon;
        public Intent intent;
        /** 目标 Activity 是否对外导出；未导出的目标普通应用无法启动 */
        public boolean launchable = true;
    }

    /** 支持 ACTION_CREATE_SHORTCUT 的应用 */
    public static class ShortcutPickerApp {
        public String appLabel;
        public String packageName;
        public String activityName;
        public Drawable icon;
    }

    public static class Result {
        public final List<StaticShortcut> staticShortcuts = new ArrayList<>();
        public final List<ShortcutPickerApp> pickerApps = new ArrayList<>();
    }

    /**
     * 扫描所有已安装应用。耗时操作，请在后台线程调用。
     */
    public static Result scan(Context context) {
        Result result = new Result();
        PackageManager pm = context.getPackageManager();
        String selfPkg = context.getPackageName();

        // 1) 静态快捷方式：解析 launcher Activity 上的 android.app.shortcuts
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherActivities;
        try {
            launcherActivities = pm.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.w(TAG, "查询 launcher Activity 失败", e);
            launcherActivities = Collections.emptyList();
        }

        Set<String> seenShortcuts = new HashSet<>();
        for (ResolveInfo ri : launcherActivities) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.applicationInfo == null) continue;
            if (selfPkg.equals(ai.packageName)) continue;

            XmlResourceParser parser = null;
            try {
                parser = ai.loadXmlMetaData(pm, META_SHORTCUTS);
                if (parser == null) continue;

                Resources res = pm.getResourcesForApplication(ai.applicationInfo);
                String appLabel = safeLabel(ri.loadLabel(pm), ai.packageName);
                Drawable appIcon = ri.loadIcon(pm);
                if (appIcon == null) {
                    appIcon = pm.getDefaultActivityIcon();
                }
                parseShortcutsXml(res, parser, pm, ai.packageName, appLabel, appIcon,
                        seenShortcuts, result.staticShortcuts);
            } catch (Exception e) {
                Log.w(TAG, "解析快捷方式失败: " + ai.packageName, e);
            } finally {
                if (parser != null) {
                    try {
                        parser.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 2) 支持 ACTION_CREATE_SHORTCUT 的应用（旧版快捷方式选择页）
        Set<String> seenPickerActivities = new HashSet<>();
        try {
            Intent createIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
            List<ResolveInfo> pickers = pm.queryIntentActivities(createIntent, 0);
            for (ResolveInfo ri : pickers) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || selfPkg.equals(ai.packageName) || !ai.exported) continue;
                if (!seenPickerActivities.add(ai.packageName + "/" + ai.name)) continue;

                ShortcutPickerApp app = new ShortcutPickerApp();
                app.appLabel = safeLabel(ri.loadLabel(pm), ai.packageName);
                app.packageName = ai.packageName;
                app.activityName = ai.name;
                app.icon = ri.loadIcon(pm);
                if (app.icon == null) {
                    app.icon = pm.getDefaultActivityIcon();
                }
                result.pickerApps.add(app);
            }
        } catch (Exception e) {
            Log.w(TAG, "查询 ACTION_CREATE_SHORTCUT 失败", e);
        }

        final Collator collator = Collator.getInstance(Locale.CHINESE);
        Collections.sort(result.staticShortcuts, new Comparator<StaticShortcut>() {
            @Override
            public int compare(StaticShortcut a, StaticShortcut b) {
                int c = collator.compare(a.appLabel, b.appLabel);
                return c != 0 ? c : collator.compare(a.label, b.label);
            }
        });
        Collections.sort(result.pickerApps, new Comparator<ShortcutPickerApp>() {
            @Override
            public int compare(ShortcutPickerApp a, ShortcutPickerApp b) {
                return collator.compare(a.appLabel, b.appLabel);
            }
        });
        return result;
    }

    private static String safeLabel(CharSequence label, String fallback) {
        if (label == null) return fallback;
        String s = label.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private static void parseShortcutsXml(Resources res, XmlResourceParser parser,
                                          PackageManager pm, String packageName, String appLabel,
                                          Drawable appIcon, Set<String> seen,
                                          List<StaticShortcut> out)
            throws Exception {
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "shortcut".equals(parser.getName())) {
                String shortcutId = parser.getAttributeValue(ANDROID_NS, "shortcutId");
                boolean enabled = parser.getAttributeBooleanValue(ANDROID_NS, "enabled", true);
                int iconRes = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0);

                String label = readStringAttr(res, parser, "shortcutShortLabel");
                if (TextUtils.isEmpty(label)) {
                    label = readStringAttr(res, parser, "shortcutLongLabel");
                }

                List<Intent> intents = new ArrayList<>();
                Intent currentIntent = null;
                int depth = 1;
                while (depth > 0) {
                    event = parser.next();
                    if (event == XmlPullParser.END_DOCUMENT) {
                        break;
                    }
                    if (event == XmlPullParser.START_TAG) {
                        depth++;
                        String tag = parser.getName();
                        if ("intent".equals(tag)) {
                            currentIntent = new Intent();
                            String action = parser.getAttributeValue(ANDROID_NS, "action");
                            String targetPkg = parser.getAttributeValue(ANDROID_NS, "targetPackage");
                            String targetCls = parser.getAttributeValue(ANDROID_NS, "targetClass");
                            String data = parser.getAttributeValue(ANDROID_NS, "data");
                            if (!TextUtils.isEmpty(action)) {
                                currentIntent.setAction(action);
                            }
                            if (!TextUtils.isEmpty(targetPkg) && !TextUtils.isEmpty(targetCls)) {
                                currentIntent.setClassName(targetPkg, targetCls);
                            } else if (!TextUtils.isEmpty(targetPkg)) {
                                currentIntent.setPackage(targetPkg);
                            }
                            if (!TextUtils.isEmpty(data)) {
                                try {
                                    currentIntent.setData(Uri.parse(data));
                                } catch (Exception ignored) {
                                }
                            }
                        } else if ("extra".equals(tag) && currentIntent != null) {
                            String key = parser.getAttributeValue(ANDROID_NS, "name");
                            String value = parser.getAttributeValue(ANDROID_NS, "value");
                            if (!TextUtils.isEmpty(key)) {
                                currentIntent.putExtra(key, value == null ? "" : value);
                            }
                        }
                    } else if (event == XmlPullParser.END_TAG) {
                        depth--;
                        if ("intent".equals(parser.getName()) && currentIntent != null) {
                            intents.add(currentIntent);
                            currentIntent = null;
                        }
                    }
                }

                if (!enabled || intents.isEmpty()) {
                    continue;
                }

                String dedupeKey = packageName + "/" + (shortcutId == null ? "" : shortcutId);
                if (!seen.add(dedupeKey)) {
                    continue;
                }

                Drawable icon = appIcon;
                if (iconRes != 0) {
                    try {
                        Drawable d = res.getDrawable(iconRes, null);
                        if (d != null) icon = d;
                    } catch (Exception ignored) {
                    }
                }

                StaticShortcut sc = new StaticShortcut();
                sc.appLabel = appLabel;
                sc.packageName = packageName;
                sc.shortcutId = shortcutId == null ? "" : shortcutId;
                sc.label = TextUtils.isEmpty(label) ? appLabel : label;
                sc.icon = icon;
                sc.intent = intents.get(0);

                // 目标 Activity 未导出时，普通应用无法启动（LauncherApps.startShortcut 才行）
                ComponentName target = sc.intent.getComponent();
                if (target != null) {
                    try {
                        ActivityInfo targetInfo = pm.getActivityInfo(target, 0);
                        sc.launchable = targetInfo.exported;
                    } catch (Exception e) {
                        sc.launchable = false;
                    }
                }
                out.add(sc);
            }
            event = parser.next();
        }
    }

    private static String readStringAttr(Resources res, XmlResourceParser parser, String attr) {
        int resId = parser.getAttributeResourceValue(ANDROID_NS, attr, 0);
        if (resId != 0) {
            try {
                CharSequence cs = res.getText(resId);
                if (cs != null) return cs.toString();
            } catch (Exception ignored) {
            }
        }
        return parser.getAttributeValue(ANDROID_NS, attr);
    }
}
