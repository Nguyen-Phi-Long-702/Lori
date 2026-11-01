package com.example.lori.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.MyWord;
import com.example.lori.data.remote.dto.AiImageResponse;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;
import com.example.lori.utils.ImagePreparer;
import com.example.lori.utils.NetworkStateManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Quản lý màn Nhận diện ảnh (Premium): nhận Uri ảnh → chuẩn bị ảnh → gửi nhận diện → giữ kết quả; lưu các từ vào "Từ vựng của tôi" (Room)
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class ImageLearningViewModel extends AndroidViewModel {

    //Ba trạng thái của màn: chờ chụp/chọn ảnh, đang nhận diện, đã có kết quả
    public static final int STATE_CAPTURE = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_RESULT = 2;

    private final AiRepository aiRepository;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    //Đưa kết quả từ luồng nền về luồng chính (Repository phải được gọi từ luồng chính)
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LiveData<Boolean> online;

    private final MutableLiveData<Integer> state = new MutableLiveData<>(STATE_CAPTURE);
    //Ảnh đã gửi đi (đúng ảnh Backend trả box theo); null khi chưa có kết quả
    private final MutableLiveData<Bitmap> photo = new MutableLiveData<>();
    private final MutableLiveData<List<AiImageResponse.ImageObject>> objects = new MutableLiveData<>();
    //Vật đang được chọn xem ví dụ và tô khung
    private final MutableLiveData<Integer> selectedIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();
    //true sau khi đã bấm lưu các từ của ảnh hiện tại
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);
    //Số từ MỚI vừa lưu (0 = tất cả đã có từ trước): giao diện hiện thông báo rồi gọi consumeSavedCount()
    private final MutableLiveData<Integer> savedCount = new MutableLiveData<>();
    //Chụp/chọn ảnh chỉ bấm được khi có mạng và đang ở trạng thái chờ ảnh
    private final MediatorLiveData<Boolean> captureEnabled = new MediatorLiveData<>();

    public ImageLearningViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
        db = AppDatabase.getInstance(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
        captureEnabled.addSource(online, value -> updateCaptureEnabled());
        captureEnabled.addSource(state, value -> updateCaptureEnabled());
    }

    //Có ảnh (vừa chụp xong hoặc vừa chọn từ thư viện): chuẩn bị ảnh rồi gửi nhận diện
    public void identify(Uri uri) {
        if (!isState(STATE_CAPTURE)) {
            return;
        }
        errorMessage.setValue(null);
        state.setValue(STATE_LOADING);

        executor.execute(() -> {
            ImagePreparer.PreparedImage image = null;
            try {
                image = ImagePreparer.prepare(getApplication(), uri);
            } catch (Exception e) {
                //Không đọc được ảnh (file hỏng, định dạng lạ…): image vẫn null, báo lỗi bên dưới
            } finally {
                deleteCapturedFile(uri);
            }
            ImagePreparer.PreparedImage prepared = image;
            mainHandler.post(() -> {
                if (prepared == null) {
                    fail(R.string.image_error_prepare);
                } else {
                    send(prepared);
                }
            });
        });
    }

    private void send(ImagePreparer.PreparedImage image) {
        LiveData<ApiResult<AiImageResponse>> source = aiRepository.identifyImage(image.jpeg);
        //Nghe một lần rồi gỡ (giống SentenceCheckViewModel)
        source.observeForever(new Observer<ApiResult<AiImageResponse>>() {
            @Override
            public void onChanged(ApiResult<AiImageResponse> apiResult) {
                source.removeObserver(this);
                handleResult(image.bitmap, apiResult);
            }
        });
    }

    private void handleResult(Bitmap bitmap, ApiResult<AiImageResponse> apiResult) {
        AiImageResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.objects != null) {
            photo.setValue(bitmap);
            selectedIndex.setValue(0);
            saved.setValue(false);
            objects.setValue(body.objects);
            state.setValue(STATE_RESULT);
        } else if (apiResult.isPremiumRequired()) {
            premiumRequired.setValue(true);
            state.setValue(STATE_CAPTURE);
        } else {
            //400 của endpoint này nghĩa là ảnh không hợp lệ (câu chung "nhập lại câu tiếng Anh" không hợp)
            boolean badImage = apiResult.getStatus() == ApiResult.Status.HTTP_ERROR && apiResult.getHttpCode() == 400;
            fail(badImage ? R.string.image_error_invalid : AiErrorMessages.of(apiResult));
        }
    }

    private void fail(@StringRes int messageRes) {
        errorMessage.setValue(messageRes);
        state.setValue(STATE_CAPTURE);
    }

    //Ảnh chụp bằng CameraX là file tạm trong cacheDir: xóa sau khi đã đọc xong. Ảnh từ thư viện (content://) thì không đụng tới
    private void deleteCapturedFile(Uri uri) {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            new File(uri.getPath()).delete();
        }
    }

    //Bấm "Chụp ảnh mới": bỏ kết quả, quay về trạng thái chờ ảnh
    public void retake() {
        photo.setValue(null);
        objects.setValue(null);
        saved.setValue(false);
        errorMessage.setValue(null);
        state.setValue(STATE_CAPTURE);
    }

    //Bấm vào một dòng từ: tô khung vật đó và hiện câu ví dụ của nó
    public void select(int index) {
        selectedIndex.setValue(index);
    }

    //Giao diện báo lỗi của riêng phần chụp (vd CameraX không chụp được)
    public void showError(@StringRes int messageRes) {
        errorMessage.setValue(messageRes);
    }

    //Bấm "Lưu cả N từ": lưu toàn bộ từ của ảnh vào Room, từ đã có thì bỏ qua
    public void saveAll() {
        List<AiImageResponse.ImageObject> items = objects.getValue();
        if (items == null || items.isEmpty() || Boolean.TRUE.equals(saved.getValue())) {
            return;
        }
        //Đặt ngay để chặn bấm đúp
        saved.setValue(true);

        Date now = new Date();
        executor.execute(() -> {
            List<MyWord> words = new ArrayList<>();
            for (AiImageResponse.ImageObject item : items) {
                MyWord word = new MyWord();
                word.word = item.word;
                word.ipa = item.ipa;
                word.meaning = item.meaning;
                word.example = item.example;
                word.exampleVi = item.exampleVi;
                word.savedAt = now;
                words.add(word);
            }
            long[] ids = db.myWordDao().insertAll(words);
            int added = 0;
            for (long id : ids) {
                if (id != -1) {
                    added++;
                }
            }
            savedCount.postValue(added);
        });
    }

    public void consumePremiumRequired() {
        premiumRequired.setValue(null);
    }

    public void consumeSavedCount() {
        savedCount.setValue(null);
    }

    private void updateCaptureEnabled() {
        captureEnabled.setValue(Boolean.TRUE.equals(online.getValue()) && isState(STATE_CAPTURE));
    }

    private boolean isState(int expected) {
        Integer current = state.getValue();
        return current != null && current == expected;
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
        super.onCleared();
    }

    public LiveData<Integer> getState() { return state; }
    public LiveData<Bitmap> getPhoto() { return photo; }
    public LiveData<List<AiImageResponse.ImageObject>> getObjects() { return objects; }
    public LiveData<Integer> getSelectedIndex() { return selectedIndex; }
    //Lỗi hiện dưới vùng chụp ảnh; null = không lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
    public LiveData<Boolean> getSaved() { return saved; }
    public LiveData<Integer> getSavedCount() { return savedCount; }
    public LiveData<Boolean> getCaptureEnabled() { return captureEnabled; }
    public LiveData<Boolean> getOnline() { return online; }
}