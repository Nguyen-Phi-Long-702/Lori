package com.example.lori.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Tao/kiem tra Access Token (JWT) va sinh gia tri Refresh Token.
 * Access Token: sub = userId, iss, aud, iat, exp. Ky HMAC bang JWT_SECRET.
 */
@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32; // 256-bit

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final JwtParser parser;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.jwt.refresh-token-days}") long refreshTokenDays) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET phai dai it nhat 32 byte (256-bit)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenDays);
        // Parser kiem tra chu ky, het han, iss va aud
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build();
    }

    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** Tra ve userId neu token hop le (dung chu ky, con han, dung iss/aud); nguoc lai rong. */
    public Optional<UUID> getUserIdFromAccessToken(String token) {
        try {
            String subject = parser.parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    /** Refresh Token: 32 byte ngau nhien (256-bit), ma hoa Base64 URL-safe. */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}