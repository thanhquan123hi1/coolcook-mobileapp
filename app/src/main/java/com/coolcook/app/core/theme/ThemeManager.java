package com.coolcook.app.core.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";

    private static final String PREF_NAME = "coolcook_theme";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemeManager() {
    }

    public static void applySavedTheme(@NonNull Context context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(getStoredMode(context)));
    }

    public static void persistAndApply(@NonNull Context context, @Nullable String mode) {
        String safeMode = sanitize(mode);
        prefs(context).edit().putString(KEY_THEME_MODE, safeMode).apply();
        AppCompatDelegate.setDefaultNightMode(toNightMode(safeMode));
    }

    @NonNull
    public static String getStoredMode(@NonNull Context context) {
        return sanitize(prefs(context).getString(KEY_THEME_MODE, MODE_SYSTEM));
    }

    public static boolean isDarkActive(@NonNull Context context) {
        String mode = getStoredMode(context);
        if (MODE_DARK.equals(mode)) {
            return true;
        }
        if (MODE_LIGHT.equals(mode)) {
            return false;
        }
        int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_YES;
    }

    @NonNull
    public static String getModeLabel(@NonNull Context context) {
        String mode = getStoredMode(context);
        if (MODE_DARK.equals(mode)) {
            return "Tối";
        }
        if (MODE_LIGHT.equals(mode)) {
            return "Sáng";
        }
        return "Theo hệ thống";
    }

    private static int toNightMode(@NonNull String mode) {
        if (MODE_DARK.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (MODE_LIGHT.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    @NonNull
    private static String sanitize(@Nullable String mode) {
        if (MODE_DARK.equals(mode)) {
            return MODE_DARK;
        }
        if (MODE_LIGHT.equals(mode)) {
            return MODE_LIGHT;
        }
        return MODE_SYSTEM;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
