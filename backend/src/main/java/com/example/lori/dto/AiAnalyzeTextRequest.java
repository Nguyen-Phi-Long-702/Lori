package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeu cau phan tich van ban cua user. type nhan "grammar" (cau can kiem tra ngu phap) hoac "vocabulary"
 * (1 hoac 2 tu/cum tu can giai nghia, cach nhau bang dau phay, dau gach cheo hoac "vs").
 * Toi da 4.000 ky tu (cung muc voi AiChatRequest); AI Service lam sach roi cat con toi da 1.000 ky tu.
 */
public record AiAnalyzeTextRequest(
        @NotBlank @Pattern(regexp = "grammar|vocabulary") String type,
        @NotBlank @Size(max = 4000) String text) {
}