package com.example.lori.dto;

/** Thong ke tien trinh hoc: so muc da hoan thanh theo loai + tong so cau dung/sai. */
public record UserStatsResponse(
        long completedGrammarLessons,
        long completedVocabQuizTopics,
        long completedGrammarQuizLessons,
        long totalCorrect,
        long totalIncorrect) {
}