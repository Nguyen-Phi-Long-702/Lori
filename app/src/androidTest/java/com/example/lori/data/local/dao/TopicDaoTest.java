package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;

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
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class TopicDaoTest {

    private AppDatabase db;
    private TopicDao topicDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        topicDao = db.topicDao();
    }

    @After
    public void closeDb() throws IOException {
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

        Topic afterDelete = topicDao.getTopicById(1);
        assertEquals(null, afterDelete);
    }
}