package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;

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
}