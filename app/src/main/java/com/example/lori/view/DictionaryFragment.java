package com.example.lori.view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.DictionaryDefinition;
import com.example.lori.data.local.entity.DictionaryWord;
import com.example.lori.viewmodel.DictionaryViewModel;
import com.example.lori.viewmodel.DictionaryWordDetail;
import com.google.android.material.chip.Chip;

import java.util.List;

//Fragment tra cứu từ điển offline
public class DictionaryFragment extends Fragment {

    private static final long SEARCH_DEBOUNCE_MS = 300;

    private DictionaryViewModel viewModel;
    private DictionaryAdapter adapter;
    private EditText etSearch;
    private ImageButton btnClear;
    private TextView tvEmpty;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dictionary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(DictionaryViewModel.class);

        etSearch = view.findViewById(R.id.etDictionarySearch);
        btnClear = view.findViewById(R.id.btnDictionaryClear);
        tvEmpty = view.findViewById(R.id.tvDictionaryEmpty);
        RecyclerView rvResults = view.findViewById(R.id.rvDictionaryResults);

        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvResults.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new DictionaryAdapter(this::onWordSelected);
        rvResults.setAdapter(adapter);

        btnClear.setOnClickListener(v -> etSearch.setText(""));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch(s.toString());
            }
        });

        viewModel.getSearchResults().observe(getViewLifecycleOwner(), this::onSearchResultsChanged);
        viewModel.getWordDetail().observe(getViewLifecycleOwner(), this::onWordDetailLoaded);
    }

    //Trì hoãn tìm kiếm 300ms sau lần gõ cuối cùng, tránh query liên tục theo từng ký tự
    private void scheduleSearch(String query) {
        btnClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        if (pendingSearch != null) {
            debounceHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> viewModel.search(query);
        debounceHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    //Cập nhật danh sách kết quả và thông báo "không tìm thấy" khi cần
    private void onSearchResultsChanged(List<DictionaryWord> results) {
        adapter.setResults(results);
        String currentQuery = etSearch.getText().toString().trim();
        boolean showEmpty = !currentQuery.isEmpty() && results != null && results.isEmpty();
        tvEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            tvEmpty.setText(getString(R.string.dictionary_empty_result_format, currentQuery));
        }
    }

    //Yêu cầu viewmodel tải chi tiết đầy đủ của từ được chọn
    private void onWordSelected(DictionaryWord word) {
        viewModel.loadWordDetail(word);
    }

    //Khi chi tiết từ đã sẵn sàng, ghép thành 1 chuỗi và mở màn Chi tiết từ dùng chung
    private void onWordDetailLoaded(DictionaryWordDetail detail) {
        if (detail == null) return;

        Bundle args = new Bundle();
        args.putString("word", detail.word);
        args.putString("pronunciation", detail.ipa);
        args.putString("meaning", buildMeaningText(detail));
        args.putString("topicName", getString(R.string.dictionary_detail_source_label));
        Navigation.findNavController(requireView()).navigate(R.id.action_dictionaryFragment_to_wordDetailFragment, args);
        //Đánh dấu đã xử lý xong để tránh mở lại màn chi tiết khi LiveData phát lại giá trị cũ (ví dụ sau khi xoay màn hình)
        viewModel.consumeWordDetail();
    }

    //Ghép toàn bộ định nghĩa, ví dụ, đồng nghĩa, trái nghĩa thành 1 chuỗi nhiều dòng để hiển thị trong ô nghĩa của WordDetailFragment (màn này chỉ có 1 TextView nghĩa)
    private String buildMeaningText(DictionaryWordDetail detail) {
        List<DictionaryDefinition> definitions = detail.definitions;
        if (definitions == null || definitions.isEmpty()) {
            return getString(R.string.dictionary_no_definition);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < definitions.size(); i++) {
            DictionaryDefinition def = definitions.get(i);
            if (i > 0) sb.append("\n\n");
            sb.append(getString(R.string.dictionary_definition_line_format, i + 1, def.pos, def.definition));
            if (def.example != null && !def.example.trim().isEmpty()) {
                sb.append("\n").append(getString(R.string.dictionary_example_line_format, def.example));
            }
        }

        if (detail.synonyms != null && !detail.synonyms.isEmpty()) {
            sb.append("\n\n").append(getString(R.string.dictionary_synonyms_label, TextUtils.join(", ", detail.synonyms)));
        }
        if (detail.antonyms != null && !detail.antonyms.isEmpty()) {
            sb.append("\n\n").append(getString(R.string.dictionary_antonyms_label, TextUtils.join(", ", detail.antonyms)));
        }
        return sb.toString();
    }

    @Override
    public void onResume() {
        super.onResume();
        Chip chipSubtabDictionary = requireView().findViewById(R.id.chipSubtabDictionary);
        chipSubtabDictionary.setChecked(true);
    }
}