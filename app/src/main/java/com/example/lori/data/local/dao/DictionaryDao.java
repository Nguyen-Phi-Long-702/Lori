package com.example.lori.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.DictionaryEntry;

import java.util.List;

@Dao
public interface DictionaryDao {
    @Insert
    void insertAll(List<DictionaryEntry> entries);

    @Query("SELECT * FROM dictionary WHERE word LIKE :prefix || '%' LIMIT 50")
    List<DictionaryEntry> searchByPrefix(String prefix);

    @Query("SELECT * FROM dictionary WHERE word = :word LIMIT 1")
    DictionaryEntry getExactWord(String word);

    @Update
    void update(DictionaryEntry entry);

    @Delete
    void delete(DictionaryEntry entry);
}