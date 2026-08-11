package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.DictionaryEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DictionaryDaoTest {

    private AppDatabase db;
    private DictionaryDao dictionaryDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dictionaryDao = db.dictionaryDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insertAndGetExactWord() {
        DictionaryEntry entry = new DictionaryEntry();
        entry.word = "hello";
        entry.definitionVi = "xin chào";

        dictionaryDao.insertAll(Collections.singletonList(entry));

        DictionaryEntry result = dictionaryDao.getExactWord("hello");
        assertEquals("xin chào", result.definitionVi);
    }

    @Test
    public void searchByPrefixReturnsMatches() {
        DictionaryEntry e1 = new DictionaryEntry();
        e1.word = "happy";
        e1.definitionVi = "vui vẻ";

        DictionaryEntry e2 = new DictionaryEntry();
        e2.word = "happen";
        e2.definitionVi = "xảy ra";

        DictionaryEntry e3 = new DictionaryEntry();
        e3.word = "sad";
        e3.definitionVi = "buồn";

        dictionaryDao.insertAll(java.util.Arrays.asList(e1, e2, e3));

        List<DictionaryEntry> result = dictionaryDao.searchByPrefix("hap");
        assertEquals(2, result.size());
    }

    @Test
    public void updateEntry() {
        DictionaryEntry entry = new DictionaryEntry();
        entry.word = "book";
        entry.definitionVi = "cuốn sách";
        dictionaryDao.insertAll(Collections.singletonList(entry));

        DictionaryEntry saved = dictionaryDao.getExactWord("book");
        saved.definitionVi = "quyển sách";
        dictionaryDao.update(saved);

        DictionaryEntry updated = dictionaryDao.getExactWord("book");
        assertEquals("quyển sách", updated.definitionVi);
    }

    @Test
    public void deleteEntry() {
        DictionaryEntry entry = new DictionaryEntry();
        entry.word = "pen";
        entry.definitionVi = "cây bút";
        dictionaryDao.insertAll(Collections.singletonList(entry));

        DictionaryEntry saved = dictionaryDao.getExactWord("pen");
        dictionaryDao.delete(saved);

        assertNull(dictionaryDao.getExactWord("pen"));
    }
}