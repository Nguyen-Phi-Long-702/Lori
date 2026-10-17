package com.example.lori;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lori.utils.ExamAudioPlayer;
import com.example.lori.utils.ThemeHelper;
import com.example.lori.view.ExamQuestionNavAdapter;
import com.example.lori.view.ExamQuestionPagerAdapter;
import com.example.lori.view.QuestionNavigatorSheet;
import com.example.lori.viewmodel.ExamQuestionItem;
import com.example.lori.viewmodel.ExamSubmitError;
import com.example.lori.viewmodel.ExamTakingViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

//Màn làm bài thi TOEIC/IELTS (Premium): đồng hồ đếm ngược, mỗi câu một trang (ViewPager2),
//dải ô + lưới điều hướng câu hỏi, trình phát audio Listening, nộp bài (xác nhận → tiến trình → lỗi/thành công)
//Nộp thành công thì trả id kết quả cho màn gọi (xem getResultId) rồi đóng
public class ExamTakingActivity extends AppCompatActivity {

    private static final String EXTRA_EXAM_ID = "exam_id";
    private static final String EXTRA_RESULT_ID = "result_id";

    private ExamTakingViewModel viewModel;
    private ExamAudioPlayer audioPlayer;
    //Hộp thoại xác nhận/lỗi/thoát đang hiện (mỗi lúc chỉ một) và hộp thoại tiến trình "Đang nộp bài"
    private AlertDialog activeDialog;
    private AlertDialog submittingDialog;

    private ViewPager2 pager;
    private ExamQuestionPagerAdapter pagerAdapter;
    private RecyclerView rvStrip;
    private LinearLayoutManager stripLayoutManager;
    private ExamQuestionNavAdapter stripAdapter;

    private TextView tvTitle;
    private TextView tvPosition;
    private TextView tvTimer;
    private View btnGrid;
    private View groupContent;
    private View layoutError;
    private TextView tvError;
    private MaterialButton btnFlag;
    private MaterialButton btnNext;

    private View layoutAudio;
    private TextView tvAudioTitle;
    private TextView tvAudioTime;
    private TextView tvAudioError;
    private LinearProgressIndicator progressAudio;
    private ImageButton btnAudioPlay;

    //Mở màn làm bài cho một đề. Chỉ cần id đề, nội dung đề được tải từ Backend
    public static Intent newIntent(Context context, String examId) {
        Intent intent = new Intent(context, ExamTakingActivity.class);
        intent.putExtra(EXTRA_EXAM_ID, examId);
        return intent;
    }

    //Đọc id kết quả từ Intent mà màn làm bài trả về sau khi nộp thành công; null nếu không có (người dùng thoát giữa chừng)
    public static String getResultId(Intent data) {
        return data == null ? null : data.getStringExtra(EXTRA_RESULT_ID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Áp dụng giao diện tối đã lưu, giống các activity khác
        ThemeHelper.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_exam_taking);

        //Chừa chỗ cho thanh hệ thống và cả bàn phím để ô điền từ không bị che
        View root = findViewById(R.id.examTakingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        String examId = getIntent().getStringExtra(EXTRA_EXAM_ID);
        if (examId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ExamTakingViewModel.class);

        findViews();
        setUpAudioPlayer();
        setUpPager();
        setUpStrip();
        setUpClickListeners();
        setUpBackHandling();
        observeViewModel();

        viewModel.load(examId);
    }

    private void findViews() {
        pager = findViewById(R.id.pagerQuestions);
        rvStrip = findViewById(R.id.rvQuestionStrip);
        tvTitle = findViewById(R.id.tvExamTakingTitle);
        tvPosition = findViewById(R.id.tvExamTakingPosition);
        tvTimer = findViewById(R.id.tvExamTimer);
        btnGrid = findViewById(R.id.btnExamGrid);
        groupContent = findViewById(R.id.groupExamTaking);
        layoutError = findViewById(R.id.layoutExamTakingError);
        tvError = findViewById(R.id.tvExamTakingError);
        btnFlag = findViewById(R.id.btnExamFlag);
        btnNext = findViewById(R.id.btnExamNext);
        layoutAudio = findViewById(R.id.layoutExamAudio);
        tvAudioTitle = findViewById(R.id.tvAudioTitle);
        tvAudioTime = findViewById(R.id.tvAudioTime);
        tvAudioError = findViewById(R.id.tvAudioError);
        progressAudio = findViewById(R.id.progressAudio);
        btnAudioPlay = findViewById(R.id.btnAudioPlay);
    }

    //Trình phát audio chỉ báo trạng thái về đây để cập nhật nút Phát/Tạm dừng, thanh tiến trình, thông báo lỗi
    private void setUpAudioPlayer() {
        audioPlayer = new ExamAudioPlayer(this, new ExamAudioPlayer.Listener() {
            @Override
            public void onPlayingChanged(boolean playing) {
                btnAudioPlay.setImageResource(playing ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
                btnAudioPlay.setContentDescription(getString(playing ? R.string.cd_audio_pause : R.string.cd_audio_play));
                if (playing) {
                    tvAudioError.setVisibility(View.GONE);
                }
            }

            @Override
            public void onProgress(long positionMs, long durationMs) {
                int permille = durationMs > 0L ? (int) (positionMs * 1000L / durationMs) : 0;
                progressAudio.setProgressCompat(permille, false);
                tvAudioTime.setText(getString(R.string.exam_audio_time_format,
                        formatAudioTime(positionMs), formatAudioTime(durationMs)));
            }

            @Override
            public void onError() {
                tvAudioError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setUpPager() {
        //Vuốt sang trang khác thì báo cho ViewModel và ẩn bàn phím (nếu đang nhập ở câu điền từ)
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                viewModel.setCurrentPosition(position);
                hideKeyboard();
            }
        });
    }

    private void setUpStrip() {
        stripLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        stripAdapter = new ExamQuestionNavAdapter(false, position -> viewModel.setCurrentPosition(position));
        rvStrip.setLayoutManager(stripLayoutManager);
        rvStrip.setAdapter(stripAdapter);
    }

    private void setUpClickListeners() {
        findViewById(R.id.btnBackExam).setOnClickListener(v -> confirmExit());
        findViewById(R.id.btnExamTakingRetry).setOnClickListener(v -> viewModel.retry());
        btnGrid.setOnClickListener(v ->
                new QuestionNavigatorSheet().show(getSupportFragmentManager(), "question_navigator"));

        btnFlag.setOnClickListener(v -> viewModel.toggleCurrentFlag());
        btnNext.setOnClickListener(v -> {
            if (viewModel.hasNext()) {
                viewModel.goToNext();
            } else {
                //Câu cuối: nút là "Nộp bài" → hỏi xác nhận rồi nộp
                confirmSubmit();
            }
        });

        findViewById(R.id.btnAudioRewind).setOnClickListener(v -> audioPlayer.rewind());
        findViewById(R.id.btnAudioForward).setOnClickListener(v -> audioPlayer.forward());
        btnAudioPlay.setOnClickListener(v -> audioPlayer.togglePlayPause());
    }

    //Bấm Back (nút trên header hoặc nút hệ thống) khi đang làm bài thì hỏi lại để không mất đáp án do bấm nhầm
    private void setUpBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        //Chưa tải xong đề thì chưa có gì để mất, thoát luôn
        if (viewModel.getQuestions().getValue() == null) {
            finish();
            return;
        }
        showActiveDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.exam_exit_title)
                .setMessage(R.string.exam_exit_message)
                .setNegativeButton(R.string.exam_exit_continue, null)
                .setPositiveButton(R.string.exam_exit_confirm, (dialog, which) -> finish()));
    }

    private void observeViewModel() {
        LinearProgressIndicator progress = findViewById(R.id.progressExamTaking);
        viewModel.getLoading().observe(this, loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));

        viewModel.getErrorMessage().observe(this, messageRes -> {
            if (messageRes == null) {
                layoutError.setVisibility(View.GONE);
            } else {
                tvError.setText(messageRes);
                layoutError.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getExamTitle().observe(this, title -> tvTitle.setText(title));

        //Đề tải xong: hiện nội dung làm bài và dựng các trang câu hỏi
        viewModel.getQuestions().observe(this, questions -> {
            if (questions == null) {
                return;
            }
            groupContent.setVisibility(View.VISIBLE);
            btnGrid.setVisibility(View.VISIBLE);
            stripAdapter.setQuestions(questions);
            if (pagerAdapter == null) {
                pagerAdapter = new ExamQuestionPagerAdapter(this, questions.size());
                pager.setAdapter(pagerAdapter);
            }
            renderPosition();
        });

        viewModel.getCurrentPosition().observe(this, position -> renderPosition());
        viewModel.getAnswers().observe(this, answers -> renderNavigation());
        viewModel.getFlagged().observe(this, flagged -> {
            renderNavigation();
            renderFlagButton();
        });

        viewModel.getRemainingSeconds().observe(this, seconds -> {
            if (seconds == null) {
                return;
            }
            tvTimer.setText(getString(R.string.exam_timer_format, seconds / 60, seconds % 60));
            tvTimer.setVisibility(View.VISIBLE);
        });
        viewModel.getTimerWarning().observe(this, warning -> {
            boolean isWarning = Boolean.TRUE.equals(warning);
            tvTimer.setBackgroundTintList(ColorStateList.valueOf(
                    color(isWarning ? R.color.lori_error : R.color.lori_inverse_surface)));
            tvTimer.setTextColor(color(isWarning ? R.color.lori_on_error : R.color.lori_inverse_on_surface));
        });
        //Hết giờ: ViewModel tự nộp bài, ở đây chỉ báo cho người dùng biết
        viewModel.getTimeUp().observe(this, timeUp -> {
            if (Boolean.TRUE.equals(timeUp)) {
                Toast.makeText(this, R.string.exam_time_up_toast, Toast.LENGTH_LONG).show();
            }
        });

        //Nộp bài: hộp thoại tiến trình / hộp thoại lỗi đều dựng lại từ trạng thái ViewModel (xoay màn hình không mất)
        viewModel.getSubmitting().observe(this, submitting -> renderSubmitting(Boolean.TRUE.equals(submitting)));
        viewModel.getSubmitError().observe(this, this::showSubmitError);
        //Nộp xong: trả id kết quả cho màn danh sách (nó sẽ mở màn Kết quả) rồi đóng màn làm bài
        viewModel.getSubmitResultId().observe(this, resultId -> {
            if (resultId == null) {
                return;
            }
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT_ID, resultId));
            finish();
        });
    }

    //Bấm Nộp bài (câu cuối): hỏi lại, kèm số câu đã trả lời để người dùng biết còn bỏ sót bao nhiêu câu
    private void confirmSubmit() {
        showActiveDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.exam_submit_confirm_title)
                .setMessage(getString(R.string.exam_submit_confirm_message_format,
                        viewModel.getAnsweredCount(), viewModel.getQuestionCount(), viewModel.getFlaggedCount()))
                .setNegativeButton(R.string.exam_exit_continue, null)
                .setPositiveButton(R.string.exam_submit_button, (dialog, which) -> viewModel.submit()));
    }

    //Lỗi khi nộp bài (error = null nghĩa là hết lỗi → đóng hộp thoại). Hộp thoại không tắt khi chạm ra ngoài/bấm Back
    //Lỗi tạm thời: Nộp lại / Đóng (đáp án vẫn giữ nguyên). Lỗi không cứu được: chỉ còn Thoát
    private void showSubmitError(ExamSubmitError error) {
        if (error == null) {
            dismissActiveDialog();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.exam_submit_error_title)
                .setMessage(error.messageRes)
                .setCancelable(false);
        if (error.canRetry) {
            builder.setPositiveButton(R.string.exam_submit_retry_button, (dialog, which) -> viewModel.submit())
                    .setNegativeButton(R.string.exam_submit_error_close, (dialog, which) -> viewModel.clearSubmitError());
        } else {
            builder.setPositiveButton(R.string.exam_exit_confirm, (dialog, which) -> finish());
        }
        showActiveDialog(builder);
    }

    //Đang nộp bài: đóng các hộp thoại khác, tạm dừng audio và hiện hộp thoại tiến trình không tắt được
    private void renderSubmitting(boolean submitting) {
        if (!submitting) {
            if (submittingDialog != null) {
                submittingDialog.dismiss();
                submittingDialog = null;
            }
            return;
        }
        dismissActiveDialog();
        audioPlayer.pause();
        if (submittingDialog != null || isFinishing() || isDestroyed()) {
            return;
        }
        submittingDialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_exam_submitting)
                .setCancelable(false)
                .show();
    }

    //Hiện hộp thoại xác nhận/lỗi/thoát. Hiện cái mới thì đóng cái cũ
    private void showActiveDialog(AlertDialog.Builder builder) {
        dismissActiveDialog();
        if (isFinishing() || isDestroyed()) {
            return;
        }
        activeDialog = builder.show();
    }

    private void dismissActiveDialog() {
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
    }

    //Cập nhật mọi thứ phụ thuộc câu đang xem: trang ViewPager2, header, nút dưới, dải ô, audio
    private void renderPosition() {
        List<ExamQuestionItem> questions = viewModel.getQuestions().getValue();
        Integer position = viewModel.getCurrentPosition().getValue();
        if (questions == null || position == null || pagerAdapter == null) {
            return;
        }

        //Chạm ô số hoặc nút Câu tiếp theo: đưa ViewPager2 tới trang đó (trượt mượt khi chỉ sang câu liền kề)
        int shown = pager.getCurrentItem();
        if (shown != position) {
            pager.setCurrentItem(position, Math.abs(shown - position) == 1);
        }

        tvPosition.setText(getString(R.string.exam_taking_position_format, position + 1, questions.size()));
        btnNext.setText(viewModel.hasNext() ? R.string.exam_next_button : R.string.exam_submit_button);
        renderNavigation();
        renderFlagButton();
        scrollStripTo(position);
        bindAudio(questions.get(position));
    }

    private void renderNavigation() {
        Integer position = viewModel.getCurrentPosition().getValue();
        stripAdapter.setState(viewModel.getAnswers().getValue(), viewModel.getFlagged().getValue(),
                position != null ? position : 0);
    }

    //Nút Đánh dấu đổi chữ và đổi màu icon theo câu đang xem đã được đánh dấu hay chưa
    private void renderFlagButton() {
        Integer position = viewModel.getCurrentPosition().getValue();
        ExamQuestionItem item = position == null ? null : viewModel.getQuestion(position);
        boolean flagged = item != null && viewModel.isFlagged(item.questionId);
        btnFlag.setText(flagged ? R.string.exam_unflag_button : R.string.exam_flag_button);
        btnFlag.setIconTint(ColorStateList.valueOf(
                color(flagged ? R.color.exam_question_flagged : R.color.lori_primary)));
    }

    //Cuộn dải ô để ô của câu đang xem nằm giữa màn hình
    private void scrollStripTo(int position) {
        rvStrip.post(() -> stripLayoutManager.scrollToPositionWithOffset(
                position, Math.max(0, rvStrip.getWidth() / 2 - dp(22))));
    }

    //Section của câu đang xem có audio thì hiện trình phát; sang section khác (file khác) thì nạp file mới
    //Các câu cùng section dùng chung một file nên chuyển giữa chúng không làm audio phát lại từ đầu
    private void bindAudio(ExamQuestionItem item) {
        String url = item.audioUrl;
        if (url == null) {
            audioPlayer.stop();
            layoutAudio.setVisibility(View.GONE);
            tvAudioError.setVisibility(View.GONE);
            return;
        }
        layoutAudio.setVisibility(View.VISIBLE);
        tvAudioTitle.setText(item.sectionTitle);
        if (url.equals(audioPlayer.getCurrentUrl())) {
            return;
        }
        tvAudioError.setVisibility(View.GONE);
        //Nghe dở (xoay màn hình, quay lại app) thì nghe tiếp ở đúng vị trí và chờ người dùng bấm Phát;
        //file chưa nghe thì tự phát từ đầu như đề thi thật
        long startPositionMs = viewModel.getSavedAudioPosition(url);
        audioPlayer.load(url, startPositionMs, startPositionMs == 0L);
    }

    //Rời màn hình (Home, màn hình tắt, xoay màn hình): tạm dừng audio và nhớ vị trí đang nghe
    @Override
    protected void onStop() {
        super.onStop();
        if (audioPlayer == null) {
            return;
        }
        String url = audioPlayer.getCurrentUrl();
        if (url != null) {
            viewModel.saveAudioPosition(url, audioPlayer.getPositionMs());
        }
        audioPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        //Hộp thoại sẽ được dựng lại từ trạng thái ViewModel ở Activity mới
        dismissActiveDialog();
        if (submittingDialog != null) {
            submittingDialog.dismiss();
            submittingDialog = null;
        }
        if (audioPlayer != null) {
            audioPlayer.release();
        }
        super.onDestroy();
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(pager.getWindowToken(), 0);
        }
    }

    //Độ dài audio dạng m:ss, vd 0:42
    private static String formatAudioTime(long ms) {
        long totalSeconds = ms / 1000L;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private int color(@ColorRes int colorRes) {
        return ContextCompat.getColor(this, colorRes);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}