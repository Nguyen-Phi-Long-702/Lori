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
    //Lưu tiến độ học, đồng thời ghi đè dữ liệu cũ nếu đã tồn tại
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserProgressLocal progress);

    //Lấy tiến độ của một nội dung học cụ thể
    @Query("SELECT * FROM user_progress_local WHERE item_type = :itemType AND item_id = :itemId LIMIT 1")
    UserProgressLocal getProgress(String itemType, int itemId);

    //Lấy các tiến độ chưa được đồng bộ lên sv
    @Query("SELECT * FROM user_progress_local WHERE is_synced = 0")
    List<UserProgressLocal> getUnsyncedProgress();

    //Cập nhật tiến độ hiện có
    @Update
    void update(UserProgressLocal progress);

    //Xóa một bản ghi tiến độ
    @Delete
    void delete(UserProgressLocal progress);
}