package com.example.lori.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "chat_messages")
public class ChatMessage {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String role;

    public String content;

    @ColumnInfo(name = "image_path")
    public String imagePath;

    public Date timestamp;

    @ColumnInfo(name = "is_error")
    public boolean isError;
}