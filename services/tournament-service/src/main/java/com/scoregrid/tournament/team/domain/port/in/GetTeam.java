package com.scoregrid.tournament.team.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

public interface GetTeam {
    Team execute(Long id);
}
