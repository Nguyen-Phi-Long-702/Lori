package com.example.lori.viewmodel;

import android.app.Application;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.R;
import com.example.lori.data.remote.dto.ExamDetailResponse;
import com.example.lori.data.remote.dto.StartExamResponse;
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.data.remote.dto.SubmitExamRequest;
import com.example.lori.repository.ApiResult;
import com.example.lori.repository.ExamRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//Quản lý màn làm bài thi tải nội dung đề + xin phiên làm bài, giữ đáp án/câu đã đánh dấu/câu đang xem,
//đồng hồ đếm ngược, nộp bài (tự nộp khi hết giờ)
//ViewModel gắn với ExamTakingActivity nên xoay màn hình không làm mất đáp án và đồng hồ vẫn chạy tiếp
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class ExamTakingViewModel extends AndroidViewModel {

    //Giá trị questionType Backend trả về (CHECK trong V2__exam_tables.sql)
    public static final String TYPE_MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String TYPE_TRUE_FALSE_NOT_GIVEN = "TRUE_FALSE_NOT_GIVEN";
    public static final String TYPE_FILL_BLANK = "FILL_BLANK";
    public static final String TYPE_MATCHING = "MATCHING";

    //Loại đề TOEIC (examType Backend trả về) và mẫu tên section "Part 3 - Conversation 1 (Q32-34)" để biết section thuộc Part nào
    private static final String EXAM_TYPE_TOEIC = "TOEIC";
    private static final Pattern TOEIC_PART_PATTERN = Pattern.compile("^Part\\s+([1-7])\\b");

    //Còn từ 5 phút trở xuống thì đồng hồ chuyển sang màu cảnh báo
    private static final long WARNING_THRESHOLD_SECONDS = 5 * 60L;

    private final ExamRepository examRepository;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> examTitle = new MutableLiveData<>();
    //Toàn bộ câu hỏi của đề theo thứ tự làm bài; null = chưa tải hoặc tải lỗi
    private final MutableLiveData<List<ExamQuestionItem>> questions = new MutableLiveData<>();
    //Vị trí (bắt đầu từ 0) của câu đang xem
    private final MutableLiveData<Integer> currentPosition = new MutableLiveData<>(0);
    //Đáp án đã chọn: questionId -> đáp án. Câu chưa trả lời hoặc để trống thì không có trong map
    private final MutableLiveData<Map<String, String>> answers = new MutableLiveData<>(new HashMap<>());
    //Các questionId đã đánh dấu
    private final MutableLiveData<Set<String>> flagged = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Long> remainingSeconds = new MutableLiveData<>();
    private final MutableLiveData<Boolean> timerWarning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> timeUp = new MutableLiveData<>(false);

    //Nộp bài: đang gọi API / lỗi gần nhất (null = không lỗi) / id kết quả khi nộp thành công (null = chưa nộp)
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<ExamSubmitError> submitError = new MutableLiveData<>();
    private final MutableLiveData<String> submitResultId = new MutableLiveData<>();

    private String examId;
    //Mã phiên làm bài do Backend cấp ở /start; gửi lại khi nộp bài (dùng một lần, chống nộp lặp). null = chưa bắt đầu
    private String sessionToken;
    private CountDownTimer timer;
    //Vị trí đang nghe dở của một file audio, để xoay màn hình hoặc quay lại app thì nghe tiếp
    private String savedAudioUrl;
    private long savedAudioPositionMs;

    public ExamTakingViewModel(@NonNull Application application) {
        super(application);
        examRepository = new ExamRepository(application);
    }

    //Tải nội dung đề rồi xin phiên làm bài (start): đề chỉ hiện và đồng hồ chỉ chạy khi cả hai bước xong
    //Chỉ chạy khi chưa có dữ liệu và chưa có request nào đang chạy
    //nên gọi lại sau khi xoay màn hình là vô hại
    public void load(String examId) {
        if (questions.getValue() != null || Boolean.TRUE.equals(loading.getValue())) {
            return;
        }
        this.examId = examId;
        loading.setValue(true);
        errorMessage.setValue(null);
        LiveData<ApiResult<ExamDetailResponse>> source = examRepository.detail(examId);
        //Nghe một lần rồi gỡ (giống ExamListViewModel)
        source.observeForever(new Observer<ApiResult<ExamDetailResponse>>() {
            @Override
            public void onChanged(ApiResult<ExamDetailResponse> result) {
                source.removeObserver(this);
                handleDetail(result);
            }
        });
    }

    //Bấm Thử lại sau khi tải lỗi
    public void retry() {
        if (examId != null) {
            load(examId);
        }
    }

    private void handleDetail(ApiResult<ExamDetailResponse> result) {
        if (result.isPremiumRequired()) {
            failLoading(R.string.exam_taking_error_premium);
            return;
        }
        if (!result.isSuccess() || result.getData() == null) {
            failLoading(messageFor(result));
            return;
        }
        ExamDetailResponse detail = result.getData();
        List<ExamQuestionItem> items = flatten(detail);
        if (items.isEmpty()) {
            failLoading(R.string.exam_taking_error_empty);
            return;
        }
        //Đề hợp lệ: xin phiên làm bài. Chỉ hiện đề và chạy đồng hồ sau khi có sessionToken
        LiveData<ApiResult<StartExamResponse>> source = examRepository.start(examId);
        source.observeForever(new Observer<ApiResult<StartExamResponse>>() {
            @Override
            public void onChanged(ApiResult<StartExamResponse> startResult) {
                source.removeObserver(this);
                handleStart(detail, items, startResult);
            }
        });
    }

    //Kết quả của /start: có token thì mới hiện đề và bắt đầu đếm ngược
    private void handleStart(ExamDetailResponse detail, List<ExamQuestionItem> items,
                             ApiResult<StartExamResponse> result) {
        if (result.isPremiumRequired()) {
            failLoading(R.string.exam_taking_error_premium);
            return;
        }
        if (!result.isSuccess()) {
            failLoading(messageFor(result));
            return;
        }
        StartExamResponse started = result.getData();
        if (started == null || started.sessionToken == null || started.sessionToken.trim().isEmpty()) {
            failLoading(R.string.auth_error_server);
            return;
        }
        sessionToken = started.sessionToken;
        loading.setValue(false);
        examTitle.setValue(detail.title);
        questions.setValue(items);
        startTimer(detail.durationMinutes * 60L);
    }

    //Tải lỗi (ở bước tải đề hoặc bước start): tắt thanh tiến trình và hiện thông báo kèm nút Thử lại
    private void failLoading(@StringRes int messageRes) {
        loading.setValue(false);
        errorMessage.setValue(messageRes);
    }

    //Gộp các section thành một danh sách câu hỏi phẳng. Backend đã xếp section và câu hỏi đúng thứ tự làm bài
    //Số thứ tự câu hiển thị là vị trí trong danh sách này (IELTS đánh số lại từ 1 ở mỗi kỹ năng nên không dùng orderIndex)
    private static List<ExamQuestionItem> flatten(ExamDetailResponse detail) {
        List<ExamQuestionItem> items = new ArrayList<>();
        if (detail.sections == null) {
            return items;
        }
        boolean toeic = EXAM_TYPE_TOEIC.equals(detail.examType);
        //Part của câu liền trước, để biết câu nào là câu đầu tiên của một Part
        int previousPart = 0;
        for (ExamDetailResponse.Section section : detail.sections) {
            if (section.questions == null) {
                continue;
            }
            int part = toeic ? toeicPartOf(section.title) : 0;
            for (ExamDetailResponse.Question question : section.questions) {
                items.add(new ExamQuestionItem(
                        question.id,
                        section.title,
                        emptyToNull(section.audioUrl),
                        emptyToNull(section.imageUrl),
                        emptyToNull(section.passageText),
                        question.questionType,
                        emptyToNull(question.questionText),
                        question.options,
                        part,
                        part != 0 && part != previousPart));
                previousPart = part;
            }
        }
        return items;
    }

    //Số Part (1-7) đọc từ tên section của đề TOEIC, vd "Part 3 - Conversation 1 (Q32-34)" → 3; 0 nếu tên không theo dạng đó
    private static int toeicPartOf(String sectionTitle) {
        if (sectionTitle == null) {
            return 0;
        }
        Matcher matcher = TOEIC_PART_PATTERN.matcher(sectionTitle);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static String emptyToNull(String text) {
        return text == null || text.trim().isEmpty() ? null : text;
    }

    //Bắt đầu đếm ngược theo thời lượng của đề. CountDownTimer chạy trên luồng chính nên cập nhật LiveData trực tiếp
    private void startTimer(long totalSeconds) {
        remainingSeconds.setValue(totalSeconds);
        timerWarning.setValue(totalSeconds <= WARNING_THRESHOLD_SECONDS);
        timer = new CountDownTimer(totalSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                //Làm tròn lên để đồng hồ hiện đúng số giây còn lại
                long seconds = (millisUntilFinished + 999L) / 1000L;
                remainingSeconds.setValue(seconds);
                timerWarning.setValue(seconds <= WARNING_THRESHOLD_SECONDS);
            }

            @Override
            public void onFinish() {
                remainingSeconds.setValue(0L);
                timerWarning.setValue(true);
                timeUp.setValue(true);
                //Hết giờ: tự nộp bài như thu bài ở phòng thi thật
                submit();
            }
        }.start();
    }

    //Chuyển tới câu ở vị trí position (vuốt trang, chạm ô số, nút Câu tiếp theo)
    public void setCurrentPosition(int position) {
        List<ExamQuestionItem> list = questions.getValue();
        if (list == null || position < 0 || position >= list.size()) {
            return;
        }
        Integer current = currentPosition.getValue();
        if (current != null && current == position) {
            return;
        }
        currentPosition.setValue(position);
    }

    public boolean hasNext() {
        List<ExamQuestionItem> list = questions.getValue();
        Integer current = currentPosition.getValue();
        return list != null && current != null && current < list.size() - 1;
    }

    public void goToNext() {
        Integer current = currentPosition.getValue();
        if (hasNext() && current != null) {
            setCurrentPosition(current + 1);
        }
    }

    //Lưu đáp án của một câu. answer rỗng/null = xóa đáp án. Đáp án được cắt khoảng trắng hai đầu
    public void setAnswer(String questionId, String answer) {
        Map<String, String> updated = new HashMap<>(answers.getValue());
        String trimmed = answer == null ? "" : answer.trim();
        if (trimmed.isEmpty()) {
            if (updated.remove(questionId) == null) {
                return;
            }
        } else if (trimmed.equals(updated.put(questionId, trimmed))) {
            return;
        }
        answers.setValue(updated);
    }

    public String getAnswer(String questionId) {
        Map<String, String> current = answers.getValue();
        return current == null ? null : current.get(questionId);
    }

    //Đánh dấu / bỏ đánh dấu câu đang xem
    public void toggleCurrentFlag() {
        Integer position = currentPosition.getValue();
        ExamQuestionItem item = position == null ? null : getQuestion(position);
        if (item == null) {
            return;
        }
        Set<String> updated = new HashSet<>(flagged.getValue());
        if (!updated.remove(item.questionId)) {
            updated.add(item.questionId);
        }
        flagged.setValue(updated);
    }

    public boolean isFlagged(String questionId) {
        Set<String> current = flagged.getValue();
        return current != null && current.contains(questionId);
    }

    //Câu ở vị trí position; null nếu đề chưa tải hoặc vị trí không hợp lệ
    public ExamQuestionItem getQuestion(int position) {
        List<ExamQuestionItem> list = questions.getValue();
        if (list == null || position < 0 || position >= list.size()) {
            return null;
        }
        return list.get(position);
    }

    public int getQuestionCount() {
        List<ExamQuestionItem> list = questions.getValue();
        return list == null ? 0 : list.size();
    }

    //Số câu đã trả lời (map chỉ chứa câu có đáp án) và số câu đang đánh dấu, hiện trong hộp thoại xác nhận nộp bài
    public int getAnsweredCount() {
        Map<String, String> current = answers.getValue();
        return current == null ? 0 : current.size();
    }

    public int getFlaggedCount() {
        Set<String> current = flagged.getValue();
        return current == null ? 0 : current.size();
    }

    //Nộp bài: gửi sessionToken + các câu đã trả lời, Backend chấm điểm và trả id kết quả
    //Gọi từ nút Nộp bài, nút Nộp lại sau khi lỗi, và tự động khi hết giờ
    //Bỏ qua nếu chưa có phiên làm bài, đang nộp, đã nộp xong, hoặc lần nộp trước gặp lỗi không cứu được
    public void submit() {
        ExamSubmitError lastError = submitError.getValue();
        if (questions.getValue() == null || sessionToken == null || examId == null
                || Boolean.TRUE.equals(submitting.getValue()) || submitResultId.getValue() != null
                || (lastError != null && !lastError.canRetry)) {
            return;
        }
        //Chỉ gửi các câu đã trả lời; câu bỏ trống Backend tự tính là sai
        List<SubmitExamRequest.Answer> body = new ArrayList<>();
        for (Map.Entry<String, String> entry : answers.getValue().entrySet()) {
            body.add(new SubmitExamRequest.Answer(entry.getKey(), entry.getValue()));
        }
        submitError.setValue(null);
        submitting.setValue(true);
        LiveData<ApiResult<ExamResultResponse>> source =
                examRepository.submit(examId, new SubmitExamRequest(sessionToken, body));
        source.observeForever(new Observer<ApiResult<ExamResultResponse>>() {
            @Override
            public void onChanged(ApiResult<ExamResultResponse> result) {
                source.removeObserver(this);
                handleSubmit(result);
            }
        });
    }

    private void handleSubmit(ApiResult<ExamResultResponse> result) {
        submitting.setValue(false);
        if (result.isSuccess() && result.getData() != null && result.getData().id != null) {
            //Đã nộp xong: dừng đồng hồ (để hết giờ không tự nộp thêm lần nữa) rồi báo Activity chuyển sang màn kết quả
            if (timer != null) {
                timer.cancel();
            }
            submitResultId.setValue(result.getData().id);
            return;
        }
        submitError.setValue(submitErrorFor(result));
    }

    //Đóng thông báo lỗi nộp bài (người dùng chọn Đóng): xoay màn hình sẽ không hiện lại hộp thoại
    public void clearSubmitError() {
        submitError.setValue(null);
    }

    //Ghi nhớ vị trí đang nghe của file audio khi màn hình dừng
    public void saveAudioPosition(String audioUrl, long positionMs) {
        savedAudioUrl = audioUrl;
        savedAudioPositionMs = positionMs;
    }

    //Vị trí đã ghi nhớ của đúng file audio này; 0 nếu chưa nghe hoặc đang là file khác
    public long getSavedAudioPosition(String audioUrl) {
        return audioUrl != null && audioUrl.equals(savedAudioUrl) ? savedAudioPositionMs : 0L;
    }

    @Override
    protected void onCleared() {
        if (timer != null) {
            timer.cancel();
        }
    }

    //Lỗi khi nộp bài → câu thông báo + có cho nộp lại không. Chỉ lỗi tạm thời mới nộp lại được
    //(mất mạng giữa chừng có thể khiến Backend đã nhận bài: nộp lại sẽ gặp 400 và câu thông báo 400 chỉ người dùng sang tab Lịch sử)
    private static ExamSubmitError submitErrorFor(ApiResult<?> result) {
        if (result.isPremiumRequired()) {
            return new ExamSubmitError(R.string.exam_taking_error_premium, false);
        }
        switch (result.getStatus()) {
            case OFFLINE:
                return new ExamSubmitError(R.string.exam_submit_error_offline, true);
            case TIMEOUT:
                return new ExamSubmitError(R.string.exam_submit_error_timeout, true);
            case NETWORK_ERROR:
                return new ExamSubmitError(R.string.exam_submit_error_network, true);
            default:
                return submitErrorForHttp(result.getHttpCode());
        }
    }

    private static ExamSubmitError submitErrorForHttp(int httpCode) {
        switch (httpCode) {
            //400: phiên làm bài không hợp lệ / đã dùng / hết hạn (Backend không phân biệt)
            case 400:
                return new ExamSubmitError(R.string.exam_submit_error_session, false);
            case 401:
                return new ExamSubmitError(R.string.premium_error_session_expired, false);
            //403 còn lại là Backend từ chối ứng dụng (sai API key / chữ ký), không phải do Premium
            case 403:
                return new ExamSubmitError(R.string.auth_error_forbidden, false);
            case 404:
                return new ExamSubmitError(R.string.exam_taking_error_not_found, false);
            case 429:
                return new ExamSubmitError(R.string.auth_error_too_many_requests, true);
            default:
                return new ExamSubmitError(R.string.exam_submit_error_server, true);
        }
    }

    //Đổi kết quả lỗi thành câu thông báo tiếng Việt (403 PREMIUM_REQUIRED đã được xử lý riêng trong handleDetail)
    @StringRes
    private static int messageFor(ApiResult<?> result) {
        switch (result.getStatus()) {
            case OFFLINE:
                return R.string.auth_error_offline;
            case TIMEOUT:
                return R.string.auth_error_timeout;
            case NETWORK_ERROR:
                return R.string.auth_error_network;
            default:
                return messageForHttp(result.getHttpCode());
        }
    }

    @StringRes
    private static int messageForHttp(int httpCode) {
        switch (httpCode) {
            case 401:
                return R.string.premium_error_session_expired;
            //403 còn lại là Backend từ chối ứng dụng (sai API key / chữ ký), không phải do Premium
            case 403:
                return R.string.auth_error_forbidden;
            case 404:
                return R.string.exam_taking_error_not_found;
            case 429:
                return R.string.auth_error_too_many_requests;
            default:
                return R.string.auth_error_server;
        }
    }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<String> getExamTitle() { return examTitle; }
    public LiveData<List<ExamQuestionItem>> getQuestions() { return questions; }
    public LiveData<Integer> getCurrentPosition() { return currentPosition; }
    public LiveData<Map<String, String>> getAnswers() { return answers; }
    public LiveData<Set<String>> getFlagged() { return flagged; }
    //Số giây còn lại; null = chưa bắt đầu đếm
    public LiveData<Long> getRemainingSeconds() { return remainingSeconds; }
    //true khi còn từ 5 phút trở xuống
    public LiveData<Boolean> getTimerWarning() { return timerWarning; }
    //true khi đã hết giờ làm bài
    public LiveData<Boolean> getTimeUp() { return timeUp; }
    //true khi đang gọi API nộp bài
    public LiveData<Boolean> getSubmitting() { return submitting; }
    //Lỗi nộp bài gần nhất; null = không lỗi
    public LiveData<ExamSubmitError> getSubmitError() { return submitError; }
    //Id kết quả khi nộp thành công; null = chưa nộp
    public LiveData<String> getSubmitResultId() { return submitResultId; }
}