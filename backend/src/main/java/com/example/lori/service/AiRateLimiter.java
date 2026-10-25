package com.example.lori.service;

import com.example.lori.exception.ApiException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Rate limit rieng cho tac vu AI, theo tung user (khac RateLimitFilter gioi han theo IP): chat toi da 20 request/phut.
 * Dung chung ProxyManager (Upstash) voi RateLimitFilter.
 */
@Component
public class AiRateLimiter {

    private static final int CHAT_REQUESTS_PER_MINUTE = 20;
    private static final String CHAT_KEY_PREFIX = "ai_rate_limit:chat:";

    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration chatConfiguration = BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(CHAT_REQUESTS_PER_MINUTE)
                    .refillGreedy(CHAT_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)))
            .build();

    public AiRateLimiter(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /** Tru 1 luot chat cua user; het luot thi nem 429. */
    public void checkChat(UUID userId) {
        Bucket bucket = proxyManager.getProxy(CHAT_KEY_PREFIX + userId, () -> chatConfiguration);
        if (!bucket.tryConsume(1)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        }
    }
}