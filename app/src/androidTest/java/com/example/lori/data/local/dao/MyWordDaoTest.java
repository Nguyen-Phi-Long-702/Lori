package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.MyWord;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

@RunWith(AndroidJUnit4.class)
public class MyWordDaoTest {

    private AppDatabase db;
    private MyWordDao myWordDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        //Dùng db inmemory để test độc lập với dữ liệu thật
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        myWordDao = db.myWordDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    private static MyWord word(String text) {
        MyWord word = new MyWord();
        word.word = text;
        word.meaning = "nghĩa của " + text;
        word.savedAt = new Date(1000L);
        return word;
    }

    @Test
    public void insertAll_newWords_returnsRowIds() {
        long[] ids = myWordDao.insertAll(Arrays.asList(word("bus"), word("chair")));

        assertEquals(2, ids.length);
        assertNotEquals(-1L, ids[0]);
        assertNotEquals(-1L, ids[1]);
        assertEquals(2, myWordDao.count());
    }

    @Test
    public void insertAll_duplicateWord_isIgnored() {
        myWordDao.insertAll(Arrays.asList(word("bus")));

        long[] ids = myWordDao.insertAll(Arrays.asList(word("bus"), word("chair")));

        assertEquals(-1L, ids[0]);
        assertNotEquals(-1L, ids[1]);
        assertEquals(2, myWordDao.count());
    }
}