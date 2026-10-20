package com.example.lori.utils;

import java.text.NumberFormat;
import java.util.Locale;

//Định dạng số EXP để hiển thị, có dấu chấm ngăn cách hàng nghìn kiểu Việt Nam (vd 2.480)
public final class ExpFormatter {

    private static final Locale VIETNAM = Locale.forLanguageTag("vi-VN");

    private ExpFormatter() {
    }

    public static String format(long exp) {
        return NumberFormat.getIntegerInstance(VIETNAM).format(exp);
    }
}