package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.DictionaryWord;

import java.util.ArrayList;
import java.util.List;

//Adapter hiển thị danh sách kết quả tìm kiếm từ điển
public class DictionaryAdapter extends RecyclerView.Adapter<DictionaryAdapter.DictionaryViewHolder> {
    //Callback để fragment xử lý khi người dùng chọn một từ
    public interface OnWordClickListener {
        void onWordClick(DictionaryWord word);
    }

    private List<DictionaryWord> results = new ArrayList<>();
    private final OnWordClickListener listener;

    public DictionaryAdapter(OnWordClickListener listener) {
        this.listener = listener;
    }

    //Cập nhật danh sách kết quả và refresh recyclerview
    public void setResults(List<DictionaryWord> newResults) {
        this.results = newResults != null ? newResults : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DictionaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dictionary_word, parent, false);
        return new DictionaryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DictionaryViewHolder holder, int position) {
        DictionaryWord word = results.get(position);
        holder.tvWord.setText(word.word);
        //Trả sự kiện chọn từ về fragment thông qua callback
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onWordClick(word);
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    //viewholder giữ các thành phần giao diện của một dòng kết quả
    static class DictionaryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvWord;

        DictionaryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWord = itemView.findViewById(R.id.tvDictionaryWord);
        }
    }
}