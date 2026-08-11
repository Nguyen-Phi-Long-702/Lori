package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "grammar_examples")
public class GrammarExample {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "lesson_id")
    public int lessonId;

    @NonNull
    @ColumnInfo(name = "sentence_en") public String sentenceEn;
    @NonNull
    @ColumnInfo(name = "sentence_vi") public String sentenceVi;

    @ColumnInfo(name = "is_correct")
    public boolean isCorrect;

    public String explanation;
}