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
import com.example.lori.repository.ExamPaperRepository;
import com.example.lori.repository.ExamQuestionRepository;
import com.example.lori.repository.ExamResultRepository;
import com.example.lori.repository.ExamSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.example.lori.support.ApiAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExamServiceTest {

    private final ExamPaperRepository examPaperRepository = mock(ExamPaperRepository.class);
    private final ExamSectionRepository examSectionRepository = mock(ExamSectionRepository.class);
    private final ExamQuestionRepository examQuestionRepository = mock(ExamQuestionRepository.class);
    private final ExamResultRepository examResultRepository = mock(ExamResultRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ExpService expService = mock(ExpService.class);
    private final ExamService service = new ExamService(examPaperRepository, examSectionRepository,
            examQuestionRepository, examResultRepository, redisTemplate, expService);

    private final UUID userId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();

    @BeforeEach
    void stubRedisAndResultSave() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Gia lap DB: ket qua moi duoc gan id luc luu
        when(examResultRepository.save(any(ExamResult.class))).thenAnswer(invocation -> {
            ExamResult saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    // ===== listExams =====

    @Test
    void listExamsWithoutPapersReturnsEmptyListAndCountsNothing() {
        assertThat(service.listExams()).isEmpty();

        verifyNoInteractions(examQuestionRepository);
    }

    @Test
    void listExamsCountsQuestionsPerPaperAndDefaultsToZero() {
        ExamPaper ielts = paper(UUID.randomUUID(), "IELTS", "IELTS Test 1", "HARD", 60);
        ExamPaper toeic = paper(UUID.randomUUID(), "TOEIC", "TOEIC Test 1", "MEDIUM", 120);
        ExamPaper empty = paper(UUID.randomUUID(), "TOEIC", "TOEIC Test 2", "EASY", 120);
        when(examPaperRepository.findAllByOrderByExamTypeAscTitleAsc()).thenReturn(List.of(ielts, toeic, empty));
        List<Object[]> counts = List.of(new Object[]{toeic.getId(), 200L}, new Object[]{ielts.getId(), 40L});
        when(examQuestionRepository.countByPaperIds(any())).thenReturn(counts);

        assertThat(service.listExams()).containsExactly(
                new ExamSummaryResponse(ielts.getId(), "IELTS", "IELTS Test 1", "HARD", 60, 40),
                new ExamSummaryResponse(toeic.getId(), "TOEIC", "TOEIC Test 1", "MEDIUM", 120, 200),
                new ExamSummaryResponse(empty.getId(), "TOEIC", "TOEIC Test 2", "EASY", 120, 0));
    }

    // ===== getExam =====

    @Test
    void getExamOfUnknownExamIsNotFound() {
        assertApiException(() -> service.getExam(examId), HttpStatus.NOT_FOUND, "Exam not found");
    }

    @Test
    void getExamReturnsSectionsAndQuestionsInOrderWithoutAnswers() {
        givenPaper("TOEIC", 120);
        ExamSection listening = section("LISTENING", 1);
        listening.setAudioUrl("https://audio/1.mp3");
        listening.setImageUrl("https://image/1.jpg");
        ExamSection reading = section("READING", 2);
        reading.setPassageText("Passage");
        ExamQuestion second = question(listening, 2, "B");
        ExamQuestion first = question(listening, 1, "A");
        givenContent(List.of(listening, reading), List.of(second, first));

        ExamDetailResponse detail = service.getExam(examId);

        assertThat(detail.id()).isEqualTo(examId);
        assertThat(detail.examType()).isEqualTo("TOEIC");
        assertThat(detail.durationMinutes()).isEqualTo(120);
        assertThat(detail.sections()).hasSize(2);
        ExamDetailResponse.Section listeningDetail = detail.sections().get(0);
        assertThat(listeningDetail.skill()).isEqualTo("LISTENING");
        assertThat(listeningDetail.audioUrl()).isEqualTo("https://audio/1.mp3");
        assertThat(listeningDetail.imageUrl()).isEqualTo("https://image/1.jpg");
        assertThat(listeningDetail.questions()).extracting(ExamDetailResponse.Question::id)
                .containsExactly(first.getId(), second.getId());
        ExamDetailResponse.Section readingDetail = detail.sections().get(1);
        assertThat(readingDetail.passageText()).isEqualTo("Passage");
        assertThat(readingDetail.questions()).isEmpty();
    }

    @Test
    void getExamWithoutSectionsReturnsNoSectionsAndLoadsNoQuestions() {
        givenPaper("TOEIC", 120);

        assertThat(service.getExam(examId).sections()).isEmpty();

        verifyNoInteractions(examQuestionRepository);
    }

    // ===== start =====

    @Test
    void startOfUnknownExamIsNotFoundAndStoresNoSession() {
        assertApiException(() -> service.start(userId, examId), HttpStatus.NOT_FOUND, "Exam not found");

        verifyNoInteractions(valueOps);
    }

    @Test
    void startStoresTheSessionInRedisForDurationPlusTenMinutes() {
        givenPaper("TOEIC", 120);
        long before = Instant.now().getEpochSecond();

        StartExamResponse response = service.start(userId, examId);

        long after = Instant.now().getEpochSecond();
        String token = response.sessionToken();
        assertThatCode(() -> UUID.fromString(token)).doesNotThrowAnyException();
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("exam_session:" + token), value.capture(), eq(Duration.ofMinutes(130)));
        String[] parts = value.getValue().split(":");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo(userId.toString());
        assertThat(parts[1]).isEqualTo(examId.toString());
        assertThat(Long.parseLong(parts[2])).isBetween(before, after);
    }

    // ===== submit =====

    @Test
    void toeicSubmissionIsGradedByTheBackendAndSaved() {
        List<ExamQuestion> questions = givenTwoListeningAndTwoReadingQuestions("TOEIC");
        givenSession("token", validSession(Instant.now().getEpochSecond() - 120));

        ExamResultResponse response = service.submit(userId, examId, request("token",
                answer(questions.get(0), "A"), answer(questions.get(1), "B"),
                answer(questions.get(2), "C"), answer(questions.get(3), "X")));

        assertThat(response.paperId()).isEqualTo(examId);
        assertThat(response.examType()).isEqualTo("TOEIC");
        assertThat(response.correctCount()).isEqualTo(3);
        assertThat(response.totalQuestions()).isEqualTo(4);
        assertThat(response.listeningCorrect()).isEqualTo(2);
        assertThat(response.listeningTotal()).isEqualTo(2);
        assertThat(response.readingCorrect()).isEqualTo(1);
        assertThat(response.readingTotal()).isEqualTo(2);
        assertThat(response.listeningScore()).isEqualByComparingTo("495");
        assertThat(response.readingScore()).isEqualByComparingTo("250");
        assertThat(response.totalScore()).isEqualByComparingTo("745");
        assertThat(response.timeSpentSeconds()).isBetween(120, 130);

        ArgumentCaptor<ExamResult> saved = ArgumentCaptor.forClass(ExamResult.class);
        InOrder inOrder = inOrder(expService, examResultRepository);
        inOrder.verify(expService).awardForExam(userId, examId, 3, 4);
        inOrder.verify(examResultRepository).save(saved.capture());
        ExamResult result = saved.getValue();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPaperId()).isEqualTo(examId);
        assertThat(result.getAnswers()).containsOnly(
                entry(questions.get(0).getId().toString(), "A"),
                entry(questions.get(1).getId().toString(), "B"),
                entry(questions.get(2).getId().toString(), "C"),
                entry(questions.get(3).getId().toString(), "X"));
    }

    @Test
    void ieltsSubmissionIsConvertedToBands() {
        List<ExamQuestion> questions = givenTwoListeningAndTwoReadingQuestions("IELTS");
        givenSession("token", validSession(Instant.now().getEpochSecond()));

        ExamResultResponse response = service.submit(userId, examId, request("token",
                answer(questions.get(0), "A"), answer(questions.get(1), "B"),
                answer(questions.get(2), "C"), answer(questions.get(3), "X")));

        assertThat(response.listeningScore()).isEqualByComparingTo("9.0");
        assertThat(response.readingScore()).isEqualByComparingTo("5.5");
        assertThat(response.totalScore()).isEqualByComparingTo("7.5");
    }

    @Test
    void answersAreComparedIgnoringCaseAndExtraSpacesAndStoredTrimmed() {
        givenPaper("IELTS", 60);
        ExamSection reading = section("READING", 1);
        ExamQuestion question = question(reading, 1, "NOT GIVEN");
        givenContent(List.of(reading), List.of(question));
        givenSession("token", validSession(Instant.now().getEpochSecond()));

        ExamResultResponse response = service.submit(userId, examId,
                request("token", answer(question, "  not   given ")));

        assertThat(response.readingCorrect()).isEqualTo(1);
        assertThat(savedResult().getAnswers()).containsOnly(entry(question.getId().toString(), "not   given"));
    }

    @Test
    void blankNullAndMissingAnswersCountAsWrongAndAreNotStored() {
        givenPaper("TOEIC", 120);
        ExamSection reading = section("READING", 1);
        List<ExamQuestion> questions = List.of(
                question(reading, 1, "A"), question(reading, 2, "B"), question(reading, 3, "C"));
        givenContent(List.of(reading), questions);
        givenSession("token", validSession(Instant.now().getEpochSecond()));

        ExamResultResponse response = service.submit(userId, examId, request("token",
                answer(questions.get(0), "   "), answer(questions.get(1), null)));

        assertThat(response.readingCorrect()).isZero();
        assertThat(response.readingTotal()).isEqualTo(3);
        assertThat(savedResult().getAnswers()).isEmpty();
    }

    @Test
    void submitOfUnknownExamIsNotFoundBeforeTouchingTheSession() {
        assertApiException(() -> service.submit(userId, examId, request("token")),
                HttpStatus.NOT_FOUND, "Exam not found");

        verifyNoInteractions(valueOps, expService);
    }

    @Test
    void submitWithMissingOrExpiredSessionIsRejected() {
        assertSessionRejected(null);
    }

    @Test
    void submitWithSessionOfAnotherUserIsRejected() {
        assertSessionRejected(UUID.randomUUID() + ":" + examId + ":1700000000");
    }

    @Test
    void submitWithSessionOfAnotherExamIsRejected() {
        assertSessionRejected(userId + ":" + UUID.randomUUID() + ":1700000000");
    }

    @Test
    void submitWithMalformedSessionIsRejected() {
        assertSessionRejected("garbage");
    }

    // ===== listResults =====

    @Test
    void listResultsWithoutHistoryReturnsEmptyListAndLoadsNoPapers() {
        assertThat(service.listResults(userId)).isEmpty();

        verifyNoInteractions(examPaperRepository);
    }

    @Test
    void listResultsMapsEachResultToItsPaper() {
        ExamPaper toeic = paper(UUID.randomUUID(), "TOEIC", "TOEIC Test 1", "MEDIUM", 120);
        ExamPaper ielts = paper(UUID.randomUUID(), "IELTS", "IELTS Test 1", "HARD", 60);
        when(examResultRepository.findByUserIdOrderBySubmittedAtDesc(userId)).thenReturn(List.of(
                result(toeic.getId()), result(toeic.getId()), result(ielts.getId())));
        when(examPaperRepository.findAllById(List.of(toeic.getId(), ielts.getId()))).thenReturn(List.of(toeic, ielts));

        assertThat(service.listResults(userId)).extracting(ExamResultResponse::title)
                .containsExactly("TOEIC Test 1", "TOEIC Test 1", "IELTS Test 1");
    }

    // ===== getResultDetail =====

    @Test
    void resultDetailOfAnotherUsersResultIsNotFound() {
        assertApiException(() -> service.getResultDetail(userId, UUID.randomUUID()),
                HttpStatus.NOT_FOUND, "Result not found");

        verifyNoInteractions(examPaperRepository);
    }

    @Test
    void resultDetailShowsEveryQuestionWithYourAnswerAndTheCorrectOne() {
        List<ExamQuestion> questions = givenTwoListeningAndTwoReadingQuestions("TOEIC");
        ExamResult result = result(examId);
        result.setAnswers(Map.of(
                questions.get(0).getId().toString(), "A",
                questions.get(1).getId().toString(), "X"));
        when(examResultRepository.findByIdAndUserId(result.getId(), userId)).thenReturn(Optional.of(result));

        ExamResultDetailResponse detail = service.getResultDetail(userId, result.getId());

        assertThat(detail.result().id()).isEqualTo(result.getId());
        assertThat(detail.result().title()).isEqualTo("TOEIC Test 1");
        assertThat(detail.items()).extracting(ExamResultDetailResponse.Item::yourAnswer)
                .containsExactly("A", "X", null, null);
        assertThat(detail.items()).extracting(ExamResultDetailResponse.Item::correctAnswer)
                .containsExactly("A", "B", "C", "D");
        assertThat(detail.items()).extracting(ExamResultDetailResponse.Item::correct)
                .containsExactly(true, false, false, false);
        assertThat(detail.items().get(0).explanation()).isEqualTo("Because A");
    }

    // ===== ham dung chung =====

    private ExamPaper paper(UUID id, String examType, String title, String difficulty, int durationMinutes) {
        ExamPaper paper = new ExamPaper();
        paper.setId(id);
        paper.setExamType(examType);
        paper.setTitle(title);
        paper.setDifficulty(difficulty);
        paper.setDurationMinutes(durationMinutes);
        return paper;
    }

    // Tao de co id = examId ("<loai> Test 1") va cho examPaperRepository tim thay no
    private void givenPaper(String examType, int durationMinutes) {
        ExamPaper paper = paper(examId, examType, examType + " Test 1", "MEDIUM", durationMinutes);
        when(examPaperRepository.findById(examId)).thenReturn(Optional.of(paper));
    }

    private ExamSection section(String skill, int orderIndex) {
        ExamSection section = new ExamSection();
        section.setId(UUID.randomUUID());
        section.setPaperId(examId);
        section.setSkill(skill);
        section.setTitle(skill + " " + orderIndex);
        section.setOrderIndex(orderIndex);
        return section;
    }

    private ExamQuestion question(ExamSection section, int orderIndex, String correctAnswer) {
        ExamQuestion question = new ExamQuestion();
        question.setId(UUID.randomUUID());
        question.setSectionId(section.getId());
        question.setOrderIndex(orderIndex);
        question.setQuestionType("MULTIPLE_CHOICE");
        question.setQuestionText("Question " + orderIndex);
        question.setOptions(List.of("A", "B", "C", "D"));
        question.setCorrectAnswer(correctAnswer);
        question.setExplanation("Because " + correctAnswer);
        return question;
    }

    private void givenContent(List<ExamSection> sections, List<ExamQuestion> questions) {
        when(examSectionRepository.findByPaperIdOrderByOrderIndexAsc(examId)).thenReturn(sections);
        when(examQuestionRepository.findBySectionIdIn(any())).thenReturn(questions);
    }

    // De co 1 section LISTENING (dap an dung A, B) va 1 section READING (dap an dung C, D); tra ve 4 cau theo thu tu do
    private List<ExamQuestion> givenTwoListeningAndTwoReadingQuestions(String examType) {
        givenPaper(examType, 60);
        ExamSection listening = section("LISTENING", 1);
        ExamSection reading = section("READING", 2);
        List<ExamQuestion> questions = new ArrayList<>(List.of(
                question(listening, 1, "A"), question(listening, 2, "B"),
                question(reading, 1, "C"), question(reading, 2, "D")));
        givenContent(List.of(listening, reading), questions);
        return questions;
    }

    private void givenSession(String token, String redisValue) {
        when(valueOps.getAndDelete("exam_session:" + token)).thenReturn(redisValue);
    }

    private String validSession(long startedAtEpochSeconds) {
        return userId + ":" + examId + ":" + startedAtEpochSeconds;
    }

    private void assertSessionRejected(String redisValue) {
        givenPaper("TOEIC", 120);
        givenSession("token", redisValue);

        assertApiException(() -> service.submit(userId, examId, request("token")),
                HttpStatus.BAD_REQUEST, "Invalid or expired exam session");

        verifyNoInteractions(expService);
        verify(examResultRepository, never()).save(any(ExamResult.class));
    }

    private static SubmitExamRequest request(String token, SubmitExamRequest.Answer... answers) {
        return new SubmitExamRequest(token, Arrays.asList(answers));
    }

    private static SubmitExamRequest.Answer answer(ExamQuestion question, String text) {
        return new SubmitExamRequest.Answer(question.getId(), text);
    }

    private ExamResult result(UUID paperId) {
        ExamResult result = new ExamResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setPaperId(paperId);
        result.setSubmittedAt(Instant.parse("2026-11-01T00:00:00Z"));
        return result;
    }

    private ExamResult savedResult() {
        ArgumentCaptor<ExamResult> captor = ArgumentCaptor.forClass(ExamResult.class);
        verify(examResultRepository).save(captor.capture());
        return captor.getValue();
    }
}