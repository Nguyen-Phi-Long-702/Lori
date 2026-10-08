package com.example.lori.dto;

import java.util.List;
import java.util.UUID;

/**
 * Noi dung day du cua mot de de LAM BAI: section -> cau hoi. KHONG co dap an dung va giai thich
 * (chi tra sau khi nop bai, trong ExamResultDetailResponse).
 */
public record ExamDetailResponse(
        UUID id,
        String examType,
        String title,
        String difficulty,
        int durationMinutes,
        List<Section> sections) {

    public record Section(
            UUID id,
            String skill,
            String title,
            int orderIndex,
            String audioUrl,
            String passageText,
            List<Question> questions) {
    }

    public record Question(
            UUID id,
            int orderIndex,
            String questionType,
            String questionText,
            List<String> options) {
    }
}