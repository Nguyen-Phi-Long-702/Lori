package com.example.lori.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiImageResponse;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.Objects;

//Adapter danh sách từ nhận diện được trong ảnh. Dòng đang chọn có viền màu chính; bấm dòng để chọn, bấm loa để nghe
public class ImageWordAdapter extends ListAdapter<AiImageResponse.ImageObject, ImageWordAdapter.WordViewHolder> {

    public interface Listener {
        void onSelect(int position);

        void onSpeak(String word);
    }

    private static final DiffUtil.ItemCallback<AiImageResponse.ImageObject> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AiImageResponse.ImageObject>() {
                @Override
                public boolean areItemsTheSame(@NonNull AiImageResponse.ImageObject oldItem,
                                               @NonNull AiImageResponse.ImageObject newItem) {
                    return Objects.equals(oldItem.word, newItem.word);
                }

                @Override
                public boolean areContentsTheSame(@NonNull AiImageResponse.ImageObject oldItem,
                                                  @NonNull AiImageResponse.ImageObject newItem) {
                    return oldItem.confidence == newItem.confidence
                            && Objects.equals(oldItem.ipa, newItem.ipa)
                            && Objects.equals(oldItem.meaning, newItem.meaning);
                }
            };

    private final Listener listener;
    private int selectedIndex;

    public ImageWordAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    //Đổi dòng được chọn và vẽ lại đúng hai dòng bị ảnh hưởng
    public void setSelectedIndex(int index) {
        int old = selectedIndex;
        selectedIndex = index;
        if (old < getItemCount()) {
            notifyItemChanged(old);
        }
        if (index < getItemCount()) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        AiImageResponse.ImageObject item = getItem(position);
        Context context = holder.itemView.getContext();

        holder.tvWord.setText(item.word);
        boolean hasIpa = item.ipa != null && !item.ipa.trim().isEmpty();
        holder.tvIpa.setVisibility(hasIpa ? View.VISIBLE : View.GONE);
        holder.tvIpa.setText(item.ipa);
        holder.tvMeaning.setText(item.meaning);
        holder.tvConfidence.setText(context.getString(R.string.image_confidence_format,
                Math.round(item.confidence * 100)));

        boolean selected = position == selectedIndex;
        float density = context.getResources().getDisplayMetrics().density;
        holder.card.setStrokeWidth((int) ((selected ? 2 : 1) * density));
        holder.card.setStrokeColor(MaterialColors.getColor(holder.card, selected
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOutlineVariant));

        holder.card.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener.onSelect(adapterPosition);
            }
        });
        holder.btnSpeak.setOnClickListener(v -> listener.onSpeak(item.word));
    }

    //viewholder giữ các thành phần giao diện của một dòng
    static class WordViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView tvWord;
        final TextView tvIpa;
        final TextView tvMeaning;
        final TextView tvConfidence;
        final ImageButton btnSpeak;

        WordViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardImageWord);
            tvWord = itemView.findViewById(R.id.tvImageWord);
            tvIpa = itemView.findViewById(R.id.tvImageIpa);
            tvMeaning = itemView.findViewById(R.id.tvImageMeaning);
            tvConfidence = itemView.findViewById(R.id.tvImageConfidence);
            btnSpeak = itemView.findViewById(R.id.btnImageSpeak);
        }
    }
}