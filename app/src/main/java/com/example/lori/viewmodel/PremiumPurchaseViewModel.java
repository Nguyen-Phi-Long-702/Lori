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
import com.example.lori.data.remote.dto.SubscriptionResponse;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.PaymentRepository;
import com.example.lori.utils.NetworkStateManager;
import com.example.lori.utils.PremiumManager;

//Quản lý màn mua Premium: chọn gói, gọi mua giả lập (mock-purchase), báo thành công/lỗi
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class PremiumPurchaseViewModel extends AndroidViewModel {

    //Giá trị planType Backend nhận (PlanType: MONTHLY, YEARLY, LIFETIME)
    public static final String PLAN_MONTHLY = "MONTHLY";
    public static final String PLAN_YEARLY = "YEARLY";
    public static final String PLAN_LIFETIME = "LIFETIME";

    private final PaymentRepository paymentRepository;
    private final LiveData<Boolean> online;

    //Prototype chọn sẵn Gói Năm (gói phổ biến nhất)
    private final MutableLiveData<String> selectedPlan = new MutableLiveData<>(PLAN_YEARLY);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> purchased = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //Nút Mua chỉ bấm được khi có mạng và không đang xử lý
    private final MediatorLiveData<Boolean> purchaseEnabled = new MediatorLiveData<>();

    public PremiumPurchaseViewModel(@NonNull Application application) {
        super(application);
        paymentRepository = new PaymentRepository(application);
        online = NetworkStateManager.getInstance(application).getOnlineState();
        purchaseEnabled.addSource(online, value -> updatePurchaseEnabled());
        purchaseEnabled.addSource(loading, value -> updatePurchaseEnabled());
    }

    //Đổi gói đang chọn (không đổi khi đang xử lý giao dịch)
    public void selectPlan(String planType) {
        if (isBusy()) {
            return;
        }
        selectedPlan.setValue(planType);
    }

    //Bấm nút Mua: gọi POST /api/payment/mock-purchase cho gói đang chọn
    public void purchase() {
        if (isBusy() || Boolean.TRUE.equals(purchased.getValue())) {
            return;
        }
        errorMessage.setValue(null);
        loading.setValue(true);

        LiveData<ApiResult<SubscriptionResponse>> source = paymentRepository.mockPurchase(selectedPlan.getValue());
        //Nghe một lần rồi gỡ. Dùng observeForever (không gắn vòng đời màn hình) để nếu người dùng thoát màn hình
        //giữa chừng thì kết quả vẫn được xử lý và cờ Premium vẫn được ghi khi Backend đã tính tiền xong
        source.observeForever(new Observer<ApiResult<SubscriptionResponse>>() {
            @Override
            public void onChanged(ApiResult<SubscriptionResponse> result) {
                source.removeObserver(this);
                handleResult(result);
            }
        });
    }

    private void handleResult(ApiResult<SubscriptionResponse> result) {
        loading.setValue(false);
        if (result.isSuccess()) {
            //Backend đã bật Premium cho user trong cùng giao dịch: ghi cờ ngay để giao diện cập nhật
            PremiumManager.setPremium(getApplication(), true);
            purchased.setValue(true);
            return;
        }
        errorMessage.setValue(messageFor(result));
    }

    //Đổi kết quả lỗi thành câu thông báo tiếng Việt
    @StringRes
    private static int messageFor(ApiResult<?> result) {
        switch (result.getStatus()) {
            case OFFLINE:
                return R.string.premium_offline_note;
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
            case 403:
                return R.string.premium_error_forbidden;
            case 409:
                return R.string.premium_error_already_lifetime;
            case 429:
                return R.string.auth_error_too_many_requests;
            default:
                return R.string.auth_error_server;
        }
    }

    private void updatePurchaseEnabled() {
        boolean isOnline = Boolean.TRUE.equals(online.getValue());
        purchaseEnabled.setValue(isOnline && !isBusy());
    }

    private boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    public LiveData<String> getSelectedPlan() { return selectedPlan; }
    public LiveData<Boolean> getLoading() { return loading; }
    //true sau khi mua thành công → giao diện chuyển sang khối "Chào mừng Premium!"
    public LiveData<Boolean> getPurchased() { return purchased; }
    //Lỗi hiện dưới nút Mua; null = không có lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPurchaseEnabled() { return purchaseEnabled; }
    public LiveData<Boolean> getOnline() { return online; }
}