package com.example.lori.view;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.viewmodel.AiChatViewModel;
import com.google.android.material.button.MaterialButton;

//Màn Hỏi đáp tự do với AI Tutor (Premium): bong bóng chat, câu trả lời Markdown, bong bóng "đang trả lời"
public class AiChatFragment extends Fragment {

    //AI Service chỉ giữ tối đa 1.000 ký tự sau khi làm sạch, nên giới hạn ngay trên ô nhập
    private static final int MAX_CHARS = 1000;

    private int previousSoftInputMode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AiChatViewModel viewModel = new ViewModelProvider(this).get(AiChatViewModel.class);

        RecyclerView rvChat = view.findViewById(R.id.rvAiChat);
        EditText etInput = view.findViewById(R.id.etAiChatInput);
        MaterialButton btnSend = view.findViewById(R.id.btnAiChatSend);
        TextView tvOffline = view.findViewById(R.id.tvAiChatOffline);
        TextView tvError = view.findViewById(R.id.tvAiChatError);

        AiChatAdapter adapter = new AiChatAdapter(requireContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(adapter);

        etInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_CHARS)});

        view.findViewById(R.id.btnBackAiChat)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        btnSend.setOnClickListener(v -> {
            if (viewModel.send(etInput.getText().toString())) {
                etInput.setText("");
            }
        });

        //Bàn phím hiện lên thì đẩy ô nhập lên trên bàn phím (thanh điều hướng dưới đã ẩn ở màn này)
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(0, 0, 0, Math.max(0, keyboard - systemBars));
            return insets;
        });

        //Tin mới (của bạn, của AI hoặc bong bóng "đang trả lời") thì cuộn tới tin cuối; offset 0 để câu trả lời dài hiện từ dòng đầu
        viewModel.getItems().observe(getViewLifecycleOwner(), items ->
                adapter.submitList(items, () -> layoutManager.scrollToPositionWithOffset(items.size() - 1, 0)));

        viewModel.getSendEnabled().observe(getViewLifecycleOwner(), enabled ->
                btnSend.setEnabled(Boolean.TRUE.equals(enabled)));
        viewModel.getOnline().observe(getViewLifecycleOwner(), online ->
                tvOffline.setVisibility(Boolean.TRUE.equals(online) ? View.GONE : View.VISIBLE));

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), messageRes -> {
            if (messageRes == null) {
                tvError.setVisibility(View.GONE);
            } else {
                tvError.setText(messageRes);
                tvError.setVisibility(View.VISIBLE);
            }
        });

        //User Free (hoặc Premium đã hết hạn): Backend trả 403 PREMIUM_REQUIRED nên mở dialog nâng cấp
        viewModel.getPremiumRequired().observe(getViewLifecycleOwner(), required -> {
            if (Boolean.TRUE.equals(required)) {
                PremiumPromptDialog.newInstance(getString(R.string.ai_tutor_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
                viewModel.consumePremiumRequired();
            }
        });
    }

    //Chỉ riêng màn này cần cửa sổ co lại theo bàn phím (các màn khác giữ nguyên); trả về chế độ cũ khi rời màn
    @Override
    public void onResume() {
        super.onResume();
        previousSoftInputMode = requireActivity().getWindow().getAttributes().softInputMode;
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onPause() {
        requireActivity().getWindow().setSoftInputMode(previousSoftInputMode);
        super.onPause();
    }
}