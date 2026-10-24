package com.example.lori.service;

import org.springframework.stereotype.Component;

import java.util.Set;

import static com.example.lori.service.AiContextField.RECENT_ACTIVITY;
import static com.example.lori.service.AiContextField.RECENT_EXAMS;
import static com.example.lori.service.AiContextField.WEAK_GRAMMAR;
import static com.example.lori.service.AiContextField.WEAK_VOCABULARY;

/**
 * Quy dinh moi tac vu AI duoc nhan phan Learning Context nao (nguyen tac toi thieu: tac vu khong can thi khong duoc).
 * Them AiTaskType moi ma chua them nhanh o day thi bien dich loi, nen khong the quen khai bao.
 */
@Component
public class AiContextPolicy {

    public Set<AiContextField> allowedFields(AiTaskType taskType) {
        return switch (taskType) {
            case CHAT, RECOMMEND -> Set.of(WEAK_GRAMMAR, WEAK_VOCABULARY, RECENT_ACTIVITY);
            case GRAMMAR -> Set.of(WEAK_GRAMMAR);
            case VOCABULARY -> Set.of(WEAK_VOCABULARY);
            case GENERATE_EXERCISE, ANALYZE_QUIZ, CONVERSATION -> Set.of(WEAK_GRAMMAR, WEAK_VOCABULARY);
            case ANALYZE_EXAM -> Set.of(WEAK_GRAMMAR, WEAK_VOCABULARY, RECENT_EXAMS);
            case IDENTIFY_IMAGE -> Set.of();
        };
    }
}