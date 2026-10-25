package com.example.lori.service;

import com.example.lori.dto.AiChatRequest;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.LearningContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cong AI: rate limit -> Learning Context theo AiContextPolicy -> goi AI Service.
 * Khong gan @Transactional o day de khong giu ket noi DB trong luc cho LLM tra loi.
 * Chi gui sang AI Service cau hoi va Learning Context, khong gui userId.
 */
@Service
@RequiredArgsConstructor
public class AiGatewayService {

    private final AiRateLimiter aiRateLimiter;
    private final LearningContextService learningContextService;
    private final AiServiceClient aiServiceClient;

    public AiChatResponse chat(UUID userId, AiChatRequest request) {
        aiRateLimiter.checkChat(userId);
        LearningContext context = learningContextService.build(userId, AiTaskType.CHAT);
        return aiServiceClient.chat(request.message(), context);
    }
}