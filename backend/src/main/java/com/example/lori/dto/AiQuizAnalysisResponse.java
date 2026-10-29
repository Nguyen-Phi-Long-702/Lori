package com.example.lori.dto;

import java.util.List;

/**
 * Ket qua POST /api/ai/analyze-quiz: type = "quiz_analysis". title, correctCount, incorrectCount, accuracyPercent la
 * ket qua lan lam gan nhat do Backend doc tu user_progress; summary, patterns, recommendations do AI viet.
 */
public record AiQuizAnalysisResponse(
        String type,
        String title,
        int correctCount,
        int incorrectCount,
        int accuracyPercent,
        String summary,
        List<String> patterns,
        List<AiAnalysisResponse.Recommendation> recommendations) {
}