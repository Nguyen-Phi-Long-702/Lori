package com.example.lori;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.lori.utils.ThemeHelper;
import com.example.lori.data.sync.SyncManager;
import com.example.lori.viewmodel.NetworkViewModel;
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
        //Đồng bộ bottomnavigationview với navcontroller
        NavigationUI.setupWithNavController(bottomNav, navController);

        //Bật lịch đồng bộ 30 phút/lần. Vào được MainActivity nghĩa là đã đăng nhập; gọi lại nhiều lần vẫn an toàn (chỉ có 1 lịch)
        SyncManager.schedule(this);

        //Banner offline: chỉ hiện khi máy mất mạng
        TextView bannerOffline = findViewById(R.id.bannerOffline);
        NetworkViewModel networkViewModel = new ViewModelProvider(this).get(NetworkViewModel.class);
        networkViewModel.getOnline().observe(this, online ->
                bannerOffline.setVisibility(Boolean.FALSE.equals(online) ? View.VISIBLE : View.GONE));

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();

            //Ẩn thanh điều hướng dưới ở các màn hình cần tập trung toàn màn hình hoặc đã có nút quay lại riêng
            boolean hideBottomNav = destId == R.id.flashcardFragment
                    || destId == R.id.wordDetailFragment
                    || destId == R.id.grammarDetailFragment
                    || destId == R.id.settingsFragment
                    || destId == R.id.quizFragment
                    || destId == R.id.aiChatFragment;
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
                || destinationId == R.id.grammarDetailFragment
                || destinationId == R.id.quizFragment) {
            return R.id.topicListFragment;
        }
        if (destinationId == R.id.settingsFragment || destinationId == R.id.leaderboardFragment) {
            return R.id.profileFragment;
        }
        if (destinationId == R.id.examResultFragment || destinationId == R.id.examReviewFragment) {
            return R.id.examListFragment;
        }
        if (destinationId == R.id.translateFragment) {
            return R.id.dictionaryFragment;
        }
        if (destinationId == R.id.aiTutorHomeFragment
                || destinationId == R.id.aiChatFragment
                || destinationId == R.id.sentenceCheckFragment) {
            return R.id.homeFragment;
        }
        return -1;
    }
}