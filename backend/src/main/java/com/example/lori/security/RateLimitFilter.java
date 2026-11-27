package com.example.lori.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limit theo IP: toi da 60 request/phut. Vuot -> 429.
 * Bo qua SecurityPaths.OPEN_PATHS (de UptimeRobot khong tieu ton lenh Redis).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 60;
    private static final String KEY_PREFIX = "rate_limit:";

    private final ProxyManager<String> proxyManager;
    private final int trustedProxyCount;
    private final BucketConfiguration configuration = BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(REQUESTS_PER_MINUTE)
                    .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)))
            .build();

    public RateLimitFilter(ProxyManager<String> proxyManager, int trustedProxyCount) {
        if (trustedProxyCount < 1) {
            throw new IllegalArgumentException("app.security.trusted-proxy-count phai >= 1");
        }
        this.proxyManager = proxyManager;
        this.trustedProxyCount = trustedProxyCount;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.isOpenPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = proxyManager.getProxy(KEY_PREFIX + getClientIp(request), () -> configuration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        JsonErrorWriter.write(response, 429, "Too many requests");
    }

    // Proxy noi them IP ket noi that vao CUOI X-Forwarded-For; cac phan tu ben trai do client tu viet nen khong tin.
    // IP khach = phan tu thu trustedProxyCount tinh tu cuoi (TRUSTED_PROXY_COUNT, xem INFRA.md muc 43)
    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0) {
                return parts[Math.max(parts.length - trustedProxyCount, 0)].trim();
            }
        }
        return request.getRemoteAddr();
    }
}