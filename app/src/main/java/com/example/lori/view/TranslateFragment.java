package com.example.lori.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.google.android.material.chip.Chip;

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

        EditText etInput = view.findViewById(R.id.etTranslateInput);
        TextView tvCharCount = view.findViewById(R.id.tvTranslateCharCount);
        View btnSwap = view.findViewById(R.id.btnTranslateSwap);
        View btnSubmit = view.findViewById(R.id.btnTranslateSubmit);
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

        btnSwap.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.translate_swap_not_available_toast, Toast.LENGTH_SHORT).show());

        btnSubmit.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.translate_coming_soon_toast, Toast.LENGTH_SHORT).show());

        //Quay lại màn từ điển khi bấm subtab "Từ điển"
        chipSubtabDictionary.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    @Override
    public void onResume() {
        super.onResume();
        Chip chipSubtabTranslate = requireView().findViewById(R.id.chipSubtabTranslate);
        chipSubtabTranslate.setChecked(true);
    }
}