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
import com.example.lori.data.remote.dto.ExamResultResponse;
import com.example.lori.utils.IsoDateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

//Adapter hiển thị lịch sử các lần thi; bấm một dòng để mở màn Kết quả của lần thi đó
public class ExamResultAdapter extends RecyclerView.Adapter<ExamResultAdapter.ResultViewHolder> {

    private static final String TYPE_IELTS = "IELTS";

    //Callback để fragment xử lý khi người dùng bấm một dòng lịch sử
    public interface OnResultClickListener {
        void onResultClick(ExamResultResponse result);
    }

    private final OnResultClickListener listener;
    private List<ExamResultResponse> results = new ArrayList<>();

    public ExamResultAdapter(OnResultClickListener listener) {
        this.listener = listener;
    }

    //Cập nhật lịch sử và refresh recyclerview
    public void setResults(List<ExamResultResponse> newResults) {
        this.results = newResults != null ? newResults : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        ExamResultResponse result = results.get(position);
        Context context = holder.itemView.getContext();

        holder.tvResultType.setText(result.examType);
        int badgeColor = TYPE_IELTS.equals(result.examType) ? R.color.exam_badge_ielts : R.color.exam_badge_toeic;
        holder.tvResultType.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, badgeColor)));

        holder.tvResultTitle.setText(result.title);
        holder.tvResultMeta.setText(context.getString(R.string.exam_result_meta_format,
                formatDate(result.submittedAt), result.correctCount, result.totalQuestions,
                toMinutes(result.timeSpentSeconds)));
        holder.tvResultScore.setText(formatScore(context, result));
        holder.itemView.setOnClickListener(v -> listener.onResultClick(result));
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    //Chuỗi ISO-8601 UTC của Backend -> ngày theo múi giờ của máy, dạng dd/MM/yyyy
    private static String formatDate(String iso) {
        Date date = IsoDateUtils.parse(iso);
        if (date == null) {
            return "-";
        }
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
    }

    //Làm tròn lên theo phút, tối thiểu 1 phút
    private static int toMinutes(int seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    //TOEIC: điểm tổng 10-990 (số nguyên). IELTS: band 0-9 bước 0.5, luôn dùng dấu chấm (7.5)
    private static String formatScore(Context context, ExamResultResponse result) {
        if (TYPE_IELTS.equals(result.examType)) {
            return context.getString(R.string.exam_result_score_ielts_format,
                    String.format(Locale.US, "%.1f", result.totalScore));
        }
        return context.getString(R.string.exam_result_score_toeic_format, (int) Math.round(result.totalScore));
    }

    //viewholder giữ các thành phần giao diện của một dòng lịch sử
    static class ResultViewHolder extends RecyclerView.ViewHolder {
        final TextView tvResultType;
        final TextView tvResultTitle;
        final TextView tvResultMeta;
        final TextView tvResultScore;

        ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvResultType = itemView.findViewById(R.id.tvResultType);
            tvResultTitle = itemView.findViewById(R.id.tvResultTitle);
            tvResultMeta = itemView.findViewById(R.id.tvResultMeta);
            tvResultScore = itemView.findViewById(R.id.tvResultScore);
        }
    }
}