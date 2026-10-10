package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.ProgressItemResponse;
import com.example.lori.data.remote.dto.SyncProgressRequest;
import com.example.lori.data.remote.dto.SyncProgressResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

//Các endpoint /api/progress/* của Backend
public interface ProgressApi {

    @POST("api/progress/sync")
    Call<SyncProgressResponse> sync(@Body SyncProgressRequest request);

    @GET("api/progress/pull")
    Call<List<ProgressItemResponse>> pull();
}