package com.example.lori.viewmodel;

import java.util.List;

//Một câu hỏi của đề thi đang làm, đã gộp sẵn thông tin của section chứa nó (tiêu đề, audio, ảnh, đoạn văn)
//Do ExamTakingViewModel tạo từ ExamDetailResponse; giao diện chỉ đọc
public class ExamQuestionItem {
    public final String questionId;
    //Id của section chứa câu: so sánh để biết hai câu có cùng section hay không
    public final String sectionId;
    public final String sectionTitle;
    //null nếu section không có audio (Reading)
    public final String audioUrl;
    //null nếu section không có ảnh (TOEIC Part 1 và các câu đồ họa Part 3-4 mới có)
    public final String imageUrl;
    //null nếu section không có đoạn văn
    public final String passageText;
    public final String questionType;
    //Hướng dẫn dạng bài của câu điền từ / matching trong đề IELTS, đã tách khỏi questionText; null nếu không có
    public final String instruction;
    //null nếu câu không có nội dung (vd TOEIC Part 1-2 chỉ có audio)
    public final String questionText;
    //null với TRUE_FALSE_NOT_GIVEN và FILL_BLANK
    public final List<String> options;
    //Số Part (1-7) của câu trong đề TOEIC; 0 nếu không phải đề TOEIC
    public final int toeicPart;
    //true nếu đây là câu đầu tiên của một Part TOEIC (câu hiện khung hướng dẫn của Part)
    public final boolean firstOfPart;
    //Thông tin riêng của đề IELTS; null nếu không phải đề IELTS
    public final IeltsInfo ielts;

    public ExamQuestionItem(String questionId, String sectionId, String sectionTitle, String audioUrl,
                            String imageUrl, String passageText, String questionType, String instruction,
                            String questionText, List<String> options, int toeicPart, boolean firstOfPart,
                            IeltsInfo ielts) {
        this.questionId = questionId;
        this.sectionId = sectionId;
        this.sectionTitle = sectionTitle;
        this.audioUrl = audioUrl;
        this.imageUrl = imageUrl;
        this.passageText = passageText;
        this.questionType = questionType;
        this.instruction = instruction;
        this.questionText = questionText;
        this.options = options;
        this.toeicPart = toeicPart;
        this.firstOfPart = firstOfPart;
        this.ielts = ielts;
    }

    //Thông tin của một câu thuộc đề IELTS. Số câu theo đề bắt đầu lại từ 1 ở mỗi kỹ năng
    //(Listening 1-40, Reading 1-40), khác với vị trí trong danh sách làm bài (1-80)
    public static class IeltsInfo {
        //LISTENING hoặc READING (giá trị skill Backend trả về)
        public final String skill;
        //Section/Passage thứ mấy trong kỹ năng của nó (bắt đầu từ 1)
        public final int sectionNumber;
        //Số câu theo đánh số của đề
        public final int questionNumber;
        //true nếu đây là câu đầu tiên của section
        public final boolean firstOfSection;
        //Số câu đầu và số câu cuối của section chứa câu này
        public final int firstQuestionNumber;
        public final int lastQuestionNumber;

        public IeltsInfo(String skill, int sectionNumber, int questionNumber, boolean firstOfSection,
                         int firstQuestionNumber, int lastQuestionNumber) {
            this.skill = skill;
            this.sectionNumber = sectionNumber;
            this.questionNumber = questionNumber;
            this.firstOfSection = firstOfSection;
            this.firstQuestionNumber = firstQuestionNumber;
            this.lastQuestionNumber = lastQuestionNumber;
        }
    }
}