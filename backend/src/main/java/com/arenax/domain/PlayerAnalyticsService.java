package com.arenax.domain;

import com.arenax.game.GameTypes.TopicAnalytics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class PlayerAnalyticsService {
    public record AnswerInput(int questionIndex, String questionId, String prompt, String topic, String selectedOptionId,
                              boolean correct, int pointsEarned, Long responseMs) {}
    public record PlayerInput(String playerId, String name, String college, int score, int rank, int correctCount,
                              int totalQuestions, int accuracyPercent, long averageResponseMs,
                              Map<String,TopicAnalytics> topics, List<AnswerInput> answers) {}
    public record AnswerView(int questionIndex, String questionId, String prompt, String topic, String selectedOptionId,
                             boolean correct, int pointsEarned, Long responseMs) {}
    public record ResultView(String playerId, String name, String college, int score, int rank, int correctCount,
                             int totalQuestions, int accuracyPercent, long averageResponseMs,
                             Map<String,TopicAnalytics> topics, List<AnswerView> answers) {}

    private final PlayerMatchResultRepository results;
    private final PlayerQuestionAnswerRepository answers;
    private final ObjectMapper mapper;
    public PlayerAnalyticsService(PlayerMatchResultRepository results, PlayerQuestionAnswerRepository answers, ObjectMapper mapper) {
        this.results=results; this.answers=answers; this.mapper=mapper;
    }

    @Transactional
    public void saveMatch(String roomCode, List<PlayerInput> players) {
        for (PlayerInput input : players) {
            try {
                PlayerMatchResult result = results.save(new PlayerMatchResult(roomCode, input.playerId(), input.name(),
                        input.college(), input.score(), input.rank(), input.correctCount(), input.totalQuestions(),
                        input.accuracyPercent(), input.averageResponseMs(), mapper.writeValueAsString(input.topics())));
                List<PlayerQuestionAnswer> rows = input.answers().stream().map(answer -> new PlayerQuestionAnswer(result,
                        answer.questionIndex(), answer.questionId(), answer.prompt(), answer.topic(), answer.selectedOptionId(),
                        answer.correct(), answer.pointsEarned(), answer.responseMs())).toList();
                answers.saveAll(rows);
            } catch (Exception e) { throw new IllegalStateException("Could not persist match analytics", e); }
        }
    }

    @Transactional(readOnly = true)
    public List<ResultView> getMatch(String roomCode) {
        return results.findByRoomCodeOrderByRankAsc(roomCode.toUpperCase()).stream().map(result -> {
            try {
                Map<String,TopicAnalytics> topics = mapper.readValue(result.getTopicsJson(), new TypeReference<>() {});
                List<AnswerView> answerViews = answers.findByResult_IdOrderByQuestionIndexAsc(result.getId()).stream()
                        .map(answer -> new AnswerView(answer.getQuestionIndex(), answer.getQuestionId(), answer.getQuestionPrompt(),
                                answer.getTopic(), answer.getSelectedOptionId(), answer.isCorrect(), answer.getPointsEarned(), answer.getResponseMs())).toList();
                return new ResultView(result.getPlayerId(), result.getPlayerName(), result.getCollegeName(), result.getScore(),
                        result.getRank(), result.getCorrectCount(), result.getTotalQuestions(), result.getAccuracyPercent(),
                        result.getAverageResponseMs(), topics, answerViews);
            } catch (Exception e) { throw new IllegalStateException("Stored match analytics are invalid", e); }
        }).toList();
    }
}
