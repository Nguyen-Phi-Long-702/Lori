package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của POST /api/ai/generate-exercise (type = "exercise_set"): tối đa 5 bài cho một chủ đề
public class AiExerciseResponse {
    public String type;
    public List<Exercise> exercises;

    //type = "multiple_choice": options (4 lựa chọn) và answerIndex (vị trí đáp án đúng, tính từ 0)
    //type = "fill_blank": question có đúng một chỗ trống "___" và answers là các đáp án chấp nhận (1-3)
    //Trường không dùng tới của loại kia là null
    public static class Exercise {
        public String type;
        public String question;
        public List<String> options;
        public Integer answerIndex;
        public List<String> answers;
        public String explanation;
    }
}