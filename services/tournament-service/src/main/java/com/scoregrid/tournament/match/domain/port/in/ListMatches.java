package com.scoregrid.tournament.match.domain.port.in;

import com.scoregrid.tournament.match.domain.model.Match;

import java.util.List;
import java.util.Optional;

import com.scoregrid.tournament.match.domain.model.MatchStatus;

public interface ListMatches {
    List<Match> execute(Long tournamentId, Optional<MatchStatus> statusFilter);
}
