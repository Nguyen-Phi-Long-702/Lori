package com.example.lori.view;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.lori.R;
import com.example.lori.viewmodel.ExamQuestionItem;
import com.example.lori.viewmodel.ExamTakingViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//Một trang của màn làm bài: hiển thị một câu hỏi và nhận đáp án của người dùng
//Hỗ trợ 4 dạng: trắc nghiệm (MULTIPLE_CHOICE), nối (MATCHING), Đúng/Sai/Không có thông tin (TRUE_FALSE_NOT_GIVEN), điền từ (FILL_BLANK)
//Fragment không giữ trạng thái: đáp án nằm trong ExamTakingViewModel (dùng chung với Activity)
public class QuestionFragment extends Fragment {

    private static final String ARG_POSITION = "position";
    //Ba lựa chọn cố định của dạng Đúng/Sai/Không có thông tin; cũng là giá trị đáp án gửi cho Backend
    private static final List<String> TRUE_FALSE_NOT_GIVEN_OPTIONS = Arrays.asList("TRUE", "FALSE", "NOT GIVEN");

    //Tên Part và hướng dẫn của Part TOEIC 1-7 (phần tử i ứng với Part i + 1)
    private static final int[] PART_TITLES = {
            R.string.exam_part_title_1, R.string.exam_part_title_2, R.string.exam_part_title_3,
            R.string.exam_part_title_4, R.string.exam_part_title_5, R.string.exam_part_title_6,
            R.string.exam_part_title_7};
    private static final int[] PART_DIRECTIONS = {
            R.string.exam_part_directions_1, R.string.exam_part_directions_2, R.string.exam_part_directions_3,
            R.string.exam_part_directions_4, R.string.exam_part_directions_5, R.string.exam_part_directions_6,
            R.string.exam_part_directions_7};

    private ExamTakingViewModel viewModel;
    private ExamQuestionItem item;

    private ViewGroup layoutOptions;

    //Ba danh sách song song, cùng thứ tự: thẻ lựa chọn, chữ cái trong thẻ, giá trị đáp án của lựa chọn đó
    private final List<MaterialCardView> optionCards = new ArrayList<>();
    private final List<TextView> optionLetters = new ArrayList<>();
    private final List<String> optionValues = new ArrayList<>();

    public static QuestionFragment newInstance(int position) {
        QuestionFragment fragment = new QuestionFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_question, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ExamTakingViewModel.class);
        item = viewModel.getQuestion(requireArguments().getInt(ARG_POSITION));
        //ViewModel chưa có đề (vd app bị hệ thống thu hồi rồi mở lại): bỏ qua, Activity sẽ tải lại đề
        if (item == null) {
            return;
        }

        layoutOptions = view.findViewById(R.id.layoutOptions);
        TextView tvSection = view.findViewById(R.id.tvQuestionSection);
        View cardPassage = view.findViewById(R.id.cardPassage);
        TextView tvPassage = view.findViewById(R.id.tvPassage);
        TextView tvQuestionText = view.findViewById(R.id.tvQuestionText);

        tvSection.setText(item.sectionTitle);
        bindPartDirections(view);
        if (item.passageText != null) {
            tvPassage.setText(item.passageText);
            cardPassage.setVisibility(View.VISIBLE);
        }
        if (item.questionText != null) {
            tvQuestionText.setText(item.questionText);
            tvQuestionText.setVisibility(View.VISIBLE);
        }

        if (ExamTakingViewModel.TYPE_FILL_BLANK.equals(item.questionType)) {
            bindFillBlank(view);
        } else if (ExamTakingViewModel.TYPE_TRUE_FALSE_NOT_GIVEN.equals(item.questionType)) {
            bindOptions(TRUE_FALSE_NOT_GIVEN_OPTIONS, false);
        } else if (item.options != null) {
            //MULTIPLE_CHOICE và MATCHING cùng là chọn một trong danh sách, đáp án là chữ cái A, B, C...
            bindOptions(item.options, true);
        }

        //Tô lại lựa chọn đang chọn mỗi khi đáp án thay đổi (kể cả khi chọn từ bảng khác)
        viewModel.getAnswers().observe(getViewLifecycleOwner(),
                answers -> renderSelection(answers.get(item.questionId)));
    }

    //Câu đầu tiên của mỗi Part TOEIC: hiện tên Part và hướng dẫn làm bài (như phần Directions của đề thật)
    //Các câu khác và đề IELTS (toeicPart = 0) không hiện khung này
    private void bindPartDirections(@NonNull View view) {
        if (!item.firstOfPart || item.toeicPart < 1 || item.toeicPart > PART_TITLES.length) {
            return;
        }
        TextView tvPartTitle = view.findViewById(R.id.tvPartTitle);
        TextView tvPartDirections = view.findViewById(R.id.tvPartDirections);
        tvPartTitle.setText(PART_TITLES[item.toeicPart - 1]);
        tvPartDirections.setText(PART_DIRECTIONS[item.toeicPart - 1]);
        view.findViewById(R.id.cardPartDirections).setVisibility(View.VISIBLE);
    }

    //Dạng điền từ: hiện ô nhập, khôi phục chữ đã nhập trước đó và lưu vào ViewModel mỗi khi gõ
    private void bindFillBlank(@NonNull View view) {
        view.findViewById(R.id.layoutFillBlank).setVisibility(View.VISIBLE);
        TextInputEditText etFillBlank = view.findViewById(R.id.etFillBlank);

        String saved = viewModel.getAnswer(item.questionId);
        if (saved != null) {
            etFillBlank.setText(saved);
        }
        etFillBlank.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setAnswer(item.questionId, s.toString());
            }
        });
    }

    //Dạng chọn đáp án: mỗi lựa chọn là một thẻ item_exam_option
    //lettered = true: giá trị đáp án là chữ cái (A, B, C...), thẻ có huy hiệu chữ cái
    //lettered = false: giá trị đáp án chính là chữ của lựa chọn (TRUE / FALSE / NOT GIVEN), không có huy hiệu
    private void bindOptions(List<String> texts, boolean lettered) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < texts.size(); i++) {
            MaterialCardView card = (MaterialCardView) inflater.inflate(R.layout.item_exam_option, layoutOptions, false);
            TextView tvLetter = card.findViewById(R.id.tvOptionLetter);
            TextView tvText = card.findViewById(R.id.tvOptionText);

            String text = texts.get(i);
            String value = lettered ? String.valueOf((char) ('A' + i)) : text;

            if (lettered) {
                tvLetter.setText(value);
            } else {
                tvLetter.setVisibility(View.GONE);
            }
            //TOEIC Part 1-2 không in nội dung lựa chọn (options chỉ là "A", "B"...): chỉ hiện huy hiệu chữ cái
            boolean textIsRedundant = lettered && (text == null || text.trim().isEmpty() || text.trim().equals(value));
            tvText.setText(text);
            tvText.setVisibility(textIsRedundant ? View.GONE : View.VISIBLE);

            card.setOnClickListener(v -> viewModel.setAnswer(item.questionId, value));

            layoutOptions.addView(card);
            optionCards.add(card);
            optionLetters.add(tvLetter);
            optionValues.add(value);
        }
    }

    //Viền và chữ cái của lựa chọn đang chọn đổi sang màu chính, các lựa chọn khác về màu thường
    private void renderSelection(@Nullable String selectedValue) {
        for (int i = 0; i < optionCards.size(); i++) {
            boolean selected = optionValues.get(i).equals(selectedValue);
            MaterialCardView card = optionCards.get(i);
            TextView tvLetter = optionLetters.get(i);

            card.setStrokeColor(color(selected ? R.color.lori_primary : R.color.lori_outline_variant));
            card.setStrokeWidth(dp(selected ? 2 : 1));
            tvLetter.setBackgroundTintList(ColorStateList.valueOf(
                    color(selected ? R.color.lori_primary : R.color.lori_surface_container_high)));
            tvLetter.setTextColor(color(selected ? R.color.lori_on_primary : R.color.lori_on_surface));
        }
    }

    private int color(@ColorRes int colorRes) {
        return ContextCompat.getColor(requireContext(), colorRes);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}