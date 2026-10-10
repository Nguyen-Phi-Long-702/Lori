package com.example.lori.data.remote.dto;

//Kết quả một lần thi. TOEIC: mỗi kỹ năng 5-495, tổng 10-990. IELTS: band 0-9
public class ExamResultResponse {
    public String id;
    public String paperId;
    public String examType;
    public String title;
    public int correctCount;
    public int totalQuestions;
    public int listeningCorrect;
    public int listeningTotal;
    public int readingCorrect;
    public int readingTotal;
    public double listeningScore;
    public double readingScore;
    public double totalScore;
    public int timeSpentSeconds;
    public String submittedAt;
}