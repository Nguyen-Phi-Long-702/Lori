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
    //Lưu danh sách topic vào db
    @Insert
    void insertAll(List<Topic> topics);

    //Lấy danh sách tất cả topic
    @Query("SELECT * FROM topics")
    LiveData<List<Topic>> getAllTopics();

    //Lấy thông tin topic theo id
    @Query("SELECT * FROM topics WHERE id = :topicId")
    Topic getTopicById(int topicId);

    //Cập nhật thông tin topic
    @Update
    void update(Topic topic);

    //Xóa topic khỏi db
    @Delete
    void delete(Topic topic);
}