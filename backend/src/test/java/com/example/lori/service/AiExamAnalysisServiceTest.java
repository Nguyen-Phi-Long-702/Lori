package com.example.lori.service;

import com.example.lori.dto.AiAnalysisResponse;
import com.example.lori.dto.AiExamAnalysisRequest;
import com.example.lori.dto.AiExamAnalysisResponse;
import com.example.lori.dto.AiExamAnalysisResponse.PartStat;
import com.example.lori.dto.ExamDetailResponse;
import com.example.lori.dto.ExamResultDetailResponse;
import com.example.lori.dto.ExamResultResponse;
import com.example.lori.exception.ApiException;
import com.example.lori.service.AiServiceClient.ExamPayload;
import com.example.lori.service.AiServiceClient.TypeStat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiExamAnalysisServiceTest {

    private static final List<String> OPTIONS = List.of("write", "wrote", "written", "writes");

    private final AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
    private final ExamService examService = mock(ExamService.class);
    private final LearningContextService learningContextService = mock(LearningContextService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final AiExamAnalysisService service =
            new AiExamAnalysisService(rateLimiter, examService, learningContextService, aiServiceClient);
    private final UUID userId = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();
    private final UUID paperId = UUID.randomUUID();
    private final AiAnalysisResponse analysis = new AiAnalysisResponse("exam_analysis", "Summary", List.of(), List.of());

    @Test
    void toeicSectionsAreGroupedByPartAndSentWeakestFirst() {
        givenExam("TOEIC",
                new Sec("LISTENING", "Part 1 - Photographs (Q1)", List.of(q(null, null, "B", false))),
                new Sec("LISTENING", "Part 3 - Conversation 1 (Q32-Q33)", List.of(q("Q?", "A", "A", true))),
                new Sec("LISTENING", "Part 3 - Conversation 2 (Q35-Q36)", List.of(q("Q?", "C", "A", false))),
                new Sec("READING", "Part 5 - Incomplete Sentences (Q101-Q130)",
                        List.of(q("He ___.", "B", "B", true), q("She ___.", "B", "B", true), q("It ___.", "A", "B", false))));

        AiExamAnalysisResponse response = service.analyze(userId, new AiExamAnalysisRequest(resultId));

        List<PartStat> expected = List.of(
                new PartStat("Part 1 - Photographs", "LISTENING", 0, 1, 0),
                new PartStat("Part 3 - Conversation", "LISTENING", 1, 2, 50),
                new PartStat("Part 5 - Incomplete Sentences", "READING", 2, 3, 67));
        assertThat(response).isEqualTo(new AiExamAnalysisResponse(
                "exam_analysis", resultId, expected, "Summary", List.of(), List.of()));
        ExamPayload sent = sentPayload();
        assertThat(sent.parts()).isEqualTo(expected);
        assertThat(sent.questionTypes()).containsExactly(new TypeStat("MULTIPLE_CHOICE", 3, 6, 50));
        assertThat(sent.totalScore()).isEqualByComparingTo("650.0");
        verify(rateLimiter).checkAnalyzeExam(userId);
    }

    @Test
    void ieltsSectionsKeepTheirOwnTitle() {
        givenExam("IELTS",
                new Sec("LISTENING", "Listening - Section 1: Booking (Q1)", List.of(q("Q?", "A", "A", true))));

        service.analyze(userId, new AiExamAnalysisRequest(resultId));

        assertThat(sentPayload().parts()).extracting(PartStat::name)
                .containsExactly("Listening - Section 1: Booking (Q1)");
    }

    @Test
    void wrongQuestionsComeFromWeakestPartsFirstAreCappedAndSkipAudioOnlyOnes() {
        List<Q> part5 = new ArrayList<>();
        IntStream.range(0, 12).forEach(i -> part5.add(q(i == 0 ? "x".repeat(300) : "Q" + i, "A", "B", false)));
        IntStream.range(0, 12).forEach(i -> part5.add(q("C" + i, "B", "B", true)));
        givenExam("TOEIC",
                new Sec("READING", "Part 5 - Incomplete Sentences (Q101-Q130)", part5),
                new Sec("LISTENING", "Part 2 - Question-Response (Q7)", List.of(q(null, "A", "B", false))),
                new Sec("LISTENING", "Part 3 - Conversation 1 (Q32-Q33)", List.of(q("Why?", null, "B", false))));

        service.analyze(userId, new AiExamAnalysisRequest(resultId));

        List<AiServiceClient.WrongQuestion> wrong = sentPayload().wrongQuestions();
        assertThat(wrong).hasSize(AiExamAnalysisService.MAX_WRONG_QUESTIONS);
        assertThat(wrong.get(0).part()).isEqualTo("Part 3 - Conversation");
        assertThat(wrong.get(0).yourAnswer()).isNull();
        assertThat(wrong.get(0).correctAnswer()).isEqualTo("B: wrote");
        assertThat(wrong.get(1).part()).isEqualTo("Part 5 - Incomplete Sentences");
        assertThat(wrong.get(1).question()).hasSize(AiExamAnalysisService.MAX_QUESTION_LENGTH);
        assertThat(wrong.get(1).yourAnswer()).isEqualTo("A: write");
        assertThat(wrong).noneMatch(w -> w.part().startsWith("Part 2"));
    }

    @Test
    void partNameOnlyGroupsToeicSections() {
        assertThat(AiExamAnalysisService.partName("TOEIC", "Part 1 - Photographs (Q1)")).isEqualTo("Part 1 - Photographs");
        assertThat(AiExamAnalysisService.partName("TOEIC", "Part 7 - Reading Comprehension 10 (Q190-Q200)"))
                .isEqualTo("Part 7 - Reading Comprehension");
        assertThat(AiExamAnalysisService.partName("IELTS", "Reading - Passage 2: Top 10 (Q1)"))
                .isEqualTo("Reading - Passage 2: Top 10 (Q1)");
    }

    @Test
    void choiceAddsOptionTextOnlyForLetterAnswers() {
        assertThat(AiExamAnalysisService.choice(OPTIONS, "b")).isEqualTo("B: wrote");
        assertThat(AiExamAnalysisService.choice(OPTIONS, "E")).isEqualTo("E");
        assertThat(AiExamAnalysisService.choice(null, "TRUE")).isEqualTo("TRUE");
        assertThat(AiExamAnalysisService.choice(OPTIONS, " ")).isNull();
        assertThat(AiExamAnalysisService.choice(OPTIONS, null)).isNull();
    }

    @Test
    void resultOfAnotherUserIs404AndNeverReachesContextOrAiService() {
        when(examService.getResultDetail(userId, resultId))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Result not found"));

        assertThatThrownBy(() -> service.analyze(userId, new AiExamAnalysisRequest(resultId)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(learningContextService, aiServiceClient);
    }

    @Test
    void rateLimitedUserNeverReachesExamServiceOrAiService() {
        doThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))
                .when(rateLimiter).checkAnalyzeExam(userId);

        assertThatThrownBy(() -> service.analyze(userId, new AiExamAnalysisRequest(resultId)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(examService, learningContextService, aiServiceClient);
    }

    private ExamPayload sentPayload() {
        ArgumentCaptor<ExamPayload> sent = ArgumentCaptor.forClass(ExamPayload.class);
        verify(aiServiceClient).analyzeExam(sent.capture());
        return sent.getValue();
    }

    private void givenExam(String examType, Sec... sections) {
        List<ExamDetailResponse.Section> detailSections = new ArrayList<>();
        List<ExamResultDetailResponse.Item> items = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            Sec sec = sections[i];
            detailSections.add(new ExamDetailResponse.Section(UUID.randomUUID(), sec.skill(), sec.title(), i + 1, null,
                    null, null, sec.questions().stream().map(Q::question).toList()));
            sec.questions().forEach(q -> items.add(q.item()));
        }
        ExamResultResponse result = new ExamResultResponse(resultId, paperId, examType, "Test 1", 0, 0, 1, 2, 3, 4,
                new BigDecimal("330.0"), new BigDecimal("320.0"), new BigDecimal("650.0"), 5700, Instant.now());
        when(examService.getResultDetail(userId, resultId)).thenReturn(new ExamResultDetailResponse(result, items));
        when(examService.getExam(paperId)).thenReturn(
                new ExamDetailResponse(paperId, examType, "Test 1", "MEDIUM", 120, detailSections));
        when(aiServiceClient.analyzeExam(any())).thenReturn(analysis);
    }

    // Mot cau hoi: question (khi lam bai) va item (khi xem lai) dung chung 1 id
    private record Q(ExamDetailResponse.Question question, ExamResultDetailResponse.Item item) {
    }

    private record Sec(String skill, String title, List<Q> questions) {
    }

    private static Q q(String text, String yourAnswer, String correctAnswer, boolean correct) {
        UUID id = UUID.randomUUID();
        return new Q(
                new ExamDetailResponse.Question(id, 1, "MULTIPLE_CHOICE", text, OPTIONS),
                new ExamResultDetailResponse.Item(
                        id, "MULTIPLE_CHOICE", text, OPTIONS, yourAnswer, correctAnswer, correct, null));
    }
}