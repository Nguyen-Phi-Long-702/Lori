package com.example.lori.dto;

import java.util.List;

/**
 * Ket qua POST /api/ai/recommend: type = "recommendation". topics la toi da 3 chu de yeu do Learning Engine chon
 * (yeu nhat truoc, so lieu do Backend tinh); summary va recommendations do AI viet.
 * Chua co diem yeu nao thi topics va recommendations rong, summary = null va khong goi AI.
 */
public record AiRecommendResponse(
        String type,
        List<LearningTopicResponse> topics,
        String summary,
        List<AiAnalysisResponse.Recommendation> recommendations) {
}