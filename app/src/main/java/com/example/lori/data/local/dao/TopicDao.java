package com.example.lori.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.Topic;

import java.util.List;

@Dao
public interface TopicDao {
    @Insert
    void insertAll(List<Topic> topics);

    @Query("SELECT * FROM topics")
    LiveData<List<Topic>> getAllTopics();

    @Query("SELECT * FROM topics WHERE id = :topicId")
    Topic getTopicById(int topicId);

    @Update
    void update(Topic topic);

    @Delete
    void delete(Topic topic);
}