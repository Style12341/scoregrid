package com.scoregrid.tournament.team.domain.port.out;

import com.scoregrid.tournament.team.domain.model.Team;

import java.util.List;
import java.util.Optional;

public interface TeamRepository {
    Team save(Team team);

    Optional<Team> findById(Long id);

    List<Team> findAll();

    boolean existsById(Long id);

    List<Team> findAllById(List<Long> ids);
}
