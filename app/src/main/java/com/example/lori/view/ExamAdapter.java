package com.example.lori.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.remote.dto.ExamSummaryResponse;

import java.util.ArrayList;
import java.util.List;

//Adapter hiển thị danh sách đề thi TOEIC/IELTS
public class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ExamViewHolder> {

    //Giá trị examType / difficulty Backend trả về (CHECK trong V2__exam_tables.sql)
    private static final String TYPE_IELTS = "IELTS";
    private static final String DIFFICULTY_EASY = "EASY";
    private static final String DIFFICULTY_MEDIUM = "MEDIUM";
    private static final String DIFFICULTY_HARD = "HARD";

    //Callback để fragment xử lý khi người dùng bấm "Bắt đầu làm bài"
    public interface OnExamStartListener {
        void onExamStart(ExamSummaryResponse exam);
    }

    private List<ExamSummaryResponse> exams = new ArrayList<>();
    private final OnExamStartListener listener;

    public ExamAdapter(OnExamStartListener listener) {
        this.listener = listener;
    }

    //Cập nhật danh sách đề và refresh recyclerview
    public void setExams(List<ExamSummaryResponse> newExams) {
        this.exams = newExams != null ? newExams : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        ExamSummaryResponse exam = exams.get(position);
        Context context = holder.itemView.getContext();

        holder.tvExamType.setText(exam.examType);
        //Nền badge là shape màu trắng, tô màu theo loại đề
        int badgeColor = TYPE_IELTS.equals(exam.examType) ? R.color.exam_badge_ielts : R.color.exam_badge_toeic;
        holder.tvExamType.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, badgeColor)));

        holder.tvExamTitle.setText(exam.title);
        holder.tvExamMeta.setText(context.getString(R.string.exam_meta_format,
                exam.totalQuestions, exam.durationMinutes, difficultyLabel(context, exam.difficulty)));

        //Trả sự kiện bấm nút về fragment thông qua callback
        holder.btnExamStart.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExamStart(exam);
            }
        });
    }

    @Override
    public int getItemCount() {
        return exams.size();
    }

    //Đổi EASY/MEDIUM/HARD thành nhãn tiếng Việt kèm chấm màu (theo prototype)
    private static String difficultyLabel(Context context, String difficulty) {
        if (DIFFICULTY_EASY.equals(difficulty)) {
            return context.getString(R.string.exam_difficulty_easy);
        }
        if (DIFFICULTY_MEDIUM.equals(difficulty)) {
            return context.getString(R.string.exam_difficulty_medium);
        }
        if (DIFFICULTY_HARD.equals(difficulty)) {
            return context.getString(R.string.exam_difficulty_hard);
        }
        return difficulty != null ? difficulty : "";
    }

    //viewholder giữ các thành phần giao diện của một thẻ đề thi
    static class ExamViewHolder extends RecyclerView.ViewHolder {
        final TextView tvExamType;
        final TextView tvExamTitle;
        final TextView tvExamMeta;
        final View btnExamStart;

        ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExamType = itemView.findViewById(R.id.tvExamType);
            tvExamTitle = itemView.findViewById(R.id.tvExamTitle);
            tvExamMeta = itemView.findViewById(R.id.tvExamMeta);
            btnExamStart = itemView.findViewById(R.id.btnExamStart);
        }
    }
}