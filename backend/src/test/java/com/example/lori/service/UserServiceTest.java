package com.example.lori.service;

import com.example.lori.dto.ChangePasswordRequest;
import com.example.lori.dto.UpdateProfileRequest;
import com.example.lori.dto.UserResponse;
import com.example.lori.dto.UserStatsResponse;
import com.example.lori.entity.User;
import com.example.lori.entity.UserProgress;
import com.example.lori.repository.RefreshTokenRepository;
import com.example.lori.repository.UserProgressRepository;
import com.example.lori.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProgressRepository userProgressRepository = mock(UserProgressRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ExpService expService = mock(ExpService.class);
    private final StreakService streakService = mock(StreakService.class);
    private final UserService service = new UserService(userRepository, userProgressRepository,
            refreshTokenRepository, passwordEncoder, expService, streakService);
    private final UUID userId = UUID.randomUUID();

    private User givenUser(String passwordHash) {
        User user = new User();
        user.setId(userId);
        user.setEmail("linh@example.com");
        user.setDisplayName("Linh");
        user.setAvatarUrl("https://example.com/a.png");
        user.setPasswordHash(passwordHash);
        user.setPremium(true);
        user.setPremiumExpiresAt(Instant.parse("2027-01-01T00:00:00Z"));
        user.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    private static UserProgress progress(String itemType, String status, int correct, int incorrect) {
        UserProgress progress = new UserProgress();
        progress.setItemType(itemType);
        progress.setStatus(status);
        progress.setCorrectCount(correct);
        progress.setIncorrectCount(incorrect);
        return progress;
    }

    @Test
    void getMeReturnsTheProfileOfTheUser() {
        givenUser("HASH");

        UserResponse response = service.getMe(userId);

        assertThat(response).isEqualTo(new UserResponse(userId, "linh@example.com", "Linh",
                "https://example.com/a.png", true, Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z")));
    }

    @Test
    void getMeForAMissingUserIs401() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertApiException(() -> service.getMe(userId), HttpStatus.UNAUTHORIZED, "User not found");
    }

    @Test
    void updateMeStoresTheSanitizedDisplayName() {
        User user = givenUser("HASH");

        UserResponse response = service.updateMe(userId, new UpdateProfileRequest("  <b>Linh</b>   Nguyen "));

        assertThat(user.getDisplayName()).isEqualTo("Linh Nguyen");
        assertThat(response.displayName()).isEqualTo("Linh Nguyen");
    }

    @Test
    void updateMeRejectsANameThatIsEmptyAfterSanitizing() {
        User user = givenUser("HASH");

        assertApiException(() -> service.updateMe(userId, new UpdateProfileRequest("<b></b>")),
                HttpStatus.BAD_REQUEST, "Display name is invalid");
        assertThat(user.getDisplayName()).isEqualTo("Linh");
    }

    @Test
    void statsCountCompletedItemsByTypeAndSumAllAnswers() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(
                progress("grammar_lesson", "completed", 3, 1),
                progress("grammar_lesson", "in_progress", 2, 2),
                progress("vocab_quiz_topic", "completed", 5, 0),
                progress("grammar_quiz_lesson", "completed", 4, 1),
                progress("other_type", "completed", 1, 1)));
        when(expService.getTotalExp(userId)).thenReturn(250);
        when(streakService.getStreak(userId)).thenReturn(new StreakService.Streak(4, 9));

        UserStatsResponse stats = service.getStats(userId);

        assertThat(stats).isEqualTo(new UserStatsResponse(1, 1, 1, 15, 5, 250, ExpService.levelOf(250), 4, 9));
    }

    @Test
    void statsForAUserWithoutProgressAreZero() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of());
        when(expService.getTotalExp(userId)).thenReturn(0);
        when(streakService.getStreak(userId)).thenReturn(new StreakService.Streak(0, 0));

        assertThat(service.getStats(userId))
                .isEqualTo(new UserStatsResponse(0, 0, 0, 0, 0, 0, ExpService.levelOf(0), 0, 0));
    }

    @Test
    void changePasswordOnAnAccountWithoutPasswordIs400() {
        givenUser(null);

        assertApiException(() -> service.changePassword(userId, new ChangePasswordRequest("old", "NewPass123")),
                HttpStatus.BAD_REQUEST, "This account has no password yet, use forgot password to set one");
        verify(refreshTokenRepository, never()).revokeAllByUserId(userId);
    }

    @Test
    void changePasswordWithAWrongCurrentPasswordIs400() {
        User user = givenUser("HASH");
        when(passwordEncoder.matches("wrong", "HASH")).thenReturn(false);

        assertApiException(() -> service.changePassword(userId, new ChangePasswordRequest("wrong", "NewPass123")),
                HttpStatus.BAD_REQUEST, "Current password is incorrect");
        assertThat(user.getPasswordHash()).isEqualTo("HASH");
        verify(refreshTokenRepository, never()).revokeAllByUserId(userId);
    }

    @Test
    void changePasswordStoresTheNewHashAndRevokesEveryRefreshToken() {
        User user = givenUser("HASH");
        when(passwordEncoder.matches("old", "HASH")).thenReturn(true);
        when(passwordEncoder.encode("NewPass123")).thenReturn("NEW-HASH");

        service.changePassword(userId, new ChangePasswordRequest("old", "NewPass123"));

        assertThat(user.getPasswordHash()).isEqualTo("NEW-HASH");
        verify(refreshTokenRepository).revokeAllByUserId(userId);
    }
}