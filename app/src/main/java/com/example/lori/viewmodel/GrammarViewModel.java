package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarExample;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.UserProgressLocal;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Quản lý dữ liệu và trạng thái của màn hình nội dung bài học ngữ pháp
public class GrammarViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Integer> lessonId = new MutableLiveData<>();
    private final MutableLiveData<GrammarLesson> lesson = new MutableLiveData<>();
    private final LiveData<List<GrammarExample>> examples;
    private final LiveData<UserProgressLocal> progress;

    public GrammarViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);

        //getExamplesByLesson và observeProgress đều là livedata nên Room tự chạy nền, không cần executor
        examples = Transformations.switchMap(lessonId, id -> db.grammarDao().getExamplesByLesson(id));
        progress = Transformations.switchMap(lessonId, id ->
                db.progressDao().observeProgress(UserProgressLocal.TYPE_GRAMMAR_LESSON, id));
    }

    //Tải bài học theo id; getLessonById là hàm đồng bộ nên bắt buộc chạy trong luồng nền
    public void loadLesson(int newLessonId) {
        if (Objects.equals(lessonId.getValue(), newLessonId)) return;
        lessonId.setValue(newLessonId);
        executor.execute(() -> {
            GrammarLesson result = db.grammarDao().getLessonById(newLessonId);
            lesson.postValue(result);
        });
    }

    public void markLessonCompleted() {
        Integer id = lessonId.getValue();
        if (id == null) return;
        executor.execute(() -> {
            UserProgressLocal existing = db.progressDao()
                    .getProgress(UserProgressLocal.TYPE_GRAMMAR_LESSON, id);
            if (existing != null) {
                existing.status = UserProgressLocal.STATUS_COMPLETED;
                existing.lastStudiedAt = new Date();
                db.progressDao().update(existing);
            } else {
                UserProgressLocal newProgress = new UserProgressLocal();
                newProgress.itemType = UserProgressLocal.TYPE_GRAMMAR_LESSON;
                newProgress.itemId = id;
                newProgress.status = UserProgressLocal.STATUS_COMPLETED;
                newProgress.lastStudiedAt = new Date();
                newProgress.isSynced = false;
                db.progressDao().insertOrUpdate(newProgress);
            }
        });
    }

    public LiveData<GrammarLesson> getLesson() { return lesson; }
    public LiveData<List<GrammarExample>> getExamples() { return examples; }
    public LiveData<UserProgressLocal> getProgress() { return progress; }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}