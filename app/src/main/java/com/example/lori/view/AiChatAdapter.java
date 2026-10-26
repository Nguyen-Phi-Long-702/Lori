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
import com.example.lori.viewmodel.ChatItem;

import io.noties.markwon.Markwon;

//Danh sách chat: bong bóng của user, bong bóng của AI (render Markdown) và bong bóng "đang trả lời"
public class AiChatAdapter extends ListAdapter<ChatItem, RecyclerView.ViewHolder> {

    private static final DiffUtil.ItemCallback<ChatItem> DIFF = new DiffUtil.ItemCallback<ChatItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            return oldItem.type == newItem.type
                    && oldItem.truncated == newItem.truncated
                    && oldItem.text.equals(newItem.text);
        }
    };

    private final Markwon markwon;

    public AiChatAdapter(Context context) {
        super(DIFF);
        markwon = Markwon.create(context);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case ChatItem.TYPE_USER:
                return new UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
            case ChatItem.TYPE_BOT:
                return new BotViewHolder(inflater.inflate(R.layout.item_chat_bot, parent, false));
            default:
                return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_chat_typing, parent, false)) { };
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = getItem(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvText.setText(item.text);
        } else if (holder instanceof BotViewHolder) {
            BotViewHolder bot = (BotViewHolder) holder;
            markwon.setMarkdown(bot.tvText, item.text);
            bot.tvTruncated.setVisibility(item.truncated ? View.VISIBLE : View.GONE);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView tvText;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvChatUserText);
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        final TextView tvText;
        final TextView tvTruncated;

        BotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvChatBotText);
            tvTruncated = itemView.findViewById(R.id.tvChatBotTruncated);
        }
    }
}