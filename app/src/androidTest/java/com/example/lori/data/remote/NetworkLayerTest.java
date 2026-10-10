package com.example.lori.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.lori.data.local.TokenStore;
import com.example.lori.data.remote.api.AuthApi;
import com.example.lori.data.remote.api.ExamApi;
import com.example.lori.data.remote.api.UserApi;
import com.example.lori.data.remote.dto.AuthResponse;
import com.example.lori.data.remote.dto.ExamSummaryResponse;
import com.example.lori.data.remote.dto.RegisterRequest;
import com.example.lori.data.remote.dto.UserResponse;
import com.example.lori.utils.NetworkStateManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

//Test chạy trên thiết bị và GỌI BACKEND RENDER THẬT (cần Internet, Backend đang chạy)
@RunWith(AndroidJUnit4.class)
public class NetworkLayerTest {

    private static final String TEST_PASSWORD = "Test@12345";

    //RetrofitClient là singleton nên chỉ giữ TokenStore của lần tạo đầu tiên → dùng chung một TokenStore cho cả lớp test
    private static final InMemoryTokenStore TOKEN_STORE = new InMemoryTokenStore();

    private Context context;
    private AuthApi authApi;
    private UserApi userApi;
    private ExamApi examApi;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        TOKEN_STORE.clear();
        RetrofitClient client = RetrofitClient.getInstance(context, TOKEN_STORE);
        authApi = client.create(AuthApi.class);
        userApi = client.create(UserApi.class);
        examApi = client.create(ExamApi.class);
    }

    @Test
    public void registerThenGetMe_returnsTheSameEmail() throws IOException {
        String email = newTestEmail();
        registerAndStoreTokens(email);

        Response<UserResponse> response = userApi.getMe().execute();

        assertEquals("GET /api/users/me phải trả 200", 200, response.code());
        assertNotNull(response.body());
        assertEquals(email, response.body().email);
        assertFalse(response.body().premium);
    }

    @Test
    public void expiredAccessToken_isRefreshedAutomatically() throws IOException {
        AuthResponse tokens = registerAndStoreTokens(newTestEmail());
        //Giả lập access token hết hạn: thay bằng chuỗi không hợp lệ, giữ nguyên refresh token thật
        TOKEN_STORE.saveTokens("invalid.access.token", tokens.refreshToken);

        Response<UserResponse> response = userApi.getMe().execute();

        //401 → TokenAuthenticator refresh → gửi lại → 200
        assertEquals("Phải tự refresh rồi gửi lại và nhận 200", 200, response.code());
        assertNotEquals("invalid.access.token", TOKEN_STORE.getAccessToken());
        //Backend xoay vòng refresh token nên refresh token mới phải khác token cũ
        assertNotEquals(tokens.refreshToken, TOKEN_STORE.getRefreshToken());
    }

    @Test
    public void freeUser_listExams_isRejectedWith403() throws IOException {
        registerAndStoreTokens(newTestEmail());

        Response<List<ExamSummaryResponse>> response = examApi.list().execute();

        //User Free gọi API Premium → 403 PREMIUM_REQUIRED (xác nhận cả đường dẫn /api/exams đúng)
        assertEquals(403, response.code());
    }

    @Test
    public void networkStateManager_reportsOnline_whenDeviceHasInternet() {
        assertTrue(NetworkStateManager.getInstance(context).isOnline());
    }

    private AuthResponse registerAndStoreTokens(String email) throws IOException {
        Response<AuthResponse> response =
                authApi.register(new RegisterRequest("Android Tester", email, TEST_PASSWORD)).execute();
        assertEquals("Register phải trả 201 (nếu 403: kiểm tra API_KEY trong local.properties và APP_SIGNATURE trên Render)",
                201, response.code());
        AuthResponse tokens = response.body();
        assertNotNull(tokens);
        TOKEN_STORE.saveTokens(tokens.accessToken, tokens.refreshToken);
        return tokens;
    }

    private static String newTestEmail() {
        return "androidtest." + System.currentTimeMillis() + "@example.com";
    }

    //TokenStore trong bộ nhớ, chỉ dùng cho test (bản thật là SessionManager ở Ngày 3-4)
    private static class InMemoryTokenStore implements TokenStore {
        private String accessToken;
        private String refreshToken;

        @Override
        public synchronized String getAccessToken() {
            return accessToken;
        }

        @Override
        public synchronized String getRefreshToken() {
            return refreshToken;
        }

        @Override
        public synchronized void saveTokens(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        @Override
        public synchronized void clear() {
            accessToken = null;
            refreshToken = null;
        }
    }
}