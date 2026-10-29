package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiExamAnalysisRequest;
import com.example.lori.dto.AiExamAnalysisResponse;
import com.example.lori.dto.AiExamAnalysisResponse.PartStat;
import com.example.lori.dto.ExamDetailResponse;
import com.example.lori.dto.ExamResultDetailResponse;
import com.example.lori.dto.ExamResultResponse;
import com.example.lori.service.AiServiceClient.ExamPayload;
import com.example.lori.service.AiServiceClient.TypeStat;
import com.example.lori.service.AiServiceClient.WrongQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Module 5.9 AI Exam Analysis: chi DOC ket qua da cham cua ExamService (diem chinh thuc, dung/sai tung cau),
 * gom theo phan va loai cau hoi roi gui AI Service; AI khong cham lai, khong doi diem.
 * Khong gan @Transactional o day de khong giu ket noi DB trong luc cho LLM tra loi. Khong gui userId, resultId sang AI Service.
 */
@Service
@RequiredArgsConstructor
public class AiExamAnalysisService {

    static final int MAX_WRONG_QUESTIONS = 10;
    static final int MAX_QUESTION_LENGTH = 200;

    // Tieu de section TOEIC dang "Part 3 - Conversation 4 (Q71-Q73)": bo so thu tu va khoang cau de gom ve "Part 3 - Conversation"
    private static final Pattern TOEIC_SECTION_SUFFIX = Pattern.compile("( \\d+)? \\(Q\\d.*$");

    private final AiRateLimiter aiRateLimiter;
    private final ExamService examService;
    private final LearningContextService learningContextService;
    private final AiServiceClient aiServiceClient;

    public AiExamAnalysisResponse analyze(UUID userId, AiExamAnalysisRequest request) {
        aiRateLimiter.checkAnalyzeExam(userId);
        // Ket qua cua user khac hoac khong ton tai -> 404 (ExamService)
        ExamResultDetailResponse detail = examService.getResultDetail(userId, request.resultId());
        ExamResultResponse result = detail.result();
        ExamDetailResponse exam = examService.getExam(result.paperId());
        Map<UUID, ExamResultDetailResponse.Item> items = detail.items().stream()
                .collect(Collectors.toMap(ExamResultDetailResponse.Item::questionId, Function.identity()));

        // {so cau dung, so cau} theo phan va theo loai cau hoi; LinkedHashMap giu thu tu trong de
        Map<String, int[]> partCounts = new LinkedHashMap<>();
        Map<String, String> partSkills = new HashMap<>();
        Map<String, int[]> typeCounts = new LinkedHashMap<>();
        List<WrongQuestion> wrongQuestions = new ArrayList<>();
        for (ExamDetailResponse.Section section : exam.sections()) {
            String part = partName(result.examType(), section.title());
            partSkills.putIfAbsent(part, section.skill());
            for (ExamDetailResponse.Question question : section.questions()) {
                ExamResultDetailResponse.Item item = items.get(question.id());
                count(partCounts, part, item.correct());
                count(typeCounts, item.questionType(), item.correct());
                // Cau chi co audio (TOEIC Part 1-2) khong co noi dung de AI phan tich
                if (!item.correct() && item.questionText() != null && !item.questionText().isBlank()) {
                    wrongQuestions.add(new WrongQuestion(
                            part,
                            truncate(item.questionText().trim()),
                            choice(item.options(), item.yourAnswer()),
                            choice(item.options(), item.correctAnswer())));
                }
            }
        }

        List<PartStat> parts = partCounts.entrySet().stream()
                .map(entry -> new PartStat(entry.getKey(), partSkills.get(entry.getKey()),
                        entry.getValue()[0], entry.getValue()[1], percent(entry.getValue())))
                .sorted(Comparator.comparingInt(PartStat::accuracyPercent).thenComparing(PartStat::name))
                .toList();
        List<TypeStat> questionTypes = typeCounts.entrySet().stream()
                .map(entry -> new TypeStat(entry.getKey(), entry.getValue()[0], entry.getValue()[1], percent(entry.getValue())))
                .sorted(Comparator.comparingInt(TypeStat::accuracyPercent).thenComparing(TypeStat::name))
                .toList();

        // Cau sai cua phan yeu nhat truoc (sort on dinh nen cung phan van theo thu tu trong de), toi da MAX_WRONG_QUESTIONS cau
        Map<String, Integer> partRank = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            partRank.put(parts.get(i).name(), i);
        }
        wrongQuestions.sort(Comparator.comparingInt(wrong -> partRank.get(wrong.part())));

        AiAnalysisResponse analysis = aiServiceClient.analyzeExam(new ExamPayload(
                result.examType(),
                result.title(),
                result.totalScore(),
                result.listeningScore(),
                result.readingScore(),
                result.listeningCorrect(),
                result.listeningTotal(),
                result.readingCorrect(),
                result.readingTotal(),
                result.timeSpentSeconds(),
                parts,
                questionTypes,
                wrongQuestions.stream().limit(MAX_WRONG_QUESTIONS).toList(),
                learningContextService.grammarLessonNames(),
                learningContextService.build(userId, AiTaskType.ANALYZE_EXAM)));
        return new AiExamAnalysisResponse(
                analysis.type(), result.id(), parts, analysis.summary(), analysis.patterns(), analysis.recommendations());
    }

    // TOEIC gom cac section cung Part; de khac (IELTS) moi section la mot phan, giu nguyen tieu de
    static String partName(String examType, String sectionTitle) {
        return "TOEIC".equals(examType) ? TOEIC_SECTION_SUFFIX.matcher(sectionTitle).replaceFirst("") : sectionTitle;
    }

    // Dap an dang chu cai (A, B...) kem noi dung lua chon, vd "B: wrote"; dap an dang chu (FILL_BLANK, T/F/NG) giu nguyen; bo trong -> null
    static String choice(List<String> options, String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        if (options != null && answer.length() == 1) {
            int index = Character.toUpperCase(answer.charAt(0)) - 'A';
            if (index >= 0 && index < options.size()) {
                return answer.toUpperCase(Locale.ROOT) + ": " + options.get(index);
            }
        }
        return answer;
    }

    private static String truncate(String text) {
        return text.length() > MAX_QUESTION_LENGTH ? text.substring(0, MAX_QUESTION_LENGTH) : text;
    }

    private static void count(Map<String, int[]> counts, String key, boolean correct) {
        int[] count = counts.computeIfAbsent(key, k -> new int[2]);
        count[1]++;
        if (correct) {
            count[0]++;
        }
    }

    private static int percent(int[] count) {
        return (int) Math.round(100.0 * count[0] / count[1]);
    }
}