package com.example.lori.dto;

import java.util.List;

/**
 * Ket qua giai nghia tu: type = "vocabulary_analysis", words theo thu tu user nhap, difference la phan biet 2 tu
 * (null khi chi hoi 1 tu). Cung la dang JSON ma AI Service tra ve cho Backend.
 */
public record AiVocabularyResponse(String type, List<VocabularyWord> words, String difference)
        implements AiAnalyzeTextResponse {

    /**
     * Mot tu. inDictionary = true thi ipa va definitions lay tu dictionary.db cua Lori, con explanation,
     * collocations (va synonyms/antonyms neu tu dien khong co) la do AI sinh; inDictionary = false thi ipa = null,
     * definitions rong va nghia chi co o explanation cua AI.
     */
    public record VocabularyWord(
            String word, boolean inDictionary, String ipa, List<Definition> definitions, String explanation,
            List<String> synonyms, List<String> antonyms, List<String> collocations) {
    }

    /** Mot nghia trong tu dien: pos la ma tu loai cua tu dien (N, V, A...), example co the null. */
    public record Definition(String pos, String meaning, String example) {
    }
}