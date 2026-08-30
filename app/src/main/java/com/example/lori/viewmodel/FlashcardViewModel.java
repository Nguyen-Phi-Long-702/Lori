package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.TopicWord;

import java.util.List;
import java.util.Objects;

//Quản lý dữ liệu và trạng thái của màn hình flashcard
public class FlashcardViewModel extends AndroidViewModel {

    private final MutableLiveData<Integer> topicId = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final LiveData<List<TopicWord>> words;

    public FlashcardViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        //Tự động tải lại danh sách từ khi topicId thay đổi
        words = Transformations.switchMap(topicId, id -> db.topicWordDao().getWordsByTopic(id));
    }

    //Tải dữ liệu của chủ đề mới và đưa flashcard về vị trí đầu tiên
    public void loadTopic(int newTopicId) {
        if (!Objects.equals(topicId.getValue(), newTopicId)) {
            topicId.setValue(newTopicId);
            currentIndex.setValue(0);
        }
    }

    //Cập nhật vị trí flashcard hiện tại
    public void setCurrentIndex(int index) {
        currentIndex.setValue(index);
    }

    public LiveData<List<TopicWord>> getWords() {
        return words;
    }

    public LiveData<Integer> getCurrentIndex() {
        return currentIndex;
    }
}