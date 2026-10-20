package com.example.lori.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.remote.dto.LeaderboardEntryResponse;

import java.util.ArrayList;
import java.util.List;

//Adapter một phần tử duy nhất: bục Top 3. Nối vào đầu danh sách bằng ConcatAdapter để bục cuộn cùng các dòng bên dưới
public class LeaderboardPodiumAdapter extends RecyclerView.Adapter<LeaderboardPodiumAdapter.PodiumViewHolder> {

    private List<LeaderboardEntryResponse> podium = new ArrayList<>();

    //Cập nhật Top 3 (tối đa 3 người); null hoặc rỗng thì ẩn cả bục
    public void setPodium(@Nullable List<LeaderboardEntryResponse> newPodium) {
        podium = newPodium != null ? newPodium : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PodiumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_podium, parent, false);
        return new PodiumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PodiumViewHolder holder, int position) {
        bindSlot(holder.slotFirst, holder.avatarFirst, holder.tvNameFirst, holder.tvBarFirst,
                entryAt(0), R.color.leaderboard_gold);
        bindSlot(holder.slotSecond, holder.avatarSecond, holder.tvNameSecond, holder.tvBarSecond,
                entryAt(1), R.color.leaderboard_silver);
        bindSlot(holder.slotThird, holder.avatarThird, holder.tvNameThird, holder.tvBarThird,
                entryAt(2), R.color.leaderboard_bronze);
    }

    @Override
    public int getItemCount() {
        return podium.isEmpty() ? 0 : 1;
    }

    @Nullable
    private LeaderboardEntryResponse entryAt(int index) {
        return index < podium.size() ? podium.get(index) : null;
    }

    //Chưa đủ 3 người thì ô còn thiếu để invisible, giữ nguyên vị trí các ô còn lại
    private static void bindSlot(View slot, AvatarView avatar, TextView tvName, TextView tvBar,
                                 @Nullable LeaderboardEntryResponse entry, @ColorRes int barColor) {
        if (entry == null) {
            slot.setVisibility(View.INVISIBLE);
            return;
        }
        slot.setVisibility(View.VISIBLE);
        Context context = slot.getContext();
        avatar.bind(entry.displayName, entry.avatarUrl);
        tvName.setText(entry.displayName);
        tvBar.setText(String.valueOf(entry.rank));
        //Nền cột là shape màu trắng, tô màu theo hạng
        tvBar.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, barColor)));
    }

    //viewholder giữ các thành phần giao diện của bục
    static class PodiumViewHolder extends RecyclerView.ViewHolder {
        final View slotFirst;
        final View slotSecond;
        final View slotThird;
        final AvatarView avatarFirst;
        final AvatarView avatarSecond;
        final AvatarView avatarThird;
        final TextView tvNameFirst;
        final TextView tvNameSecond;
        final TextView tvNameThird;
        final TextView tvBarFirst;
        final TextView tvBarSecond;
        final TextView tvBarThird;

        PodiumViewHolder(@NonNull View itemView) {
            super(itemView);
            slotFirst = itemView.findViewById(R.id.slotFirst);
            slotSecond = itemView.findViewById(R.id.slotSecond);
            slotThird = itemView.findViewById(R.id.slotThird);
            avatarFirst = itemView.findViewById(R.id.avatarFirst);
            avatarSecond = itemView.findViewById(R.id.avatarSecond);
            avatarThird = itemView.findViewById(R.id.avatarThird);
            tvNameFirst = itemView.findViewById(R.id.tvNameFirst);
            tvNameSecond = itemView.findViewById(R.id.tvNameSecond);
            tvNameThird = itemView.findViewById(R.id.tvNameThird);
            tvBarFirst = itemView.findViewById(R.id.tvBarFirst);
            tvBarSecond = itemView.findViewById(R.id.tvBarSecond);
            tvBarThird = itemView.findViewById(R.id.tvBarThird);
        }
    }
}