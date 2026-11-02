package com.example.lori.data.remote.dto;

import java.util.List;

//Body của POST /api/ai/conversation. messages là toàn bộ các lượt từ đầu buổi (server không lưu gì), tối đa 30; rỗng khi nhờ AI mở đầu
//finish = true để nhận nhận xét cuối buổi thay vì câu trả lời tiếp theo
public class AiConversationRequest {
    public final String mode;
    public final List<Turn> messages;
    public final boolean finish;

    public AiConversationRequest(String mode, List<Turn> messages, boolean finish) {
        this.mode = mode;
        this.messages = messages;
        this.finish = finish;
    }

    //role là "user" (người học) hoặc "assistant" (AI)
    public static class Turn {
        public final String role;
        public final String content;

        public Turn(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}