package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Participant;

public interface JoinTournamentUseCase {

    record Command(Long tournamentId, String userId) {}

    Participant execute(Command command);
}
