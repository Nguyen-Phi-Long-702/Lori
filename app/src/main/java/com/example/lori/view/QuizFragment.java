package com.example.lori.view;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.utils.PremiumManager;
import com.example.lori.viewmodel.QuizQuestion;
import com.example.lori.viewmodel.QuizViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Fragment làm bài kiểm tra, dùng chung cho quiz từ vựng (theo chủ đề) và
//quiz ngữ pháp (theo bài học), phân biệt qua tham số quizType.
public class QuizFragment extends Fragment {

    private QuizViewModel viewModel;
    private String quizType;
    private String itemTitle;

    private View groupPlaying;
    private View groupResult;
    private LinearProgressIndicator progressQuiz;
    private TextView tvQuizEyebrow;
    private TextView tvQuizQuestion;
    private MaterialButton[] optionButtons;
    private TextView tvQuizExplanation;
    private MaterialButton btnQuizNext;
    private TextView tvQuizScore;
    private TextView tvQuizResultSubtitle;
    private TextView tvQuizCorrectCount;
    private TextView tvQuizIncorrectCount;
    private ColorStateList defaultOptionTextColor;
    private int defaultOptionStrokeWidth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        quizType = args.getString("quizType");
        int itemId = args.getInt("itemId");
        itemTitle = args.getString("itemTitle");

        ImageButton btnBack = view.findViewById(R.id.btnBackQuiz);
        TextView tvHeaderTitle = view.findViewById(R.id.tvQuizHeaderTitle);
        groupPlaying = view.findViewById(R.id.groupQuizPlaying);
        groupResult = view.findViewById(R.id.groupQuizResult);
        progressQuiz = view.findViewById(R.id.progressQuiz);
        tvQuizEyebrow = view.findViewById(R.id.tvQuizEyebrow);
        tvQuizQuestion = view.findViewById(R.id.tvQuizQuestion);
        optionButtons = new MaterialButton[]{
                view.findViewById(R.id.btnOptionA),
                view.findViewById(R.id.btnOptionB),
                view.findViewById(R.id.btnOptionC),
                view.findViewById(R.id.btnOptionD)
        };
        defaultOptionTextColor = optionButtons[0].getTextColors();
        defaultOptionStrokeWidth = optionButtons[0].getStrokeWidth();
        tvQuizExplanation = view.findViewById(R.id.tvQuizExplanation);
        btnQuizNext = view.findViewById(R.id.btnQuizNext);
        tvQuizScore = view.findViewById(R.id.tvQuizScore);
        tvQuizResultSubtitle = view.findViewById(R.id.tvQuizResultSubtitle);
        tvQuizCorrectCount = view.findViewById(R.id.tvQuizCorrectCount);
        tvQuizIncorrectCount = view.findViewById(R.id.tvQuizIncorrectCount);
        MaterialCardView cardAiInsight = view.findViewById(R.id.cardAiInsight);
        MaterialButton btnQuizFinish = view.findViewById(R.id.btnQuizFinish);

        tvHeaderTitle.setText(QuizViewModel.TYPE_VOCAB.equals(quizType)
                ? R.string.quiz_title_vocab : R.string.quiz_title_grammar);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        btnQuizFinish.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        String[] letters = {"A", "B", "C", "D"};
        for (int i = 0; i < optionButtons.length; i++) {
            String letter = letters[i];
            optionButtons[i].setOnClickListener(v -> viewModel.selectAnswer(letter));
        }
        btnQuizNext.setOnClickListener(v -> viewModel.nextQuestion());

        cardAiInsight.setOnClickListener(v -> {
            if (PremiumManager.isPremium(requireContext())) {
                Toast.makeText(requireContext(), R.string.quiz_ai_insight_not_ready_toast, Toast.LENGTH_SHORT).show();
            } else {
                PremiumPromptDialog.newInstance(getString(R.string.quiz_ai_insight_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
            }
        });

        viewModel = new ViewModelProvider(this).get(QuizViewModel.class);
        viewModel.loadQuiz(quizType, itemId, itemTitle);

        viewModel.getQuestions().observe(getViewLifecycleOwner(), questions -> renderQuestion());
        viewModel.getCurrentIndex().observe(getViewLifecycleOwner(), index -> renderQuestion());
        viewModel.getSelectedAnswer().observe(getViewLifecycleOwner(), this::renderAnswerState);
        viewModel.getQuizFinished().observe(getViewLifecycleOwner(), finished -> {
            if (Boolean.TRUE.equals(finished)) showResult();
        });
    }

    //Hiển thị câu hỏi hiện tại lên giao diện
    private void renderQuestion() {
        QuizQuestion question = viewModel.getCurrentQuestion();
        if (question == null) return;

        int total = viewModel.getTotalQuestions();
        int index = viewModel.getCurrentIndex().getValue() == null ? 0 : viewModel.getCurrentIndex().getValue();

        progressQuiz.setMax(Math.max(total, 1));
        progressQuiz.setProgress(index + 1);
        tvQuizEyebrow.setText(getString(R.string.quiz_position_format, index + 1, total, itemTitle));
        tvQuizQuestion.setText(question.question);

        String[] optionTexts = {question.optionA, question.optionB, question.optionC, question.optionD};
        String[] letters = {"A", "B", "C", "D"};
        for (int i = 0; i < optionButtons.length; i++) {
            optionButtons[i].setText(getString(R.string.quiz_option_format, letters[i], optionTexts[i]));
        }

        resetOptionStyles();
        tvQuizExplanation.setVisibility(View.GONE);
        btnQuizNext.setEnabled(false);
        for (MaterialButton button : optionButtons) button.setEnabled(true);
    }

    //Tô màu đúng/sai sau khi người dùng chọn 1 đáp án, hiển thị giải thích nếu có
    private void renderAnswerState(String selectedLetter) {
        if (selectedLetter == null) return;
        QuizQuestion question = viewModel.getCurrentQuestion();
        if (question == null) return;

        String[] letters = {"A", "B", "C", "D"};
        for (int i = 0; i < optionButtons.length; i++) {
            optionButtons[i].setEnabled(false);
            if (letters[i].equalsIgnoreCase(question.correctAnswer)) {
                tintOption(optionButtons[i], R.color.quiz_correct_bg, R.color.quiz_correct_text);
            } else if (letters[i].equals(selectedLetter)) {
                tintOption(optionButtons[i], R.color.quiz_incorrect_bg, R.color.quiz_incorrect_text);
            }
        }

        if (question.explanation != null && !question.explanation.trim().isEmpty()) {
            tvQuizExplanation.setVisibility(View.VISIBLE);
            tvQuizExplanation.setText(getString(R.string.quiz_explanation_format, question.explanation));
        }

        btnQuizNext.setEnabled(true);
    }

    private void tintOption(MaterialButton button, int bgColorRes, int textColorRes) {
        int bg = ContextCompat.getColor(requireContext(), bgColorRes);
        int text = ContextCompat.getColor(requireContext(), textColorRes);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setTextColor(text);
        button.setStrokeWidth(0);
    }

    private void resetOptionStyles() {
        for (MaterialButton button : optionButtons) {
            button.setBackgroundTintList(null);
            button.setTextColor(defaultOptionTextColor);
            button.setStrokeWidth(defaultOptionStrokeWidth);
        }
    }

    //Chuyển sang màn hình kết quả sau khi hoàn tất bài kiểm tra
    private void showResult() {
        groupPlaying.setVisibility(View.GONE);
        groupResult.setVisibility(View.VISIBLE);

        int correct = viewModel.getCorrectCount().getValue() == null ? 0 : viewModel.getCorrectCount().getValue();
        int incorrect = viewModel.getIncorrectCount().getValue() == null ? 0 : viewModel.getIncorrectCount().getValue();
        int total = viewModel.getTotalQuestions();
        String quizLabel = getString(QuizViewModel.TYPE_VOCAB.equals(quizType)
                ? R.string.quiz_title_vocab : R.string.quiz_title_grammar);

        tvQuizScore.setText(getString(R.string.quiz_score_format, correct, total));
        tvQuizResultSubtitle.setText(getString(R.string.quiz_result_subtitle_format, itemTitle, quizLabel));
        tvQuizCorrectCount.setText(getString(R.string.quiz_correct_label_format, correct));
        tvQuizIncorrectCount.setText(getString(R.string.quiz_incorrect_label_format, incorrect));
    }
}