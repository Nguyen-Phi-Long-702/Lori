package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "word_relations",
        indices = {
                @Index(value = {"word_id"}, name = "idx_word_relations_word_id"),
                @Index(value = {"word_id", "related_word", "relation_type"}, unique = true, name = "idx_word_relations_unique")
        }
)
public class WordRelation {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "word_id")
    public int wordId;

    @NonNull
    @ColumnInfo(name = "related_word")
    public String relatedWord;

    @NonNull
    @ColumnInfo(name = "relation_type")
    public String relationType;
}