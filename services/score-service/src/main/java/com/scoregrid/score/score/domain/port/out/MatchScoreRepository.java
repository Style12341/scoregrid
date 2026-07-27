package com.scoregrid.score.score.domain.port.out;

import com.scoregrid.score.score.domain.model.MatchScore;

import java.util.List;
import java.util.Optional;

public interface MatchScoreRepository {

    MatchScore save(MatchScore matchScore);

    Optional<MatchScore> findByMatchId(String matchId);

    List<MatchScore> findAllByTournamentId(String tournamentId);

    List<MatchScore> findAll();
}
