package com.arenax.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "match_history")
public class MatchHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(name = "room_code", nullable = false) private String roomCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "college_id") private College college;
    @Column(name = "winner_player_id") private String winnerPlayerId;
    @Column(name = "ended_at", nullable = false) private Instant endedAt = Instant.now();
    protected MatchHistory() {}
    public MatchHistory(String roomCode, College college, String winnerPlayerId) {
        this.roomCode=roomCode; this.college=college; this.winnerPlayerId=winnerPlayerId;
    }
    public String getId() { return id; } public String getRoomCode() { return roomCode; }
    public College getCollege() { return college; } public String getWinnerPlayerId() { return winnerPlayerId; }
    public Instant getEndedAt() { return endedAt; }
}
