package com.example.lori.data.remote.dto;

import java.util.List;

//Kết quả + xem lại từng câu (GET /api/exams/results/{resultId})
public class ExamResultDetailResponse {
    public ExamResultResponse result;
    public List<Item> items;

    public static class Item {
        public String questionId;
        public String questionType;
        public String questionText;
        public List<String> options;
        public String yourAnswer;
        public String correctAnswer;
        public boolean correct;
        public String explanation;
    }
}