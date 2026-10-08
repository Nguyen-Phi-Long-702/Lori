package com.example.lori.dto;

import java.util.UUID;

/** Mot de thi trong danh sach (chua co cau hoi). examType: TOEIC/IELTS; difficulty: EASY/MEDIUM/HARD. */
public record ExamSummaryResponse(
        UUID id,
        String examType,
        String title,
        String difficulty,
        int durationMinutes,
        int totalQuestions) {
}