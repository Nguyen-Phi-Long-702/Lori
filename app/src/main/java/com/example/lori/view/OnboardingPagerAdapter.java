package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.model.OnboardingSlide;

import java.util.List;

public class OnboardingPagerAdapter extends RecyclerView.Adapter<OnboardingPagerAdapter.SlideViewHolder> {

    private final List<OnboardingSlide> slides;

    public OnboardingPagerAdapter(List<OnboardingSlide> slides) {
        this.slides = slides;
    }

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_slide, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        OnboardingSlide slide = slides.get(position);
        holder.icon.setImageResource(slide.getIconRes());
        holder.title.setText(slide.getTitleRes());
        holder.desc.setText(slide.getDescRes());
    }

    @Override
    public int getItemCount() {
        return slides.size();
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView desc;

        SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgSlideIcon);
            title = itemView.findViewById(R.id.tvSlideTitle);
            desc = itemView.findViewById(R.id.tvSlideDesc);
        }
    }
}