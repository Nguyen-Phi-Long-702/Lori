package com.example.lori.entity;

import com.example.lori.dto.AiExerciseResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh xa bang ai_generated_exercise (V5__ai_learning_tables.sql): moi dong la 1 bai tap do AI sinh.
 * source_weakness = "grammar:<id bai>" hoac "vocabulary:<id chu de>". Field "validated" <-> cot is_validated,
 * mac dinh false cho den khi nguoi kiem tra xac nhan.
 */
@Entity
@Table(name = "ai_generated_exercise")
@Getter
@Setter
@NoArgsConstructor
public class AiGeneratedExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "exercise_type", nullable = false, length = 50)
    private String exerciseType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    private AiExerciseResponse.Exercise content;

    @Column(name = "source_weakness", length = 100)
    private String sourceWeakness;

    @Column(name = "is_validated", nullable = false)
    private boolean validated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}