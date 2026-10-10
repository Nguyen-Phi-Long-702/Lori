package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.ExamDetailResponse;
import com.example.lori.data.remote.dto.ExamResultDetailResponse;
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.data.remote.dto.ExamSummaryResponse;
import com.example.lori.data.remote.dto.StartExamResponse;
import com.example.lori.data.remote.dto.SubmitExamRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

//Các endpoint /api/exams/* của Backend. Chỉ Premium dùng được: user Free nhận 403 PREMIUM_REQUIRED
public interface ExamApi {

    @GET("api/exams")
    Call<List<ExamSummaryResponse>> list();

    @GET("api/exams/{examId}")
    Call<ExamDetailResponse> detail(@Path("examId") String examId);

    @POST("api/exams/{examId}/start")
    Call<StartExamResponse> start(@Path("examId") String examId);

    @POST("api/exams/{examId}/submit")
    Call<ExamResultResponse> submit(@Path("examId") String examId, @Body SubmitExamRequest request);

    @GET("api/exams/results")
    Call<List<ExamResultResponse>> results();

    @GET("api/exams/results/{resultId}")
    Call<ExamResultDetailResponse> resultDetail(@Path("resultId") String resultId);
}