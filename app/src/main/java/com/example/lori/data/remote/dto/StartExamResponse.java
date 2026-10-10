package com.example.lori.data.remote.dto;

//Kết quả POST /api/exams/{examId}/start: gửi lại sessionToken khi nộp bài (dùng 1 lần)
public class StartExamResponse {
    public String sessionToken;
}