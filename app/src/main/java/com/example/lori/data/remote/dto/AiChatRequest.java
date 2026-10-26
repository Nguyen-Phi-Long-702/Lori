package com.example.lori.data.remote.dto;

//Body của POST /api/ai/chat. message tối đa 4.000 ký tự ở Backend; AI Service cắt còn 1.000 ký tự sau khi làm sạch
public class AiChatRequest {
    public final String message;

    public AiChatRequest(String message) {
        this.message = message;
    }
}