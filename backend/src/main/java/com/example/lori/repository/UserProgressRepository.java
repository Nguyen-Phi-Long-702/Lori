package com.example.lori.repository;

import com.example.lori.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
    /** Toan bo tien trinh cua mot user (moi (user, loai, muc) chi co 1 dong, so luong nho). */
    List<UserProgress> findByUserId(UUID userId);

    /**
     * Them moi, hoac ghi de CHI KHI last_studied_at gui len MOI HON ban tren server (bang nhau/cu hon thi giu ban server).
     * Mot cau lenh duy nhat nen khong bi loi trung khoa khi 2 thiet bi dong bo cung luc.
     * Tra ve 1 neu da ghi, 0 neu bi bo qua.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_progress
                (user_id, item_type, item_id, status, correct_count, incorrect_count, last_studied_at)
            VALUES
                (:userId, :itemType, :itemId, :status, :correctCount, :incorrectCount, :lastStudiedAt)
            ON CONFLICT (user_id, item_type, item_id) DO UPDATE SET
                status = EXCLUDED.status,
                correct_count = EXCLUDED.correct_count,
                incorrect_count = EXCLUDED.incorrect_count,
                last_studied_at = EXCLUDED.last_studied_at
            WHERE user_progress.last_studied_at < EXCLUDED.last_studied_at
            """, nativeQuery = true)
    int upsertIfNewer(@Param("userId") UUID userId,
                      @Param("itemType") String itemType,
                      @Param("itemId") int itemId,
                      @Param("status") String status,
                      @Param("correctCount") int correctCount,
                      @Param("incorrectCount") int incorrectCount,
                      @Param("lastStudiedAt") Instant lastStudiedAt);
}