package com.example.lori.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Yeu cau phan tich ket qua mot lan thi; resultId la id cua exam_results (ExamResultResponse.id). */
public record AiExamAnalysisRequest(@NotNull UUID resultId) {
}