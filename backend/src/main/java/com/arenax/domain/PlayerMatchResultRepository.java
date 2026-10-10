package com.arenax.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;

public interface PlayerMatchResultRepository extends JpaRepository<PlayerMatchResult, String> {
    List<PlayerMatchResult> findByRoomCodeOrderByRankAsc(String roomCode);
    List<PlayerMatchResult> findByFinishedAtGreaterThanEqualOrderByFinishedAtDesc(Instant from);
    List<PlayerMatchResult> findAllByOrderByFinishedAtDesc();
    boolean existsByRoomCode(String roomCode);
}
