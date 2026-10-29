package com.example.lori.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ket qua POST /api/ai/analyze-exam: type = "exam_analysis". parts la thong ke tung phan do Backend tinh
 * (yeu nhat truoc); summary, patterns, recommendations do AI viet.
 */
public record AiExamAnalysisResponse(
        String type,
        UUID resultId,
        List<PartStat> parts,
        String summary,
        List<String> patterns,
        List<AiAnalysisResponse.Recommendation> recommendations) {

    /** Mot phan cua de (vd "Part 5 - Incomplete Sentences"); skill = LISTENING / READING. */
    public record PartStat(String name, String skill, int correctCount, int totalCount, int accuracyPercent) {
    }
}