package com.scoregrid.prediction.prediction.domain.port.in;

import com.scoregrid.prediction.prediction.domain.model.Prediction;

public interface UpdatePredictionUseCase {

    Prediction update(String userId, String predictionId, int homeScore, int awayScore);
}
