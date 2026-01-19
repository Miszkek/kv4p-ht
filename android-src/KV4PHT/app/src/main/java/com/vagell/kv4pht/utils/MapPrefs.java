package com.vagell.kv4pht.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.vagell.kv4pht.R;

import java.util.Locale;

public final class MapPrefs {

    private static final String PREFS = "kv4pht_map_prefs";
    private static final String KEY_TRAIL = "trail_count";
    private static final String KEY_COLOR = "my_marker_color";
    private static final String KEY_WEATHER = "show_weather";

    private MapPrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getTrailCount(Context ctx) {
        return prefs(ctx).getInt(KEY_TRAIL, 10);
    }

    public static void setTrailCount(Context ctx, int v) {
        prefs(ctx).edit().putInt(KEY_TRAIL, v).apply();
    }

    public static String getMyMarkerColor(Context ctx) {
        return prefs(ctx).getString(KEY_COLOR, "Blue");
    }

    public static void setMyMarkerColor(Context ctx, String v) {
        prefs(ctx).edit().putString(KEY_COLOR, v).apply();
    }

    public static boolean getShowWeather(Context ctx) {
        return prefs(ctx).getBoolean(KEY_WEATHER, false);
    }

    public static void setShowWeather(Context ctx, boolean v) {
        prefs(ctx).edit().putBoolean(KEY_WEATHER, v).apply();
    }

    public static int indexOfString(String[] arr, String value) {
        if (arr == null || value == null) return 0;
        for (int i = 0; i < arr.length; i++) {
            if (value.equalsIgnoreCase(arr[i])) return i;
        }
        return 0;
    }

    public static int indexOfTrailCount(String[] arr, int value) {
        if (arr == null) return 0;
        for (int i = 0; i < arr.length; i++) {
            try {
                if (Integer.parseInt(arr[i]) == value) return i;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public static int resolveMarkerDrawable(String colorName) {
        if (colorName == null) return R.drawable.marker_dot_blue;
        String c = colorName.toLowerCase(Locale.ROOT);
        if (c.contains("green")) return R.drawable.marker_dot_green;
        if (c.contains("red")) return R.drawable.marker_dot_red;
        if (c.contains("orange")) return R.drawable.marker_dot_orange;
        if (c.contains("purple")) return R.drawable.marker_dot_purple;
        return R.drawable.marker_dot_blue;
    }
}
