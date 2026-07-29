package com.scoregrid.tournament.group.domain.port.in;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;

public interface AssignTeamsToGroup {
    record Command(Long groupId, List<Long> teamIds) {}
    List<Team> execute(Command command);
}
