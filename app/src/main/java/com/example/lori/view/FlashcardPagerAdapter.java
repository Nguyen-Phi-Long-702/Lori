package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.TopicWord;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

//Adapter quản lý các flashcard hiển thị trong viewpager2
public class FlashcardPagerAdapter extends RecyclerView.Adapter<FlashcardPagerAdapter.CardViewHolder> {

    //Callback để fragment xử lý việc phát âm từ vựng
    public interface OnSpeakClickListener {
        void onSpeak(String word);
    }

    private List<TopicWord> words = new ArrayList<>();
    private final OnSpeakClickListener speakClickListener;

    public FlashcardPagerAdapter(OnSpeakClickListener speakClickListener) {
        this.speakClickListener = speakClickListener;
    }

    //Cập nhật danh sách từ vựng và yêu cầu recyclerview vẽ lại các item
    public void setWords(List<TopicWord> newWords) {
        this.words = newWords != null ? newWords : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard, parent, false);
        return new CardViewHolder(view, speakClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        holder.bind(words.get(position));
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    //viewholder quản lý giao diện và trạng thái của từng flashcard
    static class CardViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardFront;
        private final MaterialCardView cardBack;
        private final TextView tvWordFront;
        private final TextView tvPronunciationFront;
        private final TextView tvMeaningBack;
        private final TextView tvWordBack;
        private final ImageButton btnSpeakFront;

        CardViewHolder(@NonNull View itemView, OnSpeakClickListener speakClickListener) {
            super(itemView);
            cardFront = itemView.findViewById(R.id.cardFront);
            cardBack = itemView.findViewById(R.id.cardBack);
            tvWordFront = itemView.findViewById(R.id.tvWordFront);
            tvPronunciationFront = itemView.findViewById(R.id.tvPronunciationFront);
            tvMeaningBack = itemView.findViewById(R.id.tvMeaningBack);
            tvWordBack = itemView.findViewById(R.id.tvWordBack);
            btnSpeakFront = itemView.findViewById(R.id.btnSpeakFront);

            //Tăng khoảng cách camera để hiệu ứng lật 3D không bị méo
            float distance = 8000 * itemView.getResources().getDisplayMetrics().density;
            cardFront.setCameraDistance(distance);
            cardBack.setCameraDistance(distance);

            View.OnClickListener flipListener = v -> flip();
            cardFront.setOnClickListener(flipListener);
            cardBack.setOnClickListener(flipListener);

            //Chuyển yêu cầu phát âm về fragment thông qua callback
            btnSpeakFront.setOnClickListener(v -> {
                if (speakClickListener != null && tvWordFront.getText() != null) {
                    speakClickListener.onSpeak(tvWordFront.getText().toString());
                }
            });
        }

        //Gán dữ liệu từ vựng và đưa flashcard về mặt trước trước khi tái sử dụng
        void bind(TopicWord word) {
            cardFront.animate().cancel();
            cardBack.animate().cancel();

            cardFront.setRotationY(0f);
            cardFront.setVisibility(View.VISIBLE);
            cardBack.setRotationY(0f);
            cardBack.setVisibility(View.INVISIBLE);

            tvWordFront.setText(word.word);
            tvWordBack.setText(word.word);
            tvMeaningBack.setText(word.meaning);

            if (word.pronunciation != null && !word.pronunciation.trim().isEmpty()) {
                tvPronunciationFront.setVisibility(View.VISIBLE);
                tvPronunciationFront.setText(word.pronunciation);
            } else {
                tvPronunciationFront.setVisibility(View.GONE);
            }
        }

        //Thực hiện hiệu ứng lật giữa mặt trước và mặt sau của flashcard
        private void flip() {
            boolean frontVisible = cardFront.getVisibility() == View.VISIBLE;
            MaterialCardView viewOut = frontVisible ? cardFront : cardBack;
            MaterialCardView viewIn = frontVisible ? cardBack : cardFront;

            viewOut.animate()
                    .rotationY(90f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        viewOut.setVisibility(View.INVISIBLE);
                        viewIn.setRotationY(-90f);
                        viewIn.setVisibility(View.VISIBLE);
                        viewIn.animate()
                                .rotationY(0f)
                                .setDuration(150)
                                .start();
                    })
                    .start();
        }
    }
}