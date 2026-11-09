package com.example.lori.service;

import com.example.lori.entity.User;
import com.example.lori.repository.RefreshTokenRepository;
import com.example.lori.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final String EMAIL = "linh@example.com";
    private static final String CODE_KEY = "password_reset_code:linh@example.com";
    private static final String ATTEMPTS_KEY = "password_reset_attempts:linh@example.com";
    private static final String COOLDOWN_KEY = "password_reset_cooldown:linh@example.com";
    private static final String INVALID_CODE = "Invalid or expired code";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final BruteForceProtectionService bruteForceProtectionService = mock(BruteForceProtectionService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final PasswordResetService service = new PasswordResetService(redisTemplate, userRepository,
            refreshTokenRepository, passwordEncoder, bruteForceProtectionService, emailService);
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void redisReturnsValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void requestWithinTheCooldownIsRejectedWith429() {
        when(valueOps.setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(60))).thenReturn(false);

        assertApiException(() -> service.requestReset(EMAIL), HttpStatus.TOO_MANY_REQUESTS,
                "Please wait before requesting another code");
        verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyLong());
    }

    @Test
    void unknownEmailSucceedsSilentlyWithoutSendingAnything() {
        when(valueOps.setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        service.requestReset(EMAIL);

        verify(valueOps, never()).set(anyString(), anyString(), eq(Duration.ofMinutes(10)));
        verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyLong());
    }

    @Test
    void knownEmailGetsASixDigitCodeStoredAndEmailed() {
        when(valueOps.setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        service.requestReset("  Linh@Example.com ");

        ArgumentCaptor<String> storedCode = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(CODE_KEY), storedCode.capture(), eq(Duration.ofMinutes(10)));
        assertThat(storedCode.getValue()).matches("\\d{6}");
        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(emailService).sendPasswordResetCode(EMAIL, storedCode.getValue(), 10L);
    }

    @Test
    void resetWithoutAStoredCodeIsRejected() {
        when(valueOps.get(CODE_KEY)).thenReturn(null);

        assertApiException(() -> service.resetPassword(EMAIL, "123456", "NewPass123"),
                HttpStatus.BAD_REQUEST, INVALID_CODE);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void wrongCodeCountsAnAttemptButKeepsTheCode() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(1L);

        assertApiException(() -> service.resetPassword(EMAIL, "000000", "NewPass123"),
                HttpStatus.BAD_REQUEST, INVALID_CODE);

        verify(redisTemplate).expire(ATTEMPTS_KEY, Duration.ofMinutes(10));
        verify(redisTemplate, never()).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
    }

    @Test
    void fifthWrongCodeDeletesTheCode() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(5L);

        assertApiException(() -> service.resetPassword(EMAIL, "000000", "NewPass123"),
                HttpStatus.BAD_REQUEST, INVALID_CODE);

        verify(redisTemplate).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
    }

    @Test
    void correctCodeForAMissingUserIsRejected() {
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertApiException(() -> service.resetPassword(EMAIL, "123456", "NewPass123"),
                HttpStatus.BAD_REQUEST, INVALID_CODE);
    }

    @Test
    void correctCodeChangesThePasswordRevokesTokensAndUnlocksLogin() {
        User user = new User();
        user.setId(userId);
        user.setEmail(EMAIL);
        when(valueOps.get(CODE_KEY)).thenReturn("123456");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("HASHED");

        service.resetPassword(" LINH@example.com", "123456", "NewPass123");

        assertThat(user.getPasswordHash()).isEqualTo("HASHED");
        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(redisTemplate).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
        verify(bruteForceProtectionService).reset(EMAIL);
    }
}