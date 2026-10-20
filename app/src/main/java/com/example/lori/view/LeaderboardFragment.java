package com.example.lori.view;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.lori.R;
import com.example.lori.data.remote.dto.LeaderboardEntryResponse;
import com.example.lori.data.remote.dto.LeaderboardMeResponse;
import com.example.lori.data.remote.dto.UserResponse;
import com.example.lori.utils.ExpFormatter;
import com.example.lori.viewmodel.LeaderboardViewModel;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

//Fragment Bảng xếp hạng (Free): 3 bảng, bục Top 3, danh sách phân trang, kéo để làm mới, thẻ "Vị trí của tôi"
public class LeaderboardFragment extends Fragment {

    //Còn bấy nhiêu dòng nữa là tới cuối danh sách thì bắt đầu tải trang kế
    private static final int LOAD_MORE_THRESHOLD = 5;
    private static final long FADE_IN_DURATION_MS = 250;
    private static final long COUNT_UP_DURATION_MS = 700;

    private LeaderboardViewModel viewModel;

    private ChipGroup chipGroup;
    private ShimmerFrameLayout shimmer;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvLeaderboard;
    private TextView tvEmpty;
    private View layoutError;
    private TextView tvError;
    private View cardMe;
    private TextView tvMeRank;
    private AvatarView avatarMe;
    private TextView tvMeName;
    private TextView tvMeSubtitle;
    private TextView tvMeScore;

    private LinearLayoutManager layoutManager;
    private ConcatAdapter concatAdapter;
    private LeaderboardPodiumAdapter podiumAdapter;
    private LeaderboardAdapter rowsAdapter;
    private LeaderboardFooterAdapter footerAdapter;

    //true khi nội dung đã hiện: để chỉ chạy fade-in đúng lúc nội dung vừa xuất hiện
    private boolean contentShown;
    //Điểm đang hiển thị trên thẻ "Vị trí của tôi": hiệu ứng đếm chạy từ số này tới điểm mới
    private boolean scoreShown;
    private long shownScore;
    @Nullable
    private ValueAnimator scoreAnimator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(LeaderboardViewModel.class);
        contentShown = false;
        scoreShown = false;
        shownScore = 0;

        chipGroup = view.findViewById(R.id.chipGroupLeaderboard);
        shimmer = view.findViewById(R.id.shimmerLeaderboard);
        swipeRefresh = view.findViewById(R.id.swipeLeaderboard);
        rvLeaderboard = view.findViewById(R.id.rvLeaderboard);
        tvEmpty = view.findViewById(R.id.tvLeaderboardEmpty);
        layoutError = view.findViewById(R.id.layoutLeaderboardError);
        tvError = view.findViewById(R.id.tvLeaderboardError);
        cardMe = view.findViewById(R.id.cardLeaderboardMe);
        tvMeRank = view.findViewById(R.id.tvMeRank);
        avatarMe = view.findViewById(R.id.avatarMe);
        tvMeName = view.findViewById(R.id.tvMeName);
        tvMeSubtitle = view.findViewById(R.id.tvMeSubtitle);
        tvMeScore = view.findViewById(R.id.tvMeScore);

        view.findViewById(R.id.btnBackLeaderboard).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        view.findViewById(R.id.btnLeaderboardRetry).setOnClickListener(v -> viewModel.refresh());

        //Bục Top 3, các dòng từ hạng 4 và chân danh sách nối thành một danh sách cuộn chung
        podiumAdapter = new LeaderboardPodiumAdapter();
        rowsAdapter = new LeaderboardAdapter();
        footerAdapter = new LeaderboardFooterAdapter(() -> viewModel.retryLoadMore());
        concatAdapter = new ConcatAdapter(podiumAdapter, rowsAdapter, footerAdapter);
        layoutManager = new LinearLayoutManager(requireContext());
        rvLeaderboard.setLayoutManager(layoutManager);
        rvLeaderboard.setAdapter(concatAdapter);
        rvLeaderboard.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    loadMoreIfNeeded();
                }
            }
        });

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.lori_primary));
        swipeRefresh.setProgressBackgroundColorSchemeColor(
                ContextCompat.getColor(requireContext(), R.color.lori_surface));
        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        //Bấm chip: đổi bảng
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            viewModel.selectPeriod(periodForChip(checkedIds.get(0)));
        });

        //Đồng bộ chip đang chọn với trạng thái trong ViewModel (vd quay lại màn sau khi View bị tạo lại)
        viewModel.getSelectedPeriod().observe(getViewLifecycleOwner(), period ->
                chipGroup.check(chipIdForPeriod(period)));
        viewModel.getPodium().observe(getViewLifecycleOwner(), podium -> {
            podiumAdapter.setPodium(podium);
            render();
        });
        //Sau khi danh sách cập nhật xong, kiểm tra lại: nếu chưa đủ dài để cuộn thì tải tiếp trang kế
        viewModel.getRows().observe(getViewLifecycleOwner(), rows ->
                rowsAdapter.submitList(rows, () -> rvLeaderboard.post(this::loadMoreIfNeeded)));
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> render());
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> render());
        viewModel.getLoadingMore().observe(getViewLifecycleOwner(), loadingMore -> updateFooter());
        viewModel.getLoadMoreFailed().observe(getViewLifecycleOwner(), failed -> updateFooter());
        viewModel.getMyRank().observe(getViewLifecycleOwner(), myRank -> bindMe());
        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> bindMe());
        //Làm mới thất bại nhưng vẫn còn dữ liệu cũ: giữ dữ liệu, báo một lần
        viewModel.getRefreshError().observe(getViewLifecycleOwner(), messageRes -> {
            if (messageRes != null) {
                Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show();
                viewModel.consumeRefreshError();
            }
        });

        viewModel.loadIfNeeded();
    }

    @Override
    public void onDestroyView() {
        if (scoreAnimator != null) {
            scoreAnimator.cancel();
            scoreAnimator = null;
        }
        super.onDestroyView();
    }

    //Quyết định khối nào hiện: khung xương / danh sách / thông báo trống / lỗi kèm nút Thử lại
    private void render() {
        List<LeaderboardEntryResponse> podium = viewModel.getPodium().getValue();
        boolean hasContent = podium != null;
        boolean loading = Boolean.TRUE.equals(viewModel.getLoading().getValue());
        Integer error = viewModel.getErrorMessage().getValue();

        //Khung xương chỉ hiện khi đang tải mà chưa có gì để hiện (lần đầu hoặc vừa đổi bảng)
        if (!hasContent && loading) {
            shimmer.setVisibility(View.VISIBLE);
            shimmer.startShimmer();
        } else {
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);
        }

        swipeRefresh.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        swipeRefresh.setRefreshing(hasContent && loading);
        if (hasContent && !contentShown) {
            swipeRefresh.setAlpha(0f);
            swipeRefresh.animate().alpha(1f).setDuration(FADE_IN_DURATION_MS).start();
        } else if (!hasContent) {
            swipeRefresh.animate().cancel();
            swipeRefresh.setAlpha(1f);
        }
        contentShown = hasContent;

        tvEmpty.setVisibility(hasContent && podium.isEmpty() ? View.VISIBLE : View.GONE);

        boolean showError = !hasContent && !loading && error != null;
        layoutError.setVisibility(showError ? View.VISIBLE : View.GONE);
        if (showError) {
            tvError.setText(error);
        }
    }

    private void updateFooter() {
        if (Boolean.TRUE.equals(viewModel.getLoadMoreFailed().getValue())) {
            footerAdapter.setState(LeaderboardFooterAdapter.STATE_ERROR);
        } else if (Boolean.TRUE.equals(viewModel.getLoadingMore().getValue())) {
            footerAdapter.setState(LeaderboardFooterAdapter.STATE_LOADING);
        } else {
            footerAdapter.setState(LeaderboardFooterAdapter.STATE_HIDDEN);
        }
    }

    //Gần cuối danh sách thì xin ViewModel trang kế (ViewModel tự bỏ qua nếu đang tải hoặc đã hết)
    private void loadMoreIfNeeded() {
        if (layoutManager.findLastVisibleItemPosition() >= concatAdapter.getItemCount() - LOAD_MORE_THRESHOLD) {
            viewModel.loadNextPage();
        }
    }

    //Thẻ "Vị trí của tôi": hạng + điểm từ API hạng, tên + avatar từ hồ sơ (chưa tải được thì hiện "Bạn")
    private void bindMe() {
        LeaderboardMeResponse me = viewModel.getMyRank().getValue();
        if (me == null) {
            //Đang đổi bảng: ẩn thẻ và bỏ điểm cũ để lần hiện sau đếm từ 0 thay vì từ điểm của bảng trước
            if (scoreAnimator != null) {
                scoreAnimator.cancel();
            }
            scoreShown = false;
            shownScore = 0;
            cardMe.setVisibility(View.GONE);
            return;
        }
        UserResponse profile = viewModel.getProfile().getValue();
        String name = profile != null && profile.displayName != null
                ? profile.displayName
                : getString(R.string.leaderboard_me_default_name);

        cardMe.setVisibility(View.VISIBLE);
        avatarMe.bind(name, profile != null ? profile.avatarUrl : null);
        tvMeName.setText(name);
        if (me.rank != null) {
            tvMeRank.setText(getString(R.string.leaderboard_rank_format, me.rank));
            tvMeSubtitle.setText(R.string.leaderboard_me_label);
        } else {
            tvMeRank.setText(R.string.leaderboard_me_no_rank_value);
            tvMeSubtitle.setText(R.string.leaderboard_me_no_rank);
        }
        animateScore(me.score);
    }

    //Đếm số EXP chạy từ điểm đang hiển thị tới điểm mới
    private void animateScore(long target) {
        //Điểm không đổi (vd thẻ vẽ lại vì hồ sơ vừa tải xong): giữ nguyên, không cắt ngang hiệu ứng đang chạy
        if (scoreShown && target == shownScore) {
            return;
        }
        if (scoreAnimator != null) {
            scoreAnimator.cancel();
        }
        long from = shownScore;
        shownScore = target;
        scoreShown = true;
        if (from == target) {
            tvMeScore.setText(scoreText(target));
            return;
        }
        scoreAnimator = ValueAnimator.ofFloat(0f, 1f);
        scoreAnimator.setDuration(COUNT_UP_DURATION_MS);
        scoreAnimator.addUpdateListener(animator -> {
            long current = from + Math.round((target - from) * animator.getAnimatedFraction());
            tvMeScore.setText(scoreText(current));
        });
        scoreAnimator.start();
    }

    private String scoreText(long score) {
        return tvMeScore.getContext().getString(R.string.leaderboard_score_format, ExpFormatter.format(score));
    }

    private static String periodForChip(int chipId) {
        if (chipId == R.id.chipLeaderboardWeekly) {
            return LeaderboardViewModel.PERIOD_WEEKLY;
        }
        if (chipId == R.id.chipLeaderboardMonthly) {
            return LeaderboardViewModel.PERIOD_MONTHLY;
        }
        return LeaderboardViewModel.PERIOD_GLOBAL;
    }

    private static int chipIdForPeriod(String period) {
        if (LeaderboardViewModel.PERIOD_WEEKLY.equals(period)) {
            return R.id.chipLeaderboardWeekly;
        }
        if (LeaderboardViewModel.PERIOD_MONTHLY.equals(period)) {
            return R.id.chipLeaderboardMonthly;
        }
        return R.id.chipLeaderboardGlobal;
    }
}