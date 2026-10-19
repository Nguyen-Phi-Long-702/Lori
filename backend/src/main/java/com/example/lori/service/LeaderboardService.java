package com.example.lori.service;

import com.example.lori.dto.LeaderboardEntryResponse;
import com.example.lori.dto.LeaderboardMeResponse;
import com.example.lori.entity.User;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bang xep hang (Redis Sorted Set tren Upstash): ghi diem, doc top N, doc vi tri cua 1 user.
 * Chi nhan/tra du lieu, khong biet gi ve HTTP. Du lieu EXP goc nam o PostgreSQL (users.total_exp);
 * Redis chi giu ban sap xep san de xep hang nhanh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    /** So dong toi da moi lan lay (chong keo ca bang ve trong 1 request). */
    static final int MAX_PAGE_SIZE = 50;

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    // ===== Ghi diem =====

    /**
     * Cong amount EXP vao ca 3 bang (global, weekly, monthly) cho user. ExpService goi ham nay SAU khi transaction DB commit.
     * Redis loi thi chi ghi log, khong nem loi: EXP da luu o DB roi, khong de su co Redis lam hong request cua user.
     */
    public void updateScore(UUID userId, int amount) {
        if (amount <= 0) {
            return;
        }
        String member = userId.toString();
        try {
            for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
                redisTemplate.opsForZSet().incrementScore(period.getRedisKey(), member, amount);
            }
        } catch (RuntimeException e) {
            log.warn("Khong cap nhat duoc bang xep hang cho userId={}: {}", userId, e.toString());
        }
    }

    // ===== Doc bang =====

    /**
     * Lay mot trang cua bang xep hang, diem cao nhat truoc. page bat dau tu 0; moi trang size dong (1..50).
     * Hang cua dong dau trang = page * size + 1. User khong con trong DB bi bo qua.
     */
    public List<LeaderboardEntryResponse> getTopN(LeaderboardPeriod period, int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        long start = (long) page * size;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(period.getRedisKey(), start, start + size - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        // Lay ten + avatar cua ca trang bang 1 truy van
        List<UUID> userIds = tuples.stream().map(tuple -> UUID.fromString(tuple.getValue())).toList();
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<LeaderboardEntryResponse> entries = new ArrayList<>();
        long rank = start;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            rank++;
            User user = users.get(UUID.fromString(tuple.getValue()));
            if (user == null) {
                continue;
            }
            long score = tuple.getScore() == null ? 0L : Math.round(tuple.getScore());
            entries.add(new LeaderboardEntryResponse((int) rank, user.getDisplayName(), user.getAvatarUrl(), score));
        }
        return entries;
    }

    /** Hang (bat dau tu 1) va diem cua user trong bang; chua co diem thi rank = null, score = 0. */
    public LeaderboardMeResponse getUserRank(LeaderboardPeriod period, UUID userId) {
        String member = userId.toString();
        Long rank = redisTemplate.opsForZSet().reverseRank(period.getRedisKey(), member);
        if (rank == null) {
            return new LeaderboardMeResponse(null, 0L);
        }
        Double score = redisTemplate.opsForZSet().score(period.getRedisKey(), member);
        return new LeaderboardMeResponse(rank.intValue() + 1, score == null ? 0L : Math.round(score));
    }
}