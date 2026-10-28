package com.example.lori.service;

import com.example.lori.entity.UserWeaknessRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningEngineServiceTest {

    @Test
    void statusOf_threeOrMoreWrongAndBelow60PercentIsReviewing() {
        assertThat(LearningEngineService.statusOf(2, 8)).isEqualTo("reviewing");
        assertThat(LearningEngineService.statusOf(5, 5)).isEqualTo("reviewing");
    }

    @Test
    void statusOf_60PercentOrFewerThanThreeWrongIsNotReviewing() {
        assertThat(LearningEngineService.statusOf(6, 4)).isEqualTo("learning");
        assertThat(LearningEngineService.statusOf(1, 2)).isEqualTo("learning");
    }

    @Test
    void statusOf_90PercentOrMoreIsMastered() {
        assertThat(LearningEngineService.statusOf(9, 1)).isEqualTo("mastered");
        assertThat(LearningEngineService.statusOf(10, 0)).isEqualTo("mastered");
        assertThat(LearningEngineService.statusOf(8, 2)).isEqualTo("learning");
    }

    @Test
    void statusOf_noAnswersIsLearning() {
        assertThat(LearningEngineService.statusOf(0, 0)).isEqualTo("learning");
    }

    @Test
    void levelOf_noDataIsA1() {
        assertThat(LearningEngineService.levelOf(List.of())).isEqualTo("A1");
    }

    @Test
    void levelOf_beginnerGroupAt60PercentIsA2() {
        assertThat(LearningEngineService.levelOf(List.of(grammar(1, 6, 4)))).isEqualTo("A2");
        assertThat(LearningEngineService.levelOf(List.of(grammar(1, 5, 5)))).isEqualTo("A1");
    }

    @Test
    void levelOf_intermediateGroupIsB1OrB2() {
        assertThat(LearningEngineService.levelOf(List.of(grammar(11, 6, 4)))).isEqualTo("B1");
        assertThat(LearningEngineService.levelOf(List.of(grammar(11, 8, 2)))).isEqualTo("B2");
    }

    @Test
    void levelOf_advancedGroupAt80PercentIsC1() {
        assertThat(LearningEngineService.levelOf(List.of(grammar(23, 8, 2)))).isEqualTo("C1");
    }

    @Test
    void levelOf_groupWithFewerThan10AnswersIsIgnored() {
        assertThat(LearningEngineService.levelOf(List.of(grammar(23, 5, 0)))).isEqualTo("A1");
    }

    @Test
    void levelOf_addsUpLessonsOfTheSameGroup() {
        // bai 11 va 12 cung nhom trung cap: (5+8)/20 = 65% -> B1
        assertThat(LearningEngineService.levelOf(List.of(grammar(11, 5, 5), grammar(12, 8, 2)))).isEqualTo("B1");
    }

    private static UserWeaknessRecord grammar(int lessonId, int correct, int incorrect) {
        UserWeaknessRecord weakness = new UserWeaknessRecord();
        weakness.setCategory("grammar");
        weakness.setTopicKey(String.valueOf(lessonId));
        weakness.setCorrectCount(correct);
        weakness.setIncorrectCount(incorrect);
        return weakness;
    }
}