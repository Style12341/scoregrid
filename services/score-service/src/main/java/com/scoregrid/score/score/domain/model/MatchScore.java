package com.scoregrid.score.score.domain.model;

import java.time.Instant;
import java.util.List;

public record MatchScore(
        String matchId,
        String tournamentId,
        int homeScore,
        int awayScore,
        String outcome,
        int scoredPredictions,
        int totalPointsAwarded,
        Instant calculatedAt,
        List<ScoredPrediction> individualScores
) {}
