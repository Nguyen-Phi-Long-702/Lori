package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.viewmodel.SettingsViewModel;
import com.google.android.material.materialswitch.MaterialSwitch;

//Fragment hiển thị màn hình cài đặt ứng dụng
public class SettingsFragment extends Fragment {
    private SettingsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        view.findViewById(R.id.btnBackSettings).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        view.findViewById(R.id.rowPremium).setOnClickListener(v ->
                PremiumPromptDialog.newInstance().show(getChildFragmentManager(), "premium_prompt"));

        //Các mục cần tài khoản đăng nhập - app chưa có hệ thống tài khoản nên chỉ thông báo rõ ràng
        View.OnClickListener showAccountLockedMessage = v ->
                Toast.makeText(requireContext(), R.string.settings_account_locked_message, Toast.LENGTH_SHORT).show();
        view.findViewById(R.id.rowEditProfile).setOnClickListener(showAccountLockedMessage);
        view.findViewById(R.id.rowChangePassword).setOnClickListener(showAccountLockedMessage);

        //Chỉ gắn sự kiện đổi công tắc ở đây, trạng thái hiển thị ban đầu và đồng bộ lại xử lý trong onResume()
        MaterialSwitch switchNotification = view.findViewById(R.id.switchNotification);
        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setNotificationEnabled(isChecked));

        MaterialSwitch switchSound = view.findViewById(R.id.switchSound);
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setSoundEnabled(isChecked));

        MaterialSwitch switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setDarkModeEnabled(isChecked));

        setupCacheRow(view);
        setupAboutRow(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        //Đồng bộ lại đúng trạng thái 3 công tắc mỗi khi màn hình được hiển thị lại
        MaterialSwitch switchNotification = requireView().findViewById(R.id.switchNotification);
        switchNotification.setChecked(viewModel.isNotificationEnabled());

        MaterialSwitch switchSound = requireView().findViewById(R.id.switchSound);
        switchSound.setChecked(viewModel.isSoundEnabled());

        MaterialSwitch switchDarkMode = requireView().findViewById(R.id.switchDarkMode);
        switchDarkMode.setChecked(viewModel.isDarkModeEnabled());
    }

    //Hiển thị dung lượng bộ nhớ đệm hiện tại và cho phép xóa
    private void setupCacheRow(View view) {
        TextView tvCacheSize = view.findViewById(R.id.tvCacheSize);
        viewModel.getCacheSizeLabel().observe(getViewLifecycleOwner(), size ->
                tvCacheSize.setText(getString(R.string.settings_clear_cache_desc_format, size)));

        view.findViewById(R.id.rowClearCache).setOnClickListener(v -> {
            viewModel.clearCache();
            Toast.makeText(requireContext(), R.string.settings_clear_cache_done, Toast.LENGTH_SHORT).show();
        });
    }

    //Hiển thị hộp thoại thông tin phiên bản ứng dụng và hiện luôn số phiên bản trên dòng cài đặt
    private void setupAboutRow(View view) {
        String versionName = viewModel.getAppVersionName();

        TextView tvAppVersion = view.findViewById(R.id.tvAppVersion);
        tvAppVersion.setText(getString(R.string.settings_about_version_format, versionName));

        view.findViewById(R.id.rowAbout).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.settings_about_title))
                        .setMessage(getString(R.string.settings_about_version_format, versionName)
                                + "\n\n" + getString(R.string.settings_about_dialog_message))
                        .setPositiveButton(R.string.settings_dialog_close, null)
                        .show());
    }
}