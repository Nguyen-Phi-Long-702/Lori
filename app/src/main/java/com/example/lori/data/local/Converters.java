package com.example.lori.data.local;

import androidx.room.TypeConverter;

import java.util.Date;

public class Converters {
    //Chuyển timestamp từ db thành date
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    //Chuyển date thành timestamp để lưu vào db
    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}