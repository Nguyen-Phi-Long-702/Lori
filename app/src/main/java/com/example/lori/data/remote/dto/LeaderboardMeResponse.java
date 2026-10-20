package com.example.lori.data.remote.dto;

//Kết quả GET /api/leaderboard/{period}/me. rank = null và score = 0 khi user chưa có điểm trong bảng đó
public class LeaderboardMeResponse {
    public Integer rank;
    public long score;
}