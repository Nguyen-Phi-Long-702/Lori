package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lori.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class PremiumPromptDialog extends BottomSheetDialogFragment {

    private static final String ARG_FEATURE_LABEL = "feature_label";

    public static PremiumPromptDialog newInstance(@Nullable String featureLabel) {
        PremiumPromptDialog dialog = new PremiumPromptDialog();
        Bundle args = new Bundle();
        args.putString(ARG_FEATURE_LABEL, featureLabel);
        dialog.setArguments(args);
        return dialog;
    }

    public static PremiumPromptDialog newInstance() {
        return newInstance(null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_premium_prompt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String featureLabel = getArguments() != null ? getArguments().getString(ARG_FEATURE_LABEL) : null;
        if (featureLabel == null || featureLabel.trim().isEmpty()) {
            featureLabel = getString(R.string.premium_prompt_default_feature);
        }

        TextView tvTitle = view.findViewById(R.id.tvPremiumPromptTitle);
        TextView tvDesc = view.findViewById(R.id.tvPremiumPromptDesc);
        tvTitle.setText(R.string.premium_prompt_title);
        tvDesc.setText(getString(R.string.premium_prompt_desc_format, featureLabel));

        view.findViewById(R.id.btnPremiumLater).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnPremiumUpgrade).setOnClickListener(v -> {
            Toast.makeText(requireContext(), R.string.premium_coming_soon_toast, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }
}