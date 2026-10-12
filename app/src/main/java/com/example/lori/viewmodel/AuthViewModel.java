package com.example.lori.viewmodel;

import android.app.Application;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.R;
import com.example.lori.data.local.SessionManager;
import com.example.lori.data.remote.RetrofitClient;
import com.example.lori.data.remote.api.AuthApi;
import com.example.lori.data.remote.dto.AuthResponse;
import com.example.lori.data.remote.dto.LoginRequest;
import com.example.lori.data.remote.dto.RegisterRequest;
import com.example.lori.data.remote.dto.GoogleLoginRequest;
import com.example.lori.data.remote.dto.RefreshTokenRequest;
import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.sync.SyncManager;
import com.example.lori.utils.PremiumManager;
import com.example.lori.utils.NetworkStateManager;

import java.net.SocketTimeoutException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//Quản lý nghiệp vụ của màn Đăng nhập/Đăng ký: kiểm tra dữ liệu nhập, gọi AuthApi, lưu token vào SessionManager
//Giao diện chỉ quan sát các LiveData bên dưới; mọi thông báo lỗi (từng ô nhập và lỗi chung) đều là mã chuỗi R.string (null = không lỗi)
public class AuthViewModel extends AndroidViewModel {

    //Giới hạn khớp với Backend (RegisterRequest: displayName tối đa 100, password 8-72 ký tự)
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final SessionManager sessionManager;
    private final AuthApi authApi;
    private final NetworkStateManager networkStateManager;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> authenticated = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> formError = new MutableLiveData<>();
    private final MutableLiveData<Integer> nameError = new MutableLiveData<>();
    private final MutableLiveData<Integer> emailError = new MutableLiveData<>();
    private final MutableLiveData<Integer> passwordError = new MutableLiveData<>();
    private final MutableLiveData<Integer> confirmPasswordError = new MutableLiveData<>();

    //Request đang chạy; hủy khi ViewModel bị xóa để không cập nhật vào màn hình đã đóng
    private Call<AuthResponse> pendingCall;

    public AuthViewModel(@NonNull Application application) {
        super(application);
        sessionManager = SessionManager.getInstance(application);
        //SessionManager là TokenStore thật: chỉ lần getInstance đầu tiên của RetrofitClient dùng tới nó
        authApi = RetrofitClient.getInstance(application, sessionManager).create(AuthApi.class);
        networkStateManager = NetworkStateManager.getInstance(application);
    }

    //Đăng nhập bằng email + mật khẩu
    public void login(String email, String password) {
        if (isBusy()) {
            return;
        }
        clearErrors();
        String cleanEmail = email == null ? "" : email.trim();
        boolean emailOk = validateEmail(cleanEmail);
        boolean passwordOk = validateLoginPassword(password);
        if (!emailOk || !passwordOk) {
            return;
        }
        send(authApi.login(new LoginRequest(cleanEmail, password)),
                R.string.auth_error_invalid_credentials);
    }

    //Đăng ký tài khoản mới; thành công là có phiên đăng nhập luôn (Backend trả token ngay)
    public void register(String displayName, String email, String password, String confirmPassword) {
        if (isBusy()) {
            return;
        }
        clearErrors();
        String cleanName = displayName == null ? "" : displayName.trim();
        String cleanEmail = email == null ? "" : email.trim();
        boolean nameOk = validateDisplayName(cleanName);
        boolean emailOk = validateEmail(cleanEmail);
        boolean passwordOk = validateNewPassword(password);
        boolean confirmOk = validateConfirmPassword(password, confirmPassword);
        if (!nameOk || !emailOk || !passwordOk || !confirmOk) {
            return;
        }
        send(authApi.register(new RegisterRequest(cleanName, cleanEmail, password)),
                R.string.auth_error_invalid_request);
    }

    //Bước 1 của đăng nhập Google: View sắp mở bảng chọn tài khoản. Trả về false nếu đang bận (chống bấm 2 lần)
    public boolean startGoogleSignIn() {
        if (isBusy()) {
            return false;
        }
        clearErrors();
        loading.setValue(true);
        return true;
    }

    //Người dùng đóng bảng chọn tài khoản: không coi là lỗi
    public void cancelGoogleSignIn() {
        loading.setValue(false);
    }

    //Lấy ID Token thất bại (chưa có tài khoản Google, lỗi cấu hình…)
    public void failGoogleSignIn(@StringRes int messageRes) {
        loading.setValue(false);
        formError.setValue(messageRes);
    }

    //Bước 2: gửi ID Token cho Backend đổi lấy JWT của Lori
    public void loginWithGoogle(String idToken) {
        send(authApi.google(new GoogleLoginRequest(idToken)), R.string.auth_error_google_failed);
    }

    //Đăng xuất: xóa phiên trên máy NGAY (luôn thành công kể cả khi mất mạng), rồi báo Backend thu hồi refresh token
    public void logout() {
        String refreshToken = sessionManager.getRefreshToken();
        sessionManager.clear();

        //Dọn dẹp dữ liệu gắn với tài khoản vừa thoát để tài khoản đăng nhập sau không bị lẫn dữ liệu:
        //hủy lịch đồng bộ, đặt lại cờ Premium, xóa tiến trình học cục bộ (đăng nhập lại sẽ pull về từ server)
        SyncManager.cancel(getApplication());
        PremiumManager.setPremium(getApplication(), false);
        AppDatabase db = AppDatabase.getInstance(getApplication());
        //Dùng executor của Room (không phải của ViewModel) vì ViewModel bị hủy ngay khi màn hình đóng
        db.getQueryExecutor().execute(() -> db.progressDao().deleteAll());

        if (refreshToken == null) {
            return;
        }
        //Cố ý KHÔNG lưu vào pendingCall: màn hình đóng ngay sau khi đăng xuất, nếu hủy theo ViewModel thì Backend không nhận được yêu cầu
        authApi.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                //Bỏ qua kết quả: phiên trên máy đã xóa; token còn lại ở Backend tự hết hạn sau 7 ngày
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                //Bỏ qua (vd đang offline), lý do như trên
            }
        });
    }

    //Gửi request, xử lý kết quả. unauthorizedMessage: câu hiển thị khi Backend trả 401 (mỗi chức năng một ý nghĩa)
    private void send(Call<AuthResponse> apiCall, @StringRes int unauthorizedMessage) {
        if (!networkStateManager.isOnline()) {
            loading.setValue(false);
            formError.setValue(R.string.auth_error_offline);
            return;
        }
        loading.setValue(true);
        pendingCall = apiCall;
        apiCall.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                pendingCall = null;
                AuthResponse body = response.body();
                if (response.isSuccessful() && body != null
                        && body.accessToken != null && body.refreshToken != null) {
                    sessionManager.saveTokens(body.accessToken, body.refreshToken);
                    //Giữ loading = true: màn hình sắp chuyển sang MainActivity, tránh nút bật lại trong chốc lát
                    authenticated.setValue(true);
                    return;
                }
                loading.setValue(false);
                formError.setValue(messageForStatus(response.code(), unauthorizedMessage));
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                pendingCall = null;
                loading.setValue(false);
                formError.setValue(t instanceof SocketTimeoutException
                        ? R.string.auth_error_timeout
                        : R.string.auth_error_network);
            }
        });
    }

    //Đổi mã HTTP của Backend thành câu thông báo tiếng Việt
    @StringRes
    private static int messageForStatus(int httpCode, @StringRes int unauthorizedMessage) {
        switch (httpCode) {
            case 400:
                return R.string.auth_error_invalid_request;
            case 401:
                return unauthorizedMessage;
            case 403:
                return R.string.auth_error_forbidden;
            case 409:
                return R.string.auth_error_email_taken;
            case 429:
                return R.string.auth_error_too_many_requests;
            default:
                return R.string.auth_error_server;
        }
    }

    private boolean validateDisplayName(String name) {
        if (name.isEmpty()) {
            nameError.setValue(R.string.auth_error_name_required);
            return false;
        }
        if (name.length() > MAX_DISPLAY_NAME_LENGTH) {
            nameError.setValue(R.string.auth_error_name_too_long);
            return false;
        }
        return true;
    }

    private boolean validateEmail(String email) {
        if (email.isEmpty()) {
            emailError.setValue(R.string.auth_error_email_required);
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError.setValue(R.string.auth_error_email_invalid);
            return false;
        }
        return true;
    }

    //Đăng nhập chỉ cần có nhập mật khẩu; đúng/sai do Backend quyết định
    private boolean validateLoginPassword(String password) {
        if (password == null || password.isEmpty()) {
            passwordError.setValue(R.string.auth_error_password_required);
            return false;
        }
        return true;
    }

    private boolean validateNewPassword(String password) {
        if (password == null || password.isEmpty()) {
            passwordError.setValue(R.string.auth_error_password_required);
            return false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordError.setValue(R.string.auth_error_password_short);
            return false;
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            passwordError.setValue(R.string.auth_error_password_long);
            return false;
        }
        return true;
    }

    private boolean validateConfirmPassword(String password, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            confirmPasswordError.setValue(R.string.auth_error_confirm_required);
            return false;
        }
        if (!confirmPassword.equals(password)) {
            confirmPasswordError.setValue(R.string.auth_error_confirm_mismatch);
            return false;
        }
        return true;
    }

    private boolean isBusy() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    private void clearErrors() {
        formError.setValue(null);
        nameError.setValue(null);
        emailError.setValue(null);
        passwordError.setValue(null);
        confirmPasswordError.setValue(null);
    }

    @Override
    protected void onCleared() {
        if (pendingCall != null) {
            pendingCall.cancel();
        }
    }

    public LiveData<Boolean> getLoading() { return loading; }
    //true khi đã có token và đã lưu xong → giao diện chuyển sang MainActivity
    public LiveData<Boolean> getAuthenticated() { return authenticated; }
    //Lỗi chung (mạng, máy chủ, sai mật khẩu…); null = không có lỗi
    public LiveData<Integer> getFormError() { return formError; }
    public LiveData<Integer> getNameError() { return nameError; }
    public LiveData<Integer> getEmailError() { return emailError; }
    public LiveData<Integer> getPasswordError() { return passwordError; }
    public LiveData<Integer> getConfirmPasswordError() { return confirmPasswordError; }
}