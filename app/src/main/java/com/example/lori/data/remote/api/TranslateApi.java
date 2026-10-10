package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.TranslateRequest;
import com.example.lori.data.remote.dto.TranslateResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

//Endpoint dịch EN-VI của Backend (tính năng Free, chỉ cần đăng nhập)
public interface TranslateApi {

    @POST("api/translate")
    Call<TranslateResponse> translate(@Body TranslateRequest request);
}