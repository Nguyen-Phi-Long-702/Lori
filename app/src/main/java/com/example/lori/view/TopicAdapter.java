package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.Topic;

import java.util.ArrayList;
import java.util.List;

//Adapter hiển thị danh sách chủ đề học tập
public class TopicAdapter extends RecyclerView.Adapter<TopicAdapter.TopicViewHolder> {

    //Callback để fragment xử lý khi người dùng chọn một chủ đề
    public interface OnTopicClickListener {
        void onTopicClick(Topic topic);
    }

    private List<Topic> topics = new ArrayList<>();
    private final OnTopicClickListener listener;

    public TopicAdapter(OnTopicClickListener listener) {
        this.listener = listener;
    }

    //Cập nhật danh sách chủ đề và refresh recyclerview
    public void setTopics(List<Topic> newTopics) {
        this.topics = newTopics != null ? newTopics : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_topic, parent, false);
        return new TopicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {
        Topic topic = topics.get(position);
        holder.tvTopicName.setText(topic.nameVi);
        holder.tvTopicWordCount.setText(holder.itemView.getContext()
                .getString(R.string.topic_word_count_format, topic.wordCount));
        //Trả sự kiện chọn chủ đề về fragment thông qua callback
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTopicClick(topic);
            }
        });
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    //viewholder giữ các thành phần giao diện của một chủ đề
    static class TopicViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTopicName;
        final TextView tvTopicWordCount;

        TopicViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTopicName = itemView.findViewById(R.id.tvTopicName);
            tvTopicWordCount = itemView.findViewById(R.id.tvTopicWordCount);
        }
    }
}