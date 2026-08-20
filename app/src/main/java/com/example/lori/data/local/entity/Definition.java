package com.example.lori.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "definitions")
public class Definition {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String definition;

    public String pos;
}