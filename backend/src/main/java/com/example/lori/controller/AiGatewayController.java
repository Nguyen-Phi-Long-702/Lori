package com.example.lori.controller;

import com.example.lori.dto.AiAnalyzeTextRequest;
import com.example.lori.dto.AiAnalyzeTextResponse;
import com.example.lori.dto.AiChatRequest;
import com.example.lori.dto.AiChatResponse;
import com.example.lori.dto.AiExamAnalysisRequest;
import com.example.lori.dto.AiExamAnalysisResponse;
import com.example.lori.dto.AiQuizAnalysisRequest;
import com.example.lori.dto.AiQuizAnalysisResponse;
import com.example.lori.security.RequirePremium;
import com.example.lori.service.AiExamAnalysisService;
import com.example.lori.service.AiGatewayService;
import com.example.lori.service.AiQuizAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint /api/ai/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()).
 * @RequirePremium o cap class -> moi endpoint AI deu chan user Free bang 403 PREMIUM_REQUIRED.
 */
@RestController
@RequestMapping("/api/ai")
@RequirePremium
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiGatewayService aiGatewayService;
    private final AiQuizAnalysisService aiQuizAnalysisService;
    private final AiExamAnalysisService aiExamAnalysisService;

    @PostMapping("/chat")
    public AiChatResponse chat(@AuthenticationPrincipal UUID userId, @Valid @RequestBody AiChatRequest request) {
        return aiGatewayService.chat(userId, request);
    }

    @PostMapping("/analyze-text")
    public AiAnalyzeTextResponse analyzeText(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody AiAnalyzeTextRequest request) {
        return aiGatewayService.analyzeText(userId, request);
    }

    @PostMapping("/analyze-quiz")
    public AiQuizAnalysisResponse analyzeQuiz(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody AiQuizAnalysisRequest request) {
        return aiQuizAnalysisService.analyze(userId, request);
    }

    @PostMapping("/analyze-exam")
    public AiExamAnalysisResponse analyzeExam(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody AiExamAnalysisRequest request) {
        return aiExamAnalysisService.analyze(userId, request);
    }
}