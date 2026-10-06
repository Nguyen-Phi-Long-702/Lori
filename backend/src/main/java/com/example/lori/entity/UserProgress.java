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

import java.util.UUID;

/**
 * Anh xa bang user_progress, chi gom cac cot dung de thong ke (/api/users/me/stats).
 * Cot item_id, last_studied_at se duoc anh xa khi lam Progress Sync (Tuan 5).
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

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;
}