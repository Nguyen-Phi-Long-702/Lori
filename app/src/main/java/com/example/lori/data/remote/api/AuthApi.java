package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.AuthResponse;
import com.example.lori.data.remote.dto.ForgotPasswordRequest;
import com.example.lori.data.remote.dto.GoogleLoginRequest;
import com.example.lori.data.remote.dto.LoginRequest;
import com.example.lori.data.remote.dto.RefreshTokenRequest;
import com.example.lori.data.remote.dto.RegisterRequest;
import com.example.lori.data.remote.dto.ResetPasswordRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

//Các endpoint /api/auth/* của Backend (đường dẫn tương đối so với BASE_URL, không bắt đầu bằng /)
public interface AuthApi {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("api/auth/google")
    Call<AuthResponse> google(@Body GoogleLoginRequest request);

    @POST("api/auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshTokenRequest request);

    //Backend trả 204 không có body nên dùng Void
    @POST("api/auth/logout")
    Call<Void> logout(@Body RefreshTokenRequest request);

    @POST("api/auth/forgot-password")
    Call<Void> forgotPassword(@Body ForgotPasswordRequest request);

    @POST("api/auth/reset-password")
    Call<Void> resetPassword(@Body ResetPasswordRequest request);
}