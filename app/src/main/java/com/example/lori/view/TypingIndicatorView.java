package com.example.lori.view;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.example.lori.R;

import java.util.ArrayList;
import java.util.List;

//Ba chấm nhảy lần lượt, báo AI đang soạn câu trả lời. Chỉ chạy animation khi đang gắn vào cửa sổ
public class TypingIndicatorView extends LinearLayout {

    private static final int DOT_COUNT = 3;
    private static final int DOT_SIZE_DP = 7;
    private static final int DOT_GAP_DP = 3;
    private static final int BOUNCE_HEIGHT_DP = 5;
    private static final long BOUNCE_DURATION_MS = 700;
    private static final long DOT_DELAY_MS = 150;

    private final List<ObjectAnimator> animators = new ArrayList<>();

    public TypingIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(DOT_SIZE_DP * density);
        int gap = Math.round(DOT_GAP_DP * density);
        for (int i = 0; i < DOT_COUNT; i++) {
            View dot = new View(context);
            dot.setBackgroundResource(R.drawable.bg_chat_dot);
            LayoutParams params = new LayoutParams(size, size);
            params.setMargins(gap, 0, gap, 0);
            addView(dot, params);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        float height = BOUNCE_HEIGHT_DP * getResources().getDisplayMetrics().density;
        for (int i = 0; i < getChildCount(); i++) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(getChildAt(i), View.TRANSLATION_Y, 0f, -height, 0f);
            animator.setDuration(BOUNCE_DURATION_MS);
            animator.setStartDelay(i * DOT_DELAY_MS);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.start();
            animators.add(animator);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        for (ObjectAnimator animator : animators) {
            animator.cancel();
        }
        animators.clear();
        super.onDetachedFromWindow();
    }
}