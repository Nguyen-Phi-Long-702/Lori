package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "grammar_quizzes")
public class GrammarQuiz {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "lesson_id")
    public int lessonId;

    @NonNull
    public String question;
    @NonNull @ColumnInfo(name = "option_a") public String optionA;
    @NonNull @ColumnInfo(name = "option_b") public String optionB;
    @NonNull @ColumnInfo(name = "option_c") public String optionC;
    @NonNull @ColumnInfo(name = "option_d") public String optionD;
    @NonNull @ColumnInfo(name = "correct_answer") public String correctAnswer;

    public String explanation;
}