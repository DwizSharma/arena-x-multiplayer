package com.arenax.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class LeagueService {
    public enum Period { WEEKLY, MONTHLY, ALL_TIME }
    public record PlayerStanding(int rank, String playerId, String name, String college, long points,
                                 int matches, int wins, int accuracyPercent) {}
    public record CollegeStanding(int rank, String college, long points, int matches, int players) {}
    public record LeagueView(Period period, List<PlayerStanding> players, List<CollegeStanding> colleges) {}
    private final PlayerMatchResultRepository results;
    public LeagueService(PlayerMatchResultRepository results) { this.results = results; }

    @Transactional(readOnly = true)
    public LeagueView standings(String requestedPeriod) {
        Period period;
        try { period = Period.valueOf(requestedPeriod == null ? "ALL_TIME" : requestedPeriod.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Period must be WEEKLY, MONTHLY, or ALL_TIME"); }
        Instant from = period == Period.WEEKLY ? Instant.now().minus(7, ChronoUnit.DAYS)
                : period == Period.MONTHLY ? Instant.now().minus(30, ChronoUnit.DAYS) : null;
        List<PlayerMatchResult> rows = from == null ? results.findAllByOrderByFinishedAtDesc()
                : results.findByFinishedAtGreaterThanEqualOrderByFinishedAtDesc(from);
        Map<String, PlayerAggregate> players = new HashMap<>();
        Map<String, CollegeAggregate> colleges = new HashMap<>();
        for (PlayerMatchResult row : rows) {
            PlayerAggregate player = players.computeIfAbsent(row.getPlayerId(), ignored -> new PlayerAggregate(row));
            player.add(row);
            CollegeAggregate college = colleges.computeIfAbsent(row.getCollegeName(), ignored -> new CollegeAggregate());
            college.add(row);
        }
        List<PlayerStanding> playerRows = players.values().stream().map(PlayerAggregate::view)
                .sorted(Comparator.comparingLong(PlayerStanding::points).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerStanding::wins).reversed())
                        .thenComparing(PlayerStanding::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<CollegeStanding> collegeRows = colleges.entrySet().stream()
                .map(e -> e.getValue().view(e.getKey()))
                .sorted(Comparator.comparingLong(CollegeStanding::points).reversed()
                        .thenComparing(CollegeStanding::college, String.CASE_INSENSITIVE_ORDER)).toList();
        return new LeagueView(period, rankPlayers(playerRows), rankColleges(collegeRows));
    }
    private List<PlayerStanding> rankPlayers(List<PlayerStanding> rows) {
        List<PlayerStanding> ranked = new ArrayList<>();
        for (int i=0;i<rows.size();i++) { PlayerStanding r=rows.get(i); ranked.add(new PlayerStanding(i+1,r.playerId(),r.name(),r.college(),r.points(),r.matches(),r.wins(),r.accuracyPercent())); }
        return List.copyOf(ranked);
    }
    private List<CollegeStanding> rankColleges(List<CollegeStanding> rows) {
        List<CollegeStanding> ranked = new ArrayList<>();
        for (int i=0;i<rows.size();i++) { CollegeStanding r=rows.get(i); ranked.add(new CollegeStanding(i+1,r.college(),r.points(),r.matches(),r.players())); }
        return List.copyOf(ranked);
    }
    private static final class PlayerAggregate {
        final String id,name,college; long points; int matches,wins,totalCorrect,totalQuestions;
        PlayerAggregate(PlayerMatchResult r) { id=r.getPlayerId(); name=r.getPlayerName(); college=r.getCollegeName(); }
        void add(PlayerMatchResult r) { points+=r.getScore(); matches++; if(r.getRank()==1)wins++; totalCorrect+=r.getCorrectCount(); totalQuestions+=r.getTotalQuestions(); }
        PlayerStanding view() { return new PlayerStanding(0,id,name,college,points,matches,wins,totalQuestions==0?0:totalCorrect*100/totalQuestions); }
    }
    private static final class CollegeAggregate {
        long points; final Set<String> rooms=new HashSet<>(); final Set<String> players=new HashSet<>();
        void add(PlayerMatchResult r) { points+=r.getScore(); rooms.add(r.getRoomCode()); players.add(r.getPlayerId()); }
        CollegeStanding view(String name) { return new CollegeStanding(0,name,points,rooms.size(),players.size()); }
    }
}
