package com.arenax.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "player_match_results", indexes = {
        @Index(name = "idx_player_match_room", columnList = "room_code"),
        @Index(name = "idx_player_match_player", columnList = "player_id")
})
public class PlayerMatchResult {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(name = "room_code", nullable = false) private String roomCode;
    @Column(name = "player_id", nullable = false) private String playerId;
    @Column(name = "player_name", nullable = false) private String playerName;
    @Column(name = "college_name", nullable = false) private String collegeName;
    @Column(nullable = false) private int score;
    @Column(nullable = false) private int rank;
    @Column(name = "correct_count", nullable = false) private int correctCount;
    @Column(name = "total_questions", nullable = false) private int totalQuestions;
    @Column(name = "accuracy_percent", nullable = false) private int accuracyPercent;
    @Column(name = "average_response_ms", nullable = false) private long averageResponseMs;
    @Column(name = "topics_json", nullable = false, columnDefinition = "text") private String topicsJson;
    @Column(name = "finished_at", nullable = false) private Instant finishedAt = Instant.now();
    protected PlayerMatchResult() {}
    public PlayerMatchResult(String roomCode, String playerId, String playerName, String collegeName, int score,
                             int rank, int correctCount, int totalQuestions, int accuracyPercent,
                             long averageResponseMs, String topicsJson) {
        this.roomCode=roomCode; this.playerId=playerId; this.playerName=playerName; this.collegeName=collegeName;
        this.score=score; this.rank=rank; this.correctCount=correctCount; this.totalQuestions=totalQuestions;
        this.accuracyPercent=accuracyPercent; this.averageResponseMs=averageResponseMs; this.topicsJson=topicsJson;
    }
    public String getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getCollegeName() { return collegeName; }
    public int getScore() { return score; }
    public int getRank() { return rank; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getAccuracyPercent() { return accuracyPercent; }
    public long getAverageResponseMs() { return averageResponseMs; }
    public String getTopicsJson() { return topicsJson; }
    public Instant getFinishedAt() { return finishedAt; }
}
