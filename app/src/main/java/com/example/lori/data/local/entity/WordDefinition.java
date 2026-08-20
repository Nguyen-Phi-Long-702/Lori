package com.example.lori.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "word_definitions",
        indices = {
                @Index(value = {"word_id"}, name = "idx_word_definitions_word_id"),
                @Index(value = {"definition_id"}, name = "idx_word_definitions_definition_id")
        }
)
public class WordDefinition {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "word_id")
    public int wordId;

    @ColumnInfo(name = "definition_id")
    public int definitionId;

    public String example;
}