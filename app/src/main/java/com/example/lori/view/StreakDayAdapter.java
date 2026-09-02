package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.viewmodel.ProfileViewModel;

import java.util.ArrayList;
import java.util.List;

//Adapter hiển thị lưới 30 ngày streak học tập trên profilefragment
public class StreakDayAdapter extends RecyclerView.Adapter<StreakDayAdapter.StreakDayViewHolder> {

    private List<ProfileViewModel.StreakDay> days = new ArrayList<>();

    //Cập nhật danh sách ngày và refresh recyclerview
    public void setDays(List<ProfileViewModel.StreakDay> newDays) {
        this.days = newDays != null ? newDays : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StreakDayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_streak_day, parent, false);
        return new StreakDayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StreakDayViewHolder holder, int position) {
        ProfileViewModel.StreakDay day = days.get(position);
        holder.tvDay.setText(day.studied ? "✓" : String.valueOf(day.dayNumber));
        holder.tvDay.setSelected(day.studied);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class StreakDayViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDay;

        StreakDayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvStreakDay);
        }
    }
}