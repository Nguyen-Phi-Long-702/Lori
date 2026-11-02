package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.model.ConversationMode;

import java.util.List;

//Lưới các tình huống hội thoại: mỗi thẻ có icon và tên; bấm thẻ thì báo ra ngoài qua OnModeClickListener
public class AiConversationModeAdapter extends RecyclerView.Adapter<AiConversationModeAdapter.ModeViewHolder> {

    public interface OnModeClickListener {
        void onModeClick(ConversationMode mode);
    }

    private final List<ConversationMode> modes;
    private final OnModeClickListener listener;

    public AiConversationModeAdapter(List<ConversationMode> modes, OnModeClickListener listener) {
        this.modes = modes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ModeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation_mode, parent, false);
        return new ModeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModeViewHolder holder, int position) {
        ConversationMode mode = modes.get(position);
        holder.ivIcon.setImageResource(mode.getIconRes());
        holder.tvTitle.setText(mode.getTitleRes());
        holder.itemView.setOnClickListener(v -> listener.onModeClick(mode));
    }

    @Override
    public int getItemCount() {
        return modes.size();
    }

    static class ModeViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;

        ModeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivConversationModeIcon);
            tvTitle = itemView.findViewById(R.id.tvConversationModeTitle);
        }
    }
}