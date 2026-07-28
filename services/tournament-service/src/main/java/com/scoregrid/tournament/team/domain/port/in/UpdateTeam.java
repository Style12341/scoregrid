package com.scoregrid.tournament.team.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

public interface UpdateTeam {

    record Command(Long id, String name, String shortName, String country, String logoUrl) {}

    Team execute(Command command);
}
