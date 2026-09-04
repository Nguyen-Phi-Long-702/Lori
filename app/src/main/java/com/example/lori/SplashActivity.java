package com.example.lori;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.lori.utils.ThemeHelper;

//Màn hình khởi động, hiển thị logo trước khi chuyển sang màn hình tiếp theo
public class SplashActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "lori_prefs";
    public static final String KEY_IS_FIRST_LAUNCH = "is_first_launch";

    private static final long ANIM_DURATION_MS = 600L;
    private static final long HOLD_AFTER_ANIM_MS = 500L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        View root = findViewById(R.id.splashRoot);
        //Đảm bảo nội dung splash không bị che bởi các thanh hệ thống
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        playLogoAnimation();
    }

    //Chạy hiệu ứng phóng to và hiện dần logo khi mở ứng dụng
    private void playLogoAnimation() {
        ImageView logo = findViewById(R.id.imgLogo);
        logo.setScaleX(0.7f);
        logo.setScaleY(0.7f);
        logo.setAlpha(0f);

        logo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> new Handler(Looper.getMainLooper())
                        .postDelayed(this::navigateNext, HOLD_AFTER_ANIM_MS))
                .start();
    }

    //Kiểm tra trạng thái lần mở app để chọn màn hình điều hướng tiếp theo
    private void navigateNext() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true);

        Intent intent = isFirstLaunch
                ? new Intent(this, OnboardingActivity.class)
                : new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}