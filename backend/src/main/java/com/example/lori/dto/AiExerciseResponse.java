package com.example.lori.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Bo bai tap do AI sinh: type = "exercise_set". Cung la dang JSON ma AI Service tra ve cho Backend.
 * Moi bai co dang cua 1 trong 2 loai; truong khong dung den thi vang mat trong JSON (null).
 */
public record AiExerciseResponse(String type, List<Exercise> exercises) {

    /**
     * type = "multiple_choice": options (4 lua chon) va answerIndex (vi tri dap an dung, tinh tu 0).
     * type = "fill_blank": question co dung 1 cho trong "___" va answers la cac dap an chap nhan (1-3).
     * Cung la noi dung luu o cot content (JSONB) cua ai_generated_exercise.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Exercise(
            String type,
            String question,
            List<String> options,
            Integer answerIndex,
            List<String> answers,
            String explanation) {
    }
}