package com.scoregrid.tournament.phase.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhaseJpaRepository extends JpaRepository<PhaseJpaEntity, Long> {

    List<PhaseJpaEntity> findByTournamentIdOrderByDisplayOrderAsc(Long tournamentId);
}
