package com.example.lori.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IeltsScoreUtilTest {

    @ParameterizedTest
    @CsvSource({"40,9.0", "39,9.0", "38,8.5", "37,8.5", "36,8.0", "35,8.0", "34,7.5", "32,7.5", "31,7.0", "30,7.0",
            "29,6.5", "26,6.5", "25,6.0", "23,6.0", "22,5.5", "18,5.5", "17,5.0", "16,5.0", "15,4.5", "1,2.0", "0,0.0"})
    void listeningBandFollowsTheThresholdTable(int correct, double band) {
        assertThat(IeltsScoreUtil.listeningBand(correct, 40)).isEqualTo(band);
    }

    @ParameterizedTest
    @CsvSource({"39,9.0", "37,8.5", "33,7.5", "32,7.0", "27,6.5", "26,6.0", "19,5.5", "18,5.0", "15,5.0", "14,4.5", "0,0.0"})
    void readingBandFollowsItsOwnThresholdTable(int correct, double band) {
        assertThat(IeltsScoreUtil.readingBand(correct, 40)).isEqualTo(band);
    }

    @Test
    void emptySectionScoresZero() {
        assertThat(IeltsScoreUtil.listeningBand(5, 0)).isEqualTo(0.0);
        assertThat(IeltsScoreUtil.readingBand(5, -1)).isEqualTo(0.0);
    }

    @Test
    void correctCountIsClampedIntoZeroToTotal() {
        assertThat(IeltsScoreUtil.listeningBand(50, 40)).isEqualTo(9.0);
        assertThat(IeltsScoreUtil.listeningBand(-3, 40)).isEqualTo(0.0);
    }

    @Test
    void sectionWithoutFortyQuestionsIsScaledToForty() {
        assertThat(IeltsScoreUtil.listeningBand(20, 20)).isEqualTo(9.0);
        assertThat(IeltsScoreUtil.listeningBand(10, 20)).isEqualTo(5.5);
        assertThat(IeltsScoreUtil.readingBand(30, 30)).isEqualTo(9.0);
    }

    @ParameterizedTest
    @CsvSource({"7.0,6.5,7.0", "7.0,7.0,7.0", "6.5,6.5,6.5", "6.0,6.5,6.5", "5.5,6.0,6.0", "9.0,8.5,9.0", "0.0,0.0,0.0"})
    void overallBandIsTheAverageRoundedToHalfBands(double listening, double reading, double overall) {
        assertThat(IeltsScoreUtil.overallBand(listening, reading)).isEqualTo(overall);
    }
}