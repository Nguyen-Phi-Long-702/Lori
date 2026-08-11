package com.example.lori.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
@Entity(tableName = "vocab_quizzes")
public class VocabQuiz {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "topic_id")
    public int topicId;

    @NonNull public String question;
    @NonNull @ColumnInfo(name = "option_a") public String optionA;
    @NonNull @ColumnInfo(name = "option_b") public String optionB;
    @NonNull @ColumnInfo(name = "option_c") public String optionC;
    @NonNull @ColumnInfo(name = "option_d") public String optionD;
    @NonNull @ColumnInfo(name = "correct_answer") public String correctAnswer;

    public String explanation;
}