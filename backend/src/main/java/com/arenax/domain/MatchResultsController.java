package com.arenax.domain;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchResultsController {
    private final PlayerAnalyticsService analytics;
    public MatchResultsController(PlayerAnalyticsService analytics) { this.analytics=analytics; }
    @GetMapping("/{roomCode}/results")
    public List<PlayerAnalyticsService.ResultView> results(@PathVariable String roomCode) {
        return analytics.getMatch(roomCode);
    }
}
