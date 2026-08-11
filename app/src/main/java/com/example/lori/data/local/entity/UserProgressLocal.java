package com.example.lori.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "user_progress_local")
public class UserProgressLocal {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "item_type")
    public String itemType;

    @ColumnInfo(name = "item_id")
    public int itemId;

    public String status;

    @ColumnInfo(name = "correct_count")
    public int correctCount;

    @ColumnInfo(name = "incorrect_count")
    public int incorrectCount;

    @ColumnInfo(name = "last_studied_at")
    public Date lastStudiedAt;

    @ColumnInfo(name = "is_synced")
    public boolean isSynced;
}