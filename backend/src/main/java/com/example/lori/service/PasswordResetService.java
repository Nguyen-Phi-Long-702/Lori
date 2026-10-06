package com.example.lori.service;

import com.example.lori.entity.User;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.RefreshTokenRepository;
import com.example.lori.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Quen/dat lai mat khau bang ma 6 chu so gui qua email. Ma, so lan nhap sai va cooldown luu Redis (Upstash);
 * cac key phai dung email da chuan hoa (chu thuong).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String CODE_KEY_PREFIX = "password_reset_code:";
    private static final String ATTEMPTS_KEY_PREFIX = "password_reset_attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "password_reset_cooldown:";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_WRONG_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final BruteForceProtectionService bruteForceProtectionService;
    private final EmailService emailService;

    /**
     * Gui ma dat lai mat khau. Luon thanh cong ke ca khi email chua dang ky (khong lo email nao ton tai);
     * cooldown ap dung cho moi email nen 429 cung khong lo thong tin do.
     */
    public void requestReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(COOLDOWN_KEY_PREFIX + email, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code");
        }
        if (!userRepository.existsByEmail(email)) {
            return;
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_TTL);
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
        emailService.sendPasswordResetCode(email, code, CODE_TTL.toMinutes());
    }

    /** Dat lai mat khau: ma dung -> doi mat khau, thu hoi refresh token, xoa ma, mo khoa dang nhap. */
    @Transactional
    public void resetPassword(String rawEmail, String code, String newPassword) {
        String email = normalizeEmail(rawEmail);
        String codeKey = CODE_KEY_PREFIX + email;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null) {
            throw invalidCode();
        }

        boolean matches = MessageDigest.isEqual(
                storedCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, CODE_TTL);
            if (attempts != null && attempts >= MAX_WRONG_ATTEMPTS) {
                redisTemplate.delete(List.of(codeKey, attemptsKey));
            }
            throw invalidCode();
        }

        User user = userRepository.findByEmail(email).orElseThrow(PasswordResetService::invalidCode);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByUserId(user.getId());

        redisTemplate.delete(List.of(codeKey, attemptsKey));
        bruteForceProtectionService.reset(email);
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}