package com.example.lori.data.remote.dto;

//Một mục tiến trình trên server, trả về từ GET /api/progress/pull
public class ProgressItemResponse {
    public String itemType;
    public int itemId;
    public String status;
    public int correctCount;
    public int incorrectCount;
    public String lastStudiedAt;
}