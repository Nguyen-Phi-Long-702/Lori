package com.example.lori.service;

/**
 * Ba bang xep hang. Moi bang la 1 Redis Sorted Set (Upstash): member = userId (UUID dang chuoi), score = EXP.
 * GLOBAL: tong EXP (khop users.total_exp). WEEKLY / MONTHLY: EXP kiem duoc tu dau tuan / dau thang (gio Viet Nam),
 * bi xoa theo lich boi LeaderboardResetScheduler.
 */
public enum LeaderboardPeriod {
    GLOBAL("leaderboard:global"),
    WEEKLY("leaderboard:weekly"),
    MONTHLY("leaderboard:monthly");

    private final String redisKey;

    LeaderboardPeriod(String redisKey) {
        this.redisKey = redisKey;
    }

    public String getRedisKey() {
        return redisKey;
    }
}