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
 * Anh xa bang exam_sections (V2__exam_tables.sql): nhom cau hoi dung chung audio/doan van.
 * skill: LISTENING / READING. Chi giu paperId (khong dung quan he @ManyToOne) cho don gian.
 */
@Entity
@Table(name = "exam_sections")
@Getter
@Setter
@NoArgsConstructor
public class ExamSection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @Column(name = "skill", nullable = false, length = 20)
    private String skill;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "passage_text", columnDefinition = "text")
    private String passageText;
}