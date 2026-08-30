package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.local.entity.Topic;
import com.example.lori.viewmodel.TopicListViewModel;

//Fragment hiển thị danh sách các chủ đề học tập
public class TopicListFragment extends Fragment {

    private TopicListViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TopicListViewModel.class);

        RecyclerView rvTopics = view.findViewById(R.id.rvTopics);
        rvTopics.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        TopicAdapter adapter = new TopicAdapter(topic -> openFlashcard(view, topic));
        rvTopics.setAdapter(adapter);

        //Quan sát danh sách chủ đề và cập nhật adapter khi dữ liệu thay đổi
        viewModel.getTopics().observe(getViewLifecycleOwner(), adapter::setTopics);
    }

    //Chuyển sang màn hình flashcard của chủ đề được chọn
    private void openFlashcard(View view, Topic topic) {
        Bundle args = new Bundle();
        args.putInt("topicId", topic.id);
        args.putString("topicName", topic.nameVi);
        Navigation.findNavController(view)
                .navigate(R.id.action_topicListFragment_to_flashcardFragment, args);
    }
}