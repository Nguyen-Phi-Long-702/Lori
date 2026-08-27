package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.Topic;

import java.util.List;

/**
 * ViewModel cho HomeFragment (Dashboard thống kê offline).
 * Đọc trực tiếp qua AppDatabase (chưa có Repository ở giai đoạn này —
 * Repository pattern sẽ được thêm ở Tuần 6 cho dữ liệu online).
 */
public class HomeViewModel extends AndroidViewModel {

    private final LiveData<Integer> topicCount;
    private final LiveData<Integer> wordCount;
    private final LiveData<Integer> lessonCount;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);

        LiveData<List<Topic>> topics = db.topicDao().getAllTopics();
        LiveData<List<GrammarLesson>> lessons = db.grammarDao().getAllLessons();

        topicCount = Transformations.map(topics, List::size);
        wordCount = Transformations.map(topics, this::sumWordCount);
        lessonCount = Transformations.map(lessons, List::size);
    }

    private int sumWordCount(List<Topic> topics) {
        int total = 0;
        for (Topic topic : topics) {
            total += topic.wordCount;
        }
        return total;
    }

    public LiveData<Integer> getTopicCount() { return topicCount; }
    public LiveData<Integer> getWordCount() { return wordCount; }
    public LiveData<Integer> getLessonCount() { return lessonCount; }
}