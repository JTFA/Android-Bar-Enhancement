package com.jtfa.barenhancement;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * 方向感知的设置读写工具。
 *
 * 竖屏值始终保存在原 key 中；横屏独立值保存在 key + "_land"，
 * 每个可同步项的同步标记保存在 key + "_land_sync"（默认 true）。
 * 勾选同步时横屏直接读竖屏值，取消同步后才读横屏独立值。
 */
public final class OrientationSettings {

    public static final String LAND_SUFFIX = "_land";
    public static final String SYNC_SUFFIX = "_land_sync";

    private OrientationSettings() {
    }

    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isSynced(SharedPreferences prefs, String key) {
        return prefs.getBoolean(key + SYNC_SUFFIX, true);
    }

    public static int getInt(Context context, SharedPreferences prefs, String key, int defValue) {
        if (isLandscape(context) && !isSynced(prefs, key)) {
            return prefs.getInt(key + LAND_SUFFIX, defValue);
        }
        return prefs.getInt(key, defValue);
    }

    public static String getString(Context context, SharedPreferences prefs, String key,
                                   String defValue) {
        if (isLandscape(context) && !isSynced(prefs, key)) {
            return prefs.getString(key + LAND_SUFFIX, defValue);
        }
        return prefs.getString(key, defValue);
    }

    public static boolean getBoolean(Context context, SharedPreferences prefs, String key,
                                     boolean defValue) {
        if (isLandscape(context) && !isSynced(prefs, key)) {
            return prefs.getBoolean(key + LAND_SUFFIX, defValue);
        }
        return prefs.getBoolean(key, defValue);
    }

    /** 取消同步：保存横屏独立值，并标记不同步。 */
    public static void putLandscapeInt(SharedPreferences.Editor editor, String key, int value) {
        editor.putInt(key + LAND_SUFFIX, value);
        editor.putBoolean(key + SYNC_SUFFIX, false);
    }

    public static void putLandscapeString(SharedPreferences.Editor editor, String key, String value) {
        editor.putString(key + LAND_SUFFIX, value);
        editor.putBoolean(key + SYNC_SUFFIX, false);
    }

    public static void putLandscapeBoolean(SharedPreferences.Editor editor, String key, boolean value) {
        editor.putBoolean(key + LAND_SUFFIX, value);
        editor.putBoolean(key + SYNC_SUFFIX, false);
    }

    /** 勾选同步：删除横屏独立值，让横屏始终跟随竖屏。 */
    public static void markSynced(SharedPreferences.Editor editor, String key) {
        editor.remove(key + LAND_SUFFIX);
        editor.putBoolean(key + SYNC_SUFFIX, true);
    }
}
