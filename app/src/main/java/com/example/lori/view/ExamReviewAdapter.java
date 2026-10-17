package com.example.lori.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.remote.dto.ExamResultDetailResponse;
import com.example.lori.viewmodel.ExamTakingViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

//Adapter màn Xem lại bài làm: mỗi dòng là một câu (trạng thái, nội dung, các lựa chọn tô màu đúng/sai, giải thích)
//Chỉ đổi dữ liệu Backend trả về thành giao diện, không giữ trạng thái nào
public class ExamReviewAdapter extends RecyclerView.Adapter<ExamReviewAdapter.ReviewViewHolder> {

    //Ba lựa chọn cố định của dạng Đúng/Sai/Không có thông tin; cũng là giá trị đáp án Backend lưu
    private static final List<String> TRUE_FALSE_NOT_GIVEN_OPTIONS = Arrays.asList("TRUE", "FALSE", "NOT GIVEN");

    //Cách tô màu một dòng lựa chọn
    private enum RowState {
        NEUTRAL,
        CORRECT,
        WRONG
    }

    private List<ExamResultDetailResponse.Item> items = new ArrayList<>();

    //Cập nhật danh sách câu và refresh recyclerview
    public void setItems(List<ExamResultDetailResponse.Item> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        ExamResultDetailResponse.Item item = items.get(position);
        Context context = holder.itemView.getContext();
        boolean skipped = isBlank(item.yourAnswer);

        //Số câu là vị trí trong danh sách (Backend trả đúng thứ tự làm bài), giống số câu lúc làm bài
        holder.tvQuestion.setText(isBlank(item.questionText)
                ? context.getString(R.string.exam_review_question_no_text_format, position + 1)
                : context.getString(R.string.exam_review_question_format, position + 1, item.questionText));
        bindStatus(holder.tvStatus, context, item.correct, skipped);
        bindAnswers(holder.layoutOptions, context, item, skipped);

        boolean hasExplanation = !isBlank(item.explanation);
        holder.cardExplanation.setVisibility(hasExplanation ? View.VISIBLE : View.GONE);
        if (hasExplanation) {
            holder.tvExplanation.setText(context.getString(R.string.exam_review_explanation_format, item.explanation));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    //Nhãn Đúng / Sai / Bỏ trống (nền và chữ đổi màu theo trạng thái)
    private static void bindStatus(TextView tvStatus, Context context, boolean correct, boolean skipped) {
        int textRes;
        int bgColor;
        int textColor;
        if (correct) {
            textRes = R.string.exam_review_status_correct;
            bgColor = R.color.quiz_correct_bg;
            textColor = R.color.quiz_correct_text;
        } else if (skipped) {
            textRes = R.string.exam_review_status_skipped;
            bgColor = R.color.lori_surface_container_high;
            textColor = R.color.lori_on_surface_variant;
        } else {
            textRes = R.string.exam_review_status_wrong;
            bgColor = R.color.quiz_incorrect_bg;
            textColor = R.color.quiz_incorrect_text;
        }
        tvStatus.setText(textRes);
        tvStatus.setBackgroundTintList(ColorStateList.valueOf(color(context, bgColor)));
        tvStatus.setTextColor(color(context, textColor));
    }

    //Dựng các dòng lựa chọn (hoặc dòng câu trả lời với câu điền từ) và tô màu theo đáp án
    private static void bindAnswers(LinearLayout container, Context context,
                                    ExamResultDetailResponse.Item item, boolean skipped) {
        container.removeAllViews();
        String yours = skipped ? null : item.yourAnswer.trim();
        String correctAnswer = item.correctAnswer == null ? "" : item.correctAnswer.trim();

        boolean trueFalse = ExamTakingViewModel.TYPE_TRUE_FALSE_NOT_GIVEN.equals(item.questionType);
        boolean hasOptions = item.options != null && !item.options.isEmpty();
        boolean choice = !ExamTakingViewModel.TYPE_FILL_BLANK.equals(item.questionType) && (trueFalse || hasOptions);

        if (!choice) {
            //Điền từ (hoặc câu không có lựa chọn): câu bạn viết, rồi đáp án đúng nếu bạn sai
            if (skipped) {
                addRow(container, context, null, context.getString(R.string.exam_review_unanswered_row),
                        RowState.NEUTRAL, 0);
            } else {
                addRow(container, context, null, yours,
                        item.correct ? RowState.CORRECT : RowState.WRONG, R.string.exam_review_tag_yours_written);
            }
            if (!item.correct) {
                addRow(container, context, null, correctAnswer, RowState.CORRECT, R.string.exam_review_tag_correct);
            }
            return;
        }

        List<String> texts = trueFalse ? TRUE_FALSE_NOT_GIVEN_OPTIONS : item.options;
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            //Trắc nghiệm/nối: đáp án là chữ cái A, B, C... Đúng/Sai/Không có thông tin: đáp án chính là chữ của lựa chọn
            String value = trueFalse ? text : String.valueOf((char) ('A' + i));
            boolean isCorrect = sameAnswer(value, correctAnswer);
            boolean isYours = yours != null && sameAnswer(value, yours);

            RowState state = isCorrect ? RowState.CORRECT : (isYours ? RowState.WRONG : RowState.NEUTRAL);
            int tagRes = isYours ? R.string.exam_review_tag_yours : (isCorrect ? R.string.exam_review_tag_correct : 0);
            //TOEIC Part 1-2 không in nội dung lựa chọn (options chỉ là "A", "B"...): chỉ hiện chữ cái
            boolean textIsRedundant = !trueFalse
                    && (text == null || text.trim().isEmpty() || text.trim().equals(value));
            addRow(container, context, trueFalse ? null : value, textIsRedundant ? "" : text, state, tagRes);
        }
    }

    //Thêm một dòng vào container. letter = null thì ẩn huy hiệu chữ cái; tagRes = 0 thì không có nhãn bên phải
    private static void addRow(ViewGroup container, Context context, @Nullable String letter, String text,
                               RowState state, @StringRes int tagRes) {
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(context)
                .inflate(R.layout.item_exam_review_option, container, false);
        TextView tvLetter = card.findViewById(R.id.tvReviewOptionLetter);
        TextView tvText = card.findViewById(R.id.tvReviewOptionText);
        TextView tvTag = card.findViewById(R.id.tvReviewOptionTag);

        if (letter == null) {
            tvLetter.setVisibility(View.GONE);
        } else {
            tvLetter.setText(letter);
        }
        //Ô chữ luôn có mặt (có thể rỗng) để nhãn bên phải luôn nằm sát mép phải
        tvText.setText(text);
        if (tagRes != 0) {
            tvTag.setText(tagRes);
            tvTag.setVisibility(View.VISIBLE);
        }

        if (state != RowState.NEUTRAL) {
            boolean correct = state == RowState.CORRECT;
            int bg = color(context, correct ? R.color.quiz_correct_bg : R.color.quiz_incorrect_bg);
            int fg = color(context, correct ? R.color.quiz_correct_text : R.color.quiz_incorrect_text);
            card.setCardBackgroundColor(bg);
            card.setStrokeColor(fg);
            tvLetter.setBackgroundTintList(ColorStateList.valueOf(fg));
            tvLetter.setTextColor(color(context, R.color.white));
            tvText.setTextColor(fg);
            tvTag.setTextColor(fg);
        }
        container.addView(card);
    }

    //So đáp án giống cách Backend chấm: bỏ khoảng trắng thừa và không phân biệt hoa/thường ("not given" = "NOT GIVEN")
    private static boolean sameAnswer(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(@Nullable String text) {
        return text == null || text.trim().isEmpty();
    }

    private static int color(Context context, @ColorRes int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    //viewholder giữ các thành phần giao diện của một câu
    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        final TextView tvStatus;
        final TextView tvQuestion;
        final LinearLayout layoutOptions;
        final MaterialCardView cardExplanation;
        final TextView tvExplanation;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStatus = itemView.findViewById(R.id.tvReviewStatus);
            tvQuestion = itemView.findViewById(R.id.tvReviewQuestion);
            layoutOptions = itemView.findViewById(R.id.layoutReviewOptions);
            cardExplanation = itemView.findViewById(R.id.cardReviewExplanation);
            tvExplanation = itemView.findViewById(R.id.tvReviewExplanation);
        }
    }
}