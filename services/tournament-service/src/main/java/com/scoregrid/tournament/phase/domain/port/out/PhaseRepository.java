package com.scoregrid.tournament.phase.domain.port.out;

import com.scoregrid.tournament.phase.domain.model.Phase;

import java.util.List;
import java.util.Optional;

public interface PhaseRepository {
    Phase save(Phase phase);

    List<Phase> findByTournamentId(Long tournamentId);

    Optional<Phase> findById(Long id);
}
