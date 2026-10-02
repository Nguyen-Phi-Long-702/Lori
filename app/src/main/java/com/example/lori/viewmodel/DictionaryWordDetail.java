package com.example.lori.viewmodel;

import com.example.lori.data.local.entity.DictionaryDefinition;

import java.util.List;

public class DictionaryWordDetail {
    public final String word;
    public final String ipa;
    public final List<DictionaryDefinition> definitions;
    public final List<String> synonyms;
    public final List<String> antonyms;

    public DictionaryWordDetail(String word, String ipa, List<DictionaryDefinition> definitions, List<String> synonyms, List<String> antonyms) {
        this.word = word;
        this.ipa = ipa;
        this.definitions = definitions;
        this.synonyms = synonyms;
        this.antonyms = antonyms;
    }
}