package com.example.lori.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.GrammarLesson;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//Adapter hiển thị danh sách bài học ngữ pháp
public class GrammarAdapter extends RecyclerView.Adapter<GrammarAdapter.GrammarViewHolder> {

    //Callback để fragment xử lý khi người dùng chọn một bài học
    public interface OnLessonClickListener {
        void onLessonClick(GrammarLesson lesson);
    }

    private List<GrammarLesson> lessons = new ArrayList<>();
    private Set<Integer> completedLessonIds = new HashSet<>();
    private final OnLessonClickListener listener;

    public GrammarAdapter(OnLessonClickListener listener) {
        this.listener = listener;
    }

    //Cập nhật danh sách bài học và refresh recyclerview
    public void setLessons(List<GrammarLesson> newLessons) {
        this.lessons = newLessons != null ? newLessons : new ArrayList<>();
        notifyDataSetChanged();
    }

    //Cập nhật tập hợp id bài học đã hoàn thành và refresh recyclerview
    public void setCompletedLessonIds(Set<Integer> newIds) {
        this.completedLessonIds = newIds != null ? newIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GrammarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_grammar_lesson, parent, false);
        return new GrammarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GrammarViewHolder holder, int position) {
        GrammarLesson lesson = lessons.get(position);
        Context context = holder.itemView.getContext();
        boolean done = completedLessonIds.contains(lesson.id);

        holder.tvLessonTitle.setText(context.getString(
                R.string.grammar_lesson_item_title_format, lesson.orderIndex, lesson.titleVi));
        holder.tvLessonLevel.setText(levelLabel(context, lesson.level));
        holder.progressLesson.setProgress(done ? 100 : 0);
        holder.tvLessonStatus.setText(done ? "✓" : "—");
        holder.tvLessonStatusLabel.setText(done
                ? R.string.grammar_progress_done
                : R.string.grammar_progress_not_done);

        //Trả sự kiện chọn bài học về fragment thông qua callback
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLessonClick(lesson);
            }
        });
    }

    @Override
    public int getItemCount() {
        return lessons.size();
    }

    //Chuyển mã cấp độ lưu trong db sang nhãn tiếng Việt hiển thị
    private String levelLabel(Context context, String level) {
        if ("beginner".equals(level)) return context.getString(R.string.grammar_level_beginner);
        if ("intermediate".equals(level)) return context.getString(R.string.grammar_level_intermediate);
        if ("advanced".equals(level)) return context.getString(R.string.grammar_level_advanced);
        return level;
    }

    //viewholder giữ các thành phần giao diện của một bài học
    static class GrammarViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLessonTitle;
        final TextView tvLessonLevel;
        final LinearProgressIndicator progressLesson;
        final TextView tvLessonStatus;
        final TextView tvLessonStatusLabel;

        GrammarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLessonTitle = itemView.findViewById(R.id.tvLessonTitle);
            tvLessonLevel = itemView.findViewById(R.id.tvLessonLevel);
            progressLesson = itemView.findViewById(R.id.progressLesson);
            tvLessonStatus = itemView.findViewById(R.id.tvLessonStatus);
            tvLessonStatusLabel = itemView.findViewById(R.id.tvLessonStatusLabel);
        }
    }
}