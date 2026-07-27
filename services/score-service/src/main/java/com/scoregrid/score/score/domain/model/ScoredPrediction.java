package com.scoregrid.score.score.domain.model;

public record ScoredPrediction(
        String userId,
        String predictionId,
        int predictedHomeScore,
        int predictedAwayScore,
        int points,
        boolean hit,
        boolean exactHit
) {}
