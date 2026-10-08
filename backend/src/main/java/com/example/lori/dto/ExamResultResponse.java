package com.example.lori.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ket qua mot lan thi. Diem TOEIC: moi ky nang 5-495, tong 10-990. Diem IELTS: band 0-9 (buoc 0.5),
 * tong = band trung binh Listening + Reading.
 */
public record ExamResultResponse(
        UUID id,
        UUID paperId,
        String examType,
        String title,
        int correctCount,
        int totalQuestions,
        int listeningCorrect,
        int listeningTotal,
        int readingCorrect,
        int readingTotal,
        BigDecimal listeningScore,
        BigDecimal readingScore,
        BigDecimal totalScore,
        int timeSpentSeconds,
        Instant submittedAt) {
}