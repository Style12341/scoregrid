package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Tournament;

public interface GetTournamentUseCase {
    Tournament execute(Long id);
}
