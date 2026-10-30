package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của POST /api/ai/recommend (type = "recommendation"). Chưa có điểm yếu nào thì topics và recommendations rỗng, summary = null
public class AiRecommendResponse {
    public String type;
    //Tối đa 3 chủ đề yếu nhất do Backend chọn, yếu nhất trước
    public List<LearningTopicResponse> topics;
    public String summary;
    public List<AiRecommendation> recommendations;
}