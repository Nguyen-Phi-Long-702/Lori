package com.example.lori.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BruteForceProtectionServiceTest {

    private static final String EMAIL = "linh@example.com";
    private static final String KEY = "login_attempts:linh@example.com";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final BruteForceProtectionService service = new BruteForceProtectionService(redisTemplate);

    @BeforeEach
    void redisReturnsValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void emailWithoutFailuresIsNotLocked() {
        when(valueOps.get(KEY)).thenReturn(null);

        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void fourFailuresDoNotLockYet() {
        when(valueOps.get(KEY)).thenReturn("4");

        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void fiveFailuresLockTheEmail() {
        when(valueOps.get(KEY)).thenReturn("5");

        assertThat(service.isLocked(EMAIL)).isTrue();
    }

    @Test
    void recordFailureCountsAndRefreshesTheFifteenMinuteWindow() {
        service.recordFailure(EMAIL);

        verify(valueOps).increment(KEY);
        verify(redisTemplate).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    void resetDeletesTheCounter() {
        service.reset(EMAIL);

        verify(redisTemplate).delete(KEY);
    }
}