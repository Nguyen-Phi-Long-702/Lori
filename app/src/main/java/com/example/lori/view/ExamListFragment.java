package com.example.lori.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lori.R;

//Fragment hiển thị danh sách các đề thi
public class ExamListFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        //Tạm thời sử dụng layout placeholder trong khi màn hình danh sách đề thi chưa hoàn thiện
        View view = inflater.inflate(R.layout.fragment_placeholder, container, false);
        TextView tv = view.findViewById(R.id.tvPlaceholder);
        tv.setText(R.string.stub_exam);
        return view;
    }
}