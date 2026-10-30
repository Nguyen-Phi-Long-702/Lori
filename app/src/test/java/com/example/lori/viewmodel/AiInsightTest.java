package com.example.lori.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.lori.data.remote.dto.AiExamAnalysisResponse;
import com.example.lori.data.remote.dto.AiQuizAnalysisResponse;

import org.junit.Test;

import java.util.Arrays;

public class AiInsightTest {

    private static AiExamAnalysisResponse.PartStat part(String name, int correct, int total) {
        AiExamAnalysisResponse.PartStat part = new AiExamAnalysisResponse.PartStat();
        part.name = name;
        part.correctCount = correct;
        part.totalCount = total;
        return part;
    }

    @Test
    public void fromQuiz_withWrongAnswers_hasOneRowForTheQuiz() {
        AiQuizAnalysisResponse response = new AiQuizAnalysisResponse();
        response.title = "Articles (a/an/the)";
        response.correctCount = 6;
        response.incorrectCount = 4;
        response.summary = "Tóm tắt";

        AiInsight insight = AiInsight.fromQuiz(response);

        assertEquals(1, insight.weakRows.size());
        assertEquals("Articles (a/an/the)", insight.weakRows.get(0).name);
        assertEquals(4, insight.weakRows.get(0).wrong);
        assertEquals(10, insight.weakRows.get(0).total);
        assertEquals(40, insight.weakRows.get(0).wrongPercent);
        assertTrue(insight.patterns.isEmpty());
        assertTrue(insight.recommendations.isEmpty());
    }

    @Test
    public void fromQuiz_allCorrect_hasNoWeakRow() {
        AiQuizAnalysisResponse response = new AiQuizAnalysisResponse();
        response.correctCount = 10;

        assertTrue(AiInsight.fromQuiz(response).weakRows.isEmpty());
    }

    @Test
    public void fromExam_keepsOrderAndSkipsPerfectParts() {
        AiExamAnalysisResponse response = new AiExamAnalysisResponse();
        response.parts = Arrays.asList(part("Part 5", 5, 10), part("Part 6", 4, 4), part("Part 7", 12, 16));

        AiInsight insight = AiInsight.fromExam(response);

        assertEquals(2, insight.weakRows.size());
        assertEquals("Part 5", insight.weakRows.get(0).name);
        assertEquals(50, insight.weakRows.get(0).wrongPercent);
        assertEquals("Part 7", insight.weakRows.get(1).name);
        assertEquals(4, insight.weakRows.get(1).wrong);
        assertEquals(25, insight.weakRows.get(1).wrongPercent);
    }
}