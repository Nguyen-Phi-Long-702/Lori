package com.example.lori.data.local.dao;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarQuiz;
import com.example.lori.data.local.entity.VocabQuiz;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class QuizDaoTest {

    private AppDatabase db;
    private QuizDao quizDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        quizDao = db.quizDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void insertAndGetVocabQuizByTopic() {
        VocabQuiz quiz = new VocabQuiz();
        quiz.topicId = 1;
        quiz.question = "What is 'apple' in Vietnamese?";
        quiz.optionA = "Quả táo";
        quiz.optionB = "Quả chuối";
        quiz.optionC = "Quả cam";
        quiz.optionD = "Quả nho";
        quiz.correctAnswer = "A";

        quizDao.insertVocabQuizzes(Collections.singletonList(quiz));

        List<VocabQuiz> result = quizDao.getVocabQuizByTopic(1);
        assertEquals(1, result.size());
        assertEquals("A", result.get(0).correctAnswer);
    }

    @Test
    public void insertAndGetGrammarQuizByLesson() {
        GrammarQuiz quiz = new GrammarQuiz();
        quiz.lessonId = 1;
        quiz.question = "Choose correct tense.";
        quiz.optionA = "go";
        quiz.optionB = "goes";
        quiz.optionC = "went";
        quiz.optionD = "going";
        quiz.correctAnswer = "B";

        quizDao.insertGrammarQuizzes(Collections.singletonList(quiz));

        List<GrammarQuiz> result = quizDao.getGrammarQuizByLesson(1);
        assertEquals(1, result.size());
        assertEquals("B", result.get(0).correctAnswer);
    }

    @Test
    public void updateVocabQuiz() {
        VocabQuiz quiz = new VocabQuiz();
        quiz.topicId = 2;
        quiz.question = "Old question";
        quiz.optionA = "A";
        quiz.optionB = "B";
        quiz.optionC = "C";
        quiz.optionD = "D";
        quiz.correctAnswer = "A";
        quizDao.insertVocabQuizzes(Collections.singletonList(quiz));

        VocabQuiz saved = quizDao.getVocabQuizByTopic(2).get(0);
        saved.question = "New question";
        quizDao.updateVocabQuiz(saved);

        VocabQuiz updated = quizDao.getVocabQuizByTopic(2).get(0);
        assertEquals("New question", updated.question);
    }

    @Test
    public void deleteGrammarQuiz() {
        GrammarQuiz quiz = new GrammarQuiz();
        quiz.lessonId = 3;
        quiz.question = "Temp question";
        quiz.optionA = "A";
        quiz.optionB = "B";
        quiz.optionC = "C";
        quiz.optionD = "D";
        quiz.correctAnswer = "C";
        quizDao.insertGrammarQuizzes(Collections.singletonList(quiz));

        GrammarQuiz saved = quizDao.getGrammarQuizByLesson(3).get(0);
        quizDao.deleteGrammarQuiz(saved);

        List<GrammarQuiz> result = quizDao.getGrammarQuizByLesson(3);
        assertEquals(0, result.size());
    }
}