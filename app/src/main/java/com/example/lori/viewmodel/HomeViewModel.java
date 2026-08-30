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

//viewmodel quản lý dữ liệu thống kê hiển thị trên homefragment
public class HomeViewModel extends AndroidViewModel {

    private final LiveData<Integer> topicCount;
    private final LiveData<Integer> wordCount;
    private final LiveData<Integer> lessonCount;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);

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

    public LiveData<Integer> getTopicCount() { return topicCount; }
    public LiveData<Integer> getWordCount() { return wordCount; }
    public LiveData<Integer> getLessonCount() { return lessonCount; }
}