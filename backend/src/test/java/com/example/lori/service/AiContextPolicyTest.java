package com.example.lori.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.example.lori.service.AiContextField.RECENT_ACTIVITY;
import static com.example.lori.service.AiContextField.RECENT_EXAMS;
import static com.example.lori.service.AiContextField.WEAK_GRAMMAR;
import static com.example.lori.service.AiContextField.WEAK_VOCABULARY;
import static org.assertj.core.api.Assertions.assertThat;

class AiContextPolicyTest {

    private final AiContextPolicy policy = new AiContextPolicy();

    @Test
    void chatGetsWeakItemsAndRecentActivityButNoExams() {
        assertThat(policy.allowedFields(AiTaskType.CHAT))
                .containsExactlyInAnyOrder(WEAK_GRAMMAR, WEAK_VOCABULARY, RECENT_ACTIVITY);
    }

    @Test
    void grammarGetsOnlyWeakGrammar() {
        assertThat(policy.allowedFields(AiTaskType.GRAMMAR)).containsExactly(WEAK_GRAMMAR);
    }

    @Test
    void vocabularyGetsOnlyWeakVocabulary() {
        assertThat(policy.allowedFields(AiTaskType.VOCABULARY)).containsExactly(WEAK_VOCABULARY);
    }

    @ParameterizedTest
    @EnumSource(value = AiTaskType.class, names = {"RECOMMEND", "GENERATE_EXERCISE", "ANALYZE_QUIZ", "CONVERSATION"})
    void practiceTasksGetOnlyWeakGrammarAndWeakVocabulary(AiTaskType taskType) {
        assertThat(policy.allowedFields(taskType)).containsExactlyInAnyOrder(WEAK_GRAMMAR, WEAK_VOCABULARY);
    }

    @Test
    void examAnalysisAlsoGetsRecentExams() {
        assertThat(policy.allowedFields(AiTaskType.ANALYZE_EXAM))
                .containsExactlyInAnyOrder(WEAK_GRAMMAR, WEAK_VOCABULARY, RECENT_EXAMS);
    }

    @Test
    void imageIdentificationGetsNoLearningData() {
        assertThat(policy.allowedFields(AiTaskType.IDENTIFY_IMAGE)).isEmpty();
    }
}