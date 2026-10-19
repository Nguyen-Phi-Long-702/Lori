package com.example.lori.dto;

/** Vi tri cua chinh user trong mot bang. rank = null va score = 0 neu user chua co diem trong bang do. */
public record LeaderboardMeResponse(
        Integer rank,
        long score) {
}