package com.example.lori.data.remote;

import android.content.Context;

import com.example.lori.BuildConfig;
import com.example.lori.data.local.TokenStore;
import com.example.lori.data.remote.api.AuthApi;
import com.example.lori.utils.ApiKeyProvider;
import com.example.lori.utils.AppSignatureHelper;

import java.util.concurrent.TimeUnit;

import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

//Điểm duy nhất tạo các *Api (Retrofit). Singleton: toàn app dùng chung một OkHttpClient
public final class RetrofitClient {

    //Render Free có thể ngủ và Neon tự ngủ (INFRA.md) nên request đầu tiên có thể chậm: để timeout rộng
    private static final long TIMEOUT_SECONDS = 30;

    //Khóa công khai (SPKI SHA-256) của các root CA mà Render dùng (Let's Encrypt, Google Trust Services); chuỗi chứng chỉ chỉ cần khớp một khóa
    //Lấy bằng tools/security-audit/https_audit.py; nếu Render đổi sang CA khác thì phải cập nhật danh sách và phát hành lại app
    private static final String[] ROOT_CA_PINS = {
            "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=",
            "sha256/QXnt2YHvdHR3tJYmQIr0Paosp6t/nggsEGD4QJZ3Q0g=",
            "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
            "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
            "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="
    };

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
                .certificatePinner(buildCertificatePinner())
                .addInterceptor(new AuthInterceptor(tokenStore, apiKey, appSignature))
                .build();
        AuthApi refreshApi = buildRetrofit(refreshClient).create(AuthApi.class);

        //Client chính: dùng chung connection pool với refreshClient, thêm authenticator xử lý 401
        OkHttpClient mainClient = refreshClient.newBuilder()
                .authenticator(new TokenAuthenticator(tokenStore, refreshApi))
                .build();
        retrofit = buildRetrofit(mainClient);
    }

    //Chỉ ghim host của BASE_URL (Render); mainClient dựng từ refreshClient nên thừa hưởng luôn
    private static CertificatePinner buildCertificatePinner() {
        String host = HttpUrl.get(BuildConfig.BASE_URL).host();
        return new CertificatePinner.Builder().add(host, ROOT_CA_PINS).build();
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