package com.example.lori.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Chong brute-force dang nhap: 5 lan sai -> khoa 15 phut.
 * Bo dem luu Redis (Upstash), key login_attempts:{email}. Email phai da duoc chuan hoa (chu thuong).
 */
@Service
@RequiredArgsConstructor
public class BruteForceProtectionService {

    private static final String KEY_PREFIX = "login_attempts:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String email) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        return value != null && Long.parseLong(value) >= MAX_FAILED_ATTEMPTS;
    }

    public void recordFailure(String email) {
        String key = KEY_PREFIX + email;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOCK_DURATION);
    }

    public void reset(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }
}