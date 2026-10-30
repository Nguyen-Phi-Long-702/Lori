package com.example.lori.data.remote.dto;

//Body của POST /api/ai/analyze-exam. resultId là id kết quả bài thi (ExamResultResponse.id)
public class AiExamAnalysisRequest {
    public final String resultId;

    public AiExamAnalysisRequest(String resultId) {
        this.resultId = resultId;
    }
}