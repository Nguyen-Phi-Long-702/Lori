package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiConversationResponse;
import com.example.lori.viewmodel.AiConversationViewModel;

import java.util.List;

//Màn nhận xét cuối buổi hội thoại (Premium): tổng quan và 4 nhóm lỗi ngữ pháp, từ vựng, diễn đạt tự nhiên hơn, cụm từ hữu ích
public class AiConversationFeedbackFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_conversation_feedback, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = NavHostFragment.findNavController(this);
        AiConversationViewModel viewModel = new ViewModelProvider(
                navController.getViewModelStoreOwner(R.id.nav_ai_conversation))
                .get(AiConversationViewModel.class);

        TextView tvSummary = view.findViewById(R.id.tvConversationFeedbackSummary);
        LinearLayout layoutSections = view.findViewById(R.id.layoutConversationFeedbackSections);

        view.findViewById(R.id.btnBackAiConversationFeedback).setOnClickListener(v -> navController.navigateUp());
        //Xong: về thẳng AI Tutor Home; rời graph hội thoại nên buổi này bị hủy
        view.findViewById(R.id.btnConversationDone)
                .setOnClickListener(v -> navController.popBackStack(R.id.aiTutorHomeFragment, false));

        viewModel.getFeedback().observe(getViewLifecycleOwner(), feedback -> {
            if (feedback == null) {
                return;
            }
            tvSummary.setText(feedback.summary);
            layoutSections.removeAllViews();
            addSection(layoutSections, R.drawable.ic_feature_feedback_grammar,
                    R.string.ai_conversation_feedback_grammar, feedback.grammar);
            addSection(layoutSections, R.drawable.ic_feature_feedback_vocabulary,
                    R.string.ai_conversation_feedback_vocabulary, feedback.vocabulary);
            addSection(layoutSections, R.drawable.ic_feature_feedback_natural,
                    R.string.ai_conversation_feedback_naturalness, feedback.naturalness);
            addSection(layoutSections, R.drawable.ic_feature_feedback_phrases,
                    R.string.ai_conversation_feedback_phrases, feedback.usefulPhrases);
        });
    }

    //Thêm một nhóm nhận xét; nhóm rỗng thì bỏ qua
    private void addSection(LinearLayout parent, @DrawableRes int iconRes, @StringRes int titleRes,
                            @Nullable List<AiConversationResponse.FeedbackItem> feedbackItems) {
        if (feedbackItems == null || feedbackItems.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View section = inflater.inflate(R.layout.item_conversation_feedback_section, parent, false);
        ((ImageView) section.findViewById(R.id.ivFeedbackSectionIcon)).setImageResource(iconRes);
        ((TextView) section.findViewById(R.id.tvFeedbackSectionTitle)).setText(titleRes);
        LinearLayout container = section.findViewById(R.id.layoutFeedbackItems);

        for (AiConversationResponse.FeedbackItem item : feedbackItems) {
            View row = inflater.inflate(R.layout.item_conversation_feedback, container, false);
            //Nhóm Cụm từ hữu ích không có câu gốc (original = null)
            boolean hasOriginal = item.original != null && !item.original.trim().isEmpty();
            TextView tvOriginal = row.findViewById(R.id.tvFeedbackOriginal);
            tvOriginal.setVisibility(hasOriginal ? View.VISIBLE : View.GONE);
            tvOriginal.setText(item.original);
            ((TextView) row.findViewById(R.id.tvFeedbackBetter)).setText(hasOriginal
                    ? getString(R.string.ai_conversation_feedback_better_format, item.better)
                    : item.better);
            ((TextView) row.findViewById(R.id.tvFeedbackExplanation)).setText(item.explanation);
            container.addView(row);
        }
        parent.addView(section);
    }
}