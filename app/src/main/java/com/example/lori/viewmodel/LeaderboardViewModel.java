package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.remote.dto.LeaderboardEntryResponse;
import com.example.lori.data.remote.dto.LeaderboardMeResponse;
import com.example.lori.data.remote.dto.UserResponse;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.LeaderboardRepository;
import com.example.lori.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

//Quản lý màn Bảng xếp hạng: chọn bảng (toàn thời gian / tuần / tháng), tải từng trang, làm mới, hạng của chính user
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class LeaderboardViewModel extends AndroidViewModel {

    //Ba bảng, trùng giá trị {period} của Backend
    public static final String PERIOD_GLOBAL = "global";
    public static final String PERIOD_WEEKLY = "weekly";
    public static final String PERIOD_MONTHLY = "monthly";

    //Số dòng mỗi trang (Backend cho phép 1-50)
    private static final int PAGE_SIZE = 20;
    //Ba người đầu bảng nằm trên bục podium, các dòng còn lại nằm trong danh sách
    private static final int PODIUM_SIZE = 3;

    private final LeaderboardRepository leaderboardRepository;
    private final UserRepository userRepository;

    private final MutableLiveData<String> selectedPeriod = new MutableLiveData<>(PERIOD_GLOBAL);
    //Top 3 (tối đa 3 người); null = chưa tải hoặc tải lỗi, rỗng = bảng chưa có ai
    private final MutableLiveData<List<LeaderboardEntryResponse>> podium = new MutableLiveData<>();
    //Các dòng từ hạng 4 trở đi, tăng dần khi tải thêm trang; null = chưa tải hoặc tải lỗi
    private final MutableLiveData<List<LeaderboardEntryResponse>> rows = new MutableLiveData<>();
    private final MutableLiveData<LeaderboardMeResponse> myRank = new MutableLiveData<>();
    private final MutableLiveData<UserResponse> profile = new MutableLiveData<>();
    //true khi đang tải trang đầu (lần vào màn, đổi bảng, làm mới)
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    //true khi tải thêm trang bị lỗi: dừng tự tải cho tới khi user bấm thử lại
    private final MutableLiveData<Boolean> loadMoreFailed = new MutableLiveData<>(false);
    //Lỗi khi chưa có dữ liệu để hiển thị
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //Lỗi khi làm mới nhưng vẫn còn dữ liệu cũ trên màn; giao diện hiện một lần rồi gọi consumeRefreshError()
    private final MutableLiveData<Integer> refreshError = new MutableLiveData<>();

    //Mọi dòng đã tải của bảng đang chọn, theo thứ tự hạng
    private final List<LeaderboardEntryResponse> loaded = new ArrayList<>();
    private int nextPage;
    private boolean endReached;
    //Tăng mỗi lần đổi bảng hoặc làm mới: kết quả của request thuộc lượt cũ bị bỏ qua
    private int generation;
    private boolean profileLoading;

    public LeaderboardViewModel(@NonNull Application application) {
        super(application);
        leaderboardRepository = new LeaderboardRepository(application);
        userRepository = new UserRepository(application);
    }

    //Gọi khi màn hiện lên: chỉ tải khi chưa có dữ liệu và chưa có lỗi đang hiển thị (xoay màn hình không tải lại)
    public void loadIfNeeded() {
        if (podium.getValue() == null && errorMessage.getValue() == null && !isTrue(loading)) {
            reload();
        }
    }

    //Chọn bảng: xóa dữ liệu của bảng cũ rồi tải trang đầu của bảng mới
    public void selectPeriod(String period) {
        if (period == null || period.equals(selectedPeriod.getValue())) {
            return;
        }
        selectedPeriod.setValue(period);
        loaded.clear();
        nextPage = 0;
        endReached = false;
        podium.setValue(null);
        rows.setValue(null);
        myRank.setValue(null);
        reload();
    }

    //Kéo để làm mới hoặc bấm Thử lại: tải lại trang đầu, giữ dữ liệu cũ trên màn cho tới khi có dữ liệu mới
    public void refresh() {
        reload();
    }

    //Tải trang kế tiếp khi người dùng cuộn gần cuối danh sách
    public void loadNextPage() {
        if (podium.getValue() == null || endReached || isTrue(loading) || isTrue(loadingMore)
                || isTrue(loadMoreFailed)) {
            return;
        }
        int requestGeneration = generation;
        loadingMore.setValue(true);
        observeOnce(leaderboardRepository.top(currentPeriod(), nextPage, PAGE_SIZE), result -> {
            if (requestGeneration != generation) {
                return;
            }
            if (result.isSuccess()) {
                List<LeaderboardEntryResponse> body = result.getData();
                int received = body == null ? 0 : body.size();
                appendNewRows(body);
                nextPage++;
                endReached = received < PAGE_SIZE;
                publish();
            } else {
                loadMoreFailed.setValue(true);
            }
            loadingMore.setValue(false);
        });
    }

    //Bấm "Thử lại" ở cuối danh sách sau khi tải thêm bị lỗi
    public void retryLoadMore() {
        loadMoreFailed.setValue(false);
        loadNextPage();
    }

    public void consumeRefreshError() {
        refreshError.setValue(null);
    }

    //Tải trang đầu + hạng của user (+ hồ sơ nếu chưa có) cho bảng đang chọn
    private void reload() {
        int requestGeneration = ++generation;
        String period = currentPeriod();
        loading.setValue(true);
        loadingMore.setValue(false);
        loadMoreFailed.setValue(false);
        errorMessage.setValue(null);

        observeOnce(leaderboardRepository.top(period, 0, PAGE_SIZE), result -> {
            if (requestGeneration == generation) {
                handleFirstPage(result);
            }
        });
        observeOnce(leaderboardRepository.me(period), result -> {
            if (requestGeneration == generation && result.isSuccess() && result.getData() != null) {
                myRank.setValue(result.getData());
            }
        });
        loadProfileIfNeeded();
    }

    private void handleFirstPage(ApiResult<List<LeaderboardEntryResponse>> result) {
        loading.setValue(false);
        if (result.isSuccess()) {
            List<LeaderboardEntryResponse> body = result.getData();
            loaded.clear();
            if (body != null) {
                loaded.addAll(body);
            }
            nextPage = 1;
            endReached = loaded.size() < PAGE_SIZE;
            publish();
        } else if (podium.getValue() == null) {
            errorMessage.setValue(messageFor(result));
        } else {
            refreshError.setValue(messageFor(result));
        }
    }

    //Chỉ lấy các dòng có hạng lớn hơn dòng cuối đã tải: điểm đổi giữa hai lần tải trang có thể làm lặp dòng
    private void appendNewRows(List<LeaderboardEntryResponse> body) {
        if (body == null) {
            return;
        }
        int lastRank = loaded.isEmpty() ? 0 : loaded.get(loaded.size() - 1).rank;
        for (LeaderboardEntryResponse entry : body) {
            if (entry.rank > lastRank) {
                loaded.add(entry);
            }
        }
    }

    //Tách danh sách đã tải thành podium (3 người đầu) và các dòng còn lại
    private void publish() {
        int podiumCount = Math.min(PODIUM_SIZE, loaded.size());
        podium.setValue(new ArrayList<>(loaded.subList(0, podiumCount)));
        rows.setValue(new ArrayList<>(loaded.subList(podiumCount, loaded.size())));
    }

    //Tên và avatar cho thẻ "Vị trí của tôi" (API hạng chỉ trả hạng và điểm); chỉ tải một lần, lỗi thì thử lại ở lần tải sau
    private void loadProfileIfNeeded() {
        if (profile.getValue() != null || profileLoading) {
            return;
        }
        profileLoading = true;
        observeOnce(userRepository.me(), result -> {
            profileLoading = false;
            if (result.isSuccess() && result.getData() != null) {
                profile.setValue(result.getData());
            }
        });
    }

    //Nghe một lần rồi gỡ (giống ExamListViewModel)
    private <T> void observeOnce(LiveData<ApiResult<T>> source, Consumer<ApiResult<T>> onResult) {
        source.observeForever(new Observer<ApiResult<T>>() {
            @Override
            public void onChanged(ApiResult<T> result) {
                source.removeObserver(this);
                onResult.accept(result);
            }
        });
    }

    private String currentPeriod() {
        String period = selectedPeriod.getValue();
        return period != null ? period : PERIOD_GLOBAL;
    }

    private static boolean isTrue(LiveData<Boolean> flag) {
        return Boolean.TRUE.equals(flag.getValue());
    }

    //Đổi kết quả lỗi thành câu thông báo tiếng Việt
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
            //403 là Backend từ chối ứng dụng (sai API key / chữ ký); bảng xếp hạng không khóa theo Premium
            case 403:
                return R.string.auth_error_forbidden;
            case 429:
                return R.string.auth_error_too_many_requests;
            default:
                return R.string.auth_error_server;
        }
    }

    public LiveData<String> getSelectedPeriod() { return selectedPeriod; }
    public LiveData<List<LeaderboardEntryResponse>> getPodium() { return podium; }
    public LiveData<List<LeaderboardEntryResponse>> getRows() { return rows; }
    public LiveData<LeaderboardMeResponse> getMyRank() { return myRank; }
    public LiveData<UserResponse> getProfile() { return profile; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Boolean> getLoadingMore() { return loadingMore; }
    public LiveData<Boolean> getLoadMoreFailed() { return loadMoreFailed; }
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Integer> getRefreshError() { return refreshError; }
}