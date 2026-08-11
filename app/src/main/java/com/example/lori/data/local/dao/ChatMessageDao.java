package com.example.lori.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.ChatMessage;

import java.util.List;

@Dao
public interface ChatMessageDao {
    @Insert
    void insertMessage(ChatMessage message);

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    LiveData<List<ChatMessage>> getAllMessages();

    @Query("DELETE FROM chat_messages")
    void clearAllMessages();

    @Delete
    void deleteMessage(ChatMessage message);
    @Update
    void updateMessage(ChatMessage message);
}