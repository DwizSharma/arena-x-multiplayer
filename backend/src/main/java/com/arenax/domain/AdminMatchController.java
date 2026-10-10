package com.arenax.domain;

import com.arenax.game.AdminAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/admin/matches")
public class AdminMatchController {
    public record MatchAnalytics(String roomCode, int players, int questionsPerPlayer, long totalPoints,
                                 double averageAccuracyPercent, List<PlayerAnalyticsService.ResultView> playersResult) {}
    private final AdminAuthService auth;
    private final PlayerAnalyticsService analytics;
    public AdminMatchController(AdminAuthService auth, PlayerAnalyticsService analytics) { this.auth=auth; this.analytics=analytics; }

    @GetMapping("/{roomCode}/analytics")
    public MatchAnalytics analytics(@RequestHeader(value="Authorization", required=false) String authorization,
                                    @PathVariable String roomCode) {
        auth.requireAdmin(authorization);
        List<PlayerAnalyticsService.ResultView> rows = requireMatch(roomCode);
        long points=rows.stream().mapToLong(PlayerAnalyticsService.ResultView::score).sum();
        double accuracy=rows.isEmpty()?0:rows.stream().mapToInt(PlayerAnalyticsService.ResultView::accuracyPercent).average().orElse(0);
        int questions=rows.stream().mapToInt(PlayerAnalyticsService.ResultView::totalQuestions).max().orElse(0);
        return new MatchAnalytics(roomCode.toUpperCase(Locale.ROOT), rows.size(), questions, points, accuracy, rows);
    }

    @GetMapping(value="/{roomCode}/export.csv", produces="text/csv")
    public ResponseEntity<byte[]> export(@RequestHeader(value="Authorization", required=false) String authorization,
                                         @PathVariable String roomCode) {
        auth.requireAdmin(authorization);
        List<PlayerAnalyticsService.ResultView> rows=requireMatch(roomCode);
        StringBuilder csv=new StringBuilder("player_id,player_name,college,rank,score,accuracy_percent,correct,total_questions,average_response_ms,question_index,question_id,topic,selected_option_id,is_correct,points_earned,response_ms\r\n");
        for(var player:rows) {
            if(player.answers().isEmpty()) append(csv, player, null);
            else for(var answer:player.answers()) append(csv, player, answer);
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"arenax-"+roomCode.toUpperCase(Locale.ROOT)+"-results.csv\"")
                .contentType(new MediaType("text","csv", StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private List<PlayerAnalyticsService.ResultView> requireMatch(String roomCode) {
        List<PlayerAnalyticsService.ResultView> rows=analytics.getMatch(roomCode);
        if(rows.isEmpty()) throw new IllegalArgumentException("Completed match results not found");
        return rows;
    }
    private void append(StringBuilder csv, PlayerAnalyticsService.ResultView p, PlayerAnalyticsService.AnswerView a) {
        List<Object> values=Arrays.asList(p.playerId(),p.name(),p.college(),p.rank(),p.score(),p.accuracyPercent(),p.correctCount(),p.totalQuestions(),p.averageResponseMs(),
                a==null?null:a.questionIndex(),a==null?null:a.questionId(),a==null?null:a.topic(),a==null?null:a.selectedOptionId(),a==null?null:a.correct(),a==null?null:a.pointsEarned(),a==null?null:a.responseMs());
        for(int i=0;i<values.size();i++) { if(i>0)csv.append(','); csv.append(escape(values.get(i)==null?"":String.valueOf(values.get(i)))); }
        csv.append("\r\n");
    }
    private String escape(String value) {
        String normalized=value.stripLeading();
        if(!normalized.isEmpty() && "=+-@\t\r".indexOf(normalized.charAt(0))>=0) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
