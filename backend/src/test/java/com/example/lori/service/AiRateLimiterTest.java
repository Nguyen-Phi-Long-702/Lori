package com.example.lori.service;

import com.example.lori.exception.ApiException;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRateLimiterTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void chatIsLimitedPerUser() {
        UUID userId = UUID.randomUUID();
        BucketProxy bucket = mock(BucketProxy.class);
        when(bucket.tryConsume(1)).thenReturn(true, false);
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        when(proxyManager.getProxy(eq("ai_rate_limit:chat:" + userId), any(Supplier.class))).thenReturn(bucket);
        AiRateLimiter limiter = new AiRateLimiter(proxyManager);

        limiter.checkChat(userId);

        assertThatThrownBy(() -> limiter.checkChat(userId)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void analyzeTextHasItsOwnBucketPerUser() {
        UUID userId = UUID.randomUUID();
        BucketProxy bucket = mock(BucketProxy.class);
        when(bucket.tryConsume(1)).thenReturn(true, false);
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        when(proxyManager.getProxy(eq("ai_rate_limit:analyze-text:" + userId), any(Supplier.class))).thenReturn(bucket);
        AiRateLimiter limiter = new AiRateLimiter(proxyManager);

        limiter.checkAnalyzeText(userId);

        assertThatThrownBy(() -> limiter.checkAnalyzeText(userId)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void analyzeQuizHasItsOwnBucketPerUser() {
        UUID userId = UUID.randomUUID();
        BucketProxy bucket = mock(BucketProxy.class);
        when(bucket.tryConsume(1)).thenReturn(true, false);
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        when(proxyManager.getProxy(eq("ai_rate_limit:analyze-quiz:" + userId), any(Supplier.class))).thenReturn(bucket);
        AiRateLimiter limiter = new AiRateLimiter(proxyManager);

        limiter.checkAnalyzeQuiz(userId);

        assertThatThrownBy(() -> limiter.checkAnalyzeQuiz(userId)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void analyzeExamHasItsOwnBucketPerUser() {
        UUID userId = UUID.randomUUID();
        BucketProxy bucket = mock(BucketProxy.class);
        when(bucket.tryConsume(1)).thenReturn(true, false);
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        when(proxyManager.getProxy(eq("ai_rate_limit:analyze-exam:" + userId), any(Supplier.class))).thenReturn(bucket);
        AiRateLimiter limiter = new AiRateLimiter(proxyManager);

        limiter.checkAnalyzeExam(userId);

        assertThatThrownBy(() -> limiter.checkAnalyzeExam(userId)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
    }
}