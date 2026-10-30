package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lori.R;
import com.example.lori.data.remote.dto.LearningProfileResponse;
import com.example.lori.data.remote.dto.LearningTopicResponse;
import com.example.lori.viewmodel.LearningInsightViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Màn Hồ sơ năng lực: trình độ ước tính, điểm mạnh và điểm yếu tổng quan. Không cần Premium; ôn theo điểm yếu thì sang Ôn tập hôm nay (Premium)
public class LearningInsightFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learning_insight, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LearningInsightViewModel viewModel = new ViewModelProvider(this).get(LearningInsightViewModel.class);

        LinearProgressIndicator progress = view.findViewById(R.id.progressLearningInsight);
        View layoutError = view.findViewById(R.id.layoutLearningInsightError);
        TextView tvError = view.findViewById(R.id.tvLearningInsightError);
        View scroll = view.findViewById(R.id.scrollLearningInsight);
        CircularProgressIndicator progressLevel = view.findViewById(R.id.progressLearningLevel);
        TextView tvLevel = view.findViewById(R.id.tvLearningLevel);
        ChipGroup chipStrengths = view.findViewById(R.id.chipGroupLearningStrengths);
        View tvNoStrengths = view.findViewById(R.id.tvLearningNoStrengths);
        LinearLayout llWeaknesses = view.findViewById(R.id.llLearningWeaknesses);
        View tvNoWeaknesses = view.findViewById(R.id.tvLearningNoWeaknesses);
        View btnReview = view.findViewById(R.id.btnLearningInsightReview);

        view.findViewById(R.id.btnBackLearningInsight).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnLearningInsightRetry).setOnClickListener(v -> viewModel.load());
        btnReview.setOnClickListener(v -> openReview());

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
        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            scroll.setVisibility(profile == null ? View.GONE : View.VISIBLE);
            if (profile == null) {
                return;
            }
            bindProfile(profile, progressLevel, tvLevel, chipStrengths, tvNoStrengths, llWeaknesses, tvNoWeaknesses, btnReview);
        });

        viewModel.load();
    }

    private void bindProfile(LearningProfileResponse profile, CircularProgressIndicator progressLevel, TextView tvLevel,
                             ChipGroup chipStrengths, View tvNoStrengths, LinearLayout llWeaknesses,
                             View tvNoWeaknesses, View btnReview) {
        tvLevel.setText(profile.estimatedLevel);
        progressLevel.setProgressCompat(LearningInsightViewModel.levelPermille(profile.estimatedLevel), true);

        boolean hasStrengths = profile.strengths != null && !profile.strengths.isEmpty();
        boolean hasWeaknesses = profile.weaknesses != null && !profile.weaknesses.isEmpty();

        tvNoStrengths.setVisibility(hasStrengths ? View.GONE : View.VISIBLE);
        tvNoWeaknesses.setVisibility(hasWeaknesses ? View.GONE : View.VISIBLE);
        btnReview.setVisibility(hasWeaknesses ? View.VISIBLE : View.GONE);

        chipStrengths.removeAllViews();
        if (hasStrengths) {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (LearningTopicResponse topic : profile.strengths) {
                Chip chip = (Chip) inflater.inflate(R.layout.item_ai_strength_chip, chipStrengths, false);
                chip.setText(topic.topicName);
                chipStrengths.addView(chip);
            }
        }

        llWeaknesses.removeAllViews();
        if (hasWeaknesses) {
            for (LearningTopicResponse topic : profile.weaknesses) {
                AiInsightViews.addWeakRow(llWeaknesses, topic.topicName,
                        getString(R.string.learning_insight_accuracy_format, topic.accuracyPercent),
                        topic.accuracyPercent, null);
            }
        }
    }

    //Chỉ chuyển khi đang ở màn này: chạm nhanh hai lần sẽ không chuyển lần hai (action không còn hợp lệ từ màn khác sẽ làm app văng)
    private void openReview() {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current == null || current.getId() != R.id.learningInsightFragment) {
            return;
        }
        navController.navigate(R.id.action_learningInsightFragment_to_aiPersonalizedReviewFragment);
    }
}