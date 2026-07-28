package com.scoregrid.tournament.tournament.domain.port.in;

import com.scoregrid.tournament.tournament.domain.model.Tournament;

import java.time.LocalDate;

public interface CreateTournament {

    record Command(String name, String description, LocalDate startDate, LocalDate endDate, String creatorId) {}

    Tournament execute(Command command);
}
