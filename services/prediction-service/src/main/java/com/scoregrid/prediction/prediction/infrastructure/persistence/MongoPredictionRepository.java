package com.scoregrid.prediction.prediction.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface MongoPredictionRepository extends MongoRepository<PredictionDocument, String> {

    Optional<PredictionDocument> findByUserIdAndMatchId(String userId, String matchId);

    List<PredictionDocument> findByUserId(String userId, Pageable pageable);

    List<PredictionDocument> findByUserIdAndTournamentId(String userId, String tournamentId, Pageable pageable);

    long countByUserIdAndTournamentId(String userId, String tournamentId);

    List<PredictionDocument> findByMatchId(String matchId);

    boolean existsByUserIdAndMatchId(String userId, String matchId);
}
