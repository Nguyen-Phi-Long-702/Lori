package com.example.lori.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.data.local.SessionManager;
import com.example.lori.data.remote.RetrofitClient;
import com.example.lori.data.remote.dto.ApiErrorResponse;
import com.example.lori.utils.NetworkStateManager;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.net.SocketTimeoutException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//Lớp cha của các Repository: tạo API từ RetrofitClient và chạy một Call thành LiveData<ApiResult<T>>
public abstract class BaseRepository {

    private static final Gson GSON = new Gson();

    private final Context appContext;
    private final NetworkStateManager networkStateManager;

    protected BaseRepository(Context context) {
        appContext = context.getApplicationContext();
        networkStateManager = NetworkStateManager.getInstance(appContext);
    }

    //SessionManager là TokenStore thật; RetrofitClient là singleton nên chỉ lần getInstance đầu tiên dùng tới nó
    protected <A> A createApi(Class<A> apiClass) {
        return RetrofitClient.getInstance(appContext, SessionManager.getInstance(appContext)).create(apiClass);
    }

    //Gọi API bất đồng bộ. Phải gọi từ luồng chính (ViewModel); kết quả được đặt vào LiveData cũng ở luồng chính
    protected <T> LiveData<ApiResult<T>> execute(Call<T> call) {
        MutableLiveData<ApiResult<T>> result = new MutableLiveData<>();
        if (!networkStateManager.isOnline()) {
            result.setValue(ApiResult.offline());
            return result;
        }
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                if (response.isSuccessful()) {
                    result.setValue(ApiResult.success(response.body(), response.code()));
                } else {
                    result.setValue(ApiResult.httpError(response.code(), readErrorMessage(response)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
                if (call.isCanceled()) {
                    return;
                }
                result.setValue(t instanceof SocketTimeoutException
                        ? ApiResult.timeout()
                        : ApiResult.networkError());
            }
        });
        return result;
    }

    //Đọc trường "message" trong body lỗi {"status":..., "message":"..."}; không đọc được thì trả null
    private static String readErrorMessage(Response<?> response) {
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody == null) {
                return null;
            }
            ApiErrorResponse parsed = GSON.fromJson(errorBody.charStream(), ApiErrorResponse.class);
            return parsed == null ? null : parsed.message;
        } catch (JsonParseException e) {
            return null;
        }
    }
}