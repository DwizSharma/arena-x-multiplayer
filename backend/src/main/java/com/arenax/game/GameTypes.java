package com.arenax.game;

import java.util.*;

public final class GameTypes {
    private GameTypes() {}
    public enum Phase { LOBBY, QUESTION_INTRO, QUESTION_ACTIVE, QUESTION_REVEAL, ROUND_LEADERBOARD, FINAL_PODIUM }
    public record Option(String id, String text) {}
    public record Prompt(String id, String prompt, List<Option> options, String correctOptionId, String topic,
                         int durationSeconds, String imageUrl, List<List<String>> tableData) {}
    public record PublicQuestion(int index, String promptId, String prompt, List<Option> options, String topic,
                                 long serverStart, long endsAt, long durationMs, long remainingMs,
                                 String imageUrl, List<List<String>> tableData) {}
    public record TopicAnalytics(int answered, int correct, double accuracyPercent, long averageSpeedMs) {}
    public record PlayerView(String playerId, String name, String college, int score, int rank, boolean submitted,
                             int accuracyPercent, long averageSpeedMs, Map<String,TopicAnalytics> topics) {}
    public record Snapshot(String roomCode, Phase phase, String mode, boolean hostManaged, int questionIndex, long remainingMs,
                           List<Option> options, PublicQuestion currentQuestion, boolean submitted,
                           int score, int rank, List<PlayerView> players) {}
    public record AdminRoomView(String code, String mode, String phase, int playerCount, int capacity, String joinPath) {}
    public record RoomMoment(String momentId, String kind, String prompt, List<String> options,
                             Map<String, Integer> counts, long closesAt, boolean active) {}
    public record MomentVote(String momentId, String choice) {}
    public record Answer(String questionId, String optionId, Long clientTimestamp) {}
    public record SubmissionResult(String status, int score, long serverElapsedMs) {}
}
