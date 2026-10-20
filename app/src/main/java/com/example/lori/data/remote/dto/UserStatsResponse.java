package com.example.lori.data.remote.dto;

//Kết quả GET /api/users/me/stats. totalExp, level, currentStreak do Backend tính (streak theo giờ Việt Nam)
//Gson bỏ qua các trường Backend trả mà ở đây không khai báo (vd longestStreak)
public class UserStatsResponse {
    public long completedGrammarLessons;
    public long completedVocabQuizTopics;
    public long completedGrammarQuizLessons;
    public long totalCorrect;
    public long totalIncorrect;
    public int totalExp;
    public int level;
    public int currentStreak;
}