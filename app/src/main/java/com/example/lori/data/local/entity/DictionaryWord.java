package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "words",
        indices = {
                @Index(value = {"word", "lang_code"}, unique = true, name = "idx_words_unique")
        }
)
public class DictionaryWord {

    @PrimaryKey
    public int id;

    @NonNull
    public String word;

    @NonNull
    @ColumnInfo(name = "lang_code", defaultValue = "'vi'")
    public String langCode;
}