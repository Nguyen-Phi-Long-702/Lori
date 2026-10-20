package com.example.lori.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.remote.dto.LeaderboardEntryResponse;
import com.example.lori.utils.ExpFormatter;

import java.util.Objects;

//Adapter hiển thị các dòng từ hạng 4 trở đi. DiffUtil nên dòng mới nối thêm khi tải trang kế sẽ hiện dần (fade-in)
public class LeaderboardAdapter extends ListAdapter<LeaderboardEntryResponse, LeaderboardAdapter.RowViewHolder> {

    private static final DiffUtil.ItemCallback<LeaderboardEntryResponse> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<LeaderboardEntryResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull LeaderboardEntryResponse oldItem,
                                               @NonNull LeaderboardEntryResponse newItem) {
                    return oldItem.rank == newItem.rank;
                }

                @Override
                public boolean areContentsTheSame(@NonNull LeaderboardEntryResponse oldItem,
                                                  @NonNull LeaderboardEntryResponse newItem) {
                    return oldItem.score == newItem.score
                            && Objects.equals(oldItem.displayName, newItem.displayName)
                            && Objects.equals(oldItem.avatarUrl, newItem.avatarUrl);
                }
            };

    public LeaderboardAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_row, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        LeaderboardEntryResponse entry = getItem(position);
        Context context = holder.itemView.getContext();

        holder.tvRank.setText(context.getString(R.string.leaderboard_rank_format, entry.rank));
        holder.avatar.bind(entry.displayName, entry.avatarUrl);
        holder.tvName.setText(entry.displayName);
        holder.tvScore.setText(context.getString(R.string.leaderboard_score_format,
                ExpFormatter.format(entry.score)));
    }

    //viewholder giữ các thành phần giao diện của một dòng
    static class RowViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final AvatarView avatar;
        final TextView tvName;
        final TextView tvScore;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRowRank);
            avatar = itemView.findViewById(R.id.avatarRow);
            tvName = itemView.findViewById(R.id.tvRowName);
            tvScore = itemView.findViewById(R.id.tvRowScore);
        }
    }
}