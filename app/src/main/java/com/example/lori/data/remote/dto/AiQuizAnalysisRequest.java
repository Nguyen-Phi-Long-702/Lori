package com.example.lori.data.remote.dto;

//Body của POST /api/ai/analyze-quiz. quizType nhận "vocabulary" hoặc "grammar"; itemId là id chủ đề / bài ngữ pháp
public class AiQuizAnalysisRequest {
    public final String quizType;
    public final int itemId;

    public AiQuizAnalysisRequest(String quizType, int itemId) {
        this.quizType = quizType;
        this.itemId = itemId;
    }
}