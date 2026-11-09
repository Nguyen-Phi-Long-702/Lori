package com.example.lori.service;

import com.example.lori.dto.ProgressItemRequest;
import com.example.lori.dto.ProgressItemResponse;
import com.example.lori.dto.SyncProgressRequest;
import com.example.lori.dto.SyncProgressResponse;
import com.example.lori.entity.UserProgress;
import com.example.lori.repository.UserProgressRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgressServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-03T00:00:00Z");

    private final UserProgressRepository userProgressRepository = mock(UserProgressRepository.class);
    private final ExpService expService = mock(ExpService.class);
    private final LearningEngineService learningEngineService = mock(LearningEngineService.class);
    private final ProgressService service =
            new ProgressService(userProgressRepository, expService, learningEngineService);
    private final UUID userId = UUID.randomUUID();

    private static ProgressItemRequest item(String itemType, int itemId, Instant lastStudiedAt) {
        return new ProgressItemRequest(itemType, itemId, "completed", 3, 1, lastStudiedAt);
    }

    private static UserProgress stored(String itemType, int itemId, Instant lastStudiedAt) {
        UserProgress progress = new UserProgress();
        progress.setItemType(itemType);
        progress.setItemId(itemId);
        progress.setStatus("completed");
        progress.setCorrectCount(2);
        progress.setIncorrectCount(0);
        progress.setLastStudiedAt(lastStudiedAt);
        return progress;
    }

    private void givenUpsertResult(ProgressItemRequest item, int written) {
        when(userProgressRepository.upsertIfNewer(userId, item.itemType(), item.itemId(), item.status(),
                item.correctCount(), item.incorrectCount(), item.lastStudiedAt())).thenReturn(written);
    }

    @Test
    void syncAwardsOnlyTheItemsThatWereWritten() {
        ProgressItemRequest written = item("grammar_lesson", 1, T1);
        ProgressItemRequest skipped = item("vocab_quiz_topic", 2, T1);
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(stored("grammar_lesson", 1, T0)));
        givenUpsertResult(written, 1);
        givenUpsertResult(skipped, 0);

        SyncProgressResponse response = service.sync(userId, new SyncProgressRequest(List.of(written, skipped)));

        assertThat(response).isEqualTo(new SyncProgressResponse(2, 1));
        verify(expService).awardForProgress(userId, written, T0);
        verify(learningEngineService).recordQuizAttempt(userId, written);
        verify(expService, never()).awardForProgress(eq(userId), eq(skipped), any());
        verify(learningEngineService, never()).recordQuizAttempt(userId, skipped);
    }

    @Test
    void syncOfAnItemTheServerHasNeverSeenPassesNoPreviousTime() {
        ProgressItemRequest first = item("grammar_lesson", 7, T1);
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of());
        givenUpsertResult(first, 1);

        service.sync(userId, new SyncProgressRequest(List.of(first)));

        verify(expService).awardForProgress(eq(userId), eq(first), isNull());
    }

    @Test
    void duplicateItemsInOneBatchAreComparedWithTheirPreviousOccurrence() {
        ProgressItemRequest first = item("grammar_lesson", 1, T1);
        ProgressItemRequest second = item("grammar_lesson", 1, T2);
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(stored("grammar_lesson", 1, T0)));
        givenUpsertResult(first, 1);
        givenUpsertResult(second, 1);

        SyncProgressResponse response = service.sync(userId, new SyncProgressRequest(List.of(first, second)));

        assertThat(response).isEqualTo(new SyncProgressResponse(2, 2));
        verify(expService).awardForProgress(userId, first, T0);
        verify(expService).awardForProgress(userId, second, T1);
    }

    @Test
    void emptyBatchWritesNothing() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(service.sync(userId, new SyncProgressRequest(List.of()))).isEqualTo(new SyncProgressResponse(0, 0));
    }

    @Test
    void pullReturnsEveryStoredItem() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(stored("grammar_lesson", 1, T0)));

        List<ProgressItemResponse> items = service.pull(userId);

        assertThat(items).containsExactly(new ProgressItemResponse("grammar_lesson", 1, "completed", 2, 0, T0));
    }

    @Test
    void pullForAUserWithoutProgressIsEmpty() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(service.pull(userId)).isEmpty();
    }
}