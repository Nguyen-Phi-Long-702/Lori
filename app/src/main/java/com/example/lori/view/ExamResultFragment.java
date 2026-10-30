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
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lori.R;
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.viewmodel.ExamResultViewModel;
import com.example.lori.viewmodel.QuizExamAiInsightViewModel;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Locale;

//Màn Kết quả bài thi: điểm tổng (vòng tròn), thống kê, điểm từng kỹ năng (bar chart 2 thanh)
//Chỉ nhận id kết quả qua arguments; dữ liệu do ExamResultViewModel (gắn với MainActivity) tải và giữ
public class ExamResultFragment extends Fragment {

    //Khóa Bundle chứa id kết quả (cùng tên với argument trong nav_graph)
    public static final String ARG_RESULT_ID = "resultId";

    private ExamResultViewModel viewModel;

    private View scrollContent;
    private View layoutError;
    private TextView tvError;
    private TextView tvHeadline;
    private CircularProgressIndicator progressScore;
    private TextView tvScoreValue;
    private TextView tvScoreCaption;
    private TextView tvStatCorrect;
    private TextView tvStatWrong;
    private TextView tvStatTime;
    private TextView tvListeningScore;
    private LinearProgressIndicator progressListening;
    private TextView tvListeningCorrect;
    private TextView tvReadingScore;
    private LinearProgressIndicator progressReading;
    private TextView tvReadingCorrect;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exam_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Gắn với MainActivity để màn Xem lại dùng chung dữ liệu
        viewModel = new ViewModelProvider(requireActivity()).get(ExamResultViewModel.class);

        scrollContent = view.findViewById(R.id.scrollExamResult);
        layoutError = view.findViewById(R.id.layoutExamResultError);
        tvError = view.findViewById(R.id.tvExamResultError);
        tvHeadline = view.findViewById(R.id.tvExamResultHeadline);
        progressScore = view.findViewById(R.id.progressExamScore);
        tvScoreValue = view.findViewById(R.id.tvExamScoreValue);
        tvScoreCaption = view.findViewById(R.id.tvExamScoreCaption);
        tvStatCorrect = view.findViewById(R.id.tvExamStatCorrect);
        tvStatWrong = view.findViewById(R.id.tvExamStatWrong);
        tvStatTime = view.findViewById(R.id.tvExamStatTime);
        tvListeningScore = view.findViewById(R.id.tvSkillListeningScore);
        progressListening = view.findViewById(R.id.progressSkillListening);
        tvListeningCorrect = view.findViewById(R.id.tvSkillListeningCorrect);
        tvReadingScore = view.findViewById(R.id.tvSkillReadingScore);
        progressReading = view.findViewById(R.id.progressSkillReading);
        tvReadingCorrect = view.findViewById(R.id.tvSkillReadingCorrect);
        LinearProgressIndicator progress = view.findViewById(R.id.progressExamResult);

        view.findViewById(R.id.btnBackExamResult).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        //Về danh sách đề thi: quay lại đúng màn danh sách trong back stack (không tạo màn danh sách mới)
        view.findViewById(R.id.btnExamResultBack).setOnClickListener(v ->
                Navigation.findNavController(v).popBackStack(R.id.examListFragment, false));
        view.findViewById(R.id.btnExamResultRetry).setOnClickListener(v -> viewModel.retry());
        view.findViewById(R.id.btnExamResultReview).setOnClickListener(v -> openReview());
        view.findViewById(R.id.cardExamAiInsight).setOnClickListener(v -> openAiInsight());

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
        viewModel.getResult().observe(getViewLifecycleOwner(), this::bindResult);

        //Id cùng với lần trước (xoay màn hình, quay lại từ Xem lại) thì ViewModel không gọi mạng lại
        viewModel.load(requireArguments().getString(ARG_RESULT_ID));
    }

    //Sang màn Xem lại (dữ liệu đã có trong ViewModel, chỉ cần truyền lại id)
    //Chỉ chuyển khi đang ở màn Kết quả: chạm nhanh hai lần sẽ không chuyển lần hai (action không còn hợp lệ từ màn khác sẽ làm app văng)
    private void openReview() {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current == null || current.getId() != R.id.examResultFragment) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(ARG_RESULT_ID, requireArguments().getString(ARG_RESULT_ID));
        navController.navigate(R.id.action_examResultFragment_to_examReviewFragment, args);
    }

    //Sang màn Phân tích AI của lần thi này; user Free bị Backend trả 403 nên màn đó tự mở dialog nâng cấp
    private void openAiInsight() {
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current == null || current.getId() != R.id.examResultFragment) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(QuizExamAiInsightFragment.ARG_SOURCE, QuizExamAiInsightViewModel.SOURCE_EXAM);
        args.putString(QuizExamAiInsightFragment.ARG_RESULT_ID, requireArguments().getString(ARG_RESULT_ID));
        navController.navigate(R.id.action_examResultFragment_to_quizExamAiInsightFragment, args);
    }

    //Gán dữ liệu lên giao diện; result = null (đang tải/lỗi) thì ẩn phần nội dung
    private void bindResult(@Nullable ExamResultResponse result) {
        scrollContent.setVisibility(result == null ? View.GONE : View.VISIBLE);
        if (result == null) {
            return;
        }
        boolean ielts = ExamResultViewModel.isIelts(result);

        tvHeadline.setText(getString(R.string.exam_result_headline_format, result.title));
        tvScoreValue.setText(formatScore(result.totalScore, ielts));
        tvScoreCaption.setText(ielts ? R.string.exam_result_ring_caption_ielts : R.string.exam_result_ring_caption_toeic);
        progressScore.setProgressCompat(ExamResultViewModel.totalPermille(result), true);

        tvStatCorrect.setText(getString(R.string.exam_result_stat_correct_format, result.correctCount));
        //Sai gồm cả câu bỏ trống (Backend tính câu bỏ trống là sai)
        tvStatWrong.setText(getString(R.string.exam_result_stat_wrong_format,
                result.totalQuestions - result.correctCount));
        tvStatTime.setText(getString(R.string.exam_result_stat_time_format, ExamResultViewModel.minutesSpent(result)));

        bindSkill(result, ielts, result.listeningScore, result.listeningCorrect, result.listeningTotal,
                tvListeningScore, progressListening, tvListeningCorrect);
        bindSkill(result, ielts, result.readingScore, result.readingCorrect, result.readingTotal,
                tvReadingScore, progressReading, tvReadingCorrect);
    }

    private void bindSkill(ExamResultResponse result, boolean ielts, double score, int correct, int total,
                           TextView tvScore, LinearProgressIndicator bar, TextView tvCorrect) {
        tvScore.setText(ielts
                ? getString(R.string.exam_result_score_ielts_format, formatScore(score, true))
                : getString(R.string.exam_result_skill_score_toeic_format, (int) Math.round(score)));
        bar.setProgressCompat(ExamResultViewModel.skillPermille(result, score), true);
        tvCorrect.setText(getString(R.string.exam_result_skill_correct_format, correct, total));
    }

    //TOEIC: số nguyên (760). IELTS: một chữ số thập phân, luôn dùng dấu chấm (7.5)
    private static String formatScore(double score, boolean ielts) {
        return ielts ? String.format(Locale.US, "%.1f", score) : String.valueOf(Math.round(score));
    }
}