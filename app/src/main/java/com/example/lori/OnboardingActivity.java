package com.example.lori;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lori.model.OnboardingSlide;
import com.example.lori.view.OnboardingPagerAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout dotsContainer;
    private MaterialButton btnNext;
    private int slideCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        View root = findViewById(R.id.onboardingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        viewPager = findViewById(R.id.viewPagerOnboarding);
        dotsContainer = findViewById(R.id.dotsContainer);
        btnNext = findViewById(R.id.btnNext);
        TextView tvSkip = findViewById(R.id.tvSkip);

        List<OnboardingSlide> slides = buildSlides();
        slideCount = slides.size();
        viewPager.setAdapter(new OnboardingPagerAdapter(slides));

        buildDots();
        updateDots(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
            }
        });

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < slideCount - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });

        tvSkip.setOnClickListener(v -> finishOnboarding());
    }

    private List<OnboardingSlide> buildSlides() {
        List<OnboardingSlide> list = new ArrayList<>();
        list.add(new OnboardingSlide(R.drawable.ic_ob_topics, R.string.onboarding_title_1, R.string.onboarding_desc_1));
        list.add(new OnboardingSlide(R.drawable.ic_ob_dictionary, R.string.onboarding_title_2, R.string.onboarding_desc_2));
        list.add(new OnboardingSlide(R.drawable.ic_ob_premium, R.string.onboarding_title_3, R.string.onboarding_desc_3));
        list.add(new OnboardingSlide(R.drawable.ic_ob_security, R.string.onboarding_title_4, R.string.onboarding_desc_4));
        return list;
    }

    private void buildDots() {
        dotsContainer.removeAllViews();
        int sizePx = dpToPx(8);
        int marginPx = dpToPx(3);
        for (int i = 0; i < slideCount; i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(params);
            dotsContainer.addView(dot);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateDots(int selected) {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            ImageView dot = (ImageView) dotsContainer.getChildAt(i);
            dot.setImageResource(i == selected ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences(SplashActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(SplashActivity.KEY_IS_FIRST_LAUNCH, false).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}