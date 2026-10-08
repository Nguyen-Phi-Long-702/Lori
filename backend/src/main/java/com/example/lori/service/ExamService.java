package com.example.lori.service;

import com.example.lori.dto.ExamDetailResponse;
import com.example.lori.dto.ExamResultDetailResponse;
import com.example.lori.dto.ExamResultResponse;
import com.example.lori.dto.ExamSummaryResponse;
import com.example.lori.dto.StartExamResponse;
import com.example.lori.dto.SubmitExamRequest;
import com.example.lori.entity.ExamPaper;
import com.example.lori.entity.ExamQuestion;
import com.example.lori.entity.ExamResult;
import com.example.lori.entity.ExamSection;
import com.example.lori.exception.ApiException;
import com.example.lori.repository.ExamPaperRepository;
import com.example.lori.repository.ExamQuestionRepository;
import com.example.lori.repository.ExamResultRepository;
import com.example.lori.repository.ExamSectionRepository;
import com.example.lori.util.IeltsScoreUtil;
import com.example.lori.util.ToeicScoreUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nghiep vu de thi (/api/exams): danh sach, chi tiet, bat dau, nop bai + cham diem, lich su va xem lai.
 * Phan quyen Premium nam o PremiumAspect (@RequirePremium tren Controller), khong lap lai o day.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private static final String SESSION_KEY_PREFIX = "exam_session:";
    // Cho phep nop tre toi da 10 phut sau khi het gio lam bai (mang cham)
    private static final Duration SESSION_GRACE = Duration.ofMinutes(10);

    private static final String TYPE_TOEIC = "TOEIC";
    private static final String SKILL_LISTENING = "LISTENING";

    private final ExamPaperRepository examPaperRepository;
    private final ExamSectionRepository examSectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamResultRepository examResultRepository;
    private final StringRedisTemplate redisTemplate;

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

    // ===== Bat dau + nop bai =====

    /**
     * Bat dau lam bai: tao exam_session_token (UUID) luu Redis exam_session:{token} voi TTL = thoi gian lam bai + 10 phut.
     * Gia tri luu: "userId:examId:startedAtEpochSeconds" de nop bai kiem tra dung nguoi, dung de, va tinh thoi gian lam.
     */
    public StartExamResponse start(UUID userId, UUID examId) {
        ExamPaper paper = findPaper(examId);
        Instant startedAt = Instant.now();
        String token = UUID.randomUUID().toString();
        String value = userId + ":" + paper.getId() + ":" + startedAt.getEpochSecond();
        Duration ttl = Duration.ofMinutes(paper.getDurationMinutes()).plus(SESSION_GRACE);
        redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + token, value, ttl);
        return new StartExamResponse(token);
    }

    /**
     * Nop bai: xac thuc + xoa session (nop lan 2 -> bi tu choi), cham diem o Backend, luu ket qua.
     * Diem chinh thuc luon do Backend tinh, khong tin diem tu app gui len.
     */
    @Transactional
    public ExamResultResponse submit(UUID userId, UUID examId, SubmitExamRequest request) {
        ExamPaper paper = findPaper(examId);
        Instant startedAt = consumeSession(request.sessionToken(), userId, examId);
        PaperContent content = loadContent(paper);

        Map<UUID, String> given = new HashMap<>();
        for (SubmitExamRequest.Answer answer : request.answers()) {
            given.put(answer.questionId(), answer.answer());
        }

        int listeningCorrect = 0;
        int listeningTotal = 0;
        int readingCorrect = 0;
        int readingTotal = 0;
        Map<String, String> storedAnswers = new HashMap<>();
        for (ExamSection section : content.sections()) {
            boolean listening = SKILL_LISTENING.equals(section.getSkill());
            for (ExamQuestion question : content.questionsOf(section)) {
                String answer = given.get(question.getId());
                boolean correct = isCorrect(question, answer);
                if (answer != null && !answer.isBlank()) {
                    storedAnswers.put(question.getId().toString(), answer.trim());
                }
                if (listening) {
                    listeningTotal++;
                    if (correct) {
                        listeningCorrect++;
                    }
                } else {
                    readingTotal++;
                    if (correct) {
                        readingCorrect++;
                    }
                }
            }
        }

        BigDecimal listeningScore;
        BigDecimal readingScore;
        BigDecimal totalScore;
        if (TYPE_TOEIC.equals(paper.getExamType())) {
            int listening = ToeicScoreUtil.sectionScore(listeningCorrect, listeningTotal);
            int reading = ToeicScoreUtil.sectionScore(readingCorrect, readingTotal);
            listeningScore = toScore(listening);
            readingScore = toScore(reading);
            totalScore = toScore(listening + reading);
        } else {
            // exam_type chi co TOEIC hoac IELTS (CHECK o DB)
            double listening = IeltsScoreUtil.listeningBand(listeningCorrect, listeningTotal);
            double reading = IeltsScoreUtil.readingBand(readingCorrect, readingTotal);
            listeningScore = toScore(listening);
            readingScore = toScore(reading);
            totalScore = toScore(IeltsScoreUtil.overallBand(listening, reading));
        }

        ExamResult result = new ExamResult();
        result.setUserId(userId);
        result.setPaperId(paper.getId());
        result.setListeningCorrect(listeningCorrect);
        result.setListeningTotal(listeningTotal);
        result.setReadingCorrect(readingCorrect);
        result.setReadingTotal(readingTotal);
        result.setListeningScore(listeningScore);
        result.setReadingScore(readingScore);
        result.setTotalScore(totalScore);
        result.setTimeSpentSeconds((int) Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds()));
        result.setAnswers(storedAnswers);
        return toResultResponse(examResultRepository.save(result), paper);
    }

    // ===== Lich su + xem lai =====

    /** Lich su thi cua user, moi nhat truoc. */
    @Transactional(readOnly = true)
    public List<ExamResultResponse> listResults(UUID userId) {
        List<ExamResult> results = examResultRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        if (results.isEmpty()) {
            return List.of();
        }
        List<UUID> paperIds = results.stream().map(ExamResult::getPaperId).distinct().toList();
        Map<UUID, ExamPaper> papers = examPaperRepository.findAllById(paperIds).stream()
                .collect(Collectors.toMap(ExamPaper::getId, Function.identity()));
        return results.stream()
                .map(result -> toResultResponse(result, papers.get(result.getPaperId())))
                .toList();
    }

    /** Ket qua + xem lai tung cau. Ket qua cua user khac -> 404. */
    @Transactional(readOnly = true)
    public ExamResultDetailResponse getResultDetail(UUID userId, UUID resultId) {
        ExamResult result = examResultRepository.findByIdAndUserId(resultId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Result not found"));
        ExamPaper paper = findPaper(result.getPaperId());
        PaperContent content = loadContent(paper);

        List<ExamResultDetailResponse.Item> items = new ArrayList<>();
        for (ExamSection section : content.sections()) {
            for (ExamQuestion question : content.questionsOf(section)) {
                String yourAnswer = result.getAnswers().get(question.getId().toString());
                items.add(new ExamResultDetailResponse.Item(
                        question.getId(),
                        question.getQuestionType(),
                        question.getQuestionText(),
                        question.getOptions(),
                        yourAnswer,
                        question.getCorrectAnswer(),
                        isCorrect(question, yourAnswer),
                        question.getExplanation()));
            }
        }
        return new ExamResultDetailResponse(toResultResponse(result, paper), items);
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

    /**
     * Lay va XOA session trong 1 lenh (GETDEL) nen hai request nop cung luc chi co 1 request thanh cong.
     * Tra ve thoi diem bat dau; token sai/het han/khong thuoc user nay/khong thuoc de nay -> 400.
     */
    private Instant consumeSession(String sessionToken, UUID userId, UUID examId) {
        String value = redisTemplate.opsForValue().getAndDelete(SESSION_KEY_PREFIX + sessionToken);
        if (value != null) {
            String[] parts = value.split(":");
            if (parts.length == 3 && parts[0].equals(userId.toString()) && parts[1].equals(examId.toString())) {
                return Instant.ofEpochSecond(Long.parseLong(parts[2]));
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired exam session");
    }

    // So sanh khong phan biet hoa/thuong va khoang trang thua (cho FILL_BLANK, T/F/NG: "not given" = "NOT GIVEN")
    private static boolean isCorrect(ExamQuestion question, String answer) {
        return answer != null && normalize(answer).equals(normalize(question.getCorrectAnswer()));
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static BigDecimal toScore(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static ExamResultResponse toResultResponse(ExamResult result, ExamPaper paper) {
        return new ExamResultResponse(
                result.getId(),
                result.getPaperId(),
                paper.getExamType(),
                paper.getTitle(),
                result.getListeningCorrect() + result.getReadingCorrect(),
                result.getListeningTotal() + result.getReadingTotal(),
                result.getListeningCorrect(),
                result.getListeningTotal(),
                result.getReadingCorrect(),
                result.getReadingTotal(),
                result.getListeningScore(),
                result.getReadingScore(),
                result.getTotalScore(),
                result.getTimeSpentSeconds(),
                result.getSubmittedAt());
    }

    /** Section + cau hoi cua mot de, da sap xep. */
    private record PaperContent(List<ExamSection> sections, Map<UUID, List<ExamQuestion>> questionsBySection) {
        List<ExamQuestion> questionsOf(ExamSection section) {
            return questionsBySection.getOrDefault(section.getId(), List.of());
        }
    }
}