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
    @Insert
    void insertLessons(List<GrammarLesson> lessons);

    @Insert
    void insertExamples(List<GrammarExample> examples);

    @Query("SELECT * FROM grammar_lessons ORDER BY order_index ASC")
    LiveData<List<GrammarLesson>> getAllLessons();

    @Query("SELECT * FROM grammar_lessons WHERE id = :lessonId")
    GrammarLesson getLessonById(int lessonId);

    @Query("SELECT * FROM grammar_examples WHERE lesson_id = :lessonId")
    LiveData<List<GrammarExample>> getExamplesByLesson(int lessonId);

    @Update
    void updateLesson(GrammarLesson lesson);

    @Delete
    void deleteLesson(GrammarLesson lesson);
}