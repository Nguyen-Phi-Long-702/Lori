package com.example.lori.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Yeu cau mot luot hoi thoai role-play. Server khong luu gi: client gui lai toan bo cac luot tu dau buoi
 * (toi da 30 tin, tinh ca hai ben; rong khi nho AI mo dau buoi).
 * finish = true: ket thuc buoi va nhan nhan xet thay vi cau tra loi tiep theo.
 * Ma mode phai khop CONVERSATION_MODES ben AI Service va ConversationMode.java ben Android.
 */
public record AiConversationRequest(
        @NotBlank @Pattern(regexp = "daily|teacher|partner|restaurant|airport|interview|ielts_speaking") String mode,
        @NotNull @Size(max = 30) List<@NotNull @Valid Turn> messages,
        boolean finish) {

    /** role = "user" (hoc vien) hoac "assistant" (AI). Toi da 4.000 ky tu, cung muc voi AiChatRequest. */
    public record Turn(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 4000) String content) {
    }
}