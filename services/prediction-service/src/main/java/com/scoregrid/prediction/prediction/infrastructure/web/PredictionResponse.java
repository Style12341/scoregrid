package com.scoregrid.prediction.prediction.infrastructure.web;

import com.scoregrid.prediction.prediction.domain.model.Prediction;

import java.time.Instant;

record PredictionResponse(
        String id,
        String userId,
        String tournamentId,
        String matchId,
        String predictionType,
        int homeScore,
        int awayScore,
        String derivedOutcome,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {
    static PredictionResponse from(Prediction prediction) {
        return new PredictionResponse(
                prediction.id(),
                prediction.userId(),
                prediction.tournamentId(),
                prediction.matchId(),
                prediction.predictionType().name(),
                prediction.homeScore(),
                prediction.awayScore(),
                prediction.derivedOutcome().name(),
                prediction.locked(),
                prediction.createdAt(),
                prediction.updatedAt()
        );
    }
}
