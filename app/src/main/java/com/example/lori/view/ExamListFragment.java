package com.example.lori.view;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.ExamTakingActivity;
import com.example.lori.R;
import com.example.lori.viewmodel.ExamListViewModel;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

//Fragment danh sách đề thi TOEIC/IELTS (Premium) kèm bộ lọc và tab Lịch sử thi
//User Free: Backend trả 403 PREMIUM_REQUIRED nên hiện khung khóa với nút mở PremiumPromptDialog
public class ExamListFragment extends Fragment {

    private ExamListViewModel viewModel;

    private ChipGroup chipGroupExam;
    private RecyclerView rvExams;
    private RecyclerView rvResults;
    private TextView tvEmpty;
    private View layoutError;
    private TextView tvError;
    private View cardLocked;

    //Mở màn làm bài và nhận kết quả trả về: nộp bài thành công thì có resultId → mở màn Kết quả
    //Thoát giữa chừng (không nộp) thì không có gì xảy ra. Khai báo ở đây (trước onCreate) để vẫn nhận được kết quả sau khi app bị hệ thống thu hồi
    private final ActivityResultLauncher<Intent> examTakingLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            activityResult -> {
                if (activityResult.getResultCode() == Activity.RESULT_OK) {
                    openResult(ExamTakingActivity.getResultId(activityResult.getData()));
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exam_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ExamListViewModel.class);

        chipGroupExam = view.findViewById(R.id.chipGroupExam);
        rvExams = view.findViewById(R.id.rvExams);
        rvResults = view.findViewById(R.id.rvResults);
        tvEmpty = view.findViewById(R.id.tvExamEmpty);
        layoutError = view.findViewById(R.id.layoutExamError);
        tvError = view.findViewById(R.id.tvExamError);
        cardLocked = view.findViewById(R.id.cardExamLocked);
        LinearProgressIndicator progress = view.findViewById(R.id.progressExam);
        View btnRetry = view.findViewById(R.id.btnExamRetry);
        View btnUnlock = view.findViewById(R.id.btnExamUnlock);

        //Bấm "Bắt đầu làm bài": mở màn làm bài của đề đó (nhận resultId khi nộp xong)
        ExamAdapter examAdapter = new ExamAdapter(exam ->
                examTakingLauncher.launch(ExamTakingActivity.newIntent(requireContext(), exam.id)));
        //Bấm một dòng Lịch sử: mở màn Kết quả của lần thi đó
        ExamResultAdapter resultAdapter = new ExamResultAdapter(item -> openResult(item.id));
        rvExams.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvExams.setAdapter(examAdapter);
        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvResults.setAdapter(resultAdapter);

        //Bấm chip: đổi bộ lọc / mở tab Lịch sử
        chipGroupExam.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            viewModel.selectTab(tabForChip(checkedIds.get(0)));
        });

        btnRetry.setOnClickListener(v -> viewModel.refresh());

        //User Free bấm "Mở khóa để làm bài": mở PremiumPromptDialog (giống QuizFragment)
        btnUnlock.setOnClickListener(v ->
                PremiumPromptDialog.newInstance(getString(R.string.exam_premium_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt"));

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));
        //Đồng bộ chip đang chọn với trạng thái trong ViewModel (vd quay lại màn sau khi View bị tạo lại)
        viewModel.getSelectedTab().observe(getViewLifecycleOwner(), tab -> {
            chipGroupExam.check(chipIdForTab(tab));
            render();
        });
        viewModel.getLocked().observe(getViewLifecycleOwner(), locked -> render());
        viewModel.getVisibleExams().observe(getViewLifecycleOwner(), exams -> {
            examAdapter.setExams(exams);
            render();
        });
        viewModel.getResults().observe(getViewLifecycleOwner(), results -> {
            resultAdapter.setResults(results);
            render();
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> render());
    }

    //Tải lại mỗi khi màn hiện lên: lần đầu vào tab, và khi quay lại từ màn mua Premium (khung khóa sẽ tự đổi thành danh sách)
    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    //Quyết định khối nào hiện: khung khóa / danh sách đề / lịch sử / thông báo trống / lỗi kèm nút Thử lại
    private void render() {
        boolean locked = Boolean.TRUE.equals(viewModel.getLocked().getValue());
        cardLocked.setVisibility(locked ? View.VISIBLE : View.GONE);
        chipGroupExam.setVisibility(locked ? View.GONE : View.VISIBLE);
        if (locked) {
            rvExams.setVisibility(View.GONE);
            rvResults.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);
            return;
        }

        boolean history = ExamListViewModel.TAB_HISTORY.equals(viewModel.getSelectedTab().getValue());
        List<?> items;
        if (history) {
            items = viewModel.getResults().getValue();
        } else {
            items = viewModel.getVisibleExams().getValue();
        }
        Integer error = viewModel.getErrorMessage().getValue();

        boolean hasItems = items != null && !items.isEmpty();
        boolean isEmpty = items != null && items.isEmpty();
        boolean hasError = items == null && error != null;

        rvExams.setVisibility(hasItems && !history ? View.VISIBLE : View.GONE);
        rvResults.setVisibility(hasItems && history ? View.VISIBLE : View.GONE);

        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            tvEmpty.setText(history ? R.string.exam_empty_history : R.string.exam_empty_exams);
        }

        layoutError.setVisibility(hasError ? View.VISIBLE : View.GONE);
        if (hasError) {
            tvError.setText(error);
        }
    }

    //Mở màn Kết quả của một lần thi (chỉ truyền id, màn đó tự tải dữ liệu)
    //Chỉ chuyển khi đang đứng ở danh sách đề: chạm nhanh hai lần sẽ không chuyển lần hai (action không còn hợp lệ từ màn khác sẽ làm app văng)
    private void openResult(@Nullable String resultId) {
        if (resultId == null) {
            return;
        }
        NavController navController = NavHostFragment.findNavController(this);
        NavDestination current = navController.getCurrentDestination();
        if (current == null || current.getId() != R.id.examListFragment) {
            return;
        }
        Bundle args = new Bundle();
        args.putString(ExamResultFragment.ARG_RESULT_ID, resultId);
        navController.navigate(R.id.action_examListFragment_to_examResultFragment, args);
    }

    private static String tabForChip(int chipId) {
        if (chipId == R.id.chipExamToeic) {
            return ExamListViewModel.TAB_TOEIC;
        }
        if (chipId == R.id.chipExamIelts) {
            return ExamListViewModel.TAB_IELTS;
        }
        if (chipId == R.id.chipExamHistory) {
            return ExamListViewModel.TAB_HISTORY;
        }
        return ExamListViewModel.TAB_ALL;
    }

    private static int chipIdForTab(String tab) {
        if (ExamListViewModel.TAB_TOEIC.equals(tab)) {
            return R.id.chipExamToeic;
        }
        if (ExamListViewModel.TAB_IELTS.equals(tab)) {
            return R.id.chipExamIelts;
        }
        if (ExamListViewModel.TAB_HISTORY.equals(tab)) {
            return R.id.chipExamHistory;
        }
        return R.id.chipExamAll;
    }
}