package com.arenax.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerMatchResultRepository extends JpaRepository<PlayerMatchResult, String> {
    List<PlayerMatchResult> findByRoomCodeOrderByRankAsc(String roomCode);
}
