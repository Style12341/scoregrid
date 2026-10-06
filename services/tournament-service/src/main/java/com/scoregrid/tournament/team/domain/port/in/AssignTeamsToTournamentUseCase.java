package com.scoregrid.tournament.team.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;

public interface AssignTeamsToTournamentUseCase {

    record Command(Long tournamentId, List<String> teamIds) {}

    List<Team> execute(Command command);
}
