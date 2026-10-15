package com.example.lori.view;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

//Adapter của ViewPager2 màn làm bài: mỗi trang là một QuestionFragment ứng với một câu hỏi
public class ExamQuestionPagerAdapter extends FragmentStateAdapter {

    private final int questionCount;

    public ExamQuestionPagerAdapter(@NonNull FragmentActivity activity, int questionCount) {
        super(activity);
        this.questionCount = questionCount;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return QuestionFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return questionCount;
    }
}