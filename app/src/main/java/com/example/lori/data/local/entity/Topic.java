package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "topics")
public class Topic {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String name;

    @NonNull
    @ColumnInfo(name = "name_vi")
    public String nameVi;

    @ColumnInfo(name = "icon_url")
    public String iconUrl;

    @NonNull
    public String level;

    @ColumnInfo(name = "word_count")
    public int wordCount;
}