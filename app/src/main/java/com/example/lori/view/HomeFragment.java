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

        viewModel.getTopicCount().observe(getViewLifecycleOwner(),
                count -> tvTopicCount.setText(String.valueOf(count)));
        viewModel.getWordCount().observe(getViewLifecycleOwner(),
                count -> tvWordCount.setText(String.valueOf(count)));
        viewModel.getLessonCount().observe(getViewLifecycleOwner(),
                count -> tvLessonCount.setText(String.valueOf(count)));
        progressStudy.setProgress(0);

        btnStartLearning.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.topicListFragment));
    }
}