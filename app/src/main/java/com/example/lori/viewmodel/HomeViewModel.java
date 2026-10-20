package com.example.lori.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.Topic;
import com.example.lori.data.remote.dto.UserStatsResponse;
import com.example.lori.data.sync.SyncManager;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.UserRepository;
import com.example.lori.utils.ExpManager;

import java.util.List;

//viewmodel quản lý dữ liệu thống kê hiển thị trên homefragment: số lượng nội dung (Room) và EXP, cấp độ, streak (Backend)
public class HomeViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final UserRepository userRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final LiveData<Integer> topicCount;
    private final LiveData<Integer> wordCount;
    private final LiveData<Integer> lessonCount;

    //null = chưa tải được lần nào (giao diện giữ dấu "–")
    private final MutableLiveData<UserStatsResponse> stats = new MutableLiveData<>();
    //Số EXP vừa tăng so với lần người dùng thấy trước; giao diện chạy animation một lần rồi gọi consumeExpGain()
    private final MutableLiveData<Long> expGain = new MutableLiveData<>();

    //Lượt đồng bộ ngay đang được chờ kết quả (để gỡ observer khi xong hoặc khi ViewModel bị hủy)
    @Nullable
    private LiveData<WorkInfo> syncSource;
    @Nullable
    private Observer<WorkInfo> syncObserver;
    private boolean cleared;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        userRepository = new UserRepository(application);

        LiveData<List<Topic>> topics = db.topicDao().getAllTopics();
        LiveData<List<GrammarLesson>> lessons = db.grammarDao().getAllLessons();

        //Chuyển danh sách chủ đề thành tổng số chủ đề
        topicCount = Transformations.map(topics, List::size);
        //Tính tổng số từ vựng từ số lượng từ của từng chủ đề
        wordCount = Transformations.map(topics, this::sumWordCount);
        //Chuyển danh sách bài học thành tổng số bài học
        lessonCount = Transformations.map(lessons, List::size);
    }

    //Tính tổng số từ vựng của tất cả các chủ đề
    private int sumWordCount(List<Topic> topics) {
        int total = 0;
        for (Topic topic : topics) {
            total += topic.wordCount;
        }
        return total;
    }

    //Gọi mỗi khi màn Home hiện lên: tải ngay số liệu hiện có; nếu máy còn tiến trình học chưa đẩy lên server
    //thì đồng bộ ngay rồi tải lại, để EXP vừa kiếm được hiện ra mà không phải chờ chu kỳ 30 phút
    public void refresh() {
        loadStats();
        db.getQueryExecutor().execute(() -> {
            if (!db.progressDao().getUnsyncedProgress().isEmpty()) {
                mainHandler.post(this::syncThenReload);
            }
        });
    }

    public void consumeExpGain() {
        expGain.setValue(null);
    }

    private void syncThenReload() {
        if (cleared) {
            return;
        }
        stopWatchingSync();
        LiveData<WorkInfo> source = SyncManager.syncNow(getApplication());
        Observer<WorkInfo> observer = info -> {
            if (info == null || !info.getState().isFinished()) {
                return;
            }
            stopWatchingSync();
            //Chỉ tải lại khi đồng bộ thành công; thất bại thì giữ số liệu đang hiển thị (chu kỳ 30 phút sau sẽ thử lại)
            if (info.getState() == WorkInfo.State.SUCCEEDED) {
                loadStats();
            }
        };
        syncSource = source;
        syncObserver = observer;
        source.observeForever(observer);
    }

    private void stopWatchingSync() {
        if (syncSource != null && syncObserver != null) {
            syncSource.removeObserver(syncObserver);
        }
        syncSource = null;
        syncObserver = null;
    }

    //Lấy EXP, cấp độ, streak từ Backend. Lỗi (mất mạng, hết thời gian chờ...) thì giữ nguyên số liệu đang hiển thị
    private void loadStats() {
        LiveData<ApiResult<UserStatsResponse>> source = userRepository.stats();
        source.observeForever(new Observer<ApiResult<UserStatsResponse>>() {
            @Override
            public void onChanged(ApiResult<UserStatsResponse> result) {
                source.removeObserver(this);
                if (!cleared && result.isSuccess() && result.getData() != null) {
                    handleStats(result.getData());
                }
            }
        });
    }

    //EXP lớn hơn mốc lần trước người dùng thấy thì báo chênh lệch để giao diện chạy "+XX EXP"
    //Lần đầu (chưa có mốc) chỉ ghi mốc, không chạy animation
    private void handleStats(UserStatsResponse data) {
        long lastSeen = ExpManager.getLastSeenExp(getApplication());
        if (lastSeen != ExpManager.NO_BASELINE && data.totalExp > lastSeen) {
            expGain.setValue(data.totalExp - lastSeen);
        }
        ExpManager.setLastSeenExp(getApplication(), data.totalExp);
        stats.setValue(data);
    }

    @Override
    protected void onCleared() {
        cleared = true;
        stopWatchingSync();
        super.onCleared();
    }

    public LiveData<Integer> getTopicCount() { return topicCount; }
    public LiveData<Integer> getWordCount() { return wordCount; }
    public LiveData<Integer> getLessonCount() { return lessonCount; }
    public LiveData<UserStatsResponse> getStats() { return stats; }
    public LiveData<Long> getExpGain() { return expGain; }
}