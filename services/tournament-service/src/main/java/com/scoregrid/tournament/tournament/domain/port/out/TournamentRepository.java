package com.scoregrid.tournament.tournament.domain.port.out;

import com.scoregrid.tournament.tournament.domain.model.Tournament;
import com.scoregrid.tournament.tournament.domain.model.TournamentStatus;

import java.util.List;
import java.util.Optional;

public interface TournamentRepository {
    Tournament save(Tournament tournament);

    Optional<Tournament> findById(Long id);

    List<Tournament> findAllByStatus(TournamentStatus status, int offset, int limit);

    List<Tournament> findAllPaginated(int offset, int limit);

    long countByStatus(TournamentStatus status);

    long count();

    void delete(Long id);

    boolean existsById(Long id);
}
