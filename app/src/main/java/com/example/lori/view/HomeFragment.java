package com.example.lori.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.utils.ExpFormatter;
import com.example.lori.viewmodel.HomeViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Fragment hiển thị tổng quan tiến độ, EXP, cấp độ, streak và dữ liệu học tập
public class HomeFragment extends Fragment {

    //Hiệu ứng "+XX EXP": bật lên → giữ → bay lên và mờ dần
    private static final long EXP_POP_DURATION_MS = 350;
    private static final long EXP_HOLD_DURATION_MS = 900;
    private static final long EXP_LEAVE_DURATION_MS = 500;
    private static final float EXP_FLOAT_DISTANCE_DP = 20;

    private HomeViewModel viewModel;
    private TextView tvExpGain;
    @Nullable
    private AnimatorSet expGainAnimator;

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
        TextView tvTotalExp = view.findViewById(R.id.tvTotalExp);
        TextView tvLevelBadge = view.findViewById(R.id.tvLevelBadge);
        TextView tvStreak = view.findViewById(R.id.tvStreak);
        tvExpGain = view.findViewById(R.id.tvExpGain);
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
        //Quan sát tổng EXP, cấp độ và streak lấy từ Backend
        viewModel.getStats().observe(getViewLifecycleOwner(), stats -> {
            tvTotalExp.setText(getString(R.string.home_exp_format, ExpFormatter.format(stats.totalExp)));
            tvLevelBadge.setText(getString(R.string.home_level_format, stats.level));
            tvStreak.setText(getString(R.string.home_streak_format, stats.currentStreak));
        });
        //EXP vừa tăng: chạy animation một lần rồi báo ViewModel đã xử lý
        viewModel.getExpGain().observe(getViewLifecycleOwner(), gain -> {
            if (gain != null) {
                playExpGain(gain);
                viewModel.consumeExpGain();
            }
        });
        progressStudy.setProgress(0);

        //Chuyển đến danh sách chủ đề để bắt đầu học
        btnStartLearning.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.topicListFragment));
    }

    //Mỗi lần Home hiện lên (kể cả quay lại sau khi học) đều làm mới EXP và streak
    @Override
    public void onStart() {
        super.onStart();
        viewModel.refresh();
    }

    @Override
    public void onDestroyView() {
        if (expGainAnimator != null) {
            expGainAnimator.cancel();
            expGainAnimator = null;
        }
        super.onDestroyView();
    }

    //Animation "+XX EXP": phóng to có nảy → giữ một lát → bay lên và mờ dần
    private void playExpGain(long gain) {
        //Hủy lượt đang chạy trước (hàm kết thúc của nó sẽ ẩn nhãn), rồi mới đặt nhãn cho lượt mới
        if (expGainAnimator != null) {
            expGainAnimator.cancel();
        }
        tvExpGain.setText(getString(R.string.home_exp_gain_format, ExpFormatter.format(gain)));
        tvExpGain.setVisibility(View.VISIBLE);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(tvExpGain, View.ALPHA, 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tvExpGain, View.SCALE_X, 0.5f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvExpGain, View.SCALE_Y, 0.5f, 1f);
        scaleX.setInterpolator(new OvershootInterpolator());
        scaleY.setInterpolator(new OvershootInterpolator());
        AnimatorSet pop = new AnimatorSet();
        pop.playTogether(fadeIn, scaleX, scaleY);
        pop.setDuration(EXP_POP_DURATION_MS);

        float distance = EXP_FLOAT_DISTANCE_DP * getResources().getDisplayMetrics().density;
        ObjectAnimator rise = ObjectAnimator.ofFloat(tvExpGain, View.TRANSLATION_Y, 0f, -distance);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(tvExpGain, View.ALPHA, 1f, 0f);
        AnimatorSet leave = new AnimatorSet();
        leave.playTogether(rise, fadeOut);
        leave.setStartDelay(EXP_HOLD_DURATION_MS);
        leave.setDuration(EXP_LEAVE_DURATION_MS);

        AnimatorSet all = new AnimatorSet();
        all.playSequentially(pop, leave);
        all.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                //Chạy hết hoặc bị hủy đều trả nhãn về trạng thái ẩn ban đầu
                tvExpGain.setVisibility(View.GONE);
                tvExpGain.setTranslationY(0f);
            }
        });
        expGainAnimator = all;
        all.start();
    }
}