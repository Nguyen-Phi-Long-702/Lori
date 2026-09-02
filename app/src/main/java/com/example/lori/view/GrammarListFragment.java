package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.viewmodel.GrammarListViewModel;
import com.google.android.material.chip.ChipGroup;

//Fragment hiển thị danh sách bài học ngữ pháp
public class GrammarListFragment extends Fragment {

    private GrammarListViewModel viewModel;
    private GrammarAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_grammar_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(GrammarListViewModel.class);

        ChipGroup chipGroupSubtabs = view.findViewById(R.id.chipGroupSubtabs);
        ChipGroup chipGroupLevel = view.findViewById(R.id.chipGroupLevel);
        RecyclerView rvGrammarLessons = view.findViewById(R.id.rvGrammarLessons);

        rvGrammarLessons.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new GrammarAdapter(this::openLessonDetail);
        rvGrammarLessons.setAdapter(adapter);

        //Quay lại màn hình từ vựng khi người dùng chọn subtab tương ứng
        chipGroupSubtabs.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipSubtabVocab)) {
                Navigation.findNavController(view).navigateUp();
            }
        });

        //Lọc danh sách bài học theo cấp độ khi người dùng chọn chip
        chipGroupLevel.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipLevelBeginner) {
                viewModel.setLevel("beginner");
            } else if (checkedId == R.id.chipLevelIntermediate) {
                viewModel.setLevel("intermediate");
            } else if (checkedId == R.id.chipLevelAdvanced) {
                viewModel.setLevel("advanced");
            } else {
                viewModel.setLevel("all");
            }
        });

        viewModel.getFilteredLessons().observe(getViewLifecycleOwner(), adapter::setLessons);
        viewModel.getCompletedLessonIds().observe(getViewLifecycleOwner(), adapter::setCompletedLessonIds);
    }

    //Mở màn hình nội dung chi tiết của bài học được chọn
    private void openLessonDetail(GrammarLesson lesson) {
        Bundle args = new Bundle();
        args.putInt("lessonId", lesson.id);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_grammarListFragment_to_grammarDetailFragment, args);
    }
}