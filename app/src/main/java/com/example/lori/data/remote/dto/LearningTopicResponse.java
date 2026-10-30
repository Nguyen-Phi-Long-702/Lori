package com.example.lori.data.remote.dto;

//Một chủ đề đang được theo dõi (GET /api/users/me/learning-profile, POST /api/ai/recommend)
public class LearningTopicResponse {
    //"grammar" hoặc "vocabulary"
    public String category;
    //Id bài ngữ pháp / chủ đề từ vựng, dạng chuỗi số
    public String topicKey;
    public String topicName;
    public int correctCount;
    public int incorrectCount;
    //Tỉ lệ đúng cộng dồn, 0-100
    public int accuracyPercent;
}