package com.example.lori.controller;

import com.example.lori.dto.ExamDetailResponse;
import com.example.lori.dto.ExamSummaryResponse;
import com.example.lori.security.RequirePremium;
import com.example.lori.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}