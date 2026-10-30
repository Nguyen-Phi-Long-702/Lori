package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiAnalysisResponse.Recommendation;
import com.example.lori.dto.AiRecommendResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.dto.LearningTopicResponse;
import com.example.lori.exception.ApiException;
import com.example.lori.service.AiServiceClient.RecommendPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRecommendServiceTest {

    private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
    private final LearningEngineService learningEngineService = mock(LearningEngineService.class);
    private final LearningContextService learningContextService = mock(LearningContextService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AiRecommendService service =
            new AiRecommendService(rateLimiter, learningEngineService, learningContextService, aiServiceClient);
    private final UUID userId = UUID.randomUUID();

    @Test
    void sendsOnlyTheThreeWeakestTopicsAndKeepsEngineNumbers() {
        LearningContext context = new LearningContext(List.of(), List.of(), null, null);
        List<LearningTopicResponse> weaknesses = List.of(
                topic("grammar", "11", "Present Perfect", 20),
                topic("vocabulary", "1", "Gia dinh", 30),
                topic("grammar", "12", "Passive Voice", 40),
                topic("grammar", "13", "Conditionals", 50));
        AiAnalysisResponse plan = new AiAnalysisResponse("recommendation", "Focus on these.", List.of(),
                List.of(new Recommendation("Review Present Perfect", "Only 20% correct", 11)));
        when(learningEngineService.getWeaknesses(userId)).thenReturn(weaknesses);
        when(learningContextService.grammarLessonNames()).thenReturn(Map.of(11, "Present Perfect"));
        when(learningContextService.build(userId, AiTaskType.RECOMMEND)).thenReturn(context);
        when(aiServiceClient.recommend(any())).thenReturn(plan);

        AiRecommendResponse response = service.recommend(userId);

        List<LearningTopicResponse> top3 = weaknesses.subList(0, 3);
        assertThat(response).isEqualTo(new AiRecommendResponse(
                "recommendation", top3, "Focus on these.", plan.recommendations()));
        verify(rateLimiter).checkRecommend(userId);
        verify(aiServiceClient).recommend(new RecommendPayload(top3, Map.of(11, "Present Perfect"), context));
    }

    @Test
    void noWeaknessesReturnsEmptyResultWithoutCallingAiService() {
        when(learningEngineService.getWeaknesses(userId)).thenReturn(List.of());

        AiRecommendResponse response = service.recommend(userId);

        assertThat(response).isEqualTo(new AiRecommendResponse("recommendation", List.of(), null, List.of()));
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void rateLimitedUserNeverReachesDatabaseOrAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))
                .when(rateLimiter).checkRecommend(userId);

        assertThatThrownBy(() -> service.recommend(userId)).isInstanceOf(ApiException.class);
        verifyNoInteractions(learningEngineService, learningContextService, aiServiceClient);
    }

    private static LearningTopicResponse topic(String category, String key, String name, int accuracyPercent) {
        return new LearningTopicResponse(category, key, name, accuracyPercent, 100 - accuracyPercent, accuracyPercent);
    }
}