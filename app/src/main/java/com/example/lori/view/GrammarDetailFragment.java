package com.example.lori.view;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.res.TypedArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.data.local.entity.GrammarExample;
import com.example.lori.data.local.entity.GrammarLesson;
import com.example.lori.data.local.entity.UserProgressLocal;
import com.example.lori.viewmodel.GrammarViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.List;

//Fragment hiển thị nội dung chi tiết một bài học ngữ pháp
public class GrammarDetailFragment extends Fragment {
    private GrammarViewModel viewModel;
    private WebView webViewContent;
    private TextView tvEyebrow;
    private TextView tvLessonTitle;
    private LinearLayout llExamples;
    private MaterialButton btnComplete;
    private MaterialButton btnStartQuiz;
    private int lessonId;
    private String lessonTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_grammar_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lessonId = requireArguments().getInt("lessonId");

        ImageButton btnBack = view.findViewById(R.id.btnBackGrammarDetail);
        tvEyebrow = view.findViewById(R.id.tvGrammarEyebrow);
        tvLessonTitle = view.findViewById(R.id.tvGrammarLessonTitle);
        webViewContent = view.findViewById(R.id.webViewGrammarContent);
        llExamples = view.findViewById(R.id.llGrammarExamples);
        btnComplete = view.findViewById(R.id.btnGrammarComplete);
        btnStartQuiz = view.findViewById(R.id.btnGrammarStartQuiz);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        btnStartQuiz.setOnClickListener(v -> {
            Bundle quizArgs = new Bundle();
            quizArgs.putString("quizType", "grammar");
            quizArgs.putInt("itemId", lessonId);
            quizArgs.putString("itemTitle", lessonTitle);
            Navigation.findNavController(v)
                    .navigate(R.id.action_grammarDetailFragment_to_quizFragment, quizArgs);
        });
        webViewContent.setBackgroundColor(Color.TRANSPARENT);

        viewModel = new ViewModelProvider(this).get(GrammarViewModel.class);
        viewModel.loadLesson(lessonId);
        viewModel.getLesson().observe(getViewLifecycleOwner(), this::onLessonLoaded);
        viewModel.getExamples().observe(getViewLifecycleOwner(), this::onExamplesLoaded);
        viewModel.getProgress().observe(getViewLifecycleOwner(), this::onProgressChanged);

        btnComplete.setOnClickListener(v -> {
            viewModel.markLessonCompleted();
            Navigation.findNavController(v).navigateUp();
        });
    }

    //Hiển thị tiêu đề, thông tin cấp độ và nạp nội dung html của bài học vào webview
    private void onLessonLoaded(GrammarLesson lesson) {
        if (lesson == null) return;
        lessonTitle = lesson.titleVi;
        tvEyebrow.setText(getString(R.string.grammar_lesson_eyebrow_format, levelLabel(lesson.level), lesson.orderIndex));
        tvLessonTitle.setText(lesson.titleVi);

        //Lấy đúng màu chữ và màu viền theo theme đang chạy, để không bị trùng màu nền
        String textColor = toHexColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface));
        String borderColor = toHexColor(resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant));

        String html = "<html><head><meta charset='utf-8'>"
                + "<style>body{font-family:sans-serif;font-size:15px;line-height:1.6;"
                + "color:" + textColor + ";margin:0;padding:0;}"
                + "table{border-collapse:collapse;width:100%;}"
                + "td,th{border:1px solid " + borderColor + ";padding:6px;text-align:left;}</style>"
                + "</head><body>" + lesson.contentHtml + "</body></html>";
        webViewContent.getSettings().setDefaultTextEncodingName("utf-8");
        webViewContent.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    //Lấy giá trị màu của một thuộc tính theme hiện hành
    private int resolveThemeColor(int attrResId) {
        TypedArray typedArray = requireContext().obtainStyledAttributes(new int[]{attrResId});
        int color = typedArray.getColor(0, 0xFF000000);
        typedArray.recycle();
        return color;
    }

    //Chuyển màu dạng số nguyên sang chuỗi mã hex
    private String toHexColor(int colorInt) {
        return String.format("#%06X", 0xFFFFFF & colorInt);
    }

    //Dựng danh sách câu ví dụ minh họa, phân biệt câu đúng và câu sai kèm giải thích
    private void onExamplesLoaded(List<GrammarExample> examples) {
        llExamples.removeAllViews();
        if (examples == null) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GrammarExample example : examples) {
            View exampleView = inflater.inflate(R.layout.item_grammar_example, llExamples, false);
            TextView tvSentenceEn = exampleView.findViewById(R.id.tvExampleSentenceEn);
            TextView tvSentenceVi = exampleView.findViewById(R.id.tvExampleSentenceVi);
            TextView tvWrongLabel = exampleView.findViewById(R.id.tvExampleWrongLabel);
            TextView tvExplanation = exampleView.findViewById(R.id.tvExampleExplanation);

            tvSentenceEn.setText(example.sentenceEn);
            tvSentenceVi.setText(example.sentenceVi);

            if (example.isCorrect) {
                tvWrongLabel.setVisibility(View.GONE);
                tvExplanation.setVisibility(View.GONE);
            } else {
                tvWrongLabel.setVisibility(View.VISIBLE);
                if (example.explanation != null && !example.explanation.isEmpty()) {
                    tvExplanation.setVisibility(View.VISIBLE);
                    tvExplanation.setText(example.explanation);
                } else {
                    tvExplanation.setVisibility(View.GONE);
                }
            }
            llExamples.addView(exampleView);
        }
    }

    //Cập nhật trạng thái nút hoàn thành dựa theo tiến độ đã lưu
    private void onProgressChanged(UserProgressLocal progress) {
        boolean done = progress != null && UserProgressLocal.STATUS_COMPLETED.equals(progress.status);
        btnComplete.setEnabled(!done);
        btnComplete.setText(done ? R.string.grammar_completed_label : R.string.grammar_complete_button);
    }

    private String levelLabel(String level) {
        if ("beginner".equals(level)) return getString(R.string.grammar_level_beginner);
        if ("intermediate".equals(level)) return getString(R.string.grammar_level_intermediate);
        if ("advanced".equals(level)) return getString(R.string.grammar_level_advanced);
        return level;
    }
}