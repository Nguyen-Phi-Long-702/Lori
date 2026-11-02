package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.model.ConversationMode;
import com.example.lori.viewmodel.AiConversationViewModel;

//Màn chọn tình huống hội thoại luyện tập (Premium). Chọn một tình huống thì bắt đầu buổi mới rồi mở màn chat
public class AiConversationFragment extends Fragment {

    private static final int COLUMNS = 2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_conversation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = NavHostFragment.findNavController(this);
        //Dùng chung với màn chat và màn nhận xét: ViewModel thuộc nav graph lồng nav_ai_conversation
        AiConversationViewModel viewModel = new ViewModelProvider(
                navController.getViewModelStoreOwner(R.id.nav_ai_conversation))
                .get(AiConversationViewModel.class);

        view.findViewById(R.id.btnBackAiConversation).setOnClickListener(v -> navController.navigateUp());

        RecyclerView rvModes = view.findViewById(R.id.rvAiConversationModes);
        rvModes.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        rvModes.setAdapter(new AiConversationModeAdapter(ConversationMode.ALL, mode -> {
            viewModel.startSession(mode.getId());
            navController.navigate(R.id.action_aiConversationFragment_to_aiConversationChatFragment);
        }));
    }
}