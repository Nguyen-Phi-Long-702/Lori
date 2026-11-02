package com.example.lori.service;

import com.example.lori.dto.AiAnalyzeTextRequest;
import com.example.lori.dto.AiAnalyzeTextResponse;
import com.example.lori.dto.AiChatRequest;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.AiConversationRequest;
import com.example.lori.dto.AiConversationResponse;
import com.example.lori.dto.AiGrammarResponse;
import com.example.lori.dto.AiVocabularyResponse;
import com.example.lori.dto.LearningContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cong AI: rate limit -> Learning Context theo AiContextPolicy -> goi AI Service.
 * Khong gan @Transactional o day de khong giu ket noi DB trong luc cho LLM tra loi.
 * Chi gui sang AI Service cau hoi/cau/tu can phan tich, lich su hoi thoai, danh sach bai ngu phap (chi khi kiem tra ngu phap)
 * va Learning Context, khong gui userId.
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

    public AiConversationResponse conversation(UUID userId, AiConversationRequest request) {
        aiRateLimiter.checkConversation(userId);
        // Learning Context chi dung cho nhan xet cuoi buoi; cac luot tro chuyen khong goi LearningContextService
        LearningContext context = request.finish() ? learningContextService.build(userId, AiTaskType.CONVERSATION) : null;
        return aiServiceClient.conversation(new AiServiceClient.ConversationPayload(
                request.mode(), request.messages(), request.finish(), context));
    }

    public AiAnalyzeTextResponse analyzeText(UUID userId, AiAnalyzeTextRequest request) {
        return "vocabulary".equals(request.type())
                ? analyzeVocabulary(userId, request)
                : analyzeGrammar(userId, request);
    }

    public AiGrammarResponse analyzeGrammar(UUID userId, AiAnalyzeTextRequest request) {
        aiRateLimiter.checkAnalyzeText(userId);
        LearningContext context = learningContextService.build(userId, AiTaskType.GRAMMAR);
        return aiServiceClient.analyzeGrammar(request.text(), learningContextService.grammarLessonNames(), context);
    }

    public AiVocabularyResponse analyzeVocabulary(UUID userId, AiAnalyzeTextRequest request) {
        aiRateLimiter.checkAnalyzeText(userId);
        LearningContext context = learningContextService.build(userId, AiTaskType.VOCABULARY);
        return aiServiceClient.analyzeVocabulary(request.text(), context);
    }
}