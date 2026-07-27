package com.scoregrid.score.score.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoMatchScoreRepository extends MongoRepository<MatchScoreDocument, String> {

    List<MatchScoreDocument> findAllByTournamentId(String tournamentId);
}
