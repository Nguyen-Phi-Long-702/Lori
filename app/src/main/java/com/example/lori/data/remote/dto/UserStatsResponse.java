package com.example.lori.data.remote.dto;

//Kết quả GET /api/users/me/stats
public class UserStatsResponse {
    public long completedGrammarLessons;
    public long completedVocabQuizTopics;
    public long completedGrammarQuizLessons;
    public long totalCorrect;
    public long totalIncorrect;
}