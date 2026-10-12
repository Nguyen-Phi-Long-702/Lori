package com.example.lori;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.lori.utils.ThemeHelper;
import com.example.lori.viewmodel.PremiumPurchaseViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

//Màn mua Premium: 3 gói + nút Thanh toán (mua giả lập, $0). Mua xong hiện khối "Chào mừng Premium!"
public class PremiumPurchaseActivity extends AppCompatActivity {

    private PremiumPurchaseViewModel viewModel;

    private MaterialCardView cardMonthly;
    private MaterialCardView cardYearly;
    private MaterialCardView cardLifetime;
    private MaterialButton btnPay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Áp dụng giao diện tối đã lưu, giống các activity khác
        ThemeHelper.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_premium_purchase);

        //Chừa chỗ cho thanh trạng thái và thanh điều hướng hệ thống
        View root = findViewById(R.id.premiumRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(PremiumPurchaseViewModel.class);

        cardMonthly = findViewById(R.id.cardPlanMonthly);
        cardYearly = findViewById(R.id.cardPlanYearly);
        cardLifetime = findViewById(R.id.cardPlanLifetime);
        btnPay = findViewById(R.id.btnPremiumPay);
        LinearProgressIndicator progress = findViewById(R.id.progressPremium);
        TextView tvError = findViewById(R.id.tvPremiumError);
        TextView tvOffline = findViewById(R.id.tvPremiumOffline);
        View layoutPurchase = findViewById(R.id.layoutPurchase);
        View layoutSuccess = findViewById(R.id.layoutPurchaseSuccess);

        findViewById(R.id.btnBackPremium).setOnClickListener(v -> finish());
        //Quay lại màn trước đó (Cài đặt / Hồ sơ / Kiểm tra...), nơi sẽ tự cập nhật theo trạng thái Premium mới
        findViewById(R.id.btnPremiumDone).setOnClickListener(v -> finish());

        cardMonthly.setOnClickListener(v -> viewModel.selectPlan(PremiumPurchaseViewModel.PLAN_MONTHLY));
        cardYearly.setOnClickListener(v -> viewModel.selectPlan(PremiumPurchaseViewModel.PLAN_YEARLY));
        cardLifetime.setOnClickListener(v -> viewModel.selectPlan(PremiumPurchaseViewModel.PLAN_LIFETIME));
        btnPay.setOnClickListener(v -> viewModel.purchase());

        viewModel.getSelectedPlan().observe(this, this::renderSelectedPlan);
        viewModel.getLoading().observe(this, loading ->
                progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.INVISIBLE));
        viewModel.getPurchaseEnabled().observe(this, enabled -> {
            boolean canPurchase = Boolean.TRUE.equals(enabled);
            btnPay.setEnabled(canPurchase);
            //Nút dùng màu nền cố định nên làm mờ để thể hiện đang bị khóa
            btnPay.setAlpha(canPurchase ? 1f : 0.5f);
        });
        viewModel.getOnline().observe(this, online ->
                tvOffline.setVisibility(Boolean.TRUE.equals(online) ? View.GONE : View.VISIBLE));
        viewModel.getErrorMessage().observe(this, messageRes -> {
            if (messageRes == null) {
                tvError.setVisibility(View.GONE);
            } else {
                tvError.setText(messageRes);
                tvError.setVisibility(View.VISIBLE);
            }
        });
        viewModel.getPurchased().observe(this, purchased -> {
            boolean done = Boolean.TRUE.equals(purchased);
            layoutPurchase.setVisibility(done ? View.GONE : View.VISIBLE);
            layoutSuccess.setVisibility(done ? View.VISIBLE : View.GONE);
        });
    }

    //Tô viền vàng cho gói đang chọn, các gói còn lại viền xám; đổi giá trên nút Thanh toán theo gói
    private void renderSelectedPlan(String plan) {
        int goldColor = ContextCompat.getColor(this, R.color.premium_badge_gradient_start);
        int neutralColor = MaterialColors.getColor(cardMonthly, com.google.android.material.R.attr.colorOutlineVariant);

        cardMonthly.setStrokeColor(PremiumPurchaseViewModel.PLAN_MONTHLY.equals(plan) ? goldColor : neutralColor);
        cardYearly.setStrokeColor(PremiumPurchaseViewModel.PLAN_YEARLY.equals(plan) ? goldColor : neutralColor);
        cardLifetime.setStrokeColor(PremiumPurchaseViewModel.PLAN_LIFETIME.equals(plan) ? goldColor : neutralColor);

        int priceRes;
        if (PremiumPurchaseViewModel.PLAN_MONTHLY.equals(plan)) {
            priceRes = R.string.premium_plan_monthly_price;
        } else if (PremiumPurchaseViewModel.PLAN_LIFETIME.equals(plan)) {
            priceRes = R.string.premium_plan_lifetime_price;
        } else {
            priceRes = R.string.premium_plan_yearly_price;
        }
        btnPay.setText(getString(R.string.premium_pay_button_format, getString(priceRes)));
    }
}