package com.example.lori.service;

import com.example.lori.dto.ExamDetailResponse;
import com.example.lori.dto.ExamSummaryResponse;
import com.example.lori.entity.ExamPaper;
import com.example.lori.entity.ExamQuestion;
import com.example.lori.entity.ExamSection;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.ExamPaperRepository;
import com.example.lori.repository.ExamQuestionRepository;
import com.example.lori.repository.ExamSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nghiep vu de thi (/api/exams): danh sach, chi tiet, bat dau, nop bai + cham diem, lich su va xem lai.
 * Phan quyen Premium nam o PremiumAspect (@RequirePremium tren Controller), khong lap lai o day.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamPaperRepository examPaperRepository;
    private final ExamSectionRepository examSectionRepository;
    private final ExamQuestionRepository examQuestionRepository;

    // ===== Danh sach + chi tiet =====

    /** Danh sach de, chi la thong tin tom tat (khong chua cau hoi). */
    @Transactional(readOnly = true)
    public List<ExamSummaryResponse> listExams() {
        List<ExamPaper> papers = examPaperRepository.findAllByOrderByExamTypeAscTitleAsc();
        if (papers.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> totals = countQuestions(papers);
        return papers.stream()
                .map(paper -> new ExamSummaryResponse(
                        paper.getId(),
                        paper.getExamType(),
                        paper.getTitle(),
                        paper.getDifficulty(),
                        paper.getDurationMinutes(),
                        totals.getOrDefault(paper.getId(), 0)))
                .toList();
    }

    /** Toan bo section + cau hoi cua mot de de lam bai (khong co dap an dung). */
    @Transactional(readOnly = true)
    public ExamDetailResponse getExam(UUID examId) {
        ExamPaper paper = findPaper(examId);
        PaperContent content = loadContent(paper);

        List<ExamDetailResponse.Section> sections = content.sections().stream()
                .map(section -> new ExamDetailResponse.Section(
                        section.getId(),
                        section.getSkill(),
                        section.getTitle(),
                        section.getOrderIndex(),
                        section.getAudioUrl(),
                        section.getPassageText(),
                        content.questionsOf(section).stream()
                                .map(question -> new ExamDetailResponse.Question(
                                        question.getId(),
                                        question.getOrderIndex(),
                                        question.getQuestionType(),
                                        question.getQuestionText(),
                                        question.getOptions()))
                                .toList()))
                .toList();
        return new ExamDetailResponse(
                paper.getId(),
                paper.getExamType(),
                paper.getTitle(),
                paper.getDifficulty(),
                paper.getDurationMinutes(),
                sections);
    }

    // ===== Ham dung chung =====

    private ExamPaper findPaper(UUID examId) {
        return examPaperRepository.findById(examId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exam not found"));
    }

    /** So cau hoi cua tung de, bang 1 truy van (de khong goi lai tung de). */
    private Map<UUID, Integer> countQuestions(List<ExamPaper> papers) {
        List<UUID> paperIds = papers.stream().map(ExamPaper::getId).toList();
        Map<UUID, Integer> totals = new HashMap<>();
        for (Object[] row : examQuestionRepository.countByPaperIds(paperIds)) {
            totals.put(UUID.fromString(String.valueOf(row[0])), ((Number) row[1]).intValue());
        }
        return totals;
    }

    /** Doc section (theo thu tu) va cau hoi (theo thu tu trong tung section) cua mot de. */
    private PaperContent loadContent(ExamPaper paper) {
        List<ExamSection> sections = examSectionRepository.findByPaperIdOrderByOrderIndexAsc(paper.getId());
        if (sections.isEmpty()) {
            return new PaperContent(sections, Map.of());
        }
        List<UUID> sectionIds = sections.stream().map(ExamSection::getId).toList();
        Map<UUID, List<ExamQuestion>> questionsBySection = examQuestionRepository.findBySectionIdIn(sectionIds).stream()
                .sorted(Comparator.comparingInt(ExamQuestion::getOrderIndex))
                .collect(Collectors.groupingBy(ExamQuestion::getSectionId));
        return new PaperContent(sections, questionsBySection);
    }

    /** Section + cau hoi cua mot de, da sap xep. */
    private record PaperContent(List<ExamSection> sections, Map<UUID, List<ExamQuestion>> questionsBySection) {
        List<ExamQuestion> questionsOf(ExamSection section) {
            return questionsBySection.getOrDefault(section.getId(), List.of());
        }
    }
}