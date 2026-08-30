package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lori.R;
import com.example.lori.data.local.entity.TopicWord;
import com.example.lori.utils.SpeechHelper;
import com.example.lori.viewmodel.FlashcardViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

//Fragment hiển thị flashcard của một chủ đề và hỗ trợ phát âm từ vựng
public class FlashcardFragment extends Fragment {

    private FlashcardViewModel viewModel;
    private FlashcardPagerAdapter pagerAdapter;
    private SpeechHelper speechHelper;
    private String topicName;

    private TextView tvPosition;
    private LinearProgressIndicator progressFlashcard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flashcard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Lấy thông tin chủ đề được truyền từ màn hình trước
        int topicId = requireArguments().getInt("topicId");
        topicName = requireArguments().getString("topicName");

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        TextView tvTopicTitle = view.findViewById(R.id.tvTopicTitle);
        ViewPager2 vpFlashcards = view.findViewById(R.id.vpFlashcards);
        MaterialButton btnViewDetail = view.findViewById(R.id.btnViewDetail);
        tvPosition = view.findViewById(R.id.tvPosition);
        progressFlashcard = view.findViewById(R.id.progressFlashcard);

        tvTopicTitle.setText(topicName);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        speechHelper = new SpeechHelper(requireContext());

        viewModel = new ViewModelProvider(this).get(FlashcardViewModel.class);

        pagerAdapter = new FlashcardPagerAdapter(this::speak);
        vpFlashcards.setAdapter(pagerAdapter);
        //Đồng bộ vị trí flashcard hiện tại với viewmodel
        vpFlashcards.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                viewModel.setCurrentIndex(position);
            }
        });

        viewModel.loadTopic(topicId);
        viewModel.getWords().observe(getViewLifecycleOwner(), this::onWordsChanged);
        viewModel.getCurrentIndex().observe(getViewLifecycleOwner(), this::updateProgress);

        //Mở màn hình chi tiết của từ đang được chọn
        btnViewDetail.setOnClickListener(v -> {
            List<TopicWord> words = viewModel.getWords().getValue();
            int position = vpFlashcards.getCurrentItem();
            if (words != null && position >= 0 && position < words.size()) {
                openWordDetail(v, words.get(position));
            }
        });
    }

    //Cập nhật danh sách flashcard và reset vị trí hiển thị
    private void onWordsChanged(List<TopicWord> words) {
        pagerAdapter.setWords(words);
        updateProgress(0);
    }

    //Cập nhật vị trí hiện tại và thanh tiến độ của bộ flashcard
    private void updateProgress(int index) {
        List<TopicWord> words = viewModel.getWords().getValue();
        int total = words != null ? words.size() : 0;
        progressFlashcard.setMax(Math.max(total, 1));
        progressFlashcard.setProgress(Math.min(index + 1, total));
        tvPosition.setText(getString(R.string.flashcard_position_format, total == 0 ? 0 : index + 1, total));
    }

    //Chuyển sang màn hình chi tiết và truyền thông tin của từ đang chọn
    private void openWordDetail(View view, TopicWord word) {
        Bundle args = new Bundle();
        args.putString("word", word.word);
        args.putString("pronunciation", word.pronunciation);
        args.putString("meaning", word.meaning);
        args.putString("topicName", topicName);
        Navigation.findNavController(view)
                .navigate(R.id.action_flashcardFragment_to_wordDetailFragment, args);
    }

    //Yêu cầu speechhelper phát âm từ vựng
    private void speak(String word) {
        if (!speechHelper.speak(word, "flashcard_utt")) {
            Toast.makeText(requireContext(), R.string.tts_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        //Giải phóng tài nguyên của speechhelper khi view của fragment bị hủy
        if (speechHelper != null) {
            speechHelper.shutdown();
        }
        super.onDestroyView();
    }
}