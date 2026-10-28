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
 * Anh xa bang user_weakness_record (V5__ai_learning_tables.sql): ket qua quiz cong don theo tung chu de cua user.
 * Moi (user_id, category, topic_key) chi co 1 dong; status do LearningEngineService quyet dinh.
 */
@Entity
@Table(name = "user_weakness_record")
@Getter
@Setter
@NoArgsConstructor
public class UserWeaknessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "topic_key", nullable = false, length = 100)
    private String topicKey;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;
}