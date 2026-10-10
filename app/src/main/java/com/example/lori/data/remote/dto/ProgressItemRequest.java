package com.example.lori.data.remote.dto;

//Một mục tiến trình gửi lên server. lastStudiedAt là chuỗi ISO-8601 (vd 2026-10-10T04:59:40Z)
public class ProgressItemRequest {
    public final String itemType;
    public final int itemId;
    public final String status;
    public final int correctCount;
    public final int incorrectCount;
    public final String lastStudiedAt;

    public ProgressItemRequest(String itemType, int itemId, String status,
                               int correctCount, int incorrectCount, String lastStudiedAt) {
        this.itemType = itemType;
        this.itemId = itemId;
        this.status = status;
        this.correctCount = correctCount;
        this.incorrectCount = incorrectCount;
        this.lastStudiedAt = lastStudiedAt;
    }
}