package com.arenax.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnswerScorerTest {
    @Test
    void givesMaximumPointsForAnImmediateCorrectAnswer() {
        assertEquals(1000, AnswerScorer.score(true, 0, 20_000));
    }

    @Test
    void givesBasePointsAtAndAfterTheAnswerDeadline() {
        assertEquals(500, AnswerScorer.score(true, 20_000, 20_000));
        assertEquals(500, AnswerScorer.score(true, 20_250, 20_000));
    }

    @Test
    void wrongAnswersEarnNoPoints() {
        assertEquals(0, AnswerScorer.score(false, 100, 20_000));
    }

    @Test
    void rejectsAnInvalidDurationForCorrectAnswers() {
        assertThrows(IllegalArgumentException.class, () -> AnswerScorer.score(true, 0, 0));
    }
}
