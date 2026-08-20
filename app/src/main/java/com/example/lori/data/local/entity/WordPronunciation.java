package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pronunciations",
        indices = {
                @Index(value = {"word_id"}, name = "idx_pronunciations_word_id"),
                @Index(value = {"word_id", "ipa", "region"}, unique = true, name = "idx_pronunciations_unique")
        }
)
public class WordPronunciation {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "word_id")
    public int wordId;

    @NonNull
    public String ipa;

    public String region;
}