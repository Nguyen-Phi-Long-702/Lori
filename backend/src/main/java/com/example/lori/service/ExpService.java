package com.example.lori.service;

import com.example.lori.dto.ProgressItemRequest;
import com.example.lori.repository.ExamResultRepository;
import com.example.lori.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * He thong EXP: bang quy doi EXP, cong EXP, cap do (level).
 * Moi lan cong EXP deu di qua award(). Cac ham awardFor... duoc goi trong transaction cua noi goi
 * (ProgressService.sync, ExamService.submit) nen loi o buoc nao thi ca lan do duoc hoan tac.
 */
@Service
@RequiredArgsConstructor
public class ExpService {

    // ===== Bang quy doi EXP (ke hoach Tuan 8) =====
    static final int GRAMMAR_LESSON_EXP = 15;
    static final int VOCAB_QUIZ_MIN_EXP = 10;
    static final int VOCAB_QUIZ_MAX_EXP = 30;
    static final int GRAMMAR_QUIZ_MIN_EXP = 15;
    static final int GRAMMAR_QUIZ_MAX_EXP = 40;
    static final int EXAM_MIN_EXP = 50;
    static final int EXAM_MAX_EXP = 200;
    /** Thuong moi ngay hoc moi (chi cong 1 lan cho moi ngay). */
    static final int STREAK_DAY_EXP = 20;

    /** Du lieu hoc chi duoc tinh EXP/streak neu ngay hoc nam trong khoang tu 7 ngay truoc den hom nay (chong gian lan ngay). */
    static final int MAX_BACKDATE_DAYS = 7;

    // Gia tri item_type / status do app Android ghi vao user_progress_local (khop UserService)
    private static final String STATUS_COMPLETED = "completed";
    private static final String TYPE_GRAMMAR_LESSON = "grammar_lesson";
    private static final String TYPE_VOCAB_QUIZ = "vocab_quiz_topic";
    private static final String TYPE_GRAMMAR_QUIZ = "grammar_quiz_lesson";

    private final UserRepository userRepository;
    private final StreakService streakService;
    private final ExamResultRepository examResultRepository;
    private final LeaderboardService leaderboardService;

    // ===== Doc EXP / level =====

    /** Tong EXP hien tai cua user (0 neu khong tim thay). */
    @Transactional(readOnly = true)
    public int getTotalExp(UUID userId) {
        Integer totalExp = userRepository.findTotalExp(userId);
        return totalExp == null ? 0 : totalExp;
    }

    /** Cap do tu tong EXP: bat dau tu cap 1, cap n can toi thieu expToReach(n) EXP tich luy. */
    public static int levelOf(int totalExp) {
        int level = 1;
        while (totalExp >= expToReach(level + 1)) {
            level++;
        }
        return level;
    }

    /** EXP tich luy can de dat cap n: 100 * n * (n - 1) / 2 (cap 2 = 100, cap 3 = 300, cap 5 = 1.000, cap 10 = 4.500). */
    static long expToReach(int level) {
        return 50L * level * (level - 1);
    }

    // ===== Cong EXP =====

    /**
     * Cong EXP cho user (amount <= 0 thi bo qua), roi cap nhat bang xep hang SAU KHI transaction commit:
     * neu transaction bi hoan tac thi EXP khong duoc ghi o DB va bang xep hang cung khong bi cong nham.
     * (award chi duoc goi tu cac ham @Transactional ben duoi nen luon co transaction dang chay.)
     */
    private void award(UUID userId, int amount) {
        if (amount > 0) {
            userRepository.addExp(userId, amount);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    leaderboardService.updateScore(userId, amount);
                }
            });
        }
    }

    /**
     * Tinh EXP + streak cho 1 muc tien trinh vua duoc ghi len server (ProgressService goi sau khi upsert co hieu luc).
     * previousStudiedAt = lastStudiedAt truoc do cua muc nay tren server, null neu day la muc moi.
     * - Ngay hoc (gio VN) ngoai khoang [hom nay - 7 ngay, hom nay]: khong tinh gi (tien trinh van duoc dong bo binh thuong).
     * - Ngay hoc hop le: ghi ngay hoc; la ngay moi thi +20 EXP.
     * - Muc "completed" thuoc 3 loai da biet: cong EXP cua muc, nhung moi muc toi da 1 lan moi ngay.
     */
    @Transactional
    public void awardForProgress(UUID userId, ProgressItemRequest item, Instant previousStudiedAt) {
        LocalDate today = StreakService.today();
        LocalDate day = item.lastStudiedAt().atZone(StreakService.ZONE).toLocalDate();
        if (day.isAfter(today) || day.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            return;
        }
        recordStudyDay(userId, day);

        if (!STATUS_COMPLETED.equals(item.status())) {
            return;
        }
        if (previousStudiedAt != null
                && !previousStudiedAt.atZone(StreakService.ZONE).toLocalDate().isBefore(day)) {
            return; // muc nay da duoc tinh EXP trong ngay hoc nay
        }
        award(userId, progressExp(item.itemType(), item.correctCount(), item.incorrectCount()));
    }

    /**
     * Tinh EXP + streak cho 1 lan nop bai thi (ExamService goi TRUOC KHI luu ket qua cua lan nop nay).
     * Ngay hoc = hom nay (gio VN). EXP de thi 50-200 theo ti le cau dung; moi de toi da 1 lan moi ngay.
     */
    @Transactional
    public void awardForExam(UUID userId, UUID paperId, int correct, int total) {
        LocalDate today = StreakService.today();
        recordStudyDay(userId, today);

        Instant startOfToday = today.atStartOfDay(StreakService.ZONE).toInstant();
        if (examResultRepository.existsByUserIdAndPaperIdAndSubmittedAtGreaterThanEqual(userId, paperId, startOfToday)) {
            return; // de nay da nop va duoc tinh EXP trong hom nay
        }
        award(userId, scaled(EXAM_MIN_EXP, EXAM_MAX_EXP, correct, total));
    }

    // ===== Ham dung chung =====

    // Ghi ngay hoc; la ngay moi thi cong thuong streak
    private void recordStudyDay(UUID userId, LocalDate day) {
        if (streakService.recordStudyDay(userId, day)) {
            award(userId, STREAK_DAY_EXP);
        }
    }

    /** EXP cua 1 muc tien trinh da hoan thanh; loai muc khong nam trong bang quy doi thi 0. */
    static int progressExp(String itemType, int correct, int incorrect) {
        return switch (itemType) {
            case TYPE_GRAMMAR_LESSON -> GRAMMAR_LESSON_EXP;
            case TYPE_VOCAB_QUIZ -> scaled(VOCAB_QUIZ_MIN_EXP, VOCAB_QUIZ_MAX_EXP, correct, correct + incorrect);
            case TYPE_GRAMMAR_QUIZ -> scaled(GRAMMAR_QUIZ_MIN_EXP, GRAMMAR_QUIZ_MAX_EXP, correct, correct + incorrect);
            default -> 0;
        };
    }

    /** min + (max - min) * correct / total, lam tron; total = 0 thi tra ve min. */
    static int scaled(int min, int max, int correct, int total) {
        if (total <= 0) {
            return min;
        }
        return min + (int) Math.round((double) (max - min) * correct / total);
    }
}