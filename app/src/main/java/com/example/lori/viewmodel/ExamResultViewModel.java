package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.remote.dto.ExamResultDetailResponse;
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.ExamRepository;

import java.util.ArrayList;
import java.util.List;

//Kết quả một lần thi: điểm (ExamResultFragment) và từng câu để xem lại (ExamReviewFragment)
//ViewModel gắn với MainActivity nên hai màn dùng chung dữ liệu: đi từ Kết quả sang Xem lại không gọi mạng lần 2
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class ExamResultViewModel extends AndroidViewModel {

    private static final String TYPE_IELTS = "IELTS";
    //Điểm tối đa để vẽ vòng tròn/thanh: TOEIC tổng 990, mỗi kỹ năng 495; IELTS band 9
    private static final int TOEIC_TOTAL_MAX = 990;
    private static final int TOEIC_SKILL_MAX = 495;
    private static final int IELTS_MAX = 9;

    private final ExamRepository examRepository;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //Điểm và thống kê; null = chưa tải hoặc tải lỗi
    private final MutableLiveData<ExamResultResponse> result = new MutableLiveData<>();
    //Từng câu của bài làm (đáp án bạn chọn, đáp án đúng, giải thích)
    private final MutableLiveData<List<ExamResultDetailResponse.Item>> items = new MutableLiveData<>();

    //Id kết quả đang tải hoặc đã tải
    private String requestedId;

    public ExamResultViewModel(@NonNull Application application) {
        super(application);
        examRepository = new ExamRepository(application);
    }

    //Tải kết quả theo id. Cùng id đã có dữ liệu hoặc đang tải thì bỏ qua (xoay màn hình, từ Kết quả sang Xem lại)
    //Id khác thì xóa dữ liệu cũ rồi tải lại để không hiện nhầm kết quả của lần thi trước
    public void load(String resultId) {
        if (resultId == null) {
            return;
        }
        if (resultId.equals(requestedId)
                && (result.getValue() != null || Boolean.TRUE.equals(loading.getValue()))) {
            return;
        }
        requestedId = resultId;
        result.setValue(null);
        items.setValue(null);
        errorMessage.setValue(null);
        loading.setValue(true);
        LiveData<ApiResult<ExamResultDetailResponse>> source = examRepository.resultDetail(resultId);
        //Nghe một lần rồi gỡ (giống ExamListViewModel)
        source.observeForever(new Observer<ApiResult<ExamResultDetailResponse>>() {
            @Override
            public void onChanged(ApiResult<ExamResultDetailResponse> apiResult) {
                source.removeObserver(this);
                //Trong lúc chờ mà đã chuyển sang kết quả khác thì bỏ câu trả lời cũ
                if (resultId.equals(requestedId)) {
                    handleResult(apiResult);
                }
            }
        });
    }

    //Bấm Thử lại sau khi tải lỗi
    public void retry() {
        if (requestedId != null) {
            load(requestedId);
        }
    }

    private void handleResult(ApiResult<ExamResultDetailResponse> apiResult) {
        loading.setValue(false);
        if (apiResult.isPremiumRequired()) {
            errorMessage.setValue(R.string.exam_result_error_premium);
            return;
        }
        ExamResultDetailResponse data = apiResult.getData();
        if (!apiResult.isSuccess() || data == null || data.result == null) {
            errorMessage.setValue(apiResult.isSuccess() ? R.string.auth_error_server : messageFor(apiResult));
            return;
        }
        items.setValue(data.items != null ? data.items : new ArrayList<>());
        result.setValue(data.result);
    }

    public static boolean isIelts(ExamResultResponse result) {
        return TYPE_IELTS.equals(result.examType);
    }

    //Độ dài vòng tròn điểm tổng theo phần nghìn (0-1000): TOEIC tổng/990, IELTS band/9
    public static int totalPermille(ExamResultResponse result) {
        return permille(result.totalScore, isIelts(result) ? IELTS_MAX : TOEIC_TOTAL_MAX);
    }

    //Độ dài thanh của một kỹ năng theo phần nghìn: TOEIC điểm/495, IELTS band/9
    public static int skillPermille(ExamResultResponse result, double skillScore) {
        return permille(skillScore, isIelts(result) ? IELTS_MAX : TOEIC_SKILL_MAX);
    }

    private static int permille(double score, double max) {
        return (int) Math.round(Math.max(0, Math.min(score, max)) / max * 1000);
    }

    //Thời gian làm bài tính bằng phút, làm tròn lên, tối thiểu 1 phút (giống tab Lịch sử)
    public static int minutesSpent(ExamResultResponse result) {
        return Math.max(1, (result.timeSpentSeconds + 59) / 60);
    }

    //Đổi kết quả lỗi thành câu thông báo tiếng Việt (403 PREMIUM_REQUIRED đã được xử lý riêng trong handleResult)
    @StringRes
    private static int messageFor(ApiResult<?> apiResult) {
        switch (apiResult.getStatus()) {
            case OFFLINE:
                return R.string.auth_error_offline;
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
            case 401:
                return R.string.premium_error_session_expired;
            //403 còn lại là Backend từ chối ứng dụng (sai API key / chữ ký), không phải do Premium
            case 403:
                return R.string.auth_error_forbidden;
            case 404:
                return R.string.exam_result_error_not_found;
            case 429:
                return R.string.auth_error_too_many_requests;
            default:
                return R.string.auth_error_server;
        }
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<ExamResultResponse> getResult() { return result; }
    public LiveData<List<ExamResultDetailResponse.Item>> getItems() { return items; }
}