package com.example.lori.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.lifecycle.LiveData;

import com.example.lori.data.local.entity.UserProgressLocal;

import java.util.List;
import java.util.Date;

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

    //Lấy tiến độ của một nội dung cụ thể để giao diện tự cập nhật khi dữ liệu đổi
    @Query("SELECT * FROM user_progress_local WHERE item_type = :itemType AND item_id = :itemId LIMIT 1")
    LiveData<UserProgressLocal> observeProgress(String itemType, int itemId);

    //Lấy toàn bộ tiến độ theo loại nội dung, dùng để hiển thị trạng thái hoàn thành trong danh sách
    @Query("SELECT * FROM user_progress_local WHERE item_type = :itemType")
    LiveData<List<UserProgressLocal>> getProgressByType(String itemType);

    //Lấy toàn bộ bản ghi có ngày học gần nhất, dùng để tính lịch streak ở màn hồ sơ
    @Query("SELECT * FROM user_progress_local WHERE last_studied_at IS NOT NULL ORDER BY last_studied_at DESC")
    LiveData<List<UserProgressLocal>> getAllStudyDates();

    //Đánh dấu đã đồng bộ, CHỈ khi bản ghi vẫn còn đúng mốc lastStudiedAt đã gửi lên server
    //Nếu người dùng vừa học lại trong lúc đang gửi thì mốc đã khác → trả 0 và giữ is_synced = 0 để lần sau gửi tiếp
    @Query("UPDATE user_progress_local SET is_synced = 1 WHERE item_type = :itemType AND item_id = :itemId AND last_studied_at = :lastStudiedAt")
    int markSyncedIfUnchanged(String itemType, int itemId, Date lastStudiedAt);

    //Xóa toàn bộ tiến trình học trên máy (dùng khi đăng xuất)
    @Query("DELETE FROM user_progress_local")
    void deleteAll();
}