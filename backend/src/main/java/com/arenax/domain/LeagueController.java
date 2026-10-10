package com.arenax.domain;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {
    private final LeagueService leagues;
    public LeagueController(LeagueService leagues) { this.leagues = leagues; }
    @GetMapping
    public LeagueService.LeagueView standings(@RequestParam(defaultValue = "ALL_TIME") String period) {
        return leagues.standings(period);
    }
}
