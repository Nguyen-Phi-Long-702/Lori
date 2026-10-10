package com.example.lori.data.remote;

import com.example.lori.data.local.TokenStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

//Gắn header cho mọi request: X-API-Key + X-App-Signature (lớp anti-abuse của Backend),
//và Authorization: Bearer <access token> nếu user đã đăng nhập
public class AuthInterceptor implements Interceptor {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final TokenStore tokenStore;
    private final String apiKey;
    private final String appSignature;

    public AuthInterceptor(TokenStore tokenStore, String apiKey, String appSignature) {
        this.tokenStore = tokenStore;
        this.apiKey = apiKey;
        this.appSignature = appSignature;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder()
                .header("X-API-Key", apiKey)
                .header("X-App-Signature", appSignature);

        //Các endpoint /api/auth/* không cần JWT (đăng nhập, refresh...) nên không gắn Authorization
        if (!original.url().encodedPath().startsWith(AUTH_PATH_PREFIX)) {
            String accessToken = tokenStore.getAccessToken();
            if (accessToken != null) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
        }
        return chain.proceed(builder.build());
    }
}