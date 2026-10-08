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
 * Anh xa bang exam_papers (V2__exam_tables.sql): mot de thi TOEIC hoac IELTS.
 * exam_type: TOEIC / IELTS; difficulty: EASY / MEDIUM / HARD (CHECK o DB).
 */
@Entity
@Table(name = "exam_papers")
@Getter
@Setter
@NoArgsConstructor
public class ExamPaper {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_type", nullable = false, length = 20)
    private String examType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "difficulty", nullable = false, length = 20)
    private String difficulty;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
}