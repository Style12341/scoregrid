package com.scoregrid.tournament.group.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;

public interface GetGroupTeams {
    List<Team> execute(Long groupId);
}
