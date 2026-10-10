package com.arenax.game;

import java.util.*;

public final class GameTypes {
    private GameTypes() {}
    public enum Phase { LOBBY, QUESTION_INTRO, QUESTION_ACTIVE, QUESTION_REVEAL, ROUND_LEADERBOARD, FINAL_PODIUM }
    public record Option(String id, String text) {}
    public record Prompt(String id, String prompt, List<Option> options, String correctOptionId, String topic,
                         int durationSeconds, String imageUrl, List<List<String>> tableData) {}
    public record PublicQuestion(int index, String promptId, String prompt, List<Option> options, String topic,
                                 long serverStart, long endsAt, String imageUrl, List<List<String>> tableData) {}
    public record PlayerView(String playerId, String name, String college, int score, int rank, boolean submitted) {}
    public record Snapshot(String roomCode, Phase phase, int questionIndex, long remainingMs,
                           List<Option> options, boolean submitted, int score, int rank, List<PlayerView> players) {}
    public record Answer(String questionId, String optionId, Long clientTimestamp) {}
    public record SubmissionResult(String status, int score) {}
}
