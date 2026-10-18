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
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.data.remote.dto.ExamSummaryResponse;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.ExamRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

//Quản lý màn Đề thi: tải danh sách đề và lịch sử thi từ Backend, lọc theo TOEIC/IELTS, báo trạng thái khóa Premium
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class ExamListViewModel extends AndroidViewModel {

    //Bốn lựa chọn trên hàng chip (theo prototype v7). TOEIC và IELTS trùng giá trị examType Backend trả về
    public static final String TAB_ALL = "ALL";
    public static final String TAB_TOEIC = "TOEIC";
    public static final String TAB_IELTS = "IELTS";
    public static final String TAB_HISTORY = "HISTORY";

    private final ExamRepository examRepository;

    private final MutableLiveData<String> selectedTab = new MutableLiveData<>(TAB_ALL);
    //true khi Backend trả 403 PREMIUM_REQUIRED (user Free): giao diện hiện khung khóa thay cho danh sách
    private final MutableLiveData<Boolean> locked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    //Toàn bộ đề Backend trả về; null = chưa tải hoặc tải lỗi
    private final MutableLiveData<List<ExamSummaryResponse>> exams = new MutableLiveData<>();
    //Lịch sử thi của user (Backend đã xếp mới nhất trước); null = chưa tải hoặc tải lỗi
    private final MutableLiveData<List<ExamResultResponse>> results = new MutableLiveData<>();
    private final MutableLiveData<Integer> examsError = new MutableLiveData<>();
    private final MutableLiveData<Integer> resultsError = new MutableLiveData<>();
    //Danh sách đề sau khi lọc theo tab đang chọn; null = chưa có dữ liệu
    private final MediatorLiveData<List<ExamSummaryResponse>> visibleExams = new MediatorLiveData<>();
    //Lỗi của tab đang chọn; null = không lỗi
    private final MediatorLiveData<Integer> errorMessage = new MediatorLiveData<>();
    //true khi cần hiện hai thẻ "Sắp ra mắt" (IELTS Writing, IELTS Speaking): chỉ ở tab IELTS
    private final MediatorLiveData<Boolean> showComingSoon = new MediatorLiveData<>();

    //Số request đang chạy; loading = true khi còn request chưa xong
    private int pendingRequests = 0;

    public ExamListViewModel(@NonNull Application application) {
        super(application);
        examRepository = new ExamRepository(application);
        visibleExams.addSource(exams, value -> updateVisibleExams());
        visibleExams.addSource(selectedTab, value -> updateVisibleExams());
        errorMessage.addSource(examsError, value -> updateErrorMessage());
        errorMessage.addSource(resultsError, value -> updateErrorMessage());
        errorMessage.addSource(selectedTab, value -> updateErrorMessage());
        showComingSoon.addSource(selectedTab, tab -> showComingSoon.setValue(TAB_IELTS.equals(tab)));
    }

    //Chọn chip. Dữ liệu đã tải sẵn nên chỉ đổi cách hiển thị, không gọi lại mạng
    public void selectTab(String tab) {
        if (tab == null || tab.equals(selectedTab.getValue())) {
            return;
        }
        selectedTab.setValue(tab);
    }

    //Tải lại dữ liệu: gọi khi màn hiện lên và khi bấm Thử lại
    //Luôn hỏi Backend thay vì đọc cờ PremiumManager trên máy (cờ có thể chưa kịp cập nhật, vd vừa đăng nhập tài khoản Premium):
    //200 = Premium, hiện danh sách; 403 PREMIUM_REQUIRED = Free, hiện khung khóa
    public void refresh() {
        load(examRepository.list(), this::handleExams);
    }

    //Gọi một API, đếm số request đang chạy, xử lý 403 PREMIUM_REQUIRED chung cho mọi API của màn này
    private <T> void load(LiveData<ApiResult<T>> source, Consumer<ApiResult<T>> onResult) {
        pendingRequests++;
        loading.setValue(true);
        //Nghe một lần rồi gỡ (giống TranslateViewModel)
        source.observeForever(new Observer<ApiResult<T>>() {
            @Override
            public void onChanged(ApiResult<T> result) {
                source.removeObserver(this);
                if (result.isPremiumRequired()) {
                    showLocked();
                } else {
                    onResult.accept(result);
                }
                pendingRequests--;
                loading.setValue(pendingRequests > 0);
            }
        });
    }

    private void showLocked() {
        locked.setValue(true);
        exams.setValue(null);
        results.setValue(null);
        examsError.setValue(null);
        resultsError.setValue(null);
    }

    private void handleExams(ApiResult<List<ExamSummaryResponse>> result) {
        locked.setValue(false);
        if (result.isSuccess()) {
            List<ExamSummaryResponse> body = result.getData();
            exams.setValue(body != null ? body : new ArrayList<ExamSummaryResponse>());
            examsError.setValue(null);
            //Danh sách đề trả 200 nghĩa là user là Premium, lúc này mới tải lịch sử thi
            load(examRepository.results(), this::handleResults);
        } else {
            exams.setValue(null);
            results.setValue(null);
            examsError.setValue(messageFor(result));
            resultsError.setValue(null);
        }
    }

    private void handleResults(ApiResult<List<ExamResultResponse>> result) {
        if (result.isSuccess()) {
            List<ExamResultResponse> body = result.getData();
            results.setValue(body != null ? body : new ArrayList<ExamResultResponse>());
            resultsError.setValue(null);
        } else {
            results.setValue(null);
            resultsError.setValue(messageFor(result));
        }
    }

    //Lọc danh sách đề theo tab: TOEIC/IELTS lọc theo examType, các tab còn lại giữ đủ danh sách
    private void updateVisibleExams() {
        List<ExamSummaryResponse> all = exams.getValue();
        String tab = selectedTab.getValue();
        if (all == null) {
            visibleExams.setValue(null);
            return;
        }
        if (TAB_TOEIC.equals(tab) || TAB_IELTS.equals(tab)) {
            List<ExamSummaryResponse> filtered = new ArrayList<>();
            for (ExamSummaryResponse exam : all) {
                if (tab.equals(exam.examType)) {
                    filtered.add(exam);
                }
            }
            visibleExams.setValue(filtered);
        } else {
            visibleExams.setValue(all);
        }
    }

    //Tab Lịch sử hiện lỗi của lịch sử, nếu không có thì hiện lỗi của danh sách đề (khi danh sách đề lỗi thì lịch sử chưa được tải)
    private void updateErrorMessage() {
        Integer examsMessage = examsError.getValue();
        if (TAB_HISTORY.equals(selectedTab.getValue())) {
            Integer resultsMessage = resultsError.getValue();
            errorMessage.setValue(resultsMessage != null ? resultsMessage : examsMessage);
        } else {
            errorMessage.setValue(examsMessage);
        }
    }

    //Đổi kết quả lỗi thành câu thông báo tiếng Việt (403 PREMIUM_REQUIRED đã được xử lý riêng trong load())
    @StringRes
    private static int messageFor(ApiResult<?> result) {
        switch (result.getStatus()) {
            case OFFLINE:
                return R.string.auth_error_offline;
            case TIMEOUT:
                return R.string.auth_error_timeout;
            case NETWORK_ERROR:
                return R.string.auth_error_network;
            default:
                return messageForHttp(result.getHttpCode());
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
            case 429:
                return R.string.auth_error_too_many_requests;
            default:
                return R.string.auth_error_server;
        }
    }

    public LiveData<String> getSelectedTab() { return selectedTab; }
    public LiveData<Boolean> getLocked() { return locked; }
    public LiveData<Boolean> getLoading() { return loading; }
    //Đề đã lọc theo tab đang chọn (dùng cho ALL/TOEIC/IELTS)
    public LiveData<List<ExamSummaryResponse>> getVisibleExams() { return visibleExams; }
    //Lịch sử thi (dùng cho tab HISTORY)
    public LiveData<List<ExamResultResponse>> getResults() { return results; }
    //Lỗi của tab đang chọn; null = không lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    //true khi cần hiện hai thẻ "Sắp ra mắt" (tab IELTS)
    public LiveData<Boolean> getShowComingSoon() { return showComingSoon; }
}