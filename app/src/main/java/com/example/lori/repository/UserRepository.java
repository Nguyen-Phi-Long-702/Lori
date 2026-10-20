package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.UserApi;
import com.example.lori.data.remote.dto.UserResponse;

//Hồ sơ của user đang đăng nhập
public class UserRepository extends BaseRepository {

    private final UserApi userApi;

    public UserRepository(Context context) {
        super(context);
        userApi = createApi(UserApi.class);
    }

    //Hồ sơ của chính user (GET /api/users/me)
    public LiveData<ApiResult<UserResponse>> me() {
        return execute(userApi.getMe());
    }
}