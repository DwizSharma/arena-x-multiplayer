package com.arenax.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class MatchFinalizationService {
    private final CollegeRepository colleges;
    private final MatchHistoryRepository matches;
    private final PlayerAnalyticsService analytics;
    public MatchFinalizationService(CollegeRepository colleges,MatchHistoryRepository matches,PlayerAnalyticsService analytics) {
        this.colleges=colleges;this.matches=matches;this.analytics=analytics;
    }
    @Transactional
    public void finalizeMatch(String roomCode,String winner,Map<String,Long> pointsByCollege,
                              List<PlayerAnalyticsService.PlayerInput> results) {
        if(!matches.existsByRoomCode(roomCode)) {
            pointsByCollege.forEach((name,points)->{
                College college=colleges.findByName(name).orElseGet(()->new College(name,null));
                college.addPoints(points); College saved=colleges.save(college);
                matches.save(new MatchHistory(roomCode,saved,winner));
            });
        }
        analytics.saveMatch(roomCode,results);
    }
}
