package com.example.lori.utils;

import java.util.List;
import java.util.Locale;

//Chấm bài điền vào chỗ trống của bài tập AI ngay trên máy (đáp án do Backend gửi kèm bài)
public final class AiExerciseChecker {

    private AiExerciseChecker() {
    }

    //Đúng khi câu trả lời khớp một trong các đáp án chấp nhận, không phân biệt hoa/thường và khoảng trắng thừa
    public static boolean isFillBlankCorrect(String input, List<String> acceptedAnswers) {
        if (input == null || acceptedAnswers == null) {
            return false;
        }
        String given = normalize(input);
        if (given.isEmpty()) {
            return false;
        }
        for (String answer : acceptedAnswers) {
            if (answer != null && given.equals(normalize(answer))) {
                return true;
            }
        }
        return false;
    }

    //Dấu nháy cong (bàn phím điện thoại hay tự gõ) đổi về nháy thẳng để "don’t" khớp với "don't"
    private static String normalize(String text) {
        return text.replace('’', '\'').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}