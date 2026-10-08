package com.example.lori.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Anh xa bang exam_results (V2__exam_tables.sql): ket qua mot lan nop bai.
 * Diem TOEIC la diem quy doi (5-495 moi ky nang, tong 10-990); diem IELTS la band (0-9).
 * answers: {"<questionId>": "<dap an user chon>"}, chi chua cau da tra loi.
 */
@Entity
@Table(name = "exam_results")
@Getter
@Setter
@NoArgsConstructor
public class ExamResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @Column(name = "listening_correct", nullable = false)
    private int listeningCorrect;

    @Column(name = "listening_total", nullable = false)
    private int listeningTotal;

    @Column(name = "reading_correct", nullable = false)
    private int readingCorrect;

    @Column(name = "reading_total", nullable = false)
    private int readingTotal;

    @Column(name = "listening_score", nullable = false, precision = 5, scale = 1)
    private BigDecimal listeningScore;

    @Column(name = "reading_score", nullable = false, precision = 5, scale = 1)
    private BigDecimal readingScore;

    @Column(name = "total_score", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalScore;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    @Convert(converter = StringMapConverter.class)
    @Column(name = "answers", nullable = false, columnDefinition = "text")
    private Map<String, String> answers;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @PrePersist
    void onCreate() {
        submittedAt = Instant.now();
    }
}