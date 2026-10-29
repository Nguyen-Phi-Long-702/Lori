package com.example.lori.dto;

import java.util.List;

/**
 * Phan phan tich do AI viet cho quiz / bai thi: type = "quiz_analysis" hoac "exam_analysis", summary la nhan xet chung,
 * patterns la cac loi lap lai (co the rong), recommendations la cac viec nen on, quan trong nhat truoc.
 * Cung la dang JSON ma AI Service tra ve cho Backend; con so/diem do Backend tinh, khong nam trong day.
 */
public record AiAnalysisResponse(
        String type, String summary, List<String> patterns, List<Recommendation> recommendations) {

    /** Mot de xuat on tap: lessonId la id bai ngu phap lien quan trong dictionary.db (null neu khong co). */
    public record Recommendation(String title, String reason, Integer lessonId) {
    }
}