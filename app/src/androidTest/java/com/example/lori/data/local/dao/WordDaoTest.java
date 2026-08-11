package com.example.lori.data.local.dao;

import static com.example.lori.data.local.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.Word;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WordDaoTest {

    private AppDatabase db;
    private WordDao wordDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        wordDao = db.wordDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insertAndGetWordById() {
        Word word = new Word();
        word.topicId = 1;
        word.word = "apple";
        word.partOfSpeech = "noun";
        word.meaningVi = "quả táo";

        wordDao.insertAll(Collections.singletonList(word));

        Word result = wordDao.getWordById(1);
        assertEquals("apple", result.word);
        assertEquals("quả táo", result.meaningVi);
    }

    @Test
    public void getWordsByTopicReturnsInsertedWord() throws InterruptedException {
        Word word = new Word();
        word.topicId = 5;
        word.word = "banana";
        word.partOfSpeech = "noun";
        word.meaningVi = "quả chuối";
        wordDao.insertAll(Collections.singletonList(word));

        List<Word> result = getOrAwaitValue(wordDao.getWordsByTopic(5));

        assertEquals(1, result.size());
        assertEquals("banana", result.get(0).word);
    }

    @Test
    public void updateWord() {
        Word word = new Word();
        word.topicId = 1;
        word.word = "cat";
        word.partOfSpeech = "noun";
        word.meaningVi = "con mèo";
        wordDao.insertAll(Collections.singletonList(word));

        Word saved = wordDao.getWordById(1);
        saved.meaningVi = "con mèo con";
        wordDao.update(saved);

        Word updated = wordDao.getWordById(1);
        assertEquals("con mèo con", updated.meaningVi);
    }

    @Test
    public void deleteWord() {
        Word word = new Word();
        word.topicId = 1;
        word.word = "dog";
        word.partOfSpeech = "noun";
        word.meaningVi = "con chó";
        wordDao.insertAll(Collections.singletonList(word));

        Word saved = wordDao.getWordById(1);
        wordDao.delete(saved);

        assertNull(wordDao.getWordById(1));
    }
}