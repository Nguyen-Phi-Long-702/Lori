package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.data.remote.dto.AiExamAnalysisResponse;
import com.example.lori.data.remote.dto.AiQuizAnalysisResponse;
import com.example.lori.data.sync.SyncBeforeLoad;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;

//Quản lý màn Phân tích AI sau một quiz hoặc một lần thi. AI chỉ đọc kết quả đã chấm, không chấm lại
//Quiz do app chấm rồi mới đồng bộ lên server nên phải đồng bộ xong mới phân tích; bài thi đã nằm trên server sẵn
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class QuizExamAiInsightViewModel extends AndroidViewModel {

    //Giá trị của argument "source": TYPE_VOCAB / TYPE_GRAMMAR của QuizViewModel là quiz, SOURCE_EXAM là bài thi
    public static final String SOURCE_EXAM = "exam";

    private static final String QUIZ_TYPE_VOCABULARY = "vocabulary";
    private static final String QUIZ_TYPE_GRAMMAR = "grammar";

    private final AiRepository aiRepository;
    private final SyncBeforeLoad syncBeforeLoad;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    //null = chưa có (đang tải hoặc lỗi)
    private final MutableLiveData<AiInsight> insight = new MutableLiveData<>();
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();

    public QuizExamAiInsightViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
        syncBeforeLoad = new SyncBeforeLoad(application);
    }

    //Gọi khi màn hình hiện lên và khi bấm Thử lại. Đang tải hoặc đã có kết quả (xoay màn hình) thì bỏ qua để không gọi AI lại
    //Quiz dùng source + itemId, bài thi dùng resultId
    public void load(String source, int itemId, String resultId) {
        if (Boolean.TRUE.equals(loading.getValue()) || insight.getValue() != null) {
            return;
        }
        errorMessage.setValue(null);
        loading.setValue(true);
        if (SOURCE_EXAM.equals(source)) {
            fetchExam(resultId);
        } else {
            String quizType = QuizViewModel.TYPE_VOCAB.equals(source) ? QUIZ_TYPE_VOCABULARY : QUIZ_TYPE_GRAMMAR;
            syncBeforeLoad.run(() -> fetchQuiz(quizType, itemId));
        }
    }

    private void fetchQuiz(String quizType, int itemId) {
        LiveData<ApiResult<AiQuizAnalysisResponse>> source = aiRepository.analyzeQuiz(quizType, itemId);
        //Nghe một lần rồi gỡ (giống SentenceCheckViewModel)
        source.observeForever(new Observer<ApiResult<AiQuizAnalysisResponse>>() {
            @Override
            public void onChanged(ApiResult<AiQuizAnalysisResponse> apiResult) {
                source.removeObserver(this);
                AiQuizAnalysisResponse body = apiResult.getData();
                handleResult(apiResult, body == null ? null : AiInsight.fromQuiz(body));
            }
        });
    }

    private void fetchExam(String resultId) {
        LiveData<ApiResult<AiExamAnalysisResponse>> source = aiRepository.analyzeExam(resultId);
        source.observeForever(new Observer<ApiResult<AiExamAnalysisResponse>>() {
            @Override
            public void onChanged(ApiResult<AiExamAnalysisResponse> apiResult) {
                source.removeObserver(this);
                AiExamAnalysisResponse body = apiResult.getData();
                handleResult(apiResult, body == null ? null : AiInsight.fromExam(body));
            }
        });
    }

    private void handleResult(ApiResult<?> apiResult, AiInsight result) {
        loading.setValue(false);
        if (apiResult.isSuccess() && result != null) {
            insight.setValue(result);
        } else if (apiResult.isPremiumRequired()) {
            premiumRequired.setValue(true);
        } else {
            errorMessage.setValue(AiErrorMessages.of(apiResult));
        }
    }

    public void consumePremiumRequired() {
        premiumRequired.setValue(null);
    }

    @Override
    protected void onCleared() {
        syncBeforeLoad.cancel();
        super.onCleared();
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<AiInsight> getInsight() { return insight; }
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
}