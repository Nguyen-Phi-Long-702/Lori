package com.example.lori.util;

import org.jsoup.Jsoup;

/**
 * Lam sach text nguoi dung nhap: bo toan bo the HTML/script (Jsoup), bo ky tu < >,
 * gop khoang trang. Dung cho text tu do nhu display name.
 */
public final class InputSanitizerUtil {

    private InputSanitizerUtil() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.parseBodyFragment(input).text()
                .replaceAll("[<>]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}