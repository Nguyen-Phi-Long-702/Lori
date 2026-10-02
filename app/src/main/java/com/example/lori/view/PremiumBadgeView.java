package com.example.lori.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import com.example.lori.R;

public class PremiumBadgeView extends LinearLayout {

    public PremiumBadgeView(Context context) {
        super(context);
        init();
    }

    public PremiumBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PremiumBadgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_premium_badge);
        int paddingH = (int) (10 * getResources().getDisplayMetrics().density);
        int paddingV = (int) (3 * getResources().getDisplayMetrics().density);
        setPadding(paddingH, paddingV, paddingH, paddingV);
        LayoutInflater.from(getContext()).inflate(R.layout.view_premium_badge, this, true);
    }
}