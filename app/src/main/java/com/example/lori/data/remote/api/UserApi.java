package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.ChangePasswordRequest;
import com.example.lori.data.remote.dto.LearningProfileResponse;
import com.example.lori.data.remote.dto.UpdateProfileRequest;
import com.example.lori.data.remote.dto.UserResponse;
import com.example.lori.data.remote.dto.UserStatsResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

//Các endpoint /api/users/* của Backend (cần đăng nhập — AuthInterceptor tự gắn JWT)
public interface UserApi {

    @GET("api/users/me")
    Call<UserResponse> getMe();

    @PUT("api/users/me")
    Call<UserResponse> updateMe(@Body UpdateProfileRequest request);

    @GET("api/users/me/stats")
    Call<UserStatsResponse> getStats();

    @GET("api/users/me/learning-profile")
    Call<LearningProfileResponse> getLearningProfile();

    @PUT("api/users/me/password")
    Call<Void> changePassword(@Body ChangePasswordRequest request);
}