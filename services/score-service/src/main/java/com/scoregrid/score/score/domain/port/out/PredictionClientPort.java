package com.scoregrid.score.score.domain.port.out;

import java.util.List;

public interface PredictionClientPort {

    List<PredictionResult> getPredictionsForMatch(String matchId);

    record PredictionResult(
            String userId,
            String predictionId,
            int homeScore,
            int awayScore
    ) {}
}
