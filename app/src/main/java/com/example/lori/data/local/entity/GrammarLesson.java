package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "grammar_lessons")
public class GrammarLesson {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String title;

    @NonNull
    @ColumnInfo(name = "title_vi")
    public String titleVi;

    @NonNull
    public String level;

    @NonNull
    @ColumnInfo(name = "content_html")
    public String contentHtml;

    public String summary;

    @ColumnInfo(name = "order_index")
    public int orderIndex;
}