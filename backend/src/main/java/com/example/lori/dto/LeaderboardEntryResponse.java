package com.example.lori.dto;

/**
 * Mot dong trong bang xep hang. rank bat dau tu 1; score la EXP (tong EXP voi bang global,
 * EXP kiem duoc trong tuan/thang voi bang weekly/monthly).
 */
public record LeaderboardEntryResponse(
        int rank,
        String displayName,
        String avatarUrl,
        long score) {
}