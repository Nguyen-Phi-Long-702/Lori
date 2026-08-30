package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.Topic;

import java.util.List;

//viewmodel quản lý danh sách chủ đề hiển thị trên topiclistfragment
public class TopicListViewModel extends AndroidViewModel {

    private final LiveData<List<Topic>> topics;

    public TopicListViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        topics = db.topicDao().getAllTopics();
    }

    public LiveData<List<Topic>> getTopics() {
        return topics;
    }
}