package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.data.remote.dto.LearningProfileResponse;
import com.example.lori.data.sync.SyncBeforeLoad;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.UserRepository;

//Quản lý màn Hồ sơ năng lực: đồng bộ tiến trình còn trên máy, rồi lấy trình độ ước tính, điểm mạnh và điểm yếu (GET /api/users/me/learning-profile)
//Màn này không cần Premium. Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class LearningInsightViewModel extends AndroidViewModel {

    //Các mức trình độ Backend trả, thấp đến cao; vòng tròn trình độ = vị trí / số mức
    private static final String[] LEVELS = {"A1", "A2", "B1", "B2", "C1"};

    private final UserRepository userRepository;
    private final SyncBeforeLoad syncBeforeLoad;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    //null = chưa có (đang tải hoặc lỗi)
    private final MutableLiveData<LearningProfileResponse> profile = new MutableLiveData<>();
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();

    public LearningInsightViewModel(@NonNull Application application) {
        super(application);
        userRepository = new UserRepository(application);
        syncBeforeLoad = new SyncBeforeLoad(application);
    }

    //Gọi khi màn hình hiện lên và khi bấm Thử lại. Đang tải hoặc đã có dữ liệu (xoay màn hình) thì bỏ qua
    public void load() {
        if (Boolean.TRUE.equals(loading.getValue()) || profile.getValue() != null) {
            return;
        }
        errorMessage.setValue(null);
        loading.setValue(true);
        syncBeforeLoad.run(this::fetch);
    }

    private void fetch() {
        LiveData<ApiResult<LearningProfileResponse>> source = userRepository.learningProfile();
        //Nghe một lần rồi gỡ (giống SentenceCheckViewModel)
        source.observeForever(new Observer<ApiResult<LearningProfileResponse>>() {
            @Override
            public void onChanged(ApiResult<LearningProfileResponse> apiResult) {
                source.removeObserver(this);
                loading.setValue(false);
                LearningProfileResponse body = apiResult.getData();
                if (apiResult.isSuccess() && body != null) {
                    profile.setValue(body);
                } else {
                    errorMessage.setValue(AiErrorMessages.of(apiResult));
                }
            }
        });
    }

    //Độ dài vòng tròn trình độ theo phần nghìn: A1 = 200 ... C1 = 1000; mức lạ hoặc null = 0
    public static int levelPermille(String level) {
        for (int i = 0; i < LEVELS.length; i++) {
            if (LEVELS[i].equals(level)) {
                return (i + 1) * 1000 / LEVELS.length;
            }
        }
        return 0;
    }

    @Override
    protected void onCleared() {
        syncBeforeLoad.cancel();
        super.onCleared();
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<LearningProfileResponse> getProfile() { return profile; }
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
}