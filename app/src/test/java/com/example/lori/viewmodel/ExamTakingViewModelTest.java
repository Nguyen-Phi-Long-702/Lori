package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.R;
import com.example.lori.data.remote.dto.ExamDetailResponse;
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.data.remote.dto.StartExamResponse;
import com.example.lori.data.remote.dto.SubmitExamRequest;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.ExamRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExamTakingViewModelTest {

    private static final String EXAM_ID = "exam-1";
    private static final String MULTIPLE_CHOICE = ExamTakingViewModel.TYPE_MULTIPLE_CHOICE;

    //Cho LiveData chạy đồng bộ trên luồng test, không cần Looper của Android
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Application application;
    private MockedConstruction<ExamRepository> examRepositoryConstruction;
    private ExamRepository examRepository;
    private ExamTakingViewModel viewModel;

    @Before
    public void setUp() {
        application = mock(Application.class);
        //ViewModel tự tạo ExamRepository nên thay phép tạo này bằng đối tượng giả
        examRepositoryConstruction = Mockito.mockConstruction(ExamRepository.class);
        newViewModel();
    }

    @After
    public void tearDown() {
        viewModel.onCleared();
        examRepositoryConstruction.close();
    }

    //Tải đề và xin phiên làm bài

    @Test
    public void load_success_exposesTitleQuestionsAndFullTimer() {
        loadToeic();

        assertEquals(Boolean.FALSE, viewModel.getLoading().getValue());
        assertNull(viewModel.getErrorMessage().getValue());
        assertEquals("Đề thử", viewModel.getExamTitle().getValue());
        assertEquals(3, viewModel.getQuestionCount());
        assertEquals(Long.valueOf(3600L), viewModel.getRemainingSeconds().getValue());
        assertEquals(Boolean.FALSE, viewModel.getTimerWarning().getValue());
        assertEquals(Boolean.FALSE, viewModel.getTimeUp().getValue());
    }

    @Test
    public void load_examOfFiveMinutesOrLess_startsInWarning() {
        stubLoad(detail("TOEIC", 5, section("s1", "READING", "Part 5 - Incomplete Sentences (Q1)",
                question("q1", 1, MULTIPLE_CHOICE, "Câu hỏi", "A", "B", "C", "D"))));

        viewModel.load(EXAM_ID);

        assertEquals(Long.valueOf(300L), viewModel.getRemainingSeconds().getValue());
        assertEquals(Boolean.TRUE, viewModel.getTimerWarning().getValue());
    }

    @Test
    public void load_whileRequestPending_ignoresSecondCall() {
        when(examRepository.detail(EXAM_ID)).thenReturn(new MutableLiveData<>());

        viewModel.load(EXAM_ID);
        viewModel.load(EXAM_ID);

        assertEquals(Boolean.TRUE, viewModel.getLoading().getValue());
        verify(examRepository, times(1)).detail(EXAM_ID);
    }

    @Test
    public void load_alreadyLoaded_doesNotLoadAgain() {
        loadToeic();

        viewModel.load(EXAM_ID);

        verify(examRepository, times(1)).detail(EXAM_ID);
    }

    @Test
    public void load_detailPremiumRequired_showsPremiumErrorAndSkipsStart() {
        when(examRepository.detail(EXAM_ID))
                .thenReturn(resultOf(ApiResult.httpError(403, ApiResult.PREMIUM_REQUIRED)));

        viewModel.load(EXAM_ID);

        assertError(R.string.exam_taking_error_premium);
        assertEquals(Boolean.FALSE, viewModel.getLoading().getValue());
        assertNull(viewModel.getQuestions().getValue());
        verify(examRepository, never()).start(any());
    }

    @Test
    public void load_detailErrors_mapToMessages() {
        loadWithDetailResult(ApiResult.offline());
        assertError(R.string.auth_error_offline);

        loadWithDetailResult(ApiResult.timeout());
        assertError(R.string.auth_error_timeout);

        loadWithDetailResult(ApiResult.networkError());
        assertError(R.string.auth_error_network);

        loadWithDetailResult(ApiResult.httpError(401, null));
        assertError(R.string.premium_error_session_expired);

        //403 không có PREMIUM_REQUIRED là Backend từ chối ứng dụng
        loadWithDetailResult(ApiResult.httpError(403, null));
        assertError(R.string.auth_error_forbidden);

        loadWithDetailResult(ApiResult.httpError(404, null));
        assertError(R.string.exam_taking_error_not_found);

        loadWithDetailResult(ApiResult.httpError(429, null));
        assertError(R.string.auth_error_too_many_requests);

        loadWithDetailResult(ApiResult.httpError(500, null));
        assertError(R.string.auth_error_server);

        //Thành công nhưng không có nội dung
        loadWithDetailResult(ApiResult.success(null, 200));
        assertError(R.string.auth_error_server);
    }

    @Test
    public void load_examWithoutQuestions_showsEmptyError() {
        ExamDetailResponse noQuestions = detail("TOEIC", 60,
                section("s1", "READING", "Part 5", new ExamDetailResponse.Question[0]));
        loadWithDetailResult(ApiResult.success(noQuestions, 200));
        assertError(R.string.exam_taking_error_empty);

        ExamDetailResponse noSections = detail("TOEIC", 60);
        noSections.sections = null;
        loadWithDetailResult(ApiResult.success(noSections, 200));
        assertError(R.string.exam_taking_error_empty);

        verify(examRepository, never()).start(any());
    }

    @Test
    public void load_startPremiumRequired_showsPremiumError() {
        when(examRepository.detail(EXAM_ID)).thenReturn(resultOf(ApiResult.success(toeicDetail(), 200)));
        when(examRepository.start(EXAM_ID))
                .thenReturn(resultOf(ApiResult.httpError(403, ApiResult.PREMIUM_REQUIRED)));

        viewModel.load(EXAM_ID);

        assertError(R.string.exam_taking_error_premium);
        assertNull(viewModel.getQuestions().getValue());
    }

    @Test
    public void load_startErrors_keepExamHiddenAndShowMessage() {
        loadWithStartResult(ApiResult.offline());
        assertError(R.string.auth_error_offline);

        loadWithStartResult(ApiResult.httpError(500, null));
        assertError(R.string.auth_error_server);

        //Có kết quả nhưng thiếu hoặc rỗng sessionToken
        loadWithStartResult(ApiResult.success(null, 200));
        assertError(R.string.auth_error_server);

        loadWithStartResult(ApiResult.success(started("  "), 200));
        assertError(R.string.auth_error_server);

        assertNull(viewModel.getQuestions().getValue());
        assertNull(viewModel.getRemainingSeconds().getValue());
    }

    @Test
    public void retry_afterFailure_loadsAgain() {
        when(examRepository.detail(EXAM_ID))
                .thenReturn(resultOf(ApiResult.timeout()))
                .thenReturn(resultOf(ApiResult.success(toeicDetail(), 200)));
        when(examRepository.start(EXAM_ID)).thenReturn(resultOf(ApiResult.success(started("tok-1"), 200)));
        viewModel.load(EXAM_ID);
        assertError(R.string.auth_error_timeout);

        viewModel.retry();

        assertNull(viewModel.getErrorMessage().getValue());
        assertEquals(3, viewModel.getQuestionCount());
        verify(examRepository, times(2)).detail(EXAM_ID);
    }

    @Test
    public void retry_beforeAnyLoad_doesNothing() {
        viewModel.retry();

        verifyNoInteractions(examRepository);
    }

    //Gộp section thành danh sách câu hỏi phẳng

    @Test
    public void load_toeic_assignsPartAndMarksFirstQuestionOfEachPart() {
        loadToeic();

        ExamQuestionItem first = viewModel.getQuestion(0);
        ExamQuestionItem second = viewModel.getQuestion(1);
        ExamQuestionItem third = viewModel.getQuestion(2);
        assertEquals("q1", first.questionId);
        assertEquals("s1", first.sectionId);
        assertEquals("Part 1 - Photographs (Q1-2)", first.sectionTitle);
        assertEquals(1, first.toeicPart);
        assertTrue(first.firstOfPart);
        assertEquals(1, second.toeicPart);
        assertFalse(second.firstOfPart);
        assertEquals(5, third.toeicPart);
        assertTrue(third.firstOfPart);
        assertEquals(Arrays.asList("go", "goes", "going", "gone"), third.options);
        assertNull(first.ielts);
    }

    @Test
    public void load_toeic_sectionTitleWithoutPart_hasNoPart() {
        stubLoad(detail("TOEIC", 60, section("s1", "READING", "Reading",
                question("q1", 1, MULTIPLE_CHOICE, "Câu hỏi", "A", "B", "C", "D"))));

        viewModel.load(EXAM_ID);

        assertEquals(0, viewModel.getQuestion(0).toeicPart);
        assertFalse(viewModel.getQuestion(0).firstOfPart);
    }

    @Test
    public void load_ielts_splitsInstructionAndNumbersSectionsPerSkill() {
        stubLoad(detail("IELTS", 60,
                section("l1", "LISTENING", "Section 1",
                        question("q1", 1, ExamTakingViewModel.TYPE_FILL_BLANK,
                                "Write ONE WORD ONLY\n\nThe train leaves at ___."),
                        question("q2", 2, MULTIPLE_CHOICE, "Chọn đáp án đúng", "A", "B", "C")),
                section("l2", "LISTENING", "Section 2",
                        question("q3", 3, ExamTakingViewModel.TYPE_MATCHING, "Match the names\n\nA. Anna")),
                section("r1", "READING", "Passage 1",
                        question("q4", 1, ExamTakingViewModel.TYPE_TRUE_FALSE_NOT_GIVEN, "Một nhận định"))));

        viewModel.load(EXAM_ID);

        ExamQuestionItem fill = viewModel.getQuestion(0);
        assertEquals("Write ONE WORD ONLY", fill.instruction);
        assertEquals("The train leaves at ___.", fill.questionText);
        assertEquals(0, fill.toeicPart);
        assertEquals("LISTENING", fill.ielts.skill);
        assertEquals(1, fill.ielts.sectionNumber);
        assertEquals(1, fill.ielts.questionNumber);
        assertTrue(fill.ielts.firstOfSection);
        assertEquals(1, fill.ielts.firstQuestionNumber);
        assertEquals(2, fill.ielts.lastQuestionNumber);

        ExamQuestionItem choice = viewModel.getQuestion(1);
        assertNull(choice.instruction);
        assertEquals("Chọn đáp án đúng", choice.questionText);
        assertFalse(choice.ielts.firstOfSection);

        ExamQuestionItem matching = viewModel.getQuestion(2);
        assertEquals("Match the names", matching.instruction);
        assertEquals("A. Anna", matching.questionText);
        assertEquals(2, matching.ielts.sectionNumber);

        ExamQuestionItem reading = viewModel.getQuestion(3);
        assertEquals("READING", reading.ielts.skill);
        assertEquals(1, reading.ielts.sectionNumber);
        assertTrue(reading.ielts.firstOfSection);
    }

    @Test
    public void load_blankSectionAndQuestionFields_becomeNull() {
        ExamDetailResponse.Section section = section("s1", "READING", "Part 7",
                question("q1", 1, MULTIPLE_CHOICE, "", "A", "B"));
        section.audioUrl = "";
        section.imageUrl = "   ";
        section.passageText = "Đoạn văn";
        stubLoad(detail("TOEIC", 60, section));

        viewModel.load(EXAM_ID);

        ExamQuestionItem item = viewModel.getQuestion(0);
        assertNull(item.audioUrl);
        assertNull(item.imageUrl);
        assertEquals("Đoạn văn", item.passageText);
        assertNull(item.questionText);
    }

    //Đáp án, đánh dấu, chuyển câu

    @Test
    public void setAnswer_trimsAndStoresTheAnswer() {
        loadToeic();

        viewModel.setAnswer("q1", "  A ");

        assertEquals("A", viewModel.getAnswer("q1"));
        assertEquals(1, viewModel.getAnsweredCount());
    }

    @Test
    public void setAnswer_blankOrNull_removesTheAnswer() {
        loadToeic();
        viewModel.setAnswer("q1", "A");
        viewModel.setAnswer("q2", "B");

        viewModel.setAnswer("q1", "   ");
        viewModel.setAnswer("q2", null);

        assertNull(viewModel.getAnswer("q1"));
        assertNull(viewModel.getAnswer("q2"));
        assertEquals(0, viewModel.getAnsweredCount());
    }

    @Test
    public void setAnswer_unchangedValue_doesNotEmitAgain() {
        loadToeic();
        int[] emissions = {0};
        viewModel.getAnswers().observeForever(answers -> emissions[0]++);
        assertEquals(1, emissions[0]);

        viewModel.setAnswer("q1", "A");
        assertEquals(2, emissions[0]);

        viewModel.setAnswer("q1", " A ");
        viewModel.setAnswer("q2", "");
        assertEquals(2, emissions[0]);
    }

    @Test
    public void toggleCurrentFlag_flagsAndUnflagsTheCurrentQuestion() {
        loadToeic();

        viewModel.toggleCurrentFlag();
        assertTrue(viewModel.isFlagged("q1"));
        assertEquals(1, viewModel.getFlaggedCount());

        viewModel.toggleCurrentFlag();
        assertFalse(viewModel.isFlagged("q1"));
        assertEquals(0, viewModel.getFlaggedCount());

        viewModel.setCurrentPosition(1);
        viewModel.toggleCurrentFlag();
        assertTrue(viewModel.isFlagged("q2"));
        assertFalse(viewModel.isFlagged("q1"));
    }

    @Test
    public void toggleCurrentFlag_beforeExamLoaded_doesNothing() {
        viewModel.toggleCurrentFlag();

        assertEquals(0, viewModel.getFlaggedCount());
    }

    @Test
    public void setCurrentPosition_outOfRange_isIgnored() {
        loadToeic();

        viewModel.setCurrentPosition(-1);
        viewModel.setCurrentPosition(3);

        assertEquals(Integer.valueOf(0), viewModel.getCurrentPosition().getValue());
        assertNull(viewModel.getQuestion(-1));
        assertNull(viewModel.getQuestion(3));
    }

    @Test
    public void setCurrentPosition_samePosition_doesNotEmitAgain() {
        loadToeic();
        int[] emissions = {0};
        viewModel.getCurrentPosition().observeForever(position -> emissions[0]++);

        viewModel.setCurrentPosition(0);

        assertEquals(1, emissions[0]);
    }

    @Test
    public void goToNext_movesUntilTheLastQuestion() {
        loadToeic();
        assertTrue(viewModel.hasNext());

        viewModel.goToNext();
        viewModel.goToNext();
        assertEquals(Integer.valueOf(2), viewModel.getCurrentPosition().getValue());
        assertFalse(viewModel.hasNext());

        viewModel.goToNext();
        assertEquals(Integer.valueOf(2), viewModel.getCurrentPosition().getValue());
    }

    @Test
    public void passage_reopensOnlyWhenMovingToAnotherSection() {
        loadToeic();
        assertEquals(Boolean.TRUE, viewModel.getPassageExpanded().getValue());

        viewModel.togglePassage();
        assertEquals(Boolean.FALSE, viewModel.getPassageExpanded().getValue());

        //q2 vẫn thuộc section s1 nên khung giữ nguyên trạng thái thu gọn
        viewModel.setCurrentPosition(1);
        assertEquals(Boolean.FALSE, viewModel.getPassageExpanded().getValue());

        //q3 thuộc section s2
        viewModel.setCurrentPosition(2);
        assertEquals(Boolean.TRUE, viewModel.getPassageExpanded().getValue());
    }

    @Test
    public void audioPosition_isReturnedOnlyForTheSameFile() {
        viewModel.saveAudioPosition("https://example.com/a.mp3", 4500L);

        assertEquals(4500L, viewModel.getSavedAudioPosition("https://example.com/a.mp3"));
        assertEquals(0L, viewModel.getSavedAudioPosition("https://example.com/b.mp3"));
        assertEquals(0L, viewModel.getSavedAudioPosition(null));
    }

    //Nộp bài

    @Test
    public void submit_beforeExamLoaded_doesNothing() {
        viewModel.submit();

        verify(examRepository, never()).submit(any(), any());
    }

    @Test
    public void submit_sendsSessionTokenAndOnlyAnsweredQuestions() {
        loadToeic();
        when(examRepository.submit(eq(EXAM_ID), any(SubmitExamRequest.class)))
                .thenReturn(new MutableLiveData<>());
        viewModel.setAnswer("q1", "A");
        viewModel.setAnswer("q3", " goes ");

        viewModel.submit();

        ArgumentCaptor<SubmitExamRequest> sent = ArgumentCaptor.forClass(SubmitExamRequest.class);
        verify(examRepository).submit(eq(EXAM_ID), sent.capture());
        assertEquals("tok-1", sent.getValue().sessionToken);
        Map<String, String> sentAnswers = new HashMap<>();
        for (SubmitExamRequest.Answer answer : sent.getValue().answers) {
            sentAnswers.put(answer.questionId, answer.answer);
        }
        Map<String, String> expected = new HashMap<>();
        expected.put("q1", "A");
        expected.put("q3", "goes");
        assertEquals(expected, sentAnswers);
    }

    @Test
    public void submit_success_publishesResultIdAndIgnoresFurtherSubmits() {
        loadToeic();
        when(examRepository.submit(eq(EXAM_ID), any(SubmitExamRequest.class)))
                .thenReturn(resultOf(ApiResult.success(examResult("result-1"), 200)));

        viewModel.submit();
        viewModel.submit();

        assertEquals("result-1", viewModel.getSubmitResultId().getValue());
        assertEquals(Boolean.FALSE, viewModel.getSubmitting().getValue());
        assertNull(viewModel.getSubmitError().getValue());
        verify(examRepository, times(1)).submit(eq(EXAM_ID), any(SubmitExamRequest.class));
    }

    @Test
    public void submit_whilePending_ignoresSecondSubmit() {
        loadToeic();
        when(examRepository.submit(eq(EXAM_ID), any(SubmitExamRequest.class)))
                .thenReturn(new MutableLiveData<>());

        viewModel.submit();
        viewModel.submit();

        assertEquals(Boolean.TRUE, viewModel.getSubmitting().getValue());
        verify(examRepository, times(1)).submit(eq(EXAM_ID), any(SubmitExamRequest.class));
    }

    @Test
    public void submit_resultWithoutId_isRetryableServerError() {
        ExamSubmitError error = submitErrorFor(ApiResult.success(examResult(null), 200));

        assertEquals(R.string.exam_submit_error_server, error.messageRes);
        assertTrue(error.canRetry);
        assertNull(viewModel.getSubmitResultId().getValue());
    }

    @Test
    public void submit_temporaryErrors_canRetry() {
        assertSubmitError(ApiResult.offline(), R.string.exam_submit_error_offline, true);
        assertSubmitError(ApiResult.timeout(), R.string.exam_submit_error_timeout, true);
        assertSubmitError(ApiResult.networkError(), R.string.exam_submit_error_network, true);
        assertSubmitError(ApiResult.httpError(429, null), R.string.auth_error_too_many_requests, true);
        assertSubmitError(ApiResult.httpError(500, null), R.string.exam_submit_error_server, true);
    }

    @Test
    public void submit_unrecoverableErrors_cannotRetry() {
        assertSubmitError(ApiResult.httpError(400, null), R.string.exam_submit_error_session, false);
        assertSubmitError(ApiResult.httpError(401, null), R.string.premium_error_session_expired, false);
        //403 không có PREMIUM_REQUIRED là Backend từ chối ứng dụng
        assertSubmitError(ApiResult.httpError(403, null), R.string.auth_error_forbidden, false);
        assertSubmitError(ApiResult.httpError(404, null), R.string.exam_taking_error_not_found, false);
        assertSubmitError(ApiResult.httpError(403, ApiResult.PREMIUM_REQUIRED),
                R.string.exam_taking_error_premium, false);
    }

    @Test
    public void submit_afterTemporaryError_canSubmitAgain() {
        loadToeic();
        when(examRepository.submit(eq(EXAM_ID), any(SubmitExamRequest.class)))
                .thenReturn(resultOf(ApiResult.timeout()))
                .thenReturn(resultOf(ApiResult.success(examResult("result-1"), 200)));

        viewModel.submit();
        assertTrue(viewModel.getSubmitError().getValue().canRetry);

        viewModel.submit();

        assertNull(viewModel.getSubmitError().getValue());
        assertEquals("result-1", viewModel.getSubmitResultId().getValue());
        verify(examRepository, times(2)).submit(eq(EXAM_ID), any(SubmitExamRequest.class));
    }

    @Test
    public void submit_afterUnrecoverableError_isBlocked() {
        loadToeic();
        when(examRepository.submit(eq(EXAM_ID), any(SubmitExamRequest.class)))
                .thenReturn(resultOf(ApiResult.httpError(400, null)));

        viewModel.submit();
        viewModel.submit();

        verify(examRepository, times(1)).submit(eq(EXAM_ID), any(SubmitExamRequest.class));
    }

    @Test
    public void clearSubmitError_removesTheError() {
        submitErrorFor(ApiResult.timeout());

        viewModel.clearSubmitError();

        assertNull(viewModel.getSubmitError().getValue());
    }

    //Hàm dùng chung

    //Mỗi lần gọi tạo ViewModel mới (và Repository giả mới) để test không dính trạng thái của lần trước
    private void newViewModel() {
        viewModel = new ExamTakingViewModel(application);
        List<ExamRepository> created = examRepositoryConstruction.constructed();
        examRepository = created.get(created.size() - 1);
    }

    private void stubLoad(ExamDetailResponse detail) {
        when(examRepository.detail(EXAM_ID)).thenReturn(resultOf(ApiResult.success(detail, 200)));
        when(examRepository.start(EXAM_ID)).thenReturn(resultOf(ApiResult.success(started("tok-1"), 200)));
    }

    private void loadToeic() {
        stubLoad(toeicDetail());
        viewModel.load(EXAM_ID);
    }

    private void loadWithDetailResult(ApiResult<ExamDetailResponse> result) {
        when(examRepository.detail(EXAM_ID)).thenReturn(resultOf(result));
        viewModel.load(EXAM_ID);
    }

    private void loadWithStartResult(ApiResult<StartExamResponse> result) {
        when(examRepository.detail(EXAM_ID)).thenReturn(resultOf(ApiResult.success(toeicDetail(), 200)));
        when(examRepository.start(EXAM_ID)).thenReturn(resultOf(result));
        viewModel.load(EXAM_ID);
    }

    //Tạo ViewModel đã tải đề, cho Backend trả kết quả nộp này rồi nộp bài; trả về lỗi nộp đang hiện
    private ExamSubmitError submitErrorFor(ApiResult<ExamResultResponse> result) {
        newViewModel();
        loadToeic();
        when(examRepository.submit(eq(EXAM_ID), any(SubmitExamRequest.class))).thenReturn(resultOf(result));
        viewModel.submit();
        return viewModel.getSubmitError().getValue();
    }

    private void assertSubmitError(ApiResult<ExamResultResponse> result, int expectedMessageRes, boolean canRetry) {
        ExamSubmitError error = submitErrorFor(result);
        assertNotNull(error);
        assertEquals(expectedMessageRes, error.messageRes);
        assertEquals(canRetry, error.canRetry);
    }

    private void assertError(int expectedMessageRes) {
        assertEquals(Integer.valueOf(expectedMessageRes), viewModel.getErrorMessage().getValue());
    }

    private static ExamDetailResponse toeicDetail() {
        return detail("TOEIC", 60,
                section("s1", "LISTENING", "Part 1 - Photographs (Q1-2)",
                        question("q1", 1, MULTIPLE_CHOICE, null, "A", "B", "C", "D"),
                        question("q2", 2, MULTIPLE_CHOICE, null, "A", "B", "C", "D")),
                section("s2", "READING", "Part 5 - Incomplete Sentences (Q3)",
                        question("q3", 3, MULTIPLE_CHOICE, "He ___ to school.", "go", "goes", "going", "gone")));
    }

    private static ExamDetailResponse detail(String examType, int durationMinutes,
                                             ExamDetailResponse.Section... sections) {
        ExamDetailResponse detail = new ExamDetailResponse();
        detail.id = EXAM_ID;
        detail.examType = examType;
        detail.title = "Đề thử";
        detail.durationMinutes = durationMinutes;
        detail.sections = Arrays.asList(sections);
        return detail;
    }

    private static ExamDetailResponse.Section section(String id, String skill, String title,
                                                      ExamDetailResponse.Question... questions) {
        ExamDetailResponse.Section section = new ExamDetailResponse.Section();
        section.id = id;
        section.skill = skill;
        section.title = title;
        section.questions = Arrays.asList(questions);
        return section;
    }

    private static ExamDetailResponse.Question question(String id, int orderIndex, String type,
                                                        String text, String... options) {
        ExamDetailResponse.Question question = new ExamDetailResponse.Question();
        question.id = id;
        question.orderIndex = orderIndex;
        question.questionType = type;
        question.questionText = text;
        question.options = options.length == 0 ? null : Arrays.asList(options);
        return question;
    }

    private static StartExamResponse started(String sessionToken) {
        StartExamResponse started = new StartExamResponse();
        started.sessionToken = sessionToken;
        return started;
    }

    private static ExamResultResponse examResult(String id) {
        ExamResultResponse result = new ExamResultResponse();
        result.id = id;
        return result;
    }

    private static <T> LiveData<ApiResult<T>> resultOf(ApiResult<T> result) {
        return new MutableLiveData<>(result);
    }
}