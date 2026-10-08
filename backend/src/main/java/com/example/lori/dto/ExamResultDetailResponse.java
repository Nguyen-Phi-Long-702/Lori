package com.example.lori.dto;

import java.util.List;
import java.util.UUID;

/** Ket qua + xem lai tung cau (dap an dung, dap an user chon, giai thich) theo thu tu trong de. */
public record ExamResultDetailResponse(
        ExamResultResponse result,
        List<Item> items) {

    public record Item(
            UUID questionId,
            String questionType,
            String questionText,
            List<String> options,
            String yourAnswer,
            String correctAnswer,
            boolean correct,
            String explanation) {
    }
}