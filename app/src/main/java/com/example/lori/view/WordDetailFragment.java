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
import androidx.navigation.Navigation;

import com.example.lori.R;
import com.example.lori.utils.SpeechHelper;

//Fragment hiển thị thông tin chi tiết của một từ vựng
public class WordDetailFragment extends Fragment {

    private SpeechHelper speechHelper;
    private String word;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_word_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Nhận thông tin từ vựng được truyền từ màn hình flashcard
        Bundle args = requireArguments();
        word = args.getString("word");
        String pronunciation = args.getString("pronunciation");
        String meaning = args.getString("meaning");
        String topicName = args.getString("topicName");

        TextView tvWord = view.findViewById(R.id.tvWordDetail);
        TextView tvPronunciation = view.findViewById(R.id.tvPronunciationDetail);
        TextView tvMeaning = view.findViewById(R.id.tvMeaningDetail);
        TextView tvTopic = view.findViewById(R.id.tvTopicDetail);
        ImageButton btnBack = view.findViewById(R.id.btnBackDetail);
        ImageButton btnSpeak = view.findViewById(R.id.btnSpeakDetail);

        tvWord.setText(word);
        tvMeaning.setText(meaning);
        tvTopic.setText(getString(R.string.word_detail_topic_label_format, topicName));

        //Chỉ hiển thị phần phát âm khi dữ liệu có giá trị
        if (pronunciation != null && !pronunciation.trim().isEmpty()) {
            tvPronunciation.setVisibility(View.VISIBLE);
            tvPronunciation.setText(pronunciation);
        } else {
            tvPronunciation.setVisibility(View.GONE);
        }

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        btnSpeak.setOnClickListener(v -> speak());

        speechHelper = new SpeechHelper(requireContext());
    }

    //Phát âm từ đang được hiển thị
    private void speak() {
        if (!speechHelper.speak(word, "word_detail_utt")) {
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