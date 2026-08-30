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
    //Lưu danh sách câu hỏi từ vựng
    @Insert
    void insertVocabQuizzes(List<VocabQuiz> quizzes);

    //Lưu danh sách câu hỏi ngữ pháp
    @Insert
    void insertGrammarQuizzes(List<GrammarQuiz> quizzes);

    //Lấy các câu hỏi từ vựng theo chủ đề
    @Query("SELECT * FROM vocab_quizzes WHERE topic_id = :topicId")
    List<VocabQuiz> getVocabQuizByTopic(int topicId);

    //Lấy các câu hỏi ngữ pháp theo bài học
    @Query("SELECT * FROM grammar_quizzes WHERE lesson_id = :lessonId")
    List<GrammarQuiz> getGrammarQuizByLesson(int lessonId);

    //Cập nhật một câu hỏi từ vựng
    @Update
    void updateVocabQuiz(VocabQuiz quiz);

    //Cập nhật một câu hỏi ngữ pháp
    @Update
    void updateGrammarQuiz(GrammarQuiz quiz);

    //Xóa một câu hỏi từ vựng
    @Delete
    void deleteVocabQuiz(VocabQuiz quiz);

    //Xóa một câu hỏi ngữ pháp
    @Delete
    void deleteGrammarQuiz(GrammarQuiz quiz);
}