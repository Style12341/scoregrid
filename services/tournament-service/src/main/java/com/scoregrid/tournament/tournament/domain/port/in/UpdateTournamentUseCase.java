package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Tournament;

import java.time.LocalDate;

public interface UpdateTournamentUseCase {

    record Command(Long tournamentId, String name, String description, LocalDate startDate, LocalDate endDate) {}

    Tournament execute(Command command);
}
