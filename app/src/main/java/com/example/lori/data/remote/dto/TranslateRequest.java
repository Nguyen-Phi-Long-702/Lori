package com.example.lori.data.remote.dto;

//Body của POST /api/translate. Tối đa 500 ký tự; sourceLang/targetLang chỉ nhận "en" hoặc "vi" và phải khác nhau
public class TranslateRequest {
    public final String text;
    public final String sourceLang;
    public final String targetLang;

    public TranslateRequest(String text, String sourceLang, String targetLang) {
        this.text = text;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
    }
}