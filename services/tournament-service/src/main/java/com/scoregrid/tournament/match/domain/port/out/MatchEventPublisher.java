package com.scoregrid.tournament.match.domain.port.out;

import com.scoregrid.tournament.match.domain.model.Match;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;

public interface MatchEventPublisher {
    void scheduled(Match match, TournamentStatus tournamentStatus);
    void updated(Match match, TournamentStatus tournamentStatus);
    void finished(Match match);
}
