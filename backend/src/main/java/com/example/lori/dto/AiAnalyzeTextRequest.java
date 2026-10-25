package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeu cau phan tich cau cua user. type hien chi nhan "grammar". Toi da 4.000 ky tu (cung muc voi AiChatRequest);
 * AI Service lam sach roi cat con toi da 1.000 ky tu.
 */
public record AiAnalyzeTextRequest(
        @NotBlank @Pattern(regexp = "grammar") String type,
        @NotBlank @Size(max = 4000) String text) {
}