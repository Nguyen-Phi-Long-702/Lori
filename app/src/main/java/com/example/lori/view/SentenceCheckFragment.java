package com.example.lori.view;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiGrammarResponse;
import com.example.lori.viewmodel.SentenceCheckViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Màn Phân tích câu của AI Tutor (Premium): nhập câu → hiện câu gốc, câu đúng, lỗi kèm giải thích, bài ngữ pháp liên quan
public class SentenceCheckFragment extends Fragment {

    //AI Service chỉ giữ tối đa 1.000 ký tự sau khi làm sạch, nên giới hạn ngay trên ô nhập
    private static final int MAX_CHARS = 1000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sentence_check, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SentenceCheckViewModel viewModel = new ViewModelProvider(this).get(SentenceCheckViewModel.class);

        EditText etInput = view.findViewById(R.id.etSentenceInput);
        TextView tvCharCount = view.findViewById(R.id.tvSentenceCharCount);
        View btnCheck = view.findViewById(R.id.btnSentenceCheck);
        LinearProgressIndicator progress = view.findViewById(R.id.progressSentenceCheck);
        TextView tvOffline = view.findViewById(R.id.tvSentenceOffline);
        TextView tvError = view.findViewById(R.id.tvSentenceError);
        View layoutResult = view.findViewById(R.id.layoutSentenceResult);
        View cardCorrect = view.findViewById(R.id.cardSentenceCorrect);
        View cardOriginal = view.findViewById(R.id.cardSentenceOriginal);
        TextView tvOriginal = view.findViewById(R.id.tvSentenceOriginal);
        TextView tvCorrected = view.findViewById(R.id.tvSentenceCorrected);
        TextView tvExplanationTitle = view.findViewById(R.id.tvSentenceExplanationTitle);
        LinearLayout llErrors = view.findViewById(R.id.llSentenceErrors);
        View cardRelated = view.findViewById(R.id.cardSentenceRelated);
        TextView tvRelatedTitle = view.findViewById(R.id.tvSentenceRelatedTitle);

        view.findViewById(R.id.btnBackSentenceCheck)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        etInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARS)});
        tvCharCount.setText(getString(R.string.translate_char_count_format, 0, MAX_CHARS));
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                tvCharCount.setText(getString(R.string.translate_char_count_format, s.length(), MAX_CHARS));
            }
        });

        btnCheck.setOnClickListener(v -> {
            //Ẩn bàn phím để thấy ngay kết quả bên dưới
            hideKeyboard(v);
            viewModel.check(etInput.getText().toString());
        });

        //Bài ngữ pháp liên quan: mở thẳng màn nội dung bài (cùng cách các màn khác mở GrammarDetailFragment)
        cardRelated.setOnClickListener(v -> {
            AiGrammarResponse current = viewModel.getResult().getValue();
            if (current == null || current.lessonId == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putInt("lessonId", current.lessonId);
            Navigation.findNavController(v)
                    .navigate(R.id.action_sentenceCheckFragment_to_grammarDetailFragment, args);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));

        //Kiểm tra cần Internet: offline (hoặc đang kiểm tra) thì khóa nút; offline thì hiện thêm dòng giải thích
        viewModel.getCheckEnabled().observe(getViewLifecycleOwner(), enabled ->
                btnCheck.setEnabled(Boolean.TRUE.equals(enabled)));
        viewModel.getOnline().observe(getViewLifecycleOwner(), online ->
                tvOffline.setVisibility(Boolean.TRUE.equals(online) ? View.GONE : View.VISIBLE));

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), messageRes -> {
            if (messageRes == null) {
                tvError.setVisibility(View.GONE);
            } else {
                tvError.setText(messageRes);
                tvError.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getOriginalSentence().observe(getViewLifecycleOwner(), text -> tvOriginal.setText(text));

        viewModel.getResult().observe(getViewLifecycleOwner(), result -> {
            layoutResult.setVisibility(result == null ? View.GONE : View.VISIBLE);
            if (result == null) {
                return;
            }
            //Câu đúng: hiện thông báo thay cho "Câu gốc" (câu gốc và câu đúng giống nhau)
            cardCorrect.setVisibility(result.correct ? View.VISIBLE : View.GONE);
            cardOriginal.setVisibility(result.correct ? View.GONE : View.VISIBLE);
            tvCorrected.setText(result.correctedSentence);

            llErrors.removeAllViews();
            boolean hasErrors = !result.correct && result.errors != null && !result.errors.isEmpty();
            tvExplanationTitle.setVisibility(hasErrors ? View.VISIBLE : View.GONE);
            if (hasErrors) {
                LayoutInflater inflater = LayoutInflater.from(requireContext());
                for (AiGrammarResponse.GrammarError error : result.errors) {
                    View item = inflater.inflate(R.layout.item_sentence_error, llErrors, false);
                    ((TextView) item.findViewById(R.id.tvSentenceErrorType)).setText(error.type);
                    ((TextView) item.findViewById(R.id.tvSentenceErrorExplanation)).setText(error.explanation);
                    llErrors.addView(item);
                }
            }
        });

        viewModel.getRelatedLessonTitle().observe(getViewLifecycleOwner(), title -> {
            cardRelated.setVisibility(title == null ? View.GONE : View.VISIBLE);
            tvRelatedTitle.setText(title);
        });

        //User Free (hoặc Premium đã hết hạn): Backend trả 403 PREMIUM_REQUIRED nên mở dialog nâng cấp
        viewModel.getPremiumRequired().observe(getViewLifecycleOwner(), required -> {
            if (Boolean.TRUE.equals(required)) {
                PremiumPromptDialog.newInstance(getString(R.string.ai_tutor_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
                viewModel.consumePremiumRequired();
            }
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}