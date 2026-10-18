package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;

//Adapter hai thẻ "Sắp ra mắt" (IELTS Writing, IELTS Speaking) ở cuối danh sách đề của tab IELTS
//Nội dung cố định, không lấy từ Backend; setVisible quyết định có hiện hai thẻ hay không
public class ExamComingSoonAdapter extends RecyclerView.Adapter<ExamComingSoonAdapter.ComingSoonViewHolder> {

    //Ba mảng song song: phần tử i là icon, tên và mô tả của thẻ thứ i
    private static final int[] ICONS = {R.drawable.ic_feature_writing, R.drawable.ic_feature_speaking};
    private static final int[] TITLES = {
            R.string.exam_coming_soon_writing_title, R.string.exam_coming_soon_speaking_title};
    private static final int[] DESCRIPTIONS = {
            R.string.exam_coming_soon_writing_desc, R.string.exam_coming_soon_speaking_desc};

    private boolean visible = false;

    //Hiện hoặc ẩn hai thẻ
    public void setVisible(boolean newVisible) {
        if (visible == newVisible) {
            return;
        }
        visible = newVisible;
        if (visible) {
            notifyItemRangeInserted(0, TITLES.length);
        } else {
            notifyItemRangeRemoved(0, TITLES.length);
        }
    }

    @NonNull
    @Override
    public ComingSoonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exam_coming_soon, parent, false);
        return new ComingSoonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComingSoonViewHolder holder, int position) {
        holder.ivIcon.setImageResource(ICONS[position]);
        holder.tvTitle.setText(TITLES[position]);
        holder.tvDescription.setText(DESCRIPTIONS[position]);
    }

    @Override
    public int getItemCount() {
        return visible ? TITLES.length : 0;
    }

    //viewholder giữ các thành phần giao diện của một thẻ "Sắp ra mắt"
    static class ComingSoonViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvDescription;

        ComingSoonViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivComingSoonIcon);
            tvTitle = itemView.findViewById(R.id.tvComingSoonTitle);
            tvDescription = itemView.findViewById(R.id.tvComingSoonDesc);
        }
    }
}