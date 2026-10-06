package com.scoregrid.prediction.prediction.infrastructure.persistence;

import com.scoregrid.prediction.prediction.domain.model.Prediction;
import com.scoregrid.prediction.prediction.domain.port.out.PredictionRepository;
import com.scoregrid.prediction.shared.error.DomainException;
import com.scoregrid.prediction.shared.error.ErrorKind;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * The unique index on (userId, matchId) is the duplicate rule (AGENTS.md
     * hard rule 7). The service's existsBy check only gives the common case a
     * clean error first: two concurrent requests both pass it, and the loser's
     * insert lands here. That is the same 409 the check would have given, not
     * a 500.
     */
    @Override
    public Prediction save(Prediction prediction) {
        PredictionDocument doc = PredictionMapper.toDocument(prediction);
        try {
            PredictionDocument saved = mongoRepo.save(doc);
            return PredictionMapper.toDomain(saved);
        } catch (DuplicateKeyException e) {
            throw new DomainException(ErrorKind.CONFLICT, "DUPLICATE_PREDICTION",
                    "You already have a prediction for this match.");
        }
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
