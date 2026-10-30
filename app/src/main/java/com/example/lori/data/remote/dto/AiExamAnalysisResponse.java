package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của POST /api/ai/analyze-exam (type = "exam_analysis"). parts do Backend tính (phần yếu nhất trước); summary, patterns, recommendations do AI viết
public class AiExamAnalysisResponse {
    public String type;
    public String resultId;
    public List<PartStat> parts;
    public String summary;
    public List<String> patterns;
    public List<AiRecommendation> recommendations;

    //Một phần của đề, vd "Part 5 - Incomplete Sentences"; skill = LISTENING hoặc READING
    public static class PartStat {
        public String name;
        public String skill;
        public int correctCount;
        public int totalCount;
        public int accuracyPercent;
    }
}