package com.example.lori.repository;

import com.example.lori.entity.AiGeneratedExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiGeneratedExerciseRepository extends JpaRepository<AiGeneratedExercise, UUID> {

    /** 10 bai gan nhat da sinh cho 1 chu de cua 1 user, de dua cho AI tranh lap lai. */
    List<AiGeneratedExercise> findTop10ByUserIdAndSourceWeaknessOrderByCreatedAtDesc(UUID userId, String sourceWeakness);
}