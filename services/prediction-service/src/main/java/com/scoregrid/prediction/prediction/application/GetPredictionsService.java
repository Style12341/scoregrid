package com.scoregrid.prediction.prediction.application;

import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.port.in.GetPredictionsUseCase;
import com.scoregrid.prediction.prediction.domain.port.out.PredictionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
class GetPredictionsService implements GetPredictionsUseCase {

    private final PredictionRepository predictionRepository;

    GetPredictionsService(PredictionRepository predictionRepository) {
        this.predictionRepository = predictionRepository;
    }

    @Override
    public List<Prediction> getMyPredictions(String userId, String tournamentId, int page, int size) {
        return predictionRepository.findByUserIdAndTournamentId(userId, tournamentId, page, size);
    }

    @Override
    public List<Prediction> getAllMyPredictions(String userId, int page, int size) {
        return predictionRepository.findByUserId(userId, page, size);
    }

    @Override
    public long countMyPredictions(String userId, String tournamentId) {
        return predictionRepository.countByUserIdAndTournamentId(userId, tournamentId);
    }

    @Override
    public Optional<Prediction> getMyPredictionForMatch(String userId, String matchId) {
        return predictionRepository.findByUserIdAndMatchId(userId, matchId);
    }

    @Override
    public List<Prediction> getPredictionsByMatch(String matchId) {
        return predictionRepository.findByMatchId(matchId);
    }

    @Override
    public List<Prediction> getPredictionsByUserAndTournament(String userId, String tournamentId) {
        return predictionRepository.findByUserIdAndTournamentId(userId, tournamentId, 0, Integer.MAX_VALUE);
    }
}
