package com.arenax.game;

/** Keeps answer scoring independent from room lifecycle and transport concerns. */
public final class AnswerScorer {
    private AnswerScorer() {}

    public static int score(boolean correct, long elapsedMs, long allowedMs) {
        if (!correct) return 0;
        if (allowedMs <= 0) throw new IllegalArgumentException("Allowed answer time must be positive");
        long scoringElapsed = Math.max(0, Math.min(elapsedMs, allowedMs));
        return 500 + (int) Math.floor(500 * (1.0 - (double) scoringElapsed / allowedMs));
    }
}
