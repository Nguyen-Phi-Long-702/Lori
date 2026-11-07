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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LearningContextServiceTest {

    private static final String GRAMMAR_LESSON = "grammar_lesson";
    private static final String GRAMMAR_QUIZ = "grammar_quiz_lesson";
    private static final String VOCAB_QUIZ = "vocab_quiz_topic";
    private static final Instant BASE_TIME = Instant.parse("2026-11-01T00:00:00Z");

    private final UserProgressRepository userProgressRepository = mock(UserProgressRepository.class);
    private final ExamResultRepository examResultRepository = mock(ExamResultRepository.class);
    private final ExamPaperRepository examPaperRepository = mock(ExamPaperRepository.class);
    private final LearningContextService service = new LearningContextService(
            userProgressRepository, examResultRepository, examPaperRepository, new AiContextPolicy(), new ObjectMapper());
    private final UUID userId = UUID.randomUUID();

    // ===== file ten bai / chu de =====

    @Test
    void namesAreLoadedFromTheJsonFile() {
        assertThat(service.grammarLessonNames()).isNotEmpty().containsKey(1);
        assertThat(service.vocabTopicNames()).isNotEmpty().containsKey(1);
    }

    @Test
    void unreadableNamesFileStopsStartup() throws Exception {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.readValue(any(InputStream.class), eq(LearningContextService.Names.class)))
                .thenThrow(new IOException("broken"));

        assertThatThrownBy(() -> new LearningContextService(
                userProgressRepository, examResultRepository, examPaperRepository, new AiContextPolicy(), brokenMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai/learning-names.json");
    }

    // ===== build: phan nao duoc nhan theo AiContextPolicy =====

    @Test
    void imageIdentificationGetsNoContextAndReadsNoData() {
        LearningContext context = service.build(userId, AiTaskType.IDENTIFY_IMAGE);

        assertThat(context).isEqualTo(new LearningContext(null, null, null, null));
        verifyNoInteractions(userProgressRepository, examResultRepository, examPaperRepository);
    }

    @Test
    void grammarTaskGetsOnlyWeakGrammarQuizzes() {
        String lessonName = service.grammarLessonNames().get(1);
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(
                progress(GRAMMAR_QUIZ, 1, 1, 9, BASE_TIME),
                progress(GRAMMAR_QUIZ, 101, 3, 7, BASE_TIME),
                progress(GRAMMAR_QUIZ, 102, 6, 4, BASE_TIME),
                progress(GRAMMAR_QUIZ, 103, 0, 0, BASE_TIME),
                progress(VOCAB_QUIZ, 201, 1, 9, BASE_TIME)));

        LearningContext context = service.build(userId, AiTaskType.GRAMMAR);

        assertThat(context.weakGrammar()).containsExactly(
                new WeakItem(lessonName, 1, 9, 10),
                new WeakItem("Grammar lesson 101", 3, 7, 30));
        assertThat(context.weakVocabulary()).isNull();
        assertThat(context.recentActivity()).isNull();
        assertThat(context.recentExams()).isNull();
        verifyNoInteractions(examResultRepository, examPaperRepository);
    }

    @Test
    void vocabularyTaskGetsOnlyWeakVocabularyQuizzes() {
        String topicName = service.vocabTopicNames().get(1);
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(
                progress(VOCAB_QUIZ, 201, 2, 8, BASE_TIME),
                progress(VOCAB_QUIZ, 1, 1, 9, BASE_TIME),
                progress(GRAMMAR_QUIZ, 101, 1, 9, BASE_TIME)));

        LearningContext context = service.build(userId, AiTaskType.VOCABULARY);

        assertThat(context.weakVocabulary()).containsExactly(
                new WeakItem(topicName, 1, 9, 10),
                new WeakItem("Vocabulary topic 201", 2, 8, 20));
        assertThat(context.weakGrammar()).isNull();
        assertThat(context.recentActivity()).isNull();
        assertThat(context.recentExams()).isNull();
    }

    @Test
    void weakItemsAreWeakestFirstTieBrokenByMoreWrongThenNameAndLimitedToFive() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(
                progress(GRAMMAR_QUIZ, 101, 5, 5, BASE_TIME),
                progress(GRAMMAR_QUIZ, 102, 2, 8, BASE_TIME),
                progress(GRAMMAR_QUIZ, 103, 4, 16, BASE_TIME),
                progress(GRAMMAR_QUIZ, 104, 3, 7, BASE_TIME),
                progress(GRAMMAR_QUIZ, 105, 1, 9, BASE_TIME),
                progress(GRAMMAR_QUIZ, 106, 2, 8, BASE_TIME),
                progress(GRAMMAR_QUIZ, 107, 0, 10, BASE_TIME)));

        LearningContext context = service.build(userId, AiTaskType.GRAMMAR);

        assertThat(context.weakGrammar()).extracting(WeakItem::name).containsExactly(
                "Grammar lesson 107",
                "Grammar lesson 105",
                "Grammar lesson 103",
                "Grammar lesson 102",
                "Grammar lesson 106");
    }

    @Test
    void userWithoutProgressGetsEmptyListsNotNulls() {
        LearningContext context = service.build(userId, AiTaskType.CHAT);

        assertThat(context.weakGrammar()).isEmpty();
        assertThat(context.weakVocabulary()).isEmpty();
        assertThat(context.recentActivity()).isEmpty();
        assertThat(context.recentExams()).isNull();
    }

    @Test
    void chatTaskGetsRecentActivityNewestFirstAndLimitedToTen() {
        List<UserProgress> rows = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            rows.add(progress(GRAMMAR_LESSON, 100 + i, 0, 0, BASE_TIME.plusSeconds(i)));
        }
        // Loai muc khong thuoc 3 loai da biet bi bo qua du moi nhat
        rows.add(progress("flashcard", 1, 0, 0, BASE_TIME.plusSeconds(99)));
        when(userProgressRepository.findByUserId(userId)).thenReturn(rows);

        LearningContext context = service.build(userId, AiTaskType.CHAT);

        assertThat(context.recentActivity()).hasSize(10);
        assertThat(context.recentActivity()).extracting(RecentActivity::name)
                .startsWith("Grammar lesson 112", "Grammar lesson 111")
                .endsWith("Grammar lesson 103");
    }

    @Test
    void recentActivityIsLabelledAndNamedPerItemType() {
        when(userProgressRepository.findByUserId(userId)).thenReturn(List.of(
                progress(GRAMMAR_LESSON, 101, 0, 0, BASE_TIME.plusSeconds(1)),
                progress(GRAMMAR_QUIZ, 102, 4, 1, BASE_TIME.plusSeconds(2)),
                progress(VOCAB_QUIZ, 201, 2, 3, BASE_TIME.plusSeconds(3))));

        LearningContext context = service.build(userId, AiTaskType.CHAT);

        assertThat(context.recentActivity()).containsExactly(
                new RecentActivity("vocabulary quiz", "Vocabulary topic 201", 2, 3),
                new RecentActivity("grammar quiz", "Grammar lesson 102", 4, 1),
                new RecentActivity("grammar lesson", "Grammar lesson 101", 0, 0));
    }

    // ===== build: ky thi gan day =====

    @Test
    void examAnalysisGetsTheThreeLatestExamsWithTheirPaperInfo() {
        ExamPaper toeic = paper("TOEIC", "TOEIC Test 1");
        ExamPaper ielts = paper("IELTS", "IELTS Test 1");
        Instant newest = BASE_TIME.plusSeconds(30);
        ExamResult first = result(toeic.getId(), "745.0", 2, 2, 1, 2, newest);
        ExamResult second = result(ielts.getId(), "7.5", 2, 2, 1, 2, BASE_TIME.plusSeconds(20));
        ExamResult third = result(toeic.getId(), "500.0", 1, 2, 1, 2, BASE_TIME.plusSeconds(10));
        ExamResult fourth = result(ielts.getId(), "5.0", 0, 2, 0, 2, BASE_TIME);
        when(examResultRepository.findByUserIdOrderBySubmittedAtDesc(userId))
                .thenReturn(List.of(first, second, third, fourth));
        when(examPaperRepository.findAllById(List.of(toeic.getId(), ielts.getId())))
                .thenReturn(List.of(toeic, ielts));

        LearningContext context = service.build(userId, AiTaskType.ANALYZE_EXAM);

        assertThat(context.recentExams()).hasSize(3);
        assertThat(context.recentExams().get(0)).isEqualTo(
                new RecentExam("TOEIC", "TOEIC Test 1", new BigDecimal("745.0"), 2, 2, 1, 2, newest));
        assertThat(context.recentExams()).extracting(RecentExam::examType).containsExactly("TOEIC", "IELTS", "TOEIC");
        assertThat(context.weakGrammar()).isEmpty();
        assertThat(context.weakVocabulary()).isEmpty();
        assertThat(context.recentActivity()).isNull();
    }

    @Test
    void examAnalysisWithoutExamsGetsEmptyListAndLoadsNoPapers() {
        LearningContext context = service.build(userId, AiTaskType.ANALYZE_EXAM);

        assertThat(context.recentExams()).isEmpty();
        verifyNoInteractions(examPaperRepository);
    }

    // ===== ham dung chung =====

    private static UserProgress progress(String itemType, int itemId, int correct, int incorrect, Instant studiedAt) {
        UserProgress progress = new UserProgress();
        progress.setItemType(itemType);
        progress.setItemId(itemId);
        progress.setStatus("completed");
        progress.setCorrectCount(correct);
        progress.setIncorrectCount(incorrect);
        progress.setLastStudiedAt(studiedAt);
        return progress;
    }

    private static ExamPaper paper(String examType, String title) {
        ExamPaper paper = new ExamPaper();
        paper.setId(UUID.randomUUID());
        paper.setExamType(examType);
        paper.setTitle(title);
        return paper;
    }

    private static ExamResult result(UUID paperId, String totalScore, int listeningCorrect, int listeningTotal,
                                     int readingCorrect, int readingTotal, Instant submittedAt) {
        ExamResult result = new ExamResult();
        result.setPaperId(paperId);
        result.setTotalScore(new BigDecimal(totalScore));
        result.setListeningCorrect(listeningCorrect);
        result.setListeningTotal(listeningTotal);
        result.setReadingCorrect(readingCorrect);
        result.setReadingTotal(readingTotal);
        result.setSubmittedAt(submittedAt);
        return result;
    }
}