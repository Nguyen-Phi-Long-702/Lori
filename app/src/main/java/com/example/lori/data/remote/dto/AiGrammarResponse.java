package com.example.lori.data.remote.dto;

import java.util.List;

//Kết quả phân tích ngữ pháp (type = "grammar_analysis"). errors rỗng khi correct = true
public class AiGrammarResponse {
    public String type;
    public boolean correct;
    //Câu đã sửa; câu đúng thì giữ nguyên
    public String correctedSentence;
    public List<GrammarError> errors;
    //Id bài ngữ pháp liên quan trong dictionary.db của Lori; null nếu không có bài phù hợp
    public Integer lessonId;

    public static class GrammarError {
        //Tên lỗi ngắn gọn, vd "Subject-verb agreement"
        public String type;
        public String explanation;
    }
}