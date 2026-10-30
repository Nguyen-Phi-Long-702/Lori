package com.example.lori.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiExerciseResponse;
import com.example.lori.viewmodel.AiExerciseViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

//Màn Bài tập AI (Premium): làm bộ bài AI vừa sinh cho một chủ đề yếu, chấm ngay trên máy, cuối lượt hiện điểm
//Nhận category, topicId, topicName qua arguments; mọi trạng thái do AiExerciseViewModel giữ
public class AiExerciseFragment extends Fragment {

    //Khóa Bundle (cùng tên với argument trong nav_graph)
    public static final String ARG_CATEGORY = "category";
    public static final String ARG_TOPIC_ID = "topicId";
    public static final String ARG_TOPIC_NAME = "topicName";

    private static final String[] LETTERS = {"A", "B", "C", "D"};

    private AiExerciseViewModel viewModel;
    private String topicName;
    //Vị trí câu đang hiển thị, để chỉ đổi nội dung câu (và xóa ô nhập) khi sang câu mới
    private int shownIndex = -1;

    private View layoutLoading;
    private View layoutError;
    private TextView tvError;
    private View scrollExercise;
    private View groupPlaying;
    private View groupResult;
    private LinearProgressIndicator progressExercise;
    private TextView tvEyebrow;
    private TextView tvType;
    private TextView tvQuestion;
    private View groupChoices;
    private MaterialButton[] optionButtons;
    private View groupFill;
    private EditText etAnswer;
    private MaterialButton btnCheck;
    private TextView tvFeedback;
    private MaterialButton btnNext;
    private TextView tvScore;
    private TextView tvResultSubtitle;
    private ColorStateList defaultOptionTextColor;
    private int defaultOptionStrokeWidth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_exercise, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String category = args.getString(ARG_CATEGORY);
        int topicId = args.getInt(ARG_TOPIC_ID);
        topicName = args.getString(ARG_TOPIC_NAME);

        layoutLoading = view.findViewById(R.id.layoutExerciseLoading);
        layoutError = view.findViewById(R.id.layoutExerciseError);
        tvError = view.findViewById(R.id.tvExerciseError);
        scrollExercise = view.findViewById(R.id.scrollExercise);
        groupPlaying = view.findViewById(R.id.groupExercisePlaying);
        groupResult = view.findViewById(R.id.groupExerciseResult);
        progressExercise = view.findViewById(R.id.progressExercise);
        tvEyebrow = view.findViewById(R.id.tvExerciseEyebrow);
        tvType = view.findViewById(R.id.tvExerciseType);
        tvQuestion = view.findViewById(R.id.tvExerciseQuestion);
        groupChoices = view.findViewById(R.id.groupExerciseChoices);
        optionButtons = new MaterialButton[]{
                view.findViewById(R.id.btnExerciseOptionA),
                view.findViewById(R.id.btnExerciseOptionB),
                view.findViewById(R.id.btnExerciseOptionC),
                view.findViewById(R.id.btnExerciseOptionD)
        };
        defaultOptionTextColor = optionButtons[0].getTextColors();
        defaultOptionStrokeWidth = optionButtons[0].getStrokeWidth();
        groupFill = view.findViewById(R.id.groupExerciseFill);
        etAnswer = view.findViewById(R.id.etExerciseAnswer);
        btnCheck = view.findViewById(R.id.btnExerciseCheck);
        tvFeedback = view.findViewById(R.id.tvExerciseFeedback);
        btnNext = view.findViewById(R.id.btnExerciseNext);
        tvScore = view.findViewById(R.id.tvExerciseScore);
        tvResultSubtitle = view.findViewById(R.id.tvExerciseResultSubtitle);

        viewModel = new ViewModelProvider(this).get(AiExerciseViewModel.class);

        view.findViewById(R.id.btnBackAiExercise).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnExerciseFinish).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnExerciseRetry).setOnClickListener(v -> viewModel.retry());
        view.findViewById(R.id.btnExerciseMore).setOnClickListener(v -> viewModel.practiceMore());
        btnNext.setOnClickListener(v -> viewModel.next());
        btnCheck.setOnClickListener(v -> {
            //Ẩn bàn phím để thấy ngay phần giải thích bên dưới
            hideKeyboard(v);
            viewModel.answerText(etAnswer.getText().toString());
        });
        for (int i = 0; i < optionButtons.length; i++) {
            int optionIndex = i;
            optionButtons[i].setOnClickListener(v -> viewModel.answerChoice(optionIndex));
        }

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                layoutLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), messageRes -> {
            if (messageRes == null) {
                layoutError.setVisibility(View.GONE);
            } else {
                tvError.setText(messageRes);
                layoutError.setVisibility(View.VISIBLE);
            }
        });
        //User Free (hoặc Premium đã hết hạn): Backend trả 403 PREMIUM_REQUIRED nên mở dialog nâng cấp
        viewModel.getPremiumRequired().observe(getViewLifecycleOwner(), required -> {
            if (Boolean.TRUE.equals(required)) {
                PremiumPromptDialog.newInstance(getString(R.string.ai_tutor_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
                viewModel.consumePremiumRequired();
            }
        });
        viewModel.getExercises().observe(getViewLifecycleOwner(), list -> render());
        viewModel.getCurrentIndex().observe(getViewLifecycleOwner(), index -> render());
        viewModel.getAnswer().observe(getViewLifecycleOwner(), answer -> render());
        viewModel.getFinished().observe(getViewLifecycleOwner(), finished -> render());

        viewModel.start(category, topicId);
    }

    //Vẽ lại toàn bộ màn theo trạng thái hiện tại của ViewModel (các bước bên dưới đều chạy lại an toàn)
    private void render() {
        List<AiExerciseResponse.Exercise> list = viewModel.getExercises().getValue();
        scrollExercise.setVisibility(list == null ? View.GONE : View.VISIBLE);
        if (list == null) {
            shownIndex = -1;
            return;
        }

        boolean finished = Boolean.TRUE.equals(viewModel.getFinished().getValue());
        groupPlaying.setVisibility(finished ? View.GONE : View.VISIBLE);
        groupResult.setVisibility(finished ? View.VISIBLE : View.GONE);
        if (finished) {
            tvScore.setText(getString(R.string.quiz_score_format, viewModel.getCorrectCount().getValue(), list.size()));
            tvResultSubtitle.setText(getString(R.string.ai_exercise_result_subtitle_format, topicName));
            return;
        }

        AiExerciseResponse.Exercise exercise = viewModel.getCurrentExercise();
        if (exercise == null) {
            return;
        }
        int index = viewModel.getCurrentIndex().getValue();
        boolean multipleChoice = AiExerciseViewModel.TYPE_MULTIPLE_CHOICE.equals(exercise.type);
        if (index != shownIndex) {
            shownIndex = index;
            bindQuestion(exercise, index, list.size(), multipleChoice);
        }
        bindAnswerState(exercise, index == list.size() - 1, multipleChoice);
    }

    private void bindQuestion(AiExerciseResponse.Exercise exercise, int index, int total, boolean multipleChoice) {
        progressExercise.setMax(total);
        progressExercise.setProgress(index + 1);
        tvEyebrow.setText(getString(R.string.quiz_position_format, index + 1, total, topicName));
        tvType.setText(multipleChoice ? R.string.ai_exercise_type_choice : R.string.ai_exercise_type_fill);
        tvQuestion.setText(exercise.question);
        groupChoices.setVisibility(multipleChoice ? View.VISIBLE : View.GONE);
        groupFill.setVisibility(multipleChoice ? View.GONE : View.VISIBLE);
        if (multipleChoice) {
            for (int i = 0; i < optionButtons.length; i++) {
                optionButtons[i].setText(getString(R.string.quiz_option_format, LETTERS[i], exercise.options.get(i)));
            }
        } else {
            etAnswer.setText("");
        }
    }

    //Tô màu đúng/sai và hiện giải thích sau khi trả lời; chưa trả lời thì đưa các nút về trạng thái ban đầu
    private void bindAnswerState(AiExerciseResponse.Exercise exercise, boolean lastQuestion, boolean multipleChoice) {
        AiExerciseViewModel.Answer answer = viewModel.getAnswer().getValue();
        boolean answered = answer != null;

        resetOptionStyles();
        for (int i = 0; i < optionButtons.length; i++) {
            optionButtons[i].setEnabled(!answered);
            if (answered && multipleChoice) {
                if (i == exercise.answerIndex) {
                    tintOption(optionButtons[i], R.color.quiz_correct_bg, R.color.quiz_correct_text);
                } else if (i == answer.selectedOption) {
                    tintOption(optionButtons[i], R.color.quiz_incorrect_bg, R.color.quiz_incorrect_text);
                }
            }
        }
        etAnswer.setEnabled(!answered);
        btnCheck.setEnabled(!answered);

        tvFeedback.setVisibility(answered ? View.VISIBLE : View.GONE);
        btnNext.setVisibility(answered ? View.VISIBLE : View.GONE);
        btnNext.setText(lastQuestion ? R.string.ai_exercise_result_button : R.string.quiz_next_button);
        if (!answered) {
            return;
        }
        Context context = requireContext();
        tvFeedback.setBackgroundColor(ContextCompat.getColor(context,
                answer.correct ? R.color.quiz_correct_bg : R.color.quiz_incorrect_bg));
        tvFeedback.setTextColor(ContextCompat.getColor(context,
                answer.correct ? R.color.quiz_correct_text : R.color.quiz_incorrect_text));
        tvFeedback.setText(answer.correct
                ? getString(R.string.ai_exercise_feedback_correct_format, exercise.explanation)
                : getString(R.string.ai_exercise_feedback_wrong_format, correctAnswerText(exercise, multipleChoice),
                exercise.explanation));
    }

    private static String correctAnswerText(AiExerciseResponse.Exercise exercise, boolean multipleChoice) {
        return multipleChoice ? exercise.options.get(exercise.answerIndex) : exercise.answers.get(0);
    }

    private void tintOption(MaterialButton button, @ColorRes int bgColorRes, @ColorRes int textColorRes) {
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bgColorRes)));
        button.setTextColor(ContextCompat.getColor(requireContext(), textColorRes));
        button.setStrokeWidth(0);
    }

    private void resetOptionStyles() {
        for (MaterialButton button : optionButtons) {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setTextColor(defaultOptionTextColor);
            button.setStrokeWidth(defaultOptionStrokeWidth);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}