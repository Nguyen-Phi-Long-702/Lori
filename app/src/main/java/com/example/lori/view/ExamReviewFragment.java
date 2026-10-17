package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.viewmodel.ExamResultViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Màn Xem lại bài làm: danh sách từng câu với đáp án bạn chọn, đáp án đúng và giải thích
//Dùng chung ExamResultViewModel (gắn với MainActivity) với màn Kết quả nên thường không phải gọi mạng lại
public class ExamReviewFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exam_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ExamResultViewModel viewModel = new ViewModelProvider(requireActivity()).get(ExamResultViewModel.class);

        RecyclerView rvReview = view.findViewById(R.id.rvExamReview);
        View layoutError = view.findViewById(R.id.layoutExamReviewError);
        TextView tvError = view.findViewById(R.id.tvExamReviewError);
        LinearProgressIndicator progress = view.findViewById(R.id.progressExamReview);

        ExamReviewAdapter adapter = new ExamReviewAdapter();
        rvReview.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReview.setAdapter(adapter);

        view.findViewById(R.id.btnBackExamReview).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnExamReviewRetry).setOnClickListener(v -> viewModel.retry());

        viewModel.getItems().observe(getViewLifecycleOwner(), adapter::setItems);
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

        //Từ màn Kết quả sang: cùng id nên ViewModel không gọi mạng lại. Chỉ tải thật khi app bị thu hồi rồi mở lại đúng màn này
        viewModel.load(requireArguments().getString(ExamResultFragment.ARG_RESULT_ID));
    }
}