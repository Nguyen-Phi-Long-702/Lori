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
 * Anh xa bang user_progress (V1__init_schema.sql): tien trinh hoc dong bo tu Android.
 * Moi (user_id, item_type, item_id) chi co 1 dong; xung dot giai quyet theo last_studied_at.
 */
@Entity
@Table(name = "user_progress")
@Getter
@Setter
@NoArgsConstructor
public class UserProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private int itemId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "last_studied_at", nullable = false)
    private Instant lastStudiedAt;
}