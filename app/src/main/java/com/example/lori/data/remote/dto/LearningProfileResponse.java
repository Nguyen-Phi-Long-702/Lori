package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của GET /api/users/me/learning-profile. estimatedLevel: A1, A2, B1, B2 hoặc C1
public class LearningProfileResponse {
    public String estimatedLevel;
    //Chủ đề đã thành thạo, tỉ lệ đúng cao trước
    public List<LearningTopicResponse> strengths;
    //Chủ đề đang yếu, tỉ lệ đúng thấp trước
    public List<LearningTopicResponse> weaknesses;
}