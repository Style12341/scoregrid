package com.scoregrid.tournament.group.domain.port.in;

import com.scoregrid.tournament.group.domain.model.Group;

import java.util.List;

public interface ListGroupsUseCase {
    List<Group> execute(Long tournamentId);
}
