package com.example.lori.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.lori.data.local.entity.MyWord;

import java.util.List;

@Dao
public interface MyWordDao {
    //Từ đã có (trùng word) thì bị bỏ qua. Trả về id dòng cho từng từ theo đúng thứ tự; từ bị bỏ qua có id = -1
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long[] insertAll(List<MyWord> words);

    //Dùng để kiểm tra trong test
    @Query("SELECT COUNT(*) FROM my_words")
    int count();
}