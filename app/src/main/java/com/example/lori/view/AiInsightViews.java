package com.example.lori.view;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiRecommendation;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.function.IntConsumer;

//Các thẻ dùng chung của những màn AI có điểm yếu và đề xuất ôn tập (Ôn tập hôm nay, Phân tích AI, Hồ sơ năng lực)
final class AiInsightViews {

    private AiInsightViews() {
    }

    //Thêm một dòng điểm yếu: tên, nhãn bên phải, thanh theo percent (0-100). onClick = null thì dòng không bấm được
    static void addWeakRow(@NonNull LinearLayout container, String name, String label, int percent,
                           @Nullable Runnable onClick) {
        View row = LayoutInflater.from(container.getContext()).inflate(R.layout.item_ai_weak_row, container, false);
        ((TextView) row.findViewById(R.id.tvWeakName)).setText(name);
        ((TextView) row.findViewById(R.id.tvWeakLabel)).setText(label);
        ((LinearProgressIndicator) row.findViewById(R.id.progressWeak)).setProgressCompat(percent, false);
        if (onClick != null) {
            row.findViewById(R.id.ivWeakChevron).setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> onClick.run());
        }
        container.addView(row);
    }

    //Thay toàn bộ nội dung container bằng các thẻ đề xuất. Thẻ có lessonId thì bấm được và gọi onOpenLesson(lessonId)
    static void bindRecommendations(@NonNull LinearLayout container, @Nullable List<AiRecommendation> items,
                                    @NonNull IntConsumer onOpenLesson) {
        container.removeAllViews();
        if (items == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int i = 0; i < items.size(); i++) {
            AiRecommendation item = items.get(i);
            View card = inflater.inflate(R.layout.item_ai_recommendation, container, false);
            ((TextView) card.findViewById(R.id.tvRecPriority)).setText(String.valueOf(i + 1));
            ((TextView) card.findViewById(R.id.tvRecTitle)).setText(item.title);
            ((TextView) card.findViewById(R.id.tvRecReason)).setText(item.reason);
            if (item.lessonId != null) {
                int lessonId = item.lessonId;
                card.findViewById(R.id.ivRecChevron).setVisibility(View.VISIBLE);
                card.setOnClickListener(v -> onOpenLesson.accept(lessonId));
            }
            container.addView(card);
        }
    }
}