package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
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

        //Premium chưa mở ở sprint này nên chỉ hiển thị thông báo tạm thời
        cardPremiumBanner.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.profile_premium_coming_soon, Toast.LENGTH_SHORT).show());

        btnOpenSettings.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_settingsFragment));
    }
}