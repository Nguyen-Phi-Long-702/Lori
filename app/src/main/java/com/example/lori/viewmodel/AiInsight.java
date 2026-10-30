package com.example.lori.viewmodel;

import com.example.lori.data.remote.dto.AiExamAnalysisResponse;
import com.example.lori.data.remote.dto.AiQuizAnalysisResponse;
import com.example.lori.data.remote.dto.AiRecommendation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//Nội dung màn Phân tích AI đã gom về một dạng chung cho cả quiz và bài thi, để giao diện không phải phân biệt hai loại phản hồi
public final class AiInsight {

    //Một chỗ yếu: tên, số câu sai / tổng số câu và độ dài thanh (phần trăm câu sai, 0-100)
    public static final class WeakRow {
        public final String name;
        public final int wrong;
        public final int total;
        public final int wrongPercent;

        WeakRow(String name, int wrong, int total) {
            this.name = name;
            this.wrong = wrong;
            this.total = total;
            this.wrongPercent = total == 0 ? 0 : wrong * 100 / total;
        }
    }

    public final String summary;
    public final List<WeakRow> weakRows;
    public final List<String> patterns;
    public final List<AiRecommendation> recommendations;

    private AiInsight(String summary, List<WeakRow> weakRows, List<String> patterns,
                      List<AiRecommendation> recommendations) {
        this.summary = summary;
        this.weakRows = weakRows;
        this.patterns = patterns == null ? Collections.emptyList() : patterns;
        this.recommendations = recommendations == null ? Collections.emptyList() : recommendations;
    }

    //Quiz có câu sai thì có đúng một chỗ yếu là chính quiz đó; làm đúng hết thì không có
    public static AiInsight fromQuiz(AiQuizAnalysisResponse response) {
        List<WeakRow> rows = new ArrayList<>();
        if (response.incorrectCount > 0) {
            rows.add(new WeakRow(response.title, response.incorrectCount,
                    response.correctCount + response.incorrectCount));
        }
        return new AiInsight(response.summary, rows, response.patterns, response.recommendations);
    }

    //Mỗi phần của đề còn câu sai là một chỗ yếu; giữ thứ tự Backend trả (phần yếu nhất trước)
    public static AiInsight fromExam(AiExamAnalysisResponse response) {
        List<WeakRow> rows = new ArrayList<>();
        if (response.parts != null) {
            for (AiExamAnalysisResponse.PartStat part : response.parts) {
                if (part.correctCount < part.totalCount) {
                    rows.add(new WeakRow(part.name, part.totalCount - part.correctCount, part.totalCount));
                }
            }
        }
        return new AiInsight(response.summary, rows, response.patterns, response.recommendations);
    }
}