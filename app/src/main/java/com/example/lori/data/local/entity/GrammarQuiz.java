package com.example.lori.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "grammar_quizzes")
public class GrammarQuiz {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "lesson_id")
    public int lessonId;

    public String question;

    @ColumnInfo(name = "option_a")
    public String optionA;

    @ColumnInfo(name = "option_b")
    public String optionB;

    @ColumnInfo(name = "option_c")
    public String optionC;

    @ColumnInfo(name = "option_d")
    public String optionD;

    @ColumnInfo(name = "correct_answer")
    public String correctAnswer;

    public String explanation;
}