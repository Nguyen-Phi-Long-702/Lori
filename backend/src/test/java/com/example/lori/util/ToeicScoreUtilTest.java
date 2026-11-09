package com.example.lori.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToeicScoreUtilTest {

    @ParameterizedTest
    @CsvSource({"0,100,5", "1,100,10", "2,100,15", "50,100,250", "99,100,490", "100,100,495", "3,4,375", "100,200,250"})
    void sectionScoreIsLinearFrom5To495InStepsOfFive(int correct, int total, int score) {
        assertThat(ToeicScoreUtil.sectionScore(correct, total)).isEqualTo(score);
    }

    @Test
    void emptySectionScoresZero() {
        assertThat(ToeicScoreUtil.sectionScore(5, 0)).isEqualTo(0);
        assertThat(ToeicScoreUtil.sectionScore(5, -1)).isEqualTo(0);
    }

    @Test
    void correctCountIsClampedIntoZeroToTotal() {
        assertThat(ToeicScoreUtil.sectionScore(150, 100)).isEqualTo(495);
        assertThat(ToeicScoreUtil.sectionScore(-5, 100)).isEqualTo(5);
    }
}