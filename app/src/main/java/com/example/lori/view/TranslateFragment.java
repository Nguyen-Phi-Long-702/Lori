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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.lifecycle.ViewModelProvider;

import com.example.lori.viewmodel.TranslateViewModel;
import com.example.lori.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Fragment dịch thuật
public class TranslateFragment extends Fragment {
    private static final int MAX_CHARS = 500;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_translate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TranslateViewModel viewModel = new ViewModelProvider(this).get(TranslateViewModel.class);

        EditText etInput = view.findViewById(R.id.etTranslateInput);
        TextView tvCharCount = view.findViewById(R.id.tvTranslateCharCount);
        Chip chipSource = view.findViewById(R.id.chipTranslateSource);
        Chip chipTarget = view.findViewById(R.id.chipTranslateTarget);
        View btnSwap = view.findViewById(R.id.btnTranslateSwap);
        View btnSubmit = view.findViewById(R.id.btnTranslateSubmit);
        LinearProgressIndicator progress = view.findViewById(R.id.progressTranslate);
        TextView tvOffline = view.findViewById(R.id.tvTranslateOffline);
        TextView tvError = view.findViewById(R.id.tvTranslateError);
        View cardResult = view.findViewById(R.id.cardTranslateResult);
        TextView tvResult = view.findViewById(R.id.tvTranslateResult);
        Chip chipSubtabDictionary = view.findViewById(R.id.chipSubtabDictionary);

        //Giới hạn tối đa 500 ký tự ngay trên ô nhập, không cho gõ vượt quá
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

        btnSwap.setOnClickListener(v -> viewModel.swapDirection());

        btnSubmit.setOnClickListener(v -> {
            //Ẩn bàn phím để thấy ngay kết quả bên dưới
            hideKeyboard(v);
            viewModel.translate(etInput.getText().toString());
        });

        //Chiều dịch: đổi nhãn 2 chip ngôn ngữ và gợi ý của ô nhập
        viewModel.getEnglishToVietnamese().observe(getViewLifecycleOwner(), englishToVietnamese -> {
            boolean enToVi = !Boolean.FALSE.equals(englishToVietnamese);
            chipSource.setText(enToVi ? R.string.translate_lang_en : R.string.translate_lang_vi);
            chipTarget.setText(enToVi ? R.string.translate_lang_vi : R.string.translate_lang_en);
            etInput.setHint(enToVi ? R.string.translate_input_hint : R.string.translate_input_hint_vi);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));

        //Dịch cần Internet: offline (hoặc đang dịch) thì khóa nút; offline thì hiện thêm dòng giải thích
        viewModel.getTranslateEnabled().observe(getViewLifecycleOwner(), enabled ->
                btnSubmit.setEnabled(Boolean.TRUE.equals(enabled)));
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

        viewModel.getTranslation().observe(getViewLifecycleOwner(), text -> {
            cardResult.setVisibility(text == null ? View.GONE : View.VISIBLE);
            tvResult.setText(text);
        });

        //Quay lại màn từ điển khi bấm subtab "Từ điển"
        chipSubtabDictionary.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Chip chipSubtabTranslate = requireView().findViewById(R.id.chipSubtabTranslate);
        chipSubtabTranslate.setChecked(true);
    }
}