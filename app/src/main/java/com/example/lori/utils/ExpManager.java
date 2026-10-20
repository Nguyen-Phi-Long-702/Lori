package com.example.lori.utils;

import android.content.Context;
import android.content.SharedPreferences;

//Lưu tổng EXP lần cuối người dùng thấy trên màn Home, để biết lần sau có EXP mới (hiện "+XX EXP")
public final class ExpManager {

    //Chưa có mốc: lần đầu mở Home sau khi đăng nhập
    public static final long NO_BASELINE = -1;

    private static final String PREFS_NAME = "lori_prefs";
    private static final String KEY_LAST_SEEN_EXP = "last_seen_exp";

    private ExpManager() {
    }

    public static long getLastSeenExp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_SEEN_EXP, NO_BASELINE);
    }

    public static void setLastSeenExp(Context context, long exp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SEEN_EXP, exp)
                .apply();
    }

    //Gọi khi đăng nhập mới (có thể là tài khoản khác) để không hiện nhầm "+XX EXP" bằng mốc của phiên trước
    public static void clear(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_SEEN_EXP)
                .apply();
    }
}