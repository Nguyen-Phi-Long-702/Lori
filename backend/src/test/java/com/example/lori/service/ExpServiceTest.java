package com.example.lori.service;

import com.example.lori.dto.ProgressItemRequest;
import com.example.lori.repository.ExamResultRepository;
import com.example.lori.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ExpServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StreakService streakService = mock(StreakService.class);
    private final ExamResultRepository examResultRepository = mock(ExamResultRepository.class);
    private final LeaderboardService leaderboardService = mock(LeaderboardService.class);
    private final ExpService service = new ExpService(userRepository, streakService, examResultRepository, leaderboardService);

    private final UUID userId = UUID.randomUUID();
    private final UUID paperId = UUID.randomUUID();
    private final LocalDate today = StreakService.today();

    // award() dang ky viec cap nhat bang xep hang sau khi commit, nen test phai bat dong bo hoa transaction
    @BeforeEach
    void startTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ===== cap do =====

    @ParameterizedTest
    @CsvSource({"0,1", "99,1", "100,2", "299,2", "300,3", "999,4", "1000,5", "4499,9", "4500,10"})
    void levelOfFollowsTheTieredFormula(int totalExp, int expectedLevel) {
        assertThat(ExpService.levelOf(totalExp)).isEqualTo(expectedLevel);
    }

    @Test
    void expToReachMatchesTheDocumentedValues() {
        assertThat(ExpService.expToReach(1)).isEqualTo(0L);
        assertThat(ExpService.expToReach(2)).isEqualTo(100L);
        assertThat(ExpService.expToReach(3)).isEqualTo(300L);
        assertThat(ExpService.expToReach(5)).isEqualTo(1000L);
        assertThat(ExpService.expToReach(10)).isEqualTo(4500L);
    }

    @Test
    void totalExpOfUnknownUserIsZero() {
        when(userRepository.findTotalExp(userId)).thenReturn(null);

        assertThat(service.getTotalExp(userId)).isZero();
    }

    @Test
    void totalExpIsReadFromTheUser() {
        when(userRepository.findTotalExp(userId)).thenReturn(250);

        assertThat(service.getTotalExp(userId)).isEqualTo(250);
    }

    // ===== bang quy doi =====

    @ParameterizedTest
    @CsvSource({
            "50, 200, 5, 0, 50",
            "50, 200, 0, 40, 50",
            "50, 200, 40, 40, 200",
            "50, 200, 20, 40, 125",
            "10, 30, 1, 8, 13",
            "15, 40, 1, 3, 23",
            "15, 40, 2, 3, 32"
    })
    void scaledInterpolatesBetweenMinAndMaxByAccuracy(int min, int max, int correct, int total, int expected) {
        assertThat(ExpService.scaled(min, max, correct, total)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "grammar_lesson, 0, 0, 15",
            "grammar_lesson, 9, 1, 15",
            "vocab_quiz_topic, 5, 0, 30",
            "vocab_quiz_topic, 0, 5, 10",
            "vocab_quiz_topic, 0, 0, 10",
            "grammar_quiz_lesson, 10, 0, 40",
            "grammar_quiz_lesson, 0, 10, 15",
            "flashcard, 5, 5, 0"
    })
    void progressExpFollowsTheConversionTable(String itemType, int correct, int incorrect, int expected) {
        assertThat(ExpService.progressExp(itemType, correct, incorrect)).isEqualTo(expected);
    }

    // ===== awardForProgress =====

    @Test
    void studyDayInTheFutureIsIgnored() {
        service.awardForProgress(userId, item("grammar_lesson", "completed", 0, 0, noonOf(today.plusDays(1))), null);

        verifyNoInteractions(streakService, userRepository, leaderboardService);
    }

    @Test
    void studyDayOlderThanSevenDaysIsIgnored() {
        service.awardForProgress(userId, item("grammar_lesson", "completed", 0, 0, noonOf(today.minusDays(8))), null);

        verifyNoInteractions(streakService, userRepository, leaderboardService);
    }

    @Test
    void studyDayExactlySevenDaysAgoIsStillCounted() {
        LocalDate day = today.minusDays(7);
        when(streakService.recordStudyDay(userId, day)).thenReturn(true);

        service.awardForProgress(userId, item("grammar_lesson", "learning", 0, 0, noonOf(day)), null);

        verify(userRepository).addExp(userId, 20);
    }

    @Test
    void newStudyDayGivesTheStreakBonusAndUpdatesTheLeaderboardOnlyAfterCommit() {
        when(streakService.recordStudyDay(userId, today)).thenReturn(true);

        service.awardForProgress(userId, item("grammar_lesson", "learning", 0, 0, noonOf(today)), null);

        verify(userRepository).addExp(userId, 20);
        verifyNoInteractions(leaderboardService);
        commit();
        verify(leaderboardService).updateScore(userId, 20);
    }

    @Test
    void existingStudyDayWithoutCompletionGivesNothing() {
        service.awardForProgress(userId, item("grammar_lesson", "learning", 0, 0, noonOf(today)), null);

        verify(streakService).recordStudyDay(userId, today);
        verify(userRepository, never()).addExp(any(UUID.class), anyInt());
    }

    @Test
    void completedItemGivesItsExp() {
        service.awardForProgress(userId, item("grammar_lesson", "completed", 0, 0, noonOf(today)), null);

        verify(userRepository).addExp(userId, 15);
        commit();
        verify(leaderboardService).updateScore(userId, 15);
    }

    @Test
    void completedQuizOnANewStudyDayGivesBothStreakAndQuizExp() {
        when(streakService.recordStudyDay(userId, today)).thenReturn(true);

        service.awardForProgress(userId, item("vocab_quiz_topic", "completed", 5, 0, noonOf(today)), null);

        verify(userRepository).addExp(userId, 20);
        verify(userRepository).addExp(userId, 30);
        commit();
        verify(leaderboardService).updateScore(userId, 20);
        verify(leaderboardService).updateScore(userId, 30);
    }

    @Test
    void itemAlreadyCountedOnTheSameDayGivesNoExp() {
        Instant earlierToday = noonOf(today).minusSeconds(3600);

        service.awardForProgress(userId, item("grammar_lesson", "completed", 0, 0, noonOf(today)), earlierToday);

        verify(userRepository, never()).addExp(any(UUID.class), anyInt());
    }

    @Test
    void itemLastStudiedOnAnEarlierDayGivesExpAgain() {
        Instant yesterday = noonOf(today.minusDays(1));

        service.awardForProgress(userId, item("grammar_lesson", "completed", 0, 0, noonOf(today)), yesterday);

        verify(userRepository).addExp(userId, 15);
    }

    @Test
    void completedItemOfUnknownTypeGivesNoExp() {
        service.awardForProgress(userId, item("flashcard", "completed", 0, 0, noonOf(today)), null);

        verify(userRepository, never()).addExp(any(UUID.class), anyInt());
    }

    // ===== awardForExam =====

    @Test
    void firstExamOfTheDayGivesScaledExpAndTheStreakBonus() {
        when(streakService.recordStudyDay(userId, today)).thenReturn(true);

        service.awardForExam(userId, paperId, 20, 40);

        verify(examResultRepository).existsByUserIdAndPaperIdAndSubmittedAtGreaterThanEqual(
                userId, paperId, today.atStartOfDay(StreakService.ZONE).toInstant());
        verify(userRepository).addExp(userId, 20);
        verify(userRepository).addExp(userId, 125);
        commit();
        verify(leaderboardService).updateScore(userId, 20);
        verify(leaderboardService).updateScore(userId, 125);
    }

    @Test
    void perfectExamGivesTheMaximumExp() {
        service.awardForExam(userId, paperId, 40, 40);

        verify(userRepository).addExp(userId, 200);
    }

    @Test
    void sameExamAgainOnTheSameDayGivesNoExp() {
        when(examResultRepository.existsByUserIdAndPaperIdAndSubmittedAtGreaterThanEqual(
                any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn(true);

        service.awardForExam(userId, paperId, 40, 40);

        verify(userRepository, never()).addExp(any(UUID.class), anyInt());
    }

    @Test
    void sameExamAgainOnANewStudyDayStillGivesTheStreakBonus() {
        when(streakService.recordStudyDay(userId, today)).thenReturn(true);
        when(examResultRepository.existsByUserIdAndPaperIdAndSubmittedAtGreaterThanEqual(
                any(UUID.class), any(UUID.class), any(Instant.class))).thenReturn(true);

        service.awardForExam(userId, paperId, 40, 40);

        verify(userRepository).addExp(userId, 20);
        verifyNoMoreInteractions(userRepository);
    }

    // ===== ham dung chung =====

    // Gia lap thoi diem transaction commit: chay cac viec da dang ky cho "sau khi commit"
    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    // 12:00 trua cua mot ngay theo gio Viet Nam, tranh sat nua dem
    private static Instant noonOf(LocalDate day) {
        return day.atTime(12, 0).atZone(StreakService.ZONE).toInstant();
    }

    private static ProgressItemRequest item(String itemType, String status, int correct, int incorrect, Instant studiedAt) {
        return new ProgressItemRequest(itemType, 1, status, correct, incorrect, studiedAt);
    }
}