package com.scoregrid.score.score.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

interface MongoMatchScoreRepository extends MongoRepository<MatchScoreDocument, String> {

    List<MatchScoreDocument> findAllByTournamentId(String tournamentId);

    @Query("{ 'individualScores.userId': ?0 }")
    List<MatchScoreDocument> findAllByUserId(String userId);
}
