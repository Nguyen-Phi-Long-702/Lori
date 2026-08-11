package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "words")
public class Word {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "topic_id")
    public int topicId;

    @NonNull
    public String word;

    public String pronunciation;

    @NonNull
    @ColumnInfo(name = "part_of_speech")
    public String partOfSpeech;

    @NonNull
    @ColumnInfo(name = "meaning_vi")
    public String meaningVi;

    @ColumnInfo(name = "example_en")
    public String exampleEn;

    @ColumnInfo(name = "example_vi")
    public String exampleVi;

    @ColumnInfo(name = "audio_path")
    public String audioPath;

    @ColumnInfo(name = "image_url")
    public String imageUrl;

    public String synonyms;

    public String antonyms;
}