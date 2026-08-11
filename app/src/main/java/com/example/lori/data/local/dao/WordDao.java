package com.example.lori.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.Word;

import java.util.List;

@Dao
public interface WordDao {
    @Insert
    void insertAll(List<Word> words);

    @Query("SELECT * FROM words WHERE topic_id = :topicId")
    LiveData<List<Word>> getWordsByTopic(int topicId);

    @Query("SELECT * FROM words WHERE id = :wordId")
    Word getWordById(int wordId);

    @Update
    void update(Word word);

    @Delete
    void delete(Word word);
}