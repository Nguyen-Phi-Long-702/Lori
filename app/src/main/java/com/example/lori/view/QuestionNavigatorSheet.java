package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.viewmodel.ExamTakingViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Map;
import java.util.Set;

//Bảng lưới toàn bộ câu hỏi của đề: chạm một ô để nhảy tới câu đó
//Dùng chung ExamTakingViewModel với ExamTakingActivity nên chạm ô chỉ cần đổi vị trí câu hiện tại
public class QuestionNavigatorSheet extends BottomSheetDialogFragment {

    private static final int GRID_SPAN_COUNT = 6;

    private ExamTakingViewModel viewModel;
    private ExamQuestionNavAdapter adapter;
    private TextView tvSummary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_question_navigator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ExamTakingViewModel.class);

        tvSummary = view.findViewById(R.id.tvNavigatorSummary);
        RecyclerView rvGrid = view.findViewById(R.id.rvNavigatorGrid);

        adapter = new ExamQuestionNavAdapter(true, position -> {
            viewModel.setCurrentPosition(position);
            dismiss();
        });
        adapter.setQuestions(viewModel.getQuestions().getValue());
        rvGrid.setLayoutManager(new GridLayoutManager(requireContext(), GRID_SPAN_COUNT));
        rvGrid.setAdapter(adapter);

        //Mở bảng là cuộn sẵn tới câu đang xem
        Integer current = viewModel.getCurrentPosition().getValue();
        if (current != null) {
            rvGrid.scrollToPosition(current);
        }

        viewModel.getAnswers().observe(getViewLifecycleOwner(), answers -> render());
        viewModel.getFlagged().observe(getViewLifecycleOwner(), flagged -> render());
        viewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> render());
    }

    //Mở bảng ở trạng thái bung hết cỡ để thấy nhiều ô cùng lúc
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    //Tô lại các ô và cập nhật dòng tổng kết "Đã trả lời x/y · Đánh dấu z"
    private void render() {
        Map<String, String> answers = viewModel.getAnswers().getValue();
        Set<String> flagged = viewModel.getFlagged().getValue();
        Integer current = viewModel.getCurrentPosition().getValue();
        adapter.setState(answers, flagged, current != null ? current : 0);
        tvSummary.setText(getString(R.string.exam_navigator_summary_format,
                answers != null ? answers.size() : 0,
                viewModel.getQuestionCount(),
                flagged != null ? flagged.size() : 0));
    }
}