package com.example.lori.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

//Chuyển đổi giữa Date (Room) và chuỗi ISO-8601 UTC (Backend). Không dùng java.time vì minSdk 24
public final class IsoDateUtils {

    //Gửi lên server: luôn UTC, có mili giây (Backend nhận Instant)
    private static final String OUTPUT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    //Phần ngày giờ đến giây; phần lẻ giây được tách ra xử lý riêng vì server có thể trả 3, 6 hoặc 9 chữ số
    private static final String INPUT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    private IsoDateUtils() {
    }

    //Ví dụ: Date(0) -> "1970-01-01T00:00:00.000Z"
    public static String format(Date date) {
        return newFormat(OUTPUT_PATTERN).format(date);
    }

    //Trả về null nếu chuỗi null hoặc không đúng dạng "...Z" (Backend luôn trả UTC kết thúc bằng Z)
    public static Date parse(String iso) {
        if (iso == null) {
            return null;
        }
        String text = iso.trim();
        if (!text.endsWith("Z")) {
            return null;
        }
        text = text.substring(0, text.length() - 1);

        long millis = 0;
        int dotIndex = text.indexOf('.');
        if (dotIndex >= 0) {
            String fraction = text.substring(dotIndex + 1);
            text = text.substring(0, dotIndex);
            if (!fraction.matches("\\d+")) {
                return null;
            }
            //Lấy đúng 3 chữ số đầu (mili giây); thiếu thì đệm 0: ".5" -> 500, ".123456" -> 123
            millis = Long.parseLong((fraction + "00").substring(0, 3));
        }

        SimpleDateFormat format = newFormat(INPUT_PATTERN);
        format.setLenient(false);
        try {
            Date seconds = format.parse(text);
            return seconds == null ? null : new Date(seconds.getTime() + millis);
        } catch (ParseException e) {
            return null;
        }
    }

    //SimpleDateFormat không an toàn đa luồng nên tạo mới mỗi lần gọi (SyncWorker chạy ở luồng nền)
    private static SimpleDateFormat newFormat(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }
}