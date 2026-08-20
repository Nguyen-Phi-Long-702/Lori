package com.example.lori.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.lori.data.local.entity.Definition;
import com.example.lori.data.local.entity.DictionaryDefinition;
import com.example.lori.data.local.entity.DictionaryWord;
import com.example.lori.data.local.entity.WordDefinition;
import com.example.lori.data.local.entity.WordPronunciation;
import com.example.lori.data.local.entity.WordRelation;

import java.util.List;

@Dao
public interface DictionaryDao {

    @Insert
    void insertWords(List<DictionaryWord> words);

    @Insert
    void insertPronunciations(List<WordPronunciation> pronunciations);

    @Insert
    void insertDefinitions(List<Definition> definitions);

    @Insert
    void insertWordDefinitions(List<WordDefinition> wordDefinitions);

    @Insert
    void insertWordRelations(List<WordRelation> relations);

    @Query("SELECT * FROM words WHERE lang_code = 'en' AND word = :word LIMIT 1")
    DictionaryWord getExactWord(String word);

    @Query("SELECT * FROM words WHERE lang_code = 'en' AND word LIKE :prefix || '%' ORDER BY word LIMIT 50")
    List<DictionaryWord> searchByPrefix(String prefix);

    @Query("SELECT * FROM pronunciations WHERE word_id = :wordId")
    List<WordPronunciation> getPronunciations(int wordId);

    @Query("SELECT d.definition AS definition, d.pos AS pos, wd.example AS example " +
            "FROM word_definitions wd " +
            "JOIN definitions d ON d.id = wd.definition_id " +
            "WHERE wd.word_id = :wordId " +
            "ORDER BY wd.id")
    List<DictionaryDefinition> getDefinitions(int wordId);

    @Query("SELECT related_word FROM word_relations WHERE word_id = :wordId AND relation_type = 's'")
    List<String> getSynonyms(int wordId);

    @Query("SELECT related_word FROM word_relations WHERE word_id = :wordId AND relation_type = 'a'")
    List<String> getAntonyms(int wordId);
}