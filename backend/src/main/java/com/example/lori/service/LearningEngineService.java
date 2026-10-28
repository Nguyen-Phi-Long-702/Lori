package com.example.lori.service;

import com.example.lori.dto.LearningProfileResponse;
import com.example.lori.dto.LearningTopicResponse;
import com.example.lori.dto.ProgressItemRequest;
import com.example.lori.entity.UserLearningProfile;
import com.example.lori.entity.UserWeaknessRecord;
import com.example.lori.repository.UserLearningProfileRepository;
import com.example.lori.repository.UserWeaknessRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Learning Engine (ke hoach Tuan 13, module 5.5): diem yeu do code quyet dinh, LLM khong tham gia.
 * Moi lan mot quiz co ket qua moi duoc dong bo len server (ProgressService.sync), so cau dung/sai duoc cong don vao
 * user_weakness_record va status duoc tinh lai; quiz ngu phap con cap nhat trinh do uoc tinh o user_learning_profile.
 * topic_key = id bai ngu phap / id chu de tu vung (ten hien thi lay tu LearningContextService).
 * user_progress chi giu ket qua lan lam gan nhat cua moi quiz, nen so cau dung/sai phai duoc cong don o day.
 */
@Service
@RequiredArgsConstructor
public class LearningEngineService {

    private static final String CATEGORY_GRAMMAR = "grammar";
    private static final String CATEGORY_VOCABULARY = "vocabulary";

    private static final String STATUS_LEARNING = "learning";
    private static final String STATUS_REVIEWING = "reviewing";
    private static final String STATUS_MASTERED = "mastered";

    // Gia tri item_type do app Android ghi vao user_progress_local (khop UserService, ExpService)
    private static final String TYPE_VOCAB_QUIZ = "vocab_quiz_topic";
    private static final String TYPE_GRAMMAR_QUIZ = "grammar_quiz_lesson";

    // ===== Nguong diem yeu (ke hoach Tuan 13: sai >= 3 lan va ty le dung < 60%) =====
    private static final int WEAK_MIN_INCORRECT = 3;
    private static final int WEAK_ACCURACY_PERCENT = 60;
    private static final int MASTERED_ACCURACY_PERCENT = 90;

    // ===== Uoc tinh trinh do tu quiz ngu phap, chia 3 nhom bai theo cot level cua grammar_lessons trong dictionary.db =====
    private static final int GRAMMAR_INTERMEDIATE_FROM_ID = 11;
    private static final int GRAMMAR_ADVANCED_FROM_ID = 23;
    private static final int LEVEL_MIN_ANSWERS = 10;
    private static final int LEVEL_PASS_PERCENT = 60;
    private static final int LEVEL_STRONG_PERCENT = 80;
    private static final String DEFAULT_LEVEL = "A1";

    private static final Comparator<LearningTopicResponse> LOWEST_ACCURACY_FIRST =
            Comparator.comparingInt(LearningTopicResponse::accuracyPercent)
                    .thenComparing(LearningTopicResponse::topicName);

    private final UserWeaknessRecordRepository weaknessRepository;
    private final UserLearningProfileRepository profileRepository;
    private final LearningContextService learningContextService;

    // ===== Ghi nhan ket qua quiz =====

    /**
     * Cong don ket qua 1 lan lam quiz vao user_weakness_record (ProgressService goi sau khi muc tien trinh duoc ghi).
     * Muc khong phai quiz ngu phap / tu vung, hoac khong co cau tra loi nao, thi bo qua.
     */
    @Transactional
    public void recordQuizAttempt(UUID userId, ProgressItemRequest item) {
        String category = categoryOf(item.itemType());
        if (category == null || item.correctCount() + item.incorrectCount() == 0) {
            return;
        }
        String topicKey = String.valueOf(item.itemId());
        // shortcut: 2 thiet bi dong bo cung luc lan dau co the dung khoa duy nhat, lan dong bo ke tiep se chay lai binh thuong
        UserWeaknessRecord weakness = weaknessRepository
                .findByUserIdAndCategoryAndTopicKey(userId, category, topicKey)
                .orElseGet(() -> {
                    UserWeaknessRecord created = new UserWeaknessRecord();
                    created.setUserId(userId);
                    created.setCategory(category);
                    created.setTopicKey(topicKey);
                    return created;
                });
        weakness.setCorrectCount(weakness.getCorrectCount() + item.correctCount());
        weakness.setIncorrectCount(weakness.getIncorrectCount() + item.incorrectCount());
        weakness.setStatus(statusOf(weakness.getCorrectCount(), weakness.getIncorrectCount()));
        weakness.setLastReviewedAt(item.lastStudiedAt());
        weaknessRepository.save(weakness);

        if (CATEGORY_GRAMMAR.equals(category)) {
            updateEstimatedLevel(userId);
        }
    }

    // ===== Doc diem yeu / ho so =====

    /** Cac chu de dang yeu (status "reviewing"), ty le dung thap nhat truoc. */
    @Transactional(readOnly = true)
    public List<LearningTopicResponse> getWeaknesses(UUID userId) {
        return topicsWithStatus(weaknessRepository.findByUserId(userId), STATUS_REVIEWING, LOWEST_ACCURACY_FIRST);
    }

    @Transactional(readOnly = true)
    public LearningProfileResponse getLearningProfile(UUID userId) {
        List<UserWeaknessRecord> records = weaknessRepository.findByUserId(userId);
        String level = profileRepository.findByUserId(userId)
                .map(UserLearningProfile::getEstimatedLevel)
                .orElse(DEFAULT_LEVEL);
        return new LearningProfileResponse(
                level,
                topicsWithStatus(records, STATUS_MASTERED, LOWEST_ACCURACY_FIRST.reversed()),
                topicsWithStatus(records, STATUS_REVIEWING, LOWEST_ACCURACY_FIRST));
    }

    // ===== Quy tac quyet dinh =====

    /**
     * Yeu ("reviewing"): sai tu 3 cau tro len VA ty le dung duoi 60%. Gioi ("mastered"): ty le dung tu 90%.
     * Con lai la "learning". So sanh bang so nguyen de khong bi sai so khi lam tron.
     */
    static String statusOf(int correct, int incorrect) {
        int total = correct + incorrect;
        if (total == 0) {
            return STATUS_LEARNING;
        }
        if (incorrect >= WEAK_MIN_INCORRECT && correct * 100 < WEAK_ACCURACY_PERCENT * total) {
            return STATUS_REVIEWING;
        }
        if (correct * 100 >= MASTERED_ACCURACY_PERCENT * total) {
            return STATUS_MASTERED;
        }
        return STATUS_LEARNING;
    }

    /**
     * Trinh do uoc tinh tu ket qua cong don cua cac bai ngu phap; moi nhom bai can it nhat 10 cau da tra loi moi duoc tinh.
     * C1: nhom nang cao >= 80%. B2: nhom trung cap >= 80%. B1: nhom trung cap >= 60%. A2: nhom co ban >= 60%. Con lai A1.
     * shortcut: uoc tinh tho theo 3 nhom bai, nang cap khi co du lieu de kiem chung.
     */
    static String levelOf(List<UserWeaknessRecord> grammarRecords) {
        int[] correct = new int[3];
        int[] total = new int[3];
        for (UserWeaknessRecord weakness : grammarRecords) {
            int tier = grammarTierOf(Integer.parseInt(weakness.getTopicKey()));
            correct[tier] += weakness.getCorrectCount();
            total[tier] += weakness.getCorrectCount() + weakness.getIncorrectCount();
        }
        if (reaches(correct[2], total[2], LEVEL_STRONG_PERCENT)) {
            return "C1";
        }
        if (reaches(correct[1], total[1], LEVEL_STRONG_PERCENT)) {
            return "B2";
        }
        if (reaches(correct[1], total[1], LEVEL_PASS_PERCENT)) {
            return "B1";
        }
        if (reaches(correct[0], total[0], LEVEL_PASS_PERCENT)) {
            return "A2";
        }
        return DEFAULT_LEVEL;
    }

    // ===== Ham dung chung =====

    // 0 = co ban, 1 = trung cap, 2 = nang cao
    private static int grammarTierOf(int lessonId) {
        if (lessonId >= GRAMMAR_ADVANCED_FROM_ID) {
            return 2;
        }
        return lessonId >= GRAMMAR_INTERMEDIATE_FROM_ID ? 1 : 0;
    }

    private static boolean reaches(int correct, int total, int percent) {
        return total >= LEVEL_MIN_ANSWERS && correct * 100 >= percent * total;
    }

    // Loai muc tien trinh -> nhom chu de theo doi; null neu khong phai quiz ngu phap / tu vung
    private static String categoryOf(String itemType) {
        return switch (itemType) {
            case TYPE_GRAMMAR_QUIZ -> CATEGORY_GRAMMAR;
            case TYPE_VOCAB_QUIZ -> CATEGORY_VOCABULARY;
            default -> null;
        };
    }

    private void updateEstimatedLevel(UUID userId) {
        List<UserWeaknessRecord> grammarRecords = weaknessRepository.findByUserIdAndCategory(userId, CATEGORY_GRAMMAR);
        UserLearningProfile profile = profileRepository.findByUserId(userId).orElseGet(() -> {
            UserLearningProfile created = new UserLearningProfile();
            created.setUserId(userId);
            return created;
        });
        profile.setEstimatedLevel(levelOf(grammarRecords));
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private List<LearningTopicResponse> topicsWithStatus(List<UserWeaknessRecord> records, String status,
                                                         Comparator<LearningTopicResponse> order) {
        return records.stream()
                .filter(weakness -> status.equals(weakness.getStatus()))
                .map(this::toResponse)
                .sorted(order)
                .toList();
    }

    private LearningTopicResponse toResponse(UserWeaknessRecord weakness) {
        int id = Integer.parseInt(weakness.getTopicKey());
        String name = CATEGORY_GRAMMAR.equals(weakness.getCategory())
                ? learningContextService.grammarLessonNames().getOrDefault(id, "Grammar lesson " + id)
                : learningContextService.vocabTopicNames().getOrDefault(id, "Vocabulary topic " + id);
        int total = weakness.getCorrectCount() + weakness.getIncorrectCount();
        return new LearningTopicResponse(
                weakness.getCategory(),
                weakness.getTopicKey(),
                name,
                weakness.getCorrectCount(),
                weakness.getIncorrectCount(),
                weakness.getCorrectCount() * 100 / total);
    }
}