package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "dictionary")
public class DictionaryEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String word;

    public String pronunciation;

    @ColumnInfo(name = "part_of_speech")
    public String partOfSpeech;

    @ColumnInfo(name = "definition_en")
    public String definitionEn;

    @NonNull
    @ColumnInfo(name = "definition_vi")
    public String definitionVi;

    public String synonyms;

    public String antonyms;

    public String examples;
}