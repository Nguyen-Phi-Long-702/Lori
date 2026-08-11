package com.example.lori.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.GrammarQuiz;
import com.example.lori.data.local.entity.VocabQuiz;

import java.util.List;

@Dao
public interface QuizDao {
    @Insert
    void insertVocabQuizzes(List<VocabQuiz> quizzes);

    @Insert
    void insertGrammarQuizzes(List<GrammarQuiz> quizzes);

    @Query("SELECT * FROM vocab_quizzes WHERE topic_id = :topicId")
    List<VocabQuiz> getVocabQuizByTopic(int topicId);

    @Query("SELECT * FROM grammar_quizzes WHERE lesson_id = :lessonId")
    List<GrammarQuiz> getGrammarQuizByLesson(int lessonId);

    @Update
    void updateVocabQuiz(VocabQuiz quiz);

    @Update
    void updateGrammarQuiz(GrammarQuiz quiz);

    @Delete
    void deleteVocabQuiz(VocabQuiz quiz);

    @Delete
    void deleteGrammarQuiz(GrammarQuiz quiz);
}