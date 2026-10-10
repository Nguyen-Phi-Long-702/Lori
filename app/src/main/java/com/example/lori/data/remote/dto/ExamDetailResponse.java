package com.example.lori.data.remote.dto;

import java.util.List;

//Nội dung đề để LÀM BÀI (GET /api/exams/{examId}): không có đáp án đúng và giải thích
public class ExamDetailResponse {
    public String id;
    public String examType;
    public String title;
    public String difficulty;
    public int durationMinutes;
    public List<Section> sections;

    public static class Section {
        public String id;
        public String skill;
        public String title;
        public int orderIndex;
        public String audioUrl;
        public String passageText;
        public List<Question> questions;
    }

    public static class Question {
        public String id;
        public int orderIndex;
        public String questionType;
        public String questionText;
        public List<String> options;
    }
}