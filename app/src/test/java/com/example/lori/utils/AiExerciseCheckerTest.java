package com.example.lori.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AiExerciseCheckerTest {

    private static final List<String> ANSWERS = Arrays.asList("has been", "has gone");

    @Test
    public void isFillBlankCorrect_ignoresCaseAndExtraSpaces() {
        assertTrue(AiExerciseChecker.isFillBlankCorrect("  Has   BEEN ", ANSWERS));
    }

    @Test
    public void isFillBlankCorrect_acceptsAnyListedAnswer() {
        assertTrue(AiExerciseChecker.isFillBlankCorrect("has gone", ANSWERS));
    }

    @Test
    public void isFillBlankCorrect_curlyApostropheMatchesStraightOne() {
        assertTrue(AiExerciseChecker.isFillBlankCorrect("doesn’t", Collections.singletonList("doesn't")));
    }

    @Test
    public void isFillBlankCorrect_rejectsWrongBlankOrNull() {
        assertFalse(AiExerciseChecker.isFillBlankCorrect("have been", ANSWERS));
        assertFalse(AiExerciseChecker.isFillBlankCorrect("   ", ANSWERS));
        assertFalse(AiExerciseChecker.isFillBlankCorrect(null, ANSWERS));
        assertFalse(AiExerciseChecker.isFillBlankCorrect("has been", null));
    }
}