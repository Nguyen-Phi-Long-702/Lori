package com.example.lori.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lori.R;
import com.example.lori.viewmodel.AiInsight;
import com.example.lori.viewmodel.QuizExamAiInsightViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Màn Phân tích AI (Premium) sau một quiz hoặc một lần thi: nhận xét tổng quan, chỗ yếu, kiểu lỗi hay gặp và đề xuất ôn tập
//Nhận source ("vocab" / "grammar" cho quiz, "exam" cho bài thi), itemId (quiz) hoặc resultId (bài thi) qua arguments
public class QuizExamAiInsightFragment extends Fragment {

    public static final String ARG_SOURCE = "source";
    public static final String ARG_ITEM_ID = "itemId";
    public static final String ARG_RESULT_ID = "resultId";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz_exam_ai_insight, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        QuizExamAiInsightViewModel viewModel = new ViewModelProvider(this).get(QuizExamAiInsightViewModel.class);
        Bundle args = requireArguments();
        String source = args.getString(ARG_SOURCE);
        int itemId = args.getInt(ARG_ITEM_ID);
        String resultId = args.getString(ARG_RESULT_ID);

        LinearProgressIndicator progress = view.findViewById(R.id.progressAiInsight);
        View layoutError = view.findViewById(R.id.layoutAiInsightError);
        TextView tvError = view.findViewById(R.id.tvAiInsightError);
        View scroll = view.findViewById(R.id.scrollAiInsight);
        View cardSummary = view.findViewById(R.id.cardAiInsightSummary);
        TextView tvSummary = view.findViewById(R.id.tvAiInsightSummary);
        View tvNoWeak = view.findViewById(R.id.tvAiInsightNoWeak);
        View tvWeakTitle = view.findViewById(R.id.tvAiInsightWeakTitle);
        LinearLayout llWeak = view.findViewById(R.id.llAiInsightWeak);
        View tvPatternsTitle = view.findViewById(R.id.tvAiInsightPatternsTitle);
        TextView tvPatterns = view.findViewById(R.id.tvAiInsightPatterns);
        View tvRecommendTitle = view.findViewById(R.id.tvAiInsightRecommendTitle);
        LinearLayout llRecommendations = view.findViewById(R.id.llAiInsightRecommendations);
        View btnStart = view.findViewById(R.id.btnAiInsightStart);

        view.findViewById(R.id.btnBackAiInsight).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnAiInsightRetry).setOnClickListener(v -> viewModel.load(source, itemId, resultId));
        btnStart.setOnClickListener(v ->
                navigate(R.id.action_quizExamAiInsightFragment_to_aiPersonalizedReviewFragment, null));

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));
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
                PremiumPromptDialog.newInstance(getString(R.string.quiz_ai_insight_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
                viewModel.consumePremiumRequired();
            }
        });
        viewModel.getInsight().observe(getViewLifecycleOwner(), insight -> {
            scroll.setVisibility(insight == null ? View.GONE : View.VISIBLE);
            if (insight == null) {
                return;
            }
            boolean hasWeak = !insight.weakRows.isEmpty();
            boolean hasSummary = insight.summary != null && !insight.summary.trim().isEmpty();
            boolean hasPatterns = !insight.patterns.isEmpty();
            boolean hasRecommendations = !insight.recommendations.isEmpty();

            cardSummary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            tvSummary.setText(insight.summary);
            tvNoWeak.setVisibility(hasWeak ? View.GONE : View.VISIBLE);
            tvWeakTitle.setVisibility(hasWeak ? View.VISIBLE : View.GONE);
            tvPatternsTitle.setVisibility(hasPatterns ? View.VISIBLE : View.GONE);
            tvPatterns.setVisibility(hasPatterns ? View.VISIBLE : View.GONE);
            tvPatterns.setText(hasPatterns ? "• " + TextUtils.join("\n• ", insight.patterns) : "");
            tvRecommendTitle.setVisibility(hasRecommendations ? View.VISIBLE : View.GONE);
            btnStart.setVisibility(hasWeak ? View.VISIBLE : View.GONE);

            llWeak.removeAllViews();
            for (AiInsight.WeakRow row : insight.weakRows) {
                AiInsightViews.addWeakRow(llWeak, row.name,
                        getString(R.string.ai_weak_wrong_format, row.wrong, row.total), row.wrongPercent, null);
            }
            AiInsightViews.bindRecommendations(llRecommendations, insight.recommendations, this::openLesson);
        });

        viewModel.load(source, itemId, resultId);
    }

    private void openLesson(int lessonId) {
        Bundle args = new Bundle();
        args.putInt("lessonId", lessonId);
        navigate(R.id.action_quizExamAiInsightFragment_to_grammarDetailFragment, args);
    }

    //Chỉ chuyển khi đang ở màn này: chạm nhanh hai lần sẽ không chuyển lần hai (action không còn hợp lệ từ màn khác sẽ làm app văng)
    private void navigate(@IdRes int actionId, @Nullable Bundle args) {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current == null || current.getId() != R.id.quizExamAiInsightFragment) {
            return;
        }
        navController.navigate(actionId, args);
    }
}