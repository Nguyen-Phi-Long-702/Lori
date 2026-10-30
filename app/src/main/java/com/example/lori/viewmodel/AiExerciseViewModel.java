package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.lori.data.remote.dto.AiExerciseResponse;
import com.example.lori.repository.AiRepository;
import com.example.lori.repository.ApiResult;
import com.example.lori.utils.AiExerciseChecker;

import java.util.List;

//Quản lý một lượt Bài tập AI cho một chủ đề: gọi Backend sinh bài, giữ câu hiện tại, chấm đáp án, đếm điểm
//Bài do AI sinh được chấm ngay trên máy bằng đáp án Backend gửi kèm; kết quả không ghi vào tiến trình học
//Giao diện chỉ quan sát các LiveData bên dưới; thông báo lỗi là mã chuỗi R.string (null = không lỗi)
public class AiExerciseViewModel extends AndroidViewModel {

    public static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";

    //Kết quả chấm câu hiện tại. selectedOption = vị trí lựa chọn đã bấm (trắc nghiệm), -1 với bài điền chỗ trống
    public static final class Answer {
        public final int selectedOption;
        public final boolean correct;

        Answer(int selectedOption, boolean correct) {
            this.selectedOption = selectedOption;
            this.correct = correct;
        }
    }

    private final AiRepository aiRepository;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> errorMessage = new MutableLiveData<>();
    //true khi Backend trả 403 PREMIUM_REQUIRED: giao diện mở PremiumPromptDialog rồi gọi consumePremiumRequired()
    private final MutableLiveData<Boolean> premiumRequired = new MutableLiveData<>();
    //Bộ bài của lượt hiện tại; null = chưa có (đang tải hoặc lỗi)
    private final MutableLiveData<List<AiExerciseResponse.Exercise>> exercises = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    //null = câu hiện tại chưa trả lời
    private final MutableLiveData<Answer> answer = new MutableLiveData<>();
    private final MutableLiveData<Integer> correctCount = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> finished = new MutableLiveData<>(false);

    private String category;
    private int topicId;
    private boolean started;

    public AiExerciseViewModel(@NonNull Application application) {
        super(application);
        aiRepository = new AiRepository(application);
    }

    //Gọi khi màn hình hiện lên. Đã bắt đầu rồi (xoay màn hình) thì bỏ qua để không gọi AI lại
    public void start(String category, int topicId) {
        if (started) {
            return;
        }
        started = true;
        this.category = category;
        this.topicId = topicId;
        fetch();
    }

    //Bấm Thử lại sau khi tải lỗi
    public void retry() {
        if (exercises.getValue() == null && !isLoading()) {
            fetch();
        }
    }

    //Bấm Luyện thêm sau khi xong một lượt: sinh bộ mới cho cùng chủ đề (Backend tránh lặp lại câu đã sinh)
    public void practiceMore() {
        if (!isLoading()) {
            fetch();
        }
    }

    private void fetch() {
        errorMessage.setValue(null);
        exercises.setValue(null);
        answer.setValue(null);
        currentIndex.setValue(0);
        correctCount.setValue(0);
        finished.setValue(false);
        loading.setValue(true);

        LiveData<ApiResult<AiExerciseResponse>> source = aiRepository.generateExercise(category, topicId);
        //Nghe một lần rồi gỡ (giống SentenceCheckViewModel)
        source.observeForever(new Observer<ApiResult<AiExerciseResponse>>() {
            @Override
            public void onChanged(ApiResult<AiExerciseResponse> apiResult) {
                source.removeObserver(this);
                handleResult(apiResult);
            }
        });
    }

    private void handleResult(ApiResult<AiExerciseResponse> apiResult) {
        loading.setValue(false);
        AiExerciseResponse body = apiResult.getData();
        if (apiResult.isSuccess() && body != null && body.exercises != null && !body.exercises.isEmpty()) {
            exercises.setValue(body.exercises);
        } else if (apiResult.isPremiumRequired()) {
            premiumRequired.setValue(true);
        } else {
            errorMessage.setValue(AiErrorMessages.of(apiResult));
        }
    }

    //Trả lời bài trắc nghiệm bằng vị trí lựa chọn. Mỗi câu chỉ chấm một lần
    public void answerChoice(int optionIndex) {
        AiExerciseResponse.Exercise current = getCurrentExercise();
        if (current == null || answer.getValue() != null || current.answerIndex == null) {
            return;
        }
        record(new Answer(optionIndex, optionIndex == current.answerIndex));
    }

    //Trả lời bài điền vào chỗ trống. Ô trống thì bỏ qua (giao diện đã chặn trước)
    public void answerText(String input) {
        AiExerciseResponse.Exercise current = getCurrentExercise();
        if (current == null || answer.getValue() != null || input == null || input.trim().isEmpty()) {
            return;
        }
        record(new Answer(-1, AiExerciseChecker.isFillBlankCorrect(input, current.answers)));
    }

    private void record(Answer result) {
        if (result.correct) {
            correctCount.setValue(correctCount.getValue() + 1);
        }
        answer.setValue(result);
    }

    //Bấm Câu tiếp theo / Xem kết quả. Chưa trả lời thì bỏ qua
    public void next() {
        List<AiExerciseResponse.Exercise> list = exercises.getValue();
        if (list == null || answer.getValue() == null) {
            return;
        }
        int index = currentIndex.getValue();
        if (index >= list.size() - 1) {
            finished.setValue(true);
        } else {
            answer.setValue(null);
            currentIndex.setValue(index + 1);
        }
    }

    @Nullable
    public AiExerciseResponse.Exercise getCurrentExercise() {
        List<AiExerciseResponse.Exercise> list = exercises.getValue();
        int index = currentIndex.getValue();
        return list == null || index >= list.size() ? null : list.get(index);
    }

    public void consumePremiumRequired() {
        premiumRequired.setValue(null);
    }

    private boolean isLoading() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    public LiveData<Boolean> getLoading() { return loading; }
    //Lỗi hiện thay cho nội dung bài; null = không lỗi
    public LiveData<Integer> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getPremiumRequired() { return premiumRequired; }
    public LiveData<List<AiExerciseResponse.Exercise>> getExercises() { return exercises; }
    public LiveData<Integer> getCurrentIndex() { return currentIndex; }
    public LiveData<Answer> getAnswer() { return answer; }
    public LiveData<Integer> getCorrectCount() { return correctCount; }
    public LiveData<Boolean> getFinished() { return finished; }
}