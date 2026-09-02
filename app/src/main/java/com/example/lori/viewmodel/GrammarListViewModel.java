package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.UserProgressLocal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//Quản lý danh sách bài học ngữ pháp và bộ lọc cấp độ hiển thị trên grammarlistfragment
public class GrammarListViewModel extends AndroidViewModel {

    private final LiveData<List<GrammarLesson>> allLessons;
    private final LiveData<Set<Integer>> completedLessonIds;
    private final MutableLiveData<String> selectedLevel = new MutableLiveData<>("all");
    private final MediatorLiveData<List<GrammarLesson>> filteredLessons = new MediatorLiveData<>();

    public GrammarListViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);

        allLessons = db.grammarDao().getAllLessons();
        LiveData<List<UserProgressLocal>> lessonProgress =
                db.progressDao().getProgressByType(UserProgressLocal.TYPE_GRAMMAR_LESSON);
        //Chuyển danh sách tiến độ thành tập hợp id bài học đã hoàn thành để tra cứu nhanh
        completedLessonIds = Transformations.map(lessonProgress, this::toCompletedIdSet);

        //Lọc lại danh sách mỗi khi dữ liệu gốc hoặc cấp độ đang chọn thay đổi
        filteredLessons.addSource(allLessons, lessons -> applyFilter());
        filteredLessons.addSource(selectedLevel, level -> applyFilter());
    }

    private Set<Integer> toCompletedIdSet(List<UserProgressLocal> progressList) {
        Set<Integer> ids = new HashSet<>();
        if (progressList != null) {
            for (UserProgressLocal progress : progressList) {
                if (UserProgressLocal.STATUS_COMPLETED.equals(progress.status)) {
                    ids.add(progress.itemId);
                }
            }
        }
        return ids;
    }

    //Áp dụng bộ lọc cấp độ hiện tại lên danh sách bài học gốc
    private void applyFilter() {
        List<GrammarLesson> lessons = allLessons.getValue();
        if (lessons == null) return;
        String level = selectedLevel.getValue();
        if (level == null || "all".equals(level)) {
            filteredLessons.setValue(lessons);
            return;
        }
        List<GrammarLesson> result = new ArrayList<>();
        for (GrammarLesson lesson : lessons) {
            if (level.equals(lesson.level)) {
                result.add(lesson);
            }
        }
        filteredLessons.setValue(result);
    }

    //Đổi cấp độ đang được lọc
    public void setLevel(String level) {
        selectedLevel.setValue(level);
    }

    public LiveData<List<GrammarLesson>> getFilteredLessons() {
        return filteredLessons;
    }

    public LiveData<Set<Integer>> getCompletedLessonIds() {
        return completedLessonIds;
    }
}