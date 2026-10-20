package com.example.lori.view;

import android.os.Bundle;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.PremiumPurchaseActivity;
import com.example.lori.viewmodel.ProfileViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

//Fragment hiển thị màn hình hồ sơ người dùng: avatar và lịch streak học tập
public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;
    private StreakDayAdapter streakAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        TextView tvCurrentStreak = view.findViewById(R.id.tvCurrentStreak);
        TextView tvBestStreak = view.findViewById(R.id.tvBestStreak);
        RecyclerView rvStreakGrid = view.findViewById(R.id.rvStreakGrid);
        MaterialCardView cardPremiumBanner = view.findViewById(R.id.cardPremiumBanner);
        MaterialButton btnOpenSettings = view.findViewById(R.id.btnOpenSettings);

        rvStreakGrid.setLayoutManager(new GridLayoutManager(requireContext(), 6));
        streakAdapter = new StreakDayAdapter();
        rvStreakGrid.setAdapter(streakAdapter);

        viewModel.getStreakGrid().observe(getViewLifecycleOwner(), streakAdapter::setDays);
        viewModel.getCurrentStreak().observe(getViewLifecycleOwner(),
                streak -> tvCurrentStreak.setText(String.valueOf(streak)));
        viewModel.getBestStreak().observe(getViewLifecycleOwner(),
                streak -> tvBestStreak.setText(String.valueOf(streak)));

        //Banner Premium mở thẳng màn mua
        cardPremiumBanner.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PremiumPurchaseActivity.class)));

        //Mở màn Bảng xếp hạng. Chỉ chuyển khi đang đứng ở màn Hồ sơ: chạm nhanh hai lần sẽ không chuyển lần hai (action không còn hợp lệ từ màn khác sẽ làm app văng)
        view.findViewById(R.id.cardLeaderboard).setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(v);
            NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.profileFragment) {
                navController.navigate(R.id.action_profileFragment_to_leaderboardFragment);
            }
        });

        btnOpenSettings.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_settingsFragment));
    }
}