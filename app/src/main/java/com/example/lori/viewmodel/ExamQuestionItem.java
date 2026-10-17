package com.example.lori.viewmodel;

import java.util.List;

//Một câu hỏi của đề thi đang làm, đã gộp sẵn thông tin của section chứa nó (tiêu đề, audio, ảnh, đoạn văn)
//Do ExamTakingViewModel tạo từ ExamDetailResponse; giao diện chỉ đọc
public class ExamQuestionItem {
    public final String questionId;
    public final String sectionTitle;
    //null nếu section không có audio (Reading)
    public final String audioUrl;
    //null nếu section không có ảnh (TOEIC Part 1 và các câu đồ họa Part 3-4 mới có)
    public final String imageUrl;
    //null nếu section không có đoạn văn
    public final String passageText;
    public final String questionType;
    //null nếu câu không có nội dung (vd TOEIC Part 1-2 chỉ có audio)
    public final String questionText;
    //null với TRUE_FALSE_NOT_GIVEN và FILL_BLANK
    public final List<String> options;
    //Số Part (1-7) của câu trong đề TOEIC; 0 nếu không phải đề TOEIC
    public final int toeicPart;
    //true nếu đây là câu đầu tiên của một Part TOEIC (câu hiện khung hướng dẫn của Part)
    public final boolean firstOfPart;

    public ExamQuestionItem(String questionId, String sectionTitle, String audioUrl, String imageUrl,
                            String passageText, String questionType, String questionText, List<String> options,
                            int toeicPart, boolean firstOfPart) {
        this.questionId = questionId;
        this.sectionTitle = sectionTitle;
        this.audioUrl = audioUrl;
        this.imageUrl = imageUrl;
        this.passageText = passageText;
        this.questionType = questionType;
        this.questionText = questionText;
        this.options = options;
        this.toeicPart = toeicPart;
        this.firstOfPart = firstOfPart;
    }
}