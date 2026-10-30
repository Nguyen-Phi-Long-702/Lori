package com.example.lori.data.remote.dto;

//Một đề xuất ôn tập do AI viết (dùng chung cho phân tích quiz, phân tích bài thi và ôn tập hôm nay)
public class AiRecommendation {
    public String title;
    public String reason;
    //Id bài ngữ pháp liên quan trong dictionary.db của Lori; null nếu không có bài phù hợp
    public Integer lessonId;
}