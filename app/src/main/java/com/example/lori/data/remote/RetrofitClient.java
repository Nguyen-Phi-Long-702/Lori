package com.example.lori.data.remote;

import android.content.Context;

import com.example.lori.BuildConfig;
import com.example.lori.data.local.TokenStore;
import com.example.lori.data.remote.api.AuthApi;
import com.example.lori.utils.ApiKeyProvider;
import com.example.lori.utils.AppSignatureHelper;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

//Điểm duy nhất tạo các *Api (Retrofit). Singleton: toàn app dùng chung một OkHttpClient
public final class RetrofitClient {

    //Render Free có thể ngủ và Neon tự ngủ (INFRA.md) nên request đầu tiên có thể chậm: để timeout rộng
    private static final long TIMEOUT_SECONDS = 30;

    private static volatile RetrofitClient instance;

    private final Retrofit retrofit;

    //tokenStore chỉ được dùng ở lần tạo đầu tiên (Ngày 3-4 truyền SessionManager vào)
    public static RetrofitClient getInstance(Context context, TokenStore tokenStore) {
        if (instance == null) {
            synchronized (RetrofitClient.class) {
                if (instance == null) {
                    instance = new RetrofitClient(context.getApplicationContext(), tokenStore);
                }
            }
        }
        return instance;
    }

    private RetrofitClient(Context context, TokenStore tokenStore) {
        String apiKey = ApiKeyProvider.getApiKey();
        String appSignature = AppSignatureHelper.getSha256(context);

        //Client không có authenticator: chỉ dùng để gọi /api/auth/refresh bên trong TokenAuthenticator
        OkHttpClient refreshClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(tokenStore, apiKey, appSignature))
                .build();
        AuthApi refreshApi = buildRetrofit(refreshClient).create(AuthApi.class);

        //Client chính: dùng chung connection pool với refreshClient, thêm authenticator xử lý 401
        OkHttpClient mainClient = refreshClient.newBuilder()
                .authenticator(new TokenAuthenticator(tokenStore, refreshApi))
                .build();
        retrofit = buildRetrofit(mainClient);
    }

    private static Retrofit buildRetrofit(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    //Dùng: RetrofitClient.getInstance(context, tokenStore).create(UserApi.class)
    public <T> T create(Class<T> apiClass) {
        return retrofit.create(apiClass);
    }
}