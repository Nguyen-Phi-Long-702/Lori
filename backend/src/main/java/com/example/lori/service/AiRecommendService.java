package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiRecommendResponse;
import com.example.lori.dto.LearningTopicResponse;
import com.example.lori.service.AiServiceClient.RecommendPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Module 5.8 Personalized Review: Learning Engine (code) chon toi da MAX_TOPICS chu de yeu nhat; AI chi viet nhan xet
 * va de xuat on tap, khong chon them chu de, khong doi so lieu. Chua co diem yeu thi khong goi AI.
 * Khong gan @Transactional o day de khong giu ket noi DB trong luc cho LLM tra loi. Khong gui userId sang AI Service.
 */
@Service
@RequiredArgsConstructor
public class AiRecommendService {

    static final int MAX_TOPICS = 3;

    private final AiRateLimiter aiRateLimiter;
    private final LearningEngineService learningEngineService;
    private final LearningContextService learningContextService;
    private final AiServiceClient aiServiceClient;

    public AiRecommendResponse recommend(UUID userId) {
        aiRateLimiter.checkRecommend(userId);
        List<LearningTopicResponse> topics = learningEngineService.getWeaknesses(userId).stream()
                .limit(MAX_TOPICS)
                .toList();
        if (topics.isEmpty()) {
            return new AiRecommendResponse("recommendation", topics, null, List.of());
        }

        AiAnalysisResponse plan = aiServiceClient.recommend(new RecommendPayload(
                topics,
                learningContextService.grammarLessonNames(),
                learningContextService.build(userId, AiTaskType.RECOMMEND)));
        return new AiRecommendResponse(plan.type(), topics, plan.summary(), plan.recommendations());
    }
}