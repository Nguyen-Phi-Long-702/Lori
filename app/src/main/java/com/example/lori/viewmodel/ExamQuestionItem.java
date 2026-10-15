package com.example.lori.viewmodel;

import java.util.List;

//Một câu hỏi của đề thi đang làm, đã gộp sẵn thông tin của section chứa nó (tiêu đề, audio, đoạn văn)
//Do ExamTakingViewModel tạo từ ExamDetailResponse; giao diện chỉ đọc
public class ExamQuestionItem {
    public final String questionId;
    public final String sectionTitle;
    //null nếu section không có audio (Reading)
    public final String audioUrl;
    //null nếu section không có đoạn văn
    public final String passageText;
    public final String questionType;
    //null nếu câu không có nội dung (vd TOEIC Part 1-2 chỉ có audio)
    public final String questionText;
    //null với TRUE_FALSE_NOT_GIVEN và FILL_BLANK
    public final List<String> options;

    public ExamQuestionItem(String questionId, String sectionTitle, String audioUrl, String passageText,
                            String questionType, String questionText, List<String> options) {
        this.questionId = questionId;
        this.sectionTitle = sectionTitle;
        this.audioUrl = audioUrl;
        this.passageText = passageText;
        this.questionType = questionType;
        this.questionText = questionText;
        this.options = options;
    }
}