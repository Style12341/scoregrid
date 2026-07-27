package com.scoregrid.prediction.prediction.domain.port.out;

import com.scoregrid.prediction.prediction.domain.model.Prediction;

import java.util.List;
import java.util.Optional;

public interface PredictionRepository {

    Prediction save(Prediction prediction);

    Optional<Prediction> findById(String id);

    Optional<Prediction> findByUserIdAndMatchId(String userId, String matchId);

    List<Prediction> findByUserIdAndTournamentId(String userId, String tournamentId, int page, int size);

    List<Prediction> findByUserId(String userId, int page, int size);

    long countByUserIdAndTournamentId(String userId, String tournamentId);

    List<Prediction> findByMatchId(String matchId);

    boolean existsByUserIdAndMatchId(String userId, String matchId);
}
