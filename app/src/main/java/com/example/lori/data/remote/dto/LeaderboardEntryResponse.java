package com.example.lori.data.remote.dto;

//Một dòng của bảng xếp hạng (GET /api/leaderboard/{period}). rank bắt đầu từ 1; score là EXP của bảng đó
public class LeaderboardEntryResponse {
    public int rank;
    public String displayName;
    public String avatarUrl;
    public long score;
}