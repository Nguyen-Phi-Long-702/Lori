package com.example.lori;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.lori.utils.ThemeHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Áp dụng lại giao diện tối đã lưu, phòng trường hợp hệ điều hành khôi phục thẳng mainactivity mà không chạy lại splashactivity
        ThemeHelper.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        //Đảm bảo nội dung không bị che bởi thanh trạng thái và thanh điều hướng hệ thống
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        //Đồng bộ bottomnavigationview với navcontroller cho các màn hình khớp trực tiếp với 1 tab
        NavigationUI.setupWithNavController(bottomNav, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();

            //Ẩn thanh điều hướng dưới ở các màn hình cần tập trung toàn màn hình hoặc đã có nút quay lại riêng
            boolean hideBottomNav = destId == R.id.flashcardFragment
                    || destId == R.id.wordDetailFragment
                    || destId == R.id.grammarDetailFragment
                    || destId == R.id.settingsFragment;
            bottomNav.setVisibility(hideBottomNav ? View.GONE : View.VISIBLE);

            //Tô sáng đúng tab cha cho các màn hình con không khớp trực tiếp với menu item nào
            int parentTabId = resolveParentTabId(destId);
            if (parentTabId != -1) {
                bottomNav.getMenu().findItem(parentTabId).setChecked(true);
            }
        });
    }

    //Ánh xạ một màn hình con về đúng tab cha của nó trên thanh điều hướng dưới
    //Trả về -1 nếu màn hình đã khớp trực tiếp với 1 tab
    private int resolveParentTabId(int destinationId) {
        if (destinationId == R.id.flashcardFragment
                || destinationId == R.id.wordDetailFragment
                || destinationId == R.id.grammarListFragment
                || destinationId == R.id.grammarDetailFragment) {
            return R.id.topicListFragment;
        }
        if (destinationId == R.id.settingsFragment) {
            return R.id.profileFragment;
        }
        return -1;
    }
}