package com.scoregrid.score.score.infrastructure.web;

import com.scoregrid.score.score.domain.model.PredictionPoints;

record PredictionPointsResponse(String predictionId, String matchId, int points) {

    static PredictionPointsResponse from(PredictionPoints score) {
        return new PredictionPointsResponse(score.predictionId(), score.matchId(), score.points());
    }
}
