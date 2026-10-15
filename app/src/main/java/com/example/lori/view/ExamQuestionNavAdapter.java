package com.example.lori.view;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.viewmodel.ExamQuestionItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

//Adapter các ô số câu hỏi. Dùng hai nơi: dải ô cuộn ngang dưới header (fillCell = false)
//và lưới trong bảng danh sách câu hỏi (fillCell = true, ô giãn đều theo cột)
//Màu ô theo thứ tự ưu tiên: câu đang xem > câu đã đánh dấu > câu đã trả lời > chưa trả lời
public class ExamQuestionNavAdapter extends RecyclerView.Adapter<ExamQuestionNavAdapter.NavViewHolder> {

    //Callback để màn hình xử lý khi người dùng chạm một ô số
    public interface OnQuestionClickListener {
        void onQuestionClick(int position);
    }

    private final boolean fillCell;
    private final OnQuestionClickListener listener;

    private List<ExamQuestionItem> questions = new ArrayList<>();
    private Map<String, String> answers = new HashMap<>();
    private Set<String> flagged = new HashSet<>();
    private int currentPosition = 0;

    public ExamQuestionNavAdapter(boolean fillCell, OnQuestionClickListener listener) {
        this.fillCell = fillCell;
        this.listener = listener;
    }

    public void setQuestions(List<ExamQuestionItem> newQuestions) {
        this.questions = newQuestions != null ? newQuestions : new ArrayList<>();
        notifyDataSetChanged();
    }

    //Cập nhật trạng thái các ô: đáp án đã chọn, câu đã đánh dấu, câu đang xem
    public void setState(Map<String, String> newAnswers, Set<String> newFlagged, int newCurrentPosition) {
        this.answers = newAnswers != null ? newAnswers : new HashMap<>();
        this.flagged = newFlagged != null ? newFlagged : new HashSet<>();
        this.currentPosition = newCurrentPosition;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NavViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam_question_nav, parent, false);
        if (fillCell) {
            //Trong lưới: ô giãn hết chiều rộng của cột để các ô nằm đều nhau
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return new NavViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NavViewHolder holder, int position) {
        ExamQuestionItem question = questions.get(position);
        holder.tvNumber.setText(String.valueOf(position + 1));

        @ColorRes int backgroundColor;
        @ColorRes int textColor;
        if (position == currentPosition) {
            backgroundColor = R.color.lori_primary;
            textColor = R.color.lori_on_primary;
        } else if (flagged.contains(question.questionId)) {
            backgroundColor = R.color.exam_question_flagged;
            textColor = R.color.exam_question_flagged_text;
        } else if (answers.containsKey(question.questionId)) {
            backgroundColor = R.color.exam_question_answered;
            textColor = R.color.white;
        } else {
            backgroundColor = R.color.lori_surface_container_high;
            textColor = R.color.lori_on_surface_variant;
        }
        holder.tvNumber.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.getContext(), backgroundColor)));
        holder.tvNumber.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), textColor));

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onQuestionClick(adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    //viewholder giữ ô số của một câu
    static class NavViewHolder extends RecyclerView.ViewHolder {
        final TextView tvNumber;

        NavViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tvQuestionNumber);
        }
    }
}