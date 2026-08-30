package com.example.lori.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.GrammarExample;
import com.example.lori.data.local.entity.GrammarLesson;

import java.util.List;

@Dao
public interface GrammarDao {
    //Lưu danh sách ngữ pháp vào database
    @Insert
    void insertLessons(List<GrammarLesson> lessons);

    //Lưu các câu ví dụ của bài học
    @Insert
    void insertExamples(List<GrammarExample> examples);

    //Lấy danh sách bài học theo thứ tự hiển thị
    @Query("SELECT * FROM grammar_lessons ORDER BY order_index ASC")
    LiveData<List<GrammarLesson>> getAllLessons();

    //Lấy thông tin bài học theo id
    @Query("SELECT * FROM grammar_lessons WHERE id = :lessonId")
    GrammarLesson getLessonById(int lessonId);

    //Lấy các câu ví dụ thuộc bài học
    @Query("SELECT * FROM grammar_examples WHERE lesson_id = :lessonId")
    LiveData<List<GrammarExample>> getExamplesByLesson(int lessonId);

    //Cập nhật thông tin bài học
    @Update
    void updateLesson(GrammarLesson lesson);

    //Xóa một bài học khỏi db
    @Delete
    void deleteLesson(GrammarLesson lesson);

    //Cập nhật thông tin câu ví dụ
    @Update
    void updateExample(GrammarExample example);

    //Xóa một câu ví dụ khỏi db
    @Delete
    void deleteExample(GrammarExample example);
}