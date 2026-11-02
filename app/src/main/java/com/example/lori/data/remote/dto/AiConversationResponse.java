package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của POST /api/ai/conversation. type = "conversation_reply": chỉ có reply (câu tiếp theo của AI)
//type = "conversation_feedback": summary (tiếng Việt) và 4 danh sách, mỗi danh sách tối đa 3 mục, có thể rỗng
//Trường không dùng vắng mặt trong JSON nên là null
public class AiConversationResponse {
    public String type;
    public String reply;
    public String summary;
    public List<FeedbackItem> grammar;
    public List<FeedbackItem> vocabulary;
    public List<FeedbackItem> naturalness;
    public List<FeedbackItem> usefulPhrases;

    //original: nguyên văn của người học (null ở usefulPhrases); better: cách viết tốt hơn (ở usefulPhrases là cụm gợi ý); explanation: giải thích tiếng Việt
    public static class FeedbackItem {
        public String original;
        public String better;
        public String explanation;
    }
}