package com.example.lori.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "vocab_quizzes")
public class VocabQuiz {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "topic_id")
    public int topicId;

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