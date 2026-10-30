package com.example.lori.view;

import android.os.Bundle;
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
import com.example.lori.data.remote.dto.AiRecommendResponse;
import com.example.lori.data.remote.dto.LearningTopicResponse;
import com.example.lori.viewmodel.AiPersonalizedReviewViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

//Màn Ôn tập hôm nay (Premium): nhận xét của AI, các chủ đề yếu nhất (bấm để luyện chủ đề đó) và các đề xuất ôn tập
public class AiPersonalizedReviewFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_personalized_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AiPersonalizedReviewViewModel viewModel = new ViewModelProvider(this).get(AiPersonalizedReviewViewModel.class);

        LinearProgressIndicator progress = view.findViewById(R.id.progressAiReview);
        View layoutError = view.findViewById(R.id.layoutAiReviewError);
        TextView tvError = view.findViewById(R.id.tvAiReviewError);
        View scroll = view.findViewById(R.id.scrollAiReview);
        View cardSummary = view.findViewById(R.id.cardAiReviewSummary);
        TextView tvSummary = view.findViewById(R.id.tvAiReviewSummary);
        View tvEmpty = view.findViewById(R.id.tvAiReviewEmpty);
        View tvTopicsTitle = view.findViewById(R.id.tvAiReviewTopicsTitle);
        LinearLayout llTopics = view.findViewById(R.id.llAiReviewTopics);
        View tvRecommendTitle = view.findViewById(R.id.tvAiReviewRecommendTitle);
        LinearLayout llRecommendations = view.findViewById(R.id.llAiReviewRecommendations);
        View btnStart = view.findViewById(R.id.btnAiReviewStart);

        view.findViewById(R.id.btnBackAiReview).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnAiReviewRetry).setOnClickListener(v -> viewModel.load());
        //Bắt đầu ôn tập: luyện chủ đề yếu nhất (đứng đầu danh sách)
        btnStart.setOnClickListener(v -> {
            AiRecommendResponse current = viewModel.getResult().getValue();
            if (current != null && !current.topics.isEmpty()) {
                openExercise(current.topics.get(0));
            }
        });

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
                PremiumPromptDialog.newInstance(getString(R.string.ai_tutor_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
                viewModel.consumePremiumRequired();
            }
        });
        viewModel.getResult().observe(getViewLifecycleOwner(), result -> {
            scroll.setVisibility(result == null ? View.GONE : View.VISIBLE);
            if (result == null) {
                return;
            }
            List<LearningTopicResponse> topics = result.topics;
            boolean hasTopics = !topics.isEmpty();
            boolean hasSummary = result.summary != null && !result.summary.trim().isEmpty();
            boolean hasRecommendations = result.recommendations != null && !result.recommendations.isEmpty();

            tvEmpty.setVisibility(hasTopics ? View.GONE : View.VISIBLE);
            cardSummary.setVisibility(hasTopics && hasSummary ? View.VISIBLE : View.GONE);
            tvSummary.setText(result.summary);
            tvTopicsTitle.setVisibility(hasTopics ? View.VISIBLE : View.GONE);
            btnStart.setVisibility(hasTopics ? View.VISIBLE : View.GONE);
            tvRecommendTitle.setVisibility(hasRecommendations ? View.VISIBLE : View.GONE);

            llTopics.removeAllViews();
            for (LearningTopicResponse topic : topics) {
                int total = topic.correctCount + topic.incorrectCount;
                AiInsightViews.addWeakRow(llTopics, topic.topicName,
                        getString(R.string.ai_weak_wrong_format, topic.incorrectCount, total),
                        total == 0 ? 0 : topic.incorrectCount * 100 / total,
                        () -> openExercise(topic));
            }
            AiInsightViews.bindRecommendations(llRecommendations, result.recommendations, this::openLesson);
        });

        viewModel.load();
    }

    private void openExercise(LearningTopicResponse topic) {
        Bundle args = new Bundle();
        args.putString(AiExerciseFragment.ARG_CATEGORY, topic.category);
        args.putInt(AiExerciseFragment.ARG_TOPIC_ID, Integer.parseInt(topic.topicKey));
        args.putString(AiExerciseFragment.ARG_TOPIC_NAME, topic.topicName);
        navigate(R.id.action_aiPersonalizedReviewFragment_to_aiExerciseFragment, args);
    }

    private void openLesson(int lessonId) {
        Bundle args = new Bundle();
        args.putInt("lessonId", lessonId);
        navigate(R.id.action_aiPersonalizedReviewFragment_to_grammarDetailFragment, args);
    }

    //Chỉ chuyển khi đang ở màn này: chạm nhanh hai lần sẽ không chuyển lần hai (action không còn hợp lệ từ màn khác sẽ làm app văng)
    private void navigate(@IdRes int actionId, Bundle args) {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current == null || current.getId() != R.id.aiPersonalizedReviewFragment) {
            return;
        }
        navController.navigate(actionId, args);
    }
}