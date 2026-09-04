package com.example.lori.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

//Đọc và lưu tuỳ chọn giao diện tối, dùng ở các activity khởi động để không mất lựa chọn sau khi thoát hẳn app
public final class ThemeHelper {

    private static final String PREFS_NAME = "lori_prefs";
    private static final String KEY_DARK_MODE_ENABLED = "dark_mode_enabled";

    private ThemeHelper() {
    }

    //Đọc tuỳ chọn giao diện tối đã lưu và áp dụng ngay; bắt buộc gọi trước super.onCreate() của activity
    public static void applySavedNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean darkModeEnabled = prefs.getBoolean(KEY_DARK_MODE_ENABLED, false);
        AppCompatDelegate.setDefaultNightMode(
                darkModeEnabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    //Lưu lại tuỳ chọn giao diện tối để áp dụng lại ở lần mở app tiếp theo
    public static void saveDarkModePreference(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply();
    }
}