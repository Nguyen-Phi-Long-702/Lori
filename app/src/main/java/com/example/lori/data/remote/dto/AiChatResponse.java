package com.example.lori.data.remote.dto;

//Phản hồi của POST /api/ai/chat. type luôn là "chat"; truncated = true khi câu trả lời bị cắt vì quá dài
public class AiChatResponse {
    public String type;
    public String answer;
    public boolean truncated;
}