package com.scoregrid.score.score.infrastructure.persistence;

import com.scoregrid.score.score.domain.model.MatchScore;
import com.scoregrid.score.score.domain.port.out.MatchScoreRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class MatchScoreRepositoryAdapter implements MatchScoreRepository {

    private final MongoMatchScoreRepository mongoRepo;

    MatchScoreRepositoryAdapter(MongoMatchScoreRepository mongoRepo) {
        this.mongoRepo = mongoRepo;
    }

    @Override
    public MatchScore save(MatchScore matchScore) {
        MatchScoreDocument doc = MatchScoreMapper.toDocument(matchScore);
        MatchScoreDocument saved = mongoRepo.save(doc);
        return MatchScoreMapper.toDomain(saved);
    }

    @Override
    public Optional<MatchScore> findByMatchId(String matchId) {
        return mongoRepo.findById(matchId).map(MatchScoreMapper::toDomain);
    }

    @Override
    public List<MatchScore> findAllByTournamentId(String tournamentId) {
        return mongoRepo.findAllByTournamentId(tournamentId).stream()
                .map(MatchScoreMapper::toDomain)
                .toList();
    }

    @Override
    public List<MatchScore> findAll() {
        return mongoRepo.findAll().stream()
                .map(MatchScoreMapper::toDomain)
                .toList();
    }
}
