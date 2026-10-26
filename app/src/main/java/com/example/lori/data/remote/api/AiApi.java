package com.example.lori.data.remote.api;

import com.example.lori.data.remote.dto.AiAnalyzeTextRequest;
import com.example.lori.data.remote.dto.AiChatRequest;
import com.example.lori.data.remote.dto.AiChatResponse;
import com.example.lori.data.remote.dto.AiGrammarResponse;

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
}