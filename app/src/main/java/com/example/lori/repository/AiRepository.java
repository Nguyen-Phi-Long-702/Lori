package com.example.lori.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.lori.data.remote.api.AiApi;
import com.example.lori.data.remote.dto.AiAnalyzeTextRequest;
import com.example.lori.data.remote.dto.AiChatRequest;
import com.example.lori.data.remote.dto.AiChatResponse;
import com.example.lori.data.remote.dto.AiGrammarResponse;

//Gọi AI Tutor qua Backend (Premium). User Free nhận HTTP_ERROR 403 với ApiResult.isPremiumRequired() = true
public class AiRepository extends BaseRepository {

    private static final String TYPE_GRAMMAR = "grammar";

    private final AiApi aiApi;

    public AiRepository(Context context) {
        super(context);
        aiApi = createApi(AiApi.class);
    }

    //Hỏi đáp tự do (POST /api/ai/chat)
    public LiveData<ApiResult<AiChatResponse>> chat(String message) {
        return execute(aiApi.chat(new AiChatRequest(message)));
    }

    //Kiểm tra ngữ pháp một câu (POST /api/ai/analyze-text, type = grammar)
    public LiveData<ApiResult<AiGrammarResponse>> analyzeGrammar(String text) {
        return execute(aiApi.analyzeGrammar(new AiAnalyzeTextRequest(TYPE_GRAMMAR, text)));
    }
}