package com.scoregrid.tournament.team.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;

public interface AssignTeamsToTournament {

    record Command(Long tournamentId, List<Long> teamIds) {}

    List<Team> execute(Command command);
}
