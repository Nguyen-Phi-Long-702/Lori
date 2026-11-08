package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.os.Handler;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.WorkInfo;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.dao.GrammarDao;
import com.example.lori.data.local.dao.ProgressDao;
import com.example.lori.data.local.dao.TopicDao;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.Topic;
import com.example.lori.data.local.entity.UserProgressLocal;
import com.example.lori.data.remote.dto.UserStatsResponse;
import com.example.lori.data.sync.SyncManager;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.UserRepository;
import com.example.lori.utils.ExpManager;
import com.example.lori.utils.PremiumManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public class HomeViewModelTest {

    //Cho LiveData chạy đồng bộ trên luồng test, không cần Looper của Android
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private final MutableLiveData<List<Topic>> topics = new MutableLiveData<>();
    private final MutableLiveData<List<GrammarLesson>> lessons = new MutableLiveData<>();

    private Application application;
    private ProgressDao progressDao;
    private MockedStatic<AppDatabase> appDatabaseStatic;
    private MockedStatic<ExpManager> expManagerStatic;
    private MockedStatic<PremiumManager> premiumManagerStatic;
    private MockedStatic<SyncManager> syncManagerStatic;
    private MockedConstruction<UserRepository> userRepositoryConstruction;
    private MockedConstruction<Handler> handlerConstruction;
    private UserRepository userRepository;
    private HomeViewModel viewModel;

    @Before
    public void setUp() {
        application = mock(Application.class);
        AppDatabase db = mock(AppDatabase.class);
        TopicDao topicDao = mock(TopicDao.class);
        GrammarDao grammarDao = mock(GrammarDao.class);
        progressDao = mock(ProgressDao.class);
        when(db.topicDao()).thenReturn(topicDao);
        when(db.grammarDao()).thenReturn(grammarDao);
        when(db.progressDao()).thenReturn(progressDao);
        when(topicDao.getAllTopics()).thenReturn(topics);
        when(grammarDao.getAllLessons()).thenReturn(lessons);
        when(progressDao.getUnsyncedProgress()).thenReturn(Collections.emptyList());
        //Tác vụ nền của Room chạy ngay trên luồng test
        Executor directExecutor = Runnable::run;
        when(db.getQueryExecutor()).thenReturn(directExecutor);

        appDatabaseStatic = Mockito.mockStatic(AppDatabase.class);
        appDatabaseStatic.when(() -> AppDatabase.getInstance(application)).thenReturn(db);
        expManagerStatic = Mockito.mockStatic(ExpManager.class);
        //Mặc định chưa có mốc EXP (lần đầu mở Home)
        expManagerStatic.when(() -> ExpManager.getLastSeenExp(application)).thenReturn(ExpManager.NO_BASELINE);
        premiumManagerStatic = Mockito.mockStatic(PremiumManager.class);
        syncManagerStatic = Mockito.mockStatic(SyncManager.class);
        userRepositoryConstruction = Mockito.mockConstruction(UserRepository.class);
        //Handler giả chạy Runnable ngay thay vì xếp vào hàng đợi của Looper
        handlerConstruction = Mockito.mockConstruction(Handler.class, (handler, context) ->
                when(handler.post(any(Runnable.class))).thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return true;
                }));

        viewModel = new HomeViewModel(application);
        userRepository = userRepositoryConstruction.constructed().get(0);
    }

    @After
    public void tearDown() {
        viewModel.onCleared();
        handlerConstruction.close();
        userRepositoryConstruction.close();
        syncManagerStatic.close();
        premiumManagerStatic.close();
        expManagerStatic.close();
        appDatabaseStatic.close();
    }

    @Test
    public void counts_followTopicsAndLessons() {
        //Transformations.map chỉ chạy khi có observer
        viewModel.getTopicCount().observeForever(count -> { });
        viewModel.getWordCount().observeForever(count -> { });
        viewModel.getLessonCount().observeForever(count -> { });

        topics.setValue(Arrays.asList(topic(10), topic(25)));
        lessons.setValue(Collections.singletonList(new GrammarLesson()));

        assertEquals(Integer.valueOf(2), viewModel.getTopicCount().getValue());
        assertEquals(Integer.valueOf(35), viewModel.getWordCount().getValue());
        assertEquals(Integer.valueOf(1), viewModel.getLessonCount().getValue());
    }

    @Test
    public void refresh_firstTime_showsStatsWithoutExpGainAndSavesBaseline() {
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5));

        viewModel.refresh();

        UserStatsResponse stats = viewModel.getStats().getValue();
        assertEquals(300, stats.totalExp);
        assertEquals(3, stats.level);
        assertEquals(5, stats.currentStreak);
        assertNull(viewModel.getExpGain().getValue());
        expManagerStatic.verify(() -> ExpManager.setLastSeenExp(application, 300L));
    }

    @Test
    public void refresh_expHigherThanLastSeen_reportsTheDifference() {
        expManagerStatic.when(() -> ExpManager.getLastSeenExp(application)).thenReturn(200L);
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5));

        viewModel.refresh();

        assertEquals(Long.valueOf(100L), viewModel.getExpGain().getValue());
        expManagerStatic.verify(() -> ExpManager.setLastSeenExp(application, 300L));
    }

    @Test
    public void refresh_expNotHigherThanLastSeen_reportsNoGain() {
        expManagerStatic.when(() -> ExpManager.getLastSeenExp(application)).thenReturn(300L);
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5));

        viewModel.refresh();

        assertNull(viewModel.getExpGain().getValue());
    }

    @Test
    public void consumeExpGain_clearsTheGain() {
        expManagerStatic.when(() -> ExpManager.getLastSeenExp(application)).thenReturn(200L);
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5));
        viewModel.refresh();

        viewModel.consumeExpGain();

        assertNull(viewModel.getExpGain().getValue());
    }

    @Test
    public void refresh_requestFails_keepsDisplayedStats() {
        LiveData<ApiResult<UserStatsResponse>> failed = new MutableLiveData<>(ApiResult.timeout());
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5), failed);
        viewModel.refresh();

        viewModel.refresh();

        assertEquals(300, viewModel.getStats().getValue().totalExp);
        expManagerStatic.verify(() -> ExpManager.setLastSeenExp(application, 300L), times(1));
    }

    @Test
    public void refresh_withoutUnsyncedProgress_doesNotSyncNow() {
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5));

        viewModel.refresh();

        syncManagerStatic.verify(() -> SyncManager.syncNow(any()), never());
    }

    @Test
    public void refresh_withUnsyncedProgress_reloadsStatsOnlyAfterSyncSucceeds() {
        MutableLiveData<WorkInfo> work = startSyncWithUnsyncedProgress();
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5), statsResult(450, 4, 5));

        viewModel.refresh();
        assertEquals(300, viewModel.getStats().getValue().totalExp);

        work.setValue(workInfo(WorkInfo.State.RUNNING));
        assertEquals(300, viewModel.getStats().getValue().totalExp);

        work.setValue(workInfo(WorkInfo.State.SUCCEEDED));
        assertEquals(450, viewModel.getStats().getValue().totalExp);
        verify(userRepository, times(2)).stats();
    }

    @Test
    public void refresh_syncFails_doesNotReloadStats() {
        MutableLiveData<WorkInfo> work = startSyncWithUnsyncedProgress();
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5), statsResult(450, 4, 5));
        viewModel.refresh();

        work.setValue(workInfo(WorkInfo.State.FAILED));

        assertEquals(300, viewModel.getStats().getValue().totalExp);
        verify(userRepository, times(1)).stats();
    }

    @Test
    public void onCleared_stopsWaitingForSync() {
        MutableLiveData<WorkInfo> work = startSyncWithUnsyncedProgress();
        when(userRepository.stats()).thenReturn(statsResult(300, 3, 5), statsResult(450, 4, 5));
        viewModel.refresh();

        viewModel.onCleared();
        work.setValue(workInfo(WorkInfo.State.SUCCEEDED));

        verify(userRepository, times(1)).stats();
    }

    @Test
    public void isPremium_readsTheSavedFlag() {
        premiumManagerStatic.when(() -> PremiumManager.isPremium(application)).thenReturn(true);

        assertTrue(viewModel.isPremium());
    }

    //Máy còn tiến trình chưa đẩy lên server nên refresh() sẽ gọi đồng bộ ngay; trả về LiveData để test tự đặt trạng thái
    private MutableLiveData<WorkInfo> startSyncWithUnsyncedProgress() {
        when(progressDao.getUnsyncedProgress()).thenReturn(Collections.singletonList(new UserProgressLocal()));
        MutableLiveData<WorkInfo> work = new MutableLiveData<>();
        syncManagerStatic.when(() -> SyncManager.syncNow(application)).thenReturn(work);
        return work;
    }

    private static WorkInfo workInfo(WorkInfo.State state) {
        WorkInfo info = mock(WorkInfo.class);
        when(info.getState()).thenReturn(state);
        return info;
    }

    private static LiveData<ApiResult<UserStatsResponse>> statsResult(int totalExp, int level, int streak) {
        UserStatsResponse stats = new UserStatsResponse();
        stats.totalExp = totalExp;
        stats.level = level;
        stats.currentStreak = streak;
        return new MutableLiveData<>(ApiResult.success(stats, 200));
    }

    private static Topic topic(int wordCount) {
        Topic topic = new Topic();
        topic.wordCount = wordCount;
        return topic;
    }
}