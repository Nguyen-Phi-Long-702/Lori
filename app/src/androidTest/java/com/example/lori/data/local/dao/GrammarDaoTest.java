package com.example.lori.data.local.dao;

import static com.example.lori.data.local.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        //Dùng db inmemory để test độc lập với dữ liệu thật
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        grammarDao = db.grammarDao();
    }

    @After
    public void closeDb() throws IOException {
        //Đóng db sau khi test để giải phóng tài nguyên
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
        //Đảm bảo bài học được lưu và truy vấn đúng theo id
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
        //Đảm bảo danh sách bài học chứa dữ liệu vừa thêm
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
        //Đảm bảo ví dụ được liên kết đúng với bài học
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
        //Kiểm tra dữ liệu bài học được cập nhật đúng
        assertEquals("Tóm tắt bài học", updated.summary);

        grammarDao.deleteLesson(updated);
        //Room trả về null khi truy vấn một bản ghi đã bị xóa
        assertNull(grammarDao.getLessonById(1));
    }

    @Test
    public void updateExample_changesTheSentence() throws InterruptedException {
        insertExample(1, "I go to school.");

        GrammarExample saved = getOrAwaitValue(grammarDao.getExamplesByLesson(1)).get(0);
        saved.sentenceEn = "I went to school.";
        grammarDao.updateExample(saved);

        //Đảm bảo câu ví dụ được cập nhật đúng trong database
        GrammarExample updated = getOrAwaitValue(grammarDao.getExamplesByLesson(1)).get(0);
        assertEquals("I went to school.", updated.sentenceEn);
    }

    @Test
    public void deleteExample_removesOnlyThatExample() throws InterruptedException {
        insertExample(1, "First sentence.");
        insertExample(1, "Second sentence.");

        List<GrammarExample> all = getOrAwaitValue(grammarDao.getExamplesByLesson(1));
        GrammarExample first = "First sentence.".equals(all.get(0).sentenceEn) ? all.get(0) : all.get(1);
        grammarDao.deleteExample(first);

        //Chỉ câu bị xóa biến mất, câu còn lại vẫn nằm trong bài học
        List<GrammarExample> remaining = getOrAwaitValue(grammarDao.getExamplesByLesson(1));
        assertEquals(1, remaining.size());
        assertEquals("Second sentence.", remaining.get(0).sentenceEn);
    }

    private void insertExample(int lessonId, String sentenceEn) {
        GrammarExample example = new GrammarExample();
        example.lessonId = lessonId;
        example.sentenceEn = sentenceEn;
        example.sentenceVi = "Câu ví dụ";
        example.isCorrect = true;
        grammarDao.insertExamples(Collections.singletonList(example));
    }
}