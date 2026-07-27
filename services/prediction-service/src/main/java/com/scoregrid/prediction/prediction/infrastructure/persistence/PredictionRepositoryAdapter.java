package com.scoregrid.prediction.prediction.infrastructure.persistence;

import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.port.out.PredictionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class PredictionRepositoryAdapter implements PredictionRepository {

    private final MongoPredictionRepository mongoRepo;

    PredictionRepositoryAdapter(MongoPredictionRepository mongoRepo) {
        this.mongoRepo = mongoRepo;
    }

    @Override
    public Prediction save(Prediction prediction) {
        PredictionDocument doc = PredictionMapper.toDocument(prediction);
        PredictionDocument saved = mongoRepo.save(doc);
        return PredictionMapper.toDomain(saved);
    }

    @Override
    public Optional<Prediction> findById(String id) {
        return mongoRepo.findById(id).map(PredictionMapper::toDomain);
    }

    @Override
    public Optional<Prediction> findByUserIdAndMatchId(String userId, String matchId) {
        return mongoRepo.findByUserIdAndMatchId(userId, matchId)
                .map(PredictionMapper::toDomain);
    }

    @Override
    public List<Prediction> findByUserIdAndTournamentId(String userId, String tournamentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return mongoRepo.findByUserIdAndTournamentId(userId, tournamentId, pageable)
                .stream()
                .map(PredictionMapper::toDomain)
                .toList();
    }

    @Override
    public List<Prediction> findByUserId(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return mongoRepo.findByUserId(userId, pageable)
                .stream()
                .map(PredictionMapper::toDomain)
                .toList();
    }

    @Override
    public long countByUserIdAndTournamentId(String userId, String tournamentId) {
        return mongoRepo.countByUserIdAndTournamentId(userId, tournamentId);
    }

    @Override
    public List<Prediction> findByMatchId(String matchId) {
        return mongoRepo.findByMatchId(matchId).stream()
                .map(PredictionMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByUserIdAndMatchId(String userId, String matchId) {
        return mongoRepo.existsByUserIdAndMatchId(userId, matchId);
    }
}
