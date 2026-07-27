package com.scoregrid.prediction.prediction.domain.port.in;

import com.scoregrid.prediction.prediction.domain.model.Prediction;

import java.util.List;
import java.util.Optional;

public interface GetPredictionsUseCase {

    List<Prediction> getMyPredictions(String userId, String tournamentId, int page, int size);

    List<Prediction> getAllMyPredictions(String userId, int page, int size);

    long countMyPredictions(String userId, String tournamentId);

    Optional<Prediction> getMyPredictionForMatch(String userId, String matchId);

    List<Prediction> getPredictionsByMatch(String matchId);

    List<Prediction> getPredictionsByUserAndTournament(String userId, String tournamentId);
}
