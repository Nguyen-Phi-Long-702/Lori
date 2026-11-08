package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;
import static com.example.lori.data.local.LiveDataTestUtil.getOrAwaitValue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.Topic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TopicDaoTest {

    private AppDatabase db;
    private TopicDao topicDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        //Dùng db inmemory để test độc lập với dữ liệu thật
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        topicDao = db.topicDao();
    }

    @After
    public void closeDb() throws IOException {
        //Đóng db sau khi test để giải phóng tài nguyên
        db.close();
    }

    @Test
    public void insertAndGetTopicById() {
        Topic topic = new Topic();
        topic.name = "Animals";
        topic.nameVi = "Động vật";
        topic.level = "beginner";
        topic.wordCount = 100;

        topicDao.insertAll(Collections.singletonList(topic));

        //Kiểm tra topic được lưu và truy vấn đúng theo id
        Topic result = topicDao.getTopicById(1);
        assertEquals("Animals", result.name);
        assertEquals("beginner", result.level);
    }

    @Test
    public void updateTopic() {
        Topic topic = new Topic();
        topic.name = "Food";
        topic.nameVi = "Đồ ăn";
        topic.level = "beginner";
        topic.wordCount = 50;
        topicDao.insertAll(Collections.singletonList(topic));

        Topic saved = topicDao.getTopicById(1);
        saved.wordCount = 80;
        topicDao.update(saved);

        //Đảm bảo topic cập nhật đúng trong database
        Topic updated = topicDao.getTopicById(1);
        assertEquals(80, updated.wordCount);
    }

    @Test
    public void deleteTopic() {
        Topic topic = new Topic();
        topic.name = "Travel";
        topic.nameVi = "Du lịch";
        topic.level = "intermediate";
        topic.wordCount = 60;
        topicDao.insertAll(Collections.singletonList(topic));

        Topic saved = topicDao.getTopicById(1);
        topicDao.delete(saved);

        //Đảm bảo topic không còn tồn tại sau khi xóa
        Topic afterDelete = topicDao.getTopicById(1);
        assertEquals(null, afterDelete);
    }

    @Test
    public void getAllTopics_returnsEveryInsertedTopic() throws InterruptedException {
        topicDao.insertAll(Arrays.asList(newTopic("Animals", "Động vật"), newTopic("Food", "Đồ ăn")));

        List<Topic> result = getOrAwaitValue(topicDao.getAllTopics());

        //Danh sách phải chứa đủ cả hai chủ đề vừa thêm
        assertEquals(2, result.size());
    }

    private Topic newTopic(String name, String nameVi) {
        Topic topic = new Topic();
        topic.name = name;
        topic.nameVi = nameVi;
        topic.level = "beginner";
        topic.wordCount = 10;
        return topic;
    }
}