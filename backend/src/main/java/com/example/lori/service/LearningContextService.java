package com.example.lori.service;

import com.example.lori.dto.LearningContext;
import com.example.lori.dto.LearningContext.RecentActivity;
import com.example.lori.dto.LearningContext.RecentExam;
import com.example.lori.dto.LearningContext.WeakItem;
import com.example.lori.entity.ExamPaper;
import com.example.lori.entity.ExamResult;
import com.example.lori.entity.UserProgress;
import com.example.lori.repository.ExamPaperRepository;
import com.example.lori.repository.ExamResultRepository;
import com.example.lori.repository.UserProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tong hop user_progress va exam_results thanh Learning Context suc tich cho 1 tac vu AI.
 * Chi lay du lieu cua cac phan AiContextPolicy cho phep; ket qua khong chua id, email hay token cua user.
 */
@Service
public class LearningContextService {

    private static final String NAMES_PATH = "ai/learning-names.json";

    // Gia tri item_type do app Android ghi vao user_progress_local (khop UserService, ExpService)
    private static final String TYPE_GRAMMAR_LESSON = "grammar_lesson";
    private static final String TYPE_VOCAB_QUIZ = "vocab_quiz_topic";
    private static final String TYPE_GRAMMAR_QUIZ = "grammar_quiz_lesson";

    /** Quiz co ty le dung duoi muc nay (%) duoc coi la yeu (nguong vi du cua ke hoach Tuan 13). */
    static final int WEAK_ACCURACY_PERCENT = 60;
    static final int MAX_WEAK_ITEMS = 5;
    static final int MAX_RECENT_ACTIVITY = 10;
    static final int MAX_RECENT_EXAMS = 3;

    private final UserProgressRepository userProgressRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamPaperRepository examPaperRepository;
    private final AiContextPolicy aiContextPolicy;
    private final Names names;

    public LearningContextService(UserProgressRepository userProgressRepository,
                                  ExamResultRepository examResultRepository,
                                  ExamPaperRepository examPaperRepository,
                                  AiContextPolicy aiContextPolicy,
                                  ObjectMapper objectMapper) {
        this.userProgressRepository = userProgressRepository;
        this.examResultRepository = examResultRepository;
        this.examPaperRepository = examPaperRepository;
        this.aiContextPolicy = aiContextPolicy;
        try (InputStream in = new ClassPathResource(NAMES_PATH).getInputStream()) {
            this.names = objectMapper.readValue(in, Names.class);
        } catch (IOException e) {
            // Thieu/hong file ten la loi cau hinh: de app dung ngay luc khoi dong thay vi chay sai
            throw new IllegalStateException("Cannot read " + NAMES_PATH, e);
        }
    }

    /** Id va ten cac bai ngu phap trong dictionary.db, de AI chon bai lien quan toi loi cua user. */
    public Map<Integer, String> grammarLessonNames() {
        return names.grammarLessons();
    }

    @Transactional(readOnly = true)
    public LearningContext build(UUID userId, AiTaskType taskType) {
        Set<AiContextField> allowed = aiContextPolicy.allowedFields(taskType);

        boolean needsProgress = allowed.contains(AiContextField.WEAK_GRAMMAR)
                || allowed.contains(AiContextField.WEAK_VOCABULARY)
                || allowed.contains(AiContextField.RECENT_ACTIVITY);
        List<UserProgress> progress = needsProgress ? userProgressRepository.findByUserId(userId) : List.of();

        return new LearningContext(
                allowed.contains(AiContextField.WEAK_GRAMMAR)
                        ? weakItems(progress, TYPE_GRAMMAR_QUIZ, names.grammarLessons(), "Grammar lesson ") : null,
                allowed.contains(AiContextField.WEAK_VOCABULARY)
                        ? weakItems(progress, TYPE_VOCAB_QUIZ, names.vocabTopics(), "Vocabulary topic ") : null,
                allowed.contains(AiContextField.RECENT_ACTIVITY) ? recentActivity(progress) : null,
                allowed.contains(AiContextField.RECENT_EXAMS) ? recentExams(userId) : null);
    }

    // Quiz co ty le dung < 60%, yeu nhat truoc, toi da MAX_WEAK_ITEMS muc
    private static List<WeakItem> weakItems(List<UserProgress> progress, String itemType,
                                            Map<Integer, String> nameById, String fallbackPrefix) {
        return progress.stream()
                .filter(p -> itemType.equals(p.getItemType()) && p.getCorrectCount() + p.getIncorrectCount() > 0)
                .map(p -> new WeakItem(
                        nameById.getOrDefault(p.getItemId(), fallbackPrefix + p.getItemId()),
                        p.getCorrectCount(),
                        p.getIncorrectCount(),
                        (int) Math.round(100.0 * p.getCorrectCount() / (p.getCorrectCount() + p.getIncorrectCount()))))
                .filter(item -> item.accuracyPercent() < WEAK_ACCURACY_PERCENT)
                .sorted(Comparator.comparingInt(WeakItem::accuracyPercent)
                        .thenComparing(WeakItem::incorrectCount, Comparator.reverseOrder())
                        .thenComparing(WeakItem::name))
                .limit(MAX_WEAK_ITEMS)
                .toList();
    }

    // MAX_RECENT_ACTIVITY muc hoc gan nhat (3 loai da biet), moi nhat truoc
    private List<RecentActivity> recentActivity(List<UserProgress> progress) {
        return progress.stream()
                .filter(p -> activityLabel(p.getItemType()) != null)
                .sorted(Comparator.comparing(UserProgress::getLastStudiedAt).reversed())
                .limit(MAX_RECENT_ACTIVITY)
                .map(p -> new RecentActivity(
                        activityLabel(p.getItemType()),
                        activityName(p),
                        p.getCorrectCount(),
                        p.getIncorrectCount()))
                .toList();
    }

    private List<RecentExam> recentExams(UUID userId) {
        List<ExamResult> results = examResultRepository.findByUserIdOrderBySubmittedAtDesc(userId).stream()
                .limit(MAX_RECENT_EXAMS)
                .toList();
        if (results.isEmpty()) {
            return List.of();
        }
        List<UUID> paperIds = results.stream().map(ExamResult::getPaperId).distinct().toList();
        Map<UUID, ExamPaper> papers = examPaperRepository.findAllById(paperIds).stream()
                .collect(Collectors.toMap(ExamPaper::getId, Function.identity()));
        return results.stream()
                .map(result -> {
                    ExamPaper paper = papers.get(result.getPaperId());
                    return new RecentExam(
                            paper.getExamType(),
                            paper.getTitle(),
                            result.getTotalScore(),
                            result.getListeningCorrect(),
                            result.getListeningTotal(),
                            result.getReadingCorrect(),
                            result.getReadingTotal(),
                            result.getSubmittedAt());
                })
                .toList();
    }

    // Loai muc hoc duoi dang chu cho AI doc; null neu khong phai 3 loai da biet
    private static String activityLabel(String itemType) {
        return switch (itemType) {
            case TYPE_GRAMMAR_LESSON -> "grammar lesson";
            case TYPE_GRAMMAR_QUIZ -> "grammar quiz";
            case TYPE_VOCAB_QUIZ -> "vocabulary quiz";
            default -> null;
        };
    }

    private String activityName(UserProgress progress) {
        int id = progress.getItemId();
        return switch (progress.getItemType()) {
            case TYPE_VOCAB_QUIZ -> names.vocabTopics().getOrDefault(id, "Vocabulary topic " + id);
            default -> names.grammarLessons().getOrDefault(id, "Grammar lesson " + id);
        };
    }

    /** Cau truc file ai/learning-names.json: ten theo id trong dictionary.db cua app. */
    public record Names(Map<Integer, String> grammarLessons, Map<Integer, String> vocabTopics) {
    }
}