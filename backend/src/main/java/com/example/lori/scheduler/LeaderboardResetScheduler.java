package com.example.lori.scheduler;

import com.example.lori.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reset bang xep hang tuan/thang theo gio Viet Nam (mac dinh 00:00 thu Hai hang tuan, 00:00 ngay 1 hang thang).
 * Bang toan thoi gian khong bao gio bi reset. Neu job loi, Spring ghi log va van chay lan sau.
 * Lich co the doi bang bien moi truong (xem application.properties).
 */
@Component
@RequiredArgsConstructor
public class LeaderboardResetScheduler {

    // zone phai khop StreakService.ZONE
    private static final String ZONE = "Asia/Ho_Chi_Minh";

    private final LeaderboardService leaderboardService;

    @Scheduled(cron = "${app.leaderboard.weekly-reset-cron}", zone = ZONE)
    public void resetWeekly() {
        leaderboardService.resetWeekly();
    }

    @Scheduled(cron = "${app.leaderboard.monthly-reset-cron}", zone = ZONE)
    public void resetMonthly() {
        leaderboardService.resetMonthly();
    }
}