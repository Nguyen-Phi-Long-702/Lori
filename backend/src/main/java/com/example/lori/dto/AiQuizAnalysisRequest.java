package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Yeu cau phan tich ket qua mot quiz da lam. quizType nhan "vocabulary" (quiz theo chu de tu vung) hoac
 * "grammar" (quiz theo bai ngu phap); itemId la id chu de / bai ngu phap, giong item_id cua user_progress.
 */
public record AiQuizAnalysisRequest(
        @NotBlank @Pattern(regexp = "vocabulary|grammar") String quizType,
        @NotNull @Positive Integer itemId) {
}