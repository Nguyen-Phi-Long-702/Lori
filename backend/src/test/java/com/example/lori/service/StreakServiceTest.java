package com.example.lori.service;

import com.example.lori.repository.UserStudyDayRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 29);

    private final UserStudyDayRepository repository = mock(UserStudyDayRepository.class);
    private final StreakService service = new StreakService(repository);
    private final UUID userId = UUID.randomUUID();

    private static LocalDate daysAgo(int days) {
        return TODAY.minusDays(days);
    }

    @Test
    void noStudyDaysMeansNoStreak() {
        assertThat(StreakService.compute(List.of(), TODAY)).isEqualTo(new StreakService.Streak(0, 0));
    }

    @Test
    void studyingOnlyTodayGivesOne() {
        assertThat(StreakService.compute(List.of(daysAgo(0)), TODAY)).isEqualTo(new StreakService.Streak(1, 1));
    }

    @Test
    void consecutiveDaysUpToTodayCountAsOneRun() {
        List<LocalDate> days = List.of(daysAgo(0), daysAgo(1), daysAgo(2));

        assertThat(StreakService.compute(days, TODAY)).isEqualTo(new StreakService.Streak(3, 3));
    }

    @Test
    void streakStaysAliveWhenTheLastStudyDayIsYesterday() {
        List<LocalDate> days = List.of(daysAgo(1), daysAgo(2));

        assertThat(StreakService.compute(days, TODAY)).isEqualTo(new StreakService.Streak(2, 2));
    }

    @Test
    void streakIsLostWhenTheLastStudyDayIsTwoDaysAgo() {
        List<LocalDate> days = List.of(daysAgo(2), daysAgo(3));

        assertThat(StreakService.compute(days, TODAY)).isEqualTo(new StreakService.Streak(0, 2));
    }

    @Test
    void aGapEndsTheCurrentRunButTheLongestRunIsKept() {
        List<LocalDate> days = List.of(daysAgo(0), daysAgo(1), daysAgo(3), daysAgo(4), daysAgo(5));

        assertThat(StreakService.compute(days, TODAY)).isEqualTo(new StreakService.Streak(2, 3));
    }

    @Test
    void longestRunCanBeOlderThanTheCurrentRun() {
        List<LocalDate> days = List.of(daysAgo(0), daysAgo(2), daysAgo(3), daysAgo(4));

        assertThat(StreakService.compute(days, TODAY)).isEqualTo(new StreakService.Streak(1, 3));
    }

    @Test
    void recordStudyDayReturnsTrueOnlyForANewDay() {
        when(repository.insertIfAbsent(userId, TODAY)).thenReturn(1);
        when(repository.insertIfAbsent(userId, daysAgo(1))).thenReturn(0);

        assertThat(service.recordStudyDay(userId, TODAY)).isTrue();
        assertThat(service.recordStudyDay(userId, daysAgo(1))).isFalse();
    }

    @Test
    void getStreakReadsTheStudyDatesOfTheUser() {
        LocalDate today = StreakService.today();
        when(repository.findStudyDates(userId)).thenReturn(List.of(today, today.minusDays(1)));

        assertThat(service.getStreak(userId)).isEqualTo(new StreakService.Streak(2, 2));
    }
}