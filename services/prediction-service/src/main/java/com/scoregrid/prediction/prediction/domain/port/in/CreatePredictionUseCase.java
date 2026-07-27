package com.scoregrid.prediction.prediction.domain.port.in;

import com.scoregrid.prediction.prediction.domain.model.Prediction;

public interface CreatePredictionUseCase {

    Prediction create(String userId, String matchId, int homeScore, int awayScore);
}
