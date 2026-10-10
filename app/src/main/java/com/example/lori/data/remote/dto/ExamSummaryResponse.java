package com.example.lori.data.remote.dto;

//Một đề thi trong danh sách GET /api/exams. examType: TOEIC/IELTS; difficulty: EASY/MEDIUM/HARD
public class ExamSummaryResponse {
    public String id;
    public String examType;
    public String title;
    public String difficulty;
    public int durationMinutes;
    public int totalQuestions;
}