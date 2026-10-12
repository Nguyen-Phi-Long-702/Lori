package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.UserProgressLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class ProgressDaoTest {

    private AppDatabase db;
    private ProgressDao progressDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        //Dùng db inmemory để test độc lập với dữ liệu thật
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        progressDao = db.progressDao();
    }

    @After
    public void closeDb() throws IOException {
        //Đóng db sau khi test để giải phóng tài nguyên
        db.close();
    }

    @Test
    public void insertAndGetProgress() {
        UserProgressLocal progress = new UserProgressLocal();
        progress.itemType = "word";
        progress.itemId = 10;
        progress.status = "learned";
        progress.correctCount = 3;
        progress.incorrectCount = 1;
        progress.isSynced = false;

        progressDao.insertOrUpdate(progress);

        UserProgressLocal result = progressDao.getProgress("word", 10);
        //Đảm bảo tiến độ được lưu và truy vấn đúng theo loại và id
        assertEquals("learned", result.status);
        assertEquals(3, result.correctCount);
    }

    @Test
    public void reinsertWithSameIdReplacesRow() {
        UserProgressLocal progress = new UserProgressLocal();
        progress.itemType = "grammar";
        progress.itemId = 20;
        progress.status = "reviewing";
        progress.correctCount = 1;
        progress.incorrectCount = 0;
        progress.isSynced = false;
        progressDao.insertOrUpdate(progress);

        UserProgressLocal saved = progressDao.getProgress("grammar", 20);
        saved.status = "mastered";
        saved.correctCount = 5;
        progressDao.insertOrUpdate(saved);

        //Đảm bảo insert lại cùng id cập nhật bản ghi thay vì tạo bản ghi mới
        UserProgressLocal updated = progressDao.getProgress("grammar", 20);
        assertEquals("mastered", updated.status);
        assertEquals(5, updated.correctCount);
    }

    @Test
    public void deleteProgress() {
        UserProgressLocal progress = new UserProgressLocal();
        progress.itemType = "quiz";
        progress.itemId = 30;
        progress.status = "learned";
        progress.correctCount = 2;
        progress.incorrectCount = 0;
        progress.isSynced = false;
        progressDao.insertOrUpdate(progress);

        UserProgressLocal saved = progressDao.getProgress("quiz", 30);
        progressDao.delete(saved);

        UserProgressLocal result = progressDao.getProgress("quiz", 30);
        //Đảm bảo bản ghi tiến độ đã được xóa khỏi db
        assertEquals(null, result);
    }

    @Test
    public void markSyncedIfUnchanged_marksOnlyWhenTimestampMatches() {
        Date studiedAt = new Date(1_700_000_000_000L);
        UserProgressLocal progress = new UserProgressLocal();
        progress.itemType = "grammar_lesson";
        progress.itemId = 1;
        progress.status = "completed";
        progress.lastStudiedAt = studiedAt;
        progress.isSynced = false;
        progressDao.insertOrUpdate(progress);

        //Mốc thời gian khác (bản ghi vừa được học lại trong lúc đồng bộ) → không đánh dấu
        int skipped = progressDao.markSyncedIfUnchanged("grammar_lesson", 1, new Date(studiedAt.getTime() + 1000));
        assertEquals(0, skipped);
        assertFalse(progressDao.getProgress("grammar_lesson", 1).isSynced);
        assertEquals(1, progressDao.getUnsyncedProgress().size());

        //Đúng mốc → đánh dấu đã đồng bộ
        int marked = progressDao.markSyncedIfUnchanged("grammar_lesson", 1, studiedAt);
        assertEquals(1, marked);
        assertTrue(progressDao.getProgress("grammar_lesson", 1).isSynced);
        assertTrue(progressDao.getUnsyncedProgress().isEmpty());
    }

    @Test
    public void deleteAll_removesEveryProgressRow() {
        for (int id = 1; id <= 2; id++) {
            UserProgressLocal progress = new UserProgressLocal();
            progress.itemType = "vocab_quiz_topic";
            progress.itemId = id;
            progress.status = "completed";
            progress.isSynced = true;
            progressDao.insertOrUpdate(progress);
        }

        progressDao.deleteAll();

        assertEquals(null, progressDao.getProgress("vocab_quiz_topic", 1));
        assertEquals(null, progressDao.getProgress("vocab_quiz_topic", 2));
    }
}