package com.example.lori.repository;

import com.example.lori.entity.ExamSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamSectionRepository extends JpaRepository<ExamSection, UUID> {
    /** Cac section cua mot de, theo thu tu hien thi. */
    List<ExamSection> findByPaperIdOrderByOrderIndexAsc(UUID paperId);
}