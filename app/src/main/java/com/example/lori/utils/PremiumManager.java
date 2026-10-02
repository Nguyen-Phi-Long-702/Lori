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
}