package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.dao.TopicWordDao;
import com.example.lori.data.local.entity.TopicWord;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

public class FlashcardViewModelTest {

    //Cho LiveData chạy đồng bộ trên luồng test, không cần Looper của Android
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private MockedStatic<AppDatabase> appDatabaseStatic;
    private TopicWordDao topicWordDao;
    private FlashcardViewModel viewModel;

    @Before
    public void setUp() {
        Application application = mock(Application.class);
        AppDatabase db = mock(AppDatabase.class);
        topicWordDao = mock(TopicWordDao.class);
        when(db.topicWordDao()).thenReturn(topicWordDao);
        //ViewModel tự lấy db qua AppDatabase.getInstance nên thay hàm tĩnh này bằng db giả
        appDatabaseStatic = Mockito.mockStatic(AppDatabase.class);
        appDatabaseStatic.when(() -> AppDatabase.getInstance(application)).thenReturn(db);
        viewModel = new FlashcardViewModel(application);
    }

    @After
    public void tearDown() {
        appDatabaseStatic.close();
    }

    @Test
    public void currentIndex_startsAtZero() {
        assertEquals(Integer.valueOf(0), viewModel.getCurrentIndex().getValue());
    }

    @Test
    public void setCurrentIndex_updatesIndex() {
        viewModel.setCurrentIndex(3);

        assertEquals(Integer.valueOf(3), viewModel.getCurrentIndex().getValue());
    }

    @Test
    public void loadTopic_exposesWordsOfThatTopic() {
        MutableLiveData<List<TopicWord>> topicWords =
                new MutableLiveData<>(Collections.singletonList(word("apple")));
        when(topicWordDao.getWordsByTopic(3)).thenReturn(topicWords);
        //switchMap chỉ chạy khi có observer
        viewModel.getWords().observeForever(words -> { });

        viewModel.loadTopic(3);

        assertEquals("apple", viewModel.getWords().getValue().get(0).word);
    }

    @Test
    public void loadTopic_newTopic_switchesWordsAndResetsIndex() {
        when(topicWordDao.getWordsByTopic(1))
                .thenReturn(new MutableLiveData<>(Collections.singletonList(word("apple"))));
        when(topicWordDao.getWordsByTopic(2))
                .thenReturn(new MutableLiveData<>(Collections.singletonList(word("bus"))));
        viewModel.getWords().observeForever(words -> { });
        viewModel.loadTopic(1);
        viewModel.setCurrentIndex(4);

        viewModel.loadTopic(2);

        assertEquals("bus", viewModel.getWords().getValue().get(0).word);
        assertEquals(Integer.valueOf(0), viewModel.getCurrentIndex().getValue());
    }

    @Test
    public void loadTopic_sameTopic_keepsCurrentIndex() {
        viewModel.loadTopic(1);
        viewModel.setCurrentIndex(4);

        viewModel.loadTopic(1);

        assertEquals(Integer.valueOf(4), viewModel.getCurrentIndex().getValue());
    }

    private static TopicWord word(String text) {
        TopicWord word = new TopicWord();
        word.word = text;
        word.meaning = "nghĩa của " + text;
        return word;
    }
}