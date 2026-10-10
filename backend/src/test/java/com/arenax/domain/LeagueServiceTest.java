package com.arenax.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeagueServiceTest {
    private final PlayerMatchResultRepository results = mock(PlayerMatchResultRepository.class);
    private final LeagueService service = new LeagueService(results);

    @Test
    void aggregatesPlayerAndCollegeStandingsAcrossMatches() {
        when(results.findAllByOrderByFinishedAtDesc()).thenReturn(List.of(
                result("room-a", "player-1", "Ari", "North College", 100, 1, 2, 3),
                result("room-b", "player-1", "Ari", "North College", 50, 2, 1, 2),
                result("room-a", "player-2", "Bea", "South College", 200, 1, 3, 3)));

        LeagueService.LeagueView league = service.standings(" all_time ");

        assertEquals(LeagueService.Period.ALL_TIME, league.period());
        assertEquals(List.of("player-2", "player-1"), league.players().stream().map(LeagueService.PlayerStanding::playerId).toList());
        LeagueService.PlayerStanding ari = league.players().get(1);
        assertEquals(150, ari.points());
        assertEquals(2, ari.matches());
        assertEquals(1, ari.wins());
        assertEquals(60, ari.accuracyPercent());
        assertEquals("North College", league.colleges().get(1).college());
        assertEquals(2, league.colleges().get(1).matches());
        assertEquals(1, league.colleges().get(1).players());
    }

    @Test
    void filtersWeeklyStandingsAtRepositoryBoundary() {
        when(results.findByFinishedAtGreaterThanEqualOrderByFinishedAtDesc(any())).thenReturn(List.of());

        LeagueService.LeagueView league = service.standings("WEEKLY");

        assertEquals(LeagueService.Period.WEEKLY, league.period());
        verify(results).findByFinishedAtGreaterThanEqualOrderByFinishedAtDesc(any());
        verify(results, never()).findAllByOrderByFinishedAtDesc();
    }

    @Test
    void rejectsUnknownPeriod() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.standings("yearly"));
        assertEquals("Period must be WEEKLY, MONTHLY, or ALL_TIME", error.getMessage());
    }

    private PlayerMatchResult result(String room, String player, String name, String college, int score, int rank,
                                     int correct, int total) {
        return new PlayerMatchResult(room, player, name, college, score, rank, correct, total,
                correct * 100 / total, 800, "{}");
    }
}
