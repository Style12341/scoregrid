package com.scoregrid.prediction.prediction.domain.model;

import java.time.Instant;

public record Prediction(
        String id,
        String userId,
        String tournamentId,
        String matchId,
        PredictionType predictionType,
        int homeScore,
        int awayScore,
        DerivedOutcome derivedOutcome,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {}
