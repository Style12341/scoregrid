package com.scoregrid.tournament.group.domain.port.in;

import com.scoregrid.tournament.group.domain.model.Group;

public interface CreateGroup {
    record Command(Long tournamentId, String name, int displayOrder) {}
    Group execute(Command command);
}
