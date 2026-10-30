package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Yeu cau sinh bai tap cho 1 chu de. category nhan "grammar" (topicId la id bai ngu phap) hoac "vocabulary"
 * (topicId la id chu de tu vung), cung cap id voi user_weakness_record.topic_key va learning-names.json.
 */
public record AiGenerateExerciseRequest(
        @NotBlank @Pattern(regexp = "grammar|vocabulary") String category,
        @NotNull @Positive Integer topicId) {
}