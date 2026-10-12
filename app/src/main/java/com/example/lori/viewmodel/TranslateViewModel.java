package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.remote.dto.TranslateResponse;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.TranslateRepository;
import com.example.lori.utils.NetworkStateManager;

//Quản lý màn Dịch thuật: chiều dịch Anh <-> Việt, gọi Backend dịch, giữ kết quả và lỗi
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class TranslateViewModel extends AndroidViewModel {

    //Mã ngôn ngữ Backend nhận (TranslateRequest: chỉ "en" hoặc "vi", và phải khác nhau)
    private static final String LANG_EN = "en";
    private static final String LANG_VI = "vi";

    private final TranslateRepository translateRepository;
    private final LiveData<Boolean> online;

    //true = Anh -> Việt (mặc định, theo prototype); false = Việt -> Anh
    private final MutableLiveData<Boolean> englishToVietnamese = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    //Kết quả dịch gần nhất; null = chưa có (ẩn thẻ kết quả)
    private final MutableLiveData<String> translation = new MutableLiveData<>();
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //Nút Dịch chỉ bấm được khi có mạng và không đang dịch
    private final MediatorLiveData<Boolean> translateEnabled = new MediatorLiveData<>();

    public TranslateViewModel(@NonNull Application application) {
        super(application);
        translateRepository = new TranslateRepository(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
        translateEnabled.addSource(online, value -> updateTranslateEnabled());
        translateEnabled.addSource(loading, value -> updateTranslateEnabled());
    }

    //Đổi chiều Anh <-> Việt, xóa kết quả và lỗi cũ (không đổi khi đang dịch). Nội dung đã nhập giữ nguyên
    public void swapDirection() {
        if (isBusy()) {
            return;
        }
        englishToVietnamese.setValue(!isEnglishToVietnamese());
        translation.setValue(null);
        errorMessage.setValue(null);
    }

    //Bấm nút Dịch ngay: gọi POST /api/translate theo chiều đang chọn
    public void translate(String input) {
        if (isBusy()) {
            return;
        }
        errorMessage.setValue(null);
        translation.setValue(null);

        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            errorMessage.setValue(R.string.translate_error_empty);
            return;
        }

        loading.setValue(true);
        boolean enToVi = isEnglishToVietnamese();
        LiveData<ApiResult<TranslateResponse>> source = translateRepository.translate(
                text,
                enToVi ? LANG_EN : LANG_VI,
                enToVi ? LANG_VI : LANG_EN);
        //Nghe một lần rồi gỡ (giống PremiumPurchaseViewModel)
        source.observeForever(new Observer<ApiResult<TranslateResponse>>() {
            @Override
            public void onChanged(ApiResult<TranslateResponse> apiResult) {
                source.removeObserver(this);
                handleResult(apiResult);
            }
        });
    }

    private void handleResult(ApiResult<TranslateResponse> apiResult) {
        loading.setValue(false);
        TranslateResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.translatedText != null) {
            translation.setValue(body.translatedText);
            return;
        }
        errorMessage.setValue(messageFor(apiResult));
    }

    //Đổi kết quả lỗi thành câu thông báo tiếng Việt
    @StringRes
    private static int messageFor(ApiResult<?> apiResult) {
        switch (apiResult.getStatus()) {
            case OFFLINE:
                return R.string.translate_offline_hint;
            case TIMEOUT:
                return R.string.auth_error_timeout;
            case NETWORK_ERROR:
                return R.string.auth_error_network;
            default:
                return messageForHttp(apiResult.getHttpCode());
        }
    }

    @StringRes
    private static int messageForHttp(int httpCode) {
        switch (httpCode) {
            case 400:
                return R.string.auth_error_invalid_request;
            case 401:
                return R.string.premium_error_session_expired;
            case 403:
                return R.string.auth_error_forbidden;
            case 429:
                return R.string.auth_error_too_many_requests;
            //Backend không gọi được LibreTranslate (chưa chạy hoặc không tới được)
            case 503:
                return R.string.translate_error_unavailable;
            default:
                return R.string.auth_error_server;
        }
    }

    private void updateTranslateEnabled() {
        boolean isOnline = Boolean.TRUE.equals(online.getValue());
        translateEnabled.setValue(isOnline && !isBusy());
    }

    private boolean isEnglishToVietnamese() {
        return !Boolean.FALSE.equals(englishToVietnamese.getValue());
    }

    private boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    //true = Anh → Việt, false = Việt -> Anh
    public LiveData<Boolean> getEnglishToVietnamese() { return englishToVietnamese; }
    public LiveData<Boolean> getLoading() { return loading; }
    //Kết quả dịch; null = ẩn thẻ kết quả
    public LiveData<String> getTranslation() { return translation; }
    //Lỗi hiện dưới nút Dịch; null = không có lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getTranslateEnabled() { return translateEnabled; }
    public LiveData<Boolean> getOnline() { return online; }
}