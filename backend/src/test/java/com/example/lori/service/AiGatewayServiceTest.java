package com.example.lori.service;

import com.example.lori.dto.AiChatRequest;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.LearningContext;
import com.example.lori.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

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
}