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
 * Rate limit rieng cho tac vu AI, theo tung user (khac RateLimitFilter gioi han theo IP):
 * chat va phan tich cau (analyze-text), moi loai toi da 20 request/phut.
 * Dung chung ProxyManager (Upstash) voi RateLimitFilter.
 */
@Component
public class AiRateLimiter {

    private static final int REQUESTS_PER_MINUTE = 20;
    private static final String CHAT_KEY_PREFIX = "ai_rate_limit:chat:";
    private static final String ANALYZE_TEXT_KEY_PREFIX = "ai_rate_limit:analyze-text:";

    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration configuration = BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(REQUESTS_PER_MINUTE)
                    .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)))
            .build();

    public AiRateLimiter(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /** Tru 1 luot chat cua user; het luot thi nem 429. */
    public void checkChat(UUID userId) {
        consume(CHAT_KEY_PREFIX + userId);
    }

    /** Tru 1 luot phan tich cau (analyze-text) cua user; het luot thi nem 429. */
    public void checkAnalyzeText(UUID userId) {
        consume(ANALYZE_TEXT_KEY_PREFIX + userId);
    }

    private void consume(String key) {
        Bucket bucket = proxyManager.getProxy(key, () -> configuration);
        if (!bucket.tryConsume(1)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        }
    }
}