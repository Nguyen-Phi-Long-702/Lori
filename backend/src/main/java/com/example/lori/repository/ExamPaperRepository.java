package com.example.lori.repository;

import com.example.lori.entity.ExamPaper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, UUID> {
    /** Toan bo de, xep theo loai (IELTS truoc TOEIC) roi theo tieu de. */
    List<ExamPaper> findAllByOrderByExamTypeAscTitleAsc();
}