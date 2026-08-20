package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "topic_words",
        indices = {
                @Index(value = {"topic_id"}, name = "idx_topic_words_topic_id")
        }
)
public class TopicWord {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "topic_id")
    public int topicId;

    @NonNull
    public String word;

    public String pronunciation;

    @NonNull
    public String meaning;

    @ColumnInfo(name = "audio_path")
    public String audioPath;

    @ColumnInfo(name = "image_url")
    public String imageUrl;
}