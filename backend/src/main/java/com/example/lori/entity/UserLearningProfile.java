package com.example.lori.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh xa bang user_learning_profile (V5__ai_learning_tables.sql): moi user toi da 1 dong.
 * Chi anh xa cac cot Learning Engine dang dung; cot learning_goal chua co chuc nang nen chua anh xa.
 */
@Entity
@Table(name = "user_learning_profile")
@Getter
@Setter
@NoArgsConstructor
public class UserLearningProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "estimated_level", nullable = false, length = 2)
    private String estimatedLevel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}