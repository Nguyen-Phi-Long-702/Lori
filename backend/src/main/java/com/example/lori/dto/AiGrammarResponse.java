package com.example.lori.dto;

import java.util.List;

/**
 * Ket qua phan tich ngu phap: type = "grammar_analysis", correctedSentence la cau da sua (cau dung thi giu nguyen),
 * errors rong khi correct = true, lessonId la id bai ngu phap lien quan trong dictionary.db (null neu khong co).
 * Cung la dang JSON ma AI Service tra ve cho Backend.
 */
public record AiGrammarResponse(
        String type, boolean correct, String correctedSentence, List<GrammarError> errors, Integer lessonId) {

    /** Mot loi: type la ten loi ngan gon (vd "Subject-verb agreement"), explanation la quy tac. */
    public record GrammarError(String type, String explanation) {
    }
}