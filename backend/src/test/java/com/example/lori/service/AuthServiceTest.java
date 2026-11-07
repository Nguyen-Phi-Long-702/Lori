package com.example.lori.service;

import com.example.lori.dto.AuthResponse;
import com.example.lori.dto.LoginRequest;
import com.example.lori.dto.RegisterRequest;
import com.example.lori.entity.RefreshToken;
import com.example.lori.entity.User;
import com.example.lori.repository.RefreshTokenRepository;
import com.example.lori.repository.UserRepository;
import com.example.lori.security.GoogleTokenVerifier;
import com.example.lori.security.GoogleTokenVerifier.GoogleUserInfo;
import com.example.lori.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String PASSWORD = "Test@12345";
    private static final AuthResponse TOKENS = new AuthResponse("access-token", "refresh-token");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final BruteForceProtectionService bruteForceProtectionService = mock(BruteForceProtectionService.class);
    private final GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
    private final AuthService service = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
            jwtTokenProvider, bruteForceProtectionService, googleTokenVerifier);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubTokenProviderAndUserSave() {
        when(jwtTokenProvider.generateAccessToken(any(UUID.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshTokenValue()).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenTtl()).thenReturn(Duration.ofDays(7));
        // Gia lap DB: user moi duoc gan id luc luu
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(userId);
            }
            return saved;
        });
    }

    // ===== register =====

    @Test
    void registerCreatesUserWithNormalizedEmailAndHashedPassword() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");
        Instant before = Instant.now();

        AuthResponse response = service.register(new RegisterRequest("  Lan  ", "  Lan@Example.COM ", PASSWORD));

        Instant after = Instant.now();
        assertThat(response).isEqualTo(TOKENS);
        verify(userRepository).existsByEmail("lan@example.com");
        User created = savedUser();
        assertThat(created.getEmail()).isEqualTo("lan@example.com");
        assertThat(created.getDisplayName()).isEqualTo("Lan");
        assertThat(created.getPasswordHash()).isEqualTo("hashed-password");
        RefreshToken refreshToken = savedRefreshToken();
        assertThat(refreshToken.getUserId()).isEqualTo(userId);
        assertThat(refreshToken.getToken()).isEqualTo("refresh-token");
        assertThat(refreshToken.getExpiresAt())
                .isAfterOrEqualTo(before.plus(Duration.ofDays(7)))
                .isBeforeOrEqualTo(after.plus(Duration.ofDays(7)));
        verify(jwtTokenProvider).generateAccessToken(userId);
    }

    @Test
    void registerStripsHtmlFromDisplayName() {
        service.register(new RegisterRequest("<b>Lan</b>", "lan@example.com", PASSWORD));

        assertThat(savedUser().getDisplayName()).isEqualTo("Lan");
    }

    @Test
    void registerRejectsEmailThatIsAlreadyTaken() {
        when(userRepository.existsByEmail("lan@example.com")).thenReturn(true);

        assertApiException(() -> service.register(new RegisterRequest("Lan", "Lan@example.com", PASSWORD)),
                HttpStatus.CONFLICT, "Email already registered");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registerRejectsDisplayNameThatIsEmptyAfterSanitizing() {
        assertApiException(() -> service.register(new RegisterRequest("<b></b>", "lan@example.com", PASSWORD)),
                HttpStatus.BAD_REQUEST, "Display name is invalid");

        verify(userRepository, never()).save(any(User.class));
    }

    // ===== login =====

    @Test
    void loginWithCorrectPasswordResetsFailuresAndIssuesTokens() {
        when(userRepository.findByEmail("lan@example.com")).thenReturn(Optional.of(userWithPassword("hashed-password")));
        when(passwordEncoder.matches(PASSWORD, "hashed-password")).thenReturn(true);

        AuthResponse response = service.login(new LoginRequest(" Lan@Example.com ", PASSWORD));

        assertThat(response).isEqualTo(TOKENS);
        verify(bruteForceProtectionService).isLocked("lan@example.com");
        verify(bruteForceProtectionService).reset("lan@example.com");
        verify(bruteForceProtectionService, never()).recordFailure(anyString());
    }

    @Test
    void loginIsRefusedWhileTheEmailIsLocked() {
        when(bruteForceProtectionService.isLocked("lan@example.com")).thenReturn(true);

        assertApiException(() -> service.login(new LoginRequest("lan@example.com", PASSWORD)),
                HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts, try again later");

        verifyNoInteractions(userRepository, passwordEncoder, refreshTokenRepository);
        verify(bruteForceProtectionService, never()).recordFailure(anyString());
    }

    @Test
    void loginWithUnknownEmailFailsAndCountsAsFailure() {
        assertApiException(() -> service.login(new LoginRequest("lan@example.com", PASSWORD)),
                HttpStatus.UNAUTHORIZED, "Invalid email or password");

        verify(bruteForceProtectionService).recordFailure("lan@example.com");
        verifyNoInteractions(passwordEncoder, refreshTokenRepository);
    }

    @Test
    void loginToAccountWithoutPasswordFailsWithTheSameMessage() {
        when(userRepository.findByEmail("lan@example.com")).thenReturn(Optional.of(userWithPassword(null)));

        assertApiException(() -> service.login(new LoginRequest("lan@example.com", PASSWORD)),
                HttpStatus.UNAUTHORIZED, "Invalid email or password");

        verify(bruteForceProtectionService).recordFailure("lan@example.com");
        verifyNoInteractions(passwordEncoder, refreshTokenRepository);
    }

    @Test
    void loginWithWrongPasswordFailsAndCountsAsFailure() {
        when(userRepository.findByEmail("lan@example.com")).thenReturn(Optional.of(userWithPassword("hashed-password")));
        when(passwordEncoder.matches(PASSWORD, "hashed-password")).thenReturn(false);

        assertApiException(() -> service.login(new LoginRequest("lan@example.com", PASSWORD)),
                HttpStatus.UNAUTHORIZED, "Invalid email or password");

        verify(bruteForceProtectionService).recordFailure("lan@example.com");
        verify(bruteForceProtectionService, never()).reset(anyString());
        verifyNoInteractions(refreshTokenRepository);
    }

    // ===== googleLogin =====

    @Test
    void googleLoginOfKnownGoogleAccountOnlyIssuesTokens() {
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(new GoogleUserInfo("g-1", "Lan@Gmail.com", "Lan", "https://pic"));
        when(userRepository.findByGoogleId("g-1")).thenReturn(Optional.of(userWithPassword(null)));

        assertThat(service.googleLogin("id-token")).isEqualTo(TOKENS);

        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void googleLoginLinksGoogleIdToAnExistingEmailAccount() {
        User existing = userWithPassword("hashed-password");
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(new GoogleUserInfo("g-1", "Lan@Gmail.com", "Google Name", "https://pic"));
        when(userRepository.findByEmail("lan@gmail.com")).thenReturn(Optional.of(existing));

        assertThat(service.googleLogin("id-token")).isEqualTo(TOKENS);

        verify(userRepository).save(existing);
        assertThat(existing.getGoogleId()).isEqualTo("g-1");
        assertThat(existing.getAvatarUrl()).isEqualTo("https://pic");
        assertThat(existing.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(existing.getDisplayName()).isEqualTo("Lan");
    }

    @Test
    void googleLoginKeepsTheAvatarTheUserAlreadyHas() {
        User existing = userWithPassword("hashed-password");
        existing.setAvatarUrl("https://old-avatar");
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(new GoogleUserInfo("g-1", "lan@gmail.com", "Lan", "https://pic"));
        when(userRepository.findByEmail("lan@gmail.com")).thenReturn(Optional.of(existing));

        service.googleLogin("id-token");

        assertThat(existing.getAvatarUrl()).isEqualTo("https://old-avatar");
    }

    @Test
    void googleLoginCreatesAccountWithoutPassword() {
        User created = loginAsNewGoogleUser(new GoogleUserInfo("g-1", "Lan@Gmail.com", "  Lan   Nguyen ", "https://pic"));

        assertThat(created.getEmail()).isEqualTo("lan@gmail.com");
        assertThat(created.getDisplayName()).isEqualTo("Lan Nguyen");
        assertThat(created.getGoogleId()).isEqualTo("g-1");
        assertThat(created.getAvatarUrl()).isEqualTo("https://pic");
        assertThat(created.getPasswordHash()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NIL, lan.nguyen@gmail.com, lan.nguyen",
            "'   ', lan.nguyen@gmail.com, lan.nguyen",
            "NIL, @gmail.com, Lori User"
    }, nullValues = "NIL")
    void googleDisplayNameFallsBackToEmailPrefixThenToDefault(String googleName, String email, String expected) {
        User created = loginAsNewGoogleUser(new GoogleUserInfo("g-1", email, googleName, null));

        assertThat(created.getDisplayName()).isEqualTo(expected);
    }

    @Test
    void googleDisplayNameIsCutAtOneHundredCharacters() {
        User created = loginAsNewGoogleUser(new GoogleUserInfo("g-1", "lan@gmail.com", "a".repeat(150), null));

        assertThat(created.getDisplayName()).isEqualTo("a".repeat(100));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void googleAvatarThatIsMissingOrBlankIsIgnored(String pictureUrl) {
        User created = loginAsNewGoogleUser(new GoogleUserInfo("g-1", "lan@gmail.com", "Lan", pictureUrl));

        assertThat(created.getAvatarUrl()).isNull();
    }

    @Test
    void googleAvatarLongerThanTheColumnIsIgnored() {
        String tooLong = "https://" + "a".repeat(493);

        User created = loginAsNewGoogleUser(new GoogleUserInfo("g-1", "lan@gmail.com", "Lan", tooLong));

        assertThat(tooLong).hasSize(501);
        assertThat(created.getAvatarUrl()).isNull();
    }

    @Test
    void googleAvatarOfExactlyTheColumnLengthIsKept() {
        String maxLength = "https://" + "a".repeat(492);

        User created = loginAsNewGoogleUser(new GoogleUserInfo("g-1", "lan@gmail.com", "Lan", maxLength));

        assertThat(maxLength).hasSize(500);
        assertThat(created.getAvatarUrl()).isEqualTo(maxLength);
    }

    // ===== refresh =====

    @Test
    void refreshRevokesTheOldTokenAndIssuesNewTokens() {
        RefreshToken stored = refreshToken(Instant.now().plus(Duration.ofDays(1)), false);
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithPassword(null)));

        AuthResponse response = service.refresh("old-token");

        assertThat(response).isEqualTo(TOKENS);
        assertThat(stored.isRevoked()).isTrue();
        assertThat(savedRefreshToken().getToken()).isEqualTo("refresh-token");
    }

    @Test
    void refreshWithUnknownTokenFails() {
        assertApiException(() -> service.refresh("missing-token"), HttpStatus.UNAUTHORIZED, "Invalid refresh token");

        verifyNoInteractions(userRepository);
    }

    @Test
    void refreshWithRevokedTokenFails() {
        when(refreshTokenRepository.findByToken("old-token"))
                .thenReturn(Optional.of(refreshToken(Instant.now().plus(Duration.ofDays(1)), true)));

        assertApiException(() -> service.refresh("old-token"), HttpStatus.UNAUTHORIZED, "Invalid refresh token");

        verifyNoInteractions(userRepository);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshWithExpiredTokenFails() {
        when(refreshTokenRepository.findByToken("old-token"))
                .thenReturn(Optional.of(refreshToken(Instant.now().minus(Duration.ofHours(1)), false)));

        assertApiException(() -> service.refresh("old-token"), HttpStatus.UNAUTHORIZED, "Invalid refresh token");

        verifyNoInteractions(userRepository);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshForDeletedUserFailsAndLeavesTheTokenUntouched() {
        RefreshToken stored = refreshToken(Instant.now().plus(Duration.ofDays(1)), false);
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(stored));

        assertApiException(() -> service.refresh("old-token"), HttpStatus.UNAUTHORIZED, "Invalid refresh token");

        assertThat(stored.isRevoked()).isFalse();
    }

    // ===== logout =====

    @Test
    void logoutRevokesTheToken() {
        RefreshToken stored = refreshToken(Instant.now().plus(Duration.ofDays(1)), false);
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(stored));

        service.logout("old-token");

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void logoutWithUnknownTokenIsIgnored() {
        assertThatCode(() -> service.logout("missing-token")).doesNotThrowAnyException();
    }

    // ===== ham dung chung =====

    private User userWithPassword(String passwordHash) {
        User user = new User();
        user.setId(userId);
        user.setEmail("lan@example.com");
        user.setDisplayName("Lan");
        user.setPasswordHash(passwordHash);
        return user;
    }

    private RefreshToken refreshToken(Instant expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setToken("old-token");
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        return token;
    }

    // Dang nhap Google khi chua co tai khoan nao; tra ve user moi duoc luu
    private User loginAsNewGoogleUser(GoogleUserInfo info) {
        when(googleTokenVerifier.verify("id-token")).thenReturn(info);
        service.googleLogin("id-token");
        return savedUser();
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private RefreshToken savedRefreshToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }
}