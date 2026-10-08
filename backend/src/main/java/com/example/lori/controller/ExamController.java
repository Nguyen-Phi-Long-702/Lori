package com.example.lori.controller;

import com.example.lori.dto.ExamDetailResponse;
import com.example.lori.dto.ExamResultDetailResponse;
import com.example.lori.dto.ExamResultResponse;
import com.example.lori.dto.ExamSummaryResponse;
import com.example.lori.dto.StartExamResponse;
import com.example.lori.dto.SubmitExamRequest;
import com.example.lori.security.RequirePremium;
import com.example.lori.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint /api/exams/*: yeu cau dang nhap (SecurityConfig: anyRequest().authenticated()).
 * @RequirePremium o cap class -> moi endpoint deu chan user Free bang 403 PREMIUM_REQUIRED.
 */
@RestController
@RequestMapping("/api/exams")
@RequirePremium
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @GetMapping
    public List<ExamSummaryResponse> list() {
        return examService.listExams();
    }

    @GetMapping("/{examId}")
    public ExamDetailResponse detail(@PathVariable UUID examId) {
        return examService.getExam(examId);
    }

    @PostMapping("/{examId}/start")
    public StartExamResponse start(@AuthenticationPrincipal UUID userId, @PathVariable UUID examId) {
        return examService.start(userId, examId);
    }

    @PostMapping("/{examId}/submit")
    public ExamResultResponse submit(@AuthenticationPrincipal UUID userId,
                                     @PathVariable UUID examId,
                                     @Valid @RequestBody SubmitExamRequest request) {
        return examService.submit(userId, examId, request);
    }

    @GetMapping("/results")
    public List<ExamResultResponse> results(@AuthenticationPrincipal UUID userId) {
        return examService.listResults(userId);
    }

    @GetMapping("/results/{resultId}")
    public ExamResultDetailResponse resultDetail(@AuthenticationPrincipal UUID userId,
                                                 @PathVariable UUID resultId) {
        return examService.getResultDetail(userId, resultId);
    }
}