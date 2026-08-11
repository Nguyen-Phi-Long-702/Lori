package com.example.lori.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.lori.data.local.entity.UserProgressLocal;

import java.util.List;

@Dao
public interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserProgressLocal progress);

    @Query("SELECT * FROM user_progress_local WHERE item_type = :itemType AND item_id = :itemId LIMIT 1")
    UserProgressLocal getProgress(String itemType, int itemId);

    @Query("SELECT * FROM user_progress_local WHERE is_synced = 0")
    List<UserProgressLocal> getUnsyncedProgress();

    @Update
    void update(UserProgressLocal progress);

    @Delete
    void delete(UserProgressLocal progress);
}