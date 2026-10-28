package com.example.lori.dto;

/**
 * Mot chu de dang theo doi: category = "grammar" / "vocabulary"; topicKey = id bai ngu phap / chu de tu vung;
 * accuracyPercent = ty le tra loi dung cong don (0-100, lam tron xuong).
 */
public record LearningTopicResponse(
        String category,
        String topicKey,
        String topicName,
        int correctCount,
        int incorrectCount,
        int accuracyPercent) {
}