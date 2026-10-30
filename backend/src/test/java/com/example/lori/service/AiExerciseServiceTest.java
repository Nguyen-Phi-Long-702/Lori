package com.example.lori.service;

import com.example.lori.dto.AiExerciseResponse;
import com.example.lori.dto.AiExerciseResponse.Exercise;
import com.example.lori.dto.AiGenerateExerciseRequest;
import com.example.lori.dto.LearningContext;
import com.example.lori.dto.LearningProfileResponse;
import com.example.lori.entity.AiGeneratedExercise;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.AiGeneratedExerciseRepository;
import com.example.lori.service.AiServiceClient.ExercisePayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiExerciseServiceTest {

    private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
    private final LearningContextService learningContextService = mock(LearningContextService.class);
    private final LearningEngineService learningEngineService = mock(LearningEngineService.class);
    private final AiGeneratedExerciseRepository exerciseRepository = mock(AiGeneratedExerciseRepository.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AiExerciseService service = new AiExerciseService(
            rateLimiter, learningContextService, learningEngineService, exerciseRepository, aiServiceClient);
    private final UUID userId = UUID.randomUUID();
    private final Exercise multipleChoice =
            new Exercise("multiple_choice", "She ___ here.", List.of("is", "are", "am", "be"), 0, null, "Use is.");
    private final Exercise fillBlank =
            new Exercise("fill_blank", "They ___ home.", null, null, List.of("went"), "Past simple.");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void grammarSendsTopicNameLevelEarlierQuestionsAndContextThenSavesUnvalidated() {
        LearningContext context = new LearningContext(List.of(), List.of(), null, null);
        when(learningContextService.grammarLessonNames()).thenReturn(Map.of(11, "Present Perfect"));
        when(learningEngineService.getLearningProfile(userId))
                .thenReturn(new LearningProfileResponse("B1", List.of(), List.of()));
        when(learningContextService.build(userId, AiTaskType.GENERATE_EXERCISE)).thenReturn(context);
        when(exerciseRepository.findTop10ByUserIdAndSourceWeaknessOrderByCreatedAtDesc(userId, "grammar:11"))
                .thenReturn(List.of(saved(multipleChoice)));
        AiExerciseResponse generated = new AiExerciseResponse("exercise_set", List.of(multipleChoice, fillBlank));
        when(aiServiceClient.generateExercise(any())).thenReturn(generated);

        AiExerciseResponse response = service.generate(userId, new AiGenerateExerciseRequest("grammar", 11));

        assertThat(response).isEqualTo(generated);
        verify(rateLimiter).checkGenerateExercise(userId);
        verify(aiServiceClient).generateExercise(new ExercisePayload(
                "grammar", "Present Perfect", "B1", List.of("She ___ here."), context));
        ArgumentCaptor<List<AiGeneratedExercise>> rows = ArgumentCaptor.forClass((Class) List.class);
        verify(exerciseRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(2).allSatisfy(row -> {
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getSourceWeakness()).isEqualTo("grammar:11");
            assertThat(row.isValidated()).isFalse();
        });
        assertThat(rows.getValue()).extracting(AiGeneratedExercise::getExerciseType)
                .containsExactly("multiple_choice", "fill_blank");
        assertThat(rows.getValue()).extracting(AiGeneratedExercise::getContent)
                .containsExactly(multipleChoice, fillBlank);
    }

    @Test
    void vocabularyUsesVocabularyTopicNamesAndSourceWeakness() {
        when(learningContextService.vocabTopicNames()).thenReturn(Map.of(1, "Gia dinh"));
        when(learningEngineService.getLearningProfile(userId))
                .thenReturn(new LearningProfileResponse("A1", List.of(), List.of()));
        when(aiServiceClient.generateExercise(any()))
                .thenReturn(new AiExerciseResponse("exercise_set", List.of(fillBlank)));

        service.generate(userId, new AiGenerateExerciseRequest("vocabulary", 1));

        ArgumentCaptor<ExercisePayload> sent = ArgumentCaptor.forClass(ExercisePayload.class);
        verify(aiServiceClient).generateExercise(sent.capture());
        assertThat(sent.getValue().category()).isEqualTo("vocabulary");
        assertThat(sent.getValue().topic()).isEqualTo("Gia dinh");
        assertThat(sent.getValue().avoidQuestions()).isEmpty();
        verify(exerciseRepository).findTop10ByUserIdAndSourceWeaknessOrderByCreatedAtDesc(userId, "vocabulary:1");
    }

    @Test
    void unknownTopicIs400AndNeverReachesAiService() {
        when(learningContextService.grammarLessonNames()).thenReturn(Map.of(11, "Present Perfect"));

        assertThatThrownBy(() -> service.generate(userId, new AiGenerateExerciseRequest("grammar", 999)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(aiServiceClient, exerciseRepository);
    }

    @Test
    void aiServiceFailureSavesNothing() {
        when(learningContextService.grammarLessonNames()).thenReturn(Map.of(11, "Present Perfect"));
        when(learningEngineService.getLearningProfile(userId))
                .thenReturn(new LearningProfileResponse("A1", List.of(), List.of()));
        when(aiServiceClient.generateExercise(any()))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable"));

        assertThatThrownBy(() -> service.generate(userId, new AiGenerateExerciseRequest("grammar", 11)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        verify(exerciseRepository, never()).saveAll(any());
    }

    @Test
    void rateLimitedUserNeverReachesDatabaseOrAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))
                .when(rateLimiter).checkGenerateExercise(userId);

        assertThatThrownBy(() -> service.generate(userId, new AiGenerateExerciseRequest("grammar", 11)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(learningContextService, learningEngineService, exerciseRepository, aiServiceClient);
    }

    private static AiGeneratedExercise saved(Exercise exercise) {
        AiGeneratedExercise row = new AiGeneratedExercise();
        row.setContent(exercise);
        return row;
    }
}