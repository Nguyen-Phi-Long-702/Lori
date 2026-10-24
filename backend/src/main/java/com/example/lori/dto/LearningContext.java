package com.example.lori.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Learning Context gui sang AI Service. Khong co id, email hay token cua user.
 * Truong null = tac vu nay khong duoc nhan phan do (AiContextPolicy) nen khong xuat hien trong JSON;
 * danh sach rong = duoc nhan nhung user chua co du lieu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningContext(
        List<WeakItem> weakGrammar,
        List<WeakItem> weakVocabulary,
        List<RecentActivity> recentActivity,
        List<RecentExam> recentExams) {

    /** Mot bai ngu phap / chu de tu vung user lam quiz con yeu; accuracyPercent = ty le tra loi dung (0-100). */
    public record WeakItem(String name, int correctCount, int incorrectCount, int accuracyPercent) {
    }

    /** Mot muc hoc gan day; type = "grammar lesson" / "grammar quiz" / "vocabulary quiz". */
    public record RecentActivity(String type, String name, int correctCount, int incorrectCount) {
    }

    /** Ket qua mot lan thi; totalScore la diem quy doi TOEIC (10-990) hoac band IELTS (0-9). */
    public record RecentExam(String examType, String title, BigDecimal totalScore,
                             int listeningCorrect, int listeningTotal,
                             int readingCorrect, int readingTotal, Instant submittedAt) {
    }
}