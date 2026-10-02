package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.DictionaryWord;
import com.example.lori.data.local.entity.WordPronunciation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Quản lý dữ liệu tìm kiếm và tra chi tiết từ điển offline
public class DictionaryViewModel extends AndroidViewModel {
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<DictionaryWord>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<DictionaryWordDetail> wordDetail = new MutableLiveData<>();

    public DictionaryViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    // Tìm kiếm theo tiền tố người dùng nhập
    public void search(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            return;
        }
        executor.execute(() -> {
            List<DictionaryWord> result = db.dictionaryDao().searchByPrefix(normalized);
            searchResults.postValue(result);
        });
    }

    //Tải toàn bộ chi tiết của 1 từ (định nghĩa, phát âm, đồng nghĩa, trái nghĩa) trước khi điều hướng sang WordDetailFragment.
    public void loadWordDetail(DictionaryWord word) {
        executor.execute(() -> {
            List<WordPronunciation> pronunciations = db.dictionaryDao().getPronunciations(word.id);
            String ipa = pronunciations.isEmpty() ? null : pronunciations.get(0).ipa;
            DictionaryWordDetail detail = new DictionaryWordDetail(
                    word.word,
                    ipa,
                    db.dictionaryDao().getDefinitions(word.id),
                    db.dictionaryDao().getSynonyms(word.id),
                    db.dictionaryDao().getAntonyms(word.id));
            wordDetail.postValue(detail);
        });
    }

    //Đánh dấu đã xử lý xong dữ liệu chi tiết vừa tải. Gọi ngay sau khi điều hướng sang WordDetailFragment, để tránh mở lại màn chi tiết một lần nữa khi livedata phát lại giá trị cũ (ví dụ sau khi xoay màn hình).
    public void consumeWordDetail() {
        wordDetail.setValue(null);
    }

    public LiveData<List<DictionaryWord>> getSearchResults() { return searchResults; }
    public LiveData<DictionaryWordDetail> getWordDetail() { return wordDetail; }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}