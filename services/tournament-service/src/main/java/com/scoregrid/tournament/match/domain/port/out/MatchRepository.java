package com.scoregrid.tournament.match.domain.port.out;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.match.domain.model.MatchStatus;

import java.util.List;
import java.util.Optional;

public interface MatchRepository {
    Match save(Match match);

    Optional<Match> findById(Long id);

    List<Match> findByTournamentId(Long tournamentId);

    List<Match> findByTournamentIdAndStatus(Long tournamentId, MatchStatus status);
}
