package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;

public interface TransitionTournamentStatus {

    record Command(Long tournamentId, TournamentStatus status) {}

    Tournament execute(Command command);
}
