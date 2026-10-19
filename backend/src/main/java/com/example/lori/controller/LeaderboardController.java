package com.example.lori.controller;

import com.example.lori.dto.LeaderboardEntryResponse;
import com.example.lori.dto.LeaderboardMeResponse;
import com.example.lori.exception.ApiException;
import com.example.lori.service.LeaderboardPeriod;
import com.example.lori.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint /api/leaderboard/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()), user Free cung dung duoc
 * (khong gan @RequirePremium). {period} = global | weekly | monthly (khong phan biet hoa/thuong).
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /** Mot trang cua bang xep hang: page bat dau tu 0, size 1..50 (mac dinh 20). */
    @GetMapping("/{period}")
    public List<LeaderboardEntryResponse> top(@PathVariable String period,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return leaderboardService.getTopN(parsePeriod(period), page, size);
    }

    /** Hang va diem cua chinh user dang dang nhap ("Vi tri cua toi"). */
    @GetMapping("/{period}/me")
    public LeaderboardMeResponse me(@AuthenticationPrincipal UUID userId, @PathVariable String period) {
        return leaderboardService.getUserRank(parsePeriod(period), userId);
    }

    private static LeaderboardPeriod parsePeriod(String value) {
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            if (period.name().equalsIgnoreCase(value)) {
                return period;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid period, use global, weekly or monthly");
    }
}