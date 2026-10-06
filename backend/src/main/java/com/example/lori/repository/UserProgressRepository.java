package com.example.lori.repository;

import com.example.lori.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
    /** Toan bo tien trinh cua mot user (moi (user, loai, muc) chi co 1 dong, so luong nho). */
    List<UserProgress> findByUserId(UUID userId);
}