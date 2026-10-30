package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của POST /api/ai/analyze-quiz (type = "quiz_analysis"). Số liệu do Backend đọc từ kết quả quiz đã đồng bộ; summary, patterns, recommendations do AI viết
public class AiQuizAnalysisResponse {
    public String type;
    public String title;
    public int correctCount;
    public int incorrectCount;
    public int accuracyPercent;
    public String summary;
    public List<String> patterns;
    public List<AiRecommendation> recommendations;
}