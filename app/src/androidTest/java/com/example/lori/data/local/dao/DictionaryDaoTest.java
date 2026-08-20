package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.Definition;
import com.example.lori.data.local.entity.DictionaryDefinition;
import com.example.lori.data.local.entity.DictionaryWord;
import com.example.lori.data.local.entity.WordDefinition;
import com.example.lori.data.local.entity.WordPronunciation;
import com.example.lori.data.local.entity.WordRelation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
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

    private DictionaryWord newWord(int id, String word) {
        DictionaryWord w = new DictionaryWord();
        w.id = id;
        w.word = word;
        w.langCode = "en";
        return w;
    }

    @Test
    public void getExactWordReturnsMatch() {
        dictionaryDao.insertWords(Collections.singletonList(newWord(1, "hello")));

        DictionaryWord result = dictionaryDao.getExactWord("hello");

        assertNotNull(result);
        assertEquals("hello", result.word);
    }

    @Test
    public void searchByPrefixReturnsMatches() {
        dictionaryDao.insertWords(Arrays.asList(
                newWord(1, "happy"),
                newWord(2, "happen"),
                newWord(3, "sad")
        ));

        List<DictionaryWord> result = dictionaryDao.searchByPrefix("hap");

        assertEquals(2, result.size());
    }

    @Test
    public void getExactWordReturnsNullWhenMissing() {
        assertNull(dictionaryDao.getExactWord("khongtontai"));
    }

    @Test
    public void getDefinitionsJoinsWordDefinitionsAndDefinitions() {
        dictionaryDao.insertWords(Collections.singletonList(newWord(1, "apple")));

        Definition def = new Definition();
        def.id = 100;
        def.definition = "Quả táo";
        def.pos = "N";
        dictionaryDao.insertDefinitions(Collections.singletonList(def));

        WordDefinition wd = new WordDefinition();
        wd.id = 200;
        wd.wordId = 1;
        wd.definitionId = 100;
        wd.example = "I eat an apple";
        dictionaryDao.insertWordDefinitions(Collections.singletonList(wd));

        List<DictionaryDefinition> results = dictionaryDao.getDefinitions(1);

        assertEquals(1, results.size());
        assertEquals("Quả táo", results.get(0).definition);
        assertEquals("I eat an apple", results.get(0).example);
    }

    @Test
    public void getSynonymsAndAntonymsFilterByRelationType() {
        dictionaryDao.insertWords(Collections.singletonList(newWord(1, "big")));

        WordRelation synonym = new WordRelation();
        synonym.id = 1;
        synonym.wordId = 1;
        synonym.relatedWord = "large";
        synonym.relationType = "s";

        WordRelation antonym = new WordRelation();
        antonym.id = 2;
        antonym.wordId = 1;
        antonym.relatedWord = "small";
        antonym.relationType = "a";

        dictionaryDao.insertWordRelations(Arrays.asList(synonym, antonym));

        assertEquals(Collections.singletonList("large"), dictionaryDao.getSynonyms(1));
        assertEquals(Collections.singletonList("small"), dictionaryDao.getAntonyms(1));
    }

    @Test
    public void getPronunciationsReturnsAllRegions() {
        dictionaryDao.insertWords(Collections.singletonList(newWord(1, "apple")));

        WordPronunciation us = new WordPronunciation();
        us.id = 1; us.wordId = 1; us.ipa = "/ˈæpəl/"; us.region = "US";

        WordPronunciation uk = new WordPronunciation();
        uk.id = 2; uk.wordId = 1; uk.ipa = "/ˈap(ə)l/"; uk.region = "UK";

        dictionaryDao.insertPronunciations(Arrays.asList(us, uk));

        List<WordPronunciation> result = dictionaryDao.getPronunciations(1);
        assertEquals(2, result.size());
    }
}