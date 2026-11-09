package com.example.lori.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-jwt-secret-for-unit-test-0123456789";
    private static final String OTHER_SECRET = "another-jwt-secret-for-unit-test-9876543210";
    private static final String ISSUER = "lori";
    private static final String AUDIENCE = "lori-app";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, ISSUER, AUDIENCE, 15, 7);
    private final UUID userId = UUID.randomUUID();

    @Test
    void accessTokenReturnsTheUserIdItWasIssuedFor() {
        String token = provider.generateAccessToken(userId);

        assertThat(provider.getUserIdFromAccessToken(token)).contains(userId);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtTokenProvider(OTHER_SECRET, ISSUER, AUDIENCE, 15, 7).generateAccessToken(userId);

        assertThat(provider.getUserIdFromAccessToken(token)).isEmpty();
    }

    @Test
    void tokenWithAnotherIssuerIsRejected() {
        String token = new JwtTokenProvider(SECRET, "someone-else", AUDIENCE, 15, 7).generateAccessToken(userId);

        assertThat(provider.getUserIdFromAccessToken(token)).isEmpty();
    }

    @Test
    void tokenWithAnotherAudienceIsRejected() {
        String token = new JwtTokenProvider(SECRET, ISSUER, "another-app", 15, 7).generateAccessToken(userId);

        assertThat(provider.getUserIdFromAccessToken(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = new JwtTokenProvider(SECRET, ISSUER, AUDIENCE, -1, 7).generateAccessToken(userId);

        assertThat(provider.getUserIdFromAccessToken(token)).isEmpty();
    }

    @Test
    void tokenWhoseSubjectIsNotAUuidIsRejected() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("not-a-uuid")
                .audience().add(AUDIENCE).and()
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(5))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.getUserIdFromAccessToken(token)).isEmpty();
    }

    @Test
    void garbageAndBlankTokensAreRejected() {
        assertThat(provider.getUserIdFromAccessToken("not.a.jwt")).isEqualTo(Optional.empty());
        assertThat(provider.getUserIdFromAccessToken("")).isEqualTo(Optional.empty());
    }

    @Test
    void secretShorterThan32BytesIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short", ISSUER, AUDIENCE, 15, 7))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refreshTokenValuesAreRandomUrlSafeAndCarryThe256BitSize() {
        String first = provider.generateRefreshTokenValue();
        String second = provider.generateRefreshTokenValue();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void refreshTokenTtlComesFromTheConfiguredDays() {
        assertThat(provider.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }
}