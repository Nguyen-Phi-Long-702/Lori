package com.example.lori.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.TopicWord;

import java.util.List;

@Dao
public interface TopicWordDao {
    //Lưu danh sách từ vựng vào db
    @Insert
    void insertAll(List<TopicWord> words);

    //Lấy danh sách từ vựng thuộc một chủ đề
    @Query("SELECT * FROM topic_words WHERE topic_id = :topicId")
    LiveData<List<TopicWord>> getWordsByTopic(int topicId);

    //Lấy thông tin một từ vựng theo id
    @Query("SELECT * FROM topic_words WHERE id = :wordId")
    TopicWord getWordById(int wordId);

    //Cập nhật thông tin từ vựng
    @Update
    void update(TopicWord word);

    //Xóa một từ vựng khỏi db
    @Delete
    void delete(TopicWord word);
}