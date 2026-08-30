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
    //Thêm tin nhắn mới và db
    @Insert
    void insertMessage(ChatMessage message);

    //Lấy tất cả tin nhắn từ db theo thứ tự tgian tăng dần
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    LiveData<List<ChatMessage>> getAllMessages();

    //Xóa toàn bộ lịch sử tin nhắn trong db
    @Query("DELETE FROM chat_messages")
    void clearAllMessages();

    //Xóa tin nhắn dựa trên id
    @Delete
    void deleteMessage(ChatMessage message);
    //Cập nhật tin nhắn dựa trên id
    @Update
    void updateMessage(ChatMessage message);
}