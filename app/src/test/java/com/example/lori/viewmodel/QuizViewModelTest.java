package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.dao.ProgressDao;
import com.example.lori.data.local.dao.QuizDao;
import com.example.lori.data.local.entity.GrammarQuiz;
import com.example.lori.data.local.entity.UserProgressLocal;
import com.example.lori.data.local.entity.VocabQuiz;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Date;

public class QuizViewModelTest {

    private static final int TOPIC_ID = 5;
    private static final int LESSON_ID = 7;

    //Cho LiveData chạy đồng bộ trên luồng test, không cần Looper của Android
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private MockedStatic<AppDatabase> appDatabaseStatic;
    private QuizDao quizDao;
    private ProgressDao progressDao;
    private QuizViewModel viewModel;

    @Before
    public void setUp() {
        Application application = mock(Application.class);
        AppDatabase db = mock(AppDatabase.class);
        quizDao = mock(QuizDao.class);
        progressDao = mock(ProgressDao.class);
        when(db.quizDao()).thenReturn(quizDao);
        when(db.progressDao()).thenReturn(progressDao);
        appDatabaseStatic = Mockito.mockStatic(AppDatabase.class);
        appDatabaseStatic.when(() -> AppDatabase.getInstance(application)).thenReturn(db);
        viewModel = new QuizViewModel(application);
    }

    @After
    public void tearDown() {
        //Tắt luồng nền của ViewModel
        viewModel.onCleared();
        appDatabaseStatic.close();
    }

    @Test
    public void loadQuiz_vocab_buildsQuestionsFromVocabQuizzes() throws InterruptedException {
        loadVocab(vocabQuiz("Q1", "A"), vocabQuiz("Q2", "B"));

        assertEquals(2, viewModel.getTotalQuestions());
        assertEquals("Q1", viewModel.getCurrentQuestion().question);
        assertEquals("Animals", viewModel.getItemTitle().getValue());
        verify(quizDao, never()).getGrammarQuizByLesson(anyInt());
    }

    @Test
    public void loadQuiz_grammar_buildsQuestionsFromGrammarQuizzes() throws InterruptedException {
        loadGrammar(grammarQuiz("G1", "C"));

        assertEquals(1, viewModel.getTotalQuestions());
        assertEquals("G1", viewModel.getCurrentQuestion().question);
        verify(quizDao, never()).getVocabQuizByTopic(anyInt());
    }

    @Test
    public void selectAnswer_correctLetterIgnoringCase_countsCorrect() throws InterruptedException {
        loadVocab(vocabQuiz("Q1", "B"));

        viewModel.selectAnswer("b");

        assertEquals("b", viewModel.getSelectedAnswer().getValue());
        assertEquals(Integer.valueOf(1), viewModel.getCorrectCount().getValue());
        assertEquals(Integer.valueOf(0), viewModel.getIncorrectCount().getValue());
    }

    @Test
    public void selectAnswer_wrongLetter_countsIncorrect() throws InterruptedException {
        loadVocab(vocabQuiz("Q1", "B"));

        viewModel.selectAnswer("A");

        assertEquals(Integer.valueOf(0), viewModel.getCorrectCount().getValue());
        assertEquals(Integer.valueOf(1), viewModel.getIncorrectCount().getValue());
    }

    @Test
    public void selectAnswer_secondTapOnSameQuestion_isIgnored() throws InterruptedException {
        loadVocab(vocabQuiz("Q1", "B"));

        viewModel.selectAnswer("A");
        viewModel.selectAnswer("B");

        assertEquals("A", viewModel.getSelectedAnswer().getValue());
        assertEquals(Integer.valueOf(0), viewModel.getCorrectCount().getValue());
        assertEquals(Integer.valueOf(1), viewModel.getIncorrectCount().getValue());
    }

    @Test
    public void selectAnswer_beforeQuestionsLoaded_isIgnored() {
        viewModel.selectAnswer("A");

        assertNull(viewModel.getSelectedAnswer().getValue());
        assertEquals(Integer.valueOf(0), viewModel.getCorrectCount().getValue());
        assertEquals(Integer.valueOf(0), viewModel.getIncorrectCount().getValue());
    }

    @Test
    public void nextQuestion_notLast_movesOnAndClearsSelection() throws InterruptedException {
        loadVocab(vocabQuiz("Q1", "A"), vocabQuiz("Q2", "B"));
        viewModel.selectAnswer("A");

        viewModel.nextQuestion();

        assertEquals(Integer.valueOf(1), viewModel.getCurrentIndex().getValue());
        assertEquals("Q2", viewModel.getCurrentQuestion().question);
        assertNull(viewModel.getSelectedAnswer().getValue());
        assertEquals(Boolean.FALSE, viewModel.getQuizFinished().getValue());
        verifyNoInteractions(progressDao);
    }

    @Test
    public void nextQuestion_beforeQuestionsLoaded_doesNothing() {
        viewModel.nextQuestion();

        assertEquals(Integer.valueOf(0), viewModel.getCurrentIndex().getValue());
        assertEquals(Boolean.FALSE, viewModel.getQuizFinished().getValue());
        verifyNoInteractions(progressDao);
    }

    @Test
    public void nextQuestion_onLastQuestion_finishesAndSavesNewProgress() throws InterruptedException {
        loadVocab(vocabQuiz("Q1", "A"), vocabQuiz("Q2", "B"));
        viewModel.selectAnswer("A");
        viewModel.nextQuestion();
        viewModel.selectAnswer("C");

        viewModel.nextQuestion();

        assertEquals(Boolean.TRUE, viewModel.getQuizFinished().getValue());
        ArgumentCaptor<UserProgressLocal> saved = ArgumentCaptor.forClass(UserProgressLocal.class);
        verify(progressDao, timeout(2000)).insertOrUpdate(saved.capture());
        UserProgressLocal progress = saved.getValue();
        assertEquals(UserProgressLocal.TYPE_VOCAB_QUIZ, progress.itemType);
        assertEquals(TOPIC_ID, progress.itemId);
        assertEquals(UserProgressLocal.STATUS_COMPLETED, progress.status);
        assertEquals(1, progress.correctCount);
        assertEquals(1, progress.incorrectCount);
        assertFalse(progress.isSynced);
        assertNotNull(progress.lastStudiedAt);
    }

    @Test
    public void nextQuestion_onLastQuestion_existingProgress_isUpdatedAndMarkedUnsynced()
            throws InterruptedException {
        UserProgressLocal existing = new UserProgressLocal();
        existing.itemType = UserProgressLocal.TYPE_GRAMMAR_QUIZ;
        existing.itemId = LESSON_ID;
        existing.status = UserProgressLocal.STATUS_COMPLETED;
        existing.correctCount = 9;
        existing.incorrectCount = 9;
        existing.lastStudiedAt = new Date(1000L);
        existing.isSynced = true;
        when(progressDao.getProgress(UserProgressLocal.TYPE_GRAMMAR_QUIZ, LESSON_ID)).thenReturn(existing);
        loadGrammar(grammarQuiz("G1", "C"));
        viewModel.selectAnswer("C");

        viewModel.nextQuestion();

        verify(progressDao, timeout(2000)).update(existing);
        assertEquals(1, existing.correctCount);
        assertEquals(0, existing.incorrectCount);
        assertFalse(existing.isSynced);
        assertTrue(existing.lastStudiedAt.getTime() > 1000L);
        verify(progressDao, never()).insertOrUpdate(any());
    }

    //Đọc câu hỏi chạy ở luồng nền và đưa kết quả về bằng postValue nên phải chờ giá trị xuất hiện
    private void loadVocab(VocabQuiz... quizzes) throws InterruptedException {
        when(quizDao.getVocabQuizByTopic(TOPIC_ID)).thenReturn(Arrays.asList(quizzes));
        viewModel.loadQuiz(QuizViewModel.TYPE_VOCAB, TOPIC_ID, "Animals");
        awaitValue(viewModel.getQuestions());
    }

    private void loadGrammar(GrammarQuiz... quizzes) throws InterruptedException {
        when(quizDao.getGrammarQuizByLesson(LESSON_ID)).thenReturn(Arrays.asList(quizzes));
        viewModel.loadQuiz(QuizViewModel.TYPE_GRAMMAR, LESSON_ID, "Present Simple");
        awaitValue(viewModel.getQuestions());
    }

    private static <T> T awaitValue(LiveData<T> liveData) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (liveData.getValue() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull("LiveData chưa có giá trị sau 2 giây", liveData.getValue());
        return liveData.getValue();
    }

    private static VocabQuiz vocabQuiz(String question, String correctAnswer) {
        VocabQuiz quiz = new VocabQuiz();
        quiz.question = question;
        quiz.optionA = "Đáp án A";
        quiz.optionB = "Đáp án B";
        quiz.optionC = "Đáp án C";
        quiz.optionD = "Đáp án D";
        quiz.correctAnswer = correctAnswer;
        quiz.explanation = "Giải thích";
        return quiz;
    }

    private static GrammarQuiz grammarQuiz(String question, String correctAnswer) {
        GrammarQuiz quiz = new GrammarQuiz();
        quiz.question = question;
        quiz.optionA = "Đáp án A";
        quiz.optionB = "Đáp án B";
        quiz.optionC = "Đáp án C";
        quiz.optionD = "Đáp án D";
        quiz.correctAnswer = correctAnswer;
        quiz.explanation = "Giải thích";
        return quiz;
    }
}