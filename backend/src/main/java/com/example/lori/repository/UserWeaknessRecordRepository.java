package com.example.lori.repository;

import com.example.lori.entity.UserWeaknessRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserWeaknessRecordRepository extends JpaRepository<UserWeaknessRecord, UUID> {

    /** Toan bo chu de dang theo doi cua mot user (toi da 30 bai ngu phap + 67 chu de tu vung). */
    List<UserWeaknessRecord> findByUserId(UUID userId);

    List<UserWeaknessRecord> findByUserIdAndCategory(UUID userId, String category);

    Optional<UserWeaknessRecord> findByUserIdAndCategoryAndTopicKey(UUID userId, String category, String topicKey);
}