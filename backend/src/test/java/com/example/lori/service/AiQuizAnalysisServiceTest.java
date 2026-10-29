package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiAnalysisResponse.Recommendation;
import com.example.lori.dto.AiQuizAnalysisRequest;
import com.example.lori.dto.AiQuizAnalysisResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.entity.UserProgress;
import com.example.lori.entity.UserWeaknessRecord;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.UserProgressRepository;
import com.example.lori.repository.UserWeaknessRecordRepository;
import com.example.lori.service.AiServiceClient.QuizHistory;
import com.example.lori.service.AiServiceClient.QuizPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiQuizAnalysisServiceTest {

    private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
    private final UserProgressRepository progressRepository = mock(UserProgressRepository.class);
    private final UserWeaknessRecordRepository weaknessRepository = mock(UserWeaknessRecordRepository.class);
    private final LearningContextService learningContextService = mock(LearningContextService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AiQuizAnalysisService service = new AiQuizAnalysisService(
            rateLimiter, progressRepository, weaknessRepository, learningContextService, aiServiceClient);
    private final UUID userId = UUID.randomUUID();
    private final AiAnalysisResponse analysis = new AiAnalysisResponse(
            "quiz_analysis", "Summary", List.of("Pattern"), List.of(new Recommendation("Review", "Weak topic", 11)));

    @Test
    void grammarQuizSendsResultHistoryLessonsAndContextToAiService() {
        LearningContext context = new LearningContext(List.of(), List.of(), null, null);
        when(progressRepository.findByUserIdAndItemTypeAndItemId(userId, "grammar_quiz_lesson", 11))
                .thenReturn(Optional.of(progress(3, 7)));
        when(weaknessRepository.findByUserIdAndCategoryAndTopicKey(userId, "grammar", "11"))
                .thenReturn(Optional.of(weakness(12, 20, "reviewing")));
        when(learningContextService.grammarLessonNames()).thenReturn(Map.of(11, "Present Perfect"));
        when(learningContextService.build(userId, AiTaskType.ANALYZE_QUIZ)).thenReturn(context);
        when(aiServiceClient.analyzeQuiz(any())).thenReturn(analysis);

        AiQuizAnalysisResponse response = service.analyze(userId, new AiQuizAnalysisRequest("grammar", 11));

        assertThat(response).isEqualTo(new AiQuizAnalysisResponse(
                "quiz_analysis", "Present Perfect", 3, 7, 30, "Summary", List.of("Pattern"), analysis.recommendations()));
        verify(rateLimiter).checkAnalyzeQuiz(userId);
        verify(aiServiceClient).analyzeQuiz(new QuizPayload("grammar", "Present Perfect", 3, 7, 30,
                new QuizHistory(12, 20, "reviewing"), Map.of(11, "Present Perfect"), context));
    }

    @Test
    void vocabularyQuizUsesVocabularyTypeCategoryAndTopicName() {
        when(progressRepository.findByUserIdAndItemTypeAndItemId(userId, "vocab_quiz_topic", 1))
                .thenReturn(Optional.of(progress(8, 2)));
        when(learningContextService.vocabTopicNames()).thenReturn(Map.of(1, "Gia dinh"));
        when(aiServiceClient.analyzeQuiz(any())).thenReturn(analysis);

        AiQuizAnalysisResponse response = service.analyze(userId, new AiQuizAnalysisRequest("vocabulary", 1));

        assertThat(response.title()).isEqualTo("Gia dinh");
        assertThat(response.accuracyPercent()).isEqualTo(80);
        verify(weaknessRepository).findByUserIdAndCategoryAndTopicKey(userId, "vocabulary", "1");
    }

    @Test
    void quizWithoutLearningEngineRecordHasNoHistoryAndFallbackTitle() {
        when(progressRepository.findByUserIdAndItemTypeAndItemId(userId, "grammar_quiz_lesson", 11))
                .thenReturn(Optional.of(progress(3, 7)));
        when(aiServiceClient.analyzeQuiz(any())).thenReturn(analysis);

        AiQuizAnalysisResponse response = service.analyze(userId, new AiQuizAnalysisRequest("grammar", 11));

        ArgumentCaptor<QuizPayload> sent = ArgumentCaptor.forClass(QuizPayload.class);
        verify(aiServiceClient).analyzeQuiz(sent.capture());
        assertThat(sent.getValue().history()).isNull();
        assertThat(response.title()).isEqualTo("Grammar lesson 11");
    }

    @Test
    void quizNotSyncedYetIs404AndNeverReachesAiService() {
        when(progressRepository.findByUserIdAndItemTypeAndItemId(userId, "grammar_quiz_lesson", 11))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(userId, new AiQuizAnalysisRequest("grammar", 11)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void progressWithoutAnswersIs404() {
        when(progressRepository.findByUserIdAndItemTypeAndItemId(userId, "grammar_quiz_lesson", 11))
                .thenReturn(Optional.of(progress(0, 0)));

        assertThatThrownBy(() -> service.analyze(userId, new AiQuizAnalysisRequest("grammar", 11)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void rateLimitedUserNeverReachesDatabaseOrAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))
                .when(rateLimiter).checkAnalyzeQuiz(userId);

        assertThatThrownBy(() -> service.analyze(userId, new AiQuizAnalysisRequest("grammar", 11)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(progressRepository, weaknessRepository, learningContextService, aiServiceClient);
    }

    private static UserProgress progress(int correct, int incorrect) {
        UserProgress progress = new UserProgress();
        progress.setCorrectCount(correct);
        progress.setIncorrectCount(incorrect);
        return progress;
    }

    private static UserWeaknessRecord weakness(int correct, int incorrect, String status) {
        UserWeaknessRecord weakness = new UserWeaknessRecord();
        weakness.setCorrectCount(correct);
        weakness.setIncorrectCount(incorrect);
        weakness.setStatus(status);
        return weakness;
    }
}