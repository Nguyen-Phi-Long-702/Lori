package com.example.lori.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Bai lam gui len: sessionToken nhan duoc tu /start + cac dap an. Toi da 500 cau/lan (de TOEIC day du co 200 cau).
 * Cau bo trong thi khong gui, hoac gui answer rong/null: tinh la sai.
 */
public record SubmitExamRequest(
        @NotBlank @Size(max = 100) String sessionToken,
        @NotNull @Size(max = 500) List<@NotNull @Valid Answer> answers) {

    public record Answer(
            @NotNull UUID questionId,
            @Size(max = 255) String answer) {
    }
}