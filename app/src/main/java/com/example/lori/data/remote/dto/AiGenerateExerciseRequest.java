package com.example.lori.data.remote.dto;

//Body của POST /api/ai/generate-exercise. category nhận "grammar" hoặc "vocabulary"; topicId là id bài ngữ pháp / chủ đề từ vựng
public class AiGenerateExerciseRequest {
    public final String category;
    public final int topicId;

    public AiGenerateExerciseRequest(String category, int topicId) {
        this.category = category;
        this.topicId = topicId;
    }
}