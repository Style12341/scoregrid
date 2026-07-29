package com.scoregrid.tournament.group.domain.port.out;

import com.scoregrid.tournament.group.domain.model.Group;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {
    Group save(Group group);

    List<Group> findByTournamentId(Long tournamentId);

    Optional<Group> findById(Long id);
}
