package com.example.lori.service;

import com.example.lori.dto.LeaderboardEntryResponse;
import com.example.lori.dto.LeaderboardMeResponse;
import com.example.lori.entity.User;
import com.example.lori.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
    private final LeaderboardService service = new LeaderboardService(redisTemplate, userRepository);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void redisReturnsTheMockedSortedSet() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    // ===== updateScore =====

    @Test
    void updateScoreAddsTheAmountToAllThreeBoards() {
        service.updateScore(userId, 50);

        verify(zSetOps).incrementScore("leaderboard:global", userId.toString(), 50.0);
        verify(zSetOps).incrementScore("leaderboard:weekly", userId.toString(), 50.0);
        verify(zSetOps).incrementScore("leaderboard:monthly", userId.toString(), 50.0);
    }

    @Test
    void updateScoreIgnoresZeroAndNegativeAmounts() {
        service.updateScore(userId, 0);
        service.updateScore(userId, -5);

        verifyNoInteractions(zSetOps);
    }

    @Test
    void updateScoreSwallowsRedisFailures() {
        when(zSetOps.incrementScore(anyString(), anyString(), anyDouble())).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> service.updateScore(userId, 10)).doesNotThrowAnyException();
    }

    // ===== getTopN =====

    @Test
    void negativePageIsRejected() {
        assertApiException(() -> service.getTopN(LeaderboardPeriod.GLOBAL, -1, 10),
                HttpStatus.BAD_REQUEST, "page must be 0 or greater");
    }

    @Test
    void sizeOutsideOneToFiftyIsRejected() {
        assertApiException(() -> service.getTopN(LeaderboardPeriod.GLOBAL, 0, 0),
                HttpStatus.BAD_REQUEST, "size must be between 1 and 50");
        assertApiException(() -> service.getTopN(LeaderboardPeriod.GLOBAL, 0, 51),
                HttpStatus.BAD_REQUEST, "size must be between 1 and 50");
    }

    @Test
    void maxSizeIsAcceptedAndTheBoardOfThePeriodIsRead() {
        assertThat(service.getTopN(LeaderboardPeriod.WEEKLY, 0, 50)).isEmpty();

        verify(zSetOps).reverseRangeWithScores("leaderboard:weekly", 0, 49);
    }

    @Test
    void boardWithoutScoresReturnsAnEmptyList() {
        assertThat(service.getTopN(LeaderboardPeriod.GLOBAL, 0, 10)).isEmpty();

        when(zSetOps.reverseRangeWithScores("leaderboard:global", 0, 9)).thenReturn(null);

        assertThat(service.getTopN(LeaderboardPeriod.GLOBAL, 0, 10)).isEmpty();
    }

    @Test
    void secondPageStartsAtTheRightRankWithRoundedScores() {
        User lan = user("Lan", "https://avatar/lan");
        User minh = user("Minh", null);
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>(List.of(
                tuple(lan, 120.4), tuple(minh, 99.6)));
        when(zSetOps.reverseRangeWithScores("leaderboard:global", 6, 8)).thenReturn(tuples);
        when(userRepository.findAllById(any())).thenReturn(List.of(lan, minh));

        List<LeaderboardEntryResponse> entries = service.getTopN(LeaderboardPeriod.GLOBAL, 2, 3);

        assertThat(entries).containsExactly(
                new LeaderboardEntryResponse(7, "Lan", "https://avatar/lan", 120L),
                new LeaderboardEntryResponse(8, "Minh", null, 100L));
    }

    @Test
    void missingUsersAreSkippedButRanksKeepCounting() {
        User lan = user("Lan", null);
        User ghost = user("Ghost", null);
        User hoa = user("Hoa", null);
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>(List.of(
                tuple(lan, 300.0), tuple(ghost, 200.0), tuple(hoa, null)));
        when(zSetOps.reverseRangeWithScores("leaderboard:global", 0, 2)).thenReturn(tuples);
        when(userRepository.findAllById(any())).thenReturn(List.of(lan, hoa));

        List<LeaderboardEntryResponse> entries = service.getTopN(LeaderboardPeriod.GLOBAL, 0, 3);

        assertThat(entries).containsExactly(
                new LeaderboardEntryResponse(1, "Lan", null, 300L),
                new LeaderboardEntryResponse(3, "Hoa", null, 0L));
    }

    // ===== getUserRank =====

    @Test
    void userWithoutScoreHasNoRank() {
        when(zSetOps.reverseRank("leaderboard:global", userId.toString())).thenReturn(null);

        assertThat(service.getUserRank(LeaderboardPeriod.GLOBAL, userId)).isEqualTo(new LeaderboardMeResponse(null, 0L));
    }

    @Test
    void rankIsOneBasedAndScoreIsRounded() {
        when(zSetOps.reverseRank("leaderboard:weekly", userId.toString())).thenReturn(0L);
        when(zSetOps.score("leaderboard:weekly", userId.toString())).thenReturn(150.4);

        assertThat(service.getUserRank(LeaderboardPeriod.WEEKLY, userId)).isEqualTo(new LeaderboardMeResponse(1, 150L));
    }

    @Test
    void rankedUserWhoseScoreIsMissingGetsZero() {
        when(zSetOps.reverseRank("leaderboard:monthly", userId.toString())).thenReturn(3L);
        when(zSetOps.score("leaderboard:monthly", userId.toString())).thenReturn(null);

        assertThat(service.getUserRank(LeaderboardPeriod.MONTHLY, userId)).isEqualTo(new LeaderboardMeResponse(4, 0L));
    }

    // ===== seedGlobalFromDatabase =====

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void seedLoadsEveryUserWithExpIntoTheGlobalBoard() {
        UUID other = UUID.randomUUID();
        Object[] first = {userId.toString(), 120};
        Object[] second = {other, 30L};
        List<Object[]> rows = List.of(first, second);
        when(userRepository.findAllWithExp()).thenReturn(rows);

        service.seedGlobalFromDatabase();

        ArgumentCaptor<Set<ZSetOperations.TypedTuple<String>>> captor = ArgumentCaptor.forClass((Class) Set.class);
        verify(zSetOps).add(eq("leaderboard:global"), captor.capture());
        Map<String, Double> scores = captor.getValue().stream()
                .collect(Collectors.toMap(tuple -> tuple.getValue(), tuple -> tuple.getScore()));
        assertThat(scores).containsOnly(entry(userId.toString(), 120.0), entry(other.toString(), 30.0));
    }

    @Test
    void seedWithoutUsersDoesNotTouchRedis() {
        service.seedGlobalFromDatabase();

        verifyNoInteractions(zSetOps);
    }

    @Test
    void seedFailureDoesNotStopStartup() {
        when(userRepository.findAllWithExp()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service.seedGlobalFromDatabase()).doesNotThrowAnyException();
    }

    // ===== reset =====

    @Test
    void resetWeeklyDeletesOnlyTheWeeklyBoard() {
        service.resetWeekly();

        verify(redisTemplate).delete("leaderboard:weekly");
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    void resetMonthlyDeletesOnlyTheMonthlyBoard() {
        service.resetMonthly();

        verify(redisTemplate).delete("leaderboard:monthly");
        verifyNoMoreInteractions(redisTemplate);
    }

    // ===== ham dung chung =====

    private static User user(String displayName, String avatarUrl) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(displayName);
        user.setAvatarUrl(avatarUrl);
        return user;
    }

    private static ZSetOperations.TypedTuple<String> tuple(User user, Double score) {
        return new DefaultTypedTuple<>(user.getId().toString(), score);
    }
}