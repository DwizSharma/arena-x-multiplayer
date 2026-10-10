package com.arenax.game;

import com.arenax.game.GameTypes.*;
import java.util.List;
import java.util.Map;

public record RoomStateSnapshot(String code, Phase phase, List<Prompt> prompts, String mode, String questionSetId,
        boolean hostManaged, int questionIndex, long serverStart, long endsAt, long allowedMs, long transitionAt,
        Map<String,Integer> distribution, List<PlayerState> players, String momentId, String momentKind,
        String momentPrompt, long momentClosesAt, List<String> momentOptions, Map<String,Integer> momentCounts,
        Map<String,String> momentVotes) {
    public record PlayerState(SessionRegistry.Identity identity, int score, int answersCorrect,
            List<Long> responseTimes, Map<Integer,SubmissionState> submissions, Map<String,String> optionMapping) {}
    public record SubmissionState(String canonicalOptionId, boolean correct, int pointsEarned, long elapsedMs) {}
}
