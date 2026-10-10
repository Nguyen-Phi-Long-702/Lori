package com.example.lori.data.remote;

import com.example.lori.data.local.TokenStore;
import com.example.lori.data.remote.api.AuthApi;
import com.example.lori.data.remote.dto.AuthResponse;
import com.example.lori.data.remote.dto.RefreshTokenRequest;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

//Được OkHttp gọi khi Backend trả 401: làm mới token rồi gửi lại request (tối đa 1 lần)
public class TokenAuthenticator implements Authenticator {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_ATTEMPTS = 2;

    private final TokenStore tokenStore;
    //AuthApi dựng từ OkHttpClient KHÔNG có authenticator, để lỗi 401 của chính lệnh refresh không gọi lặp lại
    private final AuthApi refreshApi;

    public TokenAuthenticator(TokenStore tokenStore, AuthApi refreshApi) {
        this.tokenStore = tokenStore;
        this.refreshApi = refreshApi;
    }

    @Override
    public Request authenticate(Route route, Response response) {
        //401 của /api/auth/* (vd sai mật khẩu khi đăng nhập) là lỗi nghiệp vụ, không phải token hết hạn
        if (response.request().url().encodedPath().startsWith(AUTH_PATH_PREFIX)) {
            return null;
        }
        //Đã thử lại một lần mà vẫn 401 thì dừng, tránh vòng lặp vô hạn
        if (responseCount(response) >= MAX_ATTEMPTS) {
            return null;
        }

        //Chỉ cho một luồng refresh tại một thời điểm: refresh token xoay vòng nên refresh song song sẽ làm hỏng nhau
        synchronized (this) {
            String requestToken = extractBearerToken(response.request());
            String currentToken = tokenStore.getAccessToken();

            //Luồng khác đã refresh xong trong lúc ta chờ khóa → dùng luôn token mới, không refresh lại
            if (currentToken != null && !currentToken.equals(requestToken)) {
                return withBearerToken(response.request(), currentToken);
            }

            String refreshToken = tokenStore.getRefreshToken();
            if (refreshToken == null) {
                return null;
            }

            retrofit2.Response<AuthResponse> refreshResponse;
            try {
                refreshResponse = refreshApi.refresh(new RefreshTokenRequest(refreshToken)).execute();
            } catch (IOException e) {
                //Lỗi mạng: giữ nguyên token, để request gốc nhận lỗi 401
                return null;
            }

            AuthResponse body = refreshResponse.body();
            if (refreshResponse.isSuccessful() && body != null) {
                //Backend xoay vòng refresh token: phải lưu CẢ HAI token mới
                tokenStore.saveTokens(body.accessToken, body.refreshToken);
                return withBearerToken(response.request(), body.accessToken);
            }
            if (refreshResponse.code() == 401) {
                //Refresh token hết hạn hoặc đã bị thu hồi → phiên đăng nhập kết thúc
                tokenStore.clear();
            }
            return null;
        }
    }

    private static int responseCount(Response response) {
        int count = 1;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }

    private static String extractBearerToken(Request request) {
        String header = request.header("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private static Request withBearerToken(Request request, String accessToken) {
        return request.newBuilder()
                .header("Authorization", BEARER_PREFIX + accessToken)
                .build();
    }
}