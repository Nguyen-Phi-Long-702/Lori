package com.example.lori.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Anh xa bang exam_questions (V2__exam_tables.sql): mot cau hoi.
 * question_type: MULTIPLE_CHOICE / TRUE_FALSE_NOT_GIVEN / FILL_BLANK / MATCHING (CHECK o DB).
 * options: danh sach lua chon theo thu tu A, B, C... luu dang mang JSON trong cot TEXT.
 */
@Entity
@Table(name = "exam_questions")
@Getter
@Setter
@NoArgsConstructor
public class ExamQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Convert(converter = StringListConverter.class)
    @Column(name = "options", columnDefinition = "text")
    private List<String> options;

    @Column(name = "correct_answer", nullable = false, length = 255)
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;
}