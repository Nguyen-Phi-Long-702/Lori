package com.example.lori.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.lori.data.local.AppDatabase;
import com.example.lori.data.local.entity.GrammarQuiz;
import com.example.lori.data.local.entity.UserProgressLocal;
import com.example.lori.data.local.entity.VocabQuiz;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Quản lý dữ liệu và trạng thái làm bài của màn hình quiz
public class QuizViewModel extends AndroidViewModel {

    public static final String TYPE_VOCAB = "vocab";
    public static final String TYPE_GRAMMAR = "grammar";

    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String quizType;
    private int itemId;
    private final MutableLiveData<String> itemTitle = new MutableLiveData<>();

    private final MutableLiveData<List<QuizQuestion>> questions = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final MutableLiveData<String> selectedAnswer = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> quizFinished = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> correctCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> incorrectCount = new MutableLiveData<>(0);

    public QuizViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    //Tải danh sách câu hỏi theo loại quiz + id chủ đề/bài học. getVocabQuizByTopic/getGrammarQuizByLesson là hàm đồng bộ nên bắt buộc chạy nền.
    public void loadQuiz(String quizType, int itemId, String itemTitle) {
        this.quizType = quizType;
        this.itemId = itemId;
        this.itemTitle.setValue(itemTitle);

        executor.execute(() -> {
            List<QuizQuestion> result = new ArrayList<>();
            if (TYPE_VOCAB.equals(quizType)) {
                List<VocabQuiz> raw = db.quizDao().getVocabQuizByTopic(itemId);
                for (VocabQuiz q : raw) result.add(QuizQuestion.fromVocabQuiz(q));
            } else if (TYPE_GRAMMAR.equals(quizType)) {
                List<GrammarQuiz> raw = db.quizDao().getGrammarQuizByLesson(itemId);
                for (GrammarQuiz q : raw) result.add(QuizQuestion.fromGrammarQuiz(q));
            }
            questions.postValue(result);
        });
    }

    //Ghi nhận đáp án người dùng chọn cho câu hỏi hiện tại. Chỉ chấm điểm 1 lần cho mỗi câu. Nếu đã chọn rồi thì bỏ qua các lần bấm sau.
    public void selectAnswer(String letter) {
        if (selectedAnswer.getValue() != null) return;
        QuizQuestion current = getCurrentQuestion();
        if (current == null) return;

        selectedAnswer.setValue(letter);
        if (current.correctAnswer.equalsIgnoreCase(letter)) {
            correctCount.setValue(correctCount.getValue() + 1);
        } else {
            incorrectCount.setValue(incorrectCount.getValue() + 1);
        }
    }

    //Chuyển sang câu tiếp theo. Nếu đã là câu cuối thì kết thúc bài và lưu tiến độ.
    public void nextQuestion() {
        List<QuizQuestion> list = questions.getValue();
        int index = currentIndex.getValue() == null ? 0 : currentIndex.getValue();
        if (list == null) return;

        if (index >= list.size() - 1) {
            saveProgress();
            quizFinished.setValue(true);
        } else {
            currentIndex.setValue(index + 1);
            selectedAnswer.setValue(null);
        }
    }

    private void saveProgress() {
        String progressType = TYPE_VOCAB.equals(quizType)
                ? UserProgressLocal.TYPE_VOCAB_QUIZ
                : UserProgressLocal.TYPE_GRAMMAR_QUIZ;
        int correct = correctCount.getValue() == null ? 0 : correctCount.getValue();
        int incorrect = incorrectCount.getValue() == null ? 0 : incorrectCount.getValue();
        int id = itemId;

        executor.execute(() -> {
            UserProgressLocal existing = db.progressDao().getProgress(progressType, id);
            if (existing != null) {
                existing.status = UserProgressLocal.STATUS_COMPLETED;
                existing.correctCount = correct;
                existing.incorrectCount = incorrect;
                existing.lastStudiedAt = new Date();
                db.progressDao().update(existing);
            } else {
                UserProgressLocal newProgress = new UserProgressLocal();
                newProgress.itemType = progressType;
                newProgress.itemId = id;
                newProgress.status = UserProgressLocal.STATUS_COMPLETED;
                newProgress.correctCount = correct;
                newProgress.incorrectCount = incorrect;
                newProgress.lastStudiedAt = new Date();
                newProgress.isSynced = false;
                db.progressDao().insertOrUpdate(newProgress);
            }
        });
    }

    public QuizQuestion getCurrentQuestion() {
        List<QuizQuestion> list = questions.getValue();
        int index = currentIndex.getValue() == null ? 0 : currentIndex.getValue();
        if (list == null || index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    public int getTotalQuestions() {
        List<QuizQuestion> list = questions.getValue();
        return list == null ? 0 : list.size();
    }

    public LiveData<List<QuizQuestion>> getQuestions() { return questions; }
    public LiveData<Integer> getCurrentIndex() { return currentIndex; }
    public LiveData<String> getSelectedAnswer() { return selectedAnswer; }
    public LiveData<Boolean> getQuizFinished() { return quizFinished; }
    public LiveData<Integer> getCorrectCount() { return correctCount; }
    public LiveData<Integer> getIncorrectCount() { return incorrectCount; }
    public LiveData<String> getItemTitle() { return itemTitle; }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}