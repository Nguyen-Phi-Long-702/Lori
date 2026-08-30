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
    //Lưu danh sách từ vựng vào db
    @Insert
    void insertWords(List<DictionaryWord> words);

    //Lưu danh sách phát âm vào db
    @Insert
    void insertPronunciations(List<WordPronunciation> pronunciations);

    //Lưu danh sách định nghĩa vào db
    @Insert
    void insertDefinitions(List<Definition> definitions);

    //Lưu danh sách quan hệ từ vựng và định nghĩa vào db
    @Insert
    void insertWordDefinitions(List<WordDefinition> wordDefinitions);

    //Lưu danh sách quan hệ giữa các từ(đồng nghĩa, trái nghĩa) vào db
    @Insert
    void insertWordRelations(List<WordRelation> relations);

    //Tìm 1 từ trong db theo từ khóa
    @Query("SELECT * FROM words WHERE lang_code = 'en' AND word = :word LIMIT 1")
    DictionaryWord getExactWord(String word);

    //Tìm các từ tiếng Anh bắt đầu bằng chuỗi được nhập, tối đa 50 kết quả
    @Query("SELECT * FROM words WHERE lang_code = 'en' AND word LIKE :prefix || '%' ORDER BY word LIMIT 50")
    List<DictionaryWord> searchByPrefix(String prefix);

    //Lấy toàn bộ thông tin phát âm của một từ
    @Query("SELECT * FROM pronunciations WHERE word_id = :wordId")
    List<WordPronunciation> getPronunciations(int wordId);

    //Lấy danh sách định nghĩa kèm từ loại và câu ví dụ của một từ
    @Query("SELECT d.definition AS definition, d.pos AS pos, wd.example AS example " +
            "FROM word_definitions wd " +
            "JOIN definitions d ON d.id = wd.definition_id " +
            "WHERE wd.word_id = :wordId " +
            "ORDER BY wd.id")
    List<DictionaryDefinition> getDefinitions(int wordId);

    //Lấy danh sách từ đồng nghĩa của một từ
    @Query("SELECT related_word FROM word_relations WHERE word_id = :wordId AND relation_type = 's'")
    List<String> getSynonyms(int wordId);

    //Lấy danh sách từ trái nghĩa của một từ
    @Query("SELECT related_word FROM word_relations WHERE word_id = :wordId AND relation_type = 'a'")
    List<String> getAntonyms(int wordId);
}