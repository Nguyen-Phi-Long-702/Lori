package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.AiAnalyzeTextRequest;
import com.example.lori.data.remote.dto.AiChatRequest;
import com.example.lori.data.remote.dto.AiChatResponse;
import com.example.lori.data.remote.dto.AiExamAnalysisRequest;
import com.example.lori.data.remote.dto.AiExamAnalysisResponse;
import com.example.lori.data.remote.dto.AiExerciseResponse;
import com.example.lori.data.remote.dto.AiGenerateExerciseRequest;
import com.example.lori.data.remote.dto.AiGrammarResponse;
import com.example.lori.data.remote.dto.AiQuizAnalysisRequest;
import com.example.lori.data.remote.dto.AiQuizAnalysisResponse;
import com.example.lori.data.remote.dto.AiRecommendResponse;
import com.example.lori.data.remote.dto.AiImageResponse;

import okhttp3.MultipartBody;

import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

//Các endpoint /api/ai/* của Backend (Premium: user Free nhận 403 PREMIUM_REQUIRED)
public interface AiApi {

    @POST("api/ai/chat")
    Call<AiChatResponse> chat(@Body AiChatRequest request);

    //Chỉ dùng type = "grammar"; type = "vocabulary" trả về dạng khác nên chưa có DTO
    @POST("api/ai/analyze-text")
    Call<AiGrammarResponse> analyzeGrammar(@Body AiAnalyzeTextRequest request);

    @POST("api/ai/analyze-quiz")
    Call<AiQuizAnalysisResponse> analyzeQuiz(@Body AiQuizAnalysisRequest request);

    @POST("api/ai/analyze-exam")
    Call<AiExamAnalysisResponse> analyzeExam(@Body AiExamAnalysisRequest request);

    @POST("api/ai/generate-exercise")
    Call<AiExerciseResponse> generateExercise(@Body AiGenerateExerciseRequest request);

    //Không có body: Backend lấy điểm yếu theo user đang đăng nhập
    @POST("api/ai/recommend")
    Call<AiRecommendResponse> recommend();

    //Gửi ảnh JPEG (trường "image", tối đa 3 MB) để nhận diện vật thể
    @Multipart
    @POST("api/ai/identify-image")
    Call<AiImageResponse> identifyImage(@Part MultipartBody.Part image);
}