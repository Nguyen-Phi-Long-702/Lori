package com.example.lori.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yeu cau chat voi AI Tutor. Toi da 4.000 ky tu, bang muc cat tho (MAX_RAW_LENGTH) cua AI Service;
 * duoi muc do AI Service lam sach roi cat con toi da 1.000 ky tu.
 */
public record AiChatRequest(@NotBlank @Size(max = 4000) String message) {
}