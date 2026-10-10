package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeu cau dich: toi da 500 ky tu (khop gioi han o app). Chi ho tro "en" va "vi";
 * hai ngon ngu phai khac nhau (kiem tra o TranslateService).
 */
public record TranslateRequest(
        @NotBlank @Size(max = 500) String text,
        @NotBlank @Pattern(regexp = "en|vi") String sourceLang,
        @NotBlank @Pattern(regexp = "en|vi") String targetLang) {
}