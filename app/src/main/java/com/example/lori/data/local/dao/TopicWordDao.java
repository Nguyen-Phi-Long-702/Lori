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
    @Insert
    void insertAll(List<TopicWord> words);

    @Query("SELECT * FROM topic_words WHERE topic_id = :topicId")
    LiveData<List<TopicWord>> getWordsByTopic(int topicId);

    @Query("SELECT * FROM topic_words WHERE id = :wordId")
    TopicWord getWordById(int wordId);

    @Update
    void update(TopicWord word);

    @Delete
    void delete(TopicWord word);
}