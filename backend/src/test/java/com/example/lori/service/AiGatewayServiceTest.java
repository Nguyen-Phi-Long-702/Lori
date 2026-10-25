package com.example.lori.service;

import com.example.lori.dto.AiAnalyzeTextRequest;
import com.example.lori.dto.AiGrammarResponse;
import com.example.lori.dto.AiChatRequest;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiGatewayServiceTest {

    private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
    private final LearningContextService learningContextService = mock(LearningContextService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AiGatewayService service =
            new AiGatewayService(rateLimiter, learningContextService, aiServiceClient);
    private final UUID userId = UUID.randomUUID();

    @Test
    void chatSendsMessageWithChatContextToAiService() {
        LearningContext context = new LearningContext(null, null, null, null);
        AiChatResponse answer = new AiChatResponse("chat", "Hi", false);
        when(learningContextService.build(userId, AiTaskType.CHAT)).thenReturn(context);
        when(aiServiceClient.chat("Hello", context)).thenReturn(answer);

        assertThat(service.chat(userId, new AiChatRequest("Hello"))).isEqualTo(answer);
        verify(rateLimiter).checkChat(userId);
    }

    @Test
    void rateLimitedUserNeverReachesContextOrAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests")).when(rateLimiter).checkChat(userId);

        assertThatThrownBy(() -> service.chat(userId, new AiChatRequest("Hello"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(learningContextService, aiServiceClient);
    }

    @Test
    void analyzeGrammarSendsTextLessonsAndGrammarContextToAiService() {
        LearningContext context = new LearningContext(List.of(), null, null, null);
        Map<Integer, String> lessons = Map.of(1, "Present Simple");
        AiGrammarResponse analysis = new AiGrammarResponse("grammar_analysis", true, "I like tea.", List.of(), null);
        when(learningContextService.build(userId, AiTaskType.GRAMMAR)).thenReturn(context);
        when(learningContextService.grammarLessonNames()).thenReturn(lessons);
        when(aiServiceClient.analyzeGrammar("I like tea.", lessons, context)).thenReturn(analysis);

        assertThat(service.analyzeGrammar(userId, new AiAnalyzeTextRequest("grammar", "I like tea."))).isEqualTo(analysis);
        verify(rateLimiter).checkAnalyzeText(userId);
    }

    @Test
    void rateLimitedAnalyzeTextNeverReachesContextOrAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))
                .when(rateLimiter).checkAnalyzeText(userId);

        assertThatThrownBy(() -> service.analyzeGrammar(userId, new AiAnalyzeTextRequest("grammar", "I like tea.")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(learningContextService, aiServiceClient);
    }
}