package com.example.lori.data.remote.dto;

import java.util.List;

//Body của POST /api/exams/{examId}/submit. Tối đa 500 câu; câu bỏ trống thì không gửi (hoặc answer rỗng) = sai
public class SubmitExamRequest {
    public final String sessionToken;
    public final List<Answer> answers;

    public SubmitExamRequest(String sessionToken, List<Answer> answers) {
        this.sessionToken = sessionToken;
        this.answers = answers;
    }

    public static class Answer {
        public final String questionId;
        public final String answer;

        public Answer(String questionId, String answer) {
            this.questionId = questionId;
            this.answer = answer;
        }
    }
}