package com.example.lori.data.local.dao;

import static com.example.lori.data.local.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarExample;
import com.example.lori.data.local.entity.GrammarLesson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class GrammarDaoTest {

    private AppDatabase db;
    private GrammarDao grammarDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        grammarDao = db.grammarDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insertAndGetLessonById() {
        GrammarLesson lesson = new GrammarLesson();
        lesson.title = "Present Simple";
        lesson.titleVi = "Thì hiện tại đơn";
        lesson.level = "beginner";
        lesson.contentHtml = "<p>Content</p>";
        lesson.orderIndex = 1;

        grammarDao.insertLessons(Collections.singletonList(lesson));

        GrammarLesson result = grammarDao.getLessonById(1);
        assertEquals("Present Simple", result.title);
    }

    @Test
    public void getAllLessonsReturnsInsertedLesson() throws InterruptedException {
        GrammarLesson lesson = new GrammarLesson();
        lesson.title = "Past Simple";
        lesson.titleVi = "Thì quá khứ đơn";
        lesson.level = "beginner";
        lesson.contentHtml = "<p>Content</p>";
        lesson.orderIndex = 2;
        grammarDao.insertLessons(Collections.singletonList(lesson));

        List<GrammarLesson> result = getOrAwaitValue(grammarDao.getAllLessons());
        assertEquals(1, result.size());
    }

    @Test
    public void insertAndGetExamplesByLesson() throws InterruptedException {
        GrammarLesson lesson = new GrammarLesson();
        lesson.title = "Future Simple";
        lesson.titleVi = "Thì tương lai đơn";
        lesson.level = "beginner";
        lesson.contentHtml = "<p>Content</p>";
        lesson.orderIndex = 3;
        grammarDao.insertLessons(Collections.singletonList(lesson));

        GrammarExample example = new GrammarExample();
        example.lessonId = 1;
        example.sentenceEn = "I will go.";
        example.sentenceVi = "Tôi sẽ đi.";
        example.isCorrect = true;
        grammarDao.insertExamples(Collections.singletonList(example));

        List<GrammarExample> result = getOrAwaitValue(grammarDao.getExamplesByLesson(1));
        assertEquals(1, result.size());
        assertEquals("I will go.", result.get(0).sentenceEn);
    }

    @Test
    public void updateAndDeleteLesson() {
        GrammarLesson lesson = new GrammarLesson();
        lesson.title = "Present Continuous";
        lesson.titleVi = "Thì hiện tại tiếp diễn";
        lesson.level = "intermediate";
        lesson.contentHtml = "<p>Content</p>";
        lesson.orderIndex = 4;
        grammarDao.insertLessons(Collections.singletonList(lesson));

        GrammarLesson saved = grammarDao.getLessonById(1);
        saved.summary = "Tóm tắt bài học";
        grammarDao.updateLesson(saved);

        GrammarLesson updated = grammarDao.getLessonById(1);
        assertEquals("Tóm tắt bài học", updated.summary);

        grammarDao.deleteLesson(updated);
        // Sau khi xóa, getLessonById(1) không còn dòng nào (Room trả về null với query đơn)
    }
}