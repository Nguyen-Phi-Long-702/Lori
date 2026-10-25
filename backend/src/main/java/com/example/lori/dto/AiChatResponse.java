package com.example.lori.dto;

/**
 * Phan hoi chat: type = "chat", answer la cau tra loi cua AI Tutor,
 * truncated = true khi cau tra loi bi cat vi cham gioi han do dai.
 * Cung la dang JSON ma AI Service tra ve cho Backend.
 */
public record AiChatResponse(String type, String answer, boolean truncated) {
}