package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.data.remote.dto.AiRecommendResponse;
import com.example.lori.data.sync.SyncBeforeLoad;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;

//Quản lý màn Ôn tập hôm nay: đồng bộ tiến trình còn trên máy, rồi hỏi Backend (POST /api/ai/recommend) các chủ đề yếu nhất kèm nhận xét của AI
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class AiPersonalizedReviewViewModel extends AndroidViewModel {

    private final AiRepository aiRepository;
    private final SyncBeforeLoad syncBeforeLoad;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    //null = chưa có (đang tải hoặc lỗi); topics rỗng = chưa có điểm yếu nào
    private final MutableLiveData<AiRecommendResponse> result = new MutableLiveData<>();
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();

    public AiPersonalizedReviewViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
        syncBeforeLoad = new SyncBeforeLoad(application);
    }

    //Gọi khi màn hình hiện lên và khi bấm Thử lại. Đang tải hoặc đã có kết quả (xoay màn hình) thì bỏ qua để không gọi AI lại
    public void load() {
        if (Boolean.TRUE.equals(loading.getValue()) || result.getValue() != null) {
            return;
        }
        errorMessage.setValue(null);
        loading.setValue(true);
        syncBeforeLoad.run(this::fetch);
    }

    private void fetch() {
        LiveData<ApiResult<AiRecommendResponse>> source = aiRepository.recommend();
        //Nghe một lần rồi gỡ (giống SentenceCheckViewModel)
        source.observeForever(new Observer<ApiResult<AiRecommendResponse>>() {
            @Override
            public void onChanged(ApiResult<AiRecommendResponse> apiResult) {
                source.removeObserver(this);
                handleResult(apiResult);
            }
        });
    }

    private void handleResult(ApiResult<AiRecommendResponse> apiResult) {
        loading.setValue(false);
        AiRecommendResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.topics != null) {
            result.setValue(body);
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
    public LiveData<AiRecommendResponse> getResult() { return result; }
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
}