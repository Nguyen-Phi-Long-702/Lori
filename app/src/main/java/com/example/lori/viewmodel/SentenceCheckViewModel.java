package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.remote.dto.AiGrammarResponse;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;
import com.example.lori.utils.NetworkStateManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Quản lý màn Phân tích câu: gửi câu cho AI, giữ kết quả, lỗi, và tên bài ngữ pháp liên quan tìm trong Room
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class SentenceCheckViewModel extends AndroidViewModel {

    private final AiRepository aiRepository;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LiveData<Boolean> online;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    //Kết quả lần kiểm tra gần nhất; null = chưa có (ẩn khu kết quả)
    private final MutableLiveData<AiGrammarResponse> result = new MutableLiveData<>();
    //Câu user đã gửi đi kiểm tra (để hiện "Câu gốc" dù user sửa ô nhập sau đó)
    private final MutableLiveData<String> originalSentence = new MutableLiveData<>();
    //Tên bài ngữ pháp liên quan có trong Room; null = không có bài để hiện
    private final MutableLiveData<String> relatedLessonTitle = new MutableLiveData<>();
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();
    //Nút Kiểm tra chỉ bấm được khi có mạng và không đang kiểm tra
    private final MediatorLiveData<Boolean> checkEnabled = new MediatorLiveData<>();

    public SentenceCheckViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
        db = AppDatabase.getInstance(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
        checkEnabled.addSource(online, value -> updateCheckEnabled());
        checkEnabled.addSource(loading, value -> updateCheckEnabled());
    }

    //Bấm Kiểm tra ngay: gọi POST /api/ai/analyze-text (type = grammar)
    public void check(String input) {
        if (isBusy()) {
            return;
        }
        errorMessage.setValue(null);

        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            errorMessage.setValue(R.string.ai_sentence_error_empty);
            return;
        }

        result.setValue(null);
        originalSentence.setValue(null);
        relatedLessonTitle.setValue(null);
        loading.setValue(true);

        LiveData<ApiResult<AiGrammarResponse>> source = aiRepository.analyzeGrammar(text);
        //Nghe một lần rồi gỡ (giống TranslateViewModel)
        source.observeForever(new Observer<ApiResult<AiGrammarResponse>>() {
            @Override
            public void onChanged(ApiResult<AiGrammarResponse> apiResult) {
                source.removeObserver(this);
                handleResult(text, apiResult);
            }
        });
    }

    private void handleResult(String text, ApiResult<AiGrammarResponse> apiResult) {
        loading.setValue(false);
        AiGrammarResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.correctedSentence != null) {
            originalSentence.setValue(text);
            result.setValue(body);
            loadRelatedLesson(body);
        } else if (apiResult.isPremiumRequired()) {
            premiumRequired.setValue(true);
        } else {
            errorMessage.setValue(AiErrorMessages.of(apiResult));
        }
    }

    //Câu sai và AI gắn được bài ngữ pháp: lấy tên bài trong Room (getLessonById chạy đồng bộ nên phải ở luồng nền)
    //Id không có trong Room thì thôi, không hiện thẻ
    private void loadRelatedLesson(AiGrammarResponse body) {
        if (body.correct || body.lessonId == null) {
            return;
        }
        int lessonId = body.lessonId;
        executor.execute(() -> {
            GrammarLesson lesson = db.grammarDao().getLessonById(lessonId);
            if (lesson != null) {
                relatedLessonTitle.postValue(lesson.titleVi);
            }
        });
    }

    public void consumePremiumRequired() {
        premiumRequired.setValue(null);
    }

    private void updateCheckEnabled() {
        checkEnabled.setValue(Boolean.TRUE.equals(online.getValue()) && !isBusy());
    }

    private boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
        super.onCleared();
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<AiGrammarResponse> getResult() { return result; }
    public LiveData<String> getOriginalSentence() { return originalSentence; }
    public LiveData<String> getRelatedLessonTitle() { return relatedLessonTitle; }
    //Lỗi hiện dưới nút Kiểm tra; null = không lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
    public LiveData<Boolean> getCheckEnabled() { return checkEnabled; }
    public LiveData<Boolean> getOnline() { return online; }
}