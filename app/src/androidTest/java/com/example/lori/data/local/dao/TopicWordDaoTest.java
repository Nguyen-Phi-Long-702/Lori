package com.example.lori.data.local.dao;

import static com.example.lori.data.local.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.TopicWord;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TopicWordDaoTest {

    private AppDatabase db;
    private TopicWordDao topicWordDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        //Dùng db inmemory để test độc lập với dữ liệu thật
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        topicWordDao = db.topicWordDao();
    }

    @After
    public void closeDb() throws IOException {
        //Đóng db sau khi test để giải phóng tài nguyên
        db.close();
    }

    @Test
    public void insertAndGetWordById() {
        TopicWord word = new TopicWord();
        word.topicId = 1;
        word.word = "apple";
        word.meaning = "quả táo";

        topicWordDao.insertAll(Collections.singletonList(word));

        //Kiểm tra từ được lưu và truy vấn đúng theo id
        TopicWord result = topicWordDao.getWordById(1);
        assertEquals("apple", result.word);
        assertEquals("quả táo", result.meaning);
    }

    @Test
    public void getWordsByTopicReturnsInsertedWord() throws InterruptedException {
        TopicWord word = new TopicWord();
        word.topicId = 5;
        word.word = "banana";
        word.meaning = "quả chuối";
        topicWordDao.insertAll(Collections.singletonList(word));

        //Kiểm tra danh sách từ được trả về đúng theo topic
        List<TopicWord> result = getOrAwaitValue(topicWordDao.getWordsByTopic(5));

        assertEquals(1, result.size());
        assertEquals("banana", result.get(0).word);
    }

    @Test
    public void updateWord() {
        TopicWord word = new TopicWord();
        word.topicId = 1;
        word.word = "cat";
        word.meaning = "con mèo";
        topicWordDao.insertAll(Collections.singletonList(word));

        TopicWord saved = topicWordDao.getWordById(1);
        saved.meaning = "con mèo con";
        topicWordDao.update(saved);

        //Đảm bảo từ cập nhật trong database
        TopicWord updated = topicWordDao.getWordById(1);
        assertEquals("con mèo con", updated.meaning);
    }

    @Test
    public void deleteWord() {
        TopicWord word = new TopicWord();
        word.topicId = 1;
        word.word = "dog";
        word.meaning = "con chó";
        topicWordDao.insertAll(Collections.singletonList(word));

        TopicWord saved = topicWordDao.getWordById(1);
        topicWordDao.delete(saved);

        //Đảm bảo từ không còn tồn tại sau khi xóa
        assertNull(topicWordDao.getWordById(1));
    }
}