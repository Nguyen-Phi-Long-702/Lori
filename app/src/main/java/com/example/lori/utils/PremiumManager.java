package com.example.lori.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class PremiumManager {

    private static final String PREFS_NAME = "lori_prefs";
    private static final String KEY_IS_PREMIUM = "is_premium";

    private PremiumManager() {
    }

    public static boolean isPremium(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_PREMIUM, false);
    }

    //Ghi trạng thái Premium đã biết từ Backend (sau khi mua, khi SyncWorker làm mới, hoặc đặt false khi đăng xuất)
    //apply() an toàn khi gọi từ luồng nền. Đây chỉ là bản lưu để hiện giao diện; quyền thật do Backend kiểm tra (403 PREMIUM_REQUIRED)
    public static void setPremium(Context context, boolean premium) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_PREMIUM, premium)
                .apply();
    }
}