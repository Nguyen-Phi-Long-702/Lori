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

import com.example.lori.R;
import com.example.lori.viewmodel.HomeViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Fragment hiển thị tổng quan tiến độ và dữ liệu học tập
public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        TextView tvTopicCount = view.findViewById(R.id.tvTopicCount);
        TextView tvWordCount = view.findViewById(R.id.tvWordCount);
        TextView tvLessonCount = view.findViewById(R.id.tvLessonCount);
        LinearProgressIndicator progressStudy = view.findViewById(R.id.progressStudy);
        MaterialButton btnStartLearning = view.findViewById(R.id.btnStartLearning);

        //Quan sát số lượng chủ đề và cập nhật giao diện khi dữ liệu thay đổi
        viewModel.getTopicCount().observe(getViewLifecycleOwner(),
                count -> tvTopicCount.setText(String.valueOf(count)));
        //Quan sát số lượng từ vựng và cập nhật giao diện khi dữ liệu thay đổi
        viewModel.getWordCount().observe(getViewLifecycleOwner(),
                count -> tvWordCount.setText(String.valueOf(count)));
        //Quan sát số lượng bài học ngữ pháp và cập nhật giao diện khi dữ liệu thay đổi
        viewModel.getLessonCount().observe(getViewLifecycleOwner(),
                count -> tvLessonCount.setText(String.valueOf(count)));
        progressStudy.setProgress(0);

        //Chuyển đến danh sách chủ đề để bắt đầu học
        btnStartLearning.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.topicListFragment));
    }
}