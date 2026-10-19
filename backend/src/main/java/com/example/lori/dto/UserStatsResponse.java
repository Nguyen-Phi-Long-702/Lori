package com.example.lori.dto;

/**
 * Thong ke hoc tap: so muc da hoan thanh theo loai + tong so cau dung/sai (tu user_progress),
 * cung tong EXP, cap do va streak (ngay hoc lien tiep hien tai / dai nhat).
 */
public record UserStatsResponse(
        long completedGrammarLessons,
        long completedVocabQuizTopics,
        long completedGrammarQuizLessons,
        long totalCorrect,
        long totalIncorrect,
        int totalExp,
        int level,
        int currentStreak,
        int longestStreak) {
}