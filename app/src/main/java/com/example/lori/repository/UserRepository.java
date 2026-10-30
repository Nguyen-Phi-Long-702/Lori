package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.UserApi;
import com.example.lori.data.remote.dto.LearningProfileResponse;
import com.example.lori.data.remote.dto.UserResponse;
import com.example.lori.data.remote.dto.UserStatsResponse;

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

    //Thống kê học tập kèm tổng EXP, cấp độ, streak (GET /api/users/me/stats)
    public LiveData<ApiResult<UserStatsResponse>> stats() {
        return execute(userApi.getStats());
    }

    //Trình độ ước tính, điểm mạnh và điểm yếu (GET /api/users/me/learning-profile)
    public LiveData<ApiResult<LearningProfileResponse>> learningProfile() {
        return execute(userApi.getLearningProfile());
    }
}