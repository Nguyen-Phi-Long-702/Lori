package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;

//Adapter một phần tử ở cuối danh sách: vòng xoay khi đang tải thêm trang, hoặc dòng báo lỗi chạm để thử lại
public class LeaderboardFooterAdapter extends RecyclerView.Adapter<LeaderboardFooterAdapter.FooterViewHolder> {

    public static final int STATE_HIDDEN = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_ERROR = 2;

    //Callback để fragment xử lý khi người dùng chạm dòng báo lỗi
    public interface OnRetryListener {
        void onRetry();
    }

    private final OnRetryListener listener;
    private int state = STATE_HIDDEN;

    public LeaderboardFooterAdapter(OnRetryListener listener) {
        this.listener = listener;
    }

    public void setState(int newState) {
        if (newState == state) {
            return;
        }
        state = newState;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FooterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_footer, parent, false);
        return new FooterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FooterViewHolder holder, int position) {
        holder.progress.setVisibility(state == STATE_LOADING ? View.VISIBLE : View.GONE);
        holder.tvError.setVisibility(state == STATE_ERROR ? View.VISIBLE : View.GONE);
        holder.tvError.setOnClickListener(v -> listener.onRetry());
    }

    @Override
    public int getItemCount() {
        return state == STATE_HIDDEN ? 0 : 1;
    }

    //viewholder giữ các thành phần giao diện của chân danh sách
    static class FooterViewHolder extends RecyclerView.ViewHolder {
        final View progress;
        final View tvError;

        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            progress = itemView.findViewById(R.id.progressLeaderboardMore);
            tvError = itemView.findViewById(R.id.tvLeaderboardMoreError);
        }
    }
}