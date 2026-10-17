package com.example.lori.viewmodel;

import androidx.annotation.StringRes;

//Một lỗi khi nộp bài: câu thông báo (mã R.string) và có nên cho nộp lại hay không
//Chỉ giữ dữ liệu, không biết gì về giao diện
public final class ExamSubmitError {

    @StringRes
    public final int messageRes;
    //true = lỗi tạm thời (mất mạng, máy chủ chậm/lỗi...): hiện nút Nộp lại
    //false = không cứu được (phiên hết hạn/đã nộp, Premium hết hạn...): chỉ cho thoát
    public final boolean canRetry;

    public ExamSubmitError(@StringRes int messageRes, boolean canRetry) {
        this.messageRes = messageRes;
        this.canRetry = canRetry;
    }
}