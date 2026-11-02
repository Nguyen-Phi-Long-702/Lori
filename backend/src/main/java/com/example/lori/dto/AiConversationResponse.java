package com.example.lori.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Phan hoi hoi thoai. type = "conversation_reply": chi co reply (cau tiep theo cua AI).
 * type = "conversation_feedback": summary (tieng Viet) va 4 danh sach, moi danh sach toi da 3 muc, co the rong.
 * Cung la dang JSON ma AI Service tra ve cho Backend.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiConversationResponse(
        String type,
        String reply,
        String summary,
        List<FeedbackItem> grammar,
        List<FeedbackItem> vocabulary,
        List<FeedbackItem> naturalness,
        List<FeedbackItem> usefulPhrases) {

    /**
     * original = nguyen van cau/cum cua hoc vien (null o usefulPhrases), better = cach viet tot hon
     * (o usefulPhrases la cum tu goi y), explanation = giai thich tieng Viet.
     */
    public record FeedbackItem(String original, String better, String explanation) {
    }
}