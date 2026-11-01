package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

//Một từ user lưu vào "Từ vựng của tôi" (hiện chỉ lưu từ màn Nhận diện ảnh). Chỉ lưu trên máy, không đồng bộ lên Backend
//word là duy nhất: lưu lại từ đã có thì bị bỏ qua
@Entity(tableName = "my_words", indices = {@Index(value = "word", unique = true)})
public class MyWord {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String word;

    //IPA lấy từ từ điển Lori; null nếu từ điển không có
    public String ipa;

    @NonNull
    public String meaning;

    //Câu ví dụ do AI tạo và bản dịch tiếng Việt; null nếu AI không tạo được
    public String example;

    @ColumnInfo(name = "example_vi")
    public String exampleVi;

    @NonNull
    @ColumnInfo(name = "saved_at")
    public Date savedAt;
}