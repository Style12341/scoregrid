package com.scoregrid.tournament.team.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;

public interface ListTeams {
    List<Team> execute();
}
