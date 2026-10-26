package com.example.lori.data.remote.dto;

//Body của POST /api/ai/analyze-text. type nhận "grammar" hoặc "vocabulary"; text tối đa 4.000 ký tự ở Backend
public class AiAnalyzeTextRequest {
    public final String type;
    public final String text;

    public AiAnalyzeTextRequest(String type, String text) {
        this.type = type;
        this.text = text;
    }
}